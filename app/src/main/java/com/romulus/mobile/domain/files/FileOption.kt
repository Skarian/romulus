package com.romulus.mobile.domain.files

data class FileOption(
    val partIndex: Int,
    val partName: String,
    val magnetUrl: String,
    val torrentFileId: Int,
    val originalName: String,
    val sizeBytes: Long,
    val defaultDisplayName: String
)
