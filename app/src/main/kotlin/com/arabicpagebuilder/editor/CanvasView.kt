package com.arabicpagebuilder.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.arabicpagebuilder.PageBuilderApp
import com.arabicpagebuilder.core.canvas.Box
import com.arabicpagebuilder.core.canvas.DropResolver
import com.arabicpagebuilder.core.canvas.EditorScript
import com.arabicpagebuilder.core.canvas.GapGuide
import com.arabicpagebuilder.core.canvas.GapSide
import com.arabicpagebuilder.core.canvas.GuideCalculator
import com.arabicpagebuilder.core.canvas.Guides
import com.arabicpagebuilder.core.canvas.HitTester
import com.arabicpagebuilder.core.canvas.RectsParser
import com.arabicpagebuilder.core.canvas.ResizeHandle
import com.arabicpagebuilder.core.canvas.ResizeMath
import com.arabicpagebuilder.core.canvas.ResizeResult
import com.arabicpagebuilder.core.canvas.ResolvedDrop
import com.arabicpagebuilder.core.canvas.ZoomMath
import com.arabicpagebuilder.core.export.AssetSource
import com.arabicpagebuilder.core.generator.GeneratedSite
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import com.arabicpagebuilder.core.model.find
import com.arabicpagebuilder.core.model.findParent
import com.arabicpagebuilder.core.session.DropZone
import com.arabicpagebuilder.core.session.ElementCatalog
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * لوحة التصميم: معاينة WebView لصفحة «وضع التحرير» (كل عنصر يحمل data-eid) مع طبقة رسم فوقها.
 * - **النقر** يحدد أعمق عنصر تحت الإصبع من مستطيلات تُقرأ بـ evaluateJavascript (بلا أي واجهة JS↔Kotlin).
 * - **إطار التحديد** ومقابض تغيير الحجم (العرض/الارتفاع/الزاوية) وأدلة المحاذاة والمسافات تُرسم فوق الصفحة.
 * - **الضغط المطوّل** على عنصر ثم السحب ينقله بين الأقسام (مؤشر موضع الإدراج، وتمرير تلقائي عند الحافة).
 * - **القرص بإصبعين** يكبّر ويصغّر المساحة (تحويل CSS داخل الصفحة)، والتمرير الأحادي للصفحة كالمعتاد.
 * كل القرارات الهندسية في `core.canvas` (مختبرة آليًا)؛ هذا الصنف ربط بنظام اللمس والرسم فقط.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
class CanvasView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onSelect(id: String?)
        fun onResize(id: String, widthCss: String?, heightCss: String?)
        fun onDrop(draggedId: String, targetId: String, zone: DropZone)
        fun onZoom(zoom: Double)
    }

    private enum class Mode { IDLE, RESIZE, DRAG, PINCH }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val webView = WebView(context)
    private val overlay = Overlay(context)

    // ---- الحالة ----
    private var doc: PageDocument? = null
    private var selectedId: String? = null
    private var rtl = true
    private var zoom = 1.0
    private var guidesOn = true
    @Volatile private var assets: AssetSource? = null
    @Volatile private var allowExternal = false
    @Volatile private var dead = false
    private var lastHtml: String? = null

    private var boxes: Map<String, Box> = emptyMap() // CSS px، بإطار لحظة الاستعلام
    private var queryScrollX = 0
    private var queryScrollY = 0
    private var refreshQueued = false
    private var guides: Guides? = null

    private var mode = Mode.IDLE
    private var tempSize = false

    // تغيير الحجم
    private var resizeHandle: ResizeHandle? = null
    private var resizeStartBox: Box? = null
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeResult: ResizeResult? = null

    // السحب
    private var dragId: String? = null
    private var dragX = 0f
    private var dragY = 0f
    private var dropTarget: ResolvedDrop? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (mode == Mode.IDLE) selectAt(e.x, e.y)
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (mode == Mode.IDLE) beginDrag(e.x, e.y)
            }
        },
    )

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyZoom(zoom * detector.scaleFactor, fromGesture = true)
                return true
            }
        },
    )

    init {
        addView(webView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // JavaScript مفعّل هنا لأجل سكربت التحرير الثابت فقط؛ لا واجهة JS↔Kotlin، وسياسة CSP تمنع أي مصدر آخر.
        with(webView.settings) {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return blocked()
                if (uri.host == PREVIEW_HOST) {
                    val path = uri.path.orEmpty().removePrefix("/")
                    if (path.startsWith("assets/")) {
                        val stream = assets?.open(path) ?: return notFound()
                        return WebResourceResponse(mimeOf(path), null, stream)
                    }
                    return notFound()
                }
                if (uri.scheme == "data" || uri.scheme == "about") return null
                val external = uri.scheme == "https" || uri.scheme == "http"
                return if (external && allowExternal) null else blocked()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                applyZoomAndRefresh()
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                dead = true
                (context.applicationContext as? PageBuilderApp)?.container?.logError("انهارت عملية عرض لوحة التصميم (crashed=${detail?.didCrash()})")
                return true
            }
        }
        webView.setOnScrollChangeListener { _, _, _, _, _ ->
            overlay.invalidate()
            refreshRects()
        }
    }

    // ---- واجهة الاستخدام من Compose ----

    fun configure(
        site: GeneratedSite,
        doc: PageDocument,
        selectedId: String?,
        assets: AssetSource?,
        allowExternal: Boolean,
        zoom: Double,
        guides: Boolean,
    ) {
        if (dead) return
        this.doc = doc
        this.rtl = doc.direction == TextDirection.RTL
        this.selectedId = selectedId
        this.assets = assets
        this.allowExternal = allowExternal
        this.guidesOn = guides
        val html = buildHtml(site, allowExternal)
        val zoomChanged = abs(zoom - this.zoom) > 1e-6
        this.zoom = ZoomMath.clamp(zoom)
        if (html != lastHtml) {
            lastHtml = html
            tempSize = false
            webView.loadDataWithBaseURL(PREVIEW_BASE, html, "text/html", "UTF-8", null)
        } else if (zoomChanged) {
            applyZoomAndRefresh()
        }
        recompute()
    }

    private fun buildHtml(site: GeneratedSite, external: Boolean): String {
        var h = site.indexHtml.replace("<link rel=\"stylesheet\" href=\"css/style.css\">", "<style>\n${site.styleCss}</style>")
        val ext = if (external) " https:" else ""
        val csp = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "img-src https://$PREVIEW_HOST$ext; media-src https://$PREVIEW_HOST$ext; form-action 'none'; base-uri 'none'"
        h = h.replaceFirst("<head>\n", "<head>\n  <meta http-equiv=\"Content-Security-Policy\" content=\"$csp\">\n")
        h = h.replaceFirst("</head>", "<style>\n${EditorScript.CSS}</style>\n</head>")
        val idx = h.lastIndexOf("</body>")
        if (idx >= 0) h = h.substring(0, idx) + "<script>\n${EditorScript.RUNTIME}</script>\n" + h.substring(idx)
        return h
    }

    // ---- قراءة المستطيلات والتكبير ----

    private fun applyZoomAndRefresh() {
        if (dead) return
        webView.evaluateJavascript("window.__apb && window.__apb.setZoom(${String.format(Locale.ROOT, "%.3f", zoom)})") { refreshRects() }
    }

    private fun applyZoom(z: Double, fromGesture: Boolean) {
        val nz = ZoomMath.clamp(z)
        if (abs(nz - zoom) < 1e-4) return
        zoom = nz
        if (fromGesture) listener?.onZoom(nz)
        applyZoomAndRefresh()
    }

    private fun refreshRects() {
        if (dead || refreshQueued) return
        refreshQueued = true
        postOnAnimation {
            refreshQueued = false
            val sx = webView.scrollX
            val sy = webView.scrollY
            webView.evaluateJavascript("window.__apb ? window.__apb.rects() : null") { raw ->
                boxes = RectsParser.parse(raw)
                queryScrollX = sx
                queryScrollY = sy
                if (mode == Mode.DRAG) updateDropTarget()
                recompute()
            }
        }
    }

    // ---- تحويل الإحداثيات: CSS px (إطار الاستعلام) ↔ بكسلات العرض ----

    private fun offX(): Int = queryScrollX - webView.scrollX
    private fun offY(): Int = queryScrollY - webView.scrollY
    private fun toViewX(css: Double): Float = (css * density + offX()).toFloat()
    private fun toViewY(css: Double): Float = (css * density + offY()).toFloat()
    private fun toCssX(view: Float): Double = (view - offX()) / density.toDouble()
    private fun toCssY(view: Float): Double = (view - offY()) / density.toDouble()

    private fun recompute() {
        val d = doc
        val sel = selectedId
        guides = if (d != null && sel != null && guidesOn && sel != d.root.id && boxes[sel] != null && mode != Mode.PINCH) computeGuides(d, sel) else null
        overlay.invalidate()
    }

    private fun computeGuides(d: PageDocument, sel: String): Guides {
        val parent = d.root.findParent(sel)
        val siblings = parent?.children.orEmpty().filter { it.id != sel }.mapNotNull { e -> boxes[e.id]?.let { e.id to it } }.toMap()
        return GuideCalculator.compute(boxes.getValue(sel), siblings, parent?.let { boxes[it.id] })
    }

    // ---- اللمس ----

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        scaler.onTouchEvent(ev)
        // ضغط مطوّل ثم رفع الإصبع دون أي حركة: لم يصلنا MOVE لنعترض، فنُنهي السحب هنا كي لا يبقى معلقًا.
        if (mode == Mode.DRAG && (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)) {
            handleDrag(ev)
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mode != Mode.IDLE) resetMode()
                val handle = handleAt(ev.x, ev.y)
                if (handle != null) {
                    beginResize(handle, ev)
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                beginPinch()
                return true
            }
        }
        return mode != Mode.IDLE
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (mode) {
            Mode.RESIZE -> handleResize(ev)
            Mode.DRAG -> handleDrag(ev)
            Mode.PINCH -> {
                scaler.onTouchEvent(ev)
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) endMode()
            }
            Mode.IDLE -> return super.onTouchEvent(ev)
        }
        return true
    }

    private fun selectAt(x: Float, y: Float) {
        val d = doc ?: return
        val id = HitTester.pick(d, boxes, toCssX(x), toCssY(y))
        listener?.onSelect(id)
    }

    private fun beginPinch() {
        mode = Mode.PINCH
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun resetMode() {
        if (mode == Mode.RESIZE) cancelResizePreview()
        mode = Mode.IDLE
        dragId = null
        dropTarget = null
        resizeHandle = null
        overlay.invalidate()
    }

    private fun endMode() {
        mode = Mode.IDLE
        dragId = null
        dropTarget = null
        resizeHandle = null
        recompute()
    }

    // ---- تغيير الحجم بالمقابض ----

    private data class HandlePoint(val handle: ResizeHandle, val x: Float, val y: Float)

    private fun handlePoints(): List<HandlePoint>? {
        val d = doc ?: return null
        val id = selectedId ?: return null
        if (id == d.root.id) return null
        val b = boxes[id] ?: return null
        val endX = if (rtl) toViewX(b.left) else toViewX(b.right)
        val cx = toViewX(b.centerX)
        val cy = toViewY(b.centerY)
        val bottom = toViewY(b.bottom)
        return listOf(
            HandlePoint(ResizeHandle.BOTH, endX, bottom),
            HandlePoint(ResizeHandle.WIDTH, endX, cy),
            HandlePoint(ResizeHandle.HEIGHT, cx, bottom),
        )
    }

    private fun handleAt(x: Float, y: Float): ResizeHandle? {
        if (mode != Mode.IDLE) return null
        val radius = 24f * density
        return handlePoints()?.firstOrNull { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) <= radius }?.handle
    }

    private fun beginResize(handle: ResizeHandle, ev: MotionEvent) {
        val id = selectedId ?: return
        resizeStartBox = boxes[id] ?: return
        resizeHandle = handle
        resizeStartX = ev.x
        resizeStartY = ev.y
        resizeResult = null
        mode = Mode.RESIZE
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun handleResize(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                computeResize(ev)
                applyLiveSize()
            }
            MotionEvent.ACTION_UP -> {
                computeResize(ev)
                commitResize()
                endMode()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelResizePreview()
                endMode()
            }
        }
    }

    private fun computeResize(ev: MotionEvent) {
        val d = doc ?: return
        val id = selectedId ?: return
        val start = resizeStartBox ?: return
        val handle = resizeHandle ?: return
        val z = zoom
        // الإصبع يتحرك ببكسلات العرض؛ نحوّل إلى px تخطيط العنصر (بقسمة الكثافة ثم التكبير)
        val dx = (ev.x - resizeStartX) / density / z
        val dy = (ev.y - resizeStartY) / density / z
        val parentEl = d.root.findParent(id)
        val parentWidth = parentEl?.let { boxes[it.id] }?.width?.div(z)
        val siblingWidths = parentEl?.children.orEmpty().filter { it.id != id }.mapNotNull { boxes[it.id]?.width?.div(z) }
        resizeResult = ResizeMath.resize(Box(0.0, 0.0, start.width / z, start.height / z), handle, dx, dy, rtl, parentWidth, siblingWidths)
    }

    private fun applyLiveSize() {
        val id = selectedId ?: return
        val r = resizeResult ?: return
        tempSize = true
        webView.evaluateJavascript(
            "window.__apb && window.__apb.setSize(${JSONObject.quote(id)}, ${JSONObject.quote(r.widthCss.orEmpty())}, ${JSONObject.quote(r.heightCss.orEmpty())})",
            null,
        )
        refreshRects()
    }

    private fun cancelResizePreview() {
        val id = selectedId ?: return
        if (!tempSize) return
        tempSize = false
        webView.evaluateJavascript("window.__apb && window.__apb.setSize(${JSONObject.quote(id)}, \"\", \"\")", null)
        refreshRects()
    }

    private fun commitResize() {
        val id = selectedId ?: return
        val r = resizeResult ?: return
        listener?.onResize(id, r.widthCss, r.heightCss)
        // إن لم يتغير مستند المشروع (فلا إعادة تحميل) نزيل الحجم المؤقت بعد لحظة.
        postDelayed({ if (tempSize) cancelResizePreview() }, 400)
    }

    // ---- السحب بين الأقسام ----

    private fun beginDrag(x: Float, y: Float) {
        val d = doc ?: return
        val id = HitTester.pick(d, boxes, toCssX(x), toCssY(y)) ?: return
        if (id == d.root.id) return
        listener?.onSelect(id)
        selectedId = id
        dragId = id
        dragX = x
        dragY = y
        dropTarget = null
        mode = Mode.DRAG
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        postOnAnimation(autoScroll)
        recompute()
    }

    private fun handleDrag(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragX = ev.x
                dragY = ev.y
                updateDropTarget()
                overlay.invalidate()
            }
            MotionEvent.ACTION_UP -> {
                dragX = ev.x
                dragY = ev.y
                updateDropTarget()
                val id = dragId
                val t = dropTarget
                endMode()
                if (id != null && t != null) listener?.onDrop(id, t.targetId, t.zone)
            }
            MotionEvent.ACTION_CANCEL -> endMode()
        }
    }

    private fun updateDropTarget() {
        val d = doc ?: return
        dropTarget = DropResolver.resolve(d, boxes, toCssX(dragX), toCssY(dragY), dragId, rtl)
    }

    /** تمرير الصفحة تلقائيًا أثناء السحب عند اقتراب الإصبع من الحافة العلوية أو السفلية. */
    private val autoScroll = object : Runnable {
        override fun run() {
            if (mode != Mode.DRAG) return
            val edge = 56f * density
            val step = (10f * density).roundToInt()
            val dy = when {
                dragY < edge -> -step
                dragY > height - edge -> step
                else -> 0
            }
            if (dy != 0) {
                webView.scrollBy(0, dy)
                refreshRects()
            }
            postOnAnimation(this)
        }
    }

    // ---- الرسم ----

    private fun dp(v: Float): Float = v * density

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF1A73E8.toInt(); strokeWidth = 2f * density }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF1A73E8.toInt() }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 2f * density }
    private val alignPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE91E63.toInt(); strokeWidth = 1f * density }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFF57C00.toInt(); strokeWidth = 1.5f * density }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF2E7D32.toInt(); strokeWidth = 4f * density }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 11f * density }

    private fun chip(canvas: Canvas, text: String, cx: Float, cy: Float, color: Int) {
        val padX = dp(6f)
        val w = textPaint.measureText(text) + padX * 2
        val h = textPaint.textSize + dp(6f)
        val r = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        fillPaint.color = color
        canvas.drawRoundRect(r, dp(4f), dp(4f), fillPaint)
        canvas.drawText(text, r.left + padX, r.bottom - dp(4f) - textPaint.descent() / 2, textPaint)
    }

    internal fun drawOverlay(canvas: Canvas) {
        val d = doc ?: return
        // 1) هدف الإفلات أثناء السحب
        if (mode == Mode.DRAG) {
            dragId?.let { id ->
                boxes[id]?.let { b ->
                    fillPaint.color = 0x331A73E8
                    canvas.drawRect(toViewX(b.left), toViewY(b.top), toViewX(b.right), toViewY(b.bottom), fillPaint)
                }
            }
            val t = dropTarget
            val tb = t?.let { boxes[it.targetId] }
            if (t != null && tb != null) {
                val l = toViewX(tb.left); val r = toViewX(tb.right); val top = toViewY(tb.top); val bot = toViewY(tb.bottom)
                when (t.zone) {
                    DropZone.INSIDE -> {
                        fillPaint.color = 0x332E7D32
                        canvas.drawRect(l, top, r, bot, fillPaint)
                        canvas.drawRect(l, top, r, bot, dropPaint)
                    }
                    DropZone.BEFORE, DropZone.AFTER -> {
                        val before = t.zone == DropZone.BEFORE
                        if (t.horizontal) {
                            // «قبل» = جهة بداية الاتجاه: يمين في RTL ويسار في LTR
                            val x = if (before == rtl) r else l
                            canvas.drawLine(x, top, x, bot, dropPaint)
                        } else {
                            val y = if (before) top else bot
                            canvas.drawLine(l, y, r, y, dropPaint)
                        }
                    }
                }
            }
        }

        // 2) الأدلة (محاذاة ومسافات)
        guides?.let { g ->
            for (a in g.align) {
                if (a.vertical) {
                    val x = toViewX(a.position)
                    canvas.drawLine(x, toViewY(a.from), x, toViewY(a.to), alignPaint)
                } else {
                    val y = toViewY(a.position)
                    canvas.drawLine(toViewX(a.from), y, toViewX(a.to), y, alignPaint)
                }
            }
            for (gap in g.gaps) drawGap(canvas, gap)
        }

        // 3) إطار التحديد ومقابضه
        val sel = selectedId
        val b = sel?.let { boxes[it] }
        if (sel != null && b != null && sel != d.root.id) {
            val l = toViewX(b.left); val t = toViewY(b.top); val r = toViewX(b.right); val bt = toViewY(b.bottom)
            canvas.drawRect(l, t, r, bt, selectionPaint)
            val typeLabel = d.find(sel)?.let { ElementCatalog.label(it.type) } ?: ""
            val size = "${(b.width / zoom).roundToInt()}×${(b.height / zoom).roundToInt()}"
            val labelY = if (t - dp(16f) < dp(4f)) bt + dp(16f) else t - dp(12f)
            chip(canvas, "$typeLabel · $size", (l + r) / 2, labelY, 0xFF1A73E8.toInt())
            if (mode != Mode.DRAG) {
                for (hp in handlePoints().orEmpty()) {
                    canvas.drawCircle(hp.x, hp.y, dp(9f), handlePaint)
                    canvas.drawCircle(hp.x, hp.y, dp(9f), handleRing)
                }
            }
            if (mode == Mode.RESIZE) {
                resizeResult?.let { rr ->
                    val w = rr.widthCss ?: "${(b.width / zoom).roundToInt()}px"
                    val h = rr.heightCss ?: "${(b.height / zoom).roundToInt()}px"
                    chip(canvas, "$w × $h" + (rr.snappedTo?.let { " — $it" } ?: ""), (l + r) / 2, (t + bt) / 2, 0xFF2E7D32.toInt())
                }
            }
        }
    }

    private fun drawGap(canvas: Canvas, gap: GapGuide) {
        val x1 = toViewX(gap.x1); val y1 = toViewY(gap.y1); val x2 = toViewX(gap.x2); val y2 = toViewY(gap.y2)
        canvas.drawLine(x1, y1, x2, y2, gapPaint)
        // سدّادتان صغيرتان عند الطرفين
        val cap = dp(4f)
        if (gap.side == GapSide.LEFT || gap.side == GapSide.RIGHT) {
            canvas.drawLine(x1, y1 - cap, x1, y1 + cap, gapPaint); canvas.drawLine(x2, y2 - cap, x2, y2 + cap, gapPaint)
        } else {
            canvas.drawLine(x1 - cap, y1, x1 + cap, y1, gapPaint); canvas.drawLine(x2 - cap, y2, x2 + cap, y2, gapPaint)
        }
        val shown = (gap.distance / zoom).roundToInt()
        chip(canvas, "$shown", (x1 + x2) / 2, (y1 + y2) / 2, 0xFFF57C00.toInt())
    }

    private inner class Overlay(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            drawOverlay(canvas)
        }
    }
}
