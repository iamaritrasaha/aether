package com.foresightlabs.aether.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.ui.auth.CountryDial
import com.foresightlabs.aether.ui.auth.CountryDials
import com.foresightlabs.aether.ui.components.AetherBrandMark
import com.foresightlabs.aether.ui.components.isReducedMotionEnabled
import com.foresightlabs.aether.ui.theme.AetherAuthLavender
import com.foresightlabs.aether.ui.theme.AetherAuthMist
import com.foresightlabs.aether.ui.theme.AetherAuthMoon
import com.foresightlabs.aether.ui.theme.DarkBackground
import com.foresightlabs.aether.ui.theme.DarkBorder
import com.foresightlabs.aether.ui.theme.DarkSurface
import com.foresightlabs.aether.ui.theme.DarkSurfaceElevated
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

private val AuthRaised = Color(0xFF181A21)
private val AuthText = Color(0xFFF2F0F7)
private val AuthSecondary = Color(0xFFB1AFC0)
private val AuthMuted = Color(0xFF858296)
private val AuthShape = RoundedCornerShape(24.dp)

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
    modifier: Modifier = Modifier,
    onRequestQrCode: () -> Unit = {},
    onSubmitEmailAddress: (String) -> Unit = {},
    onSubmitEmailCode: (String) -> Unit = {},
    onResetEmailAddress: () -> Unit = {},
    onRequestPasswordRecovery: () -> Unit = {},
    onUsePasskey: (android.content.Context) -> Unit = {},
    passwordRecoveryRequested: Boolean = false,
    initialPhoneEntry: Boolean = false
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    var showPhoneEntry by remember { mutableStateOf(initialPhoneEntry) }
    var showCountries by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(CountryDials.list.first { it.iso == "US" }) }

    LaunchedEffect(state) {
        if (state !is AuthUiState.Phone && state !is AuthUiState.Initializing) showPhoneEntry = true
    }
    BackHandler(enabled = state is AuthUiState.Phone && showPhoneEntry) { showPhoneEntry = false }

    val isLanding = state is AuthUiState.Phone && !showPhoneEntry
    val upperHeight by animateFloatAsState(
        targetValue = if (isLanding || state is AuthUiState.Initializing) 0.47f else 0.39f,
        animationSpec = tween(if (reducedMotion) 0 else 650, easing = FastOutSlowInEasing),
        label = "auth_foreground_height"
    )
    val entryAlpha by animateFloatAsState(
        targetValue = if (state is AuthUiState.Initializing) 0.78f else 1f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "auth_content_alpha"
    )

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        AuthRoomLight(reducedMotion)
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxSize(upperHeight).clip(RoundedCornerShape(bottomStart = 42.dp, bottomEnd = 42.dp)).background(
                Brush.verticalGradient(listOf(AetherAuthLavender.copy(alpha = 0.62f), AetherAuthLavender.copy(alpha = 0.24f), DarkBackground.copy(alpha = 0.18f)))
            )
        ) { AuthIdentity(state, entryAlpha, reducedMotion) }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (isLanding) 300.dp else 230.dp))
            AnimatedContent(
                targetState = authContentKey(state, showPhoneEntry, passwordRecoveryRequested),
                transitionSpec = {
                    if (reducedMotion) fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                    else (fadeIn(tween(360)) + slideInVertically(tween(420)) { it / 12 }) togetherWith (fadeOut(tween(220)) + slideOutVertically(tween(300)) { -it / 18 })
                }, label = "auth_room_transform"
            ) {
                AuthContent(state, showPhoneEntry, busy, error, selectedCountry, { showCountries = true }, { showPhoneEntry = true }, onRequestQrCode, { onUsePasskey(context) }, onSubmitPhone, onSubmitCode, onSubmitPassword, onRegister, onResendCode, onSubmitEmailAddress, onSubmitEmailCode, onResetEmailAddress, onRequestPasswordRecovery, passwordRecoveryRequested)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (state is AuthUiState.MissingCredentials && !com.foresightlabs.aether.BuildConfig.DEBUG) "Aether is not available right now." else "A Foresight Labs product",
                color = AuthMuted, fontFamily = ManropeFontFamily, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
    if (showCountries) CountryPicker(selectedCountry, { selectedCountry = it; showCountries = false }, { showCountries = false })
}

@Composable
private fun AuthRoomLight(reducedMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "auth_moonlight")
    val drift by if (reducedMotion) remember { androidx.compose.runtime.mutableFloatStateOf(0f) } else transition.animateFloat(-0.04f, 0.04f, infiniteRepeatable(tween(14000), RepeatMode.Reverse), label = "auth_light_drift")
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * (0.32f + drift), size.height * 0.12f)
        val radius = size.width * 0.95f
        drawCircle(brush = Brush.radialGradient(listOf(AetherAuthMist.copy(alpha = 0.24f), AetherAuthLavender.copy(alpha = 0.08f), Color.Transparent), center, radius), radius = radius, center = center)
        drawCircle(AetherAuthMoon.copy(alpha = 0.12f), 2.dp.toPx(), Offset(size.width * 0.18f, size.height * 0.24f))
        drawCircle(AetherAuthMist.copy(alpha = 0.10f), 1.5.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.30f))
    }
}

@Composable
private fun AuthIdentity(state: AuthUiState, alpha: Float, reducedMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "auth_mark_breath")
    val breathe by if (reducedMotion) remember { androidx.compose.runtime.mutableFloatStateOf(1f) } else transition.animateFloat(0.985f, 1.015f, infiniteRepeatable(tween(3600), RepeatMode.Reverse), label = "auth_mark_scale")
    Column(Modifier.fillMaxSize().alpha(alpha).padding(horizontal = 24.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AetherBrandMark(size = 64.dp, modifier = Modifier.scale(breathe), colors = listOf(AetherAuthMoon, AetherAuthMist, AetherAuthLavender, Color(0xFF4D4B68)))
        Spacer(Modifier.height(12.dp))
        Text("AETHER", color = AuthText, fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(18.dp))
        Text(authHeadline(state), color = AuthText, fontFamily = SpaceGroteskFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(authSupportingCopy(state), color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 340.dp))
    }
}

private fun authHeadline(state: AuthUiState): String = when (state) {
    AuthUiState.Initializing -> "Preparing Aether…"
    AuthUiState.MissingCredentials -> "Welcome to Aether"
    is AuthUiState.Code -> "Check Telegram"
    is AuthUiState.Password -> if (state.recoveryEmailAddressPattern?.isNotBlank() == true) "Recover access" else "Two-Step Verification"
    is AuthUiState.EmailAddress -> "Verify your email"
    is AuthUiState.EmailCode -> "Check your email"
    is AuthUiState.Registration -> "Complete your profile"
    is AuthUiState.OtherDevice -> "Sign in with QR"
    is AuthUiState.Unsupported -> "Aether needs another sign-in step"
    else -> "Welcome to Aether"
}

private fun authSupportingCopy(state: AuthUiState): String = when (state) {
    AuthUiState.Initializing -> "Your quiet Telegram space is getting ready."
    AuthUiState.MissingCredentials -> "Aether is not configured for this release."
    is AuthUiState.Code -> state.hint.ifBlank { "Enter the code Telegram sent you." }
    is AuthUiState.Password -> if (state.recoveryEmailAddressPattern?.isNotBlank() == true) "Enter the recovery code sent to your email." else "Your Telegram account is protected with a cloud password."
    is AuthUiState.EmailAddress -> "Telegram needs an email address to continue this sign-in."
    is AuthUiState.EmailCode -> "Enter the code Telegram sent to ${state.addressPattern.ifBlank { "your email" }}."
    is AuthUiState.Registration -> "Finishing registration accepts Telegram's supplied terms."
    is AuthUiState.OtherDevice -> "Open Telegram on a signed-in device to scan this code."
    is AuthUiState.Unsupported -> "Aether can't complete this sign-in method yet."
    else -> "Your Telegram conversations, in a quieter place."
}

private fun authContentKey(state: AuthUiState, phone: Boolean, recovery: Boolean): String = when {
    state is AuthUiState.Phone && !phone -> "landing"
    state is AuthUiState.Phone -> "phone"
    state is AuthUiState.Password && recovery -> "password-recovery"
    state is AuthUiState.Password -> "password"
    state is AuthUiState.EmailAddress -> "email-address"
    state is AuthUiState.EmailCode -> "email-code"
    state is AuthUiState.Registration -> "registration"
    state is AuthUiState.OtherDevice -> "qr"
    state is AuthUiState.Code -> "code"
    state is AuthUiState.Initializing -> "initializing"
    else -> "unsupported"
}

@Composable
private fun AuthContent(state: AuthUiState, showPhoneEntry: Boolean, busy: Boolean, error: String?, country: CountryDial, onCountryClick: () -> Unit, onChoosePhone: () -> Unit, onRequestQrCode: () -> Unit, onUsePasskey: () -> Unit, onSubmitPhone: (String) -> Unit, onSubmitCode: (String) -> Unit, onSubmitPassword: (String) -> Unit, onRegister: (String, String) -> Unit, onResendCode: () -> Unit, onSubmitEmailAddress: (String) -> Unit, onSubmitEmailCode: (String) -> Unit, onResetEmailAddress: () -> Unit, onRequestPasswordRecovery: () -> Unit, passwordRecoveryRequested: Boolean) {
    when {
        state is AuthUiState.Phone && !showPhoneEntry -> LandingChoices(busy, onChoosePhone, onRequestQrCode, onUsePasskey)
        state is AuthUiState.Phone -> PhoneStep(busy, error, country, onCountryClick, onSubmitPhone)
        state is AuthUiState.Code -> CodeStep(state, busy, error, onSubmitCode, onResendCode)
        state is AuthUiState.Password -> PasswordStep(state, passwordRecoveryRequested, busy, error, onSubmitPassword, onRequestPasswordRecovery)
        state is AuthUiState.EmailAddress -> EmailAddressStep(busy, error, onSubmitEmailAddress)
        state is AuthUiState.EmailCode -> EmailCodeStep(state, busy, error, onSubmitEmailCode, onResendCode, onResetEmailAddress)
        state is AuthUiState.Registration -> RegistrationStep(state, busy, error, onRegister)
        state is AuthUiState.OtherDevice -> QrStep(state, busy)
        state is AuthUiState.Initializing -> PreparingStep()
        state is AuthUiState.MissingCredentials -> ConfigurationStep()
        state is AuthUiState.Unsupported -> UnsupportedStep()
    }
}

@Composable
private fun LandingChoices(busy: Boolean, onChoosePhone: () -> Unit, onRequestQrCode: () -> Unit, onUsePasskey: () -> Unit) {
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthChoice("Continue with phone", Icons.Outlined.Phone, busy, onChoosePhone)
        AuthChoice("Sign in with QR code", Icons.Outlined.QrCode2, busy, onRequestQrCode)
        AuthChoice("Use a passkey", Icons.Outlined.Lock, busy, onUsePasskey)
    }
}

@Composable
private fun AuthChoice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, busy: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).clip(AuthShape).background(AuthRaised.copy(alpha = 0.92f)).border(1.dp, DarkBorder, AuthShape).clickable(enabled = !busy, onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AetherAuthMist)
        Spacer(Modifier.width(14.dp))
        Text(label, color = AuthText, fontFamily = ManropeFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PhoneStep(busy: Boolean, error: String?, country: CountryDial, onCountryClick: () -> Unit, onSubmit: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your phone number", color = AuthText, fontFamily = SpaceGroteskFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Telegram will decide whether this is an existing account or a new registration.", color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldSurface(Modifier.width(112.dp).height(58.dp), onCountryClick) { Text("${country.flag}  +${country.dial}", color = AuthText, fontSize = 14.sp) }
            InputSurface(number, { number = it.filter { char -> char.isDigit() || char == ' ' || char == '-' } }, "Phone number", KeyboardType.Phone, ImeAction.Done, { onSubmit("+${country.dial}${number.filter(Char::isDigit)}") }, modifier = Modifier.weight(1f))
        }
        PrimaryAuthButton("Continue", busy) { onSubmit("+${country.dial}${number.filter(Char::isDigit)}") }
        AuthError(error)
    }
}

@Composable
private fun CodeStep(state: AuthUiState.Code, busy: Boolean, error: String?, onSubmit: (String) -> Unit, onResend: () -> Unit) {
    var code by remember { mutableStateOf("") }
    CodeEntry("Verification code", state.hint.ifBlank { "Enter the code Telegram sent you." }, code, state.codeLength, busy, error, { value -> code = (if (state.isNumeric) value.filter(Char::isDigit) else value.trim()) .let { if (state.codeLength == null) it else it.take(state.codeLength) } }, { onSubmit(code) }, onResend, if (state.isNumeric) KeyboardType.Number else KeyboardType.Text)
}

@Composable
private fun EmailAddressStep(busy: Boolean, error: String?, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Email address", color = AuthText, fontFamily = SpaceGroteskFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Telegram uses this address to protect this sign-in.", color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp)
        InputSurface(email, { email = it }, "name@example.com", KeyboardType.Email, ImeAction.Done, { onSubmit(email) })
        PrimaryAuthButton("Continue", busy) { onSubmit(email) }
        AuthError(error)
    }
}

@Composable
private fun EmailCodeStep(state: AuthUiState.EmailCode, busy: Boolean, error: String?, onSubmit: (String) -> Unit, onResend: () -> Unit, onReset: () -> Unit) {
    var code by remember { mutableStateOf("") }
    CodeEntry("Email verification code", "Telegram sent a code to ${state.addressPattern.ifBlank { "your email" }}.", code, state.codeLength, busy, error, { value -> code = value.filter(Char::isDigit).let { state.codeLength?.let(it::take) ?: it } }, { onSubmit(code) }, onResend)
    if (state.canReset) TextButton(onClick = onReset, modifier = Modifier.height(48.dp)) { Text("Use phone instead", color = AuthSecondary) }
}

@Composable
private fun CodeEntry(title: String, supporting: String, code: String, length: Int?, busy: Boolean, error: String?, onCodeChange: (String) -> Unit, onSubmit: () -> Unit, resend: () -> Unit, keyboardType: KeyboardType = KeyboardType.Number) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = AuthText, fontFamily = SpaceGroteskFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(supporting, color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (length != null && length > 0) repeat(length) { index -> Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(AuthRaised).border(1.dp, if (index == code.length) AetherAuthLavender else DarkBorder, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(code.getOrNull(index)?.toString().orEmpty(), color = AuthText, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
                else Text("Enter the code", color = AuthMuted, fontSize = 15.sp)
            }
            BasicTextField(value = code, onValueChange = onCodeChange, modifier = Modifier.fillMaxWidth().height(62.dp).focusRequester(focusRequester).alpha(0.02f).semantics { contentDescription = "$title. ${length ?: "Adaptive"} characters." }, textStyle = TextStyle(color = AuthText, fontSize = 20.sp, textAlign = TextAlign.Center), cursorBrush = SolidColor(AetherAuthMist), keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onSubmit() }), singleLine = true)
        }
        PrimaryAuthButton("Continue", busy, code.isNotEmpty()) { onSubmit() }
        TextButton(onClick = resend, modifier = Modifier.height(48.dp), enabled = !busy) { Text("Resend when Telegram allows it", color = AuthSecondary, fontSize = 13.sp) }
        AuthError(error)
    }
}

@Composable
private fun PasswordStep(state: AuthUiState.Password, recoveryRequested: Boolean, busy: Boolean, error: String?, onSubmit: (String) -> Unit, onRequestRecovery: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val recovery = recoveryRequested || state.recoveryEmailAddressPattern?.isNotBlank() == true
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (recovery) "Recovery code" else "Password", color = AuthText, fontFamily = SpaceGroteskFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        state.hint?.takeIf { !recovery && it.isNotBlank() }?.let { Text("Hint: $it", color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp) }
        state.recoveryEmailAddressPattern?.takeIf { recovery && it.isNotBlank() }?.let { Text("Telegram sent a recovery code to $it.", color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp) }
        InputSurface(password, { password = it }, if (recovery) "Recovery code" else "Password", if (recovery) KeyboardType.NumberPassword else KeyboardType.Password, ImeAction.Done, { onSubmit(password) }, if (visible || recovery) VisualTransformation.None else PasswordVisualTransformation(), if (!recovery) ({ IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show or hide password", tint = AuthSecondary) } }) else null)
        PrimaryAuthButton("Continue", busy, password.isNotEmpty()) { onSubmit(password) }
        if (!recovery && state.hasRecoveryEmailAddress) TextButton(onClick = onRequestRecovery, enabled = !busy, modifier = Modifier.height(48.dp)) { Text("Forgot password?", color = AuthSecondary) }
        AuthError(error)
    }
}

@Composable
private fun RegistrationStep(state: AuthUiState.Registration, busy: Boolean, error: String?, onRegister: (String, String) -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(!state.showPopup) }
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InputSurface(firstName, { firstName = it }, "First name", KeyboardType.Text, ImeAction.Next)
        InputSurface(lastName, { lastName = it }, "Last name", KeyboardType.Text, ImeAction.Done, { onRegister(firstName, lastName) })
        state.minAge.takeIf { it > 0 }?.let { Text("You must be at least $it to continue.", color = AuthSecondary, fontSize = 13.sp) }
        state.termsOfServiceText?.takeIf { it.isNotBlank() }?.let { terms -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AuthRaised.copy(alpha = 0.76f)).padding(14.dp), verticalAlignment = Alignment.Top) { IconButton(onClick = { accepted = !accepted }, Modifier.size(48.dp)) { Icon(if (accepted) Icons.Outlined.Check else Icons.Outlined.Close, "Accept Telegram terms", tint = if (accepted) AetherAuthMist else AuthMuted) }; Text(terms, color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp)) } }
        PrimaryAuthButton("Finish registration", busy, firstName.isNotBlank() && accepted) { onRegister(firstName, lastName) }
        AuthError(error)
    }
}

@Composable
private fun QrStep(state: AuthUiState.OtherDevice, busy: Boolean) {
    Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.link.takeIf { it.isNotBlank() }?.let { QrCodeImage(it) } ?: Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) { Text("Preparing code…", color = AuthSecondary) }
        Text("Open Telegram on a device where you're already signed in.", color = AuthText, fontFamily = ManropeFontFamily, fontSize = 14.sp, textAlign = TextAlign.Center)
        Text("Settings → Devices → Scan QR", color = AuthSecondary, fontFamily = ManropeFontFamily, fontSize = 13.sp, textAlign = TextAlign.Center)
        Text("Then scan this code.", color = AuthMuted, fontFamily = ManropeFontFamily, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (busy) Text("Waiting for confirmation…", color = AuthMuted, fontSize = 12.sp)
    }
}

@Composable
private fun QrCodeImage(link: String) {
    val bitmap = remember(link) { createQrBitmap(link) }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Telegram sign-in QR code. Scan it from Telegram Settings, Devices, Scan QR.", Modifier.size(236.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF2F0F2)).padding(14.dp)) else Text("This sign-in code could not be rendered.", color = AuthSecondary, textAlign = TextAlign.Center)
}

private fun createQrBitmap(value: String): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap -> for (y in 0 until matrix.height) for (x in 0 until matrix.width) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) }
}.getOrNull()

@Composable private fun PreparingStep() = Text("Aether is opening a quiet room for your conversations.", color = AuthSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
@Composable private fun ConfigurationStep() = Text("Aether is not configured for this release.", color = AuthSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
@Composable private fun UnsupportedStep() = Text("Aether can't complete this sign-in method yet.", color = AuthSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))

@Composable
private fun InputSurface(value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType, imeAction: ImeAction, onImeAction: () -> Unit = {}, visualTransformation: VisualTransformation = VisualTransformation.None, trailing: (@Composable (() -> Unit))? = null, modifier: Modifier = Modifier.fillMaxWidth()) {
    Row(modifier.height(58.dp).clip(AuthShape).background(DarkSurfaceElevated.copy(alpha = 0.92f)).border(1.dp, DarkBorder, AuthShape).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), textStyle = TextStyle(color = AuthText, fontFamily = ManropeFontFamily, fontSize = 15.sp), cursorBrush = SolidColor(AetherAuthMist), keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction), keyboardActions = KeyboardActions(onAny = { onImeAction() }), visualTransformation = visualTransformation, singleLine = true, decorationBox = { innerTextField -> if (value.isEmpty()) Text(placeholder, color = AuthMuted, fontFamily = ManropeFontFamily, fontSize = 15.sp); innerTextField() })
        trailing?.invoke()
    }
}

@Composable private fun FieldSurface(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) = Box(modifier.clip(AuthShape).background(DarkSurfaceElevated.copy(alpha = 0.92f)).border(1.dp, DarkBorder, AuthShape).clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { content() }
@Composable private fun PrimaryAuthButton(label: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) = Button(onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = AuthShape, colors = ButtonDefaults.buttonColors(containerColor = AetherAuthLavender, contentColor = Color(0xFF12121A), disabledContainerColor = AetherAuthLavender.copy(alpha = 0.32f))) { Text(if (busy) "Working…" else label, fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold) }
@Composable private fun AuthError(error: String?) { error?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFFFA9B2), fontFamily = ManropeFontFamily, fontSize = 13.sp, textAlign = TextAlign.Center) } }

@Composable
private fun CountryPicker(selected: CountryDial, onSelect: (CountryDial) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = DarkSurface, title = { Text("Choose country", color = AuthText, fontFamily = SpaceGroteskFontFamily) }, text = { LazyColumn(Modifier.height(380.dp)) { items(CountryDials.list) { country -> Row(Modifier.fillMaxWidth().height(52.dp).clickable { onSelect(country) }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(country.flag, fontSize = 20.sp); Spacer(Modifier.width(12.dp)); Text(country.name, modifier = Modifier.weight(1f), color = AuthText); Text("+${country.dial}", color = AuthSecondary) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = AuthSecondary) } })
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthLandingPreview() = AuthPreview(AuthUiState.Phone())
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthQrPreview() = AuthPreview(AuthUiState.OtherDevice("tg://login?token=preview"))
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthPhonePreview() = AuthPreview(AuthUiState.Phone(), initialPhoneEntry = true)
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthCodePreview() = AuthPreview(AuthUiState.Code("+1 •••• 42", 5, "Check Telegram on your other device."))
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthPasswordPreview() = AuthPreview(AuthUiState.Password("A familiar word", hasRecoveryEmailAddress = true))
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthEmailAddressPreview() = AuthPreview(AuthUiState.EmailAddress())
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthEmailCodePreview() = AuthPreview(AuthUiState.EmailCode("m•••@example.com", 6))
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthRegistrationPreview() = AuthPreview(AuthUiState.Registration("Telegram terms supplied by Telegram.", 16, true))
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthInitializingPreview() = AuthPreview(AuthUiState.Initializing)
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun AuthUnsupportedPreview() = AuthPreview(AuthUiState.Unsupported("Aether can't complete this sign-in method yet."))
@Composable private fun AuthPreview(state: AuthUiState, initialPhoneEntry: Boolean = false) = AuthScreen(state, false, null, {}, {}, {}, { _, _ -> }, {}, onRequestQrCode = {}, initialPhoneEntry = initialPhoneEntry)
