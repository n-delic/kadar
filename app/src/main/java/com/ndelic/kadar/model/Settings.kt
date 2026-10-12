package com.ndelic.kadar.model

import android.content.Context
import androidx.annotation.StringRes
import com.ndelic.kadar.R
import com.ndelic.kadar.ui.editor.label
import java.net.URLEncoder

enum class SearchEngine(val label: String, val home: String, private val query: String) {
    Google("Google", "https://www.google.com/imghp", "https://www.google.com/search?udm=2&q="),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?ia=images&iax=images", "https://duckduckgo.com/?ia=images&iax=images&q="),
    Bing("Bing", "https://www.bing.com/images", "https://www.bing.com/images/search?q="),
    Brave("Brave", "https://search.brave.com/images", "https://search.brave.com/images?q=");

    fun search(text: String) = query + URLEncoder.encode(text, "UTF-8")
}

/** Export size by the short side (1080p = 1080 px short side); [Original] keeps the canvas size. Never upscales. */
enum class ExportSize(@StringRes val label: Int, val shortSide: Int?) {
    Original(R.string.original, null),
    P1080(R.string.export_1080p, 1080),
    P720(R.string.export_720p, 720),
}

data class AppSettings(
    val search: SearchEngine = SearchEngine.Google,
    val exportSize: ExportSize = ExportSize.Original,
    val projectSort: ProjectSort = ProjectSort.Recent,
)

/** App-wide preferences in SharedPreferences; a handful of values read once at start. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = AppSettings(
        search = enumOr(prefs.getString(SEARCH, null), SearchEngine.Google),
        exportSize = enumOr(prefs.getString(EXPORT_SIZE, null), ExportSize.Original),
        projectSort = enumOr(prefs.getString(PROJECT_SORT, null), ProjectSort.Recent),
    )

    fun save(s: AppSettings) = prefs.edit()
        .putString(SEARCH, s.search.name)
        .putString(EXPORT_SIZE, s.exportSize.name)
        .putString(PROJECT_SORT, s.projectSort.name)
        .apply()

    private companion object {
        const val SEARCH = "search"
        const val EXPORT_SIZE = "exportSize"
        const val PROJECT_SORT = "projectSort"
    }
}

/** The enum constant named [name], or [default] for null or a name from a newer or older version. */
inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default
