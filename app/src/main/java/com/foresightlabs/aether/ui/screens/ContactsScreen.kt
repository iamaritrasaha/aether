package com.foresightlabs.aether.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.domain.contacts.DiscoveredContact
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.design.AetherElevation
import com.foresightlabs.aether.ui.design.AetherEmptyState
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.AetherSurface
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

@Composable
fun ContactsScreen(
    contacts: List<DiscoveredContact>,
    isLoading: Boolean,
    hasDeviceContactsLoaded: Boolean = false,
    onContactClick: (User) -> Unit,
    onBack: () -> Unit,
    onRequestDeviceSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current

    var searchQuery by remember { mutableStateOf("") }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var lastContactClickTime by remember { mutableStateOf(0L) }

    val onSafeContactClick: (com.foresightlabs.aether.domain.model.User) -> Unit = remember(onContactClick) {
        { user ->
            val now = System.currentTimeMillis()
            if (now - lastContactClickTime > 500L) {
                lastContactClickTime = now
                onContactClick(user)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onRequestDeviceSync()
        }
    }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                (it.isTelegramUser && it.telegramUser?.username?.contains(searchQuery, ignoreCase = true) == true) ||
                (!it.isTelegramUser && it.phone.contains(searchQuery, ignoreCase = true))
        }
    }

    val groupedContacts = remember(filteredContacts) {
        filteredContacts.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }
            .toSortedMap()
    }

    val frostState = rememberAetherFrostState()
    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            heroFraction = 1f,
            frostState = frostState
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
              Column(
                modifier = Modifier.fillMaxSize().aetherFrostSource(frostState)
                    .padding(top = aetherFloatingHeaderContentTopPadding())
              ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts…", color = Color(0x99FFFFFF)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xAAFFFFFF))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = AetherEmber.Shapes.Pill,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = atmosphere.accent,
                        unfocusedBorderColor = Color(0x28FFFFFF),
                        focusedContainerColor = Color(0x35000000),
                        unfocusedContainerColor = Color(0x25000000)
                    ),
                    singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = atmosphere.accent)
                    }
                } else if (filteredContacts.isEmpty()) {
                    AetherEmptyState(
                        title = if (searchQuery.isBlank()) "No contacts found" else "No matching contacts",
                        detail = if (searchQuery.isBlank()) "Search or tap + to discover contacts on Telegram." else "Try searching by a different name.",
                        icon = Icons.Default.Contacts
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("contacts_list"),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Contextual discovery invitation card when device contacts haven't been synced
                        if (!hasDeviceContactsLoaded && searchQuery.isBlank()) {
                            item(key = "discovery_invite_card") {
                                AetherSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable { showRationaleDialog = true },
                                    elevation = AetherElevation.Surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(atmosphere.accent.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PersonAdd,
                                                contentDescription = null,
                                                tint = atmosphere.accent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Find people you know",
                                                fontFamily = ManropeFontFamily,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Check which phone contacts use Telegram",
                                                fontFamily = ManropeFontFamily,
                                                fontSize = 12.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        groupedContacts.forEach { (letter, groupList) ->
                            item(key = "header_$letter") {
                                Text(
                                    text = letter.toString(),
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xD9FFFFFF),
                                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(groupList, key = { "${it.name}_${it.phone}" }) { contact ->
                                ContactItemRow(
                                    contact = contact,
                                    onClick = {
                                        if (contact.telegramUser != null) {
                                            onSafeContactClick(contact.telegramUser)
                                        } else {
                                            // Send SMS invite explicitly on user action
                                            val inviteIntent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:${contact.phone}")
                                                putExtra("sms_body", "Hey! Let's connect on Aether / Telegram: https://telegram.org/dl")
                                            }
                                            context.startActivity(inviteIntent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
              }

              AetherFloatingHeader(
                  title = "Contacts",
                  subtitle = "${contacts.size} people in your orbit",
                  modifier = Modifier.align(Alignment.TopCenter),
                  frostState = frostState,
                  navigation = {
                      AetherBackButton(
                          onClick = onBack,
                          modifier = Modifier.testTag("contacts_back_button")
                      )
                  },
                  actions = {
                      if (!hasDeviceContactsLoaded) {
                          AetherIconButton(
                              icon = Icons.Default.PersonAdd,
                              contentDescription = "Find people you know",
                              onClick = { showRationaleDialog = true },
                              modifier = Modifier.testTag("find_device_contacts_button")
                          )
                      }
                  }
              )
            }
        }

        // Explicit Contact Import Rationale Dialog
        if (showRationaleDialog) {
            ContactsRationaleDialog(
                onContinue = {
                    showRationaleDialog = false
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        onRequestDeviceSync()
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                onNotNow = {
                    showRationaleDialog = false
                }
            )
        }
    }
}

@Composable
private fun ContactItemRow(
    contact: DiscoveredContact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current

    AetherSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onClick() },
        elevation = AetherElevation.Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AetherAvatar(
                initials = contact.telegramUser?.avatarInitials ?: contact.name.take(2).uppercase(),
                gradient = contact.telegramUser?.avatarGradient ?: listOf(atmosphere.accent, atmosphere.shadow),
                size = 46.dp,
                isOnline = contact.telegramUser?.isOnline == true,
                photoPath = contact.telegramUser?.photoPath
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontFamily = ManropeFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                if (contact.isTelegramUser) {
                    // Minimized, people-first presentation: truthful presence / status, NEVER displaying phone number here!
                    val tgUser = contact.telegramUser
                    val statusText = when {
                        tgUser?.presence == Presence.ONLINE -> "online"
                        tgUser?.lastSeenText?.isNotBlank() == true -> tgUser.lastSeenText
                        tgUser?.presence == Presence.RECENTLY -> "last seen recently"
                        tgUser?.presence == Presence.WITHIN_WEEK -> "last seen within a week"
                        tgUser?.presence == Presence.WITHIN_MONTH -> "last seen within a month"
                        else -> "on Telegram"
                    }
                    val isOnline = tgUser?.isOnline == true

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = statusText,
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            color = if (isOnline) Color(0xFF90F0C0) else colors.textSecondary,
                            fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                } else {
                    // Non-Telegram contact: phone number shown only to disambiguate for Invite
                    Text(
                        text = contact.phone,
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                }
            }

            if (!contact.isTelegramUser) {
                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(atmosphere.accent.copy(alpha = 0.2f))
                        .border(1.dp, atmosphere.accent, AetherEmber.Shapes.Pill)
                        .clickable { onClick() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Invite",
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactsRationaleDialog(
    onContinue: () -> Unit,
    onNotNow: () -> Unit
) {
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current

    Dialog(
        onDismissRequest = onNotNow,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AetherSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.L),
                elevation = AetherElevation.Surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(atmosphere.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            tint = atmosphere.accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Find people you know",
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Aether can use contacts on this device to find people who use Telegram. If you continue, contact phone numbers needed for matching will be sent to Telegram.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.5.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Continue Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(AetherEmber.Shapes.Pill)
                            .background(atmosphere.accent)
                            .clickable { onContinue() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue",
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Not Now Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(AetherEmber.Shapes.Pill)
                            .clickable { onNotNow() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Not now",
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}
