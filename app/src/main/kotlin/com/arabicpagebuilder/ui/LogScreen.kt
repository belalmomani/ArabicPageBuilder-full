package com.arabicpagebuilder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicpagebuilder.AppContainer
import com.arabicpagebuilder.DeviceInfo
import com.arabicpagebuilder.PageBuilderApp
import com.arabicpagebuilder.Screen
import com.arabicpagebuilder.core.log.LogEntry
import com.arabicpagebuilder.core.log.LogKind
import com.arabicpagebuilder.editor.ExportActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * شاشة سجل الأخطاء المحلي: ما سُجّل من أعطال وأخطاء على هذا الجهاز فقط. لا يُرسَل شيء تلقائيًا؛
 * أنت من يقرر مشاركة التقرير (يحتوي معلومات الجهاز والإصدار وأثر الخطأ، دون محتوى مشاريعك).
 */
@Composable
fun LogScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var openId by remember { mutableStateOf<String?>(null) }
    var openText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        entries = withContext(Dispatchers.IO) { container.errorLog.list() }
    }

    LaunchedEffect(Unit) {
        container.errorLog.markSeen()
        reload()
    }

    BackHandler { if (openId != null) openId = null else container.screen = Screen.Settings }

    fun report(): String {
        val header = DeviceInfo.header(context.applicationContext as PageBuilderApp, container.screen.toString())
        return container.errorLog.report(header)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (openId != null) openId = null else container.screen = Screen.Settings }) { Text("رجوع") }
            Text(if (openId == null) "سجل الأخطاء" else "تفاصيل الحدث", style = MaterialTheme.typography.headlineSmall)
        }

        if (openId == null) {
            Text(
                "يُحفظ هنا ما يحدث من أعطال وأخطاء على هذا الجهاز فقط. لا يُرسَل شيء تلقائيًا، ولا يحتوي محتوى مشاريعك. " +
                    "عند حدوث مشكلة اضغط «مشاركة التقرير» وأرسله لي.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val pad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                File(ExportActions.exportsDir(context), "error-report.txt").also { it.writeText(report(), Charsets.UTF_8) }
                            }
                            ExportActions.shareFile(context, file, "text/plain")
                        }
                    },
                    enabled = entries.isNotEmpty(),
                    contentPadding = pad,
                ) { Text("مشاركة التقرير", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("error-report", report()))
                        info = "نُسخ التقرير إلى الحافظة."
                    },
                    enabled = entries.isNotEmpty(),
                    contentPadding = pad,
                ) { Text("نسخ", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = {
                        container.logError("سجل اختباري: للتأكد أن التسجيل يعمل", null, LogKind.WARNING)
                        scope.launch { reload() }
                    },
                    contentPadding = pad,
                ) { Text("تسجيل اختباري", fontSize = 13.sp) }
                OutlinedButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty(), contentPadding = pad) { Text("مسح السجل", fontSize = 13.sp) }
            }
            HorizontalDivider()
            if (entries.isEmpty()) {
                Text("لا توجد أحداث مسجّلة. هذا جيد!", modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.id }) { e ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                openId = e.id
                                openText = container.errorLog.read(e.id) ?: "تعذرت قراءة هذا الحدث."
                            }.padding(vertical = 8.dp),
                        ) {
                            Text(
                                (when (e.kind) { LogKind.CRASH -> "💥 عطل"; LogKind.ERROR -> "⚠ خطأ"; LogKind.WARNING -> "ℹ تنبيه" }) + " • " + Messages.formatDate(e.time),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (e.kind == LogKind.CRASH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(e.title.ifBlank { "(بلا عنوان)" }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    openText,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل") },
            text = { Text("سيُحذف كل ما في السجل نهائيًا. هل أنت متأكد؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { container.errorLog.clear() }
                        reload()
                    }
                }) { Text("مسح") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } },
        )
    }
    info?.let { text ->
        AlertDialog(onDismissRequest = { info = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { info = null }) { Text("حسنًا") } })
    }
}
