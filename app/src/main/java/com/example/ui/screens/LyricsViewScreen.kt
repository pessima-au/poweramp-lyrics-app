package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsViewScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val track by viewModel.selectedTrack.collectAsState()
    val lyrics by viewModel.selectedTrackLyrics.collectAsState()
    val lrcLines by viewModel.lrcLines.collectAsState()
    val plainLyrics by viewModel.plainLyrics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Gemini states
    val aiResult by viewModel.aiResult.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Song Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("library") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (lyrics != null) {
                        IconButton(
                            onClick = {
                                viewModel.deleteLyrics(lyrics!!.trackKey)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Local Cache")
                        }
                    }
                },
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
                .padding(16.dp)
        ) {
            if (track == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No song selected.")
                }
            } else {
                // Track information card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track!!.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track!!.artist} • ${track!!.album}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.navigateTo("immersive_lyrics") },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("launch_immersive_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Immersive Now Playing")
                    }

                    OutlinedButton(
                        onClick = { viewModel.navigateTo("lyrics_editor") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_lyrics_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.setSearchQuery("${track!!.title} ${track!!.artist}")
                            viewModel.navigateTo("search")
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("find_alternates_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Search Source")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lyrics Panel Container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (lyrics == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SentimentDissatisfied,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No cached lyrics found for this track.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = { viewModel.fetchLyricsForCurrentTrack() },
                                    modifier = Modifier.testTag("auto_fetch_lyrics_btn")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto-Download (LRCLIB)")
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                TextButton(
                                    onClick = { viewModel.requestSearchAssist() },
                                    modifier = Modifier.testTag("search_assist_ai_btn")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ask Gemini Search Assist")
                                }

                                if (isAiLoading) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }

                                if (aiResult != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Gemini Search Assist",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Row {
                                                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                                    val context = androidx.compose.ui.platform.LocalContext.current
                                                    IconButton(
                                                        onClick = {
                                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(aiResult!!))
                                                            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp).testTag("copy_ai_result_btn")
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Result", modifier = Modifier.size(16.dp))
                                                     }
                                                     Spacer(modifier = Modifier.width(8.dp))
                                                     IconButton(
                                                         onClick = { viewModel.clearAiResult() },
                                                         modifier = Modifier.size(24.dp)
                                                     ) {
                                                         Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                                     }
                                                 }
                                             }
                                             Spacer(modifier = Modifier.height(8.dp))
                                             androidx.compose.foundation.text.selection.SelectionContainer {
                                                 Text(
                                                     text = aiResult!!,
                                                     style = MaterialTheme.typography.bodySmall,
                                                     modifier = Modifier.fillMaxWidth()
                                                 )
                                             }
                                             
                                             // Find URL
                                             val urlPattern = """https?://[^\s]+""".toRegex()
                                             val match = urlPattern.find(aiResult!!)
                                             val foundUrl = match?.value
                                             
                                             if (foundUrl != null) {
                                                 Spacer(modifier = Modifier.height(12.dp))
                                                 val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                                 Button(
                                                     onClick = {
                                                         try {
                                                             uriHandler.openUri(foundUrl)
                                                         } catch (e: Exception) {
                                                             // handle invalid url formatting
                                                         }
                                                     },
                                                     modifier = Modifier.fillMaxWidth().testTag("open_source_link_btn")
                                                 ) {
                                                     Icon(Icons.Default.OpenInNew, contentDescription = null)
                                                     Spacer(modifier = Modifier.width(8.dp))
                                                     Text("Open Source Link")
                                                 }
                                             }
                                             
                                             Spacer(modifier = Modifier.height(8.dp))
                                             OutlinedButton(
                                                 onClick = { viewModel.navigateTo("lyrics_editor") },
                                                 modifier = Modifier.fillMaxWidth().testTag("go_to_editor_btn")
                                             ) {
                                                 Icon(Icons.Default.Edit, contentDescription = null)
                                                 Spacer(modifier = Modifier.width(8.dp))
                                                 Text("Open Lyrics Editor to Paste")
                                             }
                                         }
                                     }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Info / Confidence Rating Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Match Confidence: ${lyrics!!.confidenceScore}%") },
                                        icon = {
                                            Icon(
                                                imageVector = if (lyrics!!.confidenceScore >= 70) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (lyrics!!.confidenceScore >= 70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    )
                                    
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("Source: ${lyrics!!.source}") }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Render Lyrics
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (lyrics!!.isInstrumental) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "♫ This song is flagged as instrumental. No vocals exist.",
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        val displayLyrics = plainLyrics.ifBlank {
                                            lrcLines.joinToString("\n") { it.text }
                                        }
                                        Text(
                                            text = displayLyrics,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 8.dp)
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
}
