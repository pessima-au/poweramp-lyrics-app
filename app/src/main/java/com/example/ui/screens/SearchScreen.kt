package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.AppViewModel
import com.example.util.MatchStatus
import com.example.util.MatchingResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val track by viewModel.selectedTrack.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isFineMode by viewModel.isFineSearchMode.collectAsState()

    var expandedIndex by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Lyrics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo("lyrics_view") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    Text("Select a track from the library first.")
                }
            } else {
                // Header track details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = track!!.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track!!.artist} • ${track!!.album}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Coarse vs Fine search selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputChip(
                        selected = !isFineMode,
                        onClick = {
                            viewModel.setFineSearchMode(false)
                            viewModel.setSearchQuery("${track!!.title} ${track!!.artist}")
                            viewModel.searchLyrics()
                        },
                        label = { Text("Coarse (Auto Match)") },
                        leadingIcon = { if (!isFineMode) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    InputChip(
                        selected = isFineMode,
                        onClick = { viewModel.setFineSearchMode(true) },
                        label = { Text("Fine (Manual Search)") },
                        leadingIcon = { if (isFineMode) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Query for Fine mode
                if (isFineMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search song, artist, album...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_input_field"),
                            singleLine = true
                        )
                        Button(
                            onClick = { viewModel.searchLyrics() },
                            modifier = Modifier.testTag("search_trigger_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Searching LRCLIB...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No lyrics found. Try standardizing the title.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        text = "Results Ranked by Confidence Score:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(searchResults) { index, result ->
                            val isExpanded = index == expandedIndex
                            CandidateCard(
                                result = result,
                                isExpanded = isExpanded,
                                onCardClick = {
                                    expandedIndex = if (isExpanded) -1 else index
                                },
                                onUseClick = {
                                    viewModel.selectCandidate(result)
                                    viewModel.navigateTo("lyrics_view")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CandidateCard(
    result: MatchingResult,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onUseClick: () -> Unit
) {
    val cand = result.candidate
    val durationMin = cand.durationSeconds.toInt() / 60
    val durationSec = cand.durationSeconds.toInt() % 60
    val durationFormatted = String.format("%d:%02d", durationMin, durationSec)

    // Color code based on status
    val scoreColor = when (result.matchStatus) {
        MatchStatus.AUTO_ACCEPT -> MaterialTheme.colorScheme.primary
        MatchStatus.VERIFY -> MaterialTheme.colorScheme.secondary
        MatchStatus.MANUAL -> MaterialTheme.colorScheme.error
    }

    val scoreBgColor = when (result.matchStatus) {
        MatchStatus.AUTO_ACCEPT -> MaterialTheme.colorScheme.primaryContainer
        MatchStatus.VERIFY -> MaterialTheme.colorScheme.secondaryContainer
        MatchStatus.MANUAL -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_card_${cand.id}")
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cand.trackName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${cand.artistName} • ${cand.albumName}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Length: $durationFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Confidence badge
                Surface(
                    color = scoreBgColor,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${result.score}%",
                            color = scoreColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (result.matchStatus) {
                                MatchStatus.AUTO_ACCEPT -> "Strong"
                                MatchStatus.VERIFY -> "Verify"
                                MatchStatus.MANUAL -> "Low"
                            },
                            color = scoreColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Plain vs Synced badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Synced LRC") },
                    leadingIcon = {
                        if (cand.syncedLyrics != null) {
                            Icon(Icons.Default.Check, contentDescription = "Available", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Close, contentDescription = "Unavailable", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Plain Text") },
                    leadingIcon = {
                        if (cand.plainLyrics != null) {
                            Icon(Icons.Default.Check, contentDescription = "Available", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Close, contentDescription = "Unavailable", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Lyrics Preview:",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val previewText = cand.plainLyrics ?: cand.syncedLyrics ?: "No text content preview available."
                    val previewLines = previewText.lineSequence().take(4).joinToString("\n")
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = previewLines + "\n...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onUseClick() },
                            modifier = Modifier.testTag("apply_candidate_btn_${cand.id}")
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply & Cache Lyrics")
                        }
                    }
                }
            }
        }
    }
}
