package com.arabicpagebuilder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arabicpagebuilder.core.storage.LoadedProject
import com.arabicpagebuilder.core.storage.ProjectArchive
import com.arabicpagebuilder.core.storage.ProjectStore
import com.arabicpagebuilder.editor.EditorState
import java.io.File

class PageBuilderApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Editor : Screen
}

/** كل ما يعيش طوال عمر التطبيق: المخزن والإعدادات والشاشة الحالية والمحرر المفتوح. */
class AppContainer(app: Application) {
    val store = ProjectStore(File(app.filesDir, "projects"))
    val archive = ProjectArchive(store)
    val prefs = AppPrefs(app)

    var screen: Screen by mutableStateOf(Screen.Home)
    var editor: EditorState? = null
        private set

    fun openEditor(loaded: LoadedProject) {
        editor = EditorState(store, loaded)
        screen = Screen.Editor
    }

    fun closeEditor() {
        editor = null
        screen = Screen.Home
    }
}

class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var allowExternalPreview: Boolean by mutableStateOf(sp.getBoolean(KEY_EXTERNAL, false))
        private set

    fun setAllowExternalPreview(value: Boolean) {
        allowExternalPreview = value
        sp.edit().putBoolean(KEY_EXTERNAL, value).apply()
    }

    private companion object {
        const val KEY_EXTERNAL = "allow_external_preview"
    }
}
