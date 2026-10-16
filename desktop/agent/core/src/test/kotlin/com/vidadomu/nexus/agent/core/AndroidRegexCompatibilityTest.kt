package com.vidadomu.nexus.agent.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * El motor de regex de Android (ICU) no admite el flag en línea (?U) de la JVM: la clase que lo
 * use falla al inicializarse (ExceptionInInitializerError) y Nexus se cierra al abrir el chat o al
 * enviar un mensaje. Las pruebas JVM no lo detectan por sí solas, así que se vigila el código.
 */
class AndroidRegexCompatibilityTest {
    private val unsupported = Regex("""\(\?[a-zA-Z]*U[a-zA-Z]*[):]""")

    @Test fun `ningun codigo que corre en Android usa el flag (?U)`() {
        val roots = listOf("src/main", "../app/src/main").map(::File).filter { it.isDirectory }
        assertTrue(roots.isNotEmpty())
        val offenders = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { file -> file.readLines().mapIndexedNotNull { i, line ->
                val code = line.trimStart()
                val comment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                if (!comment && unsupported.containsMatchIn(line.substringBefore("//"))) "${file.path}:${i + 1}" else null
            } }
        assertTrue(offenders.isEmpty(), "(?U) no funciona en Android:\n" + offenders.joinToString("\n"))
    }
}
