package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
    val tabs = listOf("Timeline Sync", "Plain Text Edit")

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
                if (track == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No track selected.")
                    }
                } else {
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
