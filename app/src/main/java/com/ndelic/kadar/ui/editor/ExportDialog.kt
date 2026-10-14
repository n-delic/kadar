package com.ndelic.kadar.ui.editor

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.model.AppSettings
import com.ndelic.kadar.model.ExportQuality
import com.ndelic.kadar.model.PhotoFormat
import com.ndelic.kadar.model.Project
import com.ndelic.kadar.model.transparent
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.ui.common.TextTabs
import com.ndelic.kadar.video.GIF_FPS
import com.ndelic.kadar.video.GIF_LONG_SIDE
import com.ndelic.kadar.video.GIF_MAX_MS
import com.ndelic.kadar.video.estimatedSize
import com.ndelic.kadar.video.outputFrameRate

/** The export choices before saving or [share]-ing: JPG or PNG for a photo; MP4 (frame rate, quality, size) or GIF for a video. */
@Composable
fun ExportDialog(project: Project, settings: AppSettings, share: Boolean, vm: EditorViewModel, onDismiss: () -> Unit) {
    val video = project.video
    val confirm = stringResource(if (share) R.string.share else R.string.save)
    val title = stringResource(if (share) R.string.share else R.string.save_to_gallery)
    if (video == null) {
        var format by remember { mutableStateOf(settings.photoFormat) }
        KDialog(title, confirm, onConfirm = { onDismiss(); vm.exportPhoto(share, format) }, onDismiss = onDismiss) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelLabel(stringResource(R.string.export_format))
                if (project.transparent) {
                    Text(stringResource(R.string.export_png_only), color = Ink.dim)
                } else {
                    TextTabs(PhotoFormat.entries, format, { stringResource(it.label) }) { format = it }
                }
            }
        }
    } else {
        var gif by rememberSaveable { mutableStateOf(false) }
        var fps by rememberSaveable { mutableIntStateOf(outputFrameRate(video.frameRate)) }
        var quality by rememberSaveable { mutableStateOf(settings.exportQuality) }
        val onConfirm = {
            onDismiss()
            if (gif) vm.exportGif(share) else vm.exportVideo(share, fps, quality)
        }
        KDialog(title, confirm, onConfirm = onConfirm, onDismiss = onDismiss) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelLabel(stringResource(R.string.export_format))
                TextTabs(listOf(false, true), gif, { stringResource(if (it) R.string.format_gif else R.string.format_mp4) }) { gif = it }
                if (gif) {
                    Text(stringResource(R.string.gif_hint, GIF_MAX_MS / 1000, GIF_LONG_SIDE, GIF_FPS), color = Ink.dim, fontSize = 13.sp)
                    if (video.outputDurationMs > GIF_MAX_MS) Text(stringResource(R.string.gif_too_long, GIF_MAX_MS / 1000), color = Ink.accent, fontSize = 13.sp)
                } else {
                    PanelLabel(stringResource(R.string.export_frame_rate))
                    TextTabs((FRAME_RATES + outputFrameRate(video.frameRate)).distinct().sorted(), fps, { it.toString() }) { fps = it }
                    PanelLabel(stringResource(R.string.settings_quality))
                    TextTabs(ExportQuality.entries, quality, { stringResource(it.label) }) { quality = it }
                    val bytes = video.estimatedSize(vm.exportShortSide, fps, quality)
                    Text(stringResource(R.string.export_estimate, Formatter.formatShortFileSize(LocalContext.current, bytes)), color = Ink.dim, fontSize = 13.sp)
                }
            }
        }
    }
}

private val FRAME_RATES = listOf(24, 25, 30, 50, 60)
