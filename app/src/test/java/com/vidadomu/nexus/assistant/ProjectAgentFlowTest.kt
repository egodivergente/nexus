package com.vidadomu.nexus.assistant

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.SaverScope
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.assets.ProjectMemory
import com.vidadomu.nexus.agent.core.assets.ProjectNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.io.File
import java.nio.file.Files

class ProjectAgentFlowTest {
    private fun project(id: String = "p1", updatedAt: Long? = 10, rules: List<String> = listOf("Mateo nunca sonríe"),
        models: List<String> = listOf("Seedance 2.0", "Kling"), notes: List<ProjectNote> = listOf(ProjectNote(1, "a"), ProjectNote(2, "b"))) =
        Project(id, "Proyecto $id", "Drama", createdAt = 0, memory = ProjectMemory(synopsis = "Sinopsis", tone = "sobrio",
            visualStyle = "35 mm", rules = rules, preferredModels = models, notes = notes, updatedAt = updatedAt))

    @Test fun freshDraftIsCleanAndEditsMakeItDirty() {
        val p = project()
        val draft = ProjectMemoryDraft.from(p)
        assertFalse(draft.isDirty(p))
        assertTrue(draft.copy(synopsis = "Otra").isDirty(p))
        assertTrue(draft.copy(removedNotes = listOf(1)).isDirty(p))
    }

    @Test fun storageNormalisationDoesNotLookLikeAnEdit() {
        val p = project()
        val draft = ProjectMemoryDraft.from(p).copy(models = "  Seedance 2.0 \nKling\n", rules = "  Mateo nunca sonríe \n\n", description = " Drama ")
        assertFalse(draft.isDirty(p))
        assertTrue("La coma ya no separa modelos", draft.copy(models = "Seedance 2.0, Kling").isDirty(p))
    }

    @Test fun keepNotesOnlyWhenSomethingWasRemoved() {
        val p = project()
        assertNull(ProjectMemoryDraft.from(p).keepNotes(p))
        assertEquals(setOf(2L), ProjectMemoryDraft.from(p).copy(removedNotes = listOf(1)).keepNotes(p))
    }

    @Test fun draftsSurviveSaveAndRestore() {
        val draft = ProjectMemoryDraft.from(project()).copy(synopsis = "Línea 1\nLínea 2, con coma", removedNotes = listOf(1, 2), saveRequested = true)
        assertEquals(draft, ProjectMemoryDraft.fromSaved(draft.toSaved()))
        val noBase = ProjectMemoryDraft.from(project(updatedAt = null))
        assertEquals(noBase, ProjectMemoryDraft.fromSaved(noBase.toSaved()))
        assertNull(ProjectMemoryDraft.fromSaved(listOf("x")))
    }

    @Test fun pruningKeepsEditedDraftsPerProjectAndNeverMixesThem() {
        val a = project("a"); val b = project("b")
        val drafts = mutableMapOf(
            "a" to ProjectMemoryDraft.from(a).copy(tone = "épico"),
            "b" to ProjectMemoryDraft.from(b),
            "gone" to ProjectMemoryDraft.from(project("gone")).copy(tone = "x"),
        )
        pruneProjectDrafts(drafts, listOf(a, b))
        assertEquals(setOf("a"), drafts.keys)
        assertEquals("épico", drafts.getValue("a").tone)
        assertEquals("sobrio", b.memory.tone)
    }

    @Test fun projectBeingEditedIsKeptEvenWithoutChanges() {
        val a = project("a")
        val drafts = mutableMapOf("a" to ProjectMemoryDraft.from(a))
        pruneProjectDrafts(drafts, listOf(a), keep = "a")
        assertEquals(setOf("a"), drafts.keys)
    }

    @Test fun editedProjectDraftSurvivesRefreshUntilSaveCallbackHandlesIt() {
        val before = project(updatedAt = 10)
        val pending = ProjectMemoryDraft.from(before).copy(tone = "épico", saveRequested = true)
        val saved = before.copy(memory = before.memory.copy(tone = "épico", updatedAt = 11))
        val drafts = mutableMapOf(before.id to pending)
        pruneProjectDrafts(drafts, listOf(saved), keep = before.id)
        assertEquals("El callback debe conservar la referencia al borrador en edición", pending, drafts[before.id])
        pruneProjectDrafts(drafts, listOf(saved))
        assertTrue("Fuera del editor sí se retira un guardado confirmado", drafts.isEmpty())
    }

    @Test fun confirmedSaveRemovesDraftButExternalChangeDoesNot() {
        val base = project(updatedAt = 10)
        val pending = ProjectMemoryDraft.from(base).copy(tone = "épico", saveRequested = true)
        val saved = base.copy(memory = base.memory.copy(tone = "épico", updatedAt = 11))
        val external = base.copy(memory = base.memory.copy(synopsis = "El agente añadió esto", updatedAt = 11))
        assertTrue(pending.saveConfirmed(saved))
        assertFalse(pending.saveConfirmed(external))
        assertFalse(pending.saveConfirmed(base))
        val drafts = mutableMapOf("p1" to pending)
        pruneProjectDrafts(drafts, listOf(external))
        assertEquals("épico", drafts.getValue("p1").tone)
        pruneProjectDrafts(drafts, listOf(saved))
        assertTrue(drafts.isEmpty())
    }

    @Test fun staleDraftIsDetectedWhenEditableMemoryChangesAfterward() {
        val base = project(updatedAt = 10)
        val draft = ProjectMemoryDraft.from(base).copy(tone = "épico")
        assertFalse(draft.isStale(base))
        assertFalse("Solo updatedAt (p. ej. una nota) no es conflicto", draft.isStale(base.copy(memory = base.memory.copy(updatedAt = 20))))
        assertFalse("Una nota nueva del agente no es conflicto", draft.isStale(base.copy(memory = base.memory.copy(
            notes = base.memory.notes + ProjectNote(3, "nota del agente"), updatedAt = 20))))
        val agentEdit = base.copy(memory = base.memory.copy(synopsis = "Del agente", updatedAt = 20))
        assertTrue(draft.isStale(agentEdit))
        assertTrue(draft.copy(saveRequested = true).isStale(agentEdit))
        assertTrue(draft.isStale(base.copy(description = "Otra descripción")))
        assertFalse(draft.copy(saveRequested = true).isStale(base.copy(memory = base.memory.copy(tone = "épico", updatedAt = 20))))
    }

    @Test fun legacyDraftWithoutSnapshotFallsBackToUpdatedAt() {
        val base = project(updatedAt = 10)
        val legacy = ProjectMemoryDraft.from(base).copy(tone = "épico", baseSnapshot = null)
        assertFalse(legacy.isStale(base))
        assertTrue(legacy.isStale(base.copy(memory = base.memory.copy(updatedAt = 11))))
    }

    @Test fun uiLimitsAreDerivedFromTheStore() {
        assertEquals(AssetIndex.MAX_DESCRIPTION, ProjectMemoryDraft.DESCRIPTION_LIMIT)
        assertEquals(AssetIndex.MAX_FIELD, ProjectMemoryDraft.TEXT_LIMIT)
        assertEquals(12_039, ProjectMemoryDraft.RULES_LIMIT)
        assertEquals(1_451, ProjectMemoryDraft.MODELS_LIMIT)
    }

    @Test fun storageWouldDropRulesAndModelsSoSavingMustBeBlocked() = runBlocking {
        var tick = 10L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA test local")
        val draft = ProjectMemoryDraft.from(base).copy(rules = "Luz cálida\nluz calida\n" + "x".repeat(301), models = "flux\nflux")
        assertTrue(draft.saveWarning()!!.contains("300"))
        assertTrue(draft.copy(rules = "Luz cálida\nluz calida").saveWarning()!!.contains("equivalentes"))
        assertTrue(draft.copy(rules = "Una regla").saveWarning()!!.contains("repetidos"))
        val pending = draft.copy(saveRequested = true)
        val changed = index.updateProjectMemory(base.id) { m ->
            m.copy(rules = draft.rules.lines(), preferredModels = draft.models.lines())
        }
        assertFalse(pending.saveConfirmed(changed))
        assertTrue(pending.isStale(changed))
        val drafts = mutableMapOf(base.id to pending)
        pruneProjectDrafts(drafts, listOf(changed))
        assertEquals(pending, drafts[base.id])
        val valid = ProjectMemoryDraft.from(changed).copy(rules = "Luz cálida\n\nOtra regla", models = "flux\nKling")
        assertNull(valid.saveWarning())
        val saved = persistProjectMemoryDraft(index, valid)
        assertEquals(listOf("Luz cálida", "Otra regla"), saved.memory.rules)
        assertEquals(listOf("flux", "Kling"), saved.memory.preferredModels)
        assertTrue(valid.copy(saveRequested = true).saveConfirmed(saved))
    }

    @Test fun storageBoundariesDoNotSilentlyThrowAwayText() {
        val base = project()
        val clean = ProjectMemoryDraft.from(base)
        assertNull(clean.copy(rules = "x".repeat(300), models = "m".repeat(120)).saveWarning())
        assertTrue(clean.copy(rules = "x".repeat(301)).saveWarning()!!.contains("300"))
        assertTrue(clean.copy(models = "m".repeat(121)).saveWarning()!!.contains("120"))
        assertNull(clean.copy(rules = (1..40).joinToString("\n") { "regla $it" }).saveWarning())
        assertTrue(clean.copy(rules = (1..41).joinToString("\n") { "regla $it" }).saveWarning()!!.contains("40"))
        assertNull(clean.copy(models = (1..12).joinToString("\n") { "Modelo $it" }).saveWarning())
        assertTrue(clean.copy(models = (1..13).joinToString("\n") { "Modelo $it" }).saveWarning()!!.contains("12"))
        assertNull(clean.copy(models = "Flux\nflux").saveWarning())
        assertNull("La coma es parte del nombre", clean.copy(models = "Kling 2.1, Pro").saveWarning())
        assertTrue(clean.copy(models = "Kling 2.1, Pro\nKling 2.1, Pro").saveWarning()!!.contains("repetidos"))
        assertTrue(clean.copy(rules = "🎬\n✂️").saveWarning()!!.contains("equivalentes"))
        assertTrue(clean.copy(rules = "Mateo sonríe.\nMateo sonríe!").saveWarning()!!.contains("equivalentes"))
    }

    @Test fun validationKeepsBlockedDraftAndNeverRequestsWrite() {
        val p = project()
        val original = ProjectMemoryDraft.from(p).copy(rules = "Luz cálida\nluz calida.", models = "flux\nflux", saveRequested = true)
        val checked = original.prepareSave(p) as ProjectMemorySaveDecision.Blocked
        assertEquals(original.copy(saveRequested = false), checked.draft)
        assertEquals(original.rules, checked.draft.rules)
        assertEquals(original.models, checked.draft.models)
        assertTrue(checked.message.contains("equivalentes"))
        assertEquals("Luz cálida", checked.draft.rules.lineSequence().first())
        val valid = original.copy(rules = "Luz cálida\nOtra regla", models = "Flux\nflux")
        assertNull(valid.saveWarning())
        assertTrue((valid.prepareSave(p) as ProjectMemorySaveDecision.Ready).draft.saveRequested)
        val onlyTimestamp = valid.prepareSave(p.copy(memory = p.memory.copy(updatedAt = 11)))
        assertTrue(onlyTimestamp is ProjectMemorySaveDecision.Ready)
        val stale = valid.prepareSave(p.copy(memory = p.memory.copy(synopsis = "Del agente", updatedAt = 11)))
        assertTrue(stale is ProjectMemorySaveDecision.Blocked)
        assertFalse((stale as ProjectMemorySaveDecision.Blocked).draft.saveRequested)
    }

    @Test fun everyAcceptedMemoryDraftMatchesTheRealStoreAndCanBePruned() = runBlocking {
        val cases = listOf(
            "x".repeat(AssetIndex.MAX_RULE_LENGTH) to "m".repeat(AssetIndex.MAX_MODEL_LENGTH),
            (1..AssetIndex.MAX_RULES).joinToString("\n") { "regla $it" } to
                (1..AssetIndex.MAX_PREFERRED_MODELS).joinToString("\n") { "modelo $it" },
            "  Luz cálida  \n\nOtra regla" to "  Flux  \n  flux  ",
            "🎬" to "Kling 2.1 Pro",
            "Una regla" to "Kling 2.1, Pro\nWan 2.2, I2V",
        )
        for ((rules, models) in cases) {
            var tick = 100L
            val index = AssetIndex(null) { ++tick }
            val base = index.createProject("QA memoria")
            val draft = ProjectMemoryDraft.from(base).copy(description = " Drama ", synopsis = "  Sinopsis ",
                rules = rules, models = models)
            assertNull("$rules / $models", draft.saveWarning())
            val ready = (draft.prepareSave(base) as ProjectMemorySaveDecision.Ready).draft
            val saved = persistProjectMemoryDraft(index, ready)
            assertEquals("Drama", saved.description)
            assertTrue("El core no devolvió el borrador aceptado: $rules / $models", ready.saveConfirmed(saved))
            val drafts = mutableMapOf(base.id to ready)
            pruneProjectDrafts(drafts, listOf(saved))
            assertTrue(drafts.isEmpty())
        }
    }

    @Test fun everyLossyMemoryInputIsBlockedAndLeavesNoSaveRequested() {
        val base = project()
        val clean = ProjectMemoryDraft.from(base)
        val invalid = listOf(
            clean.copy(rules = "r".repeat(AssetIndex.MAX_RULE_LENGTH + 1)),
            clean.copy(rules = (1..AssetIndex.MAX_RULES + 1).joinToString("\n") { "r$it" }),
            clean.copy(rules = "Luz cálida\nluz calida."),
            clean.copy(rules = "🎬\n✂️"),
            clean.copy(models = "M".repeat(AssetIndex.MAX_MODEL_LENGTH + 1)),
            clean.copy(models = (1..AssetIndex.MAX_PREFERRED_MODELS + 1).joinToString("\n") { "m$it" }),
            clean.copy(models = "flux\nflux"),
            clean.copy(description = "d".repeat(ProjectMemoryDraft.DESCRIPTION_LIMIT + 1)),
            clean.copy(synopsis = "s".repeat(ProjectMemoryDraft.TEXT_LIMIT + 1)),
            clean.copy(rules = "x".repeat(ProjectMemoryDraft.RULES_LIMIT + 1)),
            clean.copy(models = "m".repeat(ProjectMemoryDraft.MODELS_LIMIT + 1)),
            clean.copy(models = "Seedance 2.0, Kling", legacyModelsNeedReview = true),
        )
        for (draft in invalid) {
            val decision = draft.copy(saveRequested = true).prepareSave(base)
            assertTrue("No se bloqueó: $draft", decision is ProjectMemorySaveDecision.Blocked)
            val blocked = decision as ProjectMemorySaveDecision.Blocked
            assertEquals(draft.copy(saveRequested = false), blocked.draft)
            assertTrue(blocked.message.isNotBlank())
        }
    }

    @Test fun agentNoteAddedMeanwhileDoesNotBlockAndSurvivesTheSave() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val created = index.createProject("QA notas", "Original")
        val base = index.updateProjectMemory(created.id) { it.copy(notes = listOf(ProjectNote(5, "Nota vieja"))) }
        val draft = ProjectMemoryDraft.from(base).copy(tone = "épico", removedNotes = listOf(5))
        val agentNote = ProjectNote(99, "Nota del agente")
        val current = index.updateProjectMemory(base.id) { it.copy(notes = it.notes + agentNote) }
        assertTrue(current.memory.updatedAt != draft.baseUpdatedAt)
        assertFalse(draft.isStale(current))
        assertTrue(draft.prepareSave(current) is ProjectMemorySaveDecision.Ready)
        val ready = (draft.prepareSave(current) as ProjectMemorySaveDecision.Ready).draft
        val saved = persistProjectMemoryDraft(index, ready)
        assertEquals("épico", saved.memory.tone)
        assertEquals(listOf(agentNote), saved.memory.notes)
        assertEquals("Original", saved.description)
        assertTrue(ready.saveConfirmed(saved))
        val drafts = mutableMapOf(base.id to ready)
        pruneProjectDrafts(drafts, listOf(saved))
        assertTrue(drafts.isEmpty())
    }

    @Test fun changedEditableFieldBlocksWithoutWritingDescription() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA conflicto", "Original")
        val draft = ProjectMemoryDraft.from(base).copy(description = "Nueva descripción", tone = "épico")
        val agent = index.updateProjectMemory(base.id) { it.copy(synopsis = "Del agente") }
        assertTrue(draft.prepareSave(agent) is ProjectMemorySaveDecision.Blocked)
        val failure = runCatching { persistProjectMemoryDraft(index, draft.copy(saveRequested = true)) }.exceptionOrNull()
        assertTrue("Debe rechazar: $failure", failure is IllegalArgumentException)
        val after = index.findProject(base.id)!!
        assertEquals(agent, after)
        assertEquals("Original", after.description)
        assertEquals("Del agente", after.memory.synopsis)
        assertNull(after.memory.tone)
    }

    @Test fun lossyDraftIsRejectedInsideTheStoreLockWithoutAnyWrite() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA lossy", "Original")
        val lossy = ProjectMemoryDraft.from(base).copy(description = "Nueva", rules = "r".repeat(AssetIndex.MAX_RULE_LENGTH + 1))
        val failure = runCatching { persistProjectMemoryDraft(index, lossy) }.exceptionOrNull()
        assertTrue("Debe rechazar: $failure", failure is IllegalArgumentException)
        assertEquals(base, index.findProject(base.id))
    }

    @Test fun afterReviewUnblocksOnlyTheAcceptedSnapshotAndKeepsTheText() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA revisión", "Original")
        val draft = ProjectMemoryDraft.from(base).copy(description = "Mía", tone = "épico", rules = "Regla mía")
        val first = index.updateProjectMemory(base.id) { it.copy(synopsis = "Del agente") }
        assertTrue(draft.isStale(first))
        val reviewed = draft.copy(saveRequested = true).afterReview(first)
        assertEquals("Campo no tocado adopta lo del agente", "Del agente", reviewed.synopsis)
        assertEquals(listOf(draft.description, draft.tone, draft.style, draft.rules, draft.models, draft.removedNotes),
            listOf(reviewed.description, reviewed.tone, reviewed.style, reviewed.rules, reviewed.models, reviewed.removedNotes))
        assertFalse(reviewed.saveRequested)
        assertEquals("afterReview no escribe", first, index.findProject(base.id))
        assertFalse(reviewed.isStale(first))
        val second = index.updateProjectMemory(base.id) { it.copy(visualStyle = "Otro estilo del agente") }
        assertTrue("Un cambio posterior vuelve a bloquear", reviewed.isStale(second))
        assertTrue(runCatching { persistProjectMemoryDraft(index, reviewed) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(second, index.findProject(base.id))
        val accepted = reviewed.afterReview(second)
        val saved = persistProjectMemoryDraft(index, accepted)
        assertEquals("Mía", saved.description)
        assertEquals("épico", saved.memory.tone)
        assertEquals(listOf("Regla mía"), saved.memory.rules)
        assertEquals("Del agente", saved.memory.synopsis)
        assertEquals("Otro estilo del agente", saved.memory.visualStyle)
        assertTrue(accepted.copy(saveRequested = true).saveConfirmed(saved))
    }

    @Test fun fullStoreMemoryWithCommaModelsCanSaveAnotherField() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA lleno")
        val rules = (1..AssetIndex.MAX_RULES).map { "R$it ".padEnd(AssetIndex.MAX_RULE_LENGTH, 'x') }
        val models = (1..AssetIndex.MAX_PREFERRED_MODELS).map { "Modelo $it, variante ".padEnd(AssetIndex.MAX_MODEL_LENGTH, 'm') }
        val full = index.updateProjectMemory(base.id) { it.copy(rules = rules, preferredModels = models) }
        assertEquals(rules, full.memory.rules)
        assertEquals(models, full.memory.preferredModels)
        val draft = ProjectMemoryDraft.from(full).copy(tone = "épico")
        assertEquals(ProjectMemoryDraft.RULES_LIMIT, draft.rules.length)
        assertEquals(ProjectMemoryDraft.MODELS_LIMIT, draft.models.length)
        assertFalse(draft.legacyModelsNeedReview)
        assertNull(draft.saveWarning())
        val saved = persistProjectMemoryDraft(index, (draft.prepareSave(full) as ProjectMemorySaveDecision.Ready).draft)
        assertEquals("épico", saved.memory.tone)
        assertEquals(rules, saved.memory.rules)
        assertEquals(models, saved.memory.preferredModels)
        assertTrue(draft.copy(saveRequested = true).saveConfirmed(saved))
    }

    @Test fun failedDiskWriteKeepsMemoryAndFileUnchanged() = runBlocking {
        val dir = Files.createTempDirectory("nexus-index-atomic").toFile()
        try {
            val file = File(dir, "index.json")
            var tick = 0L
            val index = AssetIndex(file) { ++tick }
            val base = index.createProject("QA disco", "Original")
            val onDisk = file.readText()
            // The temporary write target is a non-empty directory, so persist() fails before replacing index.json.
            File(dir, "index.json.tmp").apply { mkdirs(); File(this, "bloqueo").writeText("x") }
            val draft = ProjectMemoryDraft.from(base).copy(description = "Nueva", tone = "épico")
            val failure = runCatching { persistProjectMemoryDraft(index, draft) }.exceptionOrNull()
            assertTrue("La escritura debía fallar", failure != null && failure !is IllegalArgumentException)
            assertEquals(base, index.findProject(base.id))
            assertEquals(onDisk, file.readText())
            val reloaded = AssetIndex(file).findProject(base.id)!!
            assertEquals("Original", reloaded.description)
            assertNull(reloaded.memory.tone)
        } finally { dir.deleteRecursively() }
    }

    @Test fun saverRestoresLegacyMultiDraftFormatAndFlagsCommaModels() {
        val legacy = listOf(
            "a", "Desc", "Sin", "tono", "35 mm", "Regla", "Seedance 2.0, Kling", "1,2", "10", "false",
            "b", "", "", "épico", "", "", "Flux", "", "", "true",
        )
        val restored = ProjectMemoryDraftsSaver.restore(ArrayList(legacy))!!.toMap()
        assertEquals(setOf("a", "b"), restored.keys)
        val a = restored.getValue("a")
        assertEquals("Seedance 2.0, Kling", a.models)
        assertEquals(listOf(1L, 2L), a.removedNotes)
        assertEquals(10L, a.baseUpdatedAt)
        assertNull(a.baseSnapshot)
        assertTrue(a.legacyModelsNeedReview)
        assertTrue(a.saveWarning()!!.contains("Revisa los modelos"))
        assertTrue(a.prepareSave(project("a")) is ProjectMemorySaveDecision.Blocked)
        val b = restored.getValue("b")
        assertFalse(b.legacyModelsNeedReview)
        assertTrue(b.saveRequested)
        assertNull(b.baseUpdatedAt)
        assertEquals("épico", b.tone)
    }

    @Test fun saverV2RoundTripKeepsSnapshotAndReviewFlag() {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val drafts = mutableStateMapOf(
            "p1" to ProjectMemoryDraft.from(project("p1")).copy(tone = "épico", removedNotes = listOf(2)),
            "p2" to ProjectMemoryDraft.from(project("p2")).copy(models = "Kling 2.1, Pro", legacyModelsNeedReview = true, saveRequested = true),
        )
        val saved = with(ProjectMemoryDraftsSaver) { scope.save(drafts) }!!
        assertEquals("nexus-project-drafts-v2", saved.first())
        assertEquals(1 + 2 * ProjectMemoryDraft.SAVED_FIELDS, saved.size)
        val restored = ProjectMemoryDraftsSaver.restore(saved)!!.toMap()
        assertEquals(drafts.toMap(), restored)
        assertTrue(restored.getValue("p1").baseSnapshot != null)
        assertTrue(restored.getValue("p2").legacyModelsNeedReview)
        assertFalse("v2 no reinterpreta comas", ProjectMemoryDraft.fromSaved(restored.getValue("p1").copy(models = "a, b").toSaved())!!.legacyModelsNeedReview)
    }

    /** Exactly what v2 drafts stored before per-field digests: the six fields as raw JSON. */
    private fun rawV2Snapshot(p: Project): String = buildJsonArray {
        add(JsonPrimitive(p.description.orEmpty().trim()))
        add(JsonPrimitive(p.memory.synopsis.orEmpty().trim())); add(JsonPrimitive(p.memory.tone.orEmpty().trim()))
        add(JsonPrimitive(p.memory.visualStyle.orEmpty().trim()))
        add(JsonArray(p.memory.rules.map { JsonPrimitive(it.trim()) }))
        add(JsonArray(p.memory.preferredModels.map { JsonPrimitive(it.trim()) }))
    }.toString()

    @Test fun reviewKeepsUserToneAndAgentSynopsis() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA fusión", "Original")
        val draft = ProjectMemoryDraft.from(base).copy(tone = "épico")
        val agent = index.updateProjectMemory(base.id) { it.copy(synopsis = "Del agente") }
        assertTrue(draft.isStale(agent))
        val plan = draft.reviewPlan(agent)
        assertTrue(plan.fieldMergeAvailable)
        assertEquals(listOf(ProjectMemoryPart.SYNOPSIS), plan.keptCurrent)
        assertEquals("Solo se compara lo que de verdad se sobrescribe", listOf(ProjectMemoryPart.TONE), plan.overwritten)
        assertEquals(draft.afterReview(agent), plan.merged)
        assertEquals("afterReview no escribe", agent, index.findProject(base.id))
        val saved = persistProjectMemoryDraft(index, plan.merged)
        assertEquals("épico", saved.memory.tone)
        assertEquals("Del agente", saved.memory.synopsis)
        assertEquals("Original", saved.description)
        assertTrue(plan.merged.copy(saveRequested = true).saveConfirmed(saved))
    }

    @Test fun sameFieldChangedByBothKeepsDraftAfterAcceptingAndNextChangeBlocksAgain() = runBlocking {
        var tick = 0L
        val index = AssetIndex(null) { ++tick }
        val base = index.createProject("QA ambos", "Original")
        val draft = ProjectMemoryDraft.from(base).copy(synopsis = "Mía")
        val agent = index.updateProjectMemory(base.id) { it.copy(synopsis = "Del agente") }
        assertTrue(draft.prepareSave(agent) is ProjectMemorySaveDecision.Blocked)
        val plan = draft.reviewPlan(agent)
        assertEquals(listOf(ProjectMemoryPart.SYNOPSIS), plan.overwritten)
        assertTrue(plan.keptCurrent.isEmpty())
        assertEquals("Mía", plan.merged.synopsis)
        val second = index.updateProjectMemory(base.id) { it.copy(tone = "Tono del agente") }
        assertTrue("Un segundo cambio vuelve a bloquear", plan.merged.isStale(second))
        assertTrue(runCatching { persistProjectMemoryDraft(index, plan.merged) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(second, index.findProject(base.id))
        val again = plan.merged.reviewPlan(second)
        assertEquals(listOf(ProjectMemoryPart.TONE), again.keptCurrent)
        val saved = persistProjectMemoryDraft(index, again.merged)
        assertEquals("El borrador gana tras aceptar", "Mía", saved.memory.synopsis)
        assertEquals("Tono del agente", saved.memory.tone)
    }

    @Test fun rawV2SnapshotRestoredFromSavedStateStillMergesPerField() {
        val p = project()
        val raw = rawV2Snapshot(p)
        assertEquals(ProjectMemoryDraft.fieldDigests(p), ProjectMemoryDraft.baseDigests(raw))
        val draft = ProjectMemoryDraft.from(p).copy(tone = "épico", baseSnapshot = raw)
        val restored = ProjectMemoryDraft.fromSaved(draft.toSaved())!!
        assertEquals(draft, restored)
        assertFalse(restored.isStale(p))
        assertEquals(ProjectMemoryPart.entries.toSet() - ProjectMemoryPart.TONE, restored.untouchedFields())
        val agent = p.copy(memory = p.memory.copy(synopsis = "Del agente", updatedAt = 20))
        assertTrue(restored.isStale(agent))
        val plan = restored.reviewPlan(agent)
        assertTrue(plan.fieldMergeAvailable)
        assertEquals(listOf(ProjectMemoryPart.SYNOPSIS), plan.keptCurrent)
        assertEquals("Del agente", plan.merged.synopsis)
        assertEquals("épico", plan.merged.tone)
        assertTrue(plan.merged.baseSnapshot!!.contains(ProjectMemoryDraft.DIGEST_FORMAT))
        assertFalse(plan.merged.isStale(agent))
    }

    @Test fun digestSnapshotDoesNotDuplicateMemoryInSavedState() {
        val rules = (1..AssetIndex.MAX_RULES).map { "R$it ".padEnd(AssetIndex.MAX_RULE_LENGTH, 'x') }
        val models = (1..AssetIndex.MAX_PREFERRED_MODELS).map { "Modelo $it, variante ".padEnd(AssetIndex.MAX_MODEL_LENGTH, 'm') }
        val full = project(rules = rules, models = models)
        val saved = ProjectMemoryDraft.from(full).toSaved()
        val snapshot = saved[10]
        assertTrue(snapshot.contains(ProjectMemoryDraft.DIGEST_FORMAT))
        assertTrue("Tamaño fijo: ${snapshot.length}", snapshot.length < 600)
        assertEquals(ProjectMemoryDraft.from(project()).toSaved()[10].length, snapshot.length)
        assertFalse(rules.any { it in snapshot } || models.any { it in snapshot } || "Sinopsis" in snapshot)
        assertTrue(saved.sumOf { it.length } < ProjectMemoryDraft.RULES_LIMIT + ProjectMemoryDraft.MODELS_LIMIT + 1_000)
    }

    @Test fun legacyTenFieldDraftWithoutBaseKeepsEveryFieldAndStillWarns() {
        val legacy = ProjectMemoryDraft.fromSaved(listOf("a", "Desc", "Sin", "tono", "35 mm", "Regla", "Seedance 2.0, Kling", "", "10", "false"))!!
        assertNull(legacy.baseSnapshot)
        assertTrue(legacy.untouchedFields().isEmpty())
        val current = project("a", updatedAt = 20).let { it.copy(memory = it.memory.copy(synopsis = "Del agente")) }
        assertTrue(legacy.isStale(current))
        val plan = legacy.reviewPlan(current)
        assertFalse(plan.fieldMergeAvailable)
        assertTrue(plan.keptCurrent.isEmpty())
        assertEquals(listOf(legacy.description, legacy.synopsis, legacy.tone, legacy.style, legacy.rules, legacy.models),
            listOf(plan.merged.description, plan.merged.synopsis, plan.merged.tone, plan.merged.style, plan.merged.rules, plan.merged.models))
        assertTrue(ProjectMemoryPart.SYNOPSIS in plan.overwritten)
        assertTrue(plan.merged.legacyModelsNeedReview)
        assertTrue((plan.merged.prepareSave(current) as ProjectMemorySaveDecision.Blocked).message.contains("Revisa los modelos"))
    }

    @Test fun unreadableBaseSnapshotIsTreatedAsStale() {
        val p = project()
        val digests = ProjectMemoryDraft.fieldDigests(p).joinToString(",") { "\"$it\"" }
        listOf("no es json", "{\"format\":\"otro\",\"sha256\":[$digests]}",
            "{\"format\":\"${ProjectMemoryDraft.DIGEST_FORMAT}\",\"sha256\":[\"a\"]}", "[\"Drama\"]").forEach { bad ->
            val draft = ProjectMemoryDraft.from(p).copy(tone = "épico", baseSnapshot = bad)
            assertNull(bad, ProjectMemoryDraft.baseDigests(bad))
            assertTrue(bad, draft.isStale(p))
            assertTrue(bad, draft.untouchedFields().isEmpty())
            assertFalse(bad, draft.reviewPlan(p).fieldMergeAvailable)
        }
        assertFalse(ProjectMemoryDraft.from(p).copy(tone = "épico").isStale(p))
    }

    @Test fun errorNoticeShowsCauseAndKnownStateWithoutClaimingDelivery() {
        val retry = agentErrorNoticeContent("java.net.UnknownHostException: Unable to resolve host bedrock", canRetry = true)
        assertEquals("No hay conexión. Comprueba la red y vuelve a intentarlo.", retry.cause)
        assertTrue(retry.technical.contains("UnknownHostException"))
        val noRetry = agentErrorNoticeContent("algo raro", canRetry = false)
        assertEquals("El agente no ha podido completar esta respuesta.", noRetry.cause)
        listOf(retry, noRetry).forEach { c ->
            val visible = (c.cause + " " + c.status).lowercase()
            listOf("enviad", "no se envió", "no llegó", "recibid", "entregad").forEach { assertFalse("«$it» en «$visible»", it in visible) }
        }
        assertFalse(retry.status == noRetry.status)
    }

    @Test fun reasoningModesKeepPreviousMapping() {
        val standard = agentReasoningModes(antigravity = false)
        assertEquals(listOf("Auto", "Rápido", "Mínimo", "Bajo", "Medio", "Alto"), standard.map { it.name })
        assertEquals("Rápido", agentReasoningCurrent(standard, null, "disabled"))
        assertEquals("Medio", agentReasoningCurrent(standard, "medium", null))
        assertEquals("Auto", agentReasoningCurrent(standard, "unknown", null))
        val antigravity = agentReasoningModes(antigravity = true)
        assertEquals(listOf("Bajo", "Medio", "Alto"), antigravity.map { it.name })
        assertEquals("Auto", agentReasoningCurrent(antigravity, null, null))
    }

    @Test fun brainLabelIsTheFullNameWithoutShortening() {
        val options = listOf("direct" to "Bedrock", "openai" to "Compatible OpenAI", "brain:x" to "Mi cerebro propio largo")
        assertEquals("Compatible OpenAI", agentBrainLabel("openai", options))
        assertEquals("Mi cerebro propio largo", agentBrainLabel("brain:x", options))
        assertEquals("Cerebro", agentBrainLabel("missing", options))
    }
}
