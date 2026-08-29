package com.foresightlabs.aether.data.preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.foresightlabs.aether.data.preferences.AetherAppearancePreferences
import com.foresightlabs.aether.data.preferences.AppearanceRepository
import com.foresightlabs.aether.data.preferences.ChatBubbleStyle
import com.foresightlabs.aether.ui.theme.AccentColorChoice
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AppearanceRepositoryTest {
    @Test fun globalChoicesSurviveRepositoryRecreation() = runBlocking {
        val file = Files.createTempDirectory("aether-appearance-test").resolve("prefs.preferences_pb").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
        val first = AppearanceRepository(store)
        first.updateAtmosphereMode(AtmosphereMode.MANUAL)
        first.updateManualAtmosphere(TimeAtmospherePalette.NIGHT)
        first.updateUseAtmosphereAccent(false)
        first.updateAccentChoice(AccentColorChoice.MIST_BLUE)

        val recreated = AppearanceRepository(store)
        val preferences = recreated.globalPreferences.first {
            it.atmosphereMode == AtmosphereMode.MANUAL
        }
        assertEquals(TimeAtmospherePalette.NIGHT, preferences.manualAtmosphere)
        assertFalse(preferences.useAtmosphereAccent)
        assertEquals(AccentColorChoice.MIST_BLUE, preferences.accentChoice)
    }

    @Test fun chatOverridesAreChatIdScopedPersistentAndReactive() = runBlocking {
        val file = Files.createTempDirectory("aether-chat-appearance-test").resolve("prefs.preferences_pb").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
        val repository = AppearanceRepository(store)
        repository.updateAtmosphereMode(AtmosphereMode.MANUAL)
        repository.updateManualAtmosphere(TimeAtmospherePalette.DAY)
        val global = repository.globalPreferences.first { it.manualAtmosphere == TimeAtmospherePalette.DAY }

        assertEquals(TimeAtmospherePalette.DAY, repository.resolveChatAppearance(100L, global,
            repository.getChatAppearanceFlow(100L).first()).palette)
        repository.setChatAppearance(100L, false, TimeAtmospherePalette.NIGHT, ChatBubbleStyle.MIDNIGHT)

        val saved = repository.getChatAppearanceFlow(100L).first { !it.inheritGlobal }
        assertEquals(100L, saved.chatId)
        assertEquals(TimeAtmospherePalette.NIGHT, repository.resolveChatAppearance(100L, global, saved).palette)
        assertEquals(TimeAtmospherePalette.DAY, repository.resolveChatAppearance(200L, global,
            repository.getChatAppearanceFlow(200L).first()).palette)

        val recreated = AppearanceRepository(store)
        val restored = recreated.getChatAppearanceFlow(100L).first { !it.inheritGlobal }
        assertEquals(TimeAtmospherePalette.NIGHT, restored.palette)
        recreated.updateManualAtmosphere(TimeAtmospherePalette.GOLDEN_HOUR)
        val changedGlobal = recreated.globalPreferences.first { it.manualAtmosphere == TimeAtmospherePalette.GOLDEN_HOUR }
        assertEquals(TimeAtmospherePalette.NIGHT, recreated.resolveChatAppearance(100L, changedGlobal, restored).palette)
        assertEquals(TimeAtmospherePalette.GOLDEN_HOUR, recreated.resolveChatAppearance(200L, changedGlobal,
            recreated.getChatAppearanceFlow(200L).first()).palette)

        recreated.resetChatAppearance(100L)
        val reset = recreated.getChatAppearanceFlow(100L).first { it.inheritGlobal }
        assertEquals(TimeAtmospherePalette.GOLDEN_HOUR, recreated.resolveChatAppearance(100L, changedGlobal, reset).palette)
    }

    @Test fun unknownValuesFallBackSafely() = runBlocking {
        val file = Files.createTempDirectory("aether-appearance-fallback-test").resolve("prefs.preferences_pb").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
        store.edit { prefs ->
            prefs[stringPreferencesKey("theme_mode")] = "REMOVED"
            prefs[stringPreferencesKey("manual_atmosphere")] = "REMOVED"
            prefs[stringPreferencesKey("chat_appearance_42")] = "false|REMOVED|REMOVED|1"
        }
        val repository = AppearanceRepository(store)
        val preferences = repository.globalPreferences.first()
        assertEquals(TimeAtmospherePalette.GOLDEN_HOUR, preferences.manualAtmosphere)
        val override = repository.getChatAppearanceFlow(42L).first()
        assertNull(override.palette)
        assertTrue(override.inheritGlobal || repository.resolveChatAppearance(42L, preferences, override).isCustom.not())
    }
}
