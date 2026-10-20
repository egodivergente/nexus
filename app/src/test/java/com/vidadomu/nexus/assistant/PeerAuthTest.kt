package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.sync.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PeerAuthTest {
    private fun auth(expiry: Long, installation: String): JsonObject {
        val jwt = "header." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":$expiry}".toByteArray()) + ".signature"
        return buildJsonObject {
            put("antigravity-oauth-token",buildJsonObject { put("id_token",jwt);put("token","fake-$installation") }.toString())
            put("installation_id",installation)
        }
    }
    @Test fun acceptsOneSidedRefreshAndKeepsUnchangedPeerFromRollingBackLocalRefresh() {
        val base=auth(1,"old"); val fresh=auth(2,"new")
        assertEquals(PeerAuthChoice.PEER,choosePeerAuth(base,fresh,base))
        assertEquals(PeerAuthChoice.SAME,choosePeerAuth(fresh,base,base))
        assertEquals(PeerAuthChoice.SAME,choosePeerAuth(fresh,fresh,base))
    }
    @Test fun concurrentRefreshConvergesOnLatestExpiryWithoutMixingInstallationFiles() {
        val a=auth(2,"a");val b=auth(3,"b");val base=auth(1,"base")
        assertEquals(PeerAuthChoice.PEER,choosePeerAuth(a,b,base))
        assertEquals(PeerAuthChoice.LOCAL,choosePeerAuth(b,a,base))
    }
    @Test fun nonJwtAuthStillConvergesDeterministicallyAndMissingAdapterCannotEraseLogin() {
        val a=buildJsonObject {put("antigravity-oauth-token","opaque-a")}
        val b=buildJsonObject {put("antigravity-oauth-token","opaque-b")}
        assertNotEquals(choosePeerAuth(a,b,null),choosePeerAuth(b,a,null))
        assertEquals(PeerAuthChoice.SAME,choosePeerAuth(a,null,null))
    }
}
