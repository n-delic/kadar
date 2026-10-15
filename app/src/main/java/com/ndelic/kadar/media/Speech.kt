package com.ndelic.kadar.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.ndelic.kadar.R
import com.ndelic.kadar.model.UserError
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** The phone's text-to-speech voices that work offline and are installed; empty when there are none (or no engine). */
suspend fun offlineVoices(context: Context): List<Voice> = withSpeech(context) { tts ->
    tts.voices.orEmpty()
        .filter { !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
}.orEmpty()

/** Speaks [text] in [voice] into [file] (WAV). */
suspend fun synthesizeSpeech(context: Context, text: String, voice: Voice, file: File) {
    val spoken = withSpeech(context) { tts ->
        val done = CompletableDeferred<Boolean>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) {
                done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                done.complete(false)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                done.complete(false)
            }
        })
        tts.setVoice(voice) == TextToSpeech.SUCCESS &&
            tts.synthesizeToFile(text, null, file, "speech") == TextToSpeech.SUCCESS &&
            done.await()
    }
    if (spoken != true || file.length() == 0L) throw UserError(R.string.error_speech)
}

/** Runs [block] on the default engine once it's ready, then shuts it down; null if the engine can't start. */
private suspend fun <T> withSpeech(context: Context, block: suspend (TextToSpeech) -> T): T? {
    val ready = CompletableDeferred<Boolean>()
    val tts = TextToSpeech(context.applicationContext) { ready.complete(it == TextToSpeech.SUCCESS) }
    try {
        return if (ready.await()) block(tts) else null
    } finally {
        tts.shutdown()
    }
}
