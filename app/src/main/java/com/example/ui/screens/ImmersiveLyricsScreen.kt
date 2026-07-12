package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.util.LrcLine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveLyricsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val track by viewModel.selectedTrack.collectAsState()
    val lyrics by viewModel.selectedTrackLyrics.collectAsState()
    val lrcLines by viewModel.lrcLines.collectAsState()
    val plainLyrics by viewModel.plainLyrics.collectAsState()

    // Playback timeline states
    val currentPositionMs by viewModel.playbackPositionSource.currentPositionMs.collectAsState()
    val isPlaying by viewModel.playbackPositionSource.isPlaying.collectAsState()

    // Customization states
    val activePreset by viewModel.activePreset.collectAsState()
    val fontFamilyName by viewModel.immersiveFontFamily.collectAsState()
    val fontSizeSp by viewModel.immersiveFontSize.collectAsState()
    val alignmentName by viewModel.immersiveAlignment.collectAsState()
    val hasTextShadow by viewModel.immersiveTextShadow.collectAsState()

    // Extracted Palette colors and user-defined presets
    val extractedDominantColor by viewModel.extractedDominantColor.collectAsState()
    val extractedMutedColor by viewModel.extractedMutedColor.collectAsState()
    val userPresets by viewModel.userPresets.collectAsState()

    // List scrolling state
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Preset configurations
    val presets = listOf("Geometric Balance", "Cosmic Dark", "Solar Glow", "Midnight Minimalist", "Classic Slate")

    // Find active lyric line index
    val activeLineIndex = remember(lrcLines, currentPositionMs) {
        var activeIndex = -1
        for (i in lrcLines.indices) {
            if (currentPositionMs >= lrcLines[i].timestampMs) {
                activeIndex = i
            } else {
                break
            }
        }
        activeIndex
    }

    // Auto scroll to active lyric line
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = (activeLineIndex - 2).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    // Derive elegant background gradients based on preset dominant colors and dynamic palette
    val backgroundBrush = remember(activePreset, extractedDominantColor, extractedMutedColor, userPresets) {
        // Parse user presets
        val userPresetList = if (userPresets.isNotEmpty()) {
            userPresets.split(";").mapNotNull { presetStr ->
                val parts = presetStr.split("|")
                if (parts.size == 3) {
                    val name = parts[0]
                    try {
                        val start = Color(android.graphics.Color.parseColor(parts[1]))
                        val end = Color(android.graphics.Color.parseColor(parts[2]))
                        name to Brush.verticalGradient(colors = listOf(start, end))
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
        } else emptyList()

        val matchedUserPreset = userPresetList.firstOrNull { it.first == activePreset }
        if (matchedUserPreset != null) {
            matchedUserPreset.second
        } else {
            when (activePreset) {
                "Album Art Glow" -> {
                    val dominant = extractedDominantColor?.let { Color(it) } ?: Color(0xFF312E3D)
                    val muted = extractedMutedColor?.let { Color(it) } ?: Color(0xFF1C1B1F)
                    Brush.verticalGradient(colors = listOf(dominant, muted))
                }
                "Geometric Balance" -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF312E3D), Color(0xFF1C1B1F))
                )
                "Solar Glow" -> Brush.radialGradient(
                    colors = listOf(Color(0xFFFF9800), Color(0xFFE91E63), Color(0xFF3F51B5)),
                    center = Offset(200f, 200f),
                    radius = 1000f
                )
                "Midnight Minimalist" -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                )
                "Classic Slate" -> Brush.linearGradient(
                    colors = listOf(Color(0xFF1E3C72), Color(0xFF2A5298))
                )
                "Cosmic Dark" -> Brush.radialGradient(
                    colors = listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)),
                    center = Offset(500f, 500f),
                    radius = 1200f
                )
                else -> Brush.verticalGradient(
                    colors = listOf(Color(0xFF312E3D), Color(0xFF1C1B1F))
                )
            }
        }
    }

    // Map customization names to Compose types
    val fontFamily = when (fontFamilyName) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    val textAlign = when (alignmentName) {
        "left" -> TextAlign.Left
        else -> TextAlign.Center
    }

    val textShadow = if (hasTextShadow) {
        Shadow(
            color = Color.Black.copy(alpha = 0.8f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    } else null

    Scaffold(
        topBar = {
            var showPresetDropdown by remember { mutableStateOf(false) }
            var showSaveDialog by remember { mutableStateOf(false) }

            TopAppBar(
                title = { Text("Immersive Lyrics", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("lyrics_view") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showPresetDropdown = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Presets", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showPresetDropdown,
                        onDismissRequest = { showPresetDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("✨ Album Art Glow (Palette)") },
                            onClick = {
                                viewModel.setActivePreset("Album Art Glow")
                                showPresetDropdown = false
                            }
                        )
                        HorizontalDivider()
                        
                        // Default presets
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset) },
                                onClick = {
                                    viewModel.setActivePreset(preset)
                                    showPresetDropdown = false
                                }
                            )
                        }

                        // Parse user presets
                        val parsedUserPresets = if (userPresets.isNotEmpty()) {
                            userPresets.split(";").mapNotNull { it.split("|").firstOrNull() }
                        } else emptyList()

                        if (parsedUserPresets.isNotEmpty()) {
                            HorizontalDivider()
                            parsedUserPresets.forEach { up ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(up)
                                            IconButton(
                                                onClick = { viewModel.deleteUserPreset(up) },
                                                modifier = Modifier.size(24.dp).testTag("delete_preset_$up")
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete preset",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setActivePreset(up)
                                        showPresetDropdown = false
                                    }
                                )
                            }
                        }

                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("➕ Save Current as Custom...") },
                            onClick = {
                                showSaveDialog = true
                                showPresetDropdown = false
                            },
                            modifier = Modifier.testTag("save_custom_preset_dropdown_btn")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            if (showSaveDialog) {
                var presetName by remember { mutableStateOf("") }
                var startColorHex by remember { mutableStateOf(String.format("#%06X", (extractedDominantColor ?: 0xFF312E3D.toInt()) and 0xFFFFFF)) }
                var endColorHex by remember { mutableStateOf(String.format("#%06X", (extractedMutedColor ?: 0xFF1C1B1F.toInt()) and 0xFFFFFF)) }

                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Custom Preset") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = presetName,
                                onValueChange = { presetName = it },
                                label = { Text("Preset Name") },
                                modifier = Modifier.fillMaxWidth().testTag("preset_name_input")
                            )
                            OutlinedTextField(
                                value = startColorHex,
                                onValueChange = { startColorHex = it },
                                label = { Text("Start Color Hex (e.g. #FF00FF)") },
                                modifier = Modifier.fillMaxWidth().testTag("preset_start_hex")
                            )
                            OutlinedTextField(
                                value = endColorHex,
                                onValueChange = { endColorHex = it },
                                label = { Text("End Color Hex (e.g. #00FFFF)") },
                                modifier = Modifier.fillMaxWidth().testTag("preset_end_hex")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (presetName.isNotBlank() && startColorHex.isNotBlank() && endColorHex.isNotBlank()) {
                                    viewModel.saveUserPreset(presetName, startColorHex, endColorHex)
                                    viewModel.setActivePreset(presetName)
                                    showSaveDialog = false
                                }
                            },
                            modifier = Modifier.testTag("save_preset_confirm_btn")
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            // Blurred Album Art Overlay for immersive feel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Header details
                if (track != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = track!!.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track!!.artist} • ${track!!.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Synced or plain lyrics display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (lyrics?.isInstrumental == true) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.White.copy(alpha = 0.7f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Instrumental Track",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontFamily
                                )
                                Text(
                                    text = "No vocals detected in this song.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else if (lrcLines.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            itemsIndexed(lrcLines) { index, line ->
                                val isActive = index == activeLineIndex
                                val scale by animateFloatAsState(if (isActive) 1.08f else 0.95f)
                                val opacity by animateFloatAsState(if (isActive) 1.0f else 0.45f)

                                SyncedLyricsLine(
                                    text = line.text,
                                    isActive = isActive,
                                    scale = scale,
                                    opacity = opacity,
                                    textStyle = TextStyle(
                                        fontFamily = fontFamily,
                                        fontSize = fontSizeSp.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                        textAlign = textAlign,
                                        shadow = textShadow,
                                        lineHeight = (fontSizeSp * 1.4f).sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.playbackPositionSource.seekTo(line.timestampMs)
                                        }
                                        .testTag("immersive_line_$index")
                                )
                            }
                        }
                    } else {
                        // Plain Text Display
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = plainLyrics.ifBlank { "No lyrics loaded." },
                                style = TextStyle(
                                    color = Color.White,
                                    fontFamily = fontFamily,
                                    fontSize = (fontSizeSp - 2).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = textAlign,
                                    shadow = textShadow
                                ),
                                modifier = Modifier
                                    .padding(vertical = 32.dp)
                                    .testTag("plain_immersive_lyrics_text")
                            )
                        }
                    }
                }

                // Controls HUD
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        val duration = track?.durationMs ?: 240000L
                        val formattedCurrent = formatMs(currentPositionMs)
                        val formattedDuration = formatMs(duration)

                        // Progress Bar Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(formattedCurrent, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = currentPositionMs.toFloat(),
                                onValueChange = { viewModel.playbackPositionSource.seekTo(it.toLong()) },
                                valueRange = 0f..duration.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("immersive_position_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Text(formattedDuration, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }

                        // Playback Action Controls
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { viewModel.playbackPositionSource.seekTo((currentPositionMs - 5000).coerceAtLeast(0)) }) {
                                Icon(Icons.Default.Replay5, contentDescription = "Rewind 5s", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            FloatingActionButton(
                                onClick = { viewModel.playbackPositionSource.setPlaying(!isPlaying) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(56.dp).testTag("immersive_play_pause_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            IconButton(onClick = { viewModel.playbackPositionSource.seekTo((currentPositionMs + 5000).coerceAtMost(duration)) }) {
                                Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncedLyricsLine(
    text: String,
    isActive: Boolean,
    scale: Float,
    opacity: Float,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = textStyle.copy(
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = opacity)
            ),
            modifier = Modifier.animateContentSize()
        )
        if (isActive) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
