package com.vidadomu.nexus.agent.core.storage

/** Where a private file lives. Only attachment and export copies are ever removed automatically. */
enum class StorageArea(val label: String) {
    DRIVE_COPY("Copia para Google Drive"),
    ATTACHMENTS("Adjuntos e importaciones"),
    EXPORTS("Ediciones y exportaciones"),
    DEVICE_SYNC("Sincronización entre dispositivos"),
    DRAFTS("Borradores del Lienzo"),
    CACHE("Caché temporal"),
    OTHER("Otros"),
}

data class StoredFile(val path: String, val length: Long, val sha256: String, val area: StorageArea)

/** [file] is an exact copy of [keep]: references to it move to [keep] and then it is deleted. */
data class Removal(val file: StoredFile, val keep: StoredFile)

data class DuplicatePlan(val removals: List<Removal>, val groups: Int, val protectedCopies: Int, val protectedBytes: Long) {
    val bytes: Long get() = removals.sumOf { it.file.length }
    val replacements: Map<String, String> get() = removals.associate { it.file.path to it.keep.path }
}

/**
 * Chooses which exact copies can go. Rules, in order:
 * - Bytes are compared by SHA-256 and length, and only between files with the same extension.
 * - One copy always stays. The Drive copy is preferred, because the library and Drive sync already point to it.
 * - A copy mentioned anywhere Nexus cannot rewrite safely ([fixedNames]: preferences, history database, open chats,
 *   drafts, background jobs, peer sync state) is never removed.
 * - Only attachment and export copies with plain file names are removed; Drive copies, drafts and caches are not.
 */
object DuplicatePlanner {
    private val REMOVABLE = setOf(StorageArea.ATTACHMENTS, StorageArea.EXPORTS)

    fun extension(path: String): String = path.substringAfterLast('/').substringAfterLast('.', "").lowercase().let { if (it == "jpeg") "jpg" else it }
    fun name(path: String): String = path.substringAfterLast('/')

    fun plan(files: List<StoredFile>, fixedNames: Set<String>, referenced: Set<String> = emptySet()): DuplicatePlan {
        val removals = mutableListOf<Removal>(); var groups = 0; var protectedCopies = 0; var protectedBytes = 0L
        files.filter { it.length > 0 && ContentHashes.SHA.matches(it.sha256) }
            .groupBy { Triple(it.sha256, it.length, extension(it.path)) }.values
            .filter { it.size > 1 }
            .forEach { group ->
                groups++
                fun removable(file: StoredFile) = file.area in REMOVABLE && MediaReferences.SAFE_NAME.matches(name(file.path)) && name(file.path) !in fixedNames
                val keep = group.sortedWith(compareBy<StoredFile>(
                    { if (it.area == StorageArea.DRIVE_COPY) 0 else 1 },
                    { if (removable(it)) 1 else 0 },
                    { if (it.path in referenced) 0 else 1 },
                    { it.path })).first()
                group.filter { it !== keep }.forEach { file ->
                    if (removable(file)) removals += Removal(file, keep)
                    else { protectedCopies++; protectedBytes += file.length }
                }
            }
        return DuplicatePlan(removals.sortedByDescending { it.file.length }, groups, protectedCopies, protectedBytes)
    }
}
