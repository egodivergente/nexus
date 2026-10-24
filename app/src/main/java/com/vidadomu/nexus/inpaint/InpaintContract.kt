package com.vidadomu.nexus.inpaint

data class InpaintWeights(val name: String, val path: String, val bytes: Long, val sha256: String)

object InpaintContract {
    const val revision = "8a4288a76071f7280aedbdb3253bdb9e9d5d84bb"
    const val source = "https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-inpainting"
    val weights = listOf(
        InpaintWeights("clip.safetensors", "text_encoder/model.fp16.safetensors", 246144864L, "77795e2023adcf39bc29a884661950380bd093cf0750a966d473d1718dc9ef4e"),
        InpaintWeights("unet.safetensors", "unet/diffusion_pytorch_model.fp16.safetensors", 1719154104L, "24b788b4a777748377cc20364eea4ae113c8c42f4468c16bc8c02fdae5492af9"),
        InpaintWeights("vae.safetensors", "vae/diffusion_pytorch_model.fp16.safetensors", 167335342L, "4fbcf0ebe55a0984f5a5e00d8c4521d52359af7229bb4d81890039d2aa16dd7c")
    )
    const val minimumAvailableMemory = 3L * 1024 * 1024 * 1024
    fun memoryProblem(available: Long): String? = if (available < minimumAvailableMemory)
        "Memoria disponible insuficiente: ${available / (1024 * 1024)} MiB. Esta implementación reserva al menos 3072 MiB; cierra otras apps y reintenta." else null

    fun validate(width: Int, height: Int, mask: ByteArray, prompt: String, steps: Int) {
        require(width in 64..512 && height in 64..512 && width % 64 == 0 && height % 64 == 0) { "Tamaño de inferencia no compatible." }
        require(mask.size == width * height && mask.any { it.toInt() and 255 > 0 }) { "Pinta una región en la máscara." }
        require(prompt.isNotBlank()) { "Describe qué debe aparecer en la zona pintada, también al eliminar un objeto." }
        require(steps in 4..40) { "Usa entre 4 y 40 pasos." }
    }

    fun blend(original: Int, generated: Int, coverage: Int): Int {
        require(coverage in 0..255)
        if (coverage == 0) return original
        fun channel(shift: Int): Int = (((original ushr shift) and 255) * (255 - coverage) + ((generated ushr shift) and 255) * coverage + 127) / 255
        return (original and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
