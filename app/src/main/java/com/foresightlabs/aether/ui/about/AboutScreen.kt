package com.foresightlabs.aether.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

const val AETHER_GITHUB_URL = "https://github.com/iamaritrasaha/aether"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)
    val frostState = rememberAetherFrostState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Box(modifier = modifier.fillMaxSize().testTag("about_screen")) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("about_list"),
                contentPadding = PaddingValues(top = aetherFloatingHeaderContentTopPadding(), bottom = 40.dp)
            ) {
                // Header Hero Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Aether",
                            fontFamily = ManropeFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "“A quieter way to Telegram.”",
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = AetherAccent.current
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .clip(AetherEmber.Shapes.Pill)
                                .background(AetherAccent.subtle)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("about_version_info"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AetherAccent.current
                            )
                        }
                    }
                }

                // ABOUT AETHER
                item {
                    AboutSectionHeader(title = "ABOUT AETHER")
                    AboutContentCard {
                        Text(
                            text = "Aether is an independent Android messenger from Foresight Labs, built on Telegram's official TDLib. It is not an attempt to reproduce every surface of Telegram. Aether explores what Telegram can feel like when people and personal conversations come first.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Telegram supplies the account, the network and the protocol, through the official Telegram Database Library. Everything above that — the interaction model, the navigation, the visual language, the motion, the product priorities — is Aether's own.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }

                // THE AMBITION
                item {
                    AboutSectionHeader(title = "THE AMBITION")
                    AboutContentCard {
                        Text(
                            text = "Not to make Telegram look different — to build a distinct personal communication environment on top of it.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Interaction, navigation, motion and hierarchy are designed around conversation rather than feature density. Capabilities are added when they strengthen personal communication, not to close a gap on a comparison chart. Aether does not pursue feature parity for its own sake.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.5.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }

                // WHAT MAKES AETHER DIFFERENT
                item {
                    AboutSectionHeader(title = "WHAT MAKES AETHER DIFFERENT")
                    AboutContentCard {
                        Text(
                            text = "From Telegram",
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Telegram is a broad communication platform, and a very good one. Aether deliberately chooses a narrower primary experience. The difference is scope and product philosophy, not a criticism of the platform it runs on.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "From other third-party clients",
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Many third-party clients focus on extending, customizing or re-presenting the broader Telegram experience. Aether takes a more selective approach: it asks which parts of Telegram should become part of a focused personal messenger, and designs its own surface for the parts that stay.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 19.sp
                        )
                    }
                }

                // TECHNOLOGY
                item {
                    AboutSectionHeader(title = "TECHNOLOGY")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                    ) {
                        TechRowItem(
                            label = "Platform & Core Protocol",
                            value = "Telegram / TDLib"
                        )
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        TechRowItem(
                            label = "TDLib Revision",
                            value = BuildConfig.TDLIB_COMMIT.take(8)
                        )
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        TechRowItem(
                            label = "Aether Version",
                            value = BuildConfig.VERSION_NAME
                        )
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        TechRowItem(
                            label = "Build Code",
                            value = BuildConfig.VERSION_CODE.toString()
                        )
                    }
                }

                // PROJECT (GitHub Link)
                item {
                    AboutSectionHeader(title = "PROJECT")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(AetherEmber.Shapes.L)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.border, AetherEmber.Shapes.L)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        uriHandler.openUri(AETHER_GITHUB_URL)
                                    } catch (_: Exception) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AETHER_GITHUB_URL))
                                        context.startActivity(intent)
                                    }
                                }
                                .testTag("about_github_row")
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AetherAccent.subtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "GitHub Repository",
                                    tint = AetherAccent.current,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub Repository",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = AETHER_GITHUB_URL,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    color = AetherAccent.current
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // OPEN SOURCE
                item {
                    AboutSectionHeader(title = "OPEN SOURCE")
                    AboutContentCard(testTag = "about_open_source_text") {
                        Text(
                            text = "Aether is built using open source software and libraries:",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OpenSourceItem(name = "TDLib", license = "Boost Software License 1.0")
                        Spacer(modifier = Modifier.height(6.dp))
                        OpenSourceItem(name = "Haze", license = "Apache License 2.0")
                        Spacer(modifier = Modifier.height(6.dp))
                        OpenSourceItem(name = "Manrope Font", license = "SIL Open Font License 1.1")
                        Spacer(modifier = Modifier.height(6.dp))
                        OpenSourceItem(name = "Space Grotesk Font", license = "SIL Open Font License 1.1")
                    }
                }

                // LEGAL
                item {
                    AboutSectionHeader(title = "LEGAL")
                    AboutContentCard(testTag = "about_legal_text") {
                        Text(
                            text = "Aether is an independent, unofficial Telegram client. It is not affiliated with, sponsored by, or endorsed by Telegram. Telegram is the platform Aether connects to; Foresight Labs neither owns nor represents Telegram technology, and Telegram has no ownership of Aether.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }

                // COPYRIGHT
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "© 2026 Aritra Saha / Foresight Labs.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AetherEmber.Colors.AtmosphereTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "All rights reserved.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            color = AetherEmber.Colors.AtmosphereTextTertiary
                        )
                    }
                }
            }
        }

        AetherFloatingHeader(
            title = "About Aether",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = {
                AetherBackButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("about_back_button")
                )
            }
        )
    }
}

@Composable
private fun AboutSectionHeader(title: String) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = title,
        fontFamily = ManropeFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = AetherEmber.Colors.AtmosphereTextSecondary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
    )
}

@Composable
private fun AboutContentCard(
    testTag: String = "",
    content: @Composable () -> Unit
) {
    val colors = LocalAetherColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AetherEmber.Shapes.L)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, AetherEmber.Shapes.L)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun TechRowItem(
    label: String,
    value: String
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = ManropeFontFamily,
            fontSize = 13.5.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontFamily = ManropeFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun OpenSourceItem(
    name: String,
    license: String
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "• $name",
            fontFamily = ManropeFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = license,
            fontFamily = ManropeFontFamily,
            fontSize = 12.sp,
            color = colors.textTertiary
        )
    }
}
