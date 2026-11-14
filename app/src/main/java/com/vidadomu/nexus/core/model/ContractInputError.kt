package com.vidadomu.nexus.core.model

/**
 * Un adaptador rechaza la petición y además dice de qué entrada se queja, para que la pantalla lleve al
 * sitio donde se arregla en vez de a una lista de opciones donde no hay nada que tocar. [fieldKey]
 * «prompt» es la descripción; cualquier otra clave es un parámetro del modelo. Sigue siendo un
 * [IllegalArgumentException] para no romper a quien ya lo captura así.
 */
class ContractInputError(message: String, val fieldKey: String? = null) : IllegalArgumentException(message)
