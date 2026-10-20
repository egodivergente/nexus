package com.vidadomu.nexus.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lote desde archivo: lee prompts de un CSV, TXT o Excel y lanza un trabajo por prompt con el modelo
 * y ajustes actuales del Lienzo. El botón muestra el número de trabajos y el coste total estimado;
 * pulsarlo es la confirmación del lote (Flow utiliza las condiciones de tu cuenta).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PromptBatchSheet(state:CreateUiState,unitCost:String,unitUsd:Double?,viewModel:CreateViewModel,
    onDismiss:()->Unit,onFinished:(String)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val app=context.applicationContext as com.vidadomu.nexus.NexusApplication
    val settings=remember(app) {com.vidadomu.nexus.assistant.AssistantRuntime.get(app).settings}
    val batchLimit=settings.effectiveLimits(settings.load()).maxGenerationsPerBatch
    var attempted by remember {mutableStateOf(false)}
    var prompts by remember {mutableStateOf(emptyList<String>())}
    var fileName by remember {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var progress by remember {mutableStateOf<Pair<Int,Int>?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
        if(uri==null) return@rememberLauncherForActivityResult
        scope.launch {
            error=null
            try {
                val (name,bytes)=withContext(Dispatchers.IO) {
                    val name=context.contentResolver.query(uri,arrayOf("_display_name"),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null}
                        ?: uri.lastPathSegment ?: "lote.txt"
                    val bytes=context.contentResolver.openInputStream(uri)?.use {PromptBatch.readInput(it)} ?: error("Android no permitió leer el archivo.")
                    require(bytes.size<=5*1024*1024) {"El archivo supera 5 MB."}
                    name to bytes
                }
                attempted=false
                fileName=name
                prompts=PromptBatch.parse(name,bytes,batchLimit)
                if(prompts.isEmpty()) error="No se encontró ningún prompt en el archivo."
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
              catch(e:Exception) {error=e.message ?: "No se pudo leer el archivo."}
        }
    }
    val model=state.selectedModel
    val isFlow=model?.providerId=="google_flow"
    val readiness=creationReadiness(state.copy(prompt=prompts.firstOrNull() ?: "x"))
    val sending=progress!=null
    ModalBottomSheet(onDismissRequest={if(!sending) onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal=NexusTokens.screenMargin),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            Text("Lote desde archivo",style=MaterialTheme.typography.titleLarge)
            Text("CSV o Excel (columna «prompt» o la primera), o TXT con un prompt por bloque. Usa el modelo y los ajustes actuales"+
                (model?.let {": ${it.displayName}."} ?: "."),style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick={picker.launch(arrayOf("text/*","text/csv","text/comma-separated-values","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"))},
                enabled=!sending) {Text(fileName?.let {"Cambiar archivo ($it)"} ?: "Elegir archivo")}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
            progress?.let {(done,total) ->
                LinearProgressIndicator(progress={done/total.toFloat()},modifier=Modifier.fillMaxWidth())
                Text("Enviando ${done+1} de $total…",style=MaterialTheme.typography.labelMedium)
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(prompts) {index,prompt ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("${index+1}. $prompt",Modifier.weight(1f).padding(vertical=NexusTokens.space8),maxLines=3,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.bodyMedium)
                        IconButton(onClick={prompts=prompts.filterIndexed {i,_ -> i!=index}},enabled=!sending) {Icon(Icons.Default.Close,"Quitar")}
                    }
                }
            }
            if (batchLimit > 0) Text("Límite del lote: $batchLimit (Ajustes → Límites)",style=MaterialTheme.typography.labelMedium)
            if (attempted && !sending) Text("Revisa los trabajos enviados en Historial antes de iniciar otro lote.",style=MaterialTheme.typography.bodyMedium)
            if(prompts.isNotEmpty() && !readiness.canSubmit) readiness.message?.let {Text(it,style=MaterialTheme.typography.labelMedium)}
            val total=when {
                isFlow -> "cuenta de Flow"
                unitUsd!=null -> "${prompts.size} × $unitCost"
                else -> "coste por trabajo: $unitCost"
            }
            Button(onClick={
                    attempted=true
                    viewModel.submitBatch(prompts,{done,size -> progress=done to size}) {sent,firstError ->
                        progress=null
                        val summary="Lote: $sent de ${prompts.size} trabajos enviados"+(firstError?.let {". Primer fallo: $it"} ?: "")
                        if(sent==prompts.size) onFinished(summary) else error=summary
                    }
                },enabled=prompts.isNotEmpty() && readiness.canSubmit && !sending && !state.isSubmitting && !attempted,
                modifier=Modifier.fillMaxWidth().padding(bottom=NexusTokens.space16)) {
                Text(if(prompts.isEmpty()) "Lanzar lote" else "Lanzar ${prompts.size} · $total")
            }
        }
    }
}
