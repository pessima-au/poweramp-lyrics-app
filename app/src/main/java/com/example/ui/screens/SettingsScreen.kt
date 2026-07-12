package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    // Collect all settings values
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val defaultLyricsType by viewModel.defaultLyricsType.collectAsState()
    val fallbackBroaderSearch by viewModel.fallbackBroaderSearch.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val notifyFailure by viewModel.notifyFailure.collectAsState()
    val markInstrumental by viewModel.markInstrumental.collectAsState()
    val storageDestination by viewModel.storageDestination.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val matchingThreshold by viewModel.matchingThreshold.collectAsState()

    // Immersive custom preferences
    val fontFamily by viewModel.immersiveFontFamily.collectAsState()
    val fontSizeSp by viewModel.immersiveFontSize.collectAsState()
    val alignment by viewModel.immersiveAlignment.collectAsState()
    val textShadow by viewModel.immersiveTextShadow.collectAsState()

    var showClearCacheConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verse Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category: Visual Theme & Appearance
            SettingsSectionHeader(title = "Theme & Appearance", icon = Icons.Default.Palette)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Theme Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Theme Mode", fontWeight = FontWeight.SemiBold)
                            Text("Current: ${themeMode.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        var expandedTheme by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { expandedTheme = true }) {
                                Text(themeMode.replaceFirstChar { it.uppercase() })
                            }
                            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                                listOf("system", "light", "dark").forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            viewModel.setThemeMode(mode)
                                            expandedTheme = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Divider()

                    // Dynamic Color M3 Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Material You Dynamic Color", fontWeight = FontWeight.SemiBold)
                            Text("Extract color scheme from your wallpaper on Android 12+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            modifier = Modifier.testTag("dynamic_color_switch")
                        )
                    }
                }
            }

            // Category: Search & Matching Engine
            SettingsSectionHeader(title = "Search & Matching Sensitivity", icon = Icons.Default.Tune)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Matching sensitivity threshold
                    Text("Auto-Match Confidence Threshold: $matchingThreshold%", fontWeight = FontWeight.SemiBold)
                    Text(
                        "LRCLIB candidates scoring above this value are accepted. Scores below require manual fine search verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = matchingThreshold.toFloat(),
                        onValueChange = { viewModel.setMatchingThreshold(it.toInt()) },
                        valueRange = 50f..95f,
                        steps = 8,
                        modifier = Modifier.testTag("sensitivity_slider")
                    )

                    Divider()

                    // Fallback to broader search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fallback Search Assist", fontWeight = FontWeight.SemiBold)
                            Text("Use secondary broad search strategies when LRCLIB fails", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = fallbackBroaderSearch,
                            onCheckedChange = { viewModel.setFallbackBroaderSearch(it) }
                        )
                    }

                    Divider()

                    // Mark Instrumental
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-detect Instrumental", fontWeight = FontWeight.SemiBold)
                            Text("Flag lyrics empty as 'Instrumental' on positive database flags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = markInstrumental,
                            onCheckedChange = { viewModel.setMarkInstrumental(it) }
                        )
                    }
                }
            }

            // Category: Data Usage & Storage
            SettingsSectionHeader(title = "Data Usage & Storage", icon = Icons.Default.Storage)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Wifi only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WiFi-Only Downloads", fontWeight = FontWeight.SemiBold)
                            Text("Batch operations require a Wifi connection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { viewModel.setWifiOnly(it) }
                        )
                    }

                    Divider()

                    // Storage location
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage Destination", fontWeight = FontWeight.SemiBold)
                            Text("Current: ${storageDestination.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var expandedStorage by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { expandedStorage = true }) {
                                Text(when(storageDestination) {
                                    "lrc" -> "Save .lrc file (Phase 2 Plugin)"
                                    "embed" -> "Embed tag (Phase 2 Plugin)"
                                    else -> "Local Cache Only"
                                })
                            }
                            DropdownMenu(expanded = expandedStorage, onDismissRequest = { expandedStorage = false }) {
                                DropdownMenuItem(text = { Text("Local Cache Only") }, onClick = { viewModel.setStorageDestination("cache"); expandedStorage = false })
                                DropdownMenuItem(text = { Text("Save .lrc file (Coming in plugin update)") }, onClick = { viewModel.setStorageDestination("lrc"); expandedStorage = false })
                                DropdownMenuItem(text = { Text("Embed tag (Coming in plugin update)") }, onClick = { viewModel.setStorageDestination("embed"); expandedStorage = false })
                            }
                        }
                    }
                }
            }

            // Category: AI Providers and Keys
            SettingsSectionHeader(title = "AI Engine Credentials (Gemini)", icon = Icons.Default.VpnKey)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your Gemini developer key. Stored locally only on your device, never leaked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { viewModel.setGeminiApiKey(it) },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AI Studio API Key...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input_field"),
                        singleLine = true
                    )
                }
            }

            // Category: Immersive Screen Typography
            SettingsSectionHeader(title = "Immersive Lyrics Customize", icon = Icons.Default.FontDownload)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Font family selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Font Family", fontWeight = FontWeight.SemiBold)
                            Text("Current: ${fontFamily.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var expandedFontMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { expandedFontMenu = true }) {
                                Text(fontFamily.replaceFirstChar { it.uppercase() })
                            }
                            DropdownMenu(expanded = expandedFontMenu, onDismissRequest = { expandedFontMenu = false }) {
                                listOf("default", "serif", "monospace", "cursive").forEach { f ->
                                    DropdownMenuItem(text = { Text(f.replaceFirstChar { it.uppercase() }) }, onClick = { viewModel.setImmersiveFontFamily(f); expandedFontMenu = false })
                                }
                            }
                        }
                    }

                    Divider()

                    // Text size slider
                    Text("Font Size: ${fontSizeSp.toInt()} sp", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { viewModel.setImmersiveFontSize(it) },
                        valueRange = 16f..36f,
                        modifier = Modifier.testTag("font_size_slider")
                    )

                    Divider()

                    // Alignment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Lyric Alignment", fontWeight = FontWeight.SemiBold)
                            Text("Current: ${alignment.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        var expandedAlignMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { expandedAlignMenu = true }) {
                                Text(alignment.replaceFirstChar { it.uppercase() })
                            }
                            DropdownMenu(expanded = expandedAlignMenu, onDismissRequest = { expandedAlignMenu = false }) {
                                listOf("center", "left").forEach { a ->
                                    DropdownMenuItem(text = { Text(a.replaceFirstChar { it.uppercase() }) }, onClick = { viewModel.setImmersiveAlignment(a); expandedAlignMenu = false })
                                }
                            }
                        }
                    }

                    Divider()

                    // Text Shadow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contrast Text Shadows", fontWeight = FontWeight.SemiBold)
                            Text("Enhance readability over bright dynamic album art backgrounds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = textShadow,
                            onCheckedChange = { viewModel.setImmersiveTextShadow(it) }
                        )
                    }
                }
            }

            // Category: Danger Zone
            SettingsSectionHeader(title = "Danger Zone", icon = Icons.Default.Warning)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Reset Caches", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Wipes all local Room cached song lyrics. Irreversible action.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                    Button(
                        onClick = { showClearCacheConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_cache_btn")
                    ) {
                        Text("Delete All Cached Lyrics", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Cache reset dialog box
        if (showClearCacheConfirm) {
            AlertDialog(
                onDismissRequest = { showClearCacheConfirm = false },
                title = { Text("Wipe Lyrics Cache?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you absolutely sure you want to delete all cached lyrics from your local database? Offline caching will be rebuilt next lookup.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearCache()
                            showClearCacheConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Yes, Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
