package com.vidadomu.nexus.agent.core.registry

import kotlinx.serialization.Serializable

/** Identidad exacta de un modelo en Nexus: el mismo id puede existir en varios proveedores. */
@Serializable
data class ModelRef(val providerId: String, val modelId: String) {
    override fun toString(): String = "$providerId:$modelId"
}

/**
 * Vista del agente sobre un modelo del registro de Nexus. Se rellena SOLO con datos que Nexus
 * conoce (NexusModel); nada se deduce del entrenamiento del LLM.
 */
@Serializable
data class ModelSummary(
    val ref: ModelRef,
    val name: String,
    val organization: String? = null,
    val family: String? = null,
    val modalities: Set<String>,
    val capabilities: Set<String> = emptySet(),
    val inputMode: String? = null,
    val purpose: String? = null,
    val strength: String? = null,
    val releaseMonth: String? = null,
    val isFlagship: Boolean = false,
    val supportsImageInput: Boolean = false,
    val supportsFirstFrame: Boolean = false,
    val supportsLastFrame: Boolean = false,
    val supportsReferenceImages: Boolean = false,
    val supportsVideoInput: Boolean = false,
    val supportsAudio: Boolean = false,
    val maxImages: Int = 0,
    val mediaRequired: Boolean = false,
    /** Pistas del catálogo; NO son verdad: el schema manda (p. ej. duraciones desfasadas). */
    val catalogDurations: List<String> = emptyList(),
    val catalogResolutions: List<String> = emptyList(),
    val basePriceUsd: Double? = null,
    val priceUnit: String? = null,
    val priceUnitVerified: Boolean = false,
)

@Serializable
data class ParamSpec(
    val key: String,
    val displayName: String = key,
    /** Tipo de formulario de Nexus: ENUM, DURATION, RESOLUTION, ASPECT_RATIO, BOOLEAN, SEED, INTEGER, FLOAT, TEXT, LONG_TEXT. */
    val uiType: String,
    /** Tipo nativo del schema del proveedor: string, integer, number, boolean (si se conoce). */
    val nativeType: String? = null,
    val options: List<String> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val default: String? = null,
    val required: Boolean = false,
    val advanced: Boolean = false,
    val description: String? = null,
    /** Rangos que dependen de otro campo (x-depends-on del schema), p. ej. short según template. */
    val conditions: List<ParamCondition> = emptyList(),
)

/** Cuando [field] vale uno de [values], este parámetro debe estar en [min]..[max]. */
@Serializable
data class ParamCondition(
    val field: String,
    val values: List<String>,
    val min: Double? = null,
    val max: Double? = null,
    val description: String? = null,
)

/**
 * Campo que el proveedor exige pero que se deduce de otro (no lo elige el usuario ni el agente):
 * p. ej. resolution a partir del sufijo de template. [mapping] va de valor de [from] a valor enviado.
 */
@Serializable
data class DerivedParam(
    val key: String,
    val from: String,
    val mapping: Map<String, String>,
    val note: String,
) {
    fun valueFor(source: String?): String? = source?.let { mapping[it] }
}

@Serializable
data class MediaSpec(
    val inputMode: String? = null,
    val firstFrameField: String? = null,
    val lastFrameField: String? = null,
    val inputImageField: String? = null,
    val imagesArrayField: String? = null,
    val videoField: String? = null,
    val maxImages: Int = 0,
    val maxVideos: Int = 0,
    val mediaRequired: Boolean = false,
)

/** Schema REAL ya configurado por Nexus (NativeModelSchema para Atlas, contratos para Crun/Runway). */
@Serializable
data class TextLimit(
    val unit: String,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val source: String? = null,
    val description: String? = null,
)

@Serializable
data class ModelSchema(
    val ref: ModelRef,
    val parameters: List<ParamSpec>,
    val media: MediaSpec,
    val promptRequired: Boolean = true,
    /** Campos obligatorios del proveedor que el formulario de Nexus aún no sabe rellenar. */
    val unsupportedRequired: List<String> = emptyList(),
    /** Origen del schema, p. ej. la schemaUrl oficial. */
    val source: String? = null,
    val textLimits: Map<String, TextLimit> = emptyMap(),
    /** false si el schema no tiene campo prompt: no se envía texto libre a la API. */
    val promptAccepted: Boolean = true,
    val derived: List<DerivedParam> = emptyList(),
) {
    fun param(key: String): ParamSpec? = parameters.firstOrNull { it.key == key }
    fun derivedParam(key: String): DerivedParam? = derived.firstOrNull { it.key == key }
}

interface ModelRegistry {
    /** Modelos visibles y ejecutables en Nexus ahora mismo. */
    suspend fun list(): List<ModelSummary>
    suspend fun get(ref: ModelRef): ModelSummary? = list().firstOrNull { it.ref == ref }
    /** Schema real; lanza si Nexus no puede cargarlo (sin red, schema no oficial…). */
    suspend fun schema(ref: ModelRef): ModelSchema
}
