package com.ndelic.kadar.model

import android.graphics.Color
import androidx.annotation.StringRes
import com.ndelic.kadar.R

/** Kadar's own text looks, each setting several text properties at once. */
enum class TextPreset(@StringRes val label: Int) {
    Meme(R.string.preset_meme), Caption(R.string.preset_caption), Neon(R.string.preset_neon),
    Title(R.string.preset_title), Subtitle(R.string.preset_subtitle), Speech(R.string.preset_speech),
}

/**
 * This text in [preset]'s look, its fonts picked by name from [fonts] (the current one if missing). The text, size,
 * alignment, wrap, line spacing, bold, italic, underline, caps, place and timing stay; every other look property is set, so presets don't mix.
 */
fun TextLayer.styled(preset: TextPreset, fonts: List<Font>): TextLayer {
    fun font(name: String) = fonts.firstOrNull { it.name == name } ?: font
    val plain = copy(outline = null, box = null, bubble = Bubble.Box, boxOutline = null, tail = null, shadow = null, spacing = 0f, glow = null, opacity = 1f,
        boxOpacity = 1f, outlineWidth = 0.16f, shadowBlur = 0.08f, shadowDistance = 0.04f,
    )
    return when (preset) {
        TextPreset.Meme -> plain.copy(font = font("Meme"), color = Color.WHITE, outline = Color.BLACK)
        TextPreset.Caption -> plain.copy(font = font("Heavy"), color = Color.BLACK, box = Color.WHITE)
        TextPreset.Neon -> plain.copy(font = font("Retro"), color = 0xFFFFF2FB.toInt(), glow = 0xFFFF2BD6.toInt(), glowSize = 0.6f)
        TextPreset.Title -> plain.copy(font = font("Tall"), color = Color.WHITE, shadow = DEFAULT_SHADOW, spacing = 0.06f)
        TextPreset.Subtitle -> plain.copy(font = font("Sans"), color = Color.WHITE, box = Color.BLACK, boxOpacity = 0.7f)
        TextPreset.Speech -> plain.copy(font = font("Casual"), color = Color.BLACK, box = Color.WHITE, bubble = Bubble.Speech, boxOutline = Color.BLACK)
    }
}
