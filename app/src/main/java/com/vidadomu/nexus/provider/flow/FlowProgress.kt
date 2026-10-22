package com.vidadomu.nexus.provider.flow

/** Human-readable observations; elapsed time is not an invented generation percentage. */
internal object FlowProgress {
    fun elapsed(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return if (safe < 60) "${safe} s" else "${safe / 60} min ${safe % 60} s"
    }

    fun stage(phase: String?, state: String): String = when (phase ?: state) {
        "accepted" -> "Pedido recibido por el puente"
        "recovering_session" -> "Comprobando y recuperando la sesión de Google"
        "attaching_references" -> "Cargando las imágenes en Flow"
        "preparing" -> "Preparando el envío a Flow"
        "clicking", "submitting" -> "Enviando a Google Flow"
        "submitted", "generating", "waiting_results" -> "Google Flow está generando; esperando archivos"
        "downloading", "completed" -> "Resultado disponible; copiando a Nexus"
        "needs_review", "uncertain" -> "Envío pendiente de confirmar; consulta el proyecto"
        else -> "Consultando el estado de Flow"
    }

    fun problem(code: String?): String? = when (code) {
        "recovering_session" -> "Recuperando la sesión de Google automáticamente…"
        "verification_required" -> "Google pide una verificación. Abre Ajustes → Conectar Flow → Abrir navegador y complétala; después consulta este pedido."
        "consent_required" -> "Google pide aceptar permisos. Abre el navegador de Flow para revisarlos."
        "browser_rejected" -> "Google ha rechazado el acceso del navegador. Revisa la sesión desde Conectar Flow."
        "credential_required", "credential_unavailable", "password_not_autofilled" -> "No se pudo usar la contraseña guardada. Revisa el acceso en el navegador de Flow."
        "credential_expired" -> "El acceso temporal guardado ha caducado. Abre la conexión de Flow e inicia sesión en Google; este pedido no se ha enviado."
        "invalid_credentials" -> "Google no aceptó la contraseña guardada. Corrígela en el navegador de Flow."
        "account_selection_required" -> "Selecciona tu cuenta de Google en el navegador de Flow."
        "flow_output_mode_unconfirmed", "flow_quantity_unconfirmed" -> "No se pudo confirmar el tipo o la cantidad en Flow. Revisa los ajustes del proyecto; este pedido no se ha enviado."
        "flow_model_forbidden" -> "Nano Banana Lite está deshabilitado. Selecciona Nano Banana Pro o Nano Banana 2 en el proyecto de Flow."
        "login_cooldown" -> "El último acceso automático fue hace menos de 5 minutos. Espera o completa el acceso en el navegador."
        "sign_in_control_missing" -> "Flow ha cambiado su pantalla de acceso y no se encuentra el botón para iniciar sesión. Abre la conexión de Flow para revisar el acceso; el pedido no se ha enviado."
        "unexpected_destination" -> "Google ha abierto una página de acceso no reconocida. Revisa el navegador de Flow; el pedido no se ha enviado."
        "login_timeout", "session_recovery_timeout", "preparation_timeout" -> "No se pudo completar la conexión automática a tiempo. Revisa el navegador de Flow; este pedido no se ha reenviado."
        "bridge_restarted_before_submit" -> "El puente se reinició antes del envío. El pedido se conserva y no se ha enviado a Google."
        "generation_delayed" -> "Flow está tardando más de lo habitual. El envío ya está hecho; Nexus sigue consultando sin generar otra vez."
        "flow_busy" -> "El puente está atendiendo el pedido anterior. Este encargo permanece en cola."
        else -> null
    }
}
