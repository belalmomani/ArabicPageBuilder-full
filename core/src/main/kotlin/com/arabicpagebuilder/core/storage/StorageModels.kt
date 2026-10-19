package com.arabicpagebuilder.core.storage

enum class StoreError {
    INVALID_ID,
    INVALID_NAME,
    NOT_FOUND,
    ALREADY_EXISTS,
    CORRUPT,
    NO_BACKUP,
    ASSET_NOT_ALLOWED,
    ASSET_TOO_LARGE,
    INVALID_ARCHIVE,
    ARCHIVE_TOO_LARGE,
    IO_ERROR,
}

sealed interface StoreResult<out T> {
    data class Ok<out T>(val value: T) : StoreResult<T>
    data class Err(val error: StoreError, val detail: String? = null) : StoreResult<Nothing>
}

data class LoadedProject(val id: String, val data: ProjectData)

/** ملخص للعرض في الشاشة الرئيسية. [corrupt] = تعذرت قراءة الملف؛ و[hasBackup] = توجد نسخة يمكن استعادتها. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val corrupt: Boolean,
    val hasBackup: Boolean,
)
