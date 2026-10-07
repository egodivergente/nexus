package com.vidadomu.nexus.agent.core.agent

/** Product knowledge is separate from the registry of executable provider models. */
object ExternalServices {
    const val FLOW_INFO = """Google Flow existe: es el servicio creativo de Google en https://flow.google.com para crear y editar imagen y video por proyectos. No es un identificador de modelo de Atlas, Crun o Runway.
Permite trabajar con texto, referencias e imagenes de inicio/final segun el modelo y las funciones disponibles. Para un prompt de video, concreta sujeto, accion, entorno, luz, estilo y camara; las referencias deben complementar el texto, no contradecirlo.
En esta APK, Ajustes > Google Flow > Crear en Flow envia un prompt de texto al proyecto de Flow que ya este abierto y autenticado en el ASUS, a traves del puente privado Tailscale. Antes de enviar, la persona debe confirmar expresamente: Flow conserva sus ajustes actuales y determina el coste. Nexus no inicia sesion en Google, no exporta cookies, no elige un modelo ni cambia los ajustes del proyecto.
El puente solo marca un resultado como listo cuando detecta un archivo descargable de Flow; si no puede hacerlo, debe indicarlo y permitir abrir Flow, nunca inventar que se genero. La entrada de archivos/referencias desde Nexus todavia no se envia a Flow. El maximo de 4000 caracteres es una proteccion local del puente, no un limite confirmado de Google Flow.
Puedes explicar Flow y redactar prompts para pegar o enviar desde Crear en Flow. No sustituyas una peticion explicita de Flow por otro proveedor sin preguntarlo. No inventes creditos, precios, limites de texto, versiones, duraciones ni funciones habilitadas en la cuenta; deben comprobarse en Flow.
Fuentes de conocimiento (revisadas 2026-10-06): https://support.google.com/flow/answer/16353334 y https://support.google.com/flow/answer/16729550 . Son documentacion del producto, no pruebas de integracion de Nexus."""

    fun mentionsFlow(text: String): Boolean {
        if (Regex("\\bgoogle\\s+flow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        return Regex("\\bflow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !Regex("\\b(optical\\s+flow|flow\\s+matching)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    const val OFFLINE_FLOW_REPLY = """Google Flow es el servicio de Google para crear y editar imagen y video por proyectos. No es un modelo del catalogo de Nexus.
Para generar desde la APK: Ajustes > Google Flow > Crear en Flow, con Tailscale activo y el ASUS vinculado. El envio requiere una confirmacion explicita y usa la configuracion actual del proyecto abierto en Flow. No puedo comprobar desde este modo si el puente o la sesion estan disponibles, ni iniciar una generacion de pago.
Estoy en modo sin LLM: puedo mostrar esta informacion, pero no redactar ni adaptar un prompt experto. No he preparado ninguna generacion ni cambiado a otro proveedor."""
}
