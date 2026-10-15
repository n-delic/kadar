package com.ndelic.kadar.ui.editor

import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.KTextField
import com.ndelic.kadar.ui.common.PanelLabel
import java.util.Locale

/** Text to speech: a line to say and one of the phone's offline [voices], one in the phone's language picked first; [onAdd] speaks it. */
@Composable
fun SpeechDialog(voices: List<Voice>, onAdd: (String, Voice) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue()) }
    var voice by remember { mutableStateOf(voices.firstOrNull { it.locale.language == Locale.getDefault().language } ?: voices.first()) }
    KDialog(stringResource(R.string.speech), stringResource(R.string.add), onConfirm = { if (text.text.isNotBlank()) onAdd(text.text, voice) }, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.speech_text),
                modifier = Modifier.fillMaxWidth(),
                background = Ink.bg,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            PanelLabel(stringResource(R.string.speech_voice))
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                // Voices have engine names only, so each is its language and its number within it.
                voices.forEachIndexed { i, v ->
                    val number = voices.take(i + 1).count { it.locale == v.locale }
                    val active = v == voice
                    Text(
                        stringResource(R.string.speech_voice_label, v.locale.displayName, number),
                        color = if (active) Ink.accent else Ink.text,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { voice = v }.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
