package com.vidadomu.nexus.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

internal fun generationStatusLabel(job:GenerationJob):String =
    if(job.parameters["__tracking_paused"]=="true") "Seguimiento pausado"
    else if(job.parameters["__submission_uncertain"]=="true") "Por confirmar"
    else if(job.parameters["__tracking_poll_error"]=="true") "Consulta interrumpida"
    else when(job.status) {
        GenerationStatus.COMPLETED->"Completado"
        GenerationStatus.FAILED->"Fallido"
        GenerationStatus.CANCELLED->"Cancelado"
        GenerationStatus.QUEUED->"En cola"
        GenerationStatus.GENERATING->"Generando"
        GenerationStatus.PROCESSING->"Procesando"
        GenerationStatus.RENDERING->"Renderizando"
        GenerationStatus.UPLOADING->"Subiendo"
    }

/** Only formats existing values. It does not resolve, refresh or compute charges. */
internal fun confirmedJobCost(job:GenerationJob,currency:CurrencyManager):String {
    val cost=job.actualCostUsd?.takeIf {it.isFinite() && it>=0}
    val credits=job.parameters["__actual_credits"]?.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0}
    return if(job.providerId=="mock_provider") "Sin cargo" else cost?.let(currency::formatCost)
        ?: credits?.let(currency::formatCredits) ?: "Por confirmar"
}

@Composable
fun GenerationSummaryRow(job:GenerationJob,currency:CurrencyManager,onOpen:()->Unit,
    menu:@Composable (close:()->Unit)->Unit) {
    val app=LocalContext.current.applicationContext as NexusApplication
    val models by app.modelCatalogRepository.models.collectAsState()
    val currencyState by currency.state.collectAsState()
    val costText=remember(job,currencyState) {confirmedJobCost(job,currency)}
    val jobs by app.generationRepository.allJobs.collectAsState()
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val model=remember(job.modelId,models) {app.modelCatalogRepository.getModelById(job.modelId)?.displayName
        ?: job.modelId.substringAfterLast('/').replace('-',' ').replace('_',' ')}
    val type=when(job.modality) {Modality.IMAGE->"Imagen";Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.CHAT->"Texto"}
    val time=remember(job.startedAt) {SimpleDateFormat("HH:mm",Locale("es","ES")).format(Date(job.startedAt))}
    val finished=job.status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)
    var menuOpen by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).clickable(onClick=onOpen)
        .padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Box(Modifier.size(NexusTokens.minimumTouch).clip(NexusTokens.thumbnail).background(MaterialTheme.colorScheme.surfaceContainer),contentAlignment=Alignment.Center) {
            val output=job.outputs.firstOrNull()
            if(job.status==GenerationStatus.COMPLETED && output!=null && job.modality==Modality.IMAGE) {
                AsyncImage(Uri.parse(OutputFiles.trustedLocalUri(output,conflicts) ?: output.url),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            } else if(!finished) CircularProgressIndicator(Modifier.size(NexusTokens.space24),strokeWidth=2.dp)
            else Icon(when(job.status) {
                GenerationStatus.FAILED->Icons.Default.ErrorOutline
                GenerationStatus.CANCELLED->Icons.Default.Cancel
                else->when(job.modality) {Modality.VIDEO->Icons.Default.Movie;Modality.AUDIO->Icons.Default.GraphicEq;Modality.CHAT->Icons.Default.ChatBubbleOutline;else->Icons.Default.Image}
            },generationStatusLabel(job),tint=if(job.status==GenerationStatus.FAILED) MaterialTheme.colorScheme.error else TextSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(job.prompt.ifBlank {"Sin instrucción textual"},style=MaterialTheme.typography.bodyMedium,color=TextPrimary,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("$model · $type · $time",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            if(!finished || job.status==GenerationStatus.FAILED) Text(generationStatusLabel(job),style=MaterialTheme.typography.labelMedium,
                color=if(job.status==GenerationStatus.FAILED) MaterialTheme.colorScheme.error else TextSecondary)
        }
        Text(costText,modifier=Modifier.widthIn(max=92.dp),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
        Box {
            IconButton(onClick={menuOpen=true}) {Icon(Icons.Default.MoreVert,"Opciones de creación ${job.id}",tint=TextSecondary)}
            DropdownMenu(menuOpen,{menuOpen=false}) { menu {menuOpen=false} }
        }
    }
}
