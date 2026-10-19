package com.arabicpagebuilder.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arabicpagebuilder.core.edit.EditResult
import com.arabicpagebuilder.core.export.AssetSource
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.session.EditorSession
import com.arabicpagebuilder.core.session.TreeRow
import com.arabicpagebuilder.core.storage.LoadedProject
import com.arabicpagebuilder.core.storage.ProjectStore
import com.arabicpagebuilder.core.storage.StoreResult
import com.arabicpagebuilder.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class SaveStatus { SAVED, PENDING, SAVING, ERROR }

/** غلاف Compose فوق [EditorSession]: يحوّل تغيّراتها إلى حالة قابلة للمراقبة ويتولى الحفظ التلقائي. */
class EditorState(private val store: ProjectStore, loaded: LoadedProject) {
    val projectId: String = loaded.id
    val projectName: String = loaded.data.name

    private val session = EditorSession(loaded.data.document)

    var doc: PageDocument by mutableStateOf(session.document)
        private set
    var selectedId: String? by mutableStateOf(null)
        private set
    var canUndo: Boolean by mutableStateOf(false)
        private set
    var canRedo: Boolean by mutableStateOf(false)
        private set
    var revision: Long by mutableLongStateOf(0L)
        private set
    var message: String? by mutableStateOf(null)
    var saveStatus: SaveStatus by mutableStateOf(SaveStatus.SAVED)
        private set

    private var savedRevision = 0L

    val selected: Element? get() = selectedId?.let { doc.find(it) }

    fun assetSource(): AssetSource? = store.assetSource(projectId)

    fun moveTargets(): List<TreeRow> = session.validMoveTargets()

    fun select(id: String?) {
        session.select(id)
        sync()
    }

    /** ينفّذ تعديلًا؛ عند الفشل تظهر رسالة عربية ولا يتغير المستند. */
    fun apply(block: (EditorSession) -> EditResult) {
        val result = block(session)
        message = if (result is EditResult.Failure) Messages.editError(result.error) else null
        sync()
    }

    fun undo() {
        session.undo()
        message = null
        sync()
    }

    fun redo() {
        session.redo()
        message = null
        sync()
    }

    /** يحفظ الآن إن كان هناك تغيير غير محفوظ. آمن عند الإلغاء: الكتابة نفسها لا تُقطع. */
    suspend fun saveNow() {
        if (revision == savedRevision && saveStatus != SaveStatus.ERROR) return
        val rev = revision
        val snapshot = doc
        saveStatus = SaveStatus.SAVING
        val result = withContext(NonCancellable + Dispatchers.IO) { store.save(projectId, snapshot) }
        if (result is StoreResult.Ok) {
            savedRevision = rev
            saveStatus = if (revision == rev) SaveStatus.SAVED else SaveStatus.PENDING
        } else {
            saveStatus = SaveStatus.ERROR
            message = "تعذر حفظ المشروع. تغييراتك ما زالت مفتوحة في المحرر."
        }
    }

    private fun sync() {
        doc = session.document
        selectedId = session.selectedId
        canUndo = session.canUndo
        canRedo = session.canRedo
        revision = session.revision
        if (revision != savedRevision) saveStatus = SaveStatus.PENDING
    }
}
