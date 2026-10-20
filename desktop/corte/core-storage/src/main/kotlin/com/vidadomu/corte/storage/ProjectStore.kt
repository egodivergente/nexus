package com.vidadomu.corte.storage

import com.vidadomu.corte.model.Project
import java.io.File

/**
 * Project model storage. Lives in app-private files; caches (thumbnails, peaks, proxies) live
 * elsewhere and can be deleted at any time without affecting projects.
 */
class ProjectStore(private val root: File) {
    data class Summary(val id: String, val name: String, val modifiedAtMs: Long, val error: String? = null)

    data class Loaded(val project: Project, val recoveredFromBackup: Boolean, val primaryError: String?)

    private fun dir(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_\\-]+"))) { "invalid project id" }
        return File(root, id)
    }

    fun fileOf(id: String) = File(dir(id), "project.json")

    fun save(project: Project) {
        val bytes = ProjectCodec.encode(project)
        AtomicFile.write(fileOf(project.id), bytes) { ProjectCodec.decode(it) }
    }

    fun load(id: String): Loaded {
        val f = fileOf(id)
        val primary = runCatching { ProjectCodec.decode(f.readBytes()) }
        primary.getOrNull()?.let { return Loaded(it, false, null) }
        val err = primary.exceptionOrNull()?.message ?: "error desconocido"
        val bak = AtomicFile.backupOf(f)
        if (bak.exists()) {
            val b = runCatching { ProjectCodec.decode(bak.readBytes()) }
            b.getOrNull()?.let { return Loaded(it, true, err) }
        }
        throw ProjectFormatException("No se pudo abrir el proyecto ni su copia de seguridad: $err")
    }

    fun list(): List<Summary> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { d ->
            val f = File(d, "project.json")
            if (!f.exists() && !AtomicFile.backupOf(f).exists()) return@mapNotNull null
            try {
                val l = load(d.name)
                Summary(l.project.id, l.project.name, l.project.modifiedAtMs, if (l.recoveredFromBackup) "Recuperado de copia" else null)
            } catch (e: ProjectFormatException) {
                Summary(d.name, d.name, f.lastModified(), e.message)
            }
        }.sortedByDescending { it.modifiedAtMs }
    }

    fun delete(id: String): Boolean = dir(id).deleteRecursively()
}
