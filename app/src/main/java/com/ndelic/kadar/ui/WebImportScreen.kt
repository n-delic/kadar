package com.ndelic.kadar.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ndelic.kadar.R
import java.net.URLEncoder

/** A plain browser: search or open any page, long-press an image to download it into the project (never the gallery). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(onPick: (String) -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }

    BackHandler { webView?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }

    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            CircleButton(R.drawable.ic_close, "Close", onClick = onClose)
            Spacer(Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Ink.raised).padding(horizontal = 14.dp),
            ) {
                KIcon(R.drawable.ic_search, Modifier.size(20.dp), Ink.dim)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(Ink.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) webView?.loadUrl(searchUrl(query)) }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        if (query.isEmpty()) Text("Search images or paste a link", color = Ink.dim, fontSize = 15.sp)
                        field()
                    },
                )
            }
        }
        Text(
            "Tap a result to open it full size, then long-press it to add",
            color = Ink.dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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
        KDialog(
            title = "Add this image?",
            text = "It's saved inside the project only, not in your gallery.",
            confirm = "Add",
            onConfirm = {
                picked = null
                onPick(url)
            },
            onDismiss = { picked = null },
        )
    }
}

private fun searchUrl(query: String): String {
    val q = query.trim()
    if (q.startsWith("https://") || q.startsWith("http://")) return q
    return "https://www.google.com/search?udm=2&q=" + URLEncoder.encode(q, "UTF-8")
}
