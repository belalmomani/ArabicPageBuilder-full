package com.arabicpagebuilder.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arabicpagebuilder.core.canvas.ZoomMath
import com.arabicpagebuilder.core.generator.HtmlGenerator
import com.arabicpagebuilder.core.session.DropZone
import com.arabicpagebuilder.core.session.ElementCatalog

private enum class CanvasDevice(val label: String, val widthDp: Int?) {
    PHONE("هاتف", null),
    TABLET("لوحي (768)", 768),
    DESKTOP("حاسوب (1280)", 1280),
}

/**
 * «اللوحة»: تحرير مباشر فوق المعاينة. اضغط لتحديد عنصر، واسحب مقابضه لتغيير حجمه، واضغط مطوّلًا ثم اسحب لنقله،
 * وبإصبعين كبّر أو صغّر. تظهر أدلة المحاذاة والمسافات حول المحدد. الشجرة في «التصميم» تبقى متزامنة معها.
 */
@Composable
fun CanvasTab(state: EditorState, allowExternal: Boolean, onOpenProperties: () -> Unit, onOpenLibrary: () -> Unit) {
    var device by remember { mutableStateOf(CanvasDevice.PHONE) }
    var zoom by remember { mutableDoubleStateOf(1.0) }
    var guides by remember { mutableStateOf(true) }
    val site = remember(state.doc) { HtmlGenerator.generate(state.doc, editMode = true) }
    val assets = remember(state.projectId) { state.assetSource() }
    val screenWidth = LocalConfiguration.current.screenWidthDp

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            for (d in CanvasDevice.entries) {
                if (d == device) Button(onClick = {}, contentPadding = pad) { Text(d.label, fontSize = 13.sp) }
                else OutlinedButton(onClick = { device = d; zoom = ZoomMath.fit((d.widthDp ?: screenWidth).toDouble(), screenWidth.toDouble()) }, contentPadding = pad) { Text(d.label, fontSize = 13.sp) }
            }
            OutlinedButton(onClick = { zoom = ZoomMath.zoomOut(zoom) }, contentPadding = pad) { Text("−", fontSize = 16.sp) }
            Text(ZoomMath.label(zoom), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { zoom = ZoomMath.zoomIn(zoom) }, contentPadding = pad) { Text("+", fontSize = 16.sp) }
            OutlinedButton(
                onClick = { zoom = ZoomMath.fit((device.widthDp ?: screenWidth).toDouble(), screenWidth.toDouble()) },
                contentPadding = pad,
            ) { Text("ملاءمة", fontSize = 13.sp) }
            Text("الأدلة", style = MaterialTheme.typography.labelMedium)
            Switch(checked = guides, onCheckedChange = { guides = it })
        }

        val widthDp = device.widthDp
        Box(Modifier.weight(1f).fillMaxWidth().let { if (widthDp != null) it.horizontalScroll(rememberScrollState()) else it }) {
            AndroidView(
                modifier = if (widthDp != null) Modifier.width(widthDp.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                factory = { ctx ->
                    CanvasView(ctx).also { view ->
                        view.listener = object : CanvasView.Listener {
                            override fun onSelect(id: String?) {
                                state.select(id)
                            }

                            override fun onResize(id: String, widthCss: String?, heightCss: String?) {
                                val values = LinkedHashMap<String, String?>()
                                if (widthCss != null) values["width"] = widthCss
                                if (heightCss != null) values["height"] = heightCss
                                state.select(id)
                                state.apply { it.setProperties(values) }
                            }

                            override fun onDrop(draggedId: String, targetId: String, zone: DropZone) {
                                state.apply { it.dragMove(draggedId, targetId, zone) }
                            }

                            override fun onZoom(z: Double) {
                                // إيماءة القرص طبّقت التكبير داخل الصفحة بالفعل؛ نحدّث حالة الشريط (نسبة التكبير) لتبقى متطابقة.
                                zoom = z
                            }
                        }
                    }
                },
                update = { view -> view.configure(site, state.doc, state.selectedId, assets, allowExternal, zoom, guides) },
            )
        }

        HorizontalDivider()
        SelectionBar(state, onOpenProperties, onOpenLibrary)
    }
}

@Composable
private fun SelectionBar(state: EditorState, onOpenProperties: () -> Unit, onOpenLibrary: () -> Unit) {
    val sel = state.selected
    val canModify = sel != null && sel.id != state.doc.root.id
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pad = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        Text(
            if (sel != null) ElementCatalog.label(sel.type) else "اضغط عنصرًا لتحديده · اسحب المقابض لتغيير الحجم · اضغط مطوّلًا ثم اسحب للنقل · أصبعان للتكبير",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Button(onClick = onOpenLibrary, contentPadding = pad) { Text("+ إضافة", fontSize = 13.sp) }
        OutlinedButton(onClick = onOpenProperties, enabled = sel != null, contentPadding = pad) { Text("الخصائص", fontSize = 13.sp) }
        OutlinedButton(onClick = { state.apply { it.moveSelectedUp() } }, enabled = canModify, contentPadding = pad) { Text("▲", fontSize = 13.sp) }
        OutlinedButton(onClick = { state.apply { it.moveSelectedDown() } }, enabled = canModify, contentPadding = pad) { Text("▼", fontSize = 13.sp) }
        OutlinedButton(onClick = { state.apply { it.duplicateSelected() } }, enabled = canModify, contentPadding = pad) { Text("نسخ", fontSize = 13.sp) }
        OutlinedButton(onClick = { state.apply { it.removeSelected() } }, enabled = canModify, contentPadding = pad) { Text("حذف", fontSize = 13.sp) }
    }
}
