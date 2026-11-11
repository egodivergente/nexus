package com.vidadomu.nexus.cut.command

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Lectura tipada de argumentos: un valor ausente o mal formado es INVALID_ARGUMENT con el nombre del campo. */
class Args(val json: JsonObject) {
    fun has(key: String): Boolean = json[key].let { it != null && it !is kotlinx.serialization.json.JsonNull }

    fun str(key: String): String? = (json[key] as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != null }?.contentOrNull?.takeIf { has(key) }
    fun req(key: String): String = str(key)?.takeIf { it.isNotBlank() } ?: bad("falta «$key»")

    fun long(key: String): Long? {
        if (!has(key)) return null
        val p = json[key] as? JsonPrimitive ?: bad("«$key» debe ser un número entero")
        return p.longOrNull ?: p.doubleOrNull?.takeIf { it.isFinite() }?.let { Math.round(it) } ?: bad("«$key» debe ser un número entero")
    }
    fun reqLong(key: String): Long = long(key) ?: bad("falta «$key» (número entero)")
    fun int(key: String): Int? = long(key)?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else bad("«$key» está fuera de rango") }

    fun double(key: String): Double? {
        if (!has(key)) return null
        return (json[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: bad("«$key» debe ser un número")
    }
    fun float(key: String): Float? = double(key)?.toFloat()

    fun bool(key: String): Boolean? {
        if (!has(key)) return null
        return (json[key] as? JsonPrimitive)?.booleanOrNull ?: bad("«$key» debe ser true o false")
    }
    fun bool(key: String, default: Boolean): Boolean = bool(key) ?: default

    /** Lista de ids sin repetidos y en el orden recibido. Admite también un único id suelto. */
    fun ids(key: String): Set<String> = when (val v = json[key]) {
        is JsonArray -> v.mapTo(LinkedHashSet()) { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) ?: bad("«$key» debe ser una lista de ids") }
        is JsonPrimitive -> if (has(key)) setOf(v.content) else emptySet()
        else -> emptySet()
    }
    fun reqIds(key: String): Set<String> = ids(key).ifEmpty { bad("«$key» está vacío") }

    fun objects(key: String): List<Args> = (json[key] as? JsonArray)?.map { Args(it as? JsonObject ?: bad("cada elemento de «$key» debe ser un objeto")) }.orEmpty()
    fun array(key: String): JsonArray? = json[key] as? JsonArray

    inline fun <reified E : Enum<E>> enum(key: String): E? {
        val s = str(key) ?: return null
        return enumValues<E>().firstOrNull { it.name.equals(s, ignoreCase = true) }
            ?: bad("«$key» no admite «$s»; valores: ${enumValues<E>().joinToString { it.name.lowercase() }}")
    }

    fun bad(message: String): Nothing = cutFail(ErrorCode.INVALID_ARGUMENT, message)
}
