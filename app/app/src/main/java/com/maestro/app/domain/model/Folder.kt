package com.maestro.app.domain.model

data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val createdTimestamp: Long = System.currentTimeMillis()
)
