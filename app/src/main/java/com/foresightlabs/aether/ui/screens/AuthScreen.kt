package com.foresightlabs.aether.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.ui.auth.CountryDial
import com.foresightlabs.aether.ui.auth.CountryDials
import com.foresightlabs.aether.ui.components.AetherAtmosphericScreen
import com.foresightlabs.aether.ui.components.AetherBrandMark
import com.foresightlabs.aether.ui.components.isReducedMotionEnabled
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlin.math.roundToInt

enum class AuthIntentMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

@Immutable
private data class DustParticle(
    val xRatio: Float,
    val yRatio: Float,
    val radiusDp: Float,
    val alpha: Float,
    val speed: Float
)

/**
 * Modernized Aether Login / Authentication Screen.
 *
 * Layer 1: Full-screen continuous Living Atmosphere with subtle environmental drift & orbital motif
 * Layer 2: Branded identity with soft breathing emblem, headline typography & mode selector
 * Layer 3: Elevated dark-glass form card with smooth state transitions (Phone -> Code -> Password -> Registration)
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    busy: Boolean,
    error: String?,
    onSubmitPhone: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitPassword: (String) -> Unit,
    onRegister: (String, String) -> Unit,
    onResendCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val atmosphere = LocalAtmosphere.current

    var showCountryPicker by remember { mutableStateOf<((CountryDial) -> Unit)?>(null) }
    var intentMode by remember { mutableStateOf(AuthIntentMode.SIGN_IN) }

    // Staggered entry animation states
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }

    val entryAlpha by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "auth_entry_alpha"
    )
    val formTranslationY by animateFloatAsState(
        targetValue = if (hasEntered) 0f else 18f,
        animationSpec = tween(550, delayMillis = 60, easing = FastOutSlowInEasing),
        label = "auth_form_translate"
    )

    // Continuous ambient brand breathing & orbital rotation
    val infiniteTransition = rememberInfiniteTransition(label = "auth_ambient_motion")
    val orbitRotation by if (!reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(38000, easing = LinearEasing)
            ),
            label = "orbit_rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val breatheScale by if (!reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0.985f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(3800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "brand_breathe"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val particleDrift by if (!reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(24000, easing = LinearEasing)
            ),
            label = "particle_drift"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Remembered sparse atmospheric particles (zero per-frame allocations)
    val particles = remember {
        listOf(
            DustParticle(0.15f, 0.22f, 2.5f, 0.20f, 0.8f),
            DustParticle(0.82f, 0.18f, 3.0f, 0.16f, 1.1f),
            DustParticle(0.28f, 0.45f, 2.0f, 0.14f, 0.6f),
            DustParticle(0.74f, 0.58f, 3.5f, 0.18f, 0.9f),
            DustParticle(0.12f, 0.76f, 2.2f, 0.15f, 0.7f),
            DustParticle(0.88f, 0.82f, 2.8f, 0.22f, 1.0f),
            DustParticle(0.50f, 0.32f, 2.0f, 0.12f, 0.5f)
        )
    }

    AetherAtmosphericScreen(
        enableAmbientMotion = true,
        modifier = modifier.fillMaxSize()
    ) {
        // --- LAYER 1.5: SPARSE ATMOSPHERIC PARTICLES CANVAS ---
        if (!reducedMotion) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val accentColor = atmosphere.accent
                particles.forEach { p ->
                    val curY = ((p.yRatio + particleDrift * p.speed) % 1f) * h
                    val curX = (p.xRatio * w)
                    drawCircle(
                        color = accentColor.copy(alpha = p.alpha),
                        radius = p.radiusDp * density,
                        center = Offset(curX, curY)
                    )
                }
            }
        }

        // --- LAYER 2 & 3: FOREGROUND AUTH CONTENT ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .alpha(entryAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Brand Emblem with Atmospheric Orbital Motif
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(breatheScale),
                    contentAlignment = Alignment.Center
                ) {
                    // Thin Atmospheric Orbital Arcs
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radiusX = size.width * 0.44f
                        val radiusY = size.height * 0.28f

                        // Outer Arc 1
                        rotate(orbitRotation, center) {
                            drawOval(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x35FFA665),
                                        Color(0x10FF7038),
                                        Color.Transparent
                                    )
                                ),
                                topLeft = Offset(center.x - radiusX, center.y - radiusY),
                                size = androidx.compose.ui.geometry.Size(radiusX * 2, radiusY * 2),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }

                        // Inner Counter-Rotating Arc 2
                        rotate(-orbitRotation * 0.65f + 45f, center) {
                            drawOval(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0x28F04425),
                                        Color(0x10FFA665)
                                    )
                                ),
                                topLeft = Offset(center.x - radiusX * 0.85f, center.y - radiusY * 0.85f),
                                size = androidx.compose.ui.geometry.Size(radiusX * 1.7f, radiusY * 1.7f),
                                style = Stroke(width = 1.0.dp.toPx())
                            )
                        }
                    }

                    // Aether Brand Mark
                    AetherBrandMark(size = 56.dp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Brand Badge Pill
                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(Color(0x35000000))
                        .border(1.dp, Color(0x30FFFFFF), AetherEmber.Shapes.Pill)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AETHER",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.5.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // State-Specific Header Copy
                when (state) {
                    is AuthUiState.Registration -> {
                        Text(
                            text = "Create Profile",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Complete your Telegram profile details to start messaging.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    is AuthUiState.Code -> {
                        Text(
                            text = "Enter Verification Code",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A code was sent to your phone or active Telegram session.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center
                        )
                    }
                    is AuthUiState.Password -> {
                        Text(
                            text = "Two-Step Verification",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your Telegram account is protected with a cloud password.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            text = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) "Join Aether" else "Welcome to Aether",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (intentMode == AuthIntentMode.CREATE_ACCOUNT)
                                "Create your Telegram account within Aether's living atmosphere."
                            else
                                "Sign in with your phone number to connect your Telegram account.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sign In vs Create Account Toggle (on Phone step)
                if (state is AuthUiState.Phone) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AetherEmber.Shapes.Pill)
                            .background(Color(0x35000000))
                            .border(1.dp, Color(0x22FFFFFF), AetherEmber.Shapes.Pill)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(AetherEmber.Shapes.Pill)
                                .background(
                                    if (intentMode == AuthIntentMode.SIGN_IN) AetherAccent.current else Color.Transparent
                                )
                                .clickable { intentMode = AuthIntentMode.SIGN_IN },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = if (intentMode == AuthIntentMode.SIGN_IN) FontWeight.Bold else FontWeight.Medium,
                                color = if (intentMode == AuthIntentMode.SIGN_IN) Color.White else Color(0xBBFFFFFF)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(AetherEmber.Shapes.Pill)
                                .background(
                                    if (intentMode == AuthIntentMode.CREATE_ACCOUNT) AetherAccent.current else Color.Transparent
                                )
                                .clickable { intentMode = AuthIntentMode.CREATE_ACCOUNT },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Account",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) FontWeight.Bold else FontWeight.Medium,
                                color = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) Color.White else Color(0xBBFFFFFF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // --- LAYER 3: FLOATING DARK-GLASS FORM CONTAINER WITH FLUID TRANSITIONS ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, formTranslationY.roundToInt()) }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xF2121215))
                        .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 10 })
                                .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 10 })
                        },
                        label = "auth_state_transition"
                    ) { targetState ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (targetState) {
                                AuthUiState.Initializing, AuthUiState.LoggingOut, AuthUiState.Closing -> {
                                    StatusBlock(if (targetState is AuthUiState.LoggingOut) "Signing out…" else "Connecting to Telegram…")
                                }
                                AuthUiState.MissingCredentials -> {
                                    Text(
                                        text = "Developer Credentials Needed",
                                        fontFamily = SpaceGroteskFontFamily,
                                        color = Color.White,
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Add TELEGRAM_API_ID and TELEGRAM_API_HASH to local.properties to enable Telegram connection.",
                                        fontFamily = ManropeFontFamily,
                                        color = Color(0xAAFFFFFF),
                                        fontSize = 13.sp,
                                        lineHeight = 18.5.sp
                                    )
                                }
                                is AuthUiState.Phone -> PhoneStep(
                                    intentMode = intentMode,
                                    busy = busy || targetState.isLoading,
                                    error = error ?: targetState.error,
                                    onSubmit = onSubmitPhone,
                                    onPickCountry = { onSelect -> showCountryPicker = onSelect }
                                )
                                is AuthUiState.Code -> CodeStep(
                                    state = targetState,
                                    busy = busy || targetState.isLoading,
                                    error = error ?: targetState.error,
                                    onSubmit = onSubmitCode,
                                    onResend = onResendCode
                                )
                                is AuthUiState.Password -> PasswordStep(
                                    state = targetState,
                                    busy = busy || targetState.isLoading,
                                    error = error ?: targetState.error,
                                    onSubmit = onSubmitPassword
                                )
                                is AuthUiState.Registration -> RegisterStep(
                                    state = targetState,
                                    busy = busy || targetState.isLoading,
                                    error = error ?: targetState.error,
                                    onRegister = onRegister
                                )
                                is AuthUiState.OtherDevice -> {
                                    Text(
                                        text = "Confirm in Another Telegram Session",
                                        fontFamily = SpaceGroteskFontFamily,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Open Telegram on your other device and confirm the login notification.",
                                        fontFamily = ManropeFontFamily,
                                        color = Color(0xCCFFFFFF),
                                        fontSize = 13.sp
                                    )
                                    if (targetState.link.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = targetState.link,
                                            fontFamily = ManropeFontFamily,
                                            color = AetherAccent.current,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                is AuthUiState.Unsupported -> Text(
                                    text = targetState.description,
                                    fontFamily = ManropeFontFamily,
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 14.sp
                                )
                                AuthUiState.Ready -> StatusBlock("Opening Aether…")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Footer Note with 100% truthful architecture copy
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Aether • High-Performance Telegram Client",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0x99FFFFFF),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Connected directly to official Telegram servers • MTProto protocol",
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.5.sp,
                        color = Color(0x70FFFFFF),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Country Picker Modal Bottom Sheet
            showCountryPicker?.let { onSelect ->
                CountryPicker(
                    onSelect = {
                        onSelect(it)
                        showCountryPicker = null
                    },
                    onDismiss = { showCountryPicker = null }
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = AetherAccent.current,
            modifier = Modifier.size(30.dp),
            strokeWidth = 2.5.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = text,
            fontFamily = ManropeFontFamily,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PhoneStep(
    intentMode: AuthIntentMode,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onPickCountry: ((CountryDial) -> Unit) -> Unit
) {
    var country by remember { mutableStateOf(CountryDials.list.first { it.iso == "US" }) }
    var national by remember { mutableStateOf("") }

    Text(
        text = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) "PHONE NUMBER FOR NEW ACCOUNT" else "PHONE NUMBER",
        fontFamily = ManropeFontFamily,
        color = Color(0x85FFFFFF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Country Selector Pill
        Box(
            modifier = Modifier
                .width(116.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x18FFFFFF))
                .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
                .clickable { onPickCountry { country = it } }
                .padding(horizontal = 12.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${country.flag} +${country.dial}",
                    fontFamily = ManropeFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select country",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Phone Input Field
        FieldCapsule(
            modifier = Modifier.weight(1f),
            value = national,
            onValueChange = { national = it.filter { ch -> ch.isDigit() } },
            keyboardType = KeyboardType.Phone,
            placeholder = "Phone number"
        )
    }

    ErrorBanner(error)
    Spacer(modifier = Modifier.height(20.dp))
    ModernPrimaryButton(
        enabled = !busy && national.isNotBlank(),
        busy = busy,
        label = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) "Continue to Register" else "Continue"
    ) {
        onSubmit("+${country.dial}$national")
    }
}

@Composable
private fun CodeStep(
    state: AuthUiState.Code,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Text(
        text = state.hint,
        fontFamily = ManropeFontFamily,
        color = Color(0xCCFFFFFF),
        fontSize = 13.5.sp
    )
    if (state.phoneNumber.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.phoneNumber,
            fontFamily = ManropeFontFamily,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    FieldCapsule(
        value = code,
        onValueChange = { incoming ->
            val digits = incoming.filter { it.isDigit() }
            val limited = state.codeLength?.let { digits.take(it) } ?: digits
            code = limited
        },
        keyboardType = KeyboardType.Number,
        placeholder = if (state.codeLength != null) "Code (${state.codeLength} digits)" else "Code"
    )
    ErrorBanner(error)
    Spacer(modifier = Modifier.height(20.dp))
    ModernPrimaryButton(
        enabled = !busy && code.isNotBlank(),
        busy = busy,
        label = "Verify Code"
    ) {
        onSubmit(code)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = onResend,
            enabled = !busy
        ) {
            Text(
                text = "Resend Code",
                fontFamily = ManropeFontFamily,
                color = AetherAccent.current,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp
            )
        }
    }
}

@Composable
private fun PasswordStep(
    state: AuthUiState.Password,
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Text(
        text = "Enter Cloud Password",
        fontFamily = SpaceGroteskFontFamily,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    state.hint?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hint: $it",
            fontFamily = ManropeFontFamily,
            color = Color(0x95FFFFFF),
            fontSize = 13.sp
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
    FieldCapsule(
        value = password,
        onValueChange = { password = it },
        keyboardType = KeyboardType.Password,
        placeholder = "Cloud Password",
        password = !showPassword,
        trailingIcon = {
            IconButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
    ErrorBanner(error)
    Spacer(modifier = Modifier.height(20.dp))
    ModernPrimaryButton(
        enabled = !busy && password.isNotBlank(),
        busy = busy,
        label = "Unlock Account"
    ) {
        onSubmit(password)
    }
}

@Composable
private fun RegisterStep(
    state: AuthUiState.Registration,
    busy: Boolean,
    error: String?,
    onRegister: (String, String) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }

    Text(
        text = "YOUR PROFILE DETAILS",
        fontFamily = ManropeFontFamily,
        color = Color(0x85FFFFFF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(12.dp))

    FieldCapsule(value = first, onValueChange = { first = it }, placeholder = "First name (required)")
    Spacer(modifier = Modifier.height(10.dp))
    FieldCapsule(value = last, onValueChange = { last = it }, placeholder = "Last name (optional)")

    // Terms of service block
    if (!state.termsOfServiceText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x18FFFFFF))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Terms",
                        tint = AetherAccent.current,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Telegram Terms of Service",
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.termsOfServiceText,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.5.sp,
                    color = Color(0xCCFFFFFF),
                    lineHeight = 16.sp,
                    maxLines = 4
                )
            }
        }
    }

    if (state.minAge > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "By continuing, you confirm you are at least ${state.minAge} years old.",
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            color = Color(0x80FFFFFF)
        )
    }

    ErrorBanner(error)
    Spacer(modifier = Modifier.height(20.dp))
    ModernPrimaryButton(
        enabled = !busy && first.isNotBlank(),
        busy = busy,
        label = "Complete Registration"
    ) {
        onRegister(first, last)
    }
}

@Composable
private fun FieldCapsule(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = ManropeFontFamily,
                        color = Color(0x75FFFFFF),
                        fontSize = 14.5.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = readOnly,
                    enabled = enabled,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AetherAccent.current),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
private fun ModernPrimaryButton(
    enabled: Boolean,
    busy: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(AetherEmber.Shapes.Pill)
            .background(
                if (enabled) AetherAccent.actionBrush else SolidColor(Color(0x24FFFFFF))
            )
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = label,
                fontFamily = ManropeFontFamily,
                color = if (enabled) Color.White else Color(0x60FFFFFF),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: String?) {
    if (error.isNullOrBlank()) return
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x25EF4444))
            .border(0.5.dp, Color(0x50EF4444), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = Color(0xFFFF7A7A),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = error,
            fontFamily = ManropeFontFamily,
            color = Color(0xFFFFD4D4),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CountryPicker(
    onSelect: (CountryDial) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        CountryDials.list.filter {
            it.name.contains(query, ignoreCase = true) || it.dial.contains(query)
        }
    }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) {}
            .statusBarsPadding()
            .padding(top = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(AetherEmber.Shapes.RisingSheet)
                .background(Color(0xF6121215))
                .border(1.dp, Color(0x22FFFFFF), AetherEmber.Shapes.RisingSheet)
                .padding(top = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Country",
                    fontFamily = SpaceGroteskFontFamily,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x18FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0x80FFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search country or dial code",
                                fontFamily = ManropeFontFamily,
                                color = Color(0x75FFFFFF),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(AetherAccent.current),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                }
            }

            // Country List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.iso }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.flag,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 14.dp)
                            )
                            Text(
                                text = item.name,
                                fontFamily = ManropeFontFamily,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${item.dial}",
                                fontFamily = ManropeFontFamily,
                                color = AetherAccent.current,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = 0.5.dp,
                        color = Color(0x12FFFFFF)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
