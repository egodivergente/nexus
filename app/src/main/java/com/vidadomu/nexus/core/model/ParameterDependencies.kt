package com.vidadomu.nexus.core.model

/**
 * Which optional fields apply together, taken only from the model's own published documentation.
 * A field that does not apply is hidden and left out of the request; what the user typed is kept
 * in the draft and comes back if the controlling option changes again. Models without a rule are untouched.
 */
object ParameterDependencies {
    private const val TENCENT_IMAGE = "https://static.atlascloud.ai/model/readme/tencent-image-upscaler.md"

    class Dependency(val key: String, val on: String, val values: Set<String>, val source: String)

    private class ModelRules(val model: String, val hidden: Map<String, String>, val dependencies: List<Dependency>)

    private val rules = listOf(
        ModelRules(
            model = "tencent/image/upscaler",
            // Present in the JSON schema but absent from the documented parameter table.
            hidden = mapOf("output_dir" to TENCENT_IMAGE),
            dependencies = listOf(
                Dependency("percent", "mode", setOf("percent"), TENCENT_IMAGE),
                Dependency("width", "mode", setOf("aspect", "fixed"), TENCENT_IMAGE),
                Dependency("height", "mode", setOf("aspect", "fixed"), TENCENT_IMAGE),
                Dependency("long_side", "mode", setOf("aspect", "fixed"), TENCENT_IMAGE),
                Dependency("short_side", "mode", setOf("aspect", "fixed"), TENCENT_IMAGE),
                // «applies to lossy formats»: PNG and BMP ignore it.
                Dependency("encode_quality", "encode_format", setOf("jpeg", "webp"), TENCENT_IMAGE)
            )
        )
    )

    private fun rulesFor(model: NexusModel): ModelRules? = rules.firstOrNull { rule ->
        listOfNotNull(model.providerModelId, model.id).any { it.equals(rule.model, true) || it.endsWith("/" + rule.model, true) }
    }

    private fun effectiveValue(model: NexusModel, key: String, values: Map<String, String>): String =
        values[key]?.takeIf { it.isNotBlank() } ?: model.parameters.firstOrNull { it.key == key }?.defaultValue.orEmpty()

    fun isApplicable(model: NexusModel, key: String, values: Map<String, String>): Boolean {
        val rule = rulesFor(model) ?: return true
        if (key in rule.hidden) return false
        val dependency = rule.dependencies.firstOrNull { it.key == key } ?: return true
        return effectiveValue(model, dependency.on, values).lowercase() in dependency.values
    }

    /** Parameters worth showing for the current values, in contract order. */
    fun visible(model: NexusModel, parameters: List<ModelParameter>, values: Map<String, String>): List<ModelParameter> =
        parameters.filter { isApplicable(model, it.key, values) }

    /** Values that may reach the provider: inapplicable fields never travel with the request. */
    fun effective(model: NexusModel, values: Map<String, String>): Map<String, String> =
        if (rulesFor(model) == null) values else values.filterKeys { isApplicable(model, it, values) }

    fun source(model: NexusModel, key: String): String? = rulesFor(model)?.let { rule ->
        rule.dependencies.firstOrNull { it.key == key }?.source ?: rule.hidden[key]
    }
}
