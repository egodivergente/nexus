package com.vidadomu.corte.storage

import com.vidadomu.corte.model.Project
import com.vidadomu.corte.model.TimelineValidator
import kotlinx.serialization.json.Json

class ProjectFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ProjectCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(project: Project): ByteArray = json.encodeToString(Project.serializer(), project).toByteArray(Charsets.UTF_8)

    /** Parses and checks structural invariants; anything wrong is an exception, never a half project. */
    fun decode(bytes: ByteArray): Project {
        val project = try {
            json.decodeFromString(Project.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw ProjectFormatException("El archivo de proyecto no se puede leer: ${e.message}", e)
        }
        if (project.schemaVersion > Project.CURRENT_SCHEMA) {
            throw ProjectFormatException("Proyecto creado con una versión más nueva de la app (esquema ${project.schemaVersion}).")
        }
        val violations = TimelineValidator.violations(project.timeline)
        if (violations.isNotEmpty()) throw ProjectFormatException("Proyecto inconsistente: ${violations.first()}")
        return project
    }
}
