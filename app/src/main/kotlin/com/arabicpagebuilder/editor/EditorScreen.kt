package com.arabicpagebuilder.editor

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicpagebuilder.AppContainer
import com.arabicpagebuilder.core.export.ExportResult
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.session.ElementCatalog
import com.arabicpagebuilder.core.session.DropZone
import com.arabicpagebuilder.core.session.TreeRows
import com.arabicpagebuilder.core.storage.AssetNames
import com.arabicpagebuilder.core.storage.StoreError
import com.arabicpagebuilder.core.storage.StoreResult
import com.arabicpagebuilder.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private enum class Tab(val label: String) { DESIGN("التصميم"), PREVIEW("المعاينة"), CODE("الكود") }

private enum class Panel { LIBRARY, PROPERTIES, MOVE }

@Composable
fun EditorScreen(container: AppContainer, state: EditorState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.DESIGN) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    val ctrl = remember { DragController() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var exportMenu by remember { mutableStateOf(false) }
    var exportInfo by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf("src") }

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
        when {
            ctrl.active -> ctrl.cancel()
            panel != null -> panel = null
            else -> leave()
        }
    }

    // ---- اختيار ملف من الجهاز (صورة/وسائط/ملف) ----
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val key = pendingKey
            scope.launch {
                val result: StoreResult<String> = withContext(Dispatchers.IO) {
                    try {
                        val name = suggestedAssetName(context.contentResolver, uri)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            container.store.addAsset(state.projectId, name, input)
                        } ?: StoreResult.Err(StoreError.IO_ERROR, "تعذر فتح الملف")
                    } catch (e: Exception) {
                        StoreResult.Err(StoreError.IO_ERROR, e.message)
                    }
                }
                when (result) {
                    is StoreResult.Ok -> state.apply { it.setProperty(key, result.value) }
                    is StoreResult.Err -> state.message = Messages.storeError(result.error, result.detail)
                }
            }
        }
    }
    val onPick: (PickKind, String) -> Unit = { kind, key ->
        pendingKey = key
        pickFile.launch(kind.mimeTypes)
    }

    // ---- التصدير ----
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                state.saveNow()
                val tmp = File(ExportActions.exportsDir(context), "site-${System.nanoTime()}.zip")
                val r = ExportActions.buildSiteZip(state, tmp)
                exportInfo = if (r is ExportResult.Success) ExportActions.copyToUri(context, tmp, uri) ?: ExportActions.describe(r) else ExportActions.describe(r)
                tmp.delete()
                busy = false
            }
        }
    }
    val createArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                state.saveNow()
                val tmp = File(ExportActions.exportsDir(context), "project-${System.nanoTime()}.apbproj")
                val r = withContext(Dispatchers.IO) { container.archive.exportProjectToFile(state.projectId, tmp) }
                exportInfo = when (r) {
                    is StoreResult.Ok -> ExportActions.copyToUri(context, tmp, uri) ?: "تم حفظ نسخة من المشروع (${r.value} ملفًا). يمكنك استيرادها لاحقًا من الشاشة الرئيسية."
                    is StoreResult.Err -> "فشل الحفظ: " + Messages.storeError(r.error, r.detail)
                }
                tmp.delete()
                busy = false
            }
        }
    }
    fun shareSite() {
        scope.launch {
            busy = true
            state.saveNow()
            val file = File(ExportActions.exportsDir(context), ExportActions.safeFileName(state.projectName) + ".zip")
            val r = ExportActions.buildSiteZip(state, file)
            if (r is ExportResult.Success) {
                ExportActions.shareFile(context, file, "application/zip")
                if (r.warnings.isNotEmpty() || r.generationWarnings.isNotEmpty()) exportInfo = ExportActions.describe(r)
            } else {
                exportInfo = ExportActions.describe(r)
            }
            busy = false
        }
    }

    fun performDrop() {
        val result = ctrl.finish() ?: return
        val (payload, target) = result
        state.apply { s ->
            when (payload) {
                is DragPayload.Existing -> s.dragMove(payload.id, target.id, target.zone)
                is DragPayload.NewType -> s.dropNewElement(payload.type, target.id, target.zone)
                is DragPayload.NewPreset -> s.dropPreset(payload.key, target.id, target.zone)
            }
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInWindow() }) {
        Column(Modifier.fillMaxSize()) {
            // الشريط العلوي
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val tight = PaddingValues(horizontal = 8.dp)
                TextButton(onClick = { leave() }, contentPadding = tight) { Text("رجوع") }
                Column(Modifier.weight(1f)) {
                    Text(state.projectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        when {
                            busy -> "جارٍ التصدير…"
                            state.saveStatus == SaveStatus.SAVED -> "تم الحفظ"
                            state.saveStatus == SaveStatus.PENDING -> "تعديلات غير محفوظة…"
                            state.saveStatus == SaveStatus.SAVING -> "جارٍ الحفظ…"
                            else -> "تعذر الحفظ"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.saveStatus == SaveStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { state.undo() }, enabled = state.canUndo, contentPadding = tight) { Text("تراجع") }
                TextButton(onClick = { state.redo() }, enabled = state.canRedo, contentPadding = tight) { Text("إعادة") }
                Box {
                    TextButton(onClick = { exportMenu = true }, enabled = !busy, contentPadding = tight) { Text("تصدير") }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("حفظ الموقع كملف ZIP") },
                            onClick = { exportMenu = false; createZip.launch(ExportActions.safeFileName(state.projectName) + ".zip") },
                        )
                        DropdownMenuItem(text = { Text("مشاركة الموقع (ZIP)") }, onClick = { exportMenu = false; shareSite() })
                        DropdownMenuItem(
                            text = { Text("حفظ نسخة من المشروع (.apbproj)") },
                            onClick = { exportMenu = false; createArchive.launch(ExportActions.safeFileName(state.projectName, "project") + ".apbproj") },
                        )
                    }
                }
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
                    Tab.DESIGN -> DesignTab(state, ctrl, panel, { panel = it }, onPick, ::performDrop)
                    Tab.PREVIEW -> PreviewTab(state, container.prefs.allowExternalPreview) { container.prefs.updateAllowExternalPreview(it) }
                    Tab.CODE -> CodeTab(state)
                }
            }
        }

        // شبح العنصر المسحوب
        if (ctrl.active) {
            Surface(
                modifier = Modifier.offset {
                    IntOffset((ctrl.pointer.x - overlayOrigin.x).roundToInt() - 60, (ctrl.pointer.y - overlayOrigin.y).roundToInt() - 120)
                },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
            ) {
                Text(ctrl.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    exportInfo?.let { text ->
        AlertDialog(
            onDismissRequest = { exportInfo = null },
            text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { exportInfo = null }) { Text("حسنًا") } },
        )
    }
}

@Composable
private fun DesignTab(
    state: EditorState,
    ctrl: DragController,
    panel: Panel?,
    setPanel: (Panel?) -> Unit,
    onPick: (PickKind, String) -> Unit,
    onDrop: () -> Unit,
) {
    val rows = remember(state.doc) { TreeRows.of(state.doc) }
    val selected = state.selected
    val rootId = state.doc.root.id
    val canModify = selected != null && selected.id != rootId
    val listState = rememberLazyListState()
    val indicator = MaterialTheme.colorScheme.primary

    // تمرير تلقائي عند اقتراب المؤشر من حافة القائمة أثناء السحب.
    LaunchedEffect(ctrl.active) {
        while (ctrl.active) {
            val b = ctrl.listBounds
            val p = ctrl.pointer
            val inColumn = p.x >= b.left && p.x <= b.right
            val delta = when {
                !inColumn -> 0f
                p.y < b.top + 80f && p.y > b.top - 120f -> -24f
                p.y > b.bottom - 80f && p.y < b.bottom + 60f -> 24f
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                ctrl.recompute()
            }
            delay(16)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    ctrl.listBounds = it.boundsInWindow()
                    ctrl.listOrigin = it.positionInWindow()
                }
                .pointerInput(Unit) {
                    // سحب عنصر موجود: ضغط مطوّل على صفه ثم سحب (الضغط القصير للتحديد، والسحب السريع للتمرير).
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val p = ctrl.listOrigin + offset
                            val id = ctrl.rowAt(p)
                            if (id != null && id != rootId) ctrl.start(DragPayload.Existing(id), dragLabel(state, id), p)
                        },
                        onDrag = { change, _ ->
                            if (ctrl.active) {
                                change.consume()
                                ctrl.move(ctrl.listOrigin + change.position)
                            }
                        },
                        onDragEnd = { onDrop() },
                        onDragCancel = { ctrl.cancel() },
                    )
                },
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(rows, key = { it.element.id }) { row ->
                    val id = row.element.id
                    val isSelected = id == state.selectedId
                    DisposableEffect(id) { onDispose { ctrl.removeRow(id) } }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { ctrl.setRowBounds(id, it.boundsInWindow()) }
                            .graphicsLayer { alpha = if ((ctrl.payload as? DragPayload.Existing)?.id == id) 0.4f else 1f }
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .drawBehind {
                                val t = ctrl.target
                                if (t != null && t.id == id) {
                                    when (t.zone) {
                                        DropZone.BEFORE -> drawLine(indicator, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 8f)
                                        DropZone.AFTER -> drawLine(indicator, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 8f)
                                        DropZone.INSIDE -> drawRect(indicator.copy(alpha = 0.25f))
                                    }
                                }
                            }
                            .clickable { state.select(id) }
                            .padding(start = (12 + row.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ElementCatalog.label(row.element.type), fontWeight = FontWeight.Bold)
                        val preview = ElementCatalog.preview(row.element)
                        if (preview.isNotEmpty()) Text("  —  $preview", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
                item {
                    Text(
                        if (rows.size == 1) "الصفحة فارغة. اضغط «+ إضافة» ثم اختر عنصرًا، أو اسحبه من المكتبة إلى هنا."
                        else "للنقل: اضغط مطوّلًا على عنصر ثم اسحبه إلى مكانه الجديد (فوق/داخل/تحت عنصر آخر).",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (panel != null) {
            HorizontalDivider()
            Surface(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                when (panel) {
                    Panel.LIBRARY -> LibraryPanel(ctrl, onDrop, onAddType = { type ->
                        state.apply { it.addElement(type) }
                        setPanel(null)
                    }, onAddPreset = { key ->
                        state.apply { it.addPreset(key) }
                        setPanel(null)
                    })
                    Panel.PROPERTIES -> PropertiesPanel(state, onPick)
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

private fun dragLabel(state: EditorState, id: String): String =
    state.doc.find(id)?.let { ElementCatalog.label(it.type) } ?: "عنصر"

@Composable
private fun LibraryPanel(ctrl: DragController, onDrop: () -> Unit, onAddType: (ElementType) -> Unit, onAddPreset: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("مكتبة العناصر", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "اضغط لإضافة العنصر (داخل المحدد إن كان حاوية، وإلا بعده)، أو اضغط مطوّلًا واسحبه إلى مكان في القائمة.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("أجزاء جاهزة للصفحة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        for (preset in ElementCatalog.presets) {
            LibraryItem(preset.title, ctrl, DragPayload.NewPreset(preset.key), onTap = { onAddPreset(preset.key) }, onDrop = onDrop)
        }
        for (category in ElementCatalog.categories) {
            Text(category.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            for (type in category.types) {
                LibraryItem(ElementCatalog.label(type), ctrl, DragPayload.NewType(type), onTap = { onAddType(type) }, onDrop = onDrop)
            }
        }
    }
}

@Composable
private fun LibraryItem(label: String, ctrl: DragController, payload: DragPayload, onTap: () -> Unit, onDrop: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .pointerInput(payload) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> ctrl.start(payload, label, origin + offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        ctrl.move(origin + change.position)
                    },
                    onDragEnd = onDrop,
                    onDragCancel = { ctrl.cancel() },
                )
            }
            .padding(12.dp),
    ) { Text(label) }
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

/** اسم مناسب للملف المختار: الاسم المعروض، وإن خلا من امتداد مسموح يُشتق الامتداد من نوع الملف. */
private fun suggestedAssetName(resolver: ContentResolver, uri: Uri): String {
    val display = resolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: "file"
    val ext = display.substringAfterLast('.', "").lowercase()
    if (AssetNames.folderFor(ext) != null) return display
    val fromMime = when (resolver.getType(uri)) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "audio/mpeg" -> "mp3"
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        "text/csv" -> "csv"
        "application/zip" -> "zip"
        else -> null
    }
    return if (fromMime != null) "${display.substringBeforeLast('.')}.$fromMime" else display
}
