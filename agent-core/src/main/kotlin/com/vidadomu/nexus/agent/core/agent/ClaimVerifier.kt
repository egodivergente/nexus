package com.vidadomu.nexus.agent.core.agent

import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderBook
import com.vidadomu.nexus.agent.core.orders.OrderStatus

/**
 * Comprueba que lo que el agente le dice al usuario coincide con el estado verificado de los
 * encargos. «Enviado», «generando» o «listo/terminado» sin un encargo que lo respalde es una
 * contradicción: se devuelve el estado real para que el agente se corrija o la UI lo muestre.
 */
object ClaimVerifier {
    enum class Claim { SUBMITTED, COMPLETED }

    private val SUBMITTED = Regex("""\b(enviad[oa]s?|lo he enviado|he lanzado|lanzad[oa]s?|en marcha|se est[aá] generando|est[aá] generando|generándose|procesándose|en proceso|en cola)\b""", RegexOption.IGNORE_CASE)
    private val COMPLETED = Regex("""\b(ya est[aá] (?:listo|hecho|terminado|generado)|terminad[oa]s?|completad[oa]s?|ya tienes (?:el|la|tu|los|las)|aqu[ií] tienes (?:el|la|tu) (?:resultado|v[ií]deo|imagen)|resultado listo|upscale listo|ha finalizado)\b""", RegexOption.IGNORE_CASE)
    private val NEGATION = Regex("""\b(no|sin|todav[ií]a no|aún no|aun no|nada|ni|cuando|si|hasta que|antes de|en cuanto|al pulsar|para que)\b[^.;:\n]{0,40}$""", RegexOption.IGNORE_CASE)

    data class Contradiction(val claim: Claim, val sentence: String, val truth: String)

    fun claims(text: String): List<Pair<Claim, String>> = text.split(Regex("(?<=[.!?\\n])\\s+")).flatMap { sentence ->
        listOfNotNull(
            SUBMITTED.find(sentence)?.takeIf { !negated(sentence, it.range.first) }?.let { Claim.SUBMITTED to sentence.trim() },
            COMPLETED.find(sentence)?.takeIf { !negated(sentence, it.range.first) }?.let { Claim.COMPLETED to sentence.trim() },
        )
    }

    private fun negated(sentence: String, at: Int): Boolean = NEGATION.containsMatchIn(sentence.substring(0, at))

    /**
     * [orders]: encargos de la conversación. Una afirmación se acepta si ALGÚN encargo la respalda;
     * si no, se devuelve el estado real de los encargos relevantes.
     */
    fun verify(text: String, orders: List<Order>): List<Contradiction> {
        if (orders.isEmpty()) return emptyList()
        val live = orders.filter { it.status != OrderStatus.DISCARDED }
        val sent = live.any { it.status in setOf(OrderStatus.PROCESSING, OrderStatus.COMPLETED) }
        val done = live.any { it.status == OrderStatus.COMPLETED }
        val truth = live.sortedByDescending { it.updatedAt }.take(3).joinToString("; ") { "${it.modelName}: ${OrderBook.describeStatus(it)}" }
            .ifBlank { "No hay encargos activos." }
        return claims(text).mapNotNull { (claim, sentence) ->
            val ok = when (claim) { Claim.SUBMITTED -> sent; Claim.COMPLETED -> done }
            if (ok) null else Contradiction(claim, sentence, truth)
        }.distinctBy { it.claim }
    }
}
