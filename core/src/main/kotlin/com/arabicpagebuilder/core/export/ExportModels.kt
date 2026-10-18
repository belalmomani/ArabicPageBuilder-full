package com.arabicpagebuilder.core.export

import com.arabicpagebuilder.core.generator.GenerationWarning
import java.io.InputStream

/** مصدر الملفات المحلية للمشروع (صور وغيرها) بمسار نسبي مثل assets/images/a.png. */
interface AssetSource {
    fun exists(path: String): Boolean
    fun open(path: String): InputStream?
}

data class ExportLimits(
    val maxAssetBytes: Long = 20L * 1024 * 1024,
    val maxTotalBytes: Long = 200L * 1024 * 1024,
)

enum class ExportError { ASSET_TOO_LARGE, TOTAL_TOO_LARGE, IO_ERROR, VERIFICATION_FAILED }

enum class ExportWarningCode { MISSING_ASSET, LOCAL_LINK_TARGET_MISSING }

data class ExportWarning(val code: ExportWarningCode, val path: String, val elementId: String)

sealed interface ExportResult {
    /** [entries] أسماء المدخلات كما هي داخل ZIP (مع المجلد الجذر). */
    data class Success(
        val entries: List<String>,
        val warnings: List<ExportWarning>,
        val generationWarnings: List<GenerationWarning>,
    ) : ExportResult

    data class Failure(val error: ExportError, val detail: String? = null) : ExportResult
}
