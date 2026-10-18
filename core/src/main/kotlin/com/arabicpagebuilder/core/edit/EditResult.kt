package com.arabicpagebuilder.core.edit

import com.arabicpagebuilder.core.model.PageDocument

/** أسباب فشل التعديل. الواجهة تحوّلها إلى رسائل عربية مفهومة (المرحلة 7). */
enum class EditError {
    ELEMENT_NOT_FOUND,
    PARENT_NOT_FOUND,
    CANNOT_MODIFY_ROOT,
    CANNOT_ADD_ROOT_TYPE,
    PARENT_CANNOT_HAVE_CHILDREN,
    CANNOT_MOVE_INTO_SELF,
    CANNOT_MOVE_INTO_DESCENDANT,
    INDEX_OUT_OF_RANGE,
    DUPLICATE_ID,
    INVALID_PROPERTY_KEY,
    ALREADY_FIRST,
    ALREADY_LAST,
}

sealed interface EditResult {
    data class Success(val document: PageDocument) : EditResult
    data class Failure(val error: EditError) : EditResult
}
