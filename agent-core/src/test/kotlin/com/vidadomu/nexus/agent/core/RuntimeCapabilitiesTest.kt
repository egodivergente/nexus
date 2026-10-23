package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.config.*
import com.vidadomu.nexus.agent.core.registry.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RuntimeCapabilitiesTest {
    private val destination=CapabilityDestination("provider","model-v2","v2","/image","references")
    @Test fun `native units distinguish surrogate pairs and UTF8 bytes without token conversion`() {
        val text="A😀é\n"
        fun capability(unit: PromptUnit)=PromptCapability(destination,5,unit,"https://provider.example/schema",1)
        assertEquals(4L,capability(PromptUnit.UNICODE_CODE_POINTS).measured(text))
        assertEquals(5L,capability(PromptUnit.UTF16_CODE_UNITS).measured(text))
        assertEquals(8L,capability(PromptUnit.UTF8_BYTES).measured(text))
        assertNull(capability(PromptUnit.TOKENS).measured(text))
        assertFalse(capability(PromptUnit.TOKENS).exceeds(text))
    }
    @Test fun `changed endpoints versions and workflows never borrow a maximum`() {
        val cap=PromptCapability(destination,6000,PromptUnit.UNICODE_CODE_POINTS,"https://provider.example/schema",1)
        val registry=CapabilityRegistry(listOf(cap))
        assertSame(cap,registry.prompt(destination))
        assertNull(registry.prompt(destination.copy(endpoint="/video")))
        assertNull(registry.prompt(destination.copy(modelVersion="v3")))
        assertNull(registry.prompt(destination.copy(workflow="text")))
        registry.invalidate(destination);assertNull(registry.prompt(destination))
    }
    @Test fun `different destinations validate independently and keep original prompt`() {
        val prompt="😀".repeat(4500)
        val small=PromptCapability(destination,4000,PromptUnit.UNICODE_CODE_POINTS,"official",1)
        val large=small.copy(destination=destination.copy(model="large"),maximum=8000)
        assertTrue(small.exceeds(prompt));assertFalse(large.exceeds(prompt))
        assertEquals(9000,prompt.length)
    }
    @Test fun `scoped local preferences round trip reset and do not become external maxima`() {
        val global=RuleScope();val provider=RuleScope(provider="p");val model=provider.copy(model="m",workflow="edit")
        val configuration=RuntimeConfiguration().set("prompt.softWarning","1000",global)
            .set("prompt.softWarning","2000",provider).set("prompt.softWarning","9000",model)
            .set("prompt.observation","Maybe 12000",model)
        val restored=Json.decodeFromString<RuntimeConfiguration>(Json.encodeToString(configuration)).validated()
        assertEquals("9000",restored.value("prompt.softWarning",model))
        assertEquals("2000",restored.set("prompt.softWarning",null,model).value("prompt.softWarning",model))
        assertEquals("1000",restored.value("prompt.softWarning",RuleScope(provider="other")))
        assertNull(CapabilityRegistry().prompt(destination))
        assertFails {restored.set("startup.recentCount","-1")}
        assertFails {restored.set("ui.motion","false",provider)}
        assertFails {restored.copy(schemaVersion=2).validated()}
    }
}
