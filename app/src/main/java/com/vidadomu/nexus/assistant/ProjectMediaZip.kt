package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.assets.Asset
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Flat, unique entry names: no directories, traversal or overwriting similarly named photos. */
internal fun projectZipNames(assets: List<Asset>): List<String> {
    val used = mutableSetOf<String>()
    return assets.mapIndexed { index, asset ->
        val extension = when (asset.mimeType?.lowercase(Locale.ROOT)) {
            "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"
            "image/heic" -> "heic"; "image/heif" -> "heif"; "image/avif" -> "avif"
            "image/jpeg" -> "jpg"; else -> null
        }
        var base = asset.displayName.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_")
            .trim().trim('.').take(100).ifBlank { "imagen-${index + 1}" }
        if (extension != null && !base.endsWith(".$extension", true) &&
            !(extension == "jpg" && base.endsWith(".jpeg", true))) base += ".$extension"
        val dot = base.lastIndexOf('.').takeIf { it > 0 } ?: base.length
        var name = base
        var suffix = 2
        while (!used.add(name.lowercase(Locale.ROOT))) {
            name = base.take(dot) + " (${suffix++})" + base.substring(dot)
        }
        name
    }
}

internal suspend fun writeProjectMediaZip(
    assets: List<Asset>, destination: OutputStream, open: (Asset) -> InputStream,
    progress: (Int) -> Unit = {},
) {
    require(assets.isNotEmpty())
    val names = projectZipNames(assets)
    ZipOutputStream(destination.buffered()).use { zip ->
        val buffer = ByteArray(64 * 1024)
        assets.forEachIndexed { index, asset ->
            currentCoroutineContext().ensureActive()
            zip.putNextEntry(ZipEntry(names[index]))
            var size = 0L
            open(asset).use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    zip.write(buffer, 0, count)
                    size += count
                }
            }
            check(size > 0) { "La imagen está vacía: ${asset.displayName}" }
            zip.closeEntry()
            progress(index + 1)
        }
    }
}
