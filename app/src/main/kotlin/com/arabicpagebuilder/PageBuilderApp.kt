package com.arabicpagebuilder

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arabicpagebuilder.core.log.Breadcrumbs
import com.arabicpagebuilder.core.log.ErrorLog
import com.arabicpagebuilder.core.log.LogKind
import com.arabicpagebuilder.core.storage.LoadedProject
import com.arabicpagebuilder.core.storage.ProjectArchive
import com.arabicpagebuilder.core.storage.ProjectStore
import com.arabicpagebuilder.editor.EditorState
import java.io.File
import kotlin.concurrent.thread

class PageBuilderApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this, container)
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Editor : Screen
    data object ErrorLog : Screen
}

/** كل ما يعيش طوال عمر التطبيق: المخزن والإعدادات والشاشة الحالية والمحرر المفتوح. */
class AppContainer(app: Application) {
    private val projectsDir = File(app.filesDir, "projects")
    val store = ProjectStore(projectsDir)
    val archive = ProjectArchive(store)
    val prefs = AppPrefs(app)
    val errorLog = ErrorLog(File(app.filesDir, "logs"))
    val breadcrumbs = Breadcrumbs()
    private val application = app

    /** يسجّل خطأً غير قاتل (فشل حفظ أو تصدير أو استيراد) في السجل المحلي. لا يرمي استثناءات. */
    fun logError(title: String, error: Throwable? = null, kind: LogKind = LogKind.ERROR) {
        errorLog.record(kind, title, error, DeviceInfo.header(application, screen.toString()), breadcrumbs.snapshot())
    }

    init {
        // تنظيف ما خلّفه انقطاع مفاجئ (مجلدات استيراد مؤقتة وملفات تصدير قديمة) دون تعطيل الواجهة.
        thread(name = "startup-cleanup") {
            projectsDir.mkdirs()
            store.cleanupStaleTemp()
            com.arabicpagebuilder.editor.ExportActions.cleanOldExports(app)
        }
    }

    var screen: Screen by mutableStateOf(Screen.Home)
    var editor: EditorState? = null
        private set

    fun openEditor(loaded: LoadedProject) {
        breadcrumbs.add("فتح المحرر")
        editor = EditorState(store, loaded) { message -> logError(message) }
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

    fun updateAllowExternalPreview(value: Boolean) {
        allowExternalPreview = value
        sp.edit().putBoolean(KEY_EXTERNAL, value).apply()
    }

    private companion object {
        const val KEY_EXTERNAL = "allow_external_preview"
    }
}
