package com.vidadomu.nexus.inpaint

object InpaintNative {
    private val loaded by lazy { runCatching { System.loadLibrary("nexus_inpaint") }.isSuccess }
    fun available(): Boolean = loaded && android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")
    external fun start(folder: String, prompt: ByteArray, rgb: ByteArray, mask: ByteArray, width: Int, height: Int, steps: Int, seed: Long): Boolean
    external fun status(): Int
    external fun progress(): Int
    external fun result(): ByteArray?
    external fun cancel()
}
