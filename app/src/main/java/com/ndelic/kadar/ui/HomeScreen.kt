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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Text("Kadar", color = Ink.text, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(28.dp))
                PanelLabel("Create new")
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CreateTile(R.drawable.ic_movie, "Video", Color(0xFFFF6FA5), Color(0xFFFF2D6F)) {
                        video.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
                    }
                    CreateTile(R.drawable.ic_image, "Photo", Color(0xFFFF8A72), Color(0xFFFF4D5E)) {
                        photo.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    }
                    CreateTile(R.drawable.ic_crop_square, "Blank", Color(0xFFFFB25C), Color(0xFFFF7A35), vm::newBlank)
                    CreateTile(R.drawable.ic_language, "Web image", Color(0xFFC07BFF), Color(0xFF7A4DFF), onBrowse)
                }
                Spacer(Modifier.height(32.dp))
                PanelLabel("Projects")
                if (state.projects.isEmpty()) {
                    Text("Nothing yet. Everything you make is saved here automatically.", color = Ink.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(state.projects, key = { it.dir.path }) { p ->
            Column(Modifier.combinedClickable(onClick = { vm.openProject(p.dir) }, onLongClick = { deleting = p })) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Ink.raised)) {
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

/** InShot-style round gradient button with a label under it. */
@Composable
private fun CreateTile(icon: Int, label: String, from: Color, to: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Brush.linearGradient(listOf(from, to))),
            contentAlignment = Alignment.Center,
        ) {
            KIcon(icon, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Ink.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
