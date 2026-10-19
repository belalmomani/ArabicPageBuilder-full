package com.arabicpagebuilder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arabicpagebuilder.AppContainer
import com.arabicpagebuilder.Screen
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.storage.ImportedProject
import com.arabicpagebuilder.core.storage.ProjectSummary
import com.arabicpagebuilder.core.storage.StoreError
import com.arabicpagebuilder.core.storage.StoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface HomeDialog {
    data object New : HomeDialog
    data class Rename(val project: ProjectSummary) : HomeDialog
    data class Delete(val project: ProjectSummary) : HomeDialog
}

@Composable
fun HomeScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<ProjectSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    fun refresh() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { container.store.list() }
            projects = list
            loading = false
        }
    }

    fun openProject(id: String) {
        scope.launch {
            when (val r = withContext(Dispatchers.IO) { container.store.load(id) }) {
                is StoreResult.Ok -> container.openEditor(r.value)
                is StoreResult.Err -> info = Messages.storeError(r.error, r.detail)
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result: StoreResult<ImportedProject> = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { container.archive.importProject(it) }
                            ?: StoreResult.Err(StoreError.IO_ERROR, "تعذر فتح الملف")
                    } catch (e: Exception) {
                        StoreResult.Err(StoreError.IO_ERROR, e.message)
                    }
                }
                when (result) {
                    is StoreResult.Ok -> {
                        val missing = result.value.missingAssets
                        info = "تم استيراد المشروع «${result.value.project.data.name}»." +
                            if (missing.isEmpty()) "" else "\nملفات صور مفقودة في الأرشيف (${missing.size}): ${missing.joinToString("، ")}"
                        refresh()
                    }
                    is StoreResult.Err -> info = Messages.storeError(result.error, result.detail)
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("مشاريعي", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { container.screen = Screen.Settings }) { Text("الإعدادات") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialog = HomeDialog.New }) { Text("+ مشروع جديد") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("استيراد مشروع") }
        }
        HorizontalDivider()
        when {
            loading -> Text("جارٍ التحميل…", Modifier.padding(16.dp))
            projects.isEmpty() -> Text("لا توجد مشاريع بعد. أنشئ مشروعًا جديدًا أو استورد مشروعًا محفوظًا.", Modifier.padding(16.dp))
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                items(projects, key = { it.id }) { p ->
                    ProjectRow(
                        project = p,
                        onOpen = { if (p.corrupt) info = "ملف هذا المشروع تالف." + (if (p.hasBackup) " يمكنك استعادة النسخة الاحتياطية من القائمة ⋮." else "") else openProject(p.id) },
                        onRename = { dialog = HomeDialog.Rename(p) },
                        onDuplicate = {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { container.store.duplicate(p.id) }
                                if (r is StoreResult.Err) info = Messages.storeError(r.error, r.detail)
                                refresh()
                            }
                        },
                        onRestore = {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { container.store.restoreBackup(p.id) }
                                info = if (r is StoreResult.Err) Messages.storeError(r.error, r.detail) else "تمت استعادة آخر نسخة محفوظة."
                                refresh()
                            }
                        },
                        onDelete = { dialog = HomeDialog.Delete(p) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        HomeDialog.New -> NewProjectDialog(
            onDismiss = { dialog = null },
            onCreate = { name, title, direction ->
                dialog = null
                scope.launch {
                    when (val r = withContext(Dispatchers.IO) { container.store.create(name, title, direction) }) {
                        is StoreResult.Ok -> container.openEditor(r.value)
                        is StoreResult.Err -> info = Messages.storeError(r.error, r.detail)
                    }
                }
            },
        )
        is HomeDialog.Rename -> TextPromptDialog(
            title = "إعادة تسمية المشروع",
            label = "اسم المشروع",
            initial = d.project.name,
            confirmLabel = "حفظ",
            onDismiss = { dialog = null },
            onConfirm = { newName ->
                dialog = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) { container.store.rename(d.project.id, newName) }
                    if (r is StoreResult.Err) info = Messages.storeError(r.error, r.detail)
                    refresh()
                }
            },
        )
        is HomeDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("حذف المشروع") },
            text = { Text("سيُحذف المشروع «${d.project.name}» وكل صوره نهائيًا ولا يمكن التراجع. هل أنت متأكد؟") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { container.store.delete(d.project.id) }
                        if (r is StoreResult.Err) info = Messages.storeError(r.error, r.detail)
                        refresh()
                    }
                }) { Text("حذف نهائيًا") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("إلغاء") } },
        )
    }

    info?.let { text ->
        AlertDialog(
            onDismissRequest = { info = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("حسنًا") } },
        )
    }
}

@Composable
private fun ProjectRow(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (project.corrupt) {
                Text("ملف تالف" + if (project.hasBackup) " — توجد نسخة احتياطية" else "", color = MaterialTheme.colorScheme.error)
            } else {
                Text("${project.title} • آخر تعديل ${Messages.formatDate(project.modifiedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text("⋮") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (!project.corrupt) {
                    DropdownMenuItem(text = { Text("فتح") }, onClick = { expanded = false; onOpen() })
                    DropdownMenuItem(text = { Text("إعادة تسمية") }, onClick = { expanded = false; onRename() })
                    DropdownMenuItem(text = { Text("نسخ المشروع") }, onClick = { expanded = false; onDuplicate() })
                }
                if (project.hasBackup) {
                    DropdownMenuItem(text = { Text("استعادة آخر نسخة محفوظة") }, onClick = { expanded = false; onRestore() })
                }
                DropdownMenuItem(text = { Text("حذف") }, onClick = { expanded = false; onDelete() })
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, title: String, direction: TextDirection) -> Unit,
) {
    var name by remember { mutableStateOf("مشروع جديد") }
    var title by remember { mutableStateOf("صفحتي") }
    var direction by remember { mutableStateOf(TextDirection.RTL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشروع جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المشروع") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الصفحة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("اتجاه الصفحة")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (direction == TextDirection.RTL) Button(onClick = {}) { Text("من اليمين") } else OutlinedButton(onClick = { direction = TextDirection.RTL }) { Text("من اليمين") }
                    if (direction == TextDirection.LTR) Button(onClick = {}) { Text("من اليسار") } else OutlinedButton(onClick = { direction = TextDirection.LTR }) { Text("من اليسار") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, title, direction) }) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
