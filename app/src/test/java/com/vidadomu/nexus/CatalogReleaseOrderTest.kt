package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class CatalogReleaseOrderTest {
    private fun model(id:String)=NexusModel(id=id,displayName=id,providerId="atlas_cloud",organization="fixture",
        modalities=listOf(Modality.VIDEO),capabilities=listOf(ModelCapability.TEXT_TO_VIDEO),parameters=emptyList())
    @Test fun releaseOrderUsesVerifiedLaunchInsteadOfCatalogTimestampOrVersionName() {
        val models=listOf(model("unknown/v99").copy(releaseDateMillis=Long.MAX_VALUE),model("google/veo3.1"),model("bytedance/seedance-2.5"),model("kwaivgi/kling-v3.0-pro/text-to-video"))
        val sorted=sortGroups(catalogModelGroups(models),CatalogSort.RELEASE,emptyList())
        assertEquals(listOf("2026-07","2026-02","2025-10",null),sorted.map(::latestVerifiedRelease))
        assertEquals(3,filterDatedGroups(sorted,true).size)
        assertEquals(sorted,filterDatedGroups(sorted,false))
    }
    @Test fun modelsWithoutVerifiedDatesAreNotSilentlyDiscarded() {
        val groups=catalogModelGroups(listOf(model("unknown/a"),model("unknown/b")))
        assertEquals(2,sortGroups(groups,CatalogSort.RELEASE,emptyList()).size)
        assertTrue(filterDatedGroups(groups,true).isEmpty())
    }
}
