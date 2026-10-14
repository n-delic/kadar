package com.ndelic.kadar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.common.AspectTile
import com.ndelic.kadar.ui.common.CANVAS_RATIOS
import com.ndelic.kadar.ui.common.KDialog

/** Picks the shape of a new blank canvas (square to start); [onCreate] gets its width / height. */
@Composable
fun BlankDialog(onCreate: (Float) -> Unit, onDismiss: () -> Unit) {
    var ratio by remember { mutableFloatStateOf(1f) }
    KDialog(stringResource(R.string.home_blank), stringResource(R.string.blank_create), onConfirm = { onCreate(ratio) }, onDismiss = onDismiss) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CANVAS_RATIOS) { (label, r) -> AspectTile(label, r, r == ratio) { ratio = r } }
        }
    }
}
