package com.vidadomu.nexus.assistant

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MediaEditSpecTest {
    private fun input(text:String)=Json.parseToJsonElement(text).jsonObject
    @Test fun sourceSelectionIsUnambiguousAndOptionsAreClosed() {
        for(raw in listOf("""{"asset_id":"a","asset_ids":["b"]}""","""{"asset_ids":"a"}""","""{"asset_id":"a","shell":"rm"}"""))
            assertThrows(com.vidadomu.nexus.agent.core.tools.ToolInputException::class.java) {MediaEditSpec.parse("remove_audio",input(raw),null)}
    }
    @Test fun trimsValidateTheWholeIntervalBeforeEditing() {
        val spec=MediaEditSpec.parse("trim",input("""{"asset_ids":["video"],"start_ms":1500,"end_ms":9000}"""),"project")
        assertEquals(1500L,spec.startMs);assertEquals(9000L,spec.endMs);assertEquals("project",spec.projectId)
        for(raw in listOf("""{"asset_ids":["v"]}""","""{"asset_ids":["v"],"start_ms":-1,"end_ms":4}""","""{"asset_ids":["v"],"start_ms":9,"end_ms":9}""","""{"asset_ids":["v"],"end_ms":1.2}""")) {
            assertThrows(com.vidadomu.nexus.agent.core.tools.ToolInputException::class.java) {MediaEditSpec.parse("trim",input(raw),null)}
        }
    }
    @Test fun concatenationPreservesRequestedOrderAndAllowsRepeats() {
        assertEquals(listOf("b","a","b"),MediaEditSpec.parse("concat",input("""{"asset_ids":["b","a","b"]}"""),null).assetIds)
        assertThrows(com.vidadomu.nexus.agent.core.tools.ToolInputException::class.java) {MediaEditSpec.parse("concat",input("""{"asset_ids":["a"]}"""),null)}
    }
    @Test fun audioOperationsRequireAnExplicitTrackAndRejectIrrelevantIntervals() {
        assertEquals("audio",MediaEditSpec.parse("mix_audio",input("""{"asset_ids":["v"],"audio_asset_id":"audio"}"""),null).audioAssetId)
        for(raw in listOf("""{"asset_ids":["v"]}""","""{"asset_ids":["v"],"audio_asset_id":"a","start_ms":10}"""))
            assertThrows(com.vidadomu.nexus.agent.core.tools.ToolInputException::class.java) {MediaEditSpec.parse("replace_audio",input(raw),null)}
    }
    @Test fun frameTimesAreBoundedAndKeepDistinctRequestedTimes() {
        assertEquals(listOf(0L,1000L),MediaEditSpec.parse("frames",input("""{"asset_id":"v","timestamps_ms":[0,1000,0]}"""),null).framesMs)
        for(raw in listOf("""{"asset_id":"v","timestamps_ms":[]}""","""{"asset_id":"v","timestamps_ms":[-1]}""","""{"asset_id":"v","timestamps_ms":[1.2]}"""))
            assertThrows(com.vidadomu.nexus.agent.core.tools.ToolInputException::class.java) {MediaEditSpec.parse("frames",input(raw),null)}
    }
    @Test fun subtitlesUseHoursAndMillisecondPrecision() {
        assertEquals("01:02:03,004",WhisperTranscription.timestamp(3723004))
        assertEquals("00:00:00,000",WhisperTranscription.timestamp(-1))
    }
}
