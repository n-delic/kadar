package com.ndelic.kadar.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.EditorUiState
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.SavedProject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: EditorUiState, vm: EditorViewModel, onBrowse: () -> Unit) {
    val photo = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::newPhoto) }
    val video = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(vm::newVideo) }
    var deleting by remember { mutableStateOf<SavedProject?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kadar", style = MaterialTheme.typography.displaySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { photo.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, Modifier.weight(1f)) { Text("Photo") }
                    Button(onClick = { video.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly)) }, Modifier.weight(1f)) { Text("Video") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::newBlank, Modifier.weight(1f)) { Text("Blank canvas") }
                    OutlinedButton(onClick = onBrowse, Modifier.weight(1f)) { Text("Image from web") }
                }
                Text(
                    if (state.projects.isEmpty()) "Your projects will show up here." else "Projects",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(state.projects, key = { it.dir.path }) { p ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { vm.openProject(p.dir) }, onLongClick = { deleting = p }),
            ) {
                val thumb = p.thumb
                if (thumb != null) {
                    Image(
                        thumb.asImageBitmap(),
                        contentDescription = "Project",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Text(
                    DateUtils.getRelativeTimeSpanString(p.modified).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete project?") },
            text = { Text("Its images are deleted too. Things you saved to the gallery stay.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProject(p.dir)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
