package com.vidadomu.nexus.ui.output

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal const val ALEXIA_PACKAGE = "com.axel.library"

/** Abre Alexia si está instalada. Devuelve false en vez de fallar en silencio. */
internal fun launchAlexia(context: Context): Boolean = try {
    val intent = context.packageManager.getLaunchIntentForPackage(ALEXIA_PACKAGE) ?: Intent().apply {
        component = ComponentName(ALEXIA_PACKAGE, "$ALEXIA_PACKAGE.MainActivity")
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: Exception) {
    false
}

/** Texto del aviso persistente cuando Alexia no responde; nunca promete una acción que no se ofrece. */
internal fun alexiaUnavailableMessage(hasLocalCopy: Boolean): String =
    if (hasLocalCopy) "No se pudo abrir la app externa. El resultado sigue guardado en Nexus; puedes abrir esta copia con otra app."
    else "No se pudo abrir la app externa. El resultado sigue en Nexus; para guardar una copia usa «Descargar archivo»."

internal const val OPEN_WITH_LABEL = "Abrir con otra app"

/** Copia que se puede ofrecer a otra app: archivo local con ruta absoluta o documento content://. */
internal sealed interface LocalCopyTarget {
    data class LocalFile(val absolutePath: String) : LocalCopyTarget
    data class Content(val uri: String) : LocalCopyTarget
}

/** Nunca acepta enlaces remotos (http/https…): abrirlos podría descargar el archivo sin que el usuario lo pida. */
internal fun localCopyTarget(localUri: String): LocalCopyTarget? {
    val value = localUri.trim()
    if (value.isEmpty()) return null
    val scheme = value.substringBefore(':', "").lowercase()
    return when {
        scheme.isEmpty() && value.startsWith("/") -> LocalCopyTarget.LocalFile(value)
        scheme == "file" -> runCatching { java.net.URI(value).path }.getOrNull()
            ?.takeIf { it.startsWith("/") && it.length > 1 }?.let { LocalCopyTarget.LocalFile(it) }
        scheme == "content" -> value.takeIf { it.startsWith("content://") && it.removePrefix("content://").substringBefore('/').isNotBlank() }
            ?.let { LocalCopyTarget.Content(it) }
        else -> null
    }
}

/** Solo lectura de la copia local mediante selector del sistema: no descarga, envía ni borra nada. */
internal fun openLocalCopyWithAnotherApp(context: Context, localUri: String, mimeType: String): Boolean = try {
    val uri = when (val target = localCopyTarget(localUri)) {
        is LocalCopyTarget.LocalFile -> FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(target.absolutePath))
        is LocalCopyTarget.Content -> Uri.parse(target.uri)
        null -> null
    }
    if (uri == null) false else {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = android.content.ClipData.newRawUri("Resultado", uri)
        context.startActivity(Intent.createChooser(view, OPEN_WITH_LABEL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }
} catch (_: Exception) {
    false
}
