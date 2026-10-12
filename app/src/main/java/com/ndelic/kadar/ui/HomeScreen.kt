package com.ndelic.kadar.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.BuildConfig
import com.ndelic.kadar.EditorUiState
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.SavedProject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: EditorUiState, vm: EditorViewModel, onBrowse: () -> Unit) {
    val photo = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::newPhoto) }
    val video = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::newVideo) }
    var deleting by remember { mutableStateOf<SavedProject?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(Ink.bg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Ink.accent))
                    Spacer(Modifier.width(10.dp))
                    Text("Kadar", color = Ink.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOf("v${BuildConfig.VERSION_NAME}", BuildConfig.STAGE).filter { it.isNotEmpty() }.joinToString(" "),
                        color = Ink.dim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.raised).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text("Photos and videos, edited on your phone. No account, no watermark.", color = Ink.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StartCard(R.drawable.ic_image, "Photo", "Cut-outs, text, stickers", accent = true, Modifier.weight(1f)) {
                        photo.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    }
                    StartCard(R.drawable.ic_movie, "Video", "Trim, speed, music", accent = false, Modifier.weight(1f)) {
                        video.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(R.drawable.ic_crop_square, "Blank canvas", Modifier.weight(1f), vm::newBlank)
                    QuickAction(R.drawable.ic_language, "Image from web", Modifier.weight(1f), onBrowse)
                }
                Spacer(Modifier.height(30.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Projects", color = Ink.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (state.projects.isNotEmpty()) Text("  ${state.projects.size}", color = Ink.dim, fontSize = 15.sp)
                }
                if (state.projects.isEmpty()) {
                    Text("Nothing yet. Everything you make is saved here automatically.", color = Ink.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        items(state.projects, key = { it.dir.path }) { p ->
            Column(Modifier.combinedClickable(onClick = { vm.openProject(p.dir) }, onLongClick = { deleting = p })) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Ink.raised)) {
                    p.thumb?.let { Image(it.asImageBitmap(), contentDescription = "Project", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    if (p.isVideo) {
                        Box(Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(4.dp)) {
                            KIcon(R.drawable.ic_movie, Modifier.size(14.dp), description = "Video")
                        }
                    }
                }
                Text(
                    DateUtils.getRelativeTimeSpanString(p.modified).toString(),
                    color = Ink.dim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                )
            }
        }
    }

    deleting?.let { p ->
        KDialog(
            title = "Delete project?",
            text = "Its images are deleted too. Things you saved to the gallery stay.",
            confirm = "Delete",
            onConfirm = {
                vm.deleteProject(p.dir)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Big start card: icon chip, title and what it's for. The first one carries the accent. */
@Composable
private fun StartCard(icon: Int, title: String, subtitle: String, accent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (accent) Ink.accent else Ink.panel)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (accent) Ink.onAccent else Ink.raised),
            contentAlignment = Alignment.Center,
        ) {
            KIcon(icon, Modifier.size(24.dp), if (accent) Ink.accent else Ink.text)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = if (accent) Ink.onAccent else Ink.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = if (accent) Ink.onAccent.copy(alpha = 0.7f) else Ink.dim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun QuickAction(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Ink.panel).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIcon(icon, Modifier.size(20.dp), Ink.dim)
        Spacer(Modifier.width(10.dp))
        Text(label, color = Ink.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
