package com.arabicpagebuilder.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicpagebuilder.core.session.NavEntry
import com.arabicpagebuilder.core.session.NavItemsCodec
import kotlinx.coroutines.delay

/**
 * محرر مرئي لعناصر القائمة: لكل عنصر نص ورابط وأزرار (أعلى/أسفل، رئيسي/فرعي، حذف)، وزر إضافة.
 * الناتج هو نفس نص «items» الذي يقرؤه المولّد، فلا صيغة مزدوجة. تعديل النص يُطبَّق بعد توقف الكتابة،
 * وأزرار الترتيب والحذف تُطبَّق فورًا.
 */
@Composable
fun NavItemsEditor(current: String, onCommit: (String) -> Unit) {
    // القائمة المحلية تُعاد من القيمة المحفوظة عند تغيّرها (مثل التراجع).
    var entries by remember(current) { mutableStateOf(NavItemsCodec.parse(current)) }

    LaunchedEffect(entries) {
        val serialized = NavItemsCodec.serialize(entries)
        // لا نحفظ أثناء وجود عنصر نصه فارغ (وإلا اختفى صفه من الشاشة أثناء إعادة كتابة نصه).
        if (serialized != current && entries.none { it.text.isBlank() }) {
            delay(600)
            onCommit(serialized)
        }
    }

    fun commitNow(updated: List<NavEntry>) {
        entries = updated
        onCommit(NavItemsCodec.serialize(updated))
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("عناصر القائمة (${entries.size})", style = MaterialTheme.typography.labelLarge)
        if (entries.isEmpty()) Text("لا توجد عناصر. أضف أول عنصر.", style = MaterialTheme.typography.bodySmall)
        entries.forEachIndexed { index, entry ->
            key(index) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(start = if (entry.isSub) 24.dp else 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(if (entry.isSub) "عنصر فرعي" else "عنصر رئيسي", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = entry.text,
                        onValueChange = { entries = NavItemsCodec.update(entries, index, text = it) },
                        label = { Text("النص") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val hrefError = NavItemsCodec.hrefError(entry.href)
                    OutlinedTextField(
                        value = entry.href,
                        onValueChange = { entries = NavItemsCodec.update(entries, index, href = it) },
                        label = { Text("الرابط (فارغ = أعلى الصفحة)") },
                        singleLine = true,
                        isError = hrefError != null,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hrefError != null) Text(hrefError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val pad = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        OutlinedButton(onClick = { commitNow(NavItemsCodec.move(entries, index, -1)) }, enabled = index > 0, contentPadding = pad) { Text("▲", fontSize = 13.sp) }
                        OutlinedButton(onClick = { commitNow(NavItemsCodec.move(entries, index, 1)) }, enabled = index < entries.lastIndex, contentPadding = pad) { Text("▼", fontSize = 13.sp) }
                        if (index > 0) {
                            OutlinedButton(onClick = { commitNow(NavItemsCodec.update(entries, index, isSub = !entry.isSub)) }, contentPadding = pad) {
                                Text(if (entry.isSub) "اجعله رئيسيًا" else "اجعله فرعيًا", fontSize = 13.sp)
                            }
                        }
                        TextButton(onClick = { commitNow(NavItemsCodec.remove(entries, index)) }, contentPadding = pad) { Text("حذف", fontSize = 13.sp) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { commitNow(NavItemsCodec.add(entries)) }) { Text("+ عنصر") }
            OutlinedButton(onClick = { commitNow(NavItemsCodec.add(entries, isSub = true)) }, enabled = entries.isNotEmpty()) { Text("+ عنصر فرعي") }
        }
    }
}
