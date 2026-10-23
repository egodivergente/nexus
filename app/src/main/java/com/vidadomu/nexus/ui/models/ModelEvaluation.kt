package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.NexusModel
import java.text.DateFormat
import java.util.Date

private val evaluationCriteria=listOf("Calidad","Fotorealismo","Identidad","Física","Cámara","Audio","Fidelidad al prompt","Velocidad","Valor por coste")

/** Evidence for each exact destination; result scores are never inferred from parameter counts. */
@Composable internal fun ModelEvaluation(model: NexusModel, alternatives: List<NexusModel> = emptyList()) {
    val context=LocalContext.current
    var endpointEvidence by remember(model.id,model.providerId) {mutableStateOf<EndpointEvidence?>(null)}
    var differences by remember(model.id,model.providerId) {mutableStateOf(emptyList<String>())}
    LaunchedEffect(model.id,model.providerId) {endpointEvidence=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {EndpointEvaluations.evidence(context,model)}}
    val evaluation=remember(model,endpointEvidence) {EndpointEvaluations.evaluate(model,endpointEvidence)}
    LaunchedEffect(model,endpointEvidence,alternatives) {
        differences=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val others=alternatives.filter {it.id!=model.id || it.providerId!=model.providerId}.map {EndpointEvaluations.evaluate(it,EndpointEvaluations.evidence(context,it))}
            evaluation.facts.filter {fact->others.any {other->other.facts.firstOrNull {it.first==fact.first}?.second?.let {it!=fact.second}==true}}
                .map {"Diferencia de esta variante · ${it.first}: ${it.second}"}
        }
    }
    var factsOpen by remember {mutableStateOf(false)}
    val uriHandler=androidx.compose.ui.platform.LocalUriHandler.current
    val prefs=remember {context.getSharedPreferences("nexus_model_evaluations",0)}
    val key=modelBookmark(model)
    var revision by remember {mutableIntStateOf(0)}
    val values=remember(key,revision) {evaluationCriteria.associateWith {prefs.getString("$key:$it",null)}}
    val known=values.values.mapNotNull {it?.toDoubleOrNull()}
    var open by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("Evaluación del modelo",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("%.1f / 10 · controles de este endpoint".format(evaluation.score),style=MaterialTheme.typography.titleMedium)
        evaluation.facts.take(if(factsOpen) Int.MAX_VALUE else 4).forEach {(label,value)->
            Text("$label · $value",style=MaterialTheme.typography.bodySmall)
        }
        ModelResearchNotes(model)
        differences.take(2).forEach {Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        TextButton(onClick={factsOpen=!factsOpen},contentPadding=PaddingValues(0.dp)) {Text(if(factsOpen) "Menos detalle" else "Evaluación completa y fuentes")}
        if(factsOpen) {
            evaluation.axes.forEach {axis->Text("${axis.title}: ${"%.1f".format(axis.score)}/10 · ${axis.reason}",style=MaterialTheme.typography.bodySmall)}
            differences.drop(2).forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
            Text("Índice editorial de controles publicados; no equivale a un benchmark de calidad visual. Formato, medios, ajuste y tiempo se puntúan por los controles expuestos. La calidad del resultado se evalúa por separado.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(evaluation.checkedAt.take(10).ifBlank {"Contrato integrado · revisión 23 oct 2026"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(evaluation.source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(evaluation.source.startsWith("https://")) TextButton(onClick={uriHandler.openUri(evaluation.source)}) {Text("Abrir contrato consultado")}
        }
        if(known.isNotEmpty()) Text("%.1f / 10 · tu evaluación de resultados".format(known.average()),style=MaterialTheme.typography.bodyMedium)
        if(known.isNotEmpty()) Text("${known.size} de ${evaluationCriteria.size} criterios · "+DateFormat.getDateInstance().format(Date(prefs.getLong("$key:at",0))),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={open=true},contentPadding=PaddingValues(0.dp)) {Text(if(known.isEmpty()) "Añadir evaluación de mis resultados" else "Ver y editar criterios")}
    }
    if(open) {
        val draft=remember(key) {mutableStateMapOf<String,String>().apply {evaluationCriteria.forEach {put(it,values[it].orEmpty())}}}
        var source by remember(key) {mutableStateOf(prefs.getString("$key:source","").orEmpty())}
        val valid=draft.values.all {it.isBlank() || it.toDoubleOrNull()?.let {v->v.isFinite() && v in 0.0..10.0}==true}
        AlertDialog(onDismissRequest={open=false},title={Text("Evaluación editorial · 0–10")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Puntúa solo lo que hayas probado; deja el resto vacío. Estas notas son personales.",style=MaterialTheme.typography.bodySmall)
            evaluationCriteria.forEach {criterion->OutlinedTextField(draft[criterion].orEmpty(),{draft[criterion]=it},label={Text(criterion)},singleLine=true,modifier=Modifier.fillMaxWidth())}
            OutlinedTextField(source,{source=it},label={Text("Resultado o fuente de tu evaluación")},modifier=Modifier.fillMaxWidth())
        }},confirmButton={TextButton(enabled=valid,onClick={
            prefs.edit().apply {draft.forEach {(k,v)->if(v.isBlank()) remove("$key:$k") else putString("$key:$k",v)};putString("$key:source",source);putLong("$key:at",System.currentTimeMillis())}.apply()
            revision++;open=false
        }) {Text("Guardar")}},dismissButton={TextButton(onClick={open=false}) {Text("Cancelar")}})
    }
}
