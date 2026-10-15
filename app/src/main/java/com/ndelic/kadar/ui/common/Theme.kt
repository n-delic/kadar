package com.ndelic.kadar.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.editor.label

/** Kadar's palette: near-black so photos and video carry the color, one lime accent, teal for secondary marks. */
object Ink {
    val bg = Color(0xFF0E0F11)
    val panel = Color(0xFF18191C)
    val raised = Color(0xFF25272B)
    val line = Color(0xFF383A40)
    val text = Color(0xFFFFFFFF)
    val dim = Color(0xFF9B9EA6)
    val accent = Color(0xFFC6F432)
    /** Text and icons drawn on top of [accent]. */
    val onAccent = Color(0xFF0E0F11)
    val teal = Color(0xFF2DE1C2)
    /** Video layer tracks. */
    val violet = Color(0xFFB79CFF)
    /** Drawing tracks. */
    val coral = Color(0xFFFF8A65)
    /** Shape tracks. */
    val sky = Color(0xFF6FB7FF)
}

/** Swatches for text, outline and background: neutrals first, then warm to cool. */
val SWATCHES = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFF424242, 0xFFFFCDD2, 0xFFFF8A80, 0xFFFF5252, 0xFFFF1744,
    0xFFD50000, 0xFFFF9100, 0xFFFFEA00, 0xFF00E676, 0xFF1DE9B6, 0xFF00B0FF, 0xFF2979FF, 0xFF3D5AFE,
    0xFFD500F9, 0xFFF50057, 0xFF8D6E63,
).map { it.toInt() }

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(R.font.jakarta, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Plus Jakarta Sans (variable, OFL) for all UI text; layer text keeps its own fonts. */
val UiFont = FontFamily(listOf(400, 500, 600, 700, 800).map(::jakarta))

private val KadarType = Typography().run {
    fun TextStyle.ui() = copy(fontFamily = UiFont)
    Typography(
        displayLarge.ui(), displayMedium.ui(), displaySmall.ui(), headlineLarge.ui(), headlineMedium.ui(), headlineSmall.ui(),
        titleLarge.ui(), titleMedium.ui(), titleSmall.ui(), bodyLarge.ui(), bodyMedium.ui(), bodySmall.ui(),
        labelLarge.ui(), labelMedium.ui(), labelSmall.ui(),
    )
}

@Composable
fun KadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = KadarType,
        colorScheme = darkColorScheme(
            primary = Ink.accent,
            onPrimary = Ink.onAccent,
            background = Ink.bg,
            onBackground = Ink.text,
            surface = Ink.bg,
            onSurface = Ink.text,
            surfaceVariant = Ink.raised,
            onSurfaceVariant = Ink.dim,
            surfaceContainerHigh = Ink.raised,
            outline = Ink.line,
        ),
        content = content,
    )
}

@Composable
fun KIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = Ink.text, description: String? = null) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = modifier)
}

/** A bottom-toolbar entry: line icon over a small label. */
@Composable
fun IconTool(@DrawableRes icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    val tint = if (active) Ink.accent else Ink.text
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        KIcon(icon, Modifier.size(26.dp), tint)
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (active) Ink.accent else Ink.dim, fontSize = 11.sp, maxLines = 1)
    }
}

/** Round dark button used in the top bars. */
@Composable
fun CircleButton(@DrawableRes icon: Int, description: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Ink.accent else Ink.raised)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        KIcon(icon, Modifier.size(24.dp), if (active) Ink.onAccent else if (enabled) Ink.text else Ink.line, description)
    }
}

/** Text-only tabs with a dot under the selected one. */
@Composable
fun <T> TextTabs(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(options) { o ->
            val active = o == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(o) }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label(o), color = if (active) Ink.text else Ink.dim, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (active) Ink.text else Color.Transparent))
            }
        }
    }
}

/**
 * A row of color dots; [allowNone] adds a crossed-out dot that picks null. Any other color comes from the custom picker
 * or, where [LocalEyedropper] is provided, from the picture; those are remembered in the row for a while.
 */
@Composable
fun ColorRow(selected: Int?, allowNone: Boolean, onPick: (Int?) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val eyedropper = LocalEyedropper.current
    fun pickCustom(c: Int) {
        rememberColor(c)
        onPick(c)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (allowNone) item { ColorDot(null, selected == null) { onPick(null) } }
        item { CustomColorDot(selected?.takeIf { it !in SWATCHES && it !in recentColors }) { custom = true } }
        eyedropper?.let { start -> item { EyedropperDot { start(::pickCustom) } } }
        items(recentColors.toList()) { c -> ColorDot(c, selected == c) { onPick(c) } }
        items(SWATCHES) { c -> ColorDot(c, selected == c) { onPick(c) } }
    }
    if (custom) {
        CustomColorDialog(selected ?: android.graphics.Color.WHITE, onDismiss = { custom = false }) { c ->
            custom = false
            pickCustom(c)
        }
    }
}

@Composable
private fun ColorDot(color: Int?, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .border(2.dp, if (active) Ink.accent else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color?.let { Color(it) } ?: Ink.raised)
            .border(1.dp, Ink.line, CircleShape)
            .drawBehind {
                if (color == null) drawLine(Color(0xFFFF5252), Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.8f, size.height * 0.2f), 2.dp.toPx())
            }
            .clickable(onClick = onClick),
    )
}

/** Thin-track slider that commits only when the finger lifts (one undo step); [onDrag] gets the value meanwhile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KSlider(
    @DrawableRes icon: Int?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onDrag: (Float) -> Unit = {},
    onCommit: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.let { KIcon(it, Modifier.size(22.dp), Ink.dim) }
        Slider(
            value = current,
            onValueChange = {
                current = it
                onDrag(it)
            },
            valueRange = range,
            onValueChangeFinished = { onCommit(current) },
            modifier = Modifier.weight(1f),
            thumb = { Box(Modifier.size(18.dp).clip(CircleShape).background(Ink.text)) },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    modifier = Modifier.height(3.dp),
                    colors = SliderDefaults.colors(activeTrackColor = Ink.text, inactiveTrackColor = Ink.line),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        Text(valueLabel(current), color = Ink.dim, fontSize = 12.sp, modifier = Modifier.width(48.dp))
    }
}

/** Canvas shapes (width / height) for photos, videos and a blank canvas. */
val CANVAS_RATIOS = listOf(
    "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "2:3" to 2f / 3, "9:16" to 9f / 16, "1:2" to 0.5f,
    "16:9" to 16f / 9, "4:3" to 4f / 3, "3:2" to 1.5f, "2:1" to 2f, "2.35:1" to 2.35f,
)

/** A box drawn in the target aspect ratio, so the choice reads at a glance. */
@Composable
fun AspectTile(label: String, ratio: Float, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Ink.accent else Ink.dim
    val (w, h) = if (ratio >= 1) 36f to 36f / ratio else 36f * ratio to 36f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(w.dp, h.dp).border(2.dp, color, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 11.sp)
    }
}

/** A setting row with a small pill switch on the right. */
@Composable
fun KToggle(title: String, subtitle: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!on) }.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = Ink.dim, fontSize = 12.sp) }
        }
        Box(
            Modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape).background(if (on) Ink.accent else Ink.line).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Ink.text))
        }
    }
}

@Composable
fun PanelLabel(text: String) {
    Text(text.uppercase(), color = Ink.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
fun KDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    KDialog(title, confirm, onConfirm, onDismiss) { Text(text, color = Ink.dim) }

/** [KDialog] with any [content] in place of the message, such as a [KTextField]. */
@Composable
fun KDialog(title: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.raised,
        title = { Text(title, color = Ink.text) },
        text = content,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Ink.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Ink.dim) } },
    )
}

/** Single-line pill field with an optional leading [icon]; [placeholder] shows when empty and names it for TalkBack. */
@Composable
fun KTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    background: Color = Ink.raised,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(background).padding(horizontal = 14.dp),
    ) {
        icon?.let {
            KIcon(it, Modifier.size(20.dp), Ink.dim)
            Spacer(Modifier.width(10.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink.text, fontSize = 15.sp, fontFamily = UiFont),
            cursorBrush = SolidColor(Ink.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { field ->
                if (value.text.isEmpty()) Text(placeholder, color = Ink.dim, fontSize = 15.sp)
                field()
            },
        )
    }
}

/** Makes a full-screen layer swallow touches meant for what's underneath. */
fun Modifier.blockTouches() = clickable(interactionSource = null, indication = null) {}

fun Modifier.fullWidthPanel() = fillMaxWidth().background(Ink.panel).padding(horizontal = 16.dp, vertical = 12.dp)
