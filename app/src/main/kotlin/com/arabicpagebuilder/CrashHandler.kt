package com.arabicpagebuilder

import android.app.Application
import android.os.Build
import com.arabicpagebuilder.core.log.LogKind

/** معلومات الجهاز والتطبيق التي تُرفق بكل حدث في السجل (لا تحتوي محتوى مشاريع ولا بيانات شخصية). */
object DeviceInfo {
    fun header(app: Application, screen: String, thread: String? = null): Map<String, String> {
        val version = try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            @Suppress("DEPRECATION")
            val code = info.versionCode // longVersionCode يحتاج API 28 وminSdk عندنا 26
            "${info.versionName} (code $code)"
        } catch (e: Exception) {
            "غير معروف"
        }
        return linkedMapOf(
            "version" to version,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "screen" to screen,
        ).also { if (thread != null) it["thread"] = thread }
    }
}

/**
 * يسجّل أي عطل غير ملتقَط في السجل المحلي ثم يترك لنظام أندرويد إكمال إغلاق التطبيق كالمعتاد.
 * لا يرسل شيئًا إلى أي مكان. أي فشل داخل المعالج نفسه يُتجاهل كي لا يُخفي العطل الأصلي.
 */
object CrashHandler {
    fun install(app: Application, container: AppContainer) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                container.errorLog.record(
                    kind = LogKind.CRASH,
                    title = "${error.javaClass.name}: ${error.message ?: ""}",
                    throwable = error,
                    header = DeviceInfo.header(app, container.screen.toString(), thread.name),
                    breadcrumbs = container.breadcrumbs.snapshot(),
                )
            } catch (e: Throwable) {
                // تجاهل
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
