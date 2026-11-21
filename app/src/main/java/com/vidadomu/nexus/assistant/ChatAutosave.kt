package com.vidadomu.nexus.assistant

/**
 * Cuándo escribe el guardado automático del chat. Todo cambio se escribe en cuanto llega, como antes, salvo el que
 * solo mueve el porcentaje de un trabajo en curso: cada tick de progreso reescribía chat-v1.json y la conversación
 * enteros (dos archivos de cientos de KB por tick). Ese espera como mucho [intervalMs] desde la última escritura y
 * se escribe igualmente al final, con el valor más reciente. Un mensaje, un aviso, un plan o un estado nunca esperan.
 */
internal class ChatAutosave(private val intervalMs: Long = PROGRESS_INTERVAL_MS) {
    @Volatile var saved: SavedAssistantChat? = null; private set
    @Volatile private var savedAt = 0L

    /** Milisegundos que puede esperar [next]; 0 = escribir ya. */
    fun waitMs(next: SavedAssistantChat, now: Long): Long {
        val last = saved ?: return 0
        if (last == next || !progressOnly(last, next)) return 0
        return (savedAt + intervalMs - now).coerceAtLeast(0)
    }

    fun written(snapshot: SavedAssistantChat, now: Long) { saved = snapshot; savedAt = now }

    companion object {
        const val PROGRESS_INTERVAL_MS = 3_000L

        /** Iguales salvo el progreso del trabajo (y la hora de cambio que el libro de encargos le pone). */
        fun progressOnly(a: SavedAssistantChat, b: SavedAssistantChat): Boolean = withoutProgress(a) == withoutProgress(b)

        private fun withoutProgress(c: SavedAssistantChat) = c.copy(
            plans = c.plans.mapValues { it.value.copy(jobProgress = null) },
            orders = c.orders?.let { book -> book.copy(orders = book.orders.map { it.copy(progress = null, updatedAt = 0) }) })
    }
}

/**
 * El índice de conversaciones es una caché (cada entrada se valida con la fecha y el tamaño de su archivo): reescribir
 * sus 4 MB porque una conversación cambió de texto no aporta nada que el arranque no pueda rehacer leyendo esa sola
 * conversación. Se escribe cuando entra o sale una conversación, cuando cambia un título o un proyecto, la primera vez
 * en cada proceso, o cuando ya son [MAX_UNSAVED] las entradas que solo están en memoria (así el arranque nunca tiene
 * que leer muchas).
 */
internal object ConversationIndexPolicy {
    const val MAX_UNSAVED = 5
    fun persist(firstInProcess: Boolean, structural: Boolean, unsaved: Int): Boolean =
        structural || (unsaved > 0 && (firstInProcess || unsaved >= MAX_UNSAVED))
}
