package com.vidadomu.nexus.ui.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowExportTest {
    private val big = FlowExportDimensions(4096, 2304)

    @Test fun targetSizeScalesLongEdgeKeepingAspect() {
        assertEquals(FlowExportDimensions(3840, 2160), FlowExport.targetSize(4096, 2304, FlowExportSize.UHD_4K))
        assertEquals(FlowExportDimensions(2048, 1152), FlowExport.targetSize(4096, 2304, FlowExportSize.QHD_2K))
        assertEquals(FlowExportDimensions(608, 1080), FlowExport.targetSize(1152, 2048, FlowExportSize.FHD_1080))
        assertEquals(FlowExportDimensions(720, 720), FlowExport.targetSize(1024, 1024, FlowExportSize.HD_720))
    }

    @Test fun targetSizeNeverUpscales() {
        assertEquals(FlowExportDimensions(1024, 768), FlowExport.targetSize(1024, 768, FlowExportSize.UHD_4K))
        assertEquals(FlowExportDimensions(1080, 1080), FlowExport.targetSize(1080, 1080, FlowExportSize.FHD_1080))
        assertEquals(FlowExportDimensions(5000, 10), FlowExport.targetSize(5000, 10, FlowExportSize.ORIGINAL))
    }

    @Test fun targetSizeKeepsAtLeastOnePixel() {
        assertEquals(FlowExportDimensions(720, 1), FlowExport.targetSize(8000, 2, FlowExportSize.HD_720))
    }

    @Test fun sampleSizeIsLargestPowerOfTwoAboveTarget() {
        assertEquals(1, FlowExport.sampleSize(1000, 1000, FlowExportDimensions(720, 720)))
        assertEquals(2, FlowExport.sampleSize(4096, 2304, FlowExportDimensions(2048, 1152)))
        assertEquals(4, FlowExport.sampleSize(4096, 4096, FlowExportDimensions(720, 720)))
    }

    @Test fun mimeAndExtension() {
        assertEquals("image/jpeg", FlowExport.mimeType(FlowExportFormat.JPG))
        assertEquals("image/webp", FlowExport.mimeType(FlowExportFormat.WEBP))
        assertNull(FlowExport.mimeType(FlowExportFormat.ORIGINAL))
        assertEquals("jpg", FlowExport.extension("image/jpeg"))
        assertEquals("png", FlowExport.extension("IMAGE/PNG"))
        assertEquals("mp4", FlowExport.extension("video/mp4"))
        assertEquals(FlowExportFormat.JPG, FlowExport.formatForMime("image/jpg"))
    }

    @Test fun suggestedNameUsesIdAndDimensions() {
        assertEquals("nexus_flow_abc_3840x2160.png",
            FlowExport.suggestedName("abc", FlowExportDimensions(3840, 2160), "png"))
        assertEquals("nexus_flow_a_b_c.mp4", FlowExport.suggestedName("a/b:c", null, "mp4"))
    }

    @Test fun originalWithoutResizeIsByteCopy() {
        val plan = FlowExport.plan("id", "image/png", false, big, FlowExportFormat.ORIGINAL, FlowExportSize.ORIGINAL)
        assertTrue(plan.isPassThrough)
        assertEquals("nexus_flow_id_4096x2304.png", plan.fileName)
    }

    @Test fun sameFormatAndUpscaleRequestStaysByteCopy() {
        val small = FlowExportDimensions(1024, 1024)
        val plan = FlowExport.plan("id", "image/jpeg", false, small, FlowExportFormat.JPG, FlowExportSize.UHD_4K)
        assertTrue(plan.isPassThrough)
        assertEquals(small, plan.target)
    }

    @Test fun conversionAndResizeEncode() {
        val plan = FlowExport.plan("id", "image/png", false, big, FlowExportFormat.WEBP, FlowExportSize.QHD_2K)
        assertFalse(plan.isPassThrough)
        assertEquals(FlowExportFormat.WEBP, plan.encodeAs)
        assertEquals("image/webp", plan.mimeType)
        assertEquals("nexus_flow_id_2048x1152.webp", plan.fileName)
    }

    @Test fun originalFormatWithResizeKeepsOriginalEncoding() {
        val plan = FlowExport.plan("id", "image/jpeg", false, big, FlowExportFormat.ORIGINAL, FlowExportSize.HD_720)
        assertEquals(FlowExportFormat.JPG, plan.encodeAs)
        assertEquals("nexus_flow_id_720x405.jpg", plan.fileName)
    }

    @Test fun unknownOriginalMimeResizedFallsBackToPng() {
        val plan = FlowExport.plan("id", "", false, big, FlowExportFormat.ORIGINAL, FlowExportSize.HD_720)
        assertEquals(FlowExportFormat.PNG, plan.encodeAs)
    }

    @Test fun videoAlwaysOriginal() {
        val plan = FlowExport.plan("v", "video/mp4", true, big, FlowExportFormat.JPG, FlowExportSize.HD_720)
        assertTrue(plan.isPassThrough)
        assertEquals("video/mp4", plan.mimeType)
        assertEquals("nexus_flow_v_4096x2304.mp4", plan.fileName)
    }

    @Test fun unknownDimensionsForceByteCopy() {
        val plan = FlowExport.plan("v", "image/png", false, null, FlowExportFormat.JPG, FlowExportSize.HD_720)
        assertTrue(plan.isPassThrough)
        assertEquals("nexus_flow_v.png", plan.fileName)
    }
}
