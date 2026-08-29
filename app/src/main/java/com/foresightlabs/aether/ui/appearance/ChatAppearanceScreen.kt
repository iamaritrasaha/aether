package com.foresightlabs.aether.ui.appearance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foresightlabs.aether.data.preferences.ChatBubbleStyle
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherSectionLabel
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAppearanceRepository
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import kotlinx.coroutines.launch

/** Local-only appearance controls for one resolved Telegram chatId. */
@Composable
fun ChatAppearanceScreen(chatId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repository = LocalAppearanceRepository.current
    val override by repository.getChatAppearanceFlow(chatId)
        .collectAsStateWithLifecycle(initialValue = null)
    val current = override
    val atmosphere = LocalAtmosphere.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = aetherFloatingHeaderContentTopPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    PreviewCard(
                        palette = current?.palette ?: atmosphere.palette,
                        custom = current?.inheritGlobal == false
                    )
                }
                item { AetherSectionLabel("APPEARANCE", tone = com.foresightlabs.aether.ui.design.AetherHeaderTone.Atmosphere) }
                item {
                    AppearanceOption(
                        title = "Use Aether Default",
                        subtitle = "Inherits your global appearance automatically",
                        selected = current == null || current.inheritGlobal,
                        onClick = { scope.launch { repository.resetChatAppearance(chatId) } }
                    )
                }
                item { AetherSectionLabel("CUSTOM ATMOSPHERE", tone = com.foresightlabs.aether.ui.design.AetherHeaderTone.Atmosphere) }
                items(TimeAtmospherePalette.entries) { palette ->
                    AppearanceOption(
                        title = palette.displayName,
                        subtitle = "Use ${palette.displayName} for this conversation",
                        selected = current?.inheritGlobal == false && current.palette == palette,
                        onClick = { scope.launch {
                            repository.setChatAppearance(chatId, false, palette, current?.bubbleStyle ?: ChatBubbleStyle.ATMOSPHERE)
                        } }
                    )
                }
                item { AetherSectionLabel("BUBBLES", tone = com.foresightlabs.aether.ui.design.AetherHeaderTone.Atmosphere) }
                items(ChatBubbleStyle.entries) { style ->
                    AppearanceOption(
                        title = style.displayName,
                        subtitle = style.description,
                        selected = current?.inheritGlobal == false && current.bubbleStyle == style,
                        onClick = { scope.launch {
                            repository.setChatAppearance(chatId, false, current?.palette ?: atmosphere.palette, style)
                        } }
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }

        AetherFloatingHeader(
            title = "Chat Appearance",
            subtitle = "Saved only on this device",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = { AetherBackButton(onClick = onBack) }
        )
    }
}

@Composable private fun AppearanceOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val atmosphere = LocalAtmosphere.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(AetherEmber.Shapes.M)
            .background(if (selected) atmosphere.accent.copy(alpha = .28f) else Color(0x35000000))
            .border(1.dp, if (selected) atmosphere.accent else Color(0x28FFFFFF), AetherEmber.Shapes.M)
            .clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = ManropeFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = AetherEmber.Colors.AtmosphereTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontFamily = ManropeFontFamily, fontSize = 12.sp,
                color = AetherEmber.Colors.AtmosphereTextTertiary)
        }
        if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable private fun PreviewCard(palette: TimeAtmospherePalette, custom: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(AetherEmber.Shapes.L)
            .background(Brush.linearGradient(palette.colors)).border(1.dp, Color(0x35FFFFFF), AetherEmber.Shapes.L)
            .padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(if (custom) "Custom chat appearance" else "Aether default", fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(palette.displayName, fontFamily = ManropeFontFamily, fontSize = 12.sp, color = Color(0xE6FFFFFF))
        }
        Box(Modifier.clip(AetherEmber.Shapes.OutgoingBubble).background(Color(0xB8000000)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Hello", fontFamily = ManropeFontFamily, color = Color.White, fontSize = 13.sp)
        }
    }
}
