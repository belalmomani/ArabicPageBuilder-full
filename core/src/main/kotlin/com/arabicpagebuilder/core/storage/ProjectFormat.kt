package com.arabicpagebuilder.core.storage

import com.arabicpagebuilder.core.SchemaVersion
import com.arabicpagebuilder.core.model.Element
import com.arabicpagebuilder.core.model.ElementType
import com.arabicpagebuilder.core.model.PageDocument
import com.arabicpagebuilder.core.model.TextDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** بيانات المشروع كما تُحفظ: اسم المشروع وتواريخه والمستند. */
data class ProjectData(
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val document: PageDocument,
)

enum class FormatError {
    MALFORMED_JSON,
    UNSUPPORTED_SCHEMA_VERSION,
    INVALID_STRUCTURE,
    UNKNOWN_ELEMENT_TYPE,
    TOO_LARGE,
}

sealed interface DecodeResult {
    data class Success(val data: ProjectData) : DecodeResult
    data class Failure(val error: FormatError, val detail: String? = null) : DecodeResult
}

@Serializable
internal data class ElementDto(
    val id: String,
    val type: String,
    val props: Map<String, String> = emptyMap(),
    val children: List<ElementDto> = emptyList(),
)

@Serializable
internal data class ProjectDto(
    val schemaVersion: Int,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val title: String,
    val direction: String,
    val root: ElementDto,
)

/**
 * ترميز المشروع إلى JSON وقراءته مع تحقق صارم: لا يُقبل ملف إلا إذا كانت شجرته صالحة وضمن الحدود.
 * هذا هو خط الدفاع الأول عند فتح الملفات والاستيراد.
 */
object ProjectFormat {
    const val MAX_CHARS = 20_000_000
    const val MAX_NODES = 20_000
    const val MAX_DEPTH = 64
    const val MAX_ID_LENGTH = 128
    const val MAX_PROPS_PER_ELEMENT = 100
    const val MAX_PROP_KEY_LENGTH = 64
    const val MAX_PROP_VALUE_LENGTH = 100_000
    const val MAX_NAME_LENGTH = 200
    private const val MAX_JSON_NESTING = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private class FormatException(val error: FormatError, val detail: String) : Exception(detail)

    fun encode(data: ProjectData): String {
        val dto = ProjectDto(
            schemaVersion = SchemaVersion.CURRENT,
            name = data.name,
            createdAt = data.createdAt,
            modifiedAt = data.modifiedAt,
            title = data.document.title,
            direction = data.document.direction.name,
            root = toDto(data.document.root),
        )
        return json.encodeToString(ProjectDto.serializer(), dto)
    }

    fun decode(text: String, maxChars: Int = MAX_CHARS): DecodeResult {
        if (text.length > maxChars) return DecodeResult.Failure(FormatError.TOO_LARGE, "${text.length}")
        if (nestingDepth(text) > MAX_JSON_NESTING) {
            return DecodeResult.Failure(FormatError.INVALID_STRUCTURE, "تداخل JSON أعمق من المسموح")
        }
        val dto = try {
            json.decodeFromString(ProjectDto.serializer(), text)
        } catch (e: SerializationException) {
            return DecodeResult.Failure(FormatError.MALFORMED_JSON, e.message)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Failure(FormatError.MALFORMED_JSON, e.message)
        }
        if (dto.schemaVersion != SchemaVersion.CURRENT) {
            return DecodeResult.Failure(FormatError.UNSUPPORTED_SCHEMA_VERSION, dto.schemaVersion.toString())
        }
        return try {
            DecodeResult.Success(toData(dto))
        } catch (e: FormatException) {
            DecodeResult.Failure(e.error, e.detail)
        }
    }

    // ---- تحويل ----

    private fun toDto(e: Element): ElementDto =
        ElementDto(e.id, e.type.name, e.props, e.children.map { toDto(it) })

    private class Ctx {
        val ids = HashSet<String>()
        var count = 0
    }

    private fun toData(dto: ProjectDto): ProjectData {
        val name = dto.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) invalid("اسم المشروع غير صالح")
        val direction = TextDirection.entries.firstOrNull { it.name == dto.direction }
            ?: invalid("اتجاه غير معروف: ${dto.direction}")
        if (dto.root.type != ElementType.PAGE_ROOT.name) invalid("الجذر ليس PAGE_ROOT")
        val root = toElement(dto.root, 1, Ctx(), isRoot = true)
        return ProjectData(name, dto.createdAt, dto.modifiedAt, PageDocument(dto.title, direction, root))
    }

    private fun toElement(dto: ElementDto, depth: Int, ctx: Ctx, isRoot: Boolean = false): Element {
        if (depth > MAX_DEPTH) invalid("عمق الشجرة أكبر من $MAX_DEPTH")
        if (++ctx.count > MAX_NODES) invalid("عدد العناصر أكبر من $MAX_NODES")
        if (dto.id.isBlank() || dto.id.length > MAX_ID_LENGTH) invalid("معرّف عنصر غير صالح")
        if (!ctx.ids.add(dto.id)) invalid("معرّف مكرر: ${dto.id}")
        val type = ElementType.entries.firstOrNull { it.name == dto.type }
            ?: throw FormatException(FormatError.UNKNOWN_ELEMENT_TYPE, dto.type)
        if (type == ElementType.PAGE_ROOT && !isRoot) invalid("PAGE_ROOT داخل الشجرة")
        if (dto.children.isNotEmpty() && !type.acceptsChildren) invalid("عنصر ${dto.type} لا يقبل أبناء")
        if (dto.props.size > MAX_PROPS_PER_ELEMENT) invalid("خصائص أكثر من المسموح في ${dto.id}")
        for ((k, v) in dto.props) {
            if (k.isBlank() || k.length > MAX_PROP_KEY_LENGTH) invalid("مفتاح خاصية غير صالح")
            if (v.length > MAX_PROP_VALUE_LENGTH) invalid("قيمة خاصية أطول من المسموح")
        }
        return Element(dto.id, type, dto.props, dto.children.map { toElement(it, depth + 1, ctx) })
    }

    private fun invalid(detail: String): Nothing = throw FormatException(FormatError.INVALID_STRUCTURE, detail)

    /** أقصى عمق تداخل للأقواس خارج النصوص؛ يتوقف مبكرًا عند تجاوز الحد لتفادي الانهيار. */
    private fun nestingDepth(s: String): Int {
        var depth = 0
        var max = 0
        var inString = false
        var escaped = false
        for (c in s) {
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        if (depth > max) max = depth
                        if (max > MAX_JSON_NESTING) return max
                    }
                    '}', ']' -> depth--
                }
            }
        }
        return max
    }
}
