package com.foresightlabs.aether.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.ui.components.AetherBrandMark
import com.foresightlabs.aether.ui.components.isReducedMotionEnabled
import com.foresightlabs.aether.ui.theme.AetherAuthLavender
import com.foresightlabs.aether.ui.theme.AetherAuthMist
import com.foresightlabs.aether.ui.theme.AetherAuthMoon
import com.foresightlabs.aether.ui.theme.DarkBackground
import com.foresightlabs.aether.ui.theme.DarkSurfaceElevated
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

@Composable
fun OnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current || remember(context) { isReducedMotionEnabled(context) }
    var scene by remember { mutableIntStateOf(0) }
    OnboardingScene(scene, reducedMotion, { if (scene == 2) onComplete() else scene++ }, onComplete, modifier)
}

@Composable
private fun OnboardingScene(scene: Int, reducedMotion: Boolean, onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(DarkBackground)) {
        OnboardingLight(scene, reducedMotion)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            AnimatedContent(targetState = scene, transitionSpec = {
                if (reducedMotion) fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                else (fadeIn(tween(340)) + slideInVertically(tween(420)) { it / 12 }) togetherWith (fadeOut(tween(220)) + slideOutVertically(tween(300)) { -it / 18 })
            }, label = "onboarding_room_morph") { OnboardingVisual(it) }
            Spacer(Modifier.height(42.dp))
            AnimatedContent(targetState = scene, transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) }, label = "onboarding_copy") { active ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp)) {
                    Text(onboardingTitle(active), color = Color(0xFFF2F0F7), fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(onboardingCopy(active), color = AetherAuthMist, fontFamily = ManropeFontFamily, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { index -> Box(Modifier.size(if (index == scene) 22.dp else 7.dp, 7.dp).clip(CircleShape).background(if (index == scene) AetherAuthMist else AetherAuthLavender.copy(alpha = 0.35f))) } }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth().widthIn(max = 560.dp).height(56.dp).clip(RoundedCornerShape(22.dp)).background(AetherAuthMist.copy(alpha = 0.92f)).clickable(onClick = onNext).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (scene == 2) "Enter Aether" else "Continue", color = Color(0xFF101019), fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = Color(0xFF101019))
            }
            if (scene == 2) Text("Skip introduction", color = AetherAuthMist, fontFamily = ManropeFontFamily, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onSkip).padding(horizontal = 16.dp, vertical = 14.dp)) else Spacer(Modifier.height(44.dp))
        }
    }
}

private fun onboardingTitle(scene: Int) = when (scene) { 0 -> "A quieter way to Telegram."; 1 -> "People first."; else -> "Still your Telegram." }
private fun onboardingCopy(scene: Int) = when (scene) { 0 -> "Personal conversations, brought forward."; 1 -> "Aether keeps the conversations that matter close, without turning your inbox into a feed."; else -> "Sign in to your existing Telegram account. Aether connects through Telegram and asks for permissions only when a feature needs them." }

@Composable
private fun OnboardingVisual(scene: Int) {
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        when (scene) {
            0 -> {
                Canvas(Modifier.fillMaxSize()) { drawCircle(brush = Brush.radialGradient(listOf(AetherAuthMist.copy(alpha = 0.24f), Color.Transparent)), radius = size.minDimension * 0.48f, center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)) }
                AetherBrandMark(82.dp, colors = listOf(AetherAuthMoon, AetherAuthMist, AetherAuthLavender, Color(0xFF4D4B68)))
            }
            1 -> PeopleVisual()
            else -> AetherBrandMark(82.dp, colors = listOf(AetherAuthMoon, AetherAuthMist, AetherAuthLavender, Color(0xFF4D4B68)))
        }
    }
}

@Composable
private fun PeopleVisual() {
    Canvas(Modifier.fillMaxSize()) {
        val points = listOf(androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.45f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.25f), androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.45f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.75f))
        points.forEach { point -> drawCircle(AetherAuthMist.copy(alpha = 0.78f), 22.dp.toPx(), point); drawCircle(DarkBackground.copy(alpha = 0.65f), 12.dp.toPx(), point) }
        drawLine(AetherAuthLavender.copy(alpha = 0.45f), points[0], points[1], 2.dp.toPx())
        drawLine(AetherAuthLavender.copy(alpha = 0.45f), points[1], points[2], 2.dp.toPx())
        drawLine(AetherAuthLavender.copy(alpha = 0.45f), points[0], points[3], 2.dp.toPx())
        drawLine(AetherAuthLavender.copy(alpha = 0.45f), points[2], points[3], 2.dp.toPx())
        drawCircle(Color.Transparent, size.minDimension * 0.43f, androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2), style = Stroke(1.dp.toPx()))
    }
    Icon(Icons.Outlined.PeopleAlt, contentDescription = null, tint = AetherAuthMist.copy(alpha = 0.9f), modifier = Modifier.size(48.dp))
}

@Composable
private fun OnboardingLight(scene: Int, reducedMotion: Boolean) {
    Canvas(Modifier.fillMaxSize().alpha(if (reducedMotion) 0.85f else 1f)) {
        val top = when (scene) { 0 -> 0.18f; 1 -> 0.28f; else -> 0.10f }
        drawCircle(brush = Brush.radialGradient(listOf(AetherAuthLavender.copy(alpha = 0.15f), Color.Transparent)), radius = size.width * 0.95f, center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * top))
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun OnboardingSceneOnePreview() = OnboardingScene(0, true, {}, {})
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun OnboardingSceneTwoPreview() = OnboardingScene(1, true, {}, {})
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun OnboardingSceneThreePreview() = OnboardingScene(2, true, {}, {})
