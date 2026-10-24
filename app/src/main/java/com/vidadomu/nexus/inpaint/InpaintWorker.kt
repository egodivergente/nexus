package com.vidadomu.nexus.inpaint

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.*
import com.vidadomu.nexus.assistant.MediaEditWorker
import kotlinx.coroutines.*
import java.io.File

class InpaintWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var started = false
        try {
            val jobId = requireNotNull(inputData.getString("job"))
            require(Regex("[a-f0-9-]{36}").matches(jobId))
            val folder = File(applicationContext.filesDir, "inpaint-jobs/$jobId")
            if(File(folder, "result.png").isFile) return@withContext Result.success(workDataOf("job" to jobId))
            check(runAttemptCount == 0) { "El proceso de edición fue interrumpido. Se conservan imagen e instrucciones; reintenta manualmente cuando haya memoria suficiente." }
            val prompt = File(folder, "prompt.txt").readText()
            val source = checkNotNull(BitmapFactory.decodeFile(File(folder, "source.png").path)) { "No se pudo abrir la imagen." }
            val mask = checkNotNull(BitmapFactory.decodeFile(File(folder, "mask.png").path)) { "No se pudo abrir la máscara." }
            require(source.width == mask.width && source.height == mask.height) { "La máscara no coincide con la imagen." }
            require(source.width.toLong() * source.height <= 4_194_304) { "La imagen supera 4 megapíxeles." }
            val models = InpaintModelStore(applicationContext)
            check(models.ready()) { "Descarga primero el modelo local." }
            check(InpaintNative.available()) { "El motor nativo requiere Android ARM64." }
            val memory = ActivityManager.MemoryInfo()
            (applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            InpaintContract.memoryProblem(memory.availMem)?.let { error(it) }
            setForeground(MediaEditWorker.foreground(applicationContext, jobId, "Inpainting local · preparando motor"))
            val longest = inputData.getInt("size", 512).also { require(it in setOf(256, 512)) }
            val scale = longest.toFloat() / maxOf(source.width, source.height)
            val width = ((source.width * scale / 64).toInt() * 64).coerceIn(64, longest)
            val height = ((source.height * scale / 64).toInt() * 64).coerceIn(64, longest)
            val small = Bitmap.createScaledBitmap(source, width, height, true)
            val smallMask = Bitmap.createScaledBitmap(mask, width, height, true)
            val pixels = IntArray(width * height)
            val coverage = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            smallMask.getPixels(coverage, 0, width, 0, 0, width, height)
            val rgb = ByteArray(pixels.size * 3) { index -> (pixels[index / 3] ushr (16 - index % 3 * 8)).toByte() }
            val alpha = ByteArray(coverage.size) { index -> (coverage[index] ushr 24).toByte() }
            val steps = inputData.getInt("steps", 20)
            InpaintContract.validate(width, height, alpha, prompt, steps)
            currentCoroutineContext().ensureActive()
            check(InpaintNative.start(models.folder.path, prompt.toByteArray(Charsets.UTF_8), rgb, alpha, width, height, steps, inputData.getLong("seed", 42))) { "Hay una edición local en curso o la entrada no es compatible." }
            started = true
            val startedAt = android.os.SystemClock.elapsedRealtime()
            while (InpaintNative.status() in 1..3) {
                setProgress(workDataOf("percent" to InpaintNative.progress(), "elapsed" to (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000,
                    "phase" to if(InpaintNative.status() == 1) "Cargando modelo" else "Rellenando región"))
                delay(1000)
            }
            currentCoroutineContext().ensureActive()
            check(InpaintNative.status() == 4) { if(InpaintNative.status() == 6) "Edición cancelada." else "El motor local no pudo generar. Revisa la memoria disponible y los archivos del modelo." }
            val output = checkNotNull(InpaintNative.result()) { "El motor no devolvió imagen." }
            check(output.size == width * height * 3)
            val generatedPixels = IntArray(width * height) { index ->
                -0x1000000 or ((output[index * 3].toInt() and 255) shl 16) or ((output[index * 3 + 1].toInt() and 255) shl 8) or (output[index * 3 + 2].toInt() and 255)
            }
            val generated = Bitmap.createBitmap(generatedPixels, width, height, Bitmap.Config.ARGB_8888)
            val restored = Bitmap.createScaledBitmap(generated, source.width, source.height, true)
            val merged = source.copy(Bitmap.Config.ARGB_8888, true)
            val originalRow = IntArray(source.width)
            val generatedRow = IntArray(source.width)
            val maskRow = IntArray(source.width)
            for (row in 0 until source.height) {
                currentCoroutineContext().ensureActive()
                source.getPixels(originalRow, 0, source.width, 0, row, source.width, 1)
                restored.getPixels(generatedRow, 0, source.width, 0, row, source.width, 1)
                mask.getPixels(maskRow, 0, source.width, 0, row, source.width, 1)
                for (column in originalRow.indices) originalRow[column] = InpaintContract.blend(originalRow[column], generatedRow[column], maskRow[column] ushr 24)
                merged.setPixels(originalRow, 0, source.width, 0, row, source.width, 1)
            }
            val temporary = File(folder, "result.part")
            temporary.outputStream().use { check(merged.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(File(folder, "result.png"))) { "No se pudo guardar el resultado." }
            Result.success(workDataOf("job" to jobId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(workDataOf("error" to (failure.message ?: "La edición local no se pudo completar.")))
        } finally {
            if(started && InpaintNative.status() in 1..3) {
                InpaintNative.cancel()
                withContext(NonCancellable) { while(InpaintNative.status() in 1..3) delay(200) }
            }
        }
    }
}
