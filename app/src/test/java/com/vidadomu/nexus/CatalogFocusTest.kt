package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.models.CatalogFocus
import org.junit.Assert.*
import org.junit.Test

class CatalogFocusTest {
    private fun model(id: String) = NexusModel(id=id, displayName=id, providerId="atlas_cloud",
        organization="fixture", modalities=listOf(Modality.IMAGE), capabilities=emptyList(), parameters=emptyList())

    @Test fun reviewedRoutesHaveAConcreteUseAndLimit() {
        val selected=model("bytedance/seedream-v5.0-pro/text-to-image")
        val old=model("bytedance/seedream-v4/text-to-image")
        val advice=CatalogFocus.forModel(selected)
        assertTrue(advice!!.good.isNotBlank())
        assertTrue(advice.avoid.isNotBlank())
        assertNull(CatalogFocus.forModel(old))
    }
}
