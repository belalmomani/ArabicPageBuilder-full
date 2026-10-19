package com.arabicpagebuilder.editor

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arabicpagebuilder.core.export.AssetSource
import com.arabicpagebuilder.core.generator.GeneratedSite
import com.arabicpagebuilder.core.generator.HtmlGenerator
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.session.ElementCatalog
import com.arabicpagebuilder.ui.Messages
import java.io.ByteArrayInputStream

private enum class Device(val label: String, val widthDp: Int?) {
    PHONE("هاتف", null),
    TABLET("لوحي (768)", 768),
    DESKTOP("حاسوب (1280)", 1280),
}

private const val PREVIEW_HOST = "preview.invalid"
private const val PREVIEW_BASE = "https://$PREVIEW_HOST/"

/** بيانات المعاينة التي يقرؤها WebViewClient (يُستدعى بعضها من خيط خلفي). */
private class PreviewData {
    @Volatile var assets: AssetSource? = null
    @Volatile var allowExternal: Boolean = false
    var lastHtml: String? = null
    var lastAllowExternal: Boolean? = null
    var pendingScrollY: Int = 0
}

/**
 * معاينة حية ناتجة عن نموذج التصميم نفسه عبر المولّد. WebView للعرض فقط:
 * JavaScript معطّل، ولا وصول للملفات، ولا واجهة JavaScript↔Kotlin، وكل الموارد الخارجية محجوبة
 * ما لم يفعّلها المستخدم. الصور المحلية تُقدَّم من أصول المشروع بعد فحص المسار.
 */
@Composable
fun PreviewTab(state: EditorState, allowExternal: Boolean, onToggleExternal: (Boolean) -> Unit) {
    var device by remember { mutableStateOf(Device.PHONE) }
    val site = remember(state.doc) { HtmlGenerator.generate(state.doc) }
    val assets = remember(state.projectId) { state.assetSource() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (d in Device.entries) {
                if (d == device) Button(onClick = {}) { Text(d.label) } else OutlinedButton(onClick = { device = d }) { Text(d.label) }
            }
            Text("موارد خارجية", style = MaterialTheme.typography.bodySmall)
            Switch(checked = allowExternal, onCheckedChange = onToggleExternal)
        }
        val widthDp = device.widthDp
        Box(
            Modifier.weight(1f).fillMaxWidth().let { if (widthDp != null) it.horizontalScroll(rememberScrollState()) else it },
        ) {
            AndroidView(
                modifier = if (widthDp != null) Modifier.width(widthDp.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                factory = { ctx -> createPreviewWebView(ctx) },
                update = { wv -> updatePreview(wv, site, assets, allowExternal) },
            )
        }
        if (site.warnings.isNotEmpty()) {
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().heightIn(max = 110.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                Text("تنبيهات (${site.warnings.size})", style = MaterialTheme.typography.labelLarge)
                for (w in site.warnings) {
                    val type = state.doc.find(w.elementId)?.type?.let { ElementCatalog.label(it) } ?: ""
                    Text("• $type: ${Messages.warning(w.code)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun createPreviewWebView(context: Context): WebView = WebView(context).apply {
    tag = PreviewData()
    settings.javaScriptEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.domStorageEnabled = false
    settings.setSupportZoom(false)
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    webViewClient = object : WebViewClient() {
        // لا تنقّل داخل المعاينة: الروابط لا تعمل هنا عمدًا.
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val data = view?.tag as? PreviewData ?: return blocked()
            val uri = request?.url ?: return blocked()
            if (uri.host == PREVIEW_HOST) {
                val path = uri.path.orEmpty().removePrefix("/")
                if (path.startsWith("assets/")) {
                    val stream = data.assets?.open(path) ?: return notFound()
                    return WebResourceResponse(mimeOf(path), null, stream)
                }
                return notFound()
            }
            val external = uri.scheme == "https" || uri.scheme == "http"
            return if (external && data.allowExternal) null else blocked()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            val v = view ?: return
            val data = v.tag as? PreviewData ?: return
            if (data.pendingScrollY > 0) v.post { v.scrollTo(0, data.pendingScrollY) }
        }
    }
}

private fun updatePreview(wv: WebView, site: GeneratedSite, assets: AssetSource?, allowExternal: Boolean) {
    val data = wv.tag as PreviewData
    data.assets = assets
    data.allowExternal = allowExternal
    // نضمّن CSS داخل صفحة المعاينة فقط (ملف التصدير الحقيقي يبقى منفصلًا) لتفادي أي تخزين مؤقت قديم.
    val html = site.indexHtml.replace(
        "<link rel=\"stylesheet\" href=\"css/style.css\">",
        "<style>\n${site.styleCss}</style>",
    )
    if (data.lastHtml != html || data.lastAllowExternal != allowExternal) {
        data.pendingScrollY = wv.scrollY // نعيد موضع التمرير بعد إعادة التحميل
        data.lastHtml = html
        data.lastAllowExternal = allowExternal
        wv.loadDataWithBaseURL(PREVIEW_BASE, html, "text/html", "UTF-8", null)
    }
}

private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private fun notFound() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    else -> "application/octet-stream"
}
