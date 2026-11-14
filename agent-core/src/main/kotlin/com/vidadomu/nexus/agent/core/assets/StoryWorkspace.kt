package com.vidadomu.nexus.agent.core.assets

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Narrative records share AssetIndex's transaction, file, identity and change stream. */
@Serializable data class StoryBlock(
    val id: String, val projectId: String, val sceneId: String, val kind: String = "action",
    val text: String = "", val speaker: String? = null, val shotId: String? = null,
    val order: Int = 0, val narrativeState: String = "story", val revision: Long = 0, val contentRevision: Long = 0,
)
@Serializable data class StoryLink(
    val id: String, val projectId: String, val assetId: String, val scope: String, val targetId: String,
    val role: String = "reference", val selected: Boolean = false, val behavior: String = "complement",
    val characterId: String? = null, val note: String? = null, val revision: Long = 0,
    val context: StoryGenerationContext? = null, val createdAt: Long = 0,
)
@Serializable data class StoryElement(
    val id: String, val projectId: String, val kind: String, val name: String,
    val description: String? = null, val sceneId: String? = null, val characterId: String? = null,
    val fields: JsonObject = JsonObject(emptyMap()), val revision: Long = 0,
)
@Serializable data class StoryPreference(
    val id: String, val projectId: String, val scope: String, val targetId: String,
    val text: String, val revision: Long = 0, val enabled: Boolean = true,
)
@Serializable data class StoryChange(val id: String, val command: JsonObject)
@Serializable data class StoryProposal(
    val id: String, val projectId: String, val title: String, val changes: List<StoryChange>,
    val scopeSceneId: String? = null, val scopeShotId: String? = null, val scopeBlockId: String? = null,
    val preserveIds: List<String> = emptyList(), val appliedIds: List<String> = emptyList(),
    val status: String = "pending", val actor: String, val createdAt: Long, val revision: Long = 0,
)
@Serializable data class StoryTask(
    val id: String, val projectId: String, val title: String, val kind: String = "preparation",
    val sceneId: String? = null, val shotId: String? = null, val status: String = "queued",
    val detail: String? = null, val context: StoryGenerationContext? = null,
    val providerJobId: String? = null, val generationId: String? = null,
    val executor: String? = null, val startedAt: Long? = null, val updatedAt: Long,
    val revision: Long = 0,
)
@Serializable data class StoryAutonomy(
    val projectId: String, val enabled: Boolean = false, val mode: String = "proposals",
    val sceneId: String? = null, val revision: Long = 0,
)
@Serializable data class StoryReceipt(val operationId: String, val projectId: String, val requestHash: String, val result: JsonObject)
@Serializable data class StoryHistory(
    val id: String, val projectId: String, val action: String, val actor: String, val at: Long,
    val before: JsonObject, val after: JsonObject, val undone: Boolean = false,
)
@Serializable data class StoryState(
    val blocks: List<StoryBlock> = emptyList(), val links: List<StoryLink> = emptyList(),
    val elements: List<StoryElement> = emptyList(), val preferences: List<StoryPreference> = emptyList(),
    val proposals: List<StoryProposal> = emptyList(), val tasks: List<StoryTask> = emptyList(),
    val autonomy: List<StoryAutonomy> = emptyList(), val receipts: List<StoryReceipt> = emptyList(),
    val history: List<StoryHistory> = emptyList(),
    /** Compatibility adoption is once per project; removing a new association never resurrects it. */
    val legacyAdoptedProjectIds: List<String> = emptyList(),
)

private val storyJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
private val referenceRoles = setOf("reference", "identity", "costume", "location", "composition", "lighting", "sketch")
private val materialRoles = referenceRoles + setOf("generated", "candidate", "audio", "chosen", "discarded", "output", "final", "source")
private val kinds = setOf("action", "dialogue", "note", "reference", "shot", "material")
private val narrativeStates = setOf("idea", "story", "incorporated")
private val productionStates = setOf("pending", "prepared", "generated", "chosen")
private class StoryFailure(val code: String, message: String) : IllegalArgumentException(message)
private fun invalid(message: String): Nothing = throw StoryFailure("invalid", message)
private fun conflict(message: String): Nothing = throw StoryFailure("revision_conflict", message)
private fun JsonObject.s(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.need(key: String) = s(key)?.takeIf { it.isNotBlank() } ?: invalid("Falta «$key».")
private fun JsonObject.l(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
private fun JsonObject.i(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
private fun JsonObject.b(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.d(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.strings(key: String) = (get(key) as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull ?: invalid("«$key» debe contener identificadores.") }
private fun JsonObject.optional(key: String, current: String?) = if (key in this) s(key)?.takeIf { it.isNotBlank() } else current
private fun sameOptional(a: String?, b: String?) = a?.takeIf { it.isNotBlank() } == b?.takeIf { it.isNotBlank() }
private fun rev(input: JsonObject, current: Long) {
    val expected = input.l("expected_revision") ?: invalid("Falta expected_revision; vuelve a leer el elemento antes de editarlo.")
    if (expected != current) conflict("El elemento ha cambiado (revisión $current; se esperaba $expected). Tu edición reciente se conserva.")
}
private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
private fun canonical(value: JsonElement): String = when (value) {
    is JsonObject -> value.toSortedMap().entries.joinToString(prefix = "{", postfix = "}") { JsonPrimitive(it.key).toString() + ":" + canonical(it.value) }
    is JsonArray -> value.joinToString(prefix = "[", postfix = "]") { canonical(it) }
    else -> value.toString()
}
private fun id(prefix: String) = AssetIndex.newId(prefix)
private fun creationId(d: IndexData, q: JsonObject, prefix: String, field: String = "id"): String {
    val value = q.s(field) ?: return id(prefix)
    if (!value.matches(Regex("[A-Za-z0-9_-]{3,100}"))) invalid("El identificador estable contiene caracteres no admitidos.")
    if (value in (d.projects.map { it.id } + d.scenes.map { it.id } + d.shots.map { it.id } + d.characters.map { it.id } + d.assets.map { it.id } + d.generations.map { it.id } + d.story.blocks.map { it.id } + d.story.elements.map { it.id } + d.story.links.map { it.id } + d.story.preferences.map { it.id } + d.story.tasks.map { it.id })) invalid("El identificador estable ya existe.")
    return value
}
private fun result(entityId: String, revision: Long = 0, extra: Map<String, JsonElement> = emptyMap()) = buildJsonObject {
    put("ok", true); put("id", entityId); put("revision", revision); extra.forEach { (k, v) -> put(k, v) }
}
private fun scene(d: IndexData, p: String, value: String) = d.scenes.firstOrNull { it.id == value && it.projectId == p } ?: invalid("La escena no pertenece al proyecto.")
private fun shot(d: IndexData, p: String, value: String) = d.shots.firstOrNull { it.id == value && it.projectId == p } ?: invalid("El plano no pertenece al proyecto.")
private fun block(d: IndexData, p: String, value: String) = d.story.blocks.firstOrNull { it.id == value && it.projectId == p } ?: invalid("El bloque no pertenece al proyecto.")
private fun project(d: IndexData, p: String) = d.projects.firstOrNull { it.id == p && !it.archived } ?: invalid("Proyecto desconocido o archivado.")
private fun character(d: IndexData, p: String, value: String) = d.characters.firstOrNull { it.id == value && it.projectId == p } ?: invalid("El personaje no pertenece al proyecto.")
private fun checkTarget(d: IndexData, p: String, scope: String, target: String) {
    when (scope) {
        "project" -> if (target != p) invalid("El vínculo debe pertenecer al proyecto activo.")
        "scene" -> scene(d, p, target)
        "shot" -> shot(d, p, target)
        "block" -> block(d, p, target)
        "character" -> character(d, p, target)
        "element" -> if (d.story.elements.none { it.id == target && it.projectId == p }) invalid("Elemento desconocido.")
        else -> invalid("Alcance desconocido.")
    }
}

/** One serialized transaction, including proposal batches, receipts and undo. Errors never persist a partial edit. */
suspend fun AssetIndex.storyCommand(projectId: String, input: JsonObject, actor: String = "user"): JsonObject = try {
    if (input.s("action") == "read") storyView(projectId, input.s("scene_id"), input.s("shot_id"))
    else edit { before ->
        project(before, projectId)
        val operation = input.need("operation_id")
        val hash = sha(canonical(input))
        val previous = before.story.receipts.firstOrNull { it.operationId == operation && it.projectId == projectId }
        if (previous != null) {
            if (previous.requestHash != hash) invalid("operation_id ya se utilizó para una operación distinta.")
            before to previous.result
        } else {
            val action = input.need("action")
            val (changed, output) = mutate(before, projectId, input, actor, System.currentTimeMillis())
            val oldDocument = document(before, projectId)
            val newDocument = document(changed, projectId)
            val delta = documentDelta(oldDocument, newDocument)
            val history = if (action !in setOf("undo", "adopt_legacy_links") && oldDocument != newDocument) StoryHistory(id("edit"), projectId, action, actor, System.currentTimeMillis(), delta.first, delta.second) else null
            val answer = JsonObject(output + mapOf("operation_id" to JsonPrimitive(operation)) + (history?.let { mapOf("history_id" to JsonPrimitive(it.id)) } ?: emptyMap()))
            changed.copy(story = changed.story.copy(
                receipts = changed.story.receipts + StoryReceipt(operation, projectId, hash, answer),
                history = changed.story.history + listOfNotNull(history),
            )) to answer
        }
    }
} catch (e: StoryFailure) { buildJsonObject { put("ok", false); put("code", e.code); put("error", e.message) } }

/** Scope restrictions are checked for every nested proposal command under the same lock. */
private fun validateScope(d: IndexData, p: String, q: JsonObject) {
    val scopeScene = q.s("scope_scene_id")?.also { scene(d, p, it) }
    val scopeShot = q.s("scope_shot_id")?.also { shot(d, p, it) }
    val scopeBlock = q.s("scope_block_id")?.also { block(d, p, it) }
    val keep = q.strings("preserve_ids").orEmpty().toSet()
    val action = q.need("action")
    val existingLink = q.s("link_id")?.let { value -> d.story.links.firstOrNull { it.id == value && it.projectId == p } ?: invalid("Asociación desconocida.") }
    val existingPreference = q.s("preference_id")?.let { value -> d.story.preferences.firstOrNull { it.id == value && it.projectId == p } ?: invalid("Indicación desconocida.") }
    val targetShot = q.s("shot_id")?.let { value -> d.shots.firstOrNull { it.id == value && it.projectId == p } }
    val targetBlock = q.s("block_id")?.let { value -> d.story.blocks.firstOrNull { it.id == value && it.projectId == p } }
    val originBlock = (q.s("origin_block_id") ?: q.s("block_id").takeIf { action == "create_shot" })?.let { block(d, p, it) }
    val targetScene = q.s("scene_id") ?: targetShot?.sceneId ?: targetBlock?.sceneId ?: originBlock?.sceneId
    val identity = when {
        action.endsWith("_shot") -> q.s("shot_id")
        action.endsWith("_scene") -> q.s("scene_id")
        action.endsWith("_block") -> q.s("block_id")
        action == "upsert_element" -> q.s("element_id")
        action == "unlink_material" -> q.s("link_id")
        else -> null
    }
    if (identity in keep) invalid("El elemento está fijado por tu orden; se conserva.")
    if (existingLink?.targetId in keep || existingPreference?.targetId in keep || existingPreference?.id in keep) invalid("El elemento está fijado por tu orden; se conservan sus asociaciones e indicaciones.")
    if (action in setOf("link_material", "set_preference") && q.s("target_id") in keep) invalid("El elemento está fijado por tu orden; no se modifican sus vínculos o indicaciones.")
    if (action == "delete_scene" && (d.shots.any { it.sceneId == targetScene && it.id in keep } || d.story.blocks.any { it.sceneId == targetScene && it.id in keep })) invalid("La escena contiene elementos fijados.")
    val administrative = action in setOf("propose", "apply_proposal", "reject_proposal", "enqueue_task", "update_task", "prepare_generation", "register_result")
    if (!administrative && (scopeScene != null || scopeShot != null || scopeBlock != null)) {
        if (scopeScene != null && ((targetShot?.sceneId != null && targetShot.sceneId != scopeScene) || (targetBlock != null && targetBlock.sceneId != scopeScene))) invalid("El origen está fuera de la escena autorizada.")
        if (scopeShot != null && targetShot != null && targetShot.id != scopeShot) invalid("El origen está fuera del plano autorizado.")
        if (scopeBlock != null && targetBlock != null && targetBlock.id != scopeBlock) invalid("El origen está fuera del bloque autorizado.")
        if (action in setOf("update_project", "create_scene", "set_autonomy", "undo")) invalid("La orden está limitada a un fragmento; esta operación afecta al proyecto.")
        if (action == "upsert_element") invalid("No se modifican personajes o elementos generales desde un alcance limitado.")
        var sceneId = targetScene
        var shotId = targetShot?.id ?: targetBlock?.shotId
        var blockId = targetBlock?.id ?: originBlock?.id
        if (action in setOf("link_material", "unlink_material", "set_preference")) {
            val scope = existingLink?.scope ?: existingPreference?.scope ?: q.s("scope") ?: "project"
            val target = existingLink?.targetId ?: existingPreference?.targetId ?: q.s("target_id") ?: p
            when (scope) {
                "scene" -> sceneId = target
                "shot" -> { shotId = target; sceneId = shot(d, p, target).sceneId }
                "block" -> { val b = block(d, p, target); blockId = b.id; sceneId = b.sceneId; shotId = b.shotId }
                else -> invalid("El vínculo está fuera del fragmento autorizado.")
            }
        }
        if (scopeScene != null && sceneId != scopeScene) invalid("La operación está fuera de la escena autorizada.")
        if (scopeShot != null && shotId != scopeShot && !(action == "create_block" && q.s("shot_id") == scopeShot)) invalid("La operación está fuera del plano autorizado.")
        if (scopeBlock != null && blockId != scopeBlock) invalid("La operación está fuera del bloque autorizado.")
    }
}

private fun mutate(d: IndexData, p: String, q: JsonObject, actor: String, now: Long): Pair<IndexData, JsonObject> {
    project(d, p); validateScope(d, p, q)
    val st = d.story
    return when (q.need("action")) {
        "adopt_legacy_links" -> {
            if (p in st.legacyAdoptedProjectIds) d to result(p, extra = mapOf("adopted_count" to JsonPrimitive(0)))
            else {
                val additions = mutableListOf<StoryLink>()
                fun adopt(assetId: String, scope: String, targetId: String, role: String, selected: Boolean = false, characterId: String? = null) {
                    if (d.assets.none { it.id == assetId && !it.libraryDeleted }) return
                    if ((st.links + additions).any { it.projectId == p && it.assetId == assetId && it.scope == scope && it.targetId == targetId && it.role == role && it.characterId == characterId }) return
                    additions += StoryLink(id("lnk"), p, assetId, scope, targetId, role, selected, characterId = characterId, note = "Asociación existente conservada", createdAt = now)
                }
                for (asset in d.assets.filter { !it.libraryDeleted }) {
                    val shot = d.shots.firstOrNull { it.id == asset.shotId && it.projectId == p }
                    val scene = d.scenes.firstOrNull { it.id == asset.sceneId && it.projectId == p }
                    val role = when (asset.role) { AssetRole.REFERENCE -> "reference"; AssetRole.FINAL -> "chosen"; AssetRole.KEYFRAME -> "sketch"; AssetRole.OUTPUT -> "candidate"; AssetRole.SOURCE -> "source"; AssetRole.OTHER -> if (asset.type == AssetType.AUDIO) "audio" else "candidate" }
                    if (shot != null) adopt(asset.id, "shot", shot.id, role, asset.role == AssetRole.FINAL)
                    else if (scene != null) adopt(asset.id, "scene", scene.id, role, asset.role == AssetRole.FINAL)
                    // Project ownership alone is an inbox, not an inferred narrative use.
                    else if (asset.projectId == p && asset.role == AssetRole.REFERENCE) adopt(asset.id, "project", p, "reference")
                    for (character in d.characters.filter { it.projectId == p && it.id in asset.characterIds }) adopt(asset.id, "character", character.id, "reference", characterId = character.id)
                }
                for (character in d.characters.filter { it.projectId == p }) for (asset in character.referenceAssetIds) adopt(asset, "character", character.id, "identity", characterId = character.id)
                d.copy(story = st.copy(links = st.links + additions, legacyAdoptedProjectIds = st.legacyAdoptedProjectIds + p)) to result(p, extra = mapOf("adopted_count" to JsonPrimitive(additions.size)))
            }
        }
        "update_project" -> {
            val old = project(d, p); rev(q, old.revision)
            val memory = old.memory.copy(synopsis = q.optional("synopsis", old.memory.synopsis), tone = q.optional("tone", old.memory.tone),
                visualStyle = q.optional("visual_style", old.memory.visualStyle), updatedAt = now)
            val next = old.copy(name = q.s("title")?.takeIf { it.isNotBlank() } ?: old.name, description = q.optional("description", old.description),
                memory = memory, treatment = q.optional("treatment", old.treatment), format = q.optional("format", old.format),
                targetDurationSec = if ("target_duration_sec" in q) q.d("target_duration_sec")?.also { if (it <= 0 || !it.isFinite()) invalid("Duración inválida.") } else old.targetDurationSec,
                revision = old.revision + 1)
            d.copy(projects = d.projects.map { if (it.id == p) next else it }) to result(p, next.revision)
        }
        "import_script" -> {
            val text = q.need("text")
            val existing = q.s("scene_id")?.let { scene(d, p, it) }
            val parent = existing ?: Scene(creationId(d, q, "scn"), p, "E${d.scenes.count { it.projectId == p } + 1}", q.s("title") ?: "Guion importado",
                order = (d.scenes.filter { it.projectId == p }.maxOfOrNull { it.order } ?: -1) + 1)
            // Literal paragraphs, with no screenplay syntax or guessed scene boundaries imposed.
            val start = (st.blocks.filter { it.sceneId == parent.id }.maxOfOrNull { it.order } ?: -1) + 1
            val paragraphs = text.split(Regex("\\r?\\n[\\t ]*\\r?\\n"))
            val stableBlocks = q.strings("block_ids")?.also { if (it.size != paragraphs.size || it.distinct().size != it.size) invalid("Los identificadores de los párrafos no coinciden con el guion.") }
            val blocks = paragraphs.mapIndexed { index, paragraph -> StoryBlock(stableBlocks?.get(index)?.let { creationId(d, buildJsonObject { put("id", it) }, "blk") } ?: id("blk"), p, parent.id, "action", text = paragraph, order = start + index) }
            d.copy(scenes = if (existing == null) d.scenes + parent else d.scenes,
                story = st.copy(blocks = st.blocks + blocks)) to result(parent.id, parent.revision, mapOf("blocks_imported" to JsonPrimitive(blocks.size)))
        }
        "create_scene" -> {
            val next = Scene(creationId(d, q, "scn"), p, q.s("code") ?: "E${d.scenes.count { it.projectId == p } + 1}", q.s("title"),
                order = (d.scenes.filter { it.projectId == p }.maxOfOrNull { it.order } ?: -1) + 1, group = q.s("group"), description = q.s("description"))
            d.copy(scenes = d.scenes + next) to result(next.id)
        }
        "update_scene", "move_scene", "delete_scene" -> {
            val old = scene(d, p, q.need("scene_id")); rev(q, old.revision)
            if (q.s("action") == "delete_scene") {
                val removedShots = d.shots.filter { it.sceneId == old.id }.map { it.id }.toSet()
                val removedBlocks = st.blocks.filter { it.sceneId == old.id }.map { it.id }.toSet()
                d.copy(scenes = d.scenes.filterNot { it.id == old.id }, shots = d.shots.filterNot { it.id in removedShots }, story = st.copy(
                    blocks = st.blocks.filterNot { it.id in removedBlocks },
                    links = st.links.filterNot { (it.scope == "scene" && it.targetId == old.id) || (it.scope == "shot" && it.targetId in removedShots) || (it.scope == "block" && it.targetId in removedBlocks) },
                )) to result(old.id, old.revision + 1)
            } else {
                val next = old.copy(title = q.optional("title", old.title), code = q.s("code") ?: old.code, description = q.optional("description", old.description),
                    group = q.optional("group", old.group), order = q.i("order") ?: old.order,
                    timeOfDay = q.optional("time_of_day", old.timeOfDay), light = q.optional("light", old.light),
                    locationIds = q.strings("location_ids")?.also { ids -> ids.forEach { checkTarget(d, p, "element", it) } } ?: old.locationIds,
                    revision = old.revision + 1).let { candidate ->
                    val changed = !sameOptional(candidate.description, old.description) || !sameOptional(candidate.timeOfDay, old.timeOfDay) || !sameOptional(candidate.light, old.light) || candidate.locationIds.toSet() != old.locationIds.toSet()
                    candidate.copy(contentRevision = old.contentRevision + if (q.b("invalidate_material") != false && changed) 1 else 0)
                }
                val others = if (q.s("action") == "move_scene") reorder(d.scenes.filter { it.projectId == p }, old.id, next.order, { it.id }, { it.order }).associate { it.first.id to it.second } else emptyMap()
                d.copy(scenes = d.scenes.map { if (it.id == old.id) next.copy(order = others[it.id] ?: next.order) else if (it.id in others) it.copy(order = others.getValue(it.id)) else it }) to result(next.id, next.revision)
            }
        }
        "create_block" -> {
            val parent = scene(d, p, q.need("scene_id")); val kind = q.s("kind") ?: "action"
            if (kind !in kinds) invalid("Tipo de bloque desconocido.")
            val shotId = q.s("shot_id")?.also { if (shot(d, p, it).sceneId != parent.id) invalid("El plano debe estar en la misma escena.") }
            val next = StoryBlock(creationId(d, q, "blk"), p, parent.id, kind, q.s("text") ?: "", q.s("speaker"), shotId,
                q.i("order") ?: ((st.blocks.filter { it.sceneId == parent.id }.maxOfOrNull { it.order } ?: -1) + 1),
                q.s("narrative_state")?.also { if (it !in narrativeStates) invalid("Estado narrativo desconocido.") } ?: if (kind == "note") "idea" else "story")
            d.copy(story = st.copy(blocks = st.blocks + next)) to result(next.id)
        }
        "update_block", "move_block", "delete_block" -> {
            val old = block(d, p, q.need("block_id")); rev(q, old.revision)
            if (q.s("action") == "delete_block") d.copy(story = st.copy(blocks = st.blocks.filterNot { it.id == old.id }, links = st.links.filterNot { it.scope == "block" && it.targetId == old.id })) to result(old.id, old.revision + 1)
            else {
                val destination = q.s("scene_id")?.also { scene(d, p, it) } ?: old.sceneId
                val kind = q.s("kind")?.also { if (it !in kinds) invalid("Tipo de bloque desconocido.") } ?: old.kind
                val attachedShot = if ("shot_id" in q) q.s("shot_id") else old.shotId
                attachedShot?.let { if (shot(d, p, it).sceneId != destination) invalid("Mueve primero el plano, o desvincúlalo antes de mover el bloque.") }
                val next = old.copy(sceneId = destination, kind = kind, text = q.s("text") ?: old.text, speaker = q.optional("speaker", old.speaker), shotId = attachedShot,
                    order = q.i("order") ?: old.order, narrativeState = q.s("narrative_state")?.also { if (it !in narrativeStates) invalid("Estado narrativo desconocido.") } ?: old.narrativeState,
                    revision = old.revision + 1).let { candidate ->
                    val changed = candidate.kind != old.kind || candidate.text != old.text || !sameOptional(candidate.speaker, old.speaker) || candidate.shotId != old.shotId || candidate.narrativeState != old.narrativeState
                    candidate.copy(contentRevision = old.contentRevision + if (q.b("invalidate_material") != false && changed) 1 else 0)
                }
                val positions = if (q.s("action") == "move_block") reorder(st.blocks.filter { it.sceneId == destination } + listOfNotNull(old.takeIf { it.sceneId != destination }), old.id, next.order, { it.id }, { it.order }).associate { it.first.id to it.second } else emptyMap()
                d.copy(story = st.copy(blocks = st.blocks.map { if (it.id == old.id) next.copy(order = positions[it.id] ?: next.order) else if (it.id in positions) it.copy(order = positions.getValue(it.id)) else it })) to result(next.id, next.revision)
            }
        }
        "create_shot" -> {
            val parent = (q.s("scene_id") ?: (q.s("origin_block_id") ?: q.s("block_id"))?.let { block(d, p, it).sceneId })?.also { scene(d, p, it) }
            val next = patchShot(d, p, Shot(creationId(d, q, "sht"), p, parent, q.s("code") ?: "P${d.shots.count { it.projectId == p } + 1}", order = (d.shots.filter { it.projectId == p && it.sceneId == parent }.maxOfOrNull { it.order } ?: -1) + 1), q, creating = true)
            val b = parent?.let { StoryBlock(creationId(d, q, "blk", "shot_block_id"), p, it, "shot", shotId = next.id, order = q.i("block_order") ?: ((st.blocks.filter { b -> b.sceneId == it }.maxOfOrNull { b -> b.order } ?: -1) + 1)) }
            val origin = q.s("origin_block_id") ?: q.s("block_id")
            val originals = st.blocks.map { original -> if (original.id == origin && original.projectId == p) original.copy(shotId = next.id, revision = original.revision + 1) else original }
            d.copy(shots = d.shots + next, story = st.copy(blocks = originals + listOfNotNull(b))) to result(next.id)
        }
        "update_shot", "move_shot", "delete_shot", "duplicate_shot" -> {
            val old = shot(d, p, q.need("shot_id")); rev(q, old.revision)
            when (q.s("action")) {
                "delete_shot" -> d.copy(shots = d.shots.filterNot { it.id == old.id }, story = st.copy(
                    blocks = st.blocks.mapNotNull { if (it.shotId == old.id) { if (it.kind == "shot" && it.text.isBlank()) null else it.copy(shotId = null, kind = if (it.kind == "shot") "note" else it.kind, revision = it.revision + 1) } else it },
                    links = st.links.filterNot { it.scope == "shot" && it.targetId == old.id || it.scope == "block" && st.blocks.any { b -> b.id == it.targetId && b.shotId == old.id && b.kind == "shot" && b.text.isBlank() } },
                )) to result(old.id, old.revision + 1)
                "duplicate_shot" -> {
                    val copy = old.copy(id = creationId(d, q, "sht"), code = q.s("code") ?: "${old.code} · variante", title = q.s("title") ?: old.title,
                        order = (d.shots.filter { it.sceneId == old.sceneId && it.projectId == p }.maxOfOrNull { it.order } ?: -1) + 1,
                        variantOf = old.variantOf ?: old.id, productionState = "pending", revision = 0, contentRevision = 0)
                    val b = old.sceneId?.let { StoryBlock(creationId(d, q, "blk", "shot_block_id"), p, it, "shot", shotId = copy.id, order = (st.blocks.filter { b -> b.sceneId == it }.maxOfOrNull { b -> b.order } ?: -1) + 1) }
                    val references = st.links.filter { it.scope == "shot" && it.targetId == old.id && it.role in referenceRoles }.sortedBy { it.id }.map { it.copy(id = "lnk_" + sha(copy.id + ":" + it.id).take(12), targetId = copy.id, selected = false, revision = 0) }
                    d.copy(shots = d.shots + copy, story = st.copy(blocks = st.blocks + listOfNotNull(b), links = st.links + references)) to result(copy.id)
                }
                else -> {
                    val next = patchShot(d, p, old, q)
                    val positions = if (q.s("action") == "move_shot") reorder(d.shots.filter { it.projectId == p && it.sceneId == next.sceneId } + listOfNotNull(old.takeIf { it.sceneId != next.sceneId }), old.id, next.order, { it.id }, { it.order }).associate { it.first.id to it.second } else emptyMap()
                    var movedBlocks = st.blocks.map { if (it.shotId == old.id && next.sceneId != old.sceneId) it.copy(sceneId = next.sceneId ?: invalid("Un plano con bloques necesita escena."), revision = it.revision + 1) else it }
                    if (q.s("action") == "move_shot" && next.sceneId != null) {
                        val shotOrder = positions.entries.sortedBy { it.value }.map { it.key }
                        val shotBlocks = movedBlocks.filter { it.sceneId == next.sceneId && it.kind == "shot" && it.shotId in shotOrder }.sortedBy { shotOrder.indexOf(it.shotId) }
                        val slots = movedBlocks.filter { it.sceneId == next.sceneId && it.kind == "shot" && it.shotId in shotOrder }.map { it.order }.sorted()
                        val orders = shotBlocks.mapIndexed { index, b -> b.id to slots[index] }.toMap()
                        movedBlocks = movedBlocks.map { b -> orders[b.id]?.let { b.copy(order = it) } ?: b }
                    }
                    d.copy(shots = d.shots.map { if (it.id == old.id) next.copy(order = positions[it.id] ?: next.order) else if (it.id in positions) it.copy(order = positions.getValue(it.id)) else it }, story = st.copy(blocks = movedBlocks)) to result(next.id, next.revision)
                }
            }
        }
        "link_material" -> {
            val asset = d.assets.firstOrNull { it.id == q.need("asset_id") && !it.libraryDeleted } ?: invalid("El archivo no está disponible en la biblioteca.")
            val scope = q.s("scope") ?: "project"; val target = q.s("target_id") ?: p; checkTarget(d, p, scope, target)
            val role = q.s("role") ?: "reference"; if (role !in materialRoles) invalid("Papel del material desconocido.")
            val characterId = (q.s("character_id") ?: target.takeIf { scope == "character" })?.also { character(d, p, it) }
            if (role in setOf("identity", "costume") && characterId == null) invalid("Vincula esa referencia a un personaje concreto para evitar mezclar identidades o vestuario.")
            val behavior = q.s("behavior") ?: q.s("mode") ?: "complement"; if (behavior !in setOf("complement", "replace")) invalid("Comportamiento de referencia desconocido.")
            val old = st.links.firstOrNull { it.projectId == p && it.assetId == asset.id && it.scope == scope && it.targetId == target && it.role == role && it.characterId == characterId }
            if (old != null) rev(q, old.revision)
            val next = StoryLink(old?.id ?: creationId(d, q, "lnk"), p, asset.id, scope, target, role,
                q.b("selected") ?: (role in setOf("chosen", "final")), behavior, characterId, q.s("note"), (old?.revision?.plus(1) ?: 0), old?.context, old?.createdAt ?: now)
            val links = st.links.mapNotNull { link ->
                if (link.id == next.id) null else if (next.selected && scope == "shot" && link.projectId == p && link.scope == scope && link.targetId == target && link.selected && d.assets.firstOrNull { it.id == link.assetId }?.type == asset.type) link.copy(selected = false, revision = link.revision + 1) else link
            } + next
            d.copy(story = st.copy(links = links)) to result(next.id, next.revision)
        }
        "unlink_material" -> {
            val old = st.links.firstOrNull { it.id == q.need("link_id") && it.projectId == p } ?: invalid("Asociación desconocida.")
            rev(q, old.revision)
            val remaining = st.links.filterNot { it.id == old.id }
            val hasSameUse = remaining.any { it.projectId == p && it.assetId == old.assetId && it.scope == old.scope && it.targetId == old.targetId }
            val assets = if (!hasSameUse && old.scope in setOf("scene", "shot")) d.assets.map { a -> if (a.id == old.assetId) {
                if (old.scope == "shot" && a.shotId == old.targetId) a.copy(shotId = null)
                else if (old.scope == "scene" && a.sceneId == old.targetId) a.copy(sceneId = null) else a
            } else a } else d.assets
            val characters = if (!hasSameUse && old.scope == "character" && old.role == "identity") d.characters.map { c -> if (c.id == old.targetId && c.projectId == p) c.copy(referenceAssetIds = c.referenceAssetIds - old.assetId, revision = c.revision + 1) else c } else d.characters
            d.copy(assets = assets, characters = characters, story = st.copy(links = remaining)) to result(old.id, old.revision + 1)
        }
        "upsert_element" -> upsertElement(d, p, q)
        "set_preference" -> {
            val old = q.s("preference_id")?.let { value -> st.preferences.firstOrNull { it.id == value && it.projectId == p } ?: invalid("Indicación desconocida.") }
            val scope = q.s("scope") ?: old?.scope ?: "project"; val target = q.s("target_id") ?: old?.targetId ?: p; checkTarget(d, p, scope, target)
            old?.let { rev(q, it.revision) }
            val next = StoryPreference(old?.id ?: creationId(d, q, "pref"), p, scope, target, q.s("text") ?: old?.text ?: invalid("Falta la indicación."), old?.revision?.plus(1) ?: 0, q.b("enabled") ?: old?.enabled ?: true)
            d.copy(story = st.copy(preferences = st.preferences.filterNot { it.id == next.id } + next)) to result(next.id, next.revision)
        }
        "propose" -> {
            val proposalIdentity = id("prop")
            val changes = (q["changes"] as? JsonArray)?.mapIndexed { index, value ->
                var cmd = value as? JsonObject ?: invalid("Cada cambio debe ser una operación estructurada.")
                if (cmd.s("action") in setOf("propose", "apply_proposal", "reject_proposal", "undo", "register_result", "update_task", "set_autonomy")) invalid("Esa operación no puede formar parte de una propuesta editorial.")
                // IDs belong to the proposal, not to the accepting device. Offline acceptance on two
                // peers must create the same entities, including their automatic screenplay blocks.
                cmd = materializeCreation(cmd, "$proposalIdentity:c$index")
                StoryChange("c$index", withScope(cmd, q))
            }?.takeIf { it.isNotEmpty() } ?: invalid("La propuesta no contiene cambios.")
            var trial = d
            for (change in changes) trial = mutate(trial, p, change.command, actor, now).first
            val next = StoryProposal(proposalIdentity, p, q.s("title") ?: "Propuesta del agente", changes, q.s("scope_scene_id"), q.s("scope_shot_id"), q.s("scope_block_id"), q.strings("preserve_ids").orEmpty(), actor = actor, createdAt = now)
            d.copy(story = st.copy(proposals = st.proposals + next)) to result(next.id)
        }
        "apply_proposal", "reject_proposal" -> {
            val old = st.proposals.firstOrNull { it.id == q.need("proposal_id") && it.projectId == p } ?: invalid("Propuesta desconocida.")
            rev(q, old.revision)
            if (old.status !in setOf("pending", "partial")) invalid("La propuesta ya no está pendiente.")
            if (q.s("action") == "reject_proposal") {
                val next = old.copy(status = "rejected", revision = old.revision + 1)
                d.copy(story = st.copy(proposals = st.proposals.map { if (it.id == old.id) next else it })) to result(next.id, next.revision)
            } else {
                val ids = q.strings("change_ids")?.toSet() ?: old.changes.map { it.id }.toSet() - old.appliedIds.toSet()
                if (ids.isEmpty() || ids.any { selected -> old.changes.none { it.id == selected } } || ids.any { it in old.appliedIds }) invalid("Selecciona cambios pendientes de esta propuesta.")
                var nextData = d
                val effectiveScope = buildJsonObject { old.scopeSceneId?.let { put("scope_scene_id", it) }; old.scopeShotId?.let { put("scope_shot_id", it) }; old.scopeBlockId?.let { put("scope_block_id", it) }; put("preserve_ids", JsonArray(old.preserveIds.map(::JsonPrimitive))) }
                for (change in old.changes.filter { it.id in ids }) nextData = mutate(nextData, p, withScope(withScope(materializeCreation(change.command, "${old.id}:${change.id}"), effectiveScope), q), actor, old.createdAt).first
                val applied = (old.appliedIds + ids).distinct()
                val next = old.copy(appliedIds = applied, status = if (applied.size == old.changes.size) "applied" else "partial", revision = old.revision + 1)
                nextData.copy(story = nextData.story.copy(proposals = nextData.story.proposals.map { if (it.id == old.id) next else it })) to result(next.id, next.revision)
            }
        }
        "enqueue_task", "prepare_generation" -> {
            val sceneId = q.s("scene_id")?.also { scene(d, p, it) }
            val shotId = q.s("shot_id")?.also { val s = shot(d, p, it); if (sceneId != null && s.sceneId != sceneId) invalid("El plano no está en la escena indicada.") }
            if (q.s("scope_scene_id") != null && (sceneId ?: shotId?.let { shot(d, p, it).sceneId }) != q.s("scope_scene_id")) invalid("La tarea está fuera de la escena autorizada.")
            if (q.s("scope_shot_id") != null && shotId != q.s("scope_shot_id")) invalid("La tarea está fuera del plano autorizado.")
            val generation = q.s("action") == "prepare_generation"
            if (generation && shotId == null) invalid("Selecciona un plano para preparar la generación.")
            val context = shotId?.let { generationContext(d, p, it) }
            val title = q.s("title") ?: if (generation) "Preparar ${shot(d, p, shotId!!).code}" else "Trabajo del proyecto"
            val kind = if (generation) "generation_preparation" else q.s("kind") ?: "preparation"
            if (kind !in setOf("preparation", "breakdown", "storyboard", "find_material", "associations", "prompts", "variants", "generation_preparation", "production_gaps", "references", "material_index")) invalid("La cola prepara trabajo; no ejecuta acciones de pago.")
            val duplicate = st.tasks.firstOrNull { it.projectId == p && it.sceneId == sceneId && it.shotId == shotId && it.kind == kind && it.title == title && it.status !in setOf("complete", "cancelled", "error") }
            if (duplicate != null) d to result(duplicate.id, duplicate.revision, mapOf("task" to taskJson(duplicate), "context" to (duplicate.context?.let { storyJson.encodeToJsonElement(it) } ?: JsonNull)))
            else {
                val next = StoryTask(creationId(d, q, "task"), p, title, kind, sceneId ?: shotId?.let { shot(d, p, it).sceneId }, shotId, if (generation) "prepared" else "queued", q.s("detail"), context, updatedAt = now)
                d.copy(story = st.copy(tasks = st.tasks + next)) to result(next.id, extra = mapOf("task" to taskJson(next), "context" to (context?.let { storyJson.encodeToJsonElement(it) } ?: JsonNull), "requires_cost_authorization" to JsonPrimitive(true)))
            }
        }
        "update_task" -> {
            val old = st.tasks.firstOrNull { it.id == q.need("task_id") && it.projectId == p } ?: invalid("Tarea desconocida."); rev(q, old.revision)
            if (q.s("scope_scene_id") != null && old.sceneId != q.s("scope_scene_id")) invalid("Tarea fuera de alcance.")
            if (q.s("scope_shot_id") != null && old.shotId != q.s("scope_shot_id")) invalid("Tarea fuera de alcance.")
            val status = q.s("status") ?: old.status
            if (status !in setOf("queued", "prepared", "running", "pending_resume", "needs_agent", "waiting_approval", "blocked", "complete", "cancelled", "error")) invalid("Estado de tarea desconocido.")
            if (old.status == "complete" && status != "complete") invalid("Una tarea terminada no se vuelve a ejecutar; crea otra variante.")
            val executor = q.optional("executor", old.executor)
            if (status == "running" && executor.isNullOrBlank()) invalid("Una tarea solo figura ejecutándose si tiene un ejecutor identificado.")
            val next = old.copy(status = status, detail = q.optional("detail", old.detail), executor = executor,
                providerJobId = q.optional("provider_job_id", old.providerJobId), generationId = q.optional("generation_id", old.generationId),
                startedAt = if (status == "running") old.startedAt ?: now else old.startedAt, updatedAt = now, revision = old.revision + 1)
            d.copy(story = st.copy(tasks = st.tasks.map { if (it.id == old.id) next else it })) to result(next.id, next.revision)
        }
        "set_autonomy" -> {
            val old = st.autonomy.firstOrNull { it.projectId == p }; if (old != null) rev(q, old.revision)
            val sceneId = q.s("scene_id")?.also { scene(d, p, it) }
            val mode = q.s("mode") ?: old?.mode ?: "proposals"
            if (mode !in setOf("preparation", "proposals", "direct")) invalid("Modo de trabajo desconocido.")
            val next = StoryAutonomy(p, q.b("enabled") ?: false, mode, sceneId, old?.revision?.plus(1) ?: 0)
            d.copy(story = st.copy(autonomy = st.autonomy.filterNot { it.projectId == p } + next)) to result(p, next.revision)
        }
        "recover_tasks" -> {
            val tasks = st.tasks.map { if (it.projectId == p && it.status == "running" && it.providerJobId == null) it.copy(status = "pending_resume", detail = "La ejecución se interrumpió. Reanuda desde el estado guardado; no se ha reenviado ninguna generación.", revision = it.revision + 1, updatedAt = now) else it }
            d.copy(story = st.copy(tasks = tasks)) to result(p)
        }
        "run_preparation" -> {
            val policy = st.autonomy.firstOrNull { it.projectId == p } ?: StoryAutonomy(p)
            if (actor == "worker" && !policy.enabled) return d to result(p, extra = mapOf("paused" to JsonPrimitive(true), "prepared_count" to JsonPrimitive(0)))
            val selectedScene = q.s("scene_id")?.also { scene(d, p, it) } ?: policy.sceneId
            // The explicit action runs exactly one finite local pass; it never calls an LLM or a provider.
            val pending = st.tasks.filter { it.projectId == p && it.status in setOf("queued", "pending_resume", "blocked") && (selectedScene == null || it.sceneId == selectedScene) }
            val tasks = st.tasks.map { t -> if (t !in pending) t else {
                val target = t.shotId?.let { value -> d.shots.firstOrNull { it.id == value && it.projectId == p } }
                if (t.shotId != null && target == null) t.copy(status = "blocked", detail = "El plano de origen se ha eliminado. El material se conserva en Por colocar.", updatedAt = now, revision = t.revision + 1)
                else {
                    val context = target?.let { generationContext(d, p, it.id) }
                    val candidates = d.assets.count { a -> !a.libraryDeleted && (a.projectId == p || st.links.any { it.projectId == p && it.assetId == a.id }) }
                    val detail = when (t.kind) {
                        "find_material", "associations", "material_index" -> "$candidates archivos indexados disponibles. La asociación incierta requiere revisión; no se ha clasificado automáticamente."
                        "production_gaps" -> "Planos sin material: " + d.shots.filter { s -> s.projectId == p && (selectedScene == null || s.sceneId == selectedScene) && st.links.none { it.projectId == p && it.scope == "shot" && it.targetId == s.id && it.role !in referenceRoles && it.role != "discarded" } && d.assets.none { it.shotId == s.id && !it.libraryDeleted && it.role in setOf(AssetRole.OUTPUT, AssetRole.FINAL, AssetRole.KEYFRAME) } }.joinToString { "${it.code} [${it.id}]" }
                        "breakdown", "storyboard" -> "Estructura leída: ${d.scenes.count { it.projectId == p }} escenas y ${d.shots.count { it.projectId == p }} planos. El desarrollo creativo queda preparado para el agente."
                        else -> if (context != null) "Contexto y ${context.referenceAssetIds.size} referencias preparadas. Generar requiere autorización de coste independiente." else "Contexto del proyecto preparado; el agente puede continuar desde la estructura guardada."
                    }
                    val state = when (t.kind) { "production_gaps", "references", "material_index", "generation_preparation" -> "complete"; "breakdown", "storyboard", "associations", "variants", "prompts" -> "needs_agent"; else -> "prepared" }
                    t.copy(status = state, detail = detail, context = context, executor = "local_index", startedAt = t.startedAt ?: now, updatedAt = now, revision = t.revision + 1)
                }
            } }
            d.copy(story = st.copy(tasks = tasks)) to result(p, extra = mapOf("prepared_count" to JsonPrimitive(pending.size), "network_calls" to JsonPrimitive(0)))
        }
        "register_result" -> registerResult(d, p, q, now)
        "undo" -> {
            val old = q.s("history_id")?.let { value -> st.history.firstOrNull { it.id == value && it.projectId == p && !it.undone } }
                ?: st.history.lastOrNull { it.projectId == p && !it.undone } ?: invalid("No hay una operación que deshacer.")
            val next = restoreDocument(d, p, old.before, old.after)
            next.copy(story = next.story.copy(history = next.story.history.map { if (it.id == old.id) it.copy(undone = true) else it })) to result(old.id)
        }
        else -> invalid("Operación de proyecto desconocida.")
    }
}

private fun withScope(command: JsonObject, parent: JsonObject): JsonObject = buildJsonObject {
    command.forEach { (k, v) -> put(k, v) }
    for (key in listOf("scope_scene_id", "scope_shot_id", "scope_block_id")) parent[key]?.let { supplied ->
        if (command[key] != null && command[key] != supplied) invalid("Una propuesta no puede ampliar el alcance autorizado.")
        put(key, supplied)
    }
    val keep = (command.strings("preserve_ids").orEmpty() + parent.strings("preserve_ids").orEmpty()).distinct()
    if (keep.isNotEmpty()) put("preserve_ids", JsonArray(keep.map(::JsonPrimitive)))
}

/** Also normalizes pending proposals saved by older versions, without changing their authored text. */
private fun materializeCreation(command: JsonObject, seed: String): JsonObject {
    var result = command
    fun stable(prefix: String, field: String) = prefix + "_" + sha(seed + ":" + field).take(12)
    val action = command.s("action")
    val prefix = when (action) { "create_scene", "import_script" -> "scn"; "create_block" -> "blk"; "create_shot", "duplicate_shot" -> "sht"; "link_material" -> "lnk"; "set_preference" -> "pref"; "upsert_element" -> if (command.s("kind") in setOf(null, "character")) "chr" else "elm"; "enqueue_task", "prepare_generation" -> "task"; else -> null }
    if (prefix != null && command.s("id") == null) result = JsonObject(result + ("id" to JsonPrimitive(stable(prefix, "entity"))))
    if (action in setOf("create_shot", "duplicate_shot") && command.s("shot_block_id") == null) result = JsonObject(result + ("shot_block_id" to JsonPrimitive(stable("blk", "shot-block"))))
    if (action == "import_script" && "block_ids" !in command) result = JsonObject(result + ("block_ids" to JsonArray(command.need("text").split(Regex("\\r?\\n[\\t ]*\\r?\\n")).mapIndexed { index, _ -> JsonPrimitive(stable("blk", "paragraph:$index")) })))
    return result
}

private fun patchShot(d: IndexData, p: String, old: Shot, q: JsonObject, creating: Boolean = false): Shot {
    val destination = if ("scene_id" in q) q.s("scene_id")?.also { scene(d, p, it) } else old.sceneId
    val characters = q.strings("character_ids")?.also { ids -> ids.forEach { character(d, p, it) } } ?: old.characterIds
    val narrative = q.s("narrative_state")?.also { if (it !in narrativeStates) invalid("Estado narrativo desconocido.") } ?: old.narrativeState
    val production = q.s("production_state")?.also { if (it !in productionStates) invalid("Estado de producción desconocido.") } ?: old.productionState
    val duration = if ("duration_sec" in q) q.d("duration_sec")?.also { if (it <= 0 || !it.isFinite()) invalid("Duración inválida.") } else old.durationSec
    val candidate = old.copy(sceneId = destination, code = q.s("code") ?: old.code, title = q.optional("title", old.title), description = q.optional("action_text", q.optional("description", old.description)),
        intent = q.optional("intent", old.intent), characterIds = characters, framing = q.optional("framing", old.framing), camera = q.optional("camera", old.camera),
        performance = q.optional("performance", old.performance), dialogue = q.optional("dialogue", old.dialogue), sound = q.optional("sound", old.sound), durationSec = duration,
        order = q.i("order") ?: old.order, narrativeState = narrative, productionState = production,
        revision = if (creating) 0 else old.revision + 1)
    val changed = candidate.sceneId != old.sceneId || candidate.characterIds.toSet() != old.characterIds.toSet() || candidate.durationSec != old.durationSec ||
        !sameOptional(candidate.description, old.description) || !sameOptional(candidate.intent, old.intent) || !sameOptional(candidate.framing, old.framing) ||
        !sameOptional(candidate.camera, old.camera) || !sameOptional(candidate.performance, old.performance) || !sameOptional(candidate.dialogue, old.dialogue) || !sameOptional(candidate.sound, old.sound)
    return candidate.copy(contentRevision = old.contentRevision + if (!creating && q.b("invalidate_material") != false && changed) 1 else 0)
}

private fun upsertElement(d: IndexData, p: String, q: JsonObject): Pair<IndexData, JsonObject> {
    val kind = q.s("kind") ?: "character"
    val identifier = q.s("element_id")
    if (kind == "character") {
        val old = identifier?.let { character(d, p, it) }; old?.let { rev(q, it.revision) }
        val refs = q.strings("reference_asset_ids") ?: old?.referenceAssetIds.orEmpty()
        refs.forEach { value -> if (d.assets.none { it.id == value && !it.libraryDeleted }) invalid("Referencia de identidad no disponible.") }
            val next = Character(old?.id ?: creationId(d, q, "chr"), p, q.s("name")?.takeIf { it.isNotBlank() } ?: old?.name ?: invalid("Falta el nombre del personaje."),
            q.strings("aliases") ?: old?.aliases.orEmpty(), q.optional("description", old?.description), refs,
            q.strings("variants") ?: old?.variants.orEmpty(), q.optional("notes", old?.notes), old?.revision?.plus(1) ?: 0)
        return d.copy(characters = d.characters.filterNot { it.id == next.id } + next) to result(next.id, next.revision)
    }
    if (kind !in setOf("location", "costume", "object", "continuity")) invalid("Tipo de elemento desconocido.")
    val old = identifier?.let { value -> d.story.elements.firstOrNull { it.id == value && it.projectId == p } ?: invalid("Elemento desconocido.") }; old?.let { rev(q, it.revision) }
    val sceneId = q.optional("scene_id", old?.sceneId)?.also { scene(d, p, it) }
    val characterId = q.optional("character_id", old?.characterId)?.also { character(d, p, it) }
    val next = StoryElement(old?.id ?: creationId(d, q, "elm"), p, kind, q.s("name")?.takeIf { it.isNotBlank() } ?: old?.name ?: invalid("Falta el nombre del elemento."),
        q.optional("description", old?.description), sceneId, characterId, q["fields"] as? JsonObject ?: old?.fields ?: JsonObject(emptyMap()), old?.revision?.plus(1) ?: 0)
    return d.copy(story = d.story.copy(elements = d.story.elements.filterNot { it.id == next.id } + next)) to result(next.id, next.revision)
}

private fun <T> reorder(items: List<T>, id: String, at: Int, identity: (T) -> String, order: (T) -> Int): List<Pair<T, Int>> {
    val target = items.first { identity(it) == id }
    val other = items.filterNot { identity(it) == id }.sortedWith(compareBy(order).thenBy(identity)).toMutableList()
    other.add(at.coerceIn(0, other.size), target)
    return other.mapIndexed { index, value -> value to index }
}

private fun referenceLinks(d: IndexData, p: String, sceneId: String?, shotId: String?): List<StoryLink> {
    val s = shotId?.let { shot(d, p, it) }
    val sc = (sceneId ?: s?.sceneId)?.let { scene(d, p, it) }
    val characters = s?.characterIds.orEmpty().toSet()
    val elements = d.story.elements.filter { it.projectId == p && (it.id in sc?.locationIds.orEmpty() || (it.sceneId == sc?.id && it.sceneId != null && (it.characterId == null || it.characterId in characters))) }.map { it.id }.toSet()
    val refs = d.story.links.filter { link ->
        link.projectId == p && link.role in referenceRoles && link.role != "discarded" &&
            (link.characterId == null || link.characterId in characters) && when (link.scope) {
                "project" -> link.targetId == p && link.role != "costume"
                "scene" -> link.targetId == sc?.id
                "shot" -> link.targetId == s?.id
                "character" -> link.targetId in characters && link.role != "costume"
                "element" -> link.targetId in elements
                "block" -> d.story.blocks.any { it.id == link.targetId && it.sceneId == sc?.id && (it.shotId == s?.id || it.shotId == null) }
                else -> false
            }
    }.sortedBy { when (it.scope) { "project" -> 0; "character", "element" -> 1; "scene", "block" -> 2; else -> 3 } }
    val resolved = mutableListOf<StoryLink>()
    val legacy = d.characters.filter { it.projectId == p && it.id in characters }.flatMap { c -> c.referenceAssetIds.map { asset -> StoryLink("identity_${c.id}_$asset", p, asset, "character", c.id, "identity", characterId = c.id) } }
    for (link in legacy + refs) {
        if (link.behavior == "replace") resolved.removeAll { it.role == link.role && it.characterId == link.characterId }
        if (d.assets.any { it.id == link.assetId && !it.libraryDeleted }) resolved.add(link)
    }
    return resolved.distinctBy { Triple(it.assetId, it.role, it.characterId) }
}

private fun effectivePreferences(d: IndexData, p: String, sceneId: String?, shotId: String?) = d.story.preferences.filter {
    it.enabled && it.projectId == p && when (it.scope) { "project" -> true; "scene" -> it.targetId == sceneId; "shot" -> it.targetId == shotId
        "character" -> shotId?.let { id -> d.shots.firstOrNull { s -> s.id == id }?.characterIds?.contains(it.targetId) } == true
        "block" -> d.story.blocks.any { b -> b.id == it.targetId && b.shotId == shotId }; else -> false }
}

private fun generationContext(d: IndexData, p: String, shotId: String): StoryGenerationContext {
    val s = shot(d, p, shotId); val sc = s.sceneId?.let { scene(d, p, it) }; val pr = project(d, p)
    val refs = referenceLinks(d, p, sc?.id, s.id)
    // Numbering, title, order and production decisions do not invalidate a render. An explicit typo edit can opt out.
    val snapshot = buildJsonObject {
        put("project_id", p); put("shot_id", s.id); put("content_revision", s.contentRevision)
        put("tone", pr.memory.tone); put("visual_style", pr.memory.visualStyle); put("rules", JsonArray(pr.memory.rules.map(::JsonPrimitive)))
        sc?.let { put("scene_id", it.id); put("scene_content_revision", it.contentRevision) }
        put("characters", JsonArray(d.characters.filter { it.id in s.characterIds && it.projectId == p }.sortedBy { it.id }.map { c -> buildJsonObject { put("id", c.id); put("description", c.description); put("variants", JsonArray(c.variants.map(::JsonPrimitive))); put("notes", c.notes) } }))
        put("elements", JsonArray(d.story.elements.filter { it.projectId == p && (it.id in sc?.locationIds.orEmpty() || it.sceneId == sc?.id && it.sceneId != null) && (it.characterId == null || it.characterId in s.characterIds) }.sortedBy { it.id }.map { storyJson.encodeToJsonElement(it) }))
        put("references", JsonArray(refs.map { buildJsonObject { put("asset_id", it.assetId); put("role", it.role); put("character_id", it.characterId); put("scope", it.scope); put("target_id", it.targetId) } }))
        put("preferences", JsonArray(effectivePreferences(d, p, sc?.id, s.id).sortedBy { it.id }.map { JsonPrimitive(it.text) }))
        put("script", JsonArray(d.story.blocks.filter { it.projectId == p && !(it.kind == "shot" && it.text.isBlank()) && (it.shotId == s.id || it.sceneId == sc?.id && it.shotId == null && it.kind in setOf("action", "dialogue") && it.narrativeState != "idea") }.sortedWith(compareBy<StoryBlock> { it.order }.thenBy { it.id }).map { b -> buildJsonObject { put("id", b.id); put("content_revision", b.contentRevision) } }))
    }
    val contextText = buildString {
        appendLine("Proyecto: ${pr.name}"); pr.memory.synopsis?.let { appendLine("Sinopsis: $it") }; pr.treatment?.let { appendLine("Tratamiento: $it") }
        sc?.let { appendLine("Escena: ${it.title ?: it.code}"); it.description?.let { t -> appendLine(t) }; it.timeOfDay?.let { t -> appendLine("Momento: $t") }; it.light?.let { t -> appendLine("Luz: $t") } }
        appendLine("Plano: ${s.title ?: s.code}"); s.description?.let { appendLine("Acción: $it") }; s.intent?.let { appendLine("Intención: $it") }
        s.framing?.let { appendLine("Encuadre: $it") }; s.camera?.let { appendLine("Cámara: $it") }; s.performance?.let { appendLine("Actuación: $it") }; s.dialogue?.let { appendLine("Diálogo: $it") }; s.sound?.let { appendLine("Sonido: $it") }
        for (b in d.story.blocks.filter { it.projectId == p && (it.shotId == s.id || it.sceneId == sc?.id && it.shotId == null && it.kind in setOf("action", "dialogue") && it.narrativeState != "idea") }.sortedBy { it.order }) if (b.text.isNotBlank()) appendLine("${b.kind}${b.speaker?.let { ": $it" }.orEmpty()}: ${b.text}")
        appendLine("Contexto verificable: $snapshot")
    }.trim()
    return StoryGenerationContext(p, sc?.id, s.id, s.contentRevision, contextText, refs.map { it.assetId }.distinct(), sha(snapshot.toString()))
}
fun AssetIndex.storyGenerationContext(projectId: String, shotId: String): StoryGenerationContext = generationContext(snapshot(), projectId, shotId)
fun AssetIndex.isStoryContextStale(context: StoryGenerationContext): Boolean {
    val d = snapshot()
    if (d.shots.none { it.id == context.shotId && it.projectId == context.projectId } || d.projects.none { it.id == context.projectId && !it.archived }) return true
    val current = generationContext(d, context.projectId, context.shotId)
    return if (context.fingerprint.isNotBlank()) current.fingerprint != context.fingerprint else current.revision != context.revision
}

private fun registerResult(d: IndexData, p: String, q: JsonObject, now: Long): Pair<IndexData, JsonObject> {
    val asset = d.assets.firstOrNull { it.id == q.need("asset_id") && !it.libraryDeleted } ?: invalid("No existe el archivo resultante.")
    val task = q.s("task_id")?.let { value -> d.story.tasks.firstOrNull { it.id == value && it.projectId == p } ?: invalid("Tarea desconocida.") }
    val context = (q["context"] as? JsonObject)?.let { runCatching { storyJson.decodeFromJsonElement<StoryGenerationContext>(it) }.getOrElse { invalid("Contexto de origen inválido.") } } ?: task?.context
        ?: invalid("El resultado necesita el contexto que se conservó al preparar la generación.")
    if (context.projectId != p) invalid("El contexto pertenece a otro proyecto.")
    val s = d.shots.firstOrNull { it.id == context.shotId && it.projectId == p }
    val old = d.story.links.firstOrNull { it.projectId == p && it.assetId == asset.id && it.scope == "shot" && it.targetId == context.shotId && it.context != null }
    val next = old ?: StoryLink(id("lnk"), p, asset.id, if (s == null) "project" else "shot", s?.id ?: p,
        role = if (asset.type == AssetType.AUDIO) "audio" else "candidate", note = if (s == null) "Plano de origen eliminado: ${context.shotId}" else null, context = context, createdAt = now)
    val tasks = d.story.tasks.map { if (it.id == task?.id) it.copy(status = "complete", updatedAt = now, revision = it.revision + 1) else it }
    return d.copy(story = d.story.copy(links = if (old == null) d.story.links + next else d.story.links, tasks = tasks)) to result(next.id, next.revision)
}

private fun sceneJson(s: Scene) = buildJsonObject {
    put("id", s.id); put("project_id", s.projectId); put("code", s.code); put("title", s.title); put("description", s.description); put("group", s.group); put("order", s.order)
    put("time_of_day", s.timeOfDay); put("light", s.light); put("location_ids", JsonArray(s.locationIds.map(::JsonPrimitive))); put("revision", s.revision); put("content_revision", s.contentRevision)
}
private fun shotJson(s: Shot) = buildJsonObject {
    put("id", s.id); put("project_id", s.projectId); put("scene_id", s.sceneId); put("code", s.code); put("title", s.title); put("description", s.description); put("intent", s.intent); put("order", s.order)
    put("character_ids", JsonArray(s.characterIds.map(::JsonPrimitive))); put("framing", s.framing); put("camera", s.camera); put("performance", s.performance); put("dialogue", s.dialogue); put("sound", s.sound)
    s.durationSec?.let { put("duration_sec", it) }; put("narrative_state", s.narrativeState); put("production_state", s.productionState); put("variant_of", s.variantOf); put("revision", s.revision); put("content_revision", s.contentRevision)
}
private fun blockJson(b: StoryBlock) = buildJsonObject {
    put("id", b.id); put("project_id", b.projectId); put("scene_id", b.sceneId); put("kind", b.kind); put("text", b.text); put("speaker", b.speaker); put("shot_id", b.shotId); put("order", b.order); put("narrative_state", b.narrativeState); put("revision", b.revision); put("content_revision", b.contentRevision)
}
private fun taskJson(t: StoryTask) = buildJsonObject {
    put("id", t.id); put("project_id", t.projectId); put("scene_id", t.sceneId); put("shot_id", t.shotId); put("title", t.title); put("kind", t.kind); put("status", t.status); put("detail", t.detail)
    put("executor", t.executor); t.startedAt?.let { put("started_at", it) }; put("updated_at", t.updatedAt); put("revision", t.revision); put("provider_job_id", t.providerJobId); put("generation_id", t.generationId)
    t.context?.let { put("context", storyJson.encodeToJsonElement(it)) }
}

fun AssetIndex.storyView(projectId: String, sceneId: String? = null, shotId: String? = null): JsonObject {
    val d = snapshot(); val p = project(d, projectId)
    sceneId?.let { scene(d, projectId, it) }; shotId?.let { shot(d, projectId, it) }
    val refs = referenceLinks(d, projectId, sceneId, shotId)
    val allShots = d.shots.filter { it.projectId == projectId }
    return buildJsonObject {
        put("ok", true)
        put("project", buildJsonObject { put("id", p.id); put("title", p.name); put("description", p.description); put("synopsis", p.memory.synopsis); put("tone", p.memory.tone); put("visual_style", p.memory.visualStyle); put("treatment", p.treatment); put("format", p.format); p.targetDurationSec?.let { put("target_duration_sec", it) }; put("revision", p.revision) })
        put("scenes", JsonArray(d.scenes.filter { it.projectId == projectId }.sortedWith(compareBy<Scene> { it.order }.thenBy { it.id }).map(::sceneJson)))
        put("shots", JsonArray(allShots.sortedWith(compareBy<Shot> { it.order }.thenBy { it.id }).map(::shotJson)))
        put("blocks", JsonArray(d.story.blocks.filter { it.projectId == projectId }.sortedWith(compareBy<StoryBlock> { it.order }.thenBy { it.id }).map(::blockJson)))
        put("links", JsonArray(d.story.links.filter { it.projectId == projectId }.map { link -> linkJson(d, link) }))
        put("elements", JsonArray(d.story.elements.filter { it.projectId == projectId }.map { e -> buildJsonObject { put("id", e.id); put("kind", e.kind); put("name", e.name); put("description", e.description); put("scene_id", e.sceneId); put("character_id", e.characterId); put("fields", e.fields); put("revision", e.revision) } } + d.characters.filter { it.projectId == projectId }.map { c -> buildJsonObject { put("id", c.id); put("kind", "character"); put("name", c.name); put("description", c.description); put("reference_asset_ids", JsonArray(c.referenceAssetIds.map(::JsonPrimitive))); put("variants", JsonArray(c.variants.map(::JsonPrimitive))); put("notes", c.notes); put("revision", c.revision) } }))
        put("preferences", JsonArray(d.story.preferences.filter { it.projectId == projectId }.map { v -> buildJsonObject { put("id", v.id); put("scope", v.scope); put("target_id", v.targetId); put("text", v.text); put("revision", v.revision); put("enabled", v.enabled) } }))
        put("proposals", JsonArray(d.story.proposals.filter { it.projectId == projectId }.map { v -> buildJsonObject { put("id", v.id); put("title", v.title); put("status", v.status); put("revision", v.revision); put("actor", v.actor); put("applied_ids", JsonArray(v.appliedIds.map(::JsonPrimitive))); put("changes", JsonArray(v.changes.map { buildJsonObject { put("id", it.id); put("command", it.command) } })) } }))
        put("tasks", JsonArray(d.story.tasks.filter { it.projectId == projectId }.map(::taskJson)))
        put("autonomy", (d.story.autonomy.firstOrNull { it.projectId == projectId } ?: StoryAutonomy(projectId)).let { buildJsonObject { put("enabled", it.enabled); put("mode", it.mode); put("scene_id", it.sceneId); put("revision", it.revision); put("execution", "Trabajo local persistido; el agente lo ejecuta en un turno activo. Sin turno, queda pendiente de reanudación.") } })
        put("effective_references", JsonArray(refs.map { linkJson(d, it) }))
        put("effective_preferences", JsonArray(effectivePreferences(d, projectId, sceneId ?: shotId?.let { shot(d, projectId, it).sceneId }, shotId).map { JsonPrimitive(it.text) }))
        put("unplaced_asset_ids", JsonArray(d.assets.filter { a -> !a.libraryDeleted && (a.projectId == projectId || a.projectId == null) && d.story.links.none { it.projectId == projectId && it.assetId == a.id } && (a.shotId == null || d.shots.none { it.id == a.shotId }) }.map { JsonPrimitive(it.id) }))
        put("gaps", JsonArray(allShots.filter { s -> d.story.links.none { it.projectId == projectId && it.scope == "shot" && it.targetId == s.id && it.role !in referenceRoles && it.role != "discarded" } && d.assets.none { it.shotId == s.id && !it.libraryDeleted && it.role in setOf(AssetRole.OUTPUT, AssetRole.FINAL, AssetRole.KEYFRAME) } }.map { s -> buildJsonObject { put("shot_id", s.id); put("title", s.title ?: s.code); put("reason", "Sin toma candidata ni material elegido") } }))
        put("history", JsonArray(d.story.history.filter { it.projectId == projectId }.asReversed().map { h -> buildJsonObject { put("id", h.id); put("action", h.action); put("actor", h.actor); put("at", h.at); put("undone", h.undone) } }))
        put("operation_contract", buildJsonObject {
            put("mutation", "Todas las mutaciones requieren operation_id estable por intento; al editar algo existente, expected_revision de su lectura reciente.")
            put("blocks", "create_block(scene_id,kind,text,speaker?,shot_id?). kind: action/dialogue/note/reference/shot/material. update/move/delete_block(block_id,expected_revision,...).")
            put("shots", "create_shot(scene_id,title?,description?,intent?,character_ids?,framing?,camera?,performance?,dialogue?,sound?,duration_sec?). origin_block_id permite desarrollar una nota conservándola. update/move/delete/duplicate_shot(shot_id,expected_revision,...).")
            put("material", "link_material(asset_id,scope,target_id,role,selected?,behavior?). scope: project/scene/shot/character/element/block. role: identity/costume/location/composition/lighting/reference/sketch/generated/candidate/audio/chosen/discarded. behavior: complement/replace.")
            put("elements", "upsert_element(kind,name,description?,element_id?,expected_revision?,fields?). kind: character/location/costume/object/continuity. Character: reference_asset_ids?,variants?,notes?. Costume/continuity: scene_id?,character_id?.")
            put("proposal", "propose(title,changes:[comandos],scope_scene_id?,preserve_ids?). apply_proposal(proposal_id,expected_revision,change_ids?:[c0,c1]). Los cambios dependientes deben aceptarse juntos.")
            put("tasks", "enqueue_task(title,kind,scene_id?,shot_id?). run_preparation realiza una pasada local finita. update_task(task_id,expected_revision,status,detail?). No ejecuta generaciones ni llamadas a agentes externos.")
        })
    }
}

private fun linkJson(d: IndexData, link: StoryLink): JsonObject = buildJsonObject {
    put("id", link.id); put("asset_id", link.assetId); put("project_id", link.projectId); put("scope", link.scope); put("target_id", link.targetId); put("role", link.role); put("selected", link.selected); put("behavior", link.behavior); put("character_id", link.characterId); put("note", link.note); put("revision", link.revision)
    val context = link.context
    put("stale", context != null && (d.shots.none { it.id == context.shotId && it.projectId == context.projectId } || runCatching { generationContext(d, context.projectId, context.shotId).fingerprint != context.fingerprint }.getOrDefault(true)))
    d.assets.firstOrNull { it.id == link.assetId }?.let { a -> put("asset", buildJsonObject { put("id", a.id); put("name", a.displayName); put("uri", a.uri); put("type", a.type.name); put("mime_type", a.mimeType); put("generation_id", a.generationId); put("parent_asset_id", a.parentAssetId); put("available", !a.libraryDeleted) }) }
    context?.let { put("context", storyJson.encodeToJsonElement(it)) }
}

fun AssetIndex.storyContext(projectId: String, sceneId: String? = null, shotId: String? = null): String {
    val d = snapshot(); val p = project(d, projectId)
    if (shotId != null) return generationContext(d, projectId, shotId).context
    return buildString {
        appendLine("Proyecto audiovisual: ${p.name} [${p.id}]")
        p.memory.synopsis?.let { appendLine("Sinopsis: $it") }; p.treatment?.let { appendLine("Tratamiento: $it") }
        val scenes = d.scenes.filter { it.projectId == projectId && (sceneId == null || it.id == sceneId) }.sortedBy { it.order }
        for (s in scenes) {
            appendLine("Escena ${s.title ?: s.code} [${s.id}] revisión ${s.revision}")
            s.description?.let { appendLine(it) }
            for (b in d.story.blocks.filter { it.sceneId == s.id }.sortedBy { it.order }) {
                if (b.shotId != null) d.shots.firstOrNull { it.id == b.shotId && it.projectId == projectId }?.let { appendLine("Plano [${it.id}] ${it.title ?: it.code}: ${it.description.orEmpty()}") }
                if (b.text.isNotBlank()) appendLine("${b.kind} [${b.id}]: ${b.text}")
            }
        }
        val tasks = d.story.tasks.filter { it.projectId == projectId && it.status !in setOf("complete", "cancelled") }
        if (tasks.isNotEmpty()) appendLine("Trabajo pendiente: ${tasks.joinToString { "${it.title} (${it.status})" }}")
        appendLine("Usa read_story_project/edit_story_project para leer o modificar la estructura; una aprobación narrativa no autoriza gasto.")
    }.trim()
}

/** Only editorial entities are undoable; physical assets, generation records and receipts are never deleted. */
private fun document(d: IndexData, p: String): JsonObject = buildJsonObject {
    put("projects", JsonArray(d.projects.filter { it.id == p }.map { storyJson.encodeToJsonElement(it) }))
    put("scenes", JsonArray(d.scenes.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    put("shots", JsonArray(d.shots.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    put("characters", JsonArray(d.characters.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    put("blocks", JsonArray(d.story.blocks.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    put("links", JsonArray(d.story.links.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    put("elements", JsonArray(d.story.elements.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    put("preferences", JsonArray(d.story.preferences.filter { it.projectId == p }.map { storyJson.encodeToJsonElement(it) }))
    // Only logical bindings participate in undo. URIs, files and generation provenance never do.
    put("asset_bindings", JsonArray(d.assets.map { a -> buildJsonObject { put("id", a.id); put("project_id", a.projectId); put("scene_id", a.sceneId); put("shot_id", a.shotId) } }))
}

/** Store only affected entities; a long script does not get copied into every undo entry. */
private fun documentDelta(before: JsonObject, after: JsonObject): Pair<JsonObject, JsonObject> {
    val old = mutableMapOf<String, JsonElement>(); val next = mutableMapOf<String, JsonElement>()
    for (key in before.keys + after.keys) {
        fun keyed(value: JsonElement?) = (value as? JsonArray).orEmpty().associateBy { (it as JsonObject).need("id") }
        val a = keyed(before[key]); val b = keyed(after[key]); val changed = (a.keys + b.keys).filter { a[it] != b[it] }.toSet()
        old[key] = JsonArray(changed.mapNotNull { a[it] }); next[key] = JsonArray(changed.mapNotNull { b[it] })
    }
    return JsonObject(old) to JsonObject(next)
}

private fun restoreDocument(d: IndexData, p: String, before: JsonObject, after: JsonObject): IndexData {
    val current = document(d, p)
    val restored = buildJsonObject {
        for (key in before.keys) {
            fun byId(value: JsonElement?) = (value as? JsonArray).orEmpty().associateBy { (it as JsonObject).need("id") }
            val old = byId(before[key]); val applied = byId(after[key]); val live = byId(current[key]).toMutableMap()
            for (entityId in old.keys + applied.keys) if (old[entityId] != applied[entityId]) {
                if (live[entityId] != applied[entityId]) conflict("No se puede deshacer: ese elemento ha recibido una edición posterior. Los demás cambios se conservan.")
                old[entityId]?.let { live[entityId] = it } ?: live.remove(entityId)
            }
            put(key, JsonArray(live.values.toList()))
        }
    }
    fun merged(key: String, all: List<JsonElement>): JsonArray {
        val projectIds = (current[key] as? JsonArray).orEmpty().map { (it as JsonObject).need("id") }.toSet()
        return JsonArray(all.filterNot { (it as JsonObject).need("id") in projectIds } + (restored[key] as JsonArray))
    }
    val bindings = (restored["asset_bindings"] as? JsonArray).orEmpty().map { it.jsonObject }.associateBy { it.need("id") }
    val next = d.copy(
        projects = storyJson.decodeFromJsonElement(merged("projects", d.projects.map { storyJson.encodeToJsonElement(it) })),
        scenes = storyJson.decodeFromJsonElement(merged("scenes", d.scenes.map { storyJson.encodeToJsonElement(it) })),
        shots = storyJson.decodeFromJsonElement(merged("shots", d.shots.map { storyJson.encodeToJsonElement(it) })),
        characters = storyJson.decodeFromJsonElement(merged("characters", d.characters.map { storyJson.encodeToJsonElement(it) })),
        assets = d.assets.map { asset ->
            val binding = bindings[asset.id]
            if (binding == null) asset else asset.copy(projectId = binding.s("project_id"), sceneId = binding.s("scene_id"), shotId = binding.s("shot_id"))
        },
        story = d.story.copy(
            blocks = storyJson.decodeFromJsonElement(merged("blocks", d.story.blocks.map { storyJson.encodeToJsonElement(it) })),
            links = storyJson.decodeFromJsonElement(merged("links", d.story.links.map { storyJson.encodeToJsonElement(it) })),
            elements = storyJson.decodeFromJsonElement(merged("elements", d.story.elements.map { storyJson.encodeToJsonElement(it) })),
            preferences = storyJson.decodeFromJsonElement(merged("preferences", d.story.preferences.map { storyJson.encodeToJsonElement(it) })),
        ),
    )
    if ((invalidStoryReferences(next, p) - invalidStoryReferences(d, p)).isNotEmpty()) conflict("No se puede deshacer: hay contenido o material añadido después que depende de ese elemento. Conserva o reasigna esas dependencias antes.")
    return next
}

private fun invalidStoryReferences(d: IndexData, p: String): Set<String> = buildSet {
    val scenes = d.scenes.filter { it.projectId == p }.map { it.id }.toSet()
    val shots = d.shots.filter { it.projectId == p }.map { it.id }.toSet()
    val blocks = d.story.blocks.filter { it.projectId == p }.map { it.id }.toSet()
    val characters = d.characters.filter { it.projectId == p }.map { it.id }.toSet()
    val elements = d.story.elements.filter { it.projectId == p }.map { it.id }.toSet()
    d.shots.filter { it.projectId == p && it.sceneId != null && it.sceneId !in scenes }.forEach { add("shot:${it.id}") }
    d.story.blocks.filter { it.projectId == p && (it.sceneId !in scenes || it.shotId != null && it.shotId !in shots) }.forEach { add("block:${it.id}") }
    fun valid(scope: String, id: String): Boolean = when (scope) { "project" -> id == p; "scene" -> id in scenes; "shot" -> id in shots; "block" -> id in blocks; "character" -> id in characters; "element" -> id in elements; else -> false }
    d.story.links.filter { it.projectId == p && !valid(it.scope, it.targetId) }.forEach { add("link:${it.id}") }
    d.story.preferences.filter { it.projectId == p && !valid(it.scope, it.targetId) }.forEach { add("preference:${it.id}") }
}
