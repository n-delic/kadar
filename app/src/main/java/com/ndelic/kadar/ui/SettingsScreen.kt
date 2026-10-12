package com.ndelic.kadar.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.AppSettings
import com.ndelic.kadar.BuildConfig
import com.ndelic.kadar.ExportSize
import com.ndelic.kadar.R
import com.ndelic.kadar.SearchEngine

private const val REPO = "https://github.com/n-delic/kadar"

@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var license by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (license != null) license = null else onClose() }
    val licenses = remember { context.assets.list("").orEmpty().filter { it.endsWith(".txt") }.sorted() }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(Modifier.fillMaxSize().background(Ink.bg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(R.drawable.ic_close, "Close") { if (license != null) license = null else onClose() }
            Spacer(Modifier.width(14.dp))
            Text(license?.removeSuffix(".txt") ?: "Settings", color = Ink.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        val shown = license
        if (shown != null) {
            val text = remember(shown) { context.assets.open(shown).bufferedReader().use { it.readText() } }
            Text(
                text,
                color = Ink.dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            )
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("Image search", "Used by the built-in browser for Image from web.")
            Card {
                SearchEngine.entries.forEach { e ->
                    Choice(e.label, selected = settings.search == e) { onChange(settings.copy(search = e)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Section("Default export size", "By the short side; never upscales. Each project can override it in its Canvas panel.")
            Card {
                ExportSize.entries.forEach { s ->
                    Choice(s.label, selected = settings.exportSize == s) { onChange(settings.copy(exportSize = s)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Section("About", null)
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Version", color = Ink.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(listOf(BuildConfig.VERSION_NAME, BuildConfig.STAGE).filter { it.isNotEmpty() }.joinToString(" "), color = Ink.dim, fontSize = 15.sp)
                }
                Link("Source code", R.drawable.ic_open_in_new) { open(REPO) }
                Link("License: GPL-3.0-or-later", R.drawable.ic_open_in_new) { open("$REPO/blob/main/LICENSE") }
            }
            Spacer(Modifier.height(12.dp))
            Section("Open-source licenses", "Fonts and icons bundled in the app.")
            Card {
                licenses.forEach { name -> Link(name.removeSuffix(".txt"), R.drawable.ic_chevron_right) { license = name } }
            }
            Text(
                "Kadar is free software with no ads, accounts or tracking.",
                color = Ink.dim,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)) {
        PanelLabel(title)
        subtitle?.let { Text(it, color = Ink.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.panel)) { content() }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Ink.accent else Ink.line, CircleShape).padding(4.dp),
        ) {
            if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink.accent))
        }
    }
}

@Composable
private fun Link(label: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        KIcon(icon, Modifier.size(18.dp), Ink.dim)
    }
}
