package com.vidadomu.nexus.cut.storage

import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.Project
import java.util.UUID
import kotlinx.serialization.json.*

/** The common document is readable by the Windows editor. Android-only links survive a round trip. */
object SharedMontage {
    private val json = Json { encodeDefaults = true }
    private const val STORY = "__nexus_cut_story"

    fun localId(projectId: String): String =
        UUID.nameUUIDFromBytes("nexus-cut:$projectId".toByteArray(Charsets.UTF_8)).toString()

    fun encode(project: Project, playheadUs: Long, previous: JsonObject? = null): JsonObject {
        val nexusId = requireNotNull(project.nexusProjectId)
        val encoded = json.encodeToJsonElement(Project.serializer(), project).jsonObject
        val oldLinks = previous?.get("links")?.jsonObject.orEmpty()
        val links = linkedMapOf<String, JsonElement>()
        val media = encoded.getValue("media").jsonArray.zip(project.media).map { (entry, original) ->
            val link = oldLinks[original.id]?.jsonObject.orEmpty().toMutableMap()
            original.assetId?.let { link["asset_id"] = JsonPrimitive(it) }
            original.generationId?.let { link["generation_id"] = JsonPrimitive(it) }
            if (link.isNotEmpty()) links[original.id] = JsonObject(link)
            JsonObject(entry.jsonObject - setOf("assetId", "generationId"))
        }
        val story = linkedMapOf<String, JsonElement>()
        val timeline = mapClips(encoded.getValue("timeline").jsonObject) { clip ->
            (clip["story"] as? JsonObject)?.let { story[clip.getValue("id").jsonPrimitive.content] = it }
            JsonObject(clip - "story")
        }
        if (story.isNotEmpty()) links[STORY] = JsonObject(story)
        val portable = JsonObject(encoded - setOf("nexusProjectId", "sceneId", "revision") + mapOf(
            "id" to JsonPrimitive(nexusId), "schemaVersion" to JsonPrimitive(1),
            "modifiedAtMs" to JsonPrimitive(0), "media" to JsonArray(media), "timeline" to timeline
        ))
        return buildJsonObject {
            put("schema", 1)
            put("project", portable)
            put("links", JsonObject(links))
            put("playheadUs", playheadUs.coerceIn(0L, project.timeline.durationUs))
        }
    }

    data class Received(val project: Project, val playheadUs: Long)

    fun decode(projectId: String, document: JsonObject, cutId: String): Received {
        require(document["schema"]?.jsonPrimitive?.intOrNull == 1) { "Formato de montaje no compatible." }
        val portable = document.getValue("project").jsonObject
        require(portable["id"]?.jsonPrimitive?.content == projectId) { "El montaje corresponde a otro proyecto." }
        val links = document["links"]?.jsonObject.orEmpty()
        val story = links[STORY]?.jsonObject.orEmpty()
        val timeline = mapClips(portable.getValue("timeline").jsonObject) { clip ->
            val link = story[clip.getValue("id").jsonPrimitive.content]
            if (link == null) clip else JsonObject(clip + ("story" to link))
        }
        val media = portable.getValue("media").jsonArray.map { entry ->
            val row = entry.jsonObject
            val link = links[row.getValue("id").jsonPrimitive.content]?.jsonObject.orEmpty()
            val origin = mapOf("assetId" to link["asset_id"], "generationId" to link["generation_id"])
                .mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
            JsonObject(row + origin)
        }
        val encoded = JsonObject(portable + mapOf(
            "id" to JsonPrimitive(cutId), "nexusProjectId" to JsonPrimitive(projectId),
            "schemaVersion" to JsonPrimitive(Project.CURRENT_SCHEMA), "revision" to JsonPrimitive(0),
            "media" to JsonArray(media), "timeline" to timeline
        ))
        val project = ProjectCodec.decode(encoded.toString().toByteArray(Charsets.UTF_8))
        val mediaIds = project.media.map { it.id }.toSet()
        require(mediaIds.size == project.media.size && project.timeline.allTracks.all { track ->
            track.clips.all { it.kind == ClipKind.TEXT || it.mediaId in mediaIds }
        }) { "El montaje contiene una referencia incompleta." }
        val playhead = document["playheadUs"]?.jsonPrimitive?.longOrNull ?: 0
        return Received(project, playhead.coerceIn(0L, project.timeline.durationUs))
    }

    private fun mapClips(timeline: JsonObject, transform: (JsonObject) -> JsonObject): JsonObject =
        JsonObject(timeline.mapValues { (field, value) ->
            if (field != "videoTracks" && field != "audioTracks") value
            else JsonArray(value.jsonArray.map { item ->
                val track = item.jsonObject
                JsonObject(track + ("clips" to JsonArray(track.getValue("clips").jsonArray.map { transform(it.jsonObject) })))
            })
        })
}
