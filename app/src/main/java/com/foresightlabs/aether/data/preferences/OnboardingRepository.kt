package com.foresightlabs.aether.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/** Stores only whether the one-time introduction has been completed. */
class OnboardingRepository(
    private val context: Context,
    private val legacyInstallation: Boolean
) {
    private val completedKey = booleanPreferencesKey("completed")

    val initialCompleted: Boolean = legacyInstallation

    val completed: Flow<Boolean> = context.onboardingDataStore.data.map { preferences ->
        preferences[completedKey] ?: legacyInstallation
    }

    suspend fun markCompleted() {
        context.onboardingDataStore.edit { preferences ->
            preferences[completedKey] = true
        }
    }
}
