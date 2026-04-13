package com.maestro.app.data.model

import com.maestro.app.domain.model.Folder
import kotlinx.serialization.Serializable

@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val parentId: String = "",
    val ts: Long = 0L
) {
    fun toDomain(): Folder = Folder(
        id = id,
        name = name,
        parentId = parentId.ifBlank { null },
        createdTimestamp = ts
    )

    companion object {
        fun fromDomain(folder: Folder): FolderDto = FolderDto(
            id = folder.id,
            name = folder.name,
            parentId = folder.parentId ?: "",
            ts = folder.createdTimestamp
        )
    }
}
