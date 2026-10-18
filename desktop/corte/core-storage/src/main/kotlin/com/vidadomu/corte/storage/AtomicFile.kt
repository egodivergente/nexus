package com.vidadomu.corte.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe file replacement: write to a temp file, fsync, read back and validate, keep the
 * previous good version as `.bak`, then atomically rename over the target. At every instant the
 * target is either the old complete file or the new complete file.
 */
object AtomicFile {
    fun backupOf(target: File) = File(target.parentFile, target.name + ".bak")
    private fun tmpOf(target: File) = File(target.parentFile, target.name + ".tmp")

    fun write(target: File, bytes: ByteArray, validate: (ByteArray) -> Unit = {}) {
        target.parentFile?.mkdirs()
        val tmp = tmpOf(target)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        val back = tmp.readBytes()
        if (!back.contentEquals(bytes)) {
            tmp.delete()
            throw java.io.IOException("Verificación fallida al guardar ${target.name}")
        }
        validate(back)
        if (target.exists()) {
            val previousOk = runCatching { validate(target.readBytes()) }.isSuccess
            if (previousOk) Files.copy(target.toPath(), backupOf(target).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
