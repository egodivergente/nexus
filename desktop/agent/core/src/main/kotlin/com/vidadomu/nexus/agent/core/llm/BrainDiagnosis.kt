package com.vidadomu.nexus.agent.core.llm

enum class BrainProblemKind { NOT_ACTIVATED, MODEL_NOT_FOUND, AUTH, NO_CREDIT, RATE_LIMIT, NETWORK, OTHER }

/**
 * Diagnóstico de un fallo del proveedor, listo para mostrar: qué pasa, qué hacer y, si lo hay, el enlace directo que lo
 * resuelve. Nunca incluye claves ni cuerpos de petición.
 */
data class BrainProblem(
    val kind: BrainProblemKind,
    val title: String,
    val detail: String,
    val steps: List<String> = emptyList(),
    val actionLabel: String? = null,
    val actionUrl: String? = null,
)

/** Estado de activación de un modelo en la cuenta, según una petición mínima real. */
enum class ModelState { ACTIVE, NOT_ACTIVATED, NOT_FOUND, UNKNOWN }

object BrainDiagnosis {
    private val DATE_SUFFIX = Regex("-\\d{6}$")
    private val BYTEPLUS_HOST = Regex("^https://ark\\.([a-z0-9-]+)\\.bytepluses\\.com(/|$)")

    fun isBytePlus(profile: BrainProfile) = BYTEPLUS_HOST.containsMatchIn(profile.baseUrl.trim())

    /** `ark.ap-southeast.bytepluses.com` → `ap-southeast-1` (región de la consola). */
    fun bytePlusRegion(baseUrl: String): String? = BYTEPLUS_HOST.find(baseUrl.trim())?.groupValues?.get(1)?.let { "$it-1" }

    /** Nombre base del modelo (sin el sufijo de fecha `-260628`), el que usan la ficha y ActivateModels. */
    fun foundationName(modelId: String) = modelId.trim().replace(DATE_SUFFIX, "")

    /** Consola de activación de modelos (Activation Management) de la región del proveedor. */
    fun activationUrl(profile: BrainProfile): String? = bytePlusRegion(profile.baseUrl)?.let { "https://console.byteplus.com/ark/region:$it/openManagement" }

    /** Ficha del modelo en la consola (precio, capacidades y activación). */
    fun modelPageUrl(profile: BrainProfile, modelId: String = profile.modelId): String? =
        bytePlusRegion(profile.baseUrl)?.let { "https://console.byteplus.com/ark/region:$it/model/detail?Id=${foundationName(modelId)}" }

    fun of(profile: BrainProfile, error: LlmException): BrainProblem {
        val who = profile.name
        val model = profile.modelId
        return when {
            error.code == "ModelNotOpen" -> BrainProblem(
                BrainProblemKind.NOT_ACTIVATED, "Modelo sin activar",
                "Tu clave es válida, pero tu cuenta de $who no ha activado «$model». Hasta activarlo, cualquier petición responde ModelNotOpen.",
                if (isBytePlus(profile)) listOf(
                    "Abre la consola de ModelArk con la cuenta dueña de la clave.",
                    "En «Activation Management» busca «${foundationName(model)}» y pulsa Activar (acepta las condiciones que muestre).",
                    "O por CLI: bp ark ActivateModels --FoundationModelNames '[\"${foundationName(model)}\"]'",
                    "Vuelve aquí y pulsa Probar conexión.",
                ) else listOf("Activa el modelo en el panel de $who y vuelve a probar."),
                if (isBytePlus(profile)) "Activar modelo" else null, activationUrl(profile),
            )
            error.code?.startsWith("InvalidEndpointOrModel") == true || error.kind == LlmException.Kind.MODEL_NOT_ALLOWED -> BrainProblem(
                BrainProblemKind.MODEL_NOT_FOUND, "El modelo no existe en esta región",
                "«$model» no figura como disponible en $who para esta clave (puede estar retirado, mal escrito o en otra región).",
                listOf("Pulsa «Descubrir modelos» y elige uno de la lista del proveedor."),
            )
            error.kind == LlmException.Kind.AUTH -> BrainProblem(
                BrainProblemKind.AUTH, "Clave rechazada",
                "$who no acepta esta clave. Las claves de ModelArk son de una sola región y de un proyecto: comprueba que sea de la región de la URL y que no sea el nombre de la clave, sino su valor.",
                listOf("Copia la clave completa desde la consola (solo se muestra al crearla).", "Comprueba la URL base y el método de autenticación (Bearer)."),
            )
            error.kind == LlmException.Kind.BUDGET -> BrainProblem(BrainProblemKind.NO_CREDIT, "Sin saldo o crédito", error.message.orEmpty(),
                listOf("Revisa el saldo y el método de pago de la cuenta de $who."))
            error.kind == LlmException.Kind.THROTTLED -> BrainProblem(BrainProblemKind.RATE_LIMIT, "Límite de peticiones", error.message.orEmpty(), listOf("Espera unos segundos y vuelve a probar."))
            error.kind == LlmException.Kind.NETWORK -> BrainProblem(BrainProblemKind.NETWORK, "Sin conexión con el proveedor", error.message.orEmpty(), listOf("Comprueba la URL base y la red."))
            else -> BrainProblem(BrainProblemKind.OTHER, "El proveedor devolvió un error", error.message.orEmpty())
        }
    }

    /** Candidatos de texto/razonamiento entre los modelos de ModelArk: fuera imagen, vídeo, 3D, voz y embeddings. */
    fun isTextModel(id: String) = !Regex("seedream|seedance|seededit|embedding|vision-pro-image|t2i|i2v|t2v|i2i|3d|tts|asr|speech|rerank|hyper3d|hitem3d", RegexOption.IGNORE_CASE).containsMatchIn(id)

    /** El ID más parecido de [known] (distancia de edición ≤ 4 o contenido), para corregir una errata sin adivinar. */
    fun closest(wrong: String, known: List<String>): String? {
        val w = wrong.trim().lowercase()
        if (w.isEmpty() || known.isEmpty()) return null
        return known.map { it to distance(w, it.lowercase()) }.filter { (id, d) -> d <= 4 || id.lowercase().contains(w) || w.contains(id.lowercase()) }.minByOrNull { it.second }?.first
    }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) { var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val tmp = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = tmp } }
        return d[b.length]
    }
}
