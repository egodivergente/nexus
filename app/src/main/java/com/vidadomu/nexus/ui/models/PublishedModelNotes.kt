package com.vidadomu.nexus.ui.models

import kotlinx.serialization.json.*

/** Provider descriptions saved with the corresponding public model page. */
object PublishedModelNotes {
    private var notes: Map<String, String> = emptyMap()
    fun seed(raw: String) {
        notes = Json.parseToJsonElement(raw).jsonArray.associate {
            it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("about").jsonPrimitive.content
        }
    }
    fun get(id: String): String = notes[id].orEmpty()
}
