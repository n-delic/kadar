package com.ndelic.kadar.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

/** A plain browser: search or open any page, long-press an image to download it into the project (never the gallery). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(onPick: (String) -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }

    BackHandler { webView?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search images or paste a link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) webView?.loadUrl(searchUrl(query)) }),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Tap a result to open it full size, then long-press it to add",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    setOnLongClickListener {
                        val hit = hitTestResult
                        val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        if (isImage && hit.extra != null) picked = hit.extra
                        isImage
                    }
                    loadUrl("https://www.google.com/imghp")
                    webView = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }

    picked?.let { url ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("Add this image?") },
            text = { Text("It's saved inside the project only, not in your gallery.") },
            confirmButton = {
                TextButton(onClick = {
                    picked = null
                    onPick(url)
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Cancel") } },
        )
    }
}

private fun searchUrl(query: String): String {
    val q = query.trim()
    if (q.startsWith("https://") || q.startsWith("http://")) return q
    return "https://www.google.com/search?udm=2&q=" + URLEncoder.encode(q, "UTF-8")
}
