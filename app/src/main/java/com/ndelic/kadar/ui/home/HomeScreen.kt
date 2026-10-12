package com.ndelic.kadar.ui.home

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.BuildConfig
import com.ndelic.kadar.EditorUiState
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.model.SavedProject
import com.ndelic.kadar.ui.common.CircleButton
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.editor.label

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: EditorUiState, vm: EditorViewModel, onBrowse: () -> Unit, onSettings: () -> Unit) {
    val photo = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::newPhoto) }
    val video = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::newVideo) }
    // Paths of the selected projects; non-empty means selection mode.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val picked = state.projects.filter { it.dir.path in selected }
    fun toggle(p: SavedProject) {
        selected = if (p.dir.path in selected) selected - p.dir.path else selected + p.dir.path
    }
    // Projects can disappear (deleted elsewhere); keep the selection to ones that exist.
    LaunchedEffect(state.projects) { selected = selected.filter { path -> state.projects.any { it.dir.path == path } } }
    BackHandler(enabled = selecting) { selected = emptyList() }

    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(Ink.bg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = if (selecting) 110.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_mark), contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Kadar", color = Ink.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(6.dp))
                    // Superscript: small type hanging off the top of the name, no box.
                    Column(Modifier.align(Alignment.Top).padding(top = 3.dp)) {
                        if (BuildConfig.STAGE.isNotEmpty()) {
                            Text(BuildConfig.STAGE.uppercase(), color = Ink.accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, lineHeight = 10.sp)
                        }
                        Text("v${BuildConfig.VERSION_NAME}", color = Ink.dim, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    CircleButton(R.drawable.ic_settings, "Settings", onClick = onSettings)
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
            val isPicked = p.dir.path in selected
            Column(
                Modifier.combinedClickable(
                    onClick = { if (selecting) toggle(p) else vm.openProject(p.dir) },
                    onLongClick = { toggle(p) },
                ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.raised)
                        .border(if (isPicked) 3.dp else 0.dp, if (isPicked) Ink.accent else Color.Transparent, RoundedCornerShape(14.dp)),
                ) {
                    p.thumb?.let { Image(it.asImageBitmap(), contentDescription = "Project", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Ink.accent)
                            .padding(start = 5.dp, end = 7.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KIcon(if (p.isVideo) R.drawable.ic_movie else R.drawable.ic_image, Modifier.size(12.dp), Ink.onAccent)
                        Spacer(Modifier.width(4.dp))
                        Text(if (p.isVideo) "VIDEO" else "PHOTO", color = Ink.onAccent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                    }
                    if (selecting) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isPicked) Ink.accent else Color.Black.copy(alpha = 0.45f))
                                .border(1.5.dp, if (isPicked) Ink.accent else Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPicked) KIcon(R.drawable.ic_check, Modifier.size(15.dp), Ink.onAccent, "Selected")
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


    AnimatedVisibility(
        visible = selecting,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Ink.raised)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { selected = emptyList() }, contentAlignment = Alignment.Center) {
                KIcon(R.drawable.ic_close, Modifier.size(22.dp), description = "Cancel selection")
            }
            Text("${picked.size} selected", color = Ink.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            BarAction(R.drawable.ic_select_all, "All") { selected = state.projects.map { it.dir.path } }
            BarAction(R.drawable.ic_content_copy, "Duplicate") {
                vm.duplicateProjects(picked.map { it.dir })
                selected = emptyList()
            }
            BarAction(R.drawable.ic_delete, "Delete", tint = Color(0xFFFF5A6E)) { confirmDelete = true }
        }
    }
    }

    if (confirmDelete) {
        val n = picked.size
        KDialog(
            title = if (n == 1) "Delete project?" else "Delete $n projects?",
            text = "Their images are deleted too. Things you saved to the gallery stay.",
            confirm = "Delete",
            onConfirm = {
                vm.deleteProjects(picked.map { it.dir })
                selected = emptyList()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun BarAction(icon: Int, label: String, tint: Color = Ink.text, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        KIcon(icon, Modifier.size(22.dp), tint)
        Text(label, color = tint, fontSize = 11.sp)
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
