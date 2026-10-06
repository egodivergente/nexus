package com.vidadomu.nexus.ui.create

import java.io.File

/** Operates only on Nexus-owned temporary copies; originals are never passed here. */
internal object PrivateMediaStorage {
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    fun prepare(directory: File, protectedPaths: Set<String>, now: Long = System.currentTimeMillis()): Long {
        val owned = directory.listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith("nexus_media_") && it.canonicalFile.parentFile == directory.canonicalFile
        }
        owned.filter { it.absolutePath !in protectedPaths && now - it.lastModified() > 7L * 24 * 60 * 60 * 1000 }
            .forEach { it.delete() }
        return directory.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }.also {
            require(it < MAX_BYTES) { "Los medios de borrador ocupan 2 GB. Quita los archivos que ya no necesites." }
        }
    }
}
