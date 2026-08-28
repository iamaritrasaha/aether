package com.foresightlabs.aether.ui.weather

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.data.preferences.ManualWeatherLocation
import com.foresightlabs.aether.data.preferences.WeatherLocationMode
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.design.AetherGlassTokens
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Open-Meteo geocoding search result.
 */
@Immutable
data class GeocodingPlace(
    val id: Long,
    val name: String,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null
) {
    val subtitle: String
        get() = listOfNotNull(admin1, country).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Aether-native location picker sheet with Open-Meteo search.
 *
 * Implements Aether geometry (30dp top radius, porcelain/dark/OLED surfaces,
 * 4dp grid, neutral selection tokens, >=44dp touch targets).
 */
@Composable
fun AetherLocationPickerSheet(
    currentMode: WeatherLocationMode,
    currentManualLocation: ManualWeatherLocation?,
    onSelectAutomatic: () -> Unit,
    onSelectLocation: (ManualWeatherLocation) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val atmosphere = LocalAtmosphere.current
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodingPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 300ms Debounced Geocoding Search
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isSearching = false
            searchError = false
            return@LaunchedEffect
        }

        delay(300L)
        isSearching = true
        searchError = false

        val fetched = withContext(Dispatchers.IO) {
            searchPlaces(trimmed)
        }

        isSearching = false
        if (fetched != null) {
            results = fetched
            searchError = false
        } else {
            results = emptyList()
            searchError = true
        }
    }

    val sheetShape = RoundedCornerShape(
        topStart = AetherGlassTokens.SheetRadius,
        topEnd = AetherGlassTokens.SheetRadius
    )

    AetherGlass(
        frostState = null,
        shape = sheetShape,
        elevation = 12.dp,
        emphasis = 0.25f,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("location_picker_sheet")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sheet Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.surfaceHighlight)
                )
            }

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Choose location",
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .clickable { onDismiss() }
                    .semantics { contentDescription = "Close location picker" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))

        // Search Input Field (48-52dp height, 18dp radius)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(
                        fontFamily = ManropeFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(atmosphere.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("location_search_input"),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Search city or postcode",
                                fontFamily = ManropeFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textMuted
                            )
                        }
                        innerTextField()
                    }
                )

                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space16))

        // Content Area: Automatic Option + Search Results
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .testTag("location_picker_list")
        ) {
            // 1. Automatic Option (Always at top)
            item(key = "automatic_option") {
                val isAutoSelected = currentMode == WeatherLocationMode.AUTOMATIC
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(AetherEmber.Shapes.M)
                        .background(if (isAutoSelected) colors.surfaceHighlight else Color.Transparent)
                        .clickable {
                            onSelectAutomatic()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isAutoSelected) atmosphere.accent.copy(alpha = 0.20f) else colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = if (isAutoSelected) atmosphere.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Automatic location",
                                fontFamily = ManropeFontFamily,
                                fontSize = 15.sp,
                                fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Approximate device location",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textSecondary
                            )
                        }
                    }

                    if (isAutoSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = atmosphere.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = colors.divider,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // 2. Loading State
            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = atmosphere.accent,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            } else if (searchError) {
                // 3. Error State
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Couldn't search locations",
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Check your connection and try again.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else if (query.trim().length >= 2 && results.isEmpty()) {
                // 4. Empty State
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No places found",
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try another city or postcode.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                // 5. Results List
                items(results, key = { it.id }) { place ->
                    val isSelected = currentMode == WeatherLocationMode.MANUAL &&
                            currentManualLocation?.latitude == place.latitude &&
                            currentManualLocation.longitude == place.longitude

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(AetherEmber.Shapes.M)
                            .background(if (isSelected) colors.surfaceHighlight else Color.Transparent)
                            .clickable {
                                onSelectLocation(
                                    ManualWeatherLocation(
                                        name = place.name,
                                        admin1 = place.admin1,
                                        country = place.country,
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        timezone = place.timezone
                                    )
                                )
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = place.name,
                                fontFamily = ManropeFontFamily,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (place.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = place.subtitle,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = atmosphere.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = colors.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
}

/**
 * Searches places via Open-Meteo Geocoding API.
 */
fun searchPlaces(query: String): List<GeocodingPlace>? {
    var connection: HttpURLConnection? = null
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=en&format=json")
        connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("User-Agent", "Aether-LocationSearch/1.0")
        }

        if (connection.responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val text = reader.use { it.readText() }
            parseGeocodingResponse(text)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

/**
 * Parses Open-Meteo Geocoding JSON response into [GeocodingPlace] objects.
 */
fun parseGeocodingResponse(jsonString: String): List<GeocodingPlace> {
    return try {
        val root = JSONObject(jsonString)
        val resultsArray = root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<GeocodingPlace>()

        for (i in 0 until resultsArray.length()) {
            val item = resultsArray.getJSONObject(i)
            val id = item.optLong("id", i.toLong())
            val name = item.optString("name", "")
            if (name.isBlank()) continue

            val lat = item.optDouble("latitude", Double.NaN)
            val lon = item.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            val admin1 = item.optString("admin1").takeIf { it.isNotBlank() && it != "null" }
            val country = item.optString("country").takeIf { it.isNotBlank() && it != "null" }
            val timezone = item.optString("timezone").takeIf { it.isNotBlank() && it != "null" }

            list.add(
                GeocodingPlace(
                    id = id,
                    name = name,
                    admin1 = admin1,
                    country = country,
                    latitude = lat,
                    longitude = lon,
                    timezone = timezone
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}
