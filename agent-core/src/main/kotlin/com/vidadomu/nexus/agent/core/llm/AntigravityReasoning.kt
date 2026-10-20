package com.vidadomu.nexus.agent.core.llm

/** Confirmed by AGY models and the S25 bridge /health on 2026-10-20. Effort is encoded in the model ID. */
object AntigravityReasoning {
    const val DEFAULT_MODEL = "gemini-4-argon-medium"
    val efforts = listOf("low", "medium", "high")
    fun effort(model: String): String? = when (model) {
        "gemini-4-argon" -> "medium"
        else -> efforts.firstOrNull { model == "gemini-4-argon-$it" }
    }
    fun model(effort: String): String {
        require(effort in efforts) { "Argon admite bajo, medio o alto." }
        return "gemini-4-argon-$effort"
    }
}
