package com.vidadomu.nexus.sync

import com.vidadomu.nexus.agent.core.sync.SyncCrypto
import kotlinx.serialization.json.*

internal enum class PeerAuthChoice { SAME, LOCAL, PEER }

/** OAuth files form one revision: never combine a token from one login with another installation id. */
internal fun choosePeerAuth(local: JsonObject?, remote: JsonObject?, base: JsonObject?): PeerAuthChoice {
    if (remote == null || remote == local || remote == base) return PeerAuthChoice.SAME
    if (local == base || local.isNullOrEmpty()) return PeerAuthChoice.PEER
    fun rank(auth: JsonObject?): Pair<Long, String> {
        val token = auth?.get("antigravity-oauth-token")?.jsonPrimitive?.content.orEmpty()
        val expiry = runCatching {
            val jwt = Json.parseToJsonElement(token).jsonObject["id_token"]!!.jsonPrimitive.content
            val payload = java.util.Base64.getUrlDecoder().decode(jwt.split('.')[1]).toString(Charsets.UTF_8)
            Json.parseToJsonElement(payload).jsonObject["exp"]!!.jsonPrimitive.long
        }.getOrDefault(0)
        return expiry to SyncCrypto.hash(token.toByteArray())
    }
    val peer = rank(remote); val own = rank(local)
    return if (peer.first > own.first || (peer.first == own.first && peer.second > own.second)) PeerAuthChoice.PEER else PeerAuthChoice.LOCAL
}
