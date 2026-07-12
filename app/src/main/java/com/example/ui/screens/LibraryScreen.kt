package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.data.model.TrackStatus
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val tracks by viewModel.tracks.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedTrackIds by viewModel.selectedTrackIds.collectAsState()
    val batchDownloading by viewModel.batchDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val tabs = listOf("All", "No Lyrics", "Cached", "Instrumental")

    val filteredTracks = remember(tracks, selectedTab) {
        when (selectedTab) {
            "No Lyrics" -> tracks.filter { it.status == TrackStatus.NO_LYRICS }
            "Cached" -> tracks.filter { it.status == TrackStatus.CACHED }
            "Instrumental" -> tracks.filter { it.status == TrackStatus.INSTRUMENTAL }
            else -> tracks
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verse Library", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTrackIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearTrackSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadTracks() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Library")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            if (selectedTrackIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Download Selected (${selectedTrackIds.size})") },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = { viewModel.startBatchDownloadSelected() },
                    modifier = Modifier.testTag("batch_download_fab")
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Row Filters
            TabRow(
                selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.setSelectedTab(tab) },
                        text = { Text(tab) }
                    )
                }
            }

            if (batchDownloading) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = downloadProgress,
                            style = MaterialTheme.styleTextBodyMedium(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isLoading && tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No songs found in this category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (viewModel.tracks.value.isEmpty()) "Poweramp track provider not detected. Playing simulation mode." else "Everything up to date!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val isSelected = selectedTrackIds.contains(track.id)
                        TrackListItem(
                            track = track,
                            isSelected = isSelected,
                            onToggleSelect = { viewModel.toggleTrackSelection(track.id) },
                            onSelectTrack = {
                                viewModel.selectTrack(track)
                                viewModel.navigateTo("lyrics_view")
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onSelectTrack: () -> Unit
) {
    val durationMin = track.durationMs / 1000 / 60
    val durationSec = (track.durationMs / 1000) % 60
    val durationFormatted = String.format("%d:%02d", durationMin, durationSec)

    ListItem(
        headlineContent = {
            Text(
                text = track.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "${track.artist} • ${track.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Status Chip / Icon
                when (track.status) {
                    TrackStatus.CACHED -> {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("Cached") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                    TrackStatus.INSTRUMENTAL -> {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("Instrumental") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                    TrackStatus.NO_LYRICS -> {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("No Lyrics") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        leadingContent = {
            if (isSelected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onToggleSelect() }
                )
            } else {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelectTrack,
                onLongClick = onToggleSelect
            )
            .testTag("track_item_${track.id}")
    )
}

@Composable
fun MaterialTheme.styleTextBodyMedium() = typography.bodyMedium
