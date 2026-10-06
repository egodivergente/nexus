package com.vidadomu.nexus.agent.core.log

/**
 * Logging seguro: nunca se escriben secretos completos. Se aplica a todo lo que el agente registra
 * (errores de proveedor, respuestas de herramientas, cabeceras).
 */
object Redactor {
    private val patterns = listOf(
        Regex("(AKIA|ASIA)[A-Z0-9]{16}"),                                         // access key id
        Regex("(?i)(aws_secret_access_key|secret_access_key|secretKey)\\s*[=:]\\s*\\S+"),
        Regex("(?i)(aws_session_token|x-amz-security-token)\\s*[=:]\\s*\\S+"),
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[A-Za-z0-9._\\-]+"),
        Regex("(?i)(x-api-key\\s*[:=]\\s*)\\S+"),
        Regex("(?i)(api[_-]?key\"?\\s*[:=]\\s*\"?)[A-Za-z0-9._\\-]{8,}"),
        Regex("(?<![A-Za-z0-9/+])[A-Za-z0-9/+]{40}(?![A-Za-z0-9/+=])"),           // forma de secret key de AWS
    )

    fun redact(text: String): String = patterns.fold(text) { acc, rx ->
        rx.replace(acc) { m ->
            val g1 = m.groups[1]?.value
            if (g1 != null && m.value.startsWith(g1) && g1.length < m.value.length && !g1.matches(Regex("AKIA|ASIA"))) "$g1«oculto»"
            else mask(m.value)
        }
    }

    /** Deja ver 4 caracteres para poder distinguir claves sin exponerlas. */
    fun mask(secret: String): String = if (secret.length <= 8) "«oculto»" else secret.take(4) + "…«oculto»"
}

fun interface AgentLogger {
    fun log(level: String, message: String)
}

class RedactingLogger(private val sink: AgentLogger) : AgentLogger {
    override fun log(level: String, message: String) = sink.log(level, Redactor.redact(message))
}
