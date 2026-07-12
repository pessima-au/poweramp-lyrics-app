package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.AppViewModel
import com.example.util.LrcLine
import com.example.util.LrcParser
import com.example.data.model.Track
import com.example.data.model.CachedLyricsEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val track by viewModel.selectedTrack.collectAsState()
    val lyrics by viewModel.selectedTrackLyrics.collectAsState()
    val lrcLines by viewModel.lrcLines.collectAsState()
    val plainLyricsState by viewModel.plainLyrics.collectAsState()

    var activeTab by remember { mutableStateOf("Timeline Sync") }
    val tabs = listOf("Timeline Sync", "Plain Text Edit", "Sync Preview")

    // Local states for editing plain text
    var plainTextDraft by remember { mutableStateOf("") }
    LaunchedEffect(plainLyricsState) {
        plainTextDraft = plainLyricsState
    }

    // AI Assist variables
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    var selectedTranslationLang by remember { mutableStateOf("Spanish") }
    var showAiAssistPanel by remember { mutableStateOf(false) }
    var lineToEdit by remember { mutableStateOf<Pair<Int, LrcLine>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lyrics Editor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("lyrics_view") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiAssistPanel = !showAiAssistPanel }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assist", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            if (activeTab == "Plain Text Edit") {
                                val syncedLrc = LrcParser.makeLrc(lrcLines)
                                viewModel.saveEditedLyrics(plainTextDraft, syncedLrc, false)
                            } else {
                                // Already autosaved on line adjustments, but let's re-save to be safe
                                val syncedLrc = LrcParser.makeLrc(lrcLines)
                                viewModel.saveEditedLyrics(plainTextDraft.ifBlank { lrcLines.joinToString("\n") { it.text } }, syncedLrc, false)
                            }
                            viewModel.navigateTo("lyrics_view")
                        },
                        modifier = Modifier.testTag("save_editor_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Lyrics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Editor Section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                val currentTrack = track
                if (currentTrack == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No track selected.")
                    }
                } else {
                    LyricsMetadataPanel(track = currentTrack, lyrics = lyrics)

                    TabRow(selectedTabIndex = tabs.indexOf(activeTab)) {
                        tabs.forEach { tab ->
                            Tab(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                text = { Text(tab) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeTab == "Timeline Sync") {
                        if (lrcLines.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No synced lyrics timeline exists.",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = {
                                        // Generate initial empty timeline from plain text
                                        val initialLines = plainTextDraft.lineSequence()
                                            .filter { it.isNotBlank() }
                                            .mapIndexed { idx, txt -> LrcLine((idx * 4000).toLong(), txt) }
                                            .toList()
                                        val lrcStr = LrcParser.makeLrc(initialLines)
                                        viewModel.saveEditedLyrics(plainTextDraft, lrcStr, false)
                                    }) {
                                        Text("Convert Plain Text to Timeline")
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Nudge timestamps (±500ms) or snap to current playing time to synchronize line-by-line.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(lrcLines) { index, line ->
                                    LrcNudgeItem(
                                        index = index,
                                        line = line,
                                        onNudgeEarlier = { viewModel.nudgeTimestamp(index, -500L) },
                                        onNudgeLater = { viewModel.nudgeTimestamp(index, 500L) },
                                        onSyncCurrent = { viewModel.syncLineToCurrentPosition(index) }
                                    )
                                }
                            }
                        }
                    } else if (activeTab == "Sync Preview") {
                        if (lrcLines.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No synced lyrics timeline exists.",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = {
                                        val initialLines = plainTextDraft.lineSequence()
                                            .filter { it.isNotBlank() }
                                            .mapIndexed { idx, txt -> LrcLine((idx * 4000).toLong(), txt) }
                                            .toList()
                                        val lrcStr = LrcParser.makeLrc(initialLines)
                                        viewModel.saveEditedLyrics(plainTextDraft, lrcStr, false)
                                    }) {
                                        Text("Convert Plain Text to Timeline")
                                    }
                                }
                            }
                        } else {
                            val currentPosMs by viewModel.playbackPositionSource.currentPositionMs.collectAsState()
                            val playState by viewModel.playbackPositionSource.isPlaying.collectAsState()
                            var autoScroll by remember { mutableStateOf(true) }
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                            val activeLineIdx = remember(lrcLines, currentPosMs) {
                                val idx = lrcLines.indexOfLast { currentPosMs >= it.timestampMs }
                                if (idx == -1) 0 else idx
                            }

                            LaunchedEffect(activeLineIdx, autoScroll) {
                                if (autoScroll && activeLineIdx >= 0 && activeLineIdx < lrcLines.size) {
                                    val targetScrollIdx = (activeLineIdx - 2).coerceAtLeast(0)
                                    listState.animateScrollToItem(targetScrollIdx)
                                }
                            }

                            // Playback Control Strip
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledIconButton(
                                            onClick = { viewModel.playbackPositionSource.setPlaying(!playState) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (playState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (playState) "Pause" else "Play"
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val target = (currentPosMs - 5000L).coerceAtLeast(0L)
                                                viewModel.playbackPositionSource.seekTo(target)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind 5s")
                                        }

                                        IconButton(
                                            onClick = {
                                                val target = currentPosMs + 5000L
                                                viewModel.playbackPositionSource.seekTo(target)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.FastForward, contentDescription = "Forward 5s")
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        val formattedCurrent = remember(currentPosMs) {
                                            val minutes = (currentPosMs / 1000) / 60
                                            val seconds = (currentPosMs / 1000) % 60
                                            val hundredths = (currentPosMs % 1000) / 10
                                            String.format(java.util.Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
                                        }
                                        val durationVal = track?.durationMs ?: 0L
                                        val formattedDuration = remember(durationVal) {
                                            val minutes = (durationVal / 1000) / 60
                                            val seconds = (durationVal / 1000) % 60
                                            val hundredths = (durationVal % 1000) / 10
                                            String.format(java.util.Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
                                        }
                                        Text(
                                            text = "$formattedCurrent / $formattedDuration",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Checkbox(
                                                checked = autoScroll,
                                                onCheckedChange = { autoScroll = it }
                                            )
                                            Text(
                                                text = "Auto-Scroll",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Scrolling List
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("sync_preview_list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(lrcLines) { index, line ->
                                    val isActive = index == activeLineIdx
                                    val cardColor = if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    }
                                    val borderStroke = if (isActive) {
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("preview_item_$index"),
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        border = borderStroke,
                                        onClick = {
                                            viewModel.playbackPositionSource.seekTo(line.timestampMs)
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "[${line.formattedTime}]",
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.secondary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )

                                                if (isActive) {
                                                    Surface(
                                                        shape = MaterialTheme.shapes.extraSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = line.text,
                                                style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    FilledIconButton(
                                                        onClick = { viewModel.nudgeTimestamp(index, -100L) },
                                                        modifier = Modifier.size(32.dp),
                                                        colors = IconButtonDefaults.filledIconButtonColors(
                                                            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                    ) {
                                                        Text("-0.1s", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    FilledIconButton(
                                                        onClick = { viewModel.nudgeTimestamp(index, -500L) },
                                                        modifier = Modifier.size(32.dp),
                                                        colors = IconButtonDefaults.filledIconButtonColors(
                                                            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                    ) {
                                                        Text("-0.5s", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    FilledIconButton(
                                                        onClick = { viewModel.nudgeTimestamp(index, 500L) },
                                                        modifier = Modifier.size(32.dp),
                                                        colors = IconButtonDefaults.filledIconButtonColors(
                                                            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                    ) {
                                                        Text("+0.5s", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    FilledIconButton(
                                                        onClick = { viewModel.nudgeTimestamp(index, 100L) },
                                                        modifier = Modifier.size(32.dp),
                                                        colors = IconButtonDefaults.filledIconButtonColors(
                                                            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                                                        )
                                                    ) {
                                                        Text("+0.1s", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(
                                                        onClick = { viewModel.syncLineToCurrentPosition(index) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.FlashOn,
                                                            contentDescription = "Snap to Current Time",
                                                            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { lineToEdit = Pair(index, line) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Line Details",
                                                            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Plain Text Edit tab
                        OutlinedTextField(
                            value = plainTextDraft,
                            onValueChange = { plainTextDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("plain_lyrics_text_area"),
                            label = { Text("Standard Plain Lyrics") },
                            placeholder = { Text("Enter plain lyrics here...") }
                        )
                    }
                }
            }

            // AI Assist Sidebar Panel (Slide out / Toggle-able)
            AnimatedVisibility(visible = showAiAssistPanel) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Gemini AI Companion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { showAiAssistPanel = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close AI Panel")
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("Lyric Enhancements:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Formatting clean buttons
                        Button(
                            onClick = { viewModel.requestAiLyricsTask("clean") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_clean_formatting_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clean & Format Lyrics")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Translate Lyrics:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Language selector and translate button
                        val languages = listOf("Spanish", "French", "German", "Japanese", "Italian")
                        var expandedLangMenu by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedLangMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedTranslationLang)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expandedLangMenu,
                                onDismissRequest = { expandedLangMenu = false }
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang) },
                                        onClick = {
                                            selectedTranslationLang = lang
                                            expandedLangMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.requestAiLyricsTask("translate_${selectedTranslationLang.lowercase()}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_translate_btn")
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Translate to $selectedTranslationLang")
                        }

                        Divider(modifier = Modifier.padding(vertical = 16.dp))

                        // Diff Preview Box
                        Text("AI Result Preview / Diff:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (isAiLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (aiResult != null) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = aiResult!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (aiResult!!.startsWith("Error:")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (!aiResult!!.startsWith("Error:") && !aiResult!!.startsWith("Processing")) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextButton(
                                                onClick = { viewModel.clearAiResult() },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Discard")
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.acceptAiResultAsLyrics()
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_accept_btn")
                                            ) {
                                                Text("Apply Diff")
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Select an AI action above to see preview.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    lineToEdit?.let { (index, line) ->
        var editedText by remember(line) { mutableStateOf(line.text) }
        var editedTimeMsStr by remember(line) { mutableStateOf(line.timestampMs.toString()) }

        AlertDialog(
            onDismissRequest = { lineToEdit = null },
            title = { Text("Edit Line Timing & Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        label = { Text("Lyric Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedTimeMsStr,
                        onValueChange = { editedTimeMsStr = it },
                        label = { Text("Timestamp (Milliseconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    val parsedMs = editedTimeMsStr.toLongOrNull() ?: 0L
                    val formattedHelper = remember(parsedMs) {
                        val minutes = (parsedMs / 1000) / 60
                        val seconds = (parsedMs / 1000) % 60
                        val hundredths = (parsedMs % 1000) / 10
                        String.format(java.util.Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
                    }
                    Text(
                        text = "Formatted time: [$formattedHelper]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalMs = editedTimeMsStr.toLongOrNull() ?: line.timestampMs
                        viewModel.updateLrcLine(index, editedText, finalMs)
                        lineToEdit = null
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { lineToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LrcNudgeItem(
    index: Int,
    line: LrcLine,
    onNudgeEarlier: () -> Unit,
    onNudgeLater: () -> Unit,
    onSyncCurrent: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lrc_nudge_item_$index"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[${line.formattedTime}]",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledIconButton(
                        onClick = onNudgeEarlier,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("-0.5s", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledIconButton(
                        onClick = onNudgeLater,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("+0.5s", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onSyncCurrent,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("snap_current_btn_$index"),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Snap", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LyricsMetadataPanel(
    track: Track,
    lyrics: CachedLyricsEntity?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("lyrics_metadata_panel"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Track header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = "Track Info Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Technical metadata section title
            Text(
                text = "LYRICS TECHNICAL METADATA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (lyrics == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning Icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "No fetched or cached lyrics database entry for this track yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Layout technical specifications in a clean grid/row structure
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Source, Confidence
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataItem(
                            icon = Icons.Default.Cloud,
                            label = "Source Provider",
                            value = lyrics.source
                        )
                        
                        val scoreText = if (lyrics.isUserEdited) "100% (Manual)" else "${lyrics.confidenceScore}% Match"
                        val scoreColor = when {
                            lyrics.isUserEdited -> MaterialTheme.colorScheme.primary
                            lyrics.confidenceScore >= 90 -> MaterialTheme.colorScheme.primary
                            lyrics.confidenceScore >= 70 -> androidx.compose.ui.graphics.Color(0xFFE65100) // Orange
                            else -> MaterialTheme.colorScheme.error
                        }
                        
                        MetadataItem(
                            icon = Icons.Default.CheckCircle,
                            label = "Confidence Score",
                            value = scoreText,
                            valueColor = scoreColor
                        )
                    }

                    // Right Column: Sync Status, Date/Time
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (syncText, syncIcon) = when {
                            lyrics.isInstrumental -> "Instrumental (No Lyrics)" to Icons.Default.MusicNote
                            !lyrics.syncedLyrics.isNullOrEmpty() -> "Fully Synced (LRC)" to Icons.Default.Schedule
                            !lyrics.plainLyrics.isNullOrEmpty() -> "Plain Text Only" to Icons.Default.Notes
                            else -> "Empty / Missing" to Icons.Default.HelpOutline
                        }
                        
                        MetadataItem(
                            icon = syncIcon,
                            label = "Sync Type",
                            value = syncText
                        )

                        val formattedDate = remember(lyrics.fetchedAt) {
                            try {
                                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(lyrics.fetchedAt))
                            } catch (e: Exception) {
                                "Unknown"
                            }
                        }

                        MetadataItem(
                            icon = Icons.Default.History,
                            label = "Cached Timestamp",
                            value = formattedDate
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                shape = MaterialTheme.shapes.extraSmall
            ).padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}
