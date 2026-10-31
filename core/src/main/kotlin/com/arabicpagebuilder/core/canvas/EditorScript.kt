package com.arabicpagebuilder.core.canvas

/**
 * سكربت وتنسيق صفحة «لوحة التصميم» (وضع التحرير). نص ثابت مراجَع يُحقن في صفحة المعاينة التحريرية فقط،
 * ولا يدخل الموقع المُصدَّر أبدًا. لا يستقبل نصًا من المستخدم ولا يعرّض أي واجهة لـ Kotlin: التطبيق يستدعي
 * دوال `window.__apb` عبر evaluateJavascript ويقرأ نتيجتها فقط.
 */
object EditorScript {

    val RUNTIME: String = """
(function () {
  "use strict";
  function byEid(id) {
    var els = document.querySelectorAll("[data-eid]");
    for (var i = 0; i < els.length; i++) { if (els[i].getAttribute("data-eid") === id) { return els[i]; } }
    return null;
  }
  var api = {};
  api.rects = function () {
    var out = {};
    var els = document.querySelectorAll("[data-eid]");
    for (var i = 0; i < els.length; i++) {
      var id = els[i].getAttribute("data-eid");
      if (id === null) { continue; }
      var r = els[i].getBoundingClientRect();
      if (r.width > 0 || r.height > 0) {
        out[id] = [r.left, r.top, r.right, r.bottom];
      }
    }
    return out;
  };
  api.setZoom = function (z) {
    var s = document.getElementById("__stage");
    if (!s) { return false; }
    var n = Number(z);
    if (!(n > 0.1 && n <= 4)) { return false; }
    var rtl = (document.documentElement.getAttribute("dir") || "") === "rtl";
    s.style.transformOrigin = rtl ? "100% 0" : "0 0";
    s.style.transform = n === 1 ? "" : "scale(" + n + ")";
    return true;
  };
  api.setSize = function (id, w, h) {
    var el = byEid(String(id));
    if (!el) { return false; }
    el.style.width = w ? String(w) : "";
    el.style.height = h ? String(h) : "";
    return true;
  };
  api.metrics = function () {
    var de = document.documentElement;
    return { scrollWidth: de.scrollWidth, scrollHeight: de.scrollHeight, viewWidth: de.clientWidth, viewHeight: de.clientHeight };
  };
  window.__apb = api;
})();
""".trimStart()

    /** تنسيق وضع التحرير: لا حركات، والعناصر المخفية تظهر باهتة بإطار متقطع لتبقى قابلة للتحديد. */
    const val CSS: String = "*, *::before, *::after { animation: none !important; transition: none !important; }\n" +
        "[data-hidden] { opacity: .45; outline: 1px dashed #888; }\n" +
        "html { -webkit-tap-highlight-color: transparent; -webkit-user-select: none; user-select: none; }\n" +
        "#__stage { transform-origin: 0 0; }\n"
}
