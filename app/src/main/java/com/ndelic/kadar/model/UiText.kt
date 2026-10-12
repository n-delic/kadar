package com.ndelic.kadar.model

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Text made outside the UI (busy labels, messages), resolved by the UI so it follows the app language. */
sealed interface UiText {
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText
    class Plural(@PluralsRes val id: Int, val count: Int) : UiText
    /** Already words, such as a system exception's message. */
    class Raw(val text: String) : UiText

    fun asString(res: Resources): String = when (this) {
        is Res -> res.getString(id, *args)
        is Plural -> res.getQuantityString(id, count, count)
        is Raw -> text
    }
}

/** A failure the user should read as [text]; other exceptions show their own message. */
class UserError(val text: UiText, cause: Throwable? = null) : Exception(cause) {
    constructor(@StringRes id: Int, vararg args: Any) : this(UiText.Res(id, *args))
}
