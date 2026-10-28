@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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

/** Only formats existing values. It does not resolve, refresh or compute charges.
 * Una sola unidad de coste en la fila: la moneda si existe; solo si el proveedor
 * informó únicamente créditos, se muestran los créditos (nunca ambos). */
internal fun confirmedJobCost(job:GenerationJob,currency:CurrencyManager):String {
    val cost=job.actualCostUsd?.takeIf {it.isFinite() && it>=0}
    val credits=job.parameters["__actual_credits"]?.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0}
    return if(job.providerId=="mock_provider") "Sin cargo" else cost?.let(currency::formatCost)
        ?: credits?.let(currency::formatCredits) ?: "Por confirmar"
}

/** Importe sin límite de líneas: con texto grande la fila crece en vez de ocultar cifras. */
@Composable
internal fun ConfirmedGenerationCost(text:String,modifier:Modifier=Modifier) {
    Text(text,modifier=modifier.widthIn(max=96.dp).semantics {contentDescription="Coste: $text"},
        style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
}

@Composable
fun GenerationSummaryRow(job:GenerationJob,currency:CurrencyManager,onOpen:()->Unit,onRetry:()->Unit={},onDiscard:(()->Unit)?=null,
    variantLabels:Map<String,String>?=null,menu:@Composable (close:()->Unit)->Unit) {
    val app=LocalContext.current.applicationContext as NexusApplication
    val models by app.modelCatalogRepository.models.collectAsState()
    val currencyState by currency.state.collectAsState()
    val costText=remember(job,currencyState) {confirmedJobCost(job,currency)}
    val jobs by app.generationRepository.allJobs.collectAsState()
    val historyLoading by app.generationRepository.historyLoading.collectAsState()
    val conflicts=remember(jobs) {OutputFiles.conflictingLocalUris(jobs.flatMap {it.outputs})}
    val model=remember(job.modelId,models) {app.modelCatalogRepository.getModelById(job.modelId)?.displayName
        ?: job.modelId.substringAfterLast('/').replace('-',' ').replace('_',' ')}
    val type=when(job.modality) {Modality.IMAGE->"Imagen";Modality.VIDEO->"Vídeo";Modality.AUDIO->"Audio";Modality.CHAT->"Texto"}
    val time=remember(job.startedAt) {SimpleDateFormat("HH:mm",Locale("es","ES")).format(Date(job.startedAt))}
    val finished=job.status in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)
    var menuOpen by remember {mutableStateOf(false)}
    val context=LocalContext.current
    // Título útil: la primera línea real del encargo (sin rótulos de plantilla) o, si no hay texto, el modelo.
    val title=remember(job.prompt,model) {generationRowTitle(job.prompt,model)}
    // Con el mapa del snapshot (Historial) no se recorre la lista por fila; sin él, cálculo puntual de esta fila.
    val variant=remember(job,jobs,historyLoading,variantLabels) {
        if(variantLabels!=null) variantLabels[job.id] else generationVariantLabel(job,jobs,historyComplete=!historyLoading)
    }
    // Estado y hora primero: un nombre de modelo largo no puede empujarlos fuera de la línea.
    // Un fallo muestra su motivo (una línea) en vez de la ficha habitual; el detalle técnico sigue en Diagnóstico.
    val failureReason=job.error?.trim()?.takeIf {it.isNotBlank()}
    val detail=if(job.status==GenerationStatus.FAILED && failureReason!=null) listOfNotNull(variant,failureReason).joinToString(" · ")
        else listOfNotNull(generationStatusLabel(job).takeIf {!finished || job.status!=GenerationStatus.COMPLETED},time,variant,
        model.takeIf {title!=model},type).joinToString(" · ")
    // Solo un coste real; un trabajo fallido o en curso no muestra «Por confirmar».
    val cost=costText.takeIf {job.status==GenerationStatus.COMPLETED && it!="Por confirmar"}
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).combinedClickable(onClick=onOpen,onLongClick={menuOpen=true},onLongClickLabel="Opciones del resultado")
        .padding(start=NexusTokens.screenMargin,end=NexusTokens.space4,top=6.dp,bottom=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        Box(Modifier.size(NexusTokens.minimumTouch).clip(NexusTokens.thumbnail).background(MaterialTheme.colorScheme.surfaceContainer),contentAlignment=Alignment.Center) {
            val output=job.outputs.firstOrNull()
            if(job.status==GenerationStatus.COMPLETED && output!=null && job.modality in setOf(Modality.IMAGE,Modality.VIDEO)) {
                AsyncImage(coil.request.ImageRequest.Builder(context).data(Uri.parse(OutputFiles.trustedLocalUri(output,conflicts) ?: output.url)).crossfade(120)
                    .apply {if(job.modality==Modality.VIDEO) decoderFactory {result,options,_->coil.decode.VideoFrameDecoder(result.source,options)}}.build(),
                    null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            } else if(!finished) CircularProgressIndicator(Modifier.size(NexusTokens.space24),strokeWidth=2.dp)
            else Icon(when(job.status) {
                GenerationStatus.FAILED->Icons.Default.ErrorOutline
                GenerationStatus.CANCELLED->Icons.Default.Cancel
                else->when(job.modality) {Modality.VIDEO->Icons.Default.Movie;Modality.AUDIO->Icons.Default.GraphicEq;Modality.CHAT->Icons.Default.ChatBubbleOutline;else->Icons.Default.Image}
            },generationStatusLabel(job),tint=if(job.status==GenerationStatus.FAILED) MaterialTheme.colorScheme.error else TextSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.bodyMedium,color=TextPrimary,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(detail,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis,
                color=if(job.status==GenerationStatus.FAILED) MaterialTheme.colorScheme.error else TextSecondary)
        }
        if(job.status==GenerationStatus.FAILED) TextButton(onClick=onRetry,contentPadding=PaddingValues(horizontal=NexusTokens.space8)) {
            Text("Reintentar",style=MaterialTheme.typography.labelMedium)
        }
        // Un encargo que lleva más de 15 min «en cola» (o con el seguimiento pausado) se puede apartar sin borrar nada.
        val stuck=!finished && onDiscard!=null && (System.currentTimeMillis()-job.startedAt>15*60_000L || job.parameters["__tracking_paused"]=="true")
        if(stuck) TextButton(onClick=onDiscard!!,contentPadding=PaddingValues(horizontal=NexusTokens.space8)) {
            Text("Descartar",style=MaterialTheme.typography.labelMedium)
        }
        cost?.let {ConfirmedGenerationCost(it)}
        Box {
            IconButton(onClick={menuOpen=true}) {Icon(Icons.Default.MoreVert,"Opciones de «${title.take(40)}», $time${variant?.let {", $it"}.orEmpty()}",tint=TextSecondary)}
            DropdownMenu(menuOpen,{menuOpen=false}) { menu {menuOpen=false} }
        }
    }
}
