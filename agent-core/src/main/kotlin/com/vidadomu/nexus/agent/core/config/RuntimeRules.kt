package com.vidadomu.nexus.agent.core.config

import kotlinx.serialization.Serializable

enum class RuleType { BOOLEAN, INTEGER, DECIMAL, TEXT, CHOICE }
data class RuleDefinition(val key: String, val title: String, val section: String, val type: RuleType,
    val default: String, val minimum: Double? = null, val maximum: Double? = null,
    val choices: List<String> = emptyList(), val help: String = "", val scoped: Boolean = false) {
    fun validate(value: String): Boolean = when(type) {
        RuleType.BOOLEAN -> value in listOf("true", "false")
        RuleType.INTEGER, RuleType.DECIMAL -> value.toDoubleOrNull()?.let {
            it.isFinite() && (type != RuleType.INTEGER || value.toLongOrNull() != null) &&
                (minimum == null || it >= minimum) && (maximum == null || it <= maximum)
        } == true
        RuleType.CHOICE -> value in choices
        RuleType.TEXT -> true
    }
}

/** Local preferences only. Remote capabilities never become editable facts here. */
object RuntimeRules {
    val definitions = listOf(
        RuleDefinition("routing.enabled", "Elegir proveedor automáticamente", "Reglas y límites", RuleType.BOOLEAN, "true"),
        RuleDefinition("routing.creditUsd", "Coste real por crédito (USD)", "Reglas y límites", RuleType.DECIMAL, "0", 0.0,
            help="0 = conversión desconocida: no se compara ese proveedor con tarifas en USD. Usa el coste de tu compra.", scoped=true),
        RuleDefinition("ui.motion", "Animaciones", "Personalización", RuleType.BOOLEAN, "true"),
        RuleDefinition("ui.haptics", "Respuesta háptica", "Personalización", RuleType.BOOLEAN, "true"),
        RuleDefinition("flow.connectionTimeout", "Espera de conexión Flow (segundos)", "Reglas y límites", RuleType.INTEGER, "20", 0.0, help="Margen para comprobar el navegador del puente principal antes de usar el respaldo.", scoped=true),
        RuleDefinition("ui.background", "Fondo azul Nexus", "Personalización", RuleType.BOOLEAN, "true"),
        RuleDefinition("ui.backgroundOpacity", "Intensidad del fondo", "Personalización", RuleType.DECIMAL, "0.28", 0.0, 1.0),
        RuleDefinition("ui.photo", "Foto de fondo", "Personalización", RuleType.TEXT, "", help="Selecciona una imagen desde la vista Personalización."),
        RuleDefinition("ui.photoBlur", "Desenfoque de tu foto", "Personalización", RuleType.DECIMAL, "32", 0.0, 100.0),
        RuleDefinition("startup.enabled", "Continuar en… al abrir", "Proyectos y conversaciones", RuleType.BOOLEAN, "true"),
        RuleDefinition("startup.cold", "Mostrar siempre al arrancar el proceso", "Proyectos y conversaciones", RuleType.BOOLEAN, "false",
            help="Desactivado: al arrancar solo aparece si llevabas fuera el tiempo de ausencia de abajo (Android reinicia la app a menudo)."),
        RuleDefinition("startup.idleMinutes", "Ausencia para volver a elegir (min)", "Proyectos y conversaciones", RuleType.INTEGER, "240", 0.0, help="0 desactiva la selección por ausencia."),
        RuleDefinition("startup.recentCount", "Proyectos recientes", "Proyectos y conversaciones", RuleType.INTEGER, "4", 1.0),
        RuleDefinition("startup.order", "Orden de proyectos", "Proyectos y conversaciones", RuleType.CHOICE, "recientes", choices=listOf("recientes", "nombre")),
        RuleDefinition("share.choose", "Elegir destino al compartir", "Proyectos y conversaciones", RuleType.BOOLEAN, "true"),
        RuleDefinition("chat.tabsVisible", "Mostrar pestañas de conversaciones", "Proyectos y conversaciones", RuleType.BOOLEAN, "false", help="Ocultas por defecto. También puedes abrirlas desde el menú del compositor."),
        RuleDefinition("chat.attachmentCount", "Adjuntos por mensaje", "Agentes y memoria", RuleType.INTEGER, "0", 0.0, help="0 = sin límite.", scoped=true),
        RuleDefinition("chat.attachmentMB", "Avisar por tamaño de adjuntos (MB)", "Agentes y memoria", RuleType.INTEGER, "0", 0.0, help="0 desactiva el aviso", scoped=true),
        RuleDefinition("chat.importMB", "Tamaño máximo local por archivo (MB)", "Agentes y memoria", RuleType.INTEGER, "0", 0.0, help="0 = sin límite. Los límites del proveedor se comprueban al enviar.", scoped=true),
        RuleDefinition("audio.maxSeconds", "Duración de nota de voz (segundos)", "Agentes y memoria", RuleType.INTEGER, "0", 0.0, help="0 = sin límite."),
        RuleDefinition("audio.bitrate", "Bitrate AAC (bits/s)", "Agentes y memoria", RuleType.INTEGER, "96000", 8000.0),
        RuleDefinition("audio.sampleRate", "Muestreo AAC (Hz)", "Agentes y memoria", RuleType.CHOICE, "44100", choices=listOf("16000","22050","44100","48000")),
        RuleDefinition("catalog.columns", "Columnas de familias", "Catálogo y workflows", RuleType.INTEGER, "3", 1.0, 6.0),
        RuleDefinition("catalog.ageMonths", "Ocultar modelos anteriores a (meses)", "Catálogo y workflows", RuleType.INTEGER, "12", 0.0, help="Solo fechas verificadas · 0 muestra todos"),
        RuleDefinition("catalog.videoOrder", "Orden de workflows de vídeo", "Catálogo y workflows", RuleType.TEXT, "reference_to_video,image_to_video,start_end_frame_video,text_to_video", help="Identificadores separados por comas"),
        RuleDefinition("catalog.imageOrder", "Orden de workflows de imagen", "Catálogo y workflows", RuleType.TEXT, "reference_edit,compose_multi_reference,image_to_image,text_to_image"),
        RuleDefinition("generation.presetCount", "Presets por modelo", "Automatización", RuleType.INTEGER, "0", 0.0, help="Ajustes guardados que conserva cada modelo. 0 = sin límite."),
        RuleDefinition("generation.modelCount", "Modelos por Lanzadera", "Automatización", RuleType.INTEGER, "6", 0.0, help="Modelos que puedes comparar a la vez en la Lanzadera. 0 = sin límite."),
        RuleDefinition("generation.perModelCount", "Generaciones por modelo", "Automatización", RuleType.INTEGER, "20", 0.0, help="Resultados máximos de cada modelo en un mismo lanzamiento. 0 = sin límite."),
        RuleDefinition("generation.parallel", "Envíos simultáneos", "Automatización", RuleType.INTEGER, "2", 1.0, help="Trabajos que la Lanzadera envía a la vez; el resto espera turno."),
        RuleDefinition("generation.timeout", "Espera sin datos de red (segundos)", "Reglas y límites", RuleType.INTEGER, "120", 0.0, help="0 = sin límite. El servidor y el transporte pueden aplicar sus propios tiempos.", scoped=true),
        RuleDefinition("generation.retries", "Reintentos de consultas", "Reglas y límites", RuleType.INTEGER, "2", 0.0, help="Nunca repite un envío de resultado incierto", scoped=true),
        RuleDefinition("generation.costWarning", "Confirmar gasto desde (€)", "Reglas y límites", RuleType.DECIMAL, "0", 0.0, help="0 desactiva este aviso. Los costes desconocidos siempre requieren aceptación.", scoped=true),
        RuleDefinition("prompt.softWarning", "Aviso opcional de longitud", "Reglas y límites", RuleType.INTEGER, "0", 0.0, help="0 sin aviso. No representa un máximo remoto y no bloquea.", scoped=true),
        RuleDefinition("prompt.observation", "Observación manual de prompt", "Reglas y límites", RuleType.TEXT, "", help="Hipótesis sin verificar; nunca bloquea ni sustituye al contrato.", scoped=true),
        RuleDefinition("flow.drafts", "Instrucciones en un lote de Flow", "Flow", RuleType.INTEGER, "12", 1.0),
        RuleDefinition("flow.references", "Adjuntos locales de Flow", "Flow", RuleType.INTEGER, "0", 0.0, help="0 = sin límite; sujeto a la interfaz de Google"),
        RuleDefinition("flow.connectionTimeout", "Comprobación del puente (segundos)", "Flow", RuleType.INTEGER, "20", 0.0, help="Espera para elegir AXELPC antes de probar el respaldo. 0 sin corte local.", scoped=true),
    )
    fun definition(key: String) = definitions.first { it.key == key }
}

@Serializable data class RuleScope(val module: String? = null, val provider: String? = null,
    val model: String? = null, val workflow: String? = null) {
    val key get() = listOf(module.orEmpty(), provider.orEmpty(), model.orEmpty(), workflow.orEmpty()).joinToString("|")
    fun precedence(): List<String> = listOf(this, copy(workflow=null), copy(model=null,workflow=null),
        RuleScope(module=module), RuleScope()).map { it.key }.distinct()
}
@Serializable data class RuntimeConfiguration(val schemaVersion: Int = 1, val overrides: Map<String, Map<String,String>> = emptyMap()) {
    fun value(key: String, scope: RuleScope = RuleScope()): String {
        val rule = RuntimeRules.definition(key)
        return (if(rule.scoped) scope.precedence() else listOf(RuleScope().key))
            .firstNotNullOfOrNull { overrides[it]?.get(key)?.takeIf(rule::validate) } ?: rule.default
    }
    fun set(key: String, value: String?, scope: RuleScope = RuleScope()): RuntimeConfiguration {
        val rule = RuntimeRules.definition(key)
        require(value == null || rule.validate(value)) { "Valor no válido para ${rule.title}" }
        require(rule.scoped || scope == RuleScope()) { "Esta preferencia es global" }
        val values = overrides[scope.key].orEmpty().toMutableMap()
        if(value == null) values.remove(key) else values[key] = value
        return copy(overrides=overrides + (scope.key to values))
    }
    fun validated(): RuntimeConfiguration {
        require(schemaVersion == 1) { "Versión de ajustes no compatible" }
        overrides.forEach { (scope, values) ->
            require(scope.split('|').size == 4) { "Ámbito no válido" }
            values.forEach { (key,value) -> require(RuntimeRules.definition(key).validate(value) && (scope==RuleScope().key || RuntimeRules.definition(key).scoped)) { "Valor o ámbito no válido: $key" } }
        }
        return this
    }
}
