package com.vidadomu.nexus.inpaint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class LocalInpaintViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val preferences = context.getSharedPreferences("local-inpaint", 0)
    private val manager = WorkManager.getInstance(context)
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    var source by mutableStateOf<Bitmap?>(null)
        private set
    var mask by mutableStateOf<Bitmap?>(null)
    var result by mutableStateOf<Bitmap?>(null)
        private set
    var prompt by mutableStateOf(preferences.getString("prompt", "").orEmpty())
    var busy by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    private var draft: File? = null
    private var resultFile: File? = null
    private val maskRevision = java.util.concurrent.atomic.AtomicLong()

    init {
        viewModelScope.launch {
            ready = withContext(Dispatchers.IO) { InpaintModelStore(context).ready() }
            preferences.getString("draft", null)?.takeIf { Regex("[a-f0-9-]{36}").matches(it) }?.let { id ->
                val folder = File(context.filesDir, "inpaint-jobs/$id")
                val restored = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(File(folder, "source.png").path) to BitmapFactory.decodeFile(File(folder, "mask.png").path)
                }
                draft = folder
                source = restored.first
                mask = restored.second?.copy(Bitmap.Config.ARGB_8888, true)
            }
            launch {
                manager.getWorkInfosForUniqueWorkFlow("inpaint-download").collect { works ->
                    val work = selectInpaintWork(works, preferences.getString("downloadWorkId", null),
                        { it.id.toString() }, { it.state.isFinished }) ?: return@collect
                    downloading = !work.state.isFinished
                    ready = withContext(Dispatchers.IO) { InpaintModelStore(context).ready() }
                    if(downloading) mutableMessage.value = "Descargando modelo: ${work.progress.getInt("percent", 0)}%"
                    else if(work.state == WorkInfo.State.FAILED) mutableMessage.value = work.outputData.getString("error").orEmpty()
                }
            }
            manager.getWorkInfosForUniqueWorkFlow("local-inpaint").collect { works ->
                val work = selectInpaintWork(works, preferences.getString("generationWorkId", null),
                    { it.id.toString() }, { it.state.isFinished }) ?: return@collect
                busy = !work.state.isFinished
                when(work.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        work.outputData.getString("job")?.takeIf { it == draft?.name && Regex("[a-f0-9-]{36}").matches(it) }?.let { id ->
                            resultFile = File(context.filesDir, "inpaint-jobs/$id/result.png")
                            result = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(resultFile!!.path) }
                        }
                        if(result != null) mutableMessage.value = "Edición terminada. El original no se ha modificado. Guarda una copia."
                    }
                    WorkInfo.State.FAILED -> mutableMessage.value = work.outputData.getString("error") ?: "La edición local falló."
                    WorkInfo.State.CANCELLED -> mutableMessage.value = "Cancelado. El motor puede tardar en liberar el modelo."
                    else -> mutableMessage.value = "${work.progress.getString("phase") ?: "Preparando edición"} · ${work.progress.getInt("percent", 0)}% · ${work.progress.getLong("elapsed", 0)} s"
                }
            }
        }
    }

    fun open(uri: Uri) {
        if(busy) return
        busy = true
        viewModelScope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    check(Build.VERSION.SDK_INT >= 28) { "Este editor requiere Android 9 o posterior." }
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        require(info.size.width.toLong() * info.size.height <= 4_194_304) { "La imagen supera 4 megapíxeles. Exporta una copia reducida; no se modificará el original." }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                    val folder = File(context.filesDir, "inpaint-jobs/${UUID.randomUUID()}").apply { check(mkdirs()) }
                    val emptyMask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                    writeBitmap(File(folder, "source.png"), bitmap)
                    writeBitmap(File(folder, "mask.png"), emptyMask)
                    Triple(folder, bitmap, emptyMask)
                }
                draft = imported.first
                source = imported.second
                mask = imported.third
                result = null
                resultFile = null
                preferences.edit().putString("draft", imported.first.name).apply()
                mutableMessage.value = "Pinta la región que quieres editar. Fuera de la máscara se conserva el original."
            } catch(cancelled: CancellationException) { throw cancelled
            } catch(failure: Exception) { mutableMessage.value = failure.message ?: "No se pudo abrir la imagen."
            } finally { busy = false }
        }
    }

    fun persistMask() {
        val folder = draft ?: return
        val snapshot = mask?.copy(Bitmap.Config.ARGB_8888, false) ?: return
        val revision = maskRevision.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try { synchronized(this@LocalInpaintViewModel) { if(maskRevision.get() == revision) writeBitmap(File(folder, "mask.png"), snapshot) } }
            catch(_: Exception) { mutableMessage.value = "No se pudo conservar el borrador de la máscara." }
            finally { snapshot.recycle() }
        }
    }

    fun download() {
        try { InpaintModelStore(context).enqueue() }
        catch(_: Exception) { mutableMessage.value = "No se pudo iniciar la descarga del modelo." }
    }
    fun updatePrompt(value: String) {
        prompt = value
        preferences.edit().putString("prompt", value).apply()
    }
    fun cancel() {
        manager.cancelUniqueWork(if(downloading) "inpaint-download" else "local-inpaint")
        if(InpaintNative.available()) InpaintNative.cancel()
        mutableMessage.value = "Cancelando; la carga del modelo debe terminar antes de liberar memoria."
    }

    fun run(size: Int, steps: Int) {
        if(busy || downloading || prompt.isBlank()) return
        val original = source ?: return
        val selection = mask?.copy(Bitmap.Config.ARGB_8888, false) ?: return
        val instruction = prompt
        preferences.edit().putString("prompt", instruction).apply()
        result = null
        resultFile = null
        busy = true
        viewModelScope.launch {
            try {
                val folder = withContext(Dispatchers.IO) {
                    File(context.filesDir, "inpaint-jobs/${UUID.randomUUID()}").apply {
                        check(mkdirs())
                        writeBitmap(File(this, "source.png"), original)
                        writeBitmap(File(this, "mask.png"), selection)
                        File(this, "prompt.txt").writeText(instruction)
                    }
                }
                val request = OneTimeWorkRequestBuilder<InpaintWorker>()
                    .setInputData(workDataOf("job" to folder.name, "size" to size, "steps" to steps, "seed" to System.nanoTime())).build()
                // Persist the exact run before enqueueing: restores the matching source/mask after process death.
                withContext(Dispatchers.IO) {
                    check(preferences.edit().putString("generationWorkId", request.id.toString())
                        .putString("draft", folder.name).commit()) { "No se pudo conservar la edición." }
                }
                draft = folder
                withContext(Dispatchers.IO) {
                    manager.enqueueUniqueWork("local-inpaint", ExistingWorkPolicy.KEEP, request).result.get()
                }
            } catch(cancelled: CancellationException) { throw cancelled
            } catch(failure: Exception) { mutableMessage.value = failure.message ?: "No se pudo iniciar la edición."; busy = false
            } finally { selection.recycle() }
        }
    }

    fun save(uri: Uri) {
        val file = resultFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("No se pudo abrir el destino.")
                mutableMessage.value = "Copia PNG guardada."
            } catch(failure: Exception) { mutableMessage.value = failure.message ?: "No se pudo guardar la copia." }
        }
    }

    private fun writeBitmap(file: File, bitmap: Bitmap) {
        synchronized(this) {
            val stage = File(file.parentFile, file.name + ".part")
            stage.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }
            check(stage.renameTo(file)) { "No se pudo conservar el archivo local." }
        }
    }
}

@Composable
fun LocalInpaintScreen(onBack: () -> Unit, state: LocalInpaintViewModel = viewModel(), initialUri: String? = null) {
    // Opened from «Editar foto» (Biblioteca/visor): load that image once; a restored session is not replaced.
    androidx.compose.runtime.LaunchedEffect(initialUri) { if (initialUri != null && state.source == null) state.open(android.net.Uri.parse(initialUri)) }
    val message by state.message.collectAsState()
    var editor by remember { mutableStateOf<MaskPaintView?>(null) }
    var erasing by remember { mutableStateOf(false) }
    var stylusOnly by remember { mutableStateOf(true) }
    var brush by remember { mutableFloatStateOf(24f) }
    var size by remember { mutableIntStateOf(256) }
    var steps by remember { mutableFloatStateOf(20f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(state::open) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let(state::save) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick=onBack) { Text("Volver a ArtBox") }
        Text("Editar región · local", style=MaterialTheme.typography.headlineSmall)
        Text("Motor CPU ARM64 · SD 1.5 Inpainting. Integración experimental: la generación en S25 aún no está validada. Sin API ni créditos. El texto describe el resultado deseado; no garantiza una edición exacta.")
        Text("Modelo stable-diffusion-v1-5/stable-diffusion-inpainting (mirror de Runway) · licencia CreativeML Open RAIL-M · descarga 2,13 GB con SHA-256. Reserva de seguridad: 3 GiB de RAM libre.")
        if(!state.ready) Button(onClick=state::download, enabled=!state.downloading && !state.busy) { Text("Descargar y verificar modelo") }
        OutlinedButton(onClick={picker.launch(arrayOf("image/*"))}, enabled=!state.busy) { Text("Abrir imagen (hasta 4 MP)") }
        state.source?.let { bitmap ->
            key(bitmap) {
                AndroidView(factory={ context -> MaskPaintView(context).also { view ->
                    editor = view
                    view.contentDescription = "Lienzo de máscara. Pinta con S Pen la región a editar."
                    view.image = bitmap
                    view.mask = state.mask
                    view.changed = { state.mask = view.mask; state.persistMask() }
                } }, modifier=Modifier.fillMaxWidth().height(360.dp), update={ view ->
                    view.erasing = erasing; view.brush = brush; view.stylusOnly = stylusOnly; view.isEnabled = !state.busy
                })
            }
            Row { Checkbox(stylusOnly, {stylusOnly=it}, enabled=!state.busy); Text("Solo S Pen (rechaza la palma)") }
            Row { Checkbox(erasing, {erasing=it}, enabled=!state.busy); Text("Borrar máscara") }
            Text("Tamaño del pincel")
            Slider(brush, {brush=it}, valueRange=4f..80f, enabled=!state.busy)
            Row {
                TextButton(onClick={editor?.undoStroke()}, enabled=!state.busy) { Text("Deshacer") }
                TextButton(onClick={editor?.clearMask()}, enabled=!state.busy) { Text("Limpiar máscara") }
            }
            OutlinedTextField(state.prompt, state::updatePrompt, label={Text("Resultado deseado: añadir, cambiar o eliminar")}, modifier=Modifier.fillMaxWidth(), enabled=!state.busy, minLines=3)
            Row { Checkbox(size==512, {size=if(it) 512 else 256}, enabled=!state.busy); Text("512 px (más memoria y tiempo); por defecto 256 px") }
            Text("Pasos: ${steps.toInt()}")
            Slider(steps, {steps=it}, valueRange=4f..40f, steps=35, enabled=!state.busy)
            Button(onClick={state.run(size,steps.toInt())}, enabled=state.ready && !state.busy && !state.downloading && state.prompt.isNotBlank()) { Text("Editar en este dispositivo") }
        }
        if(state.busy || state.downloading) OutlinedButton(onClick=state::cancel) { Text("Cancelar") }
        if(message.isNotBlank()) Text(message)
        state.result?.let { bitmap ->
            Image(bitmap.asImageBitmap(), "Resultado local", modifier=Modifier.fillMaxWidth().height(320.dp))
            Button(onClick={saver.launch("artbox-inpainting.png")}) { Text("Guardar copia PNG") }
        }
    }
}
