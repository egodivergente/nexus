package com.vidadomu.nexus.sync

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DriveNarrativeStatusTest {
    private fun status(state: String, supported: Boolean = true, enabled: Boolean = true, conflicts: Int = 0, pending: Int = 0) = buildJsonObject {
        put("state", "synced")
        put("narrativeSync", buildJsonObject {
            put("state", state); put("supported", supported); put("enabled", enabled)
            put("conflicts", conflicts); put("pendingDependencies", pending)
        })
    }

    @Test fun aLegacySuccessNeverClaimsTheScreenplayWasShared() {
        val result = driveNarrativeStatus(buildJsonObject { put("state", "synced"); put("completedAt", 123) })
        assertEquals("Guion pendiente de sincronizar", result.title)
        assertTrue(result.detail!!.contains("todavía no confirma"))
    }

    @Test fun disabledAndUnsupportedRemainDistinctFromCompleted() {
        assertEquals("Guion y storyboard pausados", driveNarrativeStatus(status("synced"), false).title)
        assertEquals("Guion sin conexión compatible", driveNarrativeStatus(status("synced", supported = false)).title)
        assertTrue(driveNarrativeStatus(status("synced", supported = false)).attention)
        assertEquals("Guion pendiente de sincronizar", driveNarrativeStatus(status("disabled", enabled = false)).title)
    }

    @Test fun conflictsAndMissingDependenciesTakePriorityOverASuccessLabel() {
        val conflict = driveNarrativeStatus(status("synced", conflicts = 1))
        assertEquals("Guion con versiones para revisar", conflict.title)
        assertEquals("1 elemento conserva versiones distintas.", conflict.detail)
        assertTrue(conflict.attention)
        val pending = driveNarrativeStatus(status("synced", pending = 2))
        assertEquals("Guion con vínculos pendientes", pending.title)
        assertTrue(pending.attention)
    }

    @Test fun uploadCompletionDoesNotInventPeerAcknowledgement() {
        val result = driveNarrativeStatus(status("synced"))
        assertEquals("Guion compartido en Drive", result.title)
        assertTrue(result.detail!!.contains("completar su sincronización"))
        assertFalse(result.attention)
    }

    @Test fun narrativeWrappersShowStoryTextAndNamesWithoutExposingIds() {
        fun version(id: String, text: String) = buildJsonObject {
            put("id", id); put("device", if (id == "one") "Windows" else "S25"); put("at", 1_800_000_000_000)
            put("value", buildJsonObject {
                put("id", "block-private-id"); put("projectId", "project-private-id"); put("format", 1)
                put("payload", buildJsonObject { put("text", text); put("narrativeState", "idea") })
                put("commonHash", "opaque-hash")
            })
        }
        val longText = "Una indicación narrativa que debe poder leerse completa. ".repeat(4)
        val input = buildJsonObject {
            put("conflicts", buildJsonArray { add(buildJsonObject {
                put("key", "story:blocks:block-private-id")
                put("versions", buildJsonArray { add(version("one", longText)); add(version("two", "Mateo mira a Julián")) })
            }) })
        }
        val result = DriveConflictPresentation.summarize(input, mapOf("project-private-id" to "Película"))
        val group = result.groups.single()
        assertEquals(DriveConflictKind.STORY_BLOCKS, group.kind)
        val item = group.items.single()
        assertEquals(longText, item.field(item.versions.first(), "Texto"))
        assertTrue("Texto" in item.differingFields)
        assertFalse(item.title.contains("private-id"))
        assertFalse(item.versions.flatMap { it.fields }.any { it.value.contains("private-id") || it.value.contains("opaque-hash") })
    }

    @Test fun referenceConflictsUseRelatedNamesAndPreservePerUseSelection() {
        fun version(id: String, selected: Boolean) = buildJsonObject {
            put("id", id)
            put("value", buildJsonObject {
                put("id", "link-private-id"); put("projectId", "project-private-id")
                put("payload", buildJsonObject {
                    put("assetId", "asset-private-id"); put("targetId", "shot-private-id")
                    put("role", "candidate"); put("selected", selected)
                })
            })
        }
        val input = buildJsonObject { put("conflicts", buildJsonArray { add(buildJsonObject {
            put("key", "story:links:link-private-id"); put("versions", buildJsonArray { add(version("one", false)); add(version("two", true)) })
        }) }) }
        val item = DriveConflictPresentation.summarize(input, mapOf("project-private-id" to "Película"),
            relatedNames = mapOf("asset-private-id" to "Retrato de Mateo", "shot-private-id" to "La mirada")).groups.single().items.single()
        assertEquals("Retrato de Mateo", item.field(item.versions.first(), "Archivo"))
        assertEquals("La mirada", item.field(item.versions.first(), "Destino"))
        assertEquals(setOf("Material elegido"), item.differingFields)
    }

    @Test fun scopesReferenceBehaviorAndOrderRemainUnderstandable() {
        fun version(id: String, position: Int) = buildJsonObject {
            put("id", id)
            put("value", buildJsonObject { put("payload", buildJsonObject {
                put("scope", "scene"); put("behavior", "replace"); put("order", position)
                put("role", "source"); put("durationSec", 4.5)
            }) })
        }
        val input = buildJsonObject { put("conflicts", buildJsonArray { add(buildJsonObject {
            put("key", "story:links:link-id"); put("versions", buildJsonArray { add(version("one", 2)); add(version("two", 3)) })
        }) }) }
        val item = DriveConflictPresentation.summarize(input).groups.single().items.single()
        val row = item.versions.first()
        assertEquals("Escena", item.field(row, "Alcance"))
        assertEquals("Sustituye las referencias generales", item.field(row, "Referencias"))
        assertEquals("3", item.field(row, "Posición"))
        assertEquals("4.5 s", item.field(row, "Duración orientativa"))
        assertEquals("Material de origen", item.field(row, "Uso del material"))
        assertEquals(setOf("Posición"), item.differingFields)
    }
}
