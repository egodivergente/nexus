package com.vidadomu.nexus.agent.core.agent

/** Product knowledge is separate from the registry of executable provider models. */
object ExternalServices {
    const val FLOW_INFO = """Google Flow existe: es el servicio creativo de Google en https://flow.google.com para crear y editar imagen y video por proyectos. No es un identificador de modelo de Atlas, Crun o Runway.
Permite trabajar con texto, referencias e imagenes de inicio/final segun el modelo y las funciones disponibles. Para un prompt de video, concreta sujeto, accion, entorno, luz, estilo y camara; las referencias deben complementar el texto, no contradecirlo.
En esta APK, Ajustes > Google Flow > Conectar Flow abre la recuperacion privada de la cuenta del ASUS mediante Tailscale. Iniciar sesion no conecta un proveedor de generacion: la generacion de Flow desde Nexus y la reanudacion de trabajos aun no estan implementadas. No se ha comprobado aqui el estado de inicio de sesion.
Puedes explicar Flow y redactar prompts para pegar alli. No sustituyas una peticion explicita de Flow por otro proveedor sin preguntarlo. No inventes creditos, precios, limites de texto, versiones, duraciones ni funciones habilitadas en la cuenta; deben comprobarse en Flow.
Fuentes de conocimiento (revisadas 2026-10-06): https://support.google.com/flow/answer/16353334 y https://support.google.com/flow/answer/16729550 . Son documentacion del producto, no pruebas de integracion de Nexus."""

    fun mentionsFlow(text: String): Boolean {
        if (Regex("\\bgoogle\\s+flow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        return Regex("\\bflow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !Regex("\\b(optical\\s+flow|flow\\s+matching)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    const val OFFLINE_FLOW_REPLY = """Google Flow es el servicio de Google para crear y editar imagen y video por proyectos. No es un modelo del catalogo de Nexus.
Para acceder a la cuenta del ASUS: Ajustes > Google Flow > Conectar Flow, con Tailscale activo. La generacion desde esta APK y la reanudacion automatica todavia no estan conectadas; no he comprobado si tu cuenta tiene la sesion iniciada.
Estoy en modo sin LLM: puedo mostrar esta informacion, pero no redactar ni adaptar un prompt experto. No he preparado ninguna generacion ni cambiado a otro proveedor."""
}
