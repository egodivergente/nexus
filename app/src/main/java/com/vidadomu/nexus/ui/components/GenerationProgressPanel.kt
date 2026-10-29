package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Only a provider's measured progress is a percentage; stages and elapsed time are not progress. */
@Composable
fun GenerationProgressPanel(job: GenerationJob, modifier: Modifier = Modifier, compact: Boolean = false) {
    val active=job.status !in setOf(GenerationStatus.COMPLETED,GenerationStatus.FAILED,GenerationStatus.CANCELLED)
    var now by remember(job.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(job.id,active) {while(active) {now=System.currentTimeMillis();delay(1000)}}
    // Flow currently supplies fixed milestones rather than a measured percentage.
    val progress=job.progress?.takeIf {it.isFinite() && it in 0f..1f && !job.providerId.contains("flow",ignoreCase=true)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val history by (context.applicationContext as com.vidadomu.nexus.NexusApplication).generationRepository.allJobs.collectAsState()
    val samples=remember(history,job.modelId,job.providerId) {history.filter {
        it.status==GenerationStatus.COMPLETED && it.modelId==job.modelId && it.providerId==job.providerId && it.completedAt!=null && it.completedAt>it.startedAt
    }.sortedByDescending {it.startedAt}.take(20).map {(it.completedAt!!-it.startedAt)/1000}.filter {it>0}.sorted()}
    val typical=samples.takeIf {it.size>=3}?.let {it[it.size/2]}
    val elapsed=((now-job.startedAt).coerceAtLeast(0)/1000)
    val remaining=typical?.minus(elapsed)
    val timeLabel=when {
        remaining==null -> "Tiempo restante no disponible"
        remaining<=0 -> "Superando el tiempo habitual; sigue en curso"
        else -> "Restante ≈ ${remaining/60} min ${remaining%60} s (estimado con ${samples.size} encargos)"
    }
    val stage=when {
        job.parameters["__tracking_paused"]=="true" -> "Seguimiento pausado"
        job.parameters["__submission_uncertain"]=="true" -> "Envío por confirmar"
        else -> when(job.status) {
            GenerationStatus.QUEUED -> if(job.providerJobId.isNullOrBlank()) "Enviando encargo" else "En cola"
            GenerationStatus.UPLOADING -> "Subiendo referencias"
            GenerationStatus.GENERATING -> "Generando"
            GenerationStatus.PROCESSING -> "Procesando"
            GenerationStatus.RENDERING -> "Renderizando"
            GenerationStatus.COMPLETED -> "Completado"
            GenerationStatus.FAILED -> "No se pudo completar"
            GenerationStatus.CANCELLED -> "Cancelado"
        }
    }
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.padding(if(compact) NexusTokens.space12 else 18.dp),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            Text(stage,style=MaterialTheme.typography.titleSmall)
            if(active && progress!=null) {
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                Text("${(progress*100).roundToInt()}% completado · ${(100-progress*100).roundToInt()}% pendiente",style=MaterialTheme.typography.labelMedium)
            } else if(active) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            if(active) Text("${elapsed/60} min ${elapsed%60} s transcurridos · ${timeLabel}",style=MaterialTheme.typography.labelSmall)
            Text(job.modelId,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            job.error?.takeIf {it.isNotBlank()}?.let { message -> InlineNotice(noticeMessage(message,"No se pudo actualizar este trabajo. Consulta su estado antes de repetir."),technicalDetails=message) }
        }
    }
}
