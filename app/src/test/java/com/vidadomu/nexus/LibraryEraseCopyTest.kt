package com.vidadomu.nexus

import com.vidadomu.nexus.agent.core.assets.DeletionPlan
import com.vidadomu.nexus.agent.core.assets.DeletionTarget
import com.vidadomu.nexus.ui.library.eraseCopy
import org.junit.Assert.*
import org.junit.Test

class LibraryEraseCopyTest {
    private fun target(key: String, name: String) = DeletionTarget(key, name, setOf(key), setOf("file:///$key.png"))

    @Test fun oneFileSaysItCannotBeUndone() {
        val photo = target("a", "Playa")
        val copy = eraseCopy(DeletionPlan(listOf(photo), emptyMap(), emptyList(), 0, 0), listOf(photo))
        assertEquals("¿Eliminar «Playa»?", copy.title)
        assertTrue(copy.canErase)
        assertEquals("Se borrará de este móvil y no se puede deshacer.", copy.lines.first())
        assertTrue(copy.lines.any { "otros dispositivos" in it })
    }

    @Test fun externalChatsAndBlockedAreExplained() {
        val a = target("a", "Uno"); val b = target("b", "Dos"); val c = target("c", "Tres")
        val copy = eraseCopy(DeletionPlan(listOf(a, b), mapOf("c" to "se usa en el guion"), emptyList(), 2, 1), listOf(a, b, c))
        assertEquals("¿Eliminar 2 archivos?", copy.title)
        assertTrue(copy.lines.any { it.startsWith("2 archivos están en la galería o en Documentos/ALEXIA") })
        assertTrue(copy.lines.contains("Aparece en un chat; allí se verá como no disponible."))
        assertTrue(copy.lines.contains("No se elimina «Tres»: se usa en el guion."))
    }

    @Test fun nothingRemovableOnlyExplains() {
        val a = target("a", "Uno")
        val copy = eraseCopy(DeletionPlan(emptyList(), mapOf("a" to "se usa en un montaje de Nexus Cut"), emptyList(), 0, 0), listOf(a))
        assertEquals("No se puede eliminar", copy.title)
        assertFalse(copy.canErase)
        assertEquals(listOf("No se elimina «Uno»: se usa en un montaje de Nexus Cut."), copy.lines)
    }
}
