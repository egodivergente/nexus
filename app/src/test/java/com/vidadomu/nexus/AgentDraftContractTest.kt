package com.vidadomu.nexus

import com.vidadomu.nexus.agent.AgentDraftContract
import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AgentDraftContractTest {
    private val model=NexusModel("motion","Motion","atlas_cloud",organization="fixture",modalities=listOf(Modality.VIDEO),capabilities=emptyList(),
        parameters=listOf(ModelParameter("character_orientation","Orientación",ParameterType.ENUM,options=listOf("image","video"),isRequired=true),
            ModelParameter("keep_original_sound","Sonido",ParameterType.BOOLEAN)))
    @Test fun draftPreservesNativeKeysAndAllowsIncompleteMedia() {
        AgentDraftContract.validate(model,"Prueba",mapOf("character_orientation" to "video","keep_original_sound" to "false"))
        AgentDraftContract.validate(model,"",emptyMap())
    }
    @Test fun unknownFieldsAndInvalidValuesAreRejected() {
        assertTrue(runCatching {AgentDraftContract.validate(model,"",mapOf("api_key" to "example"))}.isFailure)
        assertTrue(runCatching {AgentDraftContract.validate(model,"",mapOf("character_orientation" to "other"))}.isFailure)
        assertTrue(runCatching {AgentDraftContract.validate(model,"",mapOf("keep_original_sound" to "yes"))}.isFailure)
    }
    @Test fun oversizedPromptIsRejectedWithoutPreparingAnything() {
        assertTrue(runCatching {AgentDraftContract.validate(model,"x".repeat(16001),emptyMap())}.isFailure)
    }
}
