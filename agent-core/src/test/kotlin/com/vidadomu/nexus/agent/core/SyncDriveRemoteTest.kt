package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.sync.DriveRemote
import java.nio.file.Files
import kotlin.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class SyncDriveRemoteTest {
    @Test fun existingOriginalsUseOneFolderListingPerCycle() {
        val first="a".repeat(64)+".png";val second="b".repeat(64)+".png"
        var requests=0
        val client=OkHttpClient.Builder().addInterceptor {chain->
            requests++
            val request=chain.request()
            assertEquals("GET",request.method)
            assertEquals("'root' in parents and trashed = false",request.url.queryParameter("q"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"files":[{"id":"one","name":"blob-$first","size":"4"},{"id":"two","name":"blob-$second","size":"4"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val remote=DriveRemote({"test-token"},"root",client)
        val original=Files.createTempFile("nexus-existing-original",".png").toFile().apply {writeBytes(byteArrayOf(1,2,3,4))}
        try {remote.putBlob(first,original);remote.putBlob(second,original);assertEquals(1,requests)}
        finally {original.delete()}
    }
}
