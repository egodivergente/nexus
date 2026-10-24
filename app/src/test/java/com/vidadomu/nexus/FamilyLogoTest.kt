package com.vidadomu.nexus

import com.vidadomu.nexus.ui.components.logoFamilyId
import com.vidadomu.nexus.ui.components.preferredLogo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyLogoTest {
    @Test fun familyIdentityIsStable() {
        assertEquals("seedance", logoFamilyId(" Seedance "))
        assertEquals(logoFamilyId("SEEDANCE"), logoFamilyId("Seedance"))
    }
    @Test fun individualWinsAndResetFallsBackToFamily() {
        assertEquals("individual", preferredLogo("family", "individual"))
        assertEquals("family", preferredLogo("family", null))
        assertEquals("family", preferredLogo("family", ""))
        assertNull(preferredLogo(null, null))
    }
}
