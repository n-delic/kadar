package com.ndelic.kadar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndelic.kadar.ui.EditorScreen
import com.ndelic.kadar.ui.HomeScreen
import com.ndelic.kadar.ui.WebImportScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(this) else darkColorScheme()
            MaterialTheme(colorScheme = colors) {
                App(viewModel())
            }
        }
    }
}

@Composable
private fun App(vm: EditorViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var browsing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.effects.collect { effect ->
                // Don't hold up the next effect while a snackbar is on screen.
                launch {
                    when (effect) {
                        is EditorEffect.Message -> snackbar.showSnackbar(effect.text)
                        is EditorEffect.Saved -> {
                            val result = snackbar.showSnackbar("Saved to gallery", actionLabel = "Share", duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) share(context, effect)
                        }
                    }
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            when {
                browsing -> WebImportScreen(
                    onPick = { url ->
                        browsing = false
                        if (state.project == null) vm.newPhotoFromUrl(url) else vm.addImageFromUrl(url)
                    },
                    onClose = { browsing = false },
                )
                state.project == null -> HomeScreen(state, vm, onBrowse = { browsing = true })
                else -> EditorScreen(state, vm, onBrowse = { browsing = true })
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
            state.busy?.let { BusyOverlay(it) }
        }
    }
}

@Composable
private fun BusyOverlay(label: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow touches so nothing changes while the task runs.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
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
