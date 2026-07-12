package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsManager
import com.example.data.repository.LyricsRepository
import com.example.data.repository.TrackRepository
import com.example.ui.AppViewModel
import com.example.ui.AppViewModelFactory
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.maxmpz.poweramp.player.PowerampAPI

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manual DI / Service Locator setup for robust, compilable Phase 1 build
        val database = AppDatabase.getDatabase(applicationContext)
        val lyricsDao = database.lyricsDao()
        val settingsManager = SettingsManager(applicationContext)
        
        val trackRepository = TrackRepository(applicationContext, lyricsDao, database.trackDao())
        val lyricsRepository = LyricsRepository(applicationContext, lyricsDao)
        
        val viewModelFactory = AppViewModelFactory(
            application = application,
            trackRepository = trackRepository,
            lyricsRepository = lyricsRepository,
            settingsManager = settingsManager
        )
        
        viewModel = ViewModelProvider(this, viewModelFactory)[AppViewModel::class.java]

        handleIntent(intent)

        lifecycleScope.launch {
            viewModel.floatingLyricsEnabled.collectLatest { enabled ->
                if (enabled) {
                    if (android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                        com.example.FloatingLyricsOverlayService.start(this@MainActivity)
                    }
                }
            }
        }

        enableEdgeToEdge()
        
        setContent {
            // Flow theme preferences dynamically from Datastore settings manager
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                dynamicColor = dynamicColor
            ) {
                val currentScreen by viewModel.currentScreen.collectAsState()

                Scaffold(
                    bottomBar = {
                        // Render bottom navigation bar only on primary top-level screens
                        if (currentScreen == "library" || currentScreen == "settings") {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentScreen == "library",
                                    onClick = { viewModel.navigateTo("library") },
                                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                                    label = { Text("Library") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == "settings",
                                    onClick = { viewModel.navigateTo("settings") },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val modifierWithPadding = Modifier.padding(innerPadding)
                    
                    // Route Manager
                    when (currentScreen) {
                        "library" -> LibraryScreen(
                            viewModel = viewModel,
                            modifier = modifierWithPadding
                        )
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            modifier = modifierWithPadding
                        )
                        "lyrics_view" -> LyricsViewScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize() // full-bleed sub-screen
                        )
                        "lyrics_editor" -> EditorScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        "immersive_lyrics" -> ImmersiveLyricsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        "search" -> SearchScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent != null && intent.action == PowerampAPI.Lyrics.ACTION_LYRICS_LINK) {
            val trackBundle = intent.getBundleExtra(PowerampAPI.EXTRA_TRACK)
            val title = trackBundle?.getString(PowerampAPI.Track.TITLE) ?: intent.getStringExtra(PowerampAPI.Track.TITLE) ?: ""
            val artist = trackBundle?.getString(PowerampAPI.Track.ARTIST) ?: intent.getStringExtra(PowerampAPI.Track.ARTIST) ?: ""
            val album = trackBundle?.getString(PowerampAPI.Track.ALBUM) ?: intent.getStringExtra(PowerampAPI.Track.ALBUM) ?: ""
            val durationMs = trackBundle?.getInt(PowerampAPI.Track.DURATION_MS, 0)?.toLong() ?: intent.getIntExtra(PowerampAPI.Track.DURATION_MS, 0).toLong()
            if (title.isNotEmpty()) {
                viewModel.handleLyricsLinkIntent(title, artist, album, durationMs)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val enabled = viewModel.floatingLyricsEnabled.value
            if (enabled && android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                com.example.FloatingLyricsOverlayService.start(this@MainActivity)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
