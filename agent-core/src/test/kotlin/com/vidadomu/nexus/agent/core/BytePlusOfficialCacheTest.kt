package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.BytePlusOfficialCache
import java.nio.file.Files
import kotlinx.serialization.json.*
import kotlin.test.*

class BytePlusOfficialCacheTest {
    private fun snapshot(ok: Boolean, day: String = "2026-10-20") = buildJsonObject {
        put("schema",1);put("period","day");put("fetched_at",1000);put("start",day);put("end",day)
        for (key in listOf("stats","billing","quota","balance")) put(key, buildJsonObject {
            put("status",if(ok) "ok" else "unavailable")
            if(ok) {put("updated_at",900);put("amount","0.59")} else put("error","fixture disconnected")
        })
    }
    @Test fun staleDataSurvivesRestartAndKeepsItsOwnPeriod() {
        val file=Files.createTempDirectory("byteplus-cache").resolve("cache.json").toFile()
        val cache=BytePlusOfficialCache(file)
        cache.put("day",snapshot(true))
        cache.put("day",snapshot(false,"2026-10-21"))
        val restored=BytePlusOfficialCache(file).get("day")!!["billing"]!!.jsonObject
        assertEquals("0.59",restored["amount"]!!.jsonPrimitive.content)
        assertEquals("stale",restored["status"]!!.jsonPrimitive.content)
        assertEquals("2026-10-20",restored["start"]!!.jsonPrimitive.content)
    }
    @Test fun malformedResponsesLeaveLastGoodFileUntouched() {
        val file=Files.createTempDirectory("byteplus-cache").resolve("cache.json").toFile()
        val cache=BytePlusOfficialCache(file);cache.put("day",snapshot(true));val before=file.readText()
        assertFails {cache.put("day",buildJsonObject {put("schema",2)})}
        assertEquals(before,file.readText());assertNull(cache.get("month"))
    }
}
