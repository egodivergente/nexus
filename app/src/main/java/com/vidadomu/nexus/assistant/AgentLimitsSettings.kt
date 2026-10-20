package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

@Composable
internal fun AgentLimitsSettings() {
    val app=LocalContext.current.applicationContext as NexusApplication
    val store=remember {AssistantRuntime.get(app).settings}
    val original=remember {AgentLimitForm.values(store.limits())}
    val order=remember {original.keys.toList()}
    val booleanKeys=remember {original.filterValues {it=="true" || it=="false"}.keys}
    val values=remember {mutableStateMapOf<String,String>().apply {putAll(original)}}
    var expanded by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<Pair<String,Boolean>?>(null)}
    FoldHeader("Límites del agente","Configurables · 0 = sin límite",expanded) {expanded=!expanded}
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Text("Tus valores mandan sobre las instrucciones del agente. Cada máximo de esta lista se aplica tal cual; 0 lo desactiva. Los límites reales de cada proveedor dependen de su contrato.",
            style=MaterialTheme.typography.bodySmall,color=TextSecondary)
        val subagents=setOf("maxConcurrentSubagents","maxSubagentDepth","allowSubagentGeneration")
        @Composable fun control(key:String) {
            val value=values.getValue(key)
            val label=AgentLimitForm.labels[key] ?: key
            if(key in booleanKeys) Row(verticalAlignment=Alignment.CenterVertically) {
                Text(label,Modifier.weight(1f));Switch(value=="true",{values[key]=it.toString()})
            } else StudioTextField(value,{values[key]=it},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=NexusTokens.field,
                label={Text("$label (0 = sin límite)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        }
        order.filter {it!="maxTokens" && it !in subagents}.forEach {control(it)}
        Text("Máx. tokens se cambia en Respuesta, en esta misma hoja.",style=MaterialTheme.typography.bodySmall,color=TextSecondary)
        HorizontalDivider()
        Text("Preferencias de subagentes",style=MaterialTheme.typography.titleMedium)
        Text("Se guardan para la futura delegación. El motor de subagentes aún no está habilitado.",style=MaterialTheme.typography.bodySmall,color=TextSecondary)
        subagents.forEach {control(it)}
        FilledTonalButton(onClick={
            message=runCatching {store.saveLimits(AgentLimitForm.parse(store.limits(),values))}
                .fold({"Límites guardados. Los envíos pendientes respetan la concurrencia elegida; los demás se aplican al siguiente uso." to false},
                    {(it.message ?: "No se pudieron guardar.") to true})
        }) {Text("Guardar límites")}
        message?.let {(text,error)->InlineNotice(text,isError=error)}
    }
}
