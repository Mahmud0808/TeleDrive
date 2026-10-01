package com.drdisagree.teledrive.presentation.preview

internal class ArchiveNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    var sizeBytes: Long = 0,
    var compressedBytes: Long = 0,
    val children: LinkedHashMap<String, ArchiveNode> = LinkedHashMap()
)
