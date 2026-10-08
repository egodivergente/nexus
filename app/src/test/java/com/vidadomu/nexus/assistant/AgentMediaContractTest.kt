package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.agent.core.registry.ModelRef
import org.junit.Assert.*
import org.junit.Test

class AgentMediaContractTest {
    private fun model(vararg fields: ContractMediaField) = NexusModel(id="test", displayName="Test", providerId="runway", organization="Test", modalities=listOf(Modality.VIDEO), capabilities=emptyList(), parameters=emptyList(), contractMedia=fields.toList())
    @Test fun requiredImageMapsToNativeFieldWithoutUnsupportedBlock() {
        val m = NexusModelRegistry.agentMediaContract(model(ContractMediaField("promptImage","Inicio","image/*",true)))
        val schema = NexusModelRegistry.toSchema(ModelRef("runway","test"), m, emptyList(), "contract")
        assertEquals("promptImage",schema.media.firstFrameField)
        assertTrue(schema.media.mediaRequired)
        assertTrue(schema.unsupportedRequired.isEmpty())
    }
    @Test fun typedObjectUsesOneImageRatherThanArray() {
        val m = NexusModelRegistry.agentMediaContract(model(ContractMediaField("image","Imagen","image/*",true,encoding="typed-object")))
        assertEquals("image",m.inputImageField)
        assertNull(m.imagesArrayField)
    }
    @Test fun requiredAudioRemainsExplicitlyUnsupported() {
        val m = NexusModelRegistry.agentMediaContract(model(ContractMediaField("audio","Audio","audio/*",true)))
        assertEquals(listOf("audio"),NexusModelRegistry.toSchema(ModelRef("runway","test"),m,emptyList(),"contract").unsupportedRequired)
    }
}
