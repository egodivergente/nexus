package com.vidadomu.corte.model

/** Id source. Production uses random UUIDs; tests use a deterministic counter. */
fun interface IdGenerator {
    fun next(prefix: String): String

    companion object {
        val RANDOM = IdGenerator { prefix -> prefix + "_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12) }

        fun sequential(): IdGenerator {
            var n = 0
            return IdGenerator { prefix -> "${prefix}${++n}" }
        }
    }
}
