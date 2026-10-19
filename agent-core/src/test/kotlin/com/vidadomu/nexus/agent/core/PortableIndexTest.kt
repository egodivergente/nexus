package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.assets.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortableIndexTest {
    @Test fun `bundle insertion is atomic and never overwrites existing identities`() = runTest {
        val index=AssetIndex(null)
        val existing=index.createProject("Existing")
        val bundle=IndexData(projects=listOf(Project("imported","Imported",createdAt=1)),
            assets=listOf(Asset("asset-imported","/media/reference.png",AssetType.IMAGE,displayName="Reference",projectId="imported",createdAt=1)))
        index.importProject(bundle)
        assertEquals(existing,index.findProject(existing.id))
        val before=index.snapshot()
        assertFailsWith<IllegalArgumentException> { index.importProject(bundle) }
        assertEquals(before,index.snapshot())
        val broken=bundle.copy(projects=listOf(Project("new","New",createdAt=1)),
            assets=listOf(bundle.assets.single().copy(id="new-asset",projectId="new",parentAssetId="missing")))
        assertFailsWith<IllegalArgumentException> { index.importProject(broken) }
        assertEquals(before,index.snapshot())
        val wrongType=broken.copy(assets=listOf(broken.assets.single().copy(parentAssetId=null,sceneId="new-asset")))
        assertFailsWith<IllegalArgumentException> { index.importProject(wrongType) }
        assertEquals(before,index.snapshot())
    }
}
