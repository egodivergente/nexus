package com.vidadomu.nexus.cut.export

import com.vidadomu.nexus.cut.model.BitratePreset
import com.vidadomu.nexus.cut.model.ExportResolution
import com.vidadomu.nexus.cut.model.ExportSettings
import com.vidadomu.nexus.cut.model.FrameRate
import com.vidadomu.nexus.cut.model.ProjectSettings
import com.vidadomu.nexus.cut.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMathTest {
    @Test
    fun sizeFollowsProjectAspectAndShortSide() {
        assertEquals(1920 to 1080, ExportMath.outputSize(ProjectSettings(1920, 1080), 1080))
        assertEquals(1280 to 720, ExportMath.outputSize(ProjectSettings(1920, 1080), 720))
        assertEquals(3840 to 2160, ExportMath.outputSize(ProjectSettings(1920, 1080), 2160))
        assertEquals(1080 to 1920, ExportMath.outputSize(ProjectSettings(720, 1280), 1080))
        val (w, h) = ExportMath.outputSize(ProjectSettings(1080, 1350), 1080)
        assertEquals(1080, w); assertEquals(1350, h)
    }

    @Test
    fun bitrateAndEstimate() {
        val p = ExportMath.plan(ProjectSettings(1920, 1080, FrameRate.FPS_30), ExportSettings(ExportResolution.P1080, null, VideoCodec.H264, BitratePreset.AUTO), 60_000_000)
        assertNull(p.frameRate)
        // 1920*1080*30*0.1 = 6.22 Mbps
        assertEquals(6_220_800, p.videoBitrate)
        val expected = (6_220_800 + 192_000) / 8.0 * 60 * 1.015
        assertEquals(expected, p.estimatedBytes.toDouble(), 2.0)
        val hevc = ExportMath.plan(ProjectSettings(1920, 1080, FrameRate.FPS_30), ExportSettings(codec = VideoCodec.H265), 1)
        assertTrue(hevc.videoBitrate < p.videoBitrate)
        val custom = ExportMath.plan(ProjectSettings(), ExportSettings(bitrate = BitratePreset.CUSTOM, customBitrateKbps = 12_000, frameRate = FrameRate.FPS_50), 1)
        assertEquals(12_000_000, custom.videoBitrate)
        assertEquals(50, custom.frameRate)
    }
}
