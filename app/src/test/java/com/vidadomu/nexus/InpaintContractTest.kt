package com.vidadomu.nexus

import com.vidadomu.nexus.inpaint.InpaintContract
import org.junit.Assert.*
import org.junit.Test

class InpaintContractTest {
    @Test fun outsideMaskIsBitExactIncludingTransparency() {
        listOf(0, 0x12345678, -1, -0x1000000).forEach { original ->
            assertEquals(original, InpaintContract.blend(original, 0x76543210, 0))
        }
    }
    @Test fun paintedRegionUsesGeneratedRgbButPreservesAlpha() {
        assertEquals(0x12543210, InpaintContract.blend(0x12345678, 0x76543210, 255))
        assertEquals(0x12808080, InpaintContract.blend(0x12000000, -1, 128))
    }
    @Test fun emptyMaskCannotLaunchGeneration() {
        assertThrows(IllegalArgumentException::class.java) { InpaintContract.validate(64, 64, ByteArray(4096), "a tree", 20) }
    }
    @Test fun validatesMaskGeometryAndPromptWithoutTruncation() {
        val mask = ByteArray(4096).also { it[0] = -1 }
        InpaintContract.validate(64, 64, mask, "árbol 🌳".repeat(1000), 20)
        assertThrows(IllegalArgumentException::class.java) { InpaintContract.validate(65, 64, mask, "tree", 20) }
        assertThrows(IllegalArgumentException::class.java) { InpaintContract.validate(64, 64, mask, " ", 20) }
        assertThrows(IllegalArgumentException::class.java) { InpaintContract.validate(64, 64, mask, "tree", 41) }
    }
    @Test fun memoryGuardIsAnExplicitSafetyReserve() {
        assertNotNull(InpaintContract.memoryProblem(1_419_608L * 1024))
        assertNull(InpaintContract.memoryProblem(InpaintContract.minimumAvailableMemory))
    }
    @Test fun downloadManifestPinsEveryComponent() {
        assertEquals(3, InpaintContract.weights.size)
        assertTrue(InpaintContract.revision.matches(Regex("[a-f0-9]{40}")))
        InpaintContract.weights.forEach { assertTrue(it.sha256.matches(Regex("[a-f0-9]{64}"))); assertTrue(it.bytes > 0) }
        assertEquals(2_132_634_310L, InpaintContract.weights.sumOf { it.bytes })
    }
}
