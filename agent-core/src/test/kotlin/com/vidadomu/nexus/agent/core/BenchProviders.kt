package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.AntigravityProvider
import com.vidadomu.nexus.agent.core.llm.AwsCredentials
import com.vidadomu.nexus.agent.core.llm.BedrockDirectProvider
import com.vidadomu.nexus.agent.core.llm.LlmProvider
import java.io.File

/**
 * Cerebros del banco. Los del puente AXEL van por HTTP al teléfono; Bedrock se firma aquí con las
 * credenciales del propio equipo (`~/.aws/credentials`), que nunca se copian ni se imprimen.
 */
internal object BenchProviders {
    fun bridge(endpoint: String, engine: AntigravityProvider.BridgeEngine, conversation: String, effort: String?): LlmProvider =
        AntigravityProvider(endpoint, sessionId = { conversation }, engine = engine, reasoningEffort = effort)

    fun bedrock(): LlmProvider {
        val creds = awsCredentials() ?: error("Bedrock necesita credenciales en ~/.aws/credentials o en AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY.")
        return BedrockDirectProvider(region = System.getenv("AWS_REGION") ?: awsConfigValue("region") ?: "us-east-1", credentials = { creds })
    }

    private fun awsCredentials(): AwsCredentials? {
        System.getenv("AWS_ACCESS_KEY_ID")?.let { id ->
            System.getenv("AWS_SECRET_ACCESS_KEY")?.let { return AwsCredentials(id, it, System.getenv("AWS_SESSION_TOKEN")) }
        }
        val file = File(System.getProperty("user.home"), ".aws/credentials")
        val profile = System.getenv("AWS_PROFILE") ?: "default"
        // Un equipo con un único perfil con otro nombre (p. ej. «vidaltb») es lo normal: se usa ese.
        val values = iniSection(file, profile) ?: sections(file).singleOrNull()?.let { iniSection(file, it) } ?: return null
        val id = values["aws_access_key_id"] ?: return null
        val secret = values["aws_secret_access_key"] ?: return null
        return AwsCredentials(id, secret, values["aws_session_token"])
    }

    private fun awsConfigValue(key: String): String? {
        val profile = System.getenv("AWS_PROFILE") ?: "default"
        val file = File(System.getProperty("user.home"), ".aws/config")
        return iniSection(file, "profile $profile")?.get(key) ?: iniSection(file, profile)?.get(key)
    }

    /** Nombres de sección del archivo, en orden. */
    private fun sections(file: File): List<String> =
        if (!file.isFile) emptyList() else file.readLines().map { it.substringBefore('#').substringBefore(';').trim() }
            .filter { it.startsWith("[") && it.endsWith("]") }.map { it.substring(1, it.length - 1).trim() }

    /** Lector mínimo del formato INI de AWS: solo la sección pedida, sin dependencias. */
    private fun iniSection(file: File, section: String): Map<String, String>? {
        if (!file.isFile) return null
        var inside = false
        val out = linkedMapOf<String, String>()
        for (raw in file.readLines()) {
            val line = raw.substringBefore('#').substringBefore(';').trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) { inside = line.substring(1, line.length - 1).trim() == section; continue }
            if (!inside) continue
            val i = line.indexOf('='); if (i <= 0) continue
            out[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        return out.takeIf { inside || it.isNotEmpty() }
    }
}
