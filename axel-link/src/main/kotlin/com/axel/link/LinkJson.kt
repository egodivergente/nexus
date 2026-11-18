package com.axel.link

/**
 * JSON mínimo y sin dependencias, para que el mismo cliente compile igual en AXEL Hub (Android)
 * y en el núcleo del agente de Nexus (JVM). Objetos como [Map], listas como [List], números
 * enteros como [Long] y el resto como [Double].
 */
object LinkJson {
    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skip()
        val value = parser.value(0)
        parser.skip()
        if (!parser.end()) parser.fail("sobra contenido")
        return value
    }

    fun obj(text: String): Map<String, Any?> = asObject(parse(text)) ?: throw IllegalArgumentException("Se esperaba un objeto JSON")

    fun write(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    @Suppress("UNCHECKED_CAST")
    internal fun asObject(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean -> out.append(value.toString())
            is Double -> when {
                value.isNaN() || value.isInfinite() -> out.append("null")
                value == Math.rint(value) && Math.abs(value) < 1e15 -> out.append(value.toLong())
                else -> out.append(value.toString())
            }
            is Float -> write(out, value.toDouble())
            is Number -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    quote(out, key.toString())
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    write(out, item)
                }
                out.append(']')
            }
            is Array<*> -> write(out, value.asList())
            else -> quote(out, value.toString())
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' || c == '\u2028' || c == '\u2029' -> out.append("\\u").append(String.format("%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun end() = i >= s.length
        fun fail(why: String): Nothing = throw IllegalArgumentException("JSON no válido en la posición $i: $why")
        fun skip() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }

        fun value(depth: Int): Any? {
            if (depth > 64) fail("demasiados niveles")
            if (end()) fail("fin inesperado")
            val c = s[i]
            return when {
                c == '{' -> obj(depth)
                c == '[' -> list(depth)
                c == '"' -> string()
                c == 't' -> literal("true", true)
                c == 'f' -> literal("false", false)
                c == 'n' -> literal("null", null)
                c == '-' || c in '0'..'9' -> number()
                else -> fail("carácter inesperado")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("literal desconocido")
            i += word.length
            return value
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skip()
            if (!end() && s[i] == '}') { i++; return map }
            while (true) {
                skip()
                if (end() || s[i] != '"') fail("falta una clave")
                val key = string()
                skip()
                if (end() || s[i] != ':') fail("faltan los dos puntos")
                i++
                skip()
                map[key] = value(depth + 1)
                skip()
                if (end()) fail("objeto sin cerrar")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> fail("falta un separador")
                }
            }
        }

        private fun list(depth: Int): List<Any?> {
            val items = ArrayList<Any?>()
            i++
            skip()
            if (!end() && s[i] == ']') { i++; return items }
            while (true) {
                skip()
                items.add(value(depth + 1))
                skip()
                if (end()) fail("lista sin cerrar")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return items }
                    else -> fail("falta un separador")
                }
            }
        }

        private fun string(): String {
            i++
            val out = StringBuilder()
            while (true) {
                if (end()) fail("cadena sin cerrar")
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (end()) fail("escape incompleto")
                        when (s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("escape unicode incompleto")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("escape unicode no válido")
                                out.append(code.toChar())
                                i += 4
                            }
                            else -> fail("escape desconocido")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Number {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            var real = false
            if (i < s.length && s[i] == '.') {
                real = true
                i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                real = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val text = s.substring(start, i)
            return if (real) text.toDoubleOrNull() ?: fail("número no válido")
            else text.toLongOrNull() ?: text.toDoubleOrNull() ?: fail("número no válido")
        }
    }
}

internal fun Map<String, Any?>.text(key: String, default: String = ""): String = when (val value = this[key]) {
    null -> default
    is String -> value
    else -> value.toString()
}
internal fun Map<String, Any?>.flag(key: String): Boolean = this[key] == true
internal fun Map<String, Any?>.number(key: String): Double? = (this[key] as? Number)?.toDouble()
internal fun Map<String, Any?>.child(key: String): Map<String, Any?> = LinkJson.asObject(this[key]) ?: emptyMap()
internal fun Map<String, Any?>.items(key: String): List<Any?> = (this[key] as? List<*>) ?: emptyList<Any?>()
