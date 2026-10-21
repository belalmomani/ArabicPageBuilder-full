package com.arabicpagebuilder.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.session.ElementCatalog
import com.arabicpagebuilder.core.session.PropertyKind
import com.arabicpagebuilder.core.session.PropertySpec
import com.arabicpagebuilder.core.session.PropertySpecs
import kotlinx.coroutines.delay

/** نوع الملف المطلوب اختياره من الجهاز. */
enum class PickKind(val mimeTypes: Array<String>) {
    IMAGE(arrayOf("image/*")),
    MEDIA(arrayOf("video/*", "audio/*")),
    FILE(arrayOf("*/*")),
}

@Composable
fun PropertiesPanel(state: EditorState, onPick: (PickKind, String) -> Unit) {
    val element = state.selected
    if (element == null) {
        Text("اختر عنصرًا من القائمة أولًا.", Modifier.padding(16.dp))
        return
    }
    val specs = remember(element.type) { PropertySpecs.forType(element.type) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("خصائص: ${ElementCatalog.label(element.type)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        var lastGroup = ""
        for (spec in specs) {
            val group = PropertySpecs.groupOf(spec)
            if (group != lastGroup) {
                lastGroup = group
                Text(
                    group,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
                HorizontalDivider()
            }
            key(element.id, spec.key) {
                if (element.type == ElementType.NAV_MENU && spec.key == "items") {
                    NavItemsEditor(
                        current = element.props["items"].orEmpty(),
                        onCommit = { value -> state.apply { it.setProperty("items", value) } },
                    )
                } else PropertyEditor(
                    spec = spec,
                    current = element.props[spec.key].orEmpty(),
                    onCommit = { value -> state.apply { it.setProperty(spec.key, value) } },
                    onPick = { kind -> onPick(kind, spec.key) },
                )
            }
        }
    }
}

@Composable
private fun PropertyEditor(spec: PropertySpec, current: String, onCommit: (String) -> Unit, onPick: (PickKind) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(spec.label, style = MaterialTheme.typography.labelLarge)
        if (spec.kind == PropertyKind.CHOICE) ChoiceRow(spec, current, onCommit) else TextEditor(spec, current, onCommit, onPick)
    }
}

@Composable
private fun ChoiceRow(spec: PropertySpec, current: String, onCommit: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in spec.choices) {
            if (c.value == current) Button(onClick = {}) { Text(c.label) } else OutlinedButton(onClick = { onCommit(c.value) }) { Text(c.label) }
        }
        TextButton(onClick = { onCommit("") }, enabled = current.isNotEmpty()) { Text("افتراضي") }
    }
}

@Composable
private fun TextEditor(spec: PropertySpec, current: String, onCommit: (String) -> Unit, onPick: (PickKind) -> Unit) {
    // النص المحلي يُعاد ضبطه كلما تغيّرت القيمة المحفوظة (مثل التراجع).
    var text by remember(current) { mutableStateOf(current) }
    val error = PropertySpecs.validate(spec, text)
    // يُطبَّق التعديل بعد توقف الكتابة 600 مللي ثانية، وفقط إن كانت القيمة صالحة.
    LaunchedEffect(text) {
        if (text != current && error == null) {
            delay(600)
            onCommit(text)
        }
    }
    val latin = spec.kind != PropertyKind.TEXT && spec.kind != PropertyKind.MULTILINE
    val style = if (latin) LocalTextStyle.current.copy(textDirection = TextDirection.Ltr) else LocalTextStyle.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = spec.kind != PropertyKind.MULTILINE,
        minLines = if (spec.kind == PropertyKind.MULTILINE) 2 else 1,
        isError = error != null,
        textStyle = style,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardFor(spec.kind)),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    when (spec.kind) {
        PropertyKind.COLOR -> Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (hex in PropertySpecs.colorPresets) {
                Row(
                    Modifier
                        .size(32.dp)
                        .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable {
                            text = hex
                            onCommit(hex)
                        },
                ) {}
            }
            TextButton(onClick = { text = ""; onCommit("") }, enabled = current.isNotEmpty()) { Text("مسح") }
        }
        PropertyKind.IMAGE_SOURCE -> PickButton("اختيار صورة من الجهاز") { onPick(PickKind.IMAGE) }
        PropertyKind.MEDIA_SOURCE -> PickButton("اختيار فيديو أو صوت من الجهاز") { onPick(PickKind.MEDIA) }
        PropertyKind.FILE_SOURCE -> PickButton("اختيار ملف من الجهاز") { onPick(PickKind.FILE) }
        else -> Unit
    }
}

@Composable
private fun PickButton(label: String, onClick: () -> Unit) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private fun keyboardFor(kind: PropertyKind): KeyboardType = when (kind) {
    PropertyKind.URL, PropertyKind.HTTPS_URL, PropertyKind.IMAGE_SOURCE, PropertyKind.MEDIA_SOURCE, PropertyKind.FILE_SOURCE -> KeyboardType.Uri
    PropertyKind.PHONE -> KeyboardType.Phone
    else -> KeyboardType.Text
}
