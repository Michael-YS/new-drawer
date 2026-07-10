package com.drawer.server

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the Ktor HTTP surface. Mirrors the [com.drawer.core.db]
 * schema but in transport-friendly form (camelCase, ISO-style types).
 *
 * Server decodes incoming JSON into these, converts to DB rows,
 * writes; reads rows, converts back to these, encodes for response.
 */

@Serializable
data class SourceFolderDto(
    val id: Long,
    val path: String,
    val displayName: String,
    val enabled: Boolean,
    val recursive: Boolean,
    val addedAt: Long,
)

@Serializable
data class CreateSourceFolderRequest(
    val path: String,
    val displayName: String,
    val recursive: Boolean = true,
)

@Serializable
data class TargetRootDirDto(
    val id: Long,
    val path: String,
    val displayName: String,
    val isDefault: Boolean,
    val addedAt: Long,
)

@Serializable
data class CreateTargetRootDirRequest(
    val path: String,
    val displayName: String,
)

@Serializable
data class TargetFolderDto(
    val id: Long,
    val rootDirId: Long,
    val name: String,
    val displayName: String,
    val sortOrder: Int,
    val lastUsedAt: Long?,
)

@Serializable
data class CreateTargetFolderRequest(
    val rootDirId: Long,
    val name: String,
)

@Serializable
data class PhotoDto(
    val id: Long,
    val sourceFolderId: Long,
    val status: String,
    val destinationPath: String?,
    val originalPath: String?,
    val trashedAt: Long?,
    val processedAt: Long?,
)

@Serializable
data class SettingsDto(
    val showSkipped: Boolean,
    val downscaleHighRes: Boolean,
    val recursiveScanDefault: Boolean,
)

@Serializable
data class UpdateSettingsRequest(
    val showSkipped: Boolean? = null,
    val downscaleHighRes: Boolean? = null,
    val recursiveScanDefault: Boolean? = null,
)

@Serializable
data class MoveResultDto(
    val success: Boolean,
    val errorMessage: String? = null,
)