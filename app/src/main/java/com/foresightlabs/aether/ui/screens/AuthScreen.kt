package com.foresightlabs.aether.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.ui.auth.CountryDial
import com.foresightlabs.aether.ui.auth.CountryDials
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherBrandMark
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

enum class AuthIntentMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

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
    var showCountryPicker by remember { mutableStateOf<((CountryDial) -> Unit)?>(null) }
    var intentMode by remember { mutableStateOf(AuthIntentMode.SIGN_IN) }

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroOnly = false
    ) {
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
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Brand Mark Emblem
                AetherBrandMark(size = 54.dp)

                Spacer(modifier = Modifier.height(12.dp))

                // Brand Pill Tag
                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(Color(0x35000000))
                        .border(1.dp, Color(0x30FFFFFF), AetherEmber.Shapes.Pill)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "AETHER",
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Header based on state
                when (state) {
                    is AuthUiState.Registration -> {
                        Text(
                            text = "Create Account",
                            fontFamily = ManropeFontFamily,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Complete your Telegram profile to start using Aether.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    is AuthUiState.Code -> {
                        Text(
                            text = "Enter Verification Code",
                            fontFamily = ManropeFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "We sent a code to your phone or Telegram session.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center
                        )
                    }
                    is AuthUiState.Password -> {
                        Text(
                            text = "Two-Step Verification",
                            fontFamily = ManropeFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your account is protected with a cloud password.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = Color(0xD8FFFFFF),
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            text = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) "Join Aether" else "Sign in to Aether",
                            fontFamily = ManropeFontFamily,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (intentMode == AuthIntentMode.CREATE_ACCOUNT)
                                "Create a new Telegram account with luminous atmospheric design."
                            else
                                "Connect your Telegram account to experience high-performance messaging.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = Color(0xD8FFFFFF),
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Mode Selector Capsule (Sign In vs Create Account) when on phone step
                if (state is AuthUiState.Phone) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AetherEmber.Shapes.Pill)
                            .background(Color(0x30000000))
                            .border(1.dp, Color(0x22FFFFFF), AetherEmber.Shapes.Pill)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(AetherEmber.Shapes.Pill)
                                .background(
                                    if (intentMode == AuthIntentMode.SIGN_IN) AetherEmber.Colors.Accent else Color.Transparent
                                )
                                .clickable { intentMode = AuthIntentMode.SIGN_IN },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                fontWeight = if (intentMode == AuthIntentMode.SIGN_IN) FontWeight.Bold else FontWeight.Medium,
                                color = if (intentMode == AuthIntentMode.SIGN_IN) Color.White else Color(0xBBFFFFFF)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(AetherEmber.Shapes.Pill)
                                .background(
                                    if (intentMode == AuthIntentMode.CREATE_ACCOUNT) AetherEmber.Colors.Accent else Color.Transparent
                                )
                                .clickable { intentMode = AuthIntentMode.CREATE_ACCOUNT },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Account",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                fontWeight = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) FontWeight.Bold else FontWeight.Medium,
                                color = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) Color.White else Color(0xBBFFFFFF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Main Form Card (32dp rounded near-black container)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AetherEmber.Shapes.XL)
                        .background(AetherEmber.Colors.Surface)
                        .border(1.dp, Color(0x22FFFFFF), AetherEmber.Shapes.XL)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (state) {
                            AuthUiState.Initializing, AuthUiState.LoggingOut, AuthUiState.Closing -> {
                                StatusBlock(if (state is AuthUiState.LoggingOut) "Signing out…" else "Connecting to Telegram…")
                            }
                            AuthUiState.MissingCredentials -> {
                                Text(
                                    text = "Developer credentials needed",
                                    fontFamily = ManropeFontFamily,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add TELEGRAM_API_ID and TELEGRAM_API_HASH to local.properties.",
                                    fontFamily = ManropeFontFamily,
                                    color = AetherEmber.Colors.TextSecondary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp
                                )
                            }
                            is AuthUiState.Phone -> PhoneStep(
                                intentMode = intentMode,
                                busy = busy || state.isLoading,
                                error = error ?: state.error,
                                onSubmit = onSubmitPhone,
                                onPickCountry = { onSelect -> showCountryPicker = onSelect }
                            )
                            is AuthUiState.Code -> CodeStep(
                                state = state,
                                busy = busy || state.isLoading,
                                error = error ?: state.error,
                                onSubmit = onSubmitCode,
                                onResend = onResendCode
                            )
                            is AuthUiState.Password -> PasswordStep(
                                state = state,
                                busy = busy || state.isLoading,
                                error = error ?: state.error,
                                onSubmit = onSubmitPassword
                            )
                            is AuthUiState.Registration -> RegisterStep(
                                state = state,
                                busy = busy || state.isLoading,
                                error = error ?: state.error,
                                onRegister = onRegister
                            )
                            is AuthUiState.OtherDevice -> {
                                Text(
                                    "Confirm this login in another Telegram session.",
                                    fontFamily = ManropeFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(state.link, color = AetherEmber.Colors.TextTertiary, fontSize = 12.sp)
                            }
                            is AuthUiState.Unsupported -> Text(
                                state.description,
                                fontFamily = ManropeFontFamily,
                                color = AetherEmber.Colors.TextSecondary,
                                fontSize = 15.sp
                            )
                            AuthUiState.Ready -> StatusBlock("Opening Aether…")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Legal & Creator attribution footer
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "© 2026 Aritra Saha / Foresight Labs. All rights reserved.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xB8FFFFFF),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aether is an independent third-party client that uses the Telegram API. Aether is not affiliated with or endorsed by Telegram.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.5.sp,
                        color = Color(0x88FFFFFF),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Country Picker Modal
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = AetherEmber.Colors.BrightOrange,
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            fontFamily = ManropeFontFamily,
            color = AetherEmber.Colors.TextSecondary,
            fontSize = 14.sp
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
        text = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) "ENTER PHONE FOR NEW ACCOUNT" else "PHONE NUMBER",
        fontFamily = ManropeFontFamily,
        color = AetherEmber.Colors.TextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Country Selector Pill
        Box(
            modifier = Modifier
                .width(118.dp)
                .clip(AetherEmber.Shapes.M)
                .background(AetherEmber.Colors.SurfaceElevated)
                .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
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
                    fontSize = 14.5.sp,
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

        // Phone Input
        FieldCapsule(
            modifier = Modifier.weight(1f),
            value = national,
            onValueChange = { national = it.filter { ch -> ch.isDigit() } },
            keyboardType = KeyboardType.Phone,
            placeholder = "Phone number"
        )
    }

    ErrorText(error)
    Spacer(modifier = Modifier.height(24.dp))
    EmberPrimaryButton(
        enabled = !busy && national.isNotBlank(),
        busy = busy,
        label = if (intentMode == AuthIntentMode.CREATE_ACCOUNT) "Continue to Register" else "Continue"
    ) {
        onSubmit("+${country.dial}$national")
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
                .background(AetherEmber.Colors.Surface)
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
                    fontFamily = ManropeFontFamily,
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
                    .clip(AetherEmber.Shapes.M)
                    .background(AetherEmber.Colors.SurfaceElevated)
                    .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AetherEmber.Colors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search countries",
                                fontFamily = ManropeFontFamily,
                                color = AetherEmber.Colors.TextTertiary,
                                fontSize = 14.5.sp
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 14.5.sp
                            ),
                            cursorBrush = SolidColor(AetherEmber.Colors.Accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                }
            }

            // Country Rows
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
                                color = AetherEmber.Colors.Accent,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = 0.5.dp,
                        color = AetherEmber.Colors.BorderSubtle
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
        color = AetherEmber.Colors.TextSecondary,
        fontSize = 14.sp
    )
    if (state.phoneNumber.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.phoneNumber,
            fontFamily = ManropeFontFamily,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
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
    ErrorText(error)
    Spacer(modifier = Modifier.height(24.dp))
    EmberPrimaryButton(enabled = !busy && code.isNotBlank(), busy = busy, label = "Verify Code") { onSubmit(code) }
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onResend, enabled = !busy) {
        Text(
            "Resend code",
            fontFamily = ManropeFontFamily,
            color = AetherEmber.Colors.Accent,
            fontWeight = FontWeight.SemiBold
        )
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
    Text(
        text = "Enter Cloud Password",
        fontFamily = ManropeFontFamily,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
    state.hint?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hint: $it",
            fontFamily = ManropeFontFamily,
            color = AetherEmber.Colors.TextTertiary,
            fontSize = 13.sp
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    FieldCapsule(
        value = password,
        onValueChange = { password = it },
        keyboardType = KeyboardType.Password,
        placeholder = "Password",
        password = true
    )
    ErrorText(error)
    Spacer(modifier = Modifier.height(24.dp))
    EmberPrimaryButton(enabled = !busy && password.isNotBlank(), busy = busy, label = "Unlock Account") { onSubmit(password) }
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
        text = "SET UP PROFILE",
        fontFamily = ManropeFontFamily,
        color = AetherEmber.Colors.TextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(12.dp))

    FieldCapsule(value = first, onValueChange = { first = it }, placeholder = "First name (required)")
    Spacer(modifier = Modifier.height(10.dp))
    FieldCapsule(value = last, onValueChange = { last = it }, placeholder = "Last name (optional)")

    // Telegram Terms of Service if provided
    if (!state.termsOfServiceText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AetherEmber.Shapes.M)
                .background(AetherEmber.Colors.SurfaceElevated)
                .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Terms",
                        tint = AetherEmber.Colors.Accent,
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
                    color = AetherEmber.Colors.TextSecondary,
                    lineHeight = 16.sp,
                    maxLines = 4
                )
            }
        }
    }

    if (state.minAge > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "By registering, you confirm you are at least ${state.minAge} years old.",
            fontFamily = ManropeFontFamily,
            fontSize = 11.sp,
            color = AetherEmber.Colors.TextTertiary
        )
    }

    ErrorText(error)
    Spacer(modifier = Modifier.height(24.dp))
    EmberPrimaryButton(
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
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(AetherEmber.Shapes.M)
            .background(AetherEmber.Colors.SurfaceElevated)
            .border(1.dp, AetherEmber.Colors.Border, AetherEmber.Shapes.M)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                fontFamily = ManropeFontFamily,
                color = AetherEmber.Colors.TextTertiary,
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
            cursorBrush = SolidColor(AetherEmber.Colors.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmberPrimaryButton(enabled: Boolean, busy: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(AetherEmber.Shapes.Pill)
            .background(
                if (enabled) AetherEmber.Gradients.ActionButton else SolidColor(AetherEmber.Colors.SurfaceHighlight)
            )
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = label,
                fontFamily = ManropeFontFamily,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error.isNullOrBlank()) return
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = error,
        fontFamily = ManropeFontFamily,
        color = Color(0xFFEF4444),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}
