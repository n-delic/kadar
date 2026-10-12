package com.ndelic.kadar.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ndelic.kadar.EditorViewModel
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KIcon
import com.ndelic.kadar.ui.common.KSlider
import com.ndelic.kadar.ui.common.PanelLabel
import com.ndelic.kadar.video.VideoClip
import kotlinx.coroutines.delay

/** Under this much video left, a recording starts from the beginning instead of the playhead. */
private const val MIN_VOICE_ROOM_MS = 1000L

/**
 * Voiceover part of the Audio panel: record from the playhead while the preview plays (muted, so the speaker isn't
 * recorded too), then each recording with its start, volume and a delete button.
 */
@Composable
fun VoiceoverSection(
    clip: VideoClip,
    recordingFromMs: Long?,
    positionMs: Long,
    playing: Boolean,
    player: Player?,
    vm: EditorViewModel,
    onPlay: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    fun start() {
        val from = if (positionMs < clip.outputDurationMs - MIN_VOICE_ROOM_MS) positionMs else 0L
        if (from != positionMs) player?.seekTo(0)
        if (vm.startVoice(from)) onPlay(true)
    }
    val permission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        denied = !granted
        if (granted) start()
    }
    val recording = recordingFromMs != null
    if (recording && player != null) {
        DisposableEffect(player) {
            player.volume = 0f
            onDispose { player.volume = 1f }
        }
    }
    // The voice has to stay in step with the video: pausing ends the recording, and so does the end of the video.
    LaunchedEffect(recordingFromMs, playing) {
        if (recordingFromMs == null) return@LaunchedEffect
        if (playing) delay(clip.outputDurationMs - recordingFromMs)
        vm.stopVoice()
    }
    // Closing the panel takes the stop button away, so it stops the recording too.
    DisposableEffect(Unit) { onDispose { vm.stopVoice() } }

    PanelLabel(stringResource(R.string.voiceover))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
            when {
                recording -> vm.stopVoice()
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }.padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        val color = if (recording) Ink.coral else Ink.accent
        KIcon(if (recording) R.drawable.ic_stop else R.drawable.ic_mic, Modifier.size(22.dp), color)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(if (recording) R.string.voice_recording else R.string.voice_record, formatMs(positionMs)),
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Text(stringResource(if (denied) R.string.voice_permission_denied else R.string.voice_hint), color = Ink.dim, fontSize = 12.sp)
    clip.voices.sortedBy { it.startMs }.forEach { voice ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatMs(voice.startMs), color = Ink.text, fontSize = 12.sp, modifier = Modifier.width(52.dp))
            Box(Modifier.weight(1f)) {
                KSlider(R.drawable.ic_volume_up, voice.volume, 0f..1f, ::percent) { v ->
                    vm.updateClip { copy(voices = voices.map { if (it == voice) it.copy(volume = v) else it }) }
                }
            }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.updateClip { copy(voices = voices - voice) } }.padding(8.dp)) {
                KIcon(R.drawable.ic_delete, Modifier.size(22.dp), description = stringResource(R.string.voice_remove))
            }
        }
    }
}
