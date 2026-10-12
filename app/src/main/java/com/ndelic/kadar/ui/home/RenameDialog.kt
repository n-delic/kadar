package com.ndelic.kadar.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.common.Ink
import com.ndelic.kadar.ui.common.KDialog
import com.ndelic.kadar.ui.common.KTextField

/** Name field prefilled with [current], all selected so typing replaces it; [onRename] gets the text as typed. */
@Composable
fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    KDialog(stringResource(R.string.rename_title), stringResource(R.string.save), onConfirm = { onRename(value.text) }, onDismiss = onDismiss) {
        KTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = stringResource(R.string.project_name),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            background = Ink.bg,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onRename(value.text) }),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
