package com.arabicpagebuilder.editor

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicpagebuilder.AppContainer
import com.arabicpagebuilder.Screen
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.session.ElementCatalog
import com.arabicpagebuilder.core.session.TreeRows
import com.arabicpagebuilder.core.storage.StoreError
import com.arabicpagebuilder.core.storage.StoreResult
import com.arabicpagebuilder.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val label: String) { DESIGN("التصميم"), PREVIEW("المعاينة"), CODE("الكود") }

private enum class Panel { LIBRARY, PROPERTIES, MOVE }

@Composable
fun EditorScreen(container: AppContainer, state: EditorState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.DESIGN) }
    var panel by remember { mutableStateOf<Panel?>(null) }

    // حفظ تلقائي بعد توقف التعديل قليلًا.
    LaunchedEffect(state.revision) {
        if (state.revision > 0L) {
            delay(800)
            state.saveNow()
        }
    }

    fun leave() {
        scope.launch {
            state.saveNow()
            container.closeEditor()
        }
    }

    BackHandler {
        if (panel != null) panel = null else leave()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result: StoreResult<String> = withContext(Dispatchers.IO) {
                    try {
                        val name = suggestedImageName(context.contentResolver, uri)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            container.store.addAsset(state.projectId, name, input)
                        } ?: StoreResult.Err(StoreError.IO_ERROR, "تعذر فتح الصورة")
                    } catch (e: Exception) {
                        StoreResult.Err(StoreError.IO_ERROR, e.message)
                    }
                }
                when (result) {
                    is StoreResult.Ok -> state.apply { it.setProperty("src", result.value) }
                    is StoreResult.Err -> state.message = Messages.storeError(result.error, result.detail)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // الشريط العلوي
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { leave() }) { Text("رجوع") }
            Column(Modifier.weight(1f)) {
                Text(state.projectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    when (state.saveStatus) {
                        SaveStatus.SAVED -> "تم الحفظ"
                        SaveStatus.PENDING -> "تعديلات غير محفوظة…"
                        SaveStatus.SAVING -> "جارٍ الحفظ…"
                        SaveStatus.ERROR -> "تعذر الحفظ"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.saveStatus == SaveStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { state.undo() }, enabled = state.canUndo) { Text("تراجع") }
            TextButton(onClick = { state.redo() }, enabled = state.canRedo) { Text("إعادة") }
        }
        // التبويبات
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in Tab.entries) {
                if (t == tab) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(t.label) }
                else OutlinedButton(onClick = { tab = t }, modifier = Modifier.weight(1f)) { Text(t.label) }
            }
        }
        state.message?.let { msg ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, Modifier.weight(1f).padding(8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { state.message = null }) { Text("إغلاق") }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.DESIGN -> DesignTab(state, panel, { panel = it }, onPickImage = { pickImage.launch(arrayOf("image/*")) })
                Tab.PREVIEW -> PreviewTab(state, container.prefs.allowExternalPreview) { container.prefs.updateAllowExternalPreview(it) }
                Tab.CODE -> CodeTab(state)
            }
        }
    }
}

@Composable
private fun DesignTab(state: EditorState, panel: Panel?, setPanel: (Panel?) -> Unit, onPickImage: () -> Unit) {
    val rows = remember(state.doc) { TreeRows.of(state.doc) }
    val selected = state.selected
    val rootId = state.doc.root.id
    val canModify = selected != null && selected.id != rootId

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { it.element.id }) { row ->
                val isSelected = row.element.id == state.selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { state.select(row.element.id) }
                        .padding(start = (12 + row.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ElementCatalog.label(row.element.type), fontWeight = FontWeight.Bold)
                    val preview = ElementCatalog.preview(row.element)
                    if (preview.isNotEmpty()) Text("  —  $preview", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
            if (rows.size == 1) {
                item { Text("الصفحة فارغة. اضغط «+ إضافة» لإضافة أول عنصر.", Modifier.padding(16.dp)) }
            }
        }

        if (panel != null) {
            HorizontalDivider()
            Surface(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                when (panel) {
                    Panel.LIBRARY -> LibraryPanel { type ->
                        state.apply { it.addElement(type) }
                        setPanel(null)
                    }
                    Panel.PROPERTIES -> PropertiesPanel(state, onPickImage)
                    Panel.MOVE -> MovePanel(state) { setPanel(null) }
                }
            }
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val pad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            Button(onClick = { setPanel(if (panel == Panel.LIBRARY) null else Panel.LIBRARY) }, contentPadding = pad) { Text("+ إضافة", fontSize = 13.sp) }
            OutlinedButton(onClick = { setPanel(if (panel == Panel.PROPERTIES) null else Panel.PROPERTIES) }, enabled = selected != null, contentPadding = pad) { Text("الخصائص", fontSize = 13.sp) }
            OutlinedButton(onClick = { state.apply { it.moveSelectedUp() } }, enabled = canModify, contentPadding = pad) { Text("▲ للأعلى", fontSize = 13.sp) }
            OutlinedButton(onClick = { state.apply { it.moveSelectedDown() } }, enabled = canModify, contentPadding = pad) { Text("▼ للأسفل", fontSize = 13.sp) }
            OutlinedButton(onClick = { setPanel(if (panel == Panel.MOVE) null else Panel.MOVE) }, enabled = canModify, contentPadding = pad) { Text("نقل إلى…", fontSize = 13.sp) }
            OutlinedButton(onClick = { state.apply { it.duplicateSelected() } }, enabled = canModify, contentPadding = pad) { Text("نسخ", fontSize = 13.sp) }
            OutlinedButton(
                onClick = {
                    state.apply { it.removeSelected() }
                    setPanel(null)
                },
                enabled = canModify,
                contentPadding = pad,
            ) { Text("حذف", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun LibraryPanel(onPick: (ElementType) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("مكتبة العناصر", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("يُضاف العنصر داخل المحدد إن كان حاوية، وإلا بعده.", style = MaterialTheme.typography.bodySmall)
        for (category in ElementCatalog.categories) {
            Text(category.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            for (type in category.types) {
                OutlinedButton(onClick = { onPick(type) }, modifier = Modifier.fillMaxWidth()) { Text(ElementCatalog.label(type)) }
            }
        }
    }
}

@Composable
private fun MovePanel(state: EditorState, onDone: () -> Unit) {
    val targets = remember(state.doc, state.selectedId) { state.moveTargets() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("نقل العنصر المحدد إلى…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (targets.isEmpty()) Text("لا توجد حاوية أخرى يمكن النقل إليها. أضف حاوية أو قسمًا أولًا.")
        for (t in targets) {
            OutlinedButton(
                onClick = {
                    state.apply { it.moveSelectedTo(t.element.id) }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().padding(start = (t.depth * 12).dp),
            ) { Text(ElementCatalog.label(t.element.type) + ElementCatalog.preview(t.element).let { if (it.isEmpty()) "" else " — $it" }) }
        }
    }
}

/** اسم مناسب للصورة المختارة: الاسم المعروض، وإن خلا من امتداد مسموح يُشتق الامتداد من نوع الملف. */
private fun suggestedImageName(resolver: ContentResolver, uri: Uri): String {
    val display = resolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: "image"
    val allowed = setOf("png", "jpg", "jpeg", "gif", "webp")
    val ext = display.substringAfterLast('.', "").lowercase()
    if (ext in allowed) return display
    val fromMime = when (resolver.getType(uri)) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> null
    }
    return if (fromMime != null) "${display.substringBeforeLast('.')}.$fromMime" else display
}
