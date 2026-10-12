package com.ndelic.kadar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KadarTheme
import com.ndelic.kadar.ui.common.blockTouches
import com.ndelic.kadar.ui.editor.EditorScreen
import com.ndelic.kadar.ui.editor.label
import com.ndelic.kadar.ui.home.HomeScreen
import com.ndelic.kadar.ui.home.Intro
import com.ndelic.kadar.ui.home.SettingsScreen
import com.ndelic.kadar.ui.web.WebImportScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only the first launch: after rotation the same share intent would open a second copy.
        if (savedInstanceState == null) openShared(intent)
        // Light status and navigation icons: the app is always dark, whatever the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            KadarTheme {
                App(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openShared(intent)
    }

    private fun openShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        vm.openShared(uri, intent.type ?: contentResolver.getType(uri) ?: "image/*")
    }
}

@Composable
private fun App(vm: EditorViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var browsing by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Only on a cold start, not after rotation.
    var intro by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.effects.collect { effect ->
                // Don't hold up the next effect while a snackbar is on screen.
                launch {
                    when (effect) {
                        is EditorEffect.Message -> snackbar.showSnackbar(effect.text)
                        is EditorEffect.Saved -> if (effect.share) {
                            share(context, effect)
                        } else {
                            val result = snackbar.showSnackbar("Saved to gallery", actionLabel = "Share", duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) share(context, effect)
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.bg)) {
        // System bars only: the keyboard must not shrink the editor (the video would resize behind the text entry).
        // Screens with a text field add imePadding() themselves.
        Box(Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
            when {
                settingsOpen -> SettingsScreen(state.settings, onChange = { s -> vm.updateSettings { s } }, onClose = { settingsOpen = false })
                browsing -> WebImportScreen(
                    engine = state.settings.search,
                    onPick = { url ->
                        browsing = false
                        if (state.project == null) vm.newPhotoFromUrl(url) else vm.addImageFromUrl(url)
                    },
                    onClose = { browsing = false },
                )
                state.project == null -> HomeScreen(state, vm, onBrowse = { browsing = true }, onSettings = { settingsOpen = true })
                else -> EditorScreen(state, vm, onBrowse = { browsing = true })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { data ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(24.dp)).background(Ink.raised).padding(start = 18.dp, end = 6.dp),
                ) {
                    Text(data.visuals.message, color = Ink.text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp).weight(1f, fill = false))
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = data::performAction) { Text(label, color = Ink.accent, fontWeight = FontWeight.Bold) }
                    } ?: Spacer(Modifier.width(12.dp))
                }
            }
            state.busy?.let { BusyOverlay(it) }
        }
        if (intro) Intro(ready = state.projectsLoaded, onDone = { intro = false })
    }
}

@Composable
private fun BusyOverlay(label: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Swallow touches so nothing changes while the task runs.
            .blockTouches(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Ink.raised).padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            CircularProgressIndicator(color = Ink.accent, trackColor = Ink.raised)
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun share(context: Context, saved: EditorEffect.Saved) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(saved.mime)
        .putExtra(Intent.EXTRA_STREAM, saved.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
