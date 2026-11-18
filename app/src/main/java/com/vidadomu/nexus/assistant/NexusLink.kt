package com.vidadomu.nexus.assistant

import android.content.Context
import android.os.Environment
import android.util.Log
import com.axel.link.FileLinkStore
import com.axel.link.LinkClient
import com.axel.link.LinkConfig
import com.axel.link.LinkState
import java.io.File

/**
 * El único cliente de AXEL Commander de Nexus. La identidad de cliente vive en un archivo privado
 * (`files/axel-link.json`), sobrevive a reinicios y actualizaciones, y la usa el agente a través de
 * las herramientas `remote_*`. Es el mismo cliente (`com.axel.link`) que usan AXEL Hub y Nexus Desktop.
 */
object NexusLink {
    private const val FILE = "axel-link.json"
    private const val TAG = "NexusLink"
    @Volatile private var shared: LinkClient? = null

    fun client(context: Context): LinkClient {
        val app = context.applicationContext
        val client = shared ?: synchronized(this) {
            shared ?: LinkClient(FileLinkStore(File(app.filesDir, FILE))).also { shared = it }
        }
        if (client.state is LinkState.Unconfigured && importPending(app)) client.reload()
        return client
    }

    /**
     * Emparejamiento sin cables: si hay un `Documents/ALEXIA/Nexus/axel-link.json` válido, pasa al
     * almacén privado y se borra de la carpeta compartida. Un archivo no válido se deja donde está.
     */
    private fun importPending(app: Context): Boolean = runCatching {
        val pending = File(Environment.getExternalStorageDirectory(), "Documents/ALEXIA/Nexus/$FILE")
        if (!pending.isFile) return@runCatching false
        val config = LinkConfig.parse(pending.readText(Charsets.UTF_8))
        FileLinkStore(File(app.filesDir, FILE)).write(config.toJson())
        if (!pending.delete()) Log.w(TAG, "emparejamiento importado, pero no se pudo borrar el archivo compartido")
        Log.i(TAG, "emparejamiento importado: ${config.clientId}, ${config.routes.size} rutas")
        true
    }.getOrElse { error ->
        Log.w(TAG, "emparejamiento pendiente no válido: ${error.javaClass.simpleName}")
        false
    }
}
