package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.ui.theme.NexusTokens
import androidx.compose.foundation.layout.*
import com.vidadomu.nexus.ui.components.StudioTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Mis ajustes por modelo: guardar, aplicar, actualizar, marcar uno para que se aplique al abrir el modelo y borrar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CreationPresetsSheet(state:CreateUiState,vm:CreateViewModel,onDismiss:()->Unit) {
    val model=state.selectedModel ?: return
    val prefs=vm.creationPreferences
    val modality=state.selectedModality
    val modelKey=com.vidadomu.nexus.ui.create.capability.ModelCapabilityRegistry.key(model,modality)
    var entries by remember(modelKey) {mutableStateOf(prefs.presets(model,modality))}
    var defaultName by remember(modelKey) {mutableStateOf(prefs.defaultPresetName(model,modality))}
    var name by remember(modelKey) {mutableStateOf("")}
    var rememberValues by remember {mutableStateOf(prefs.rememberParameters())}
    var deleting by remember(modelKey) {mutableStateOf<CreationPreset?>(null)}
    fun summary(values:Map<String,String>):String = model.parameters
        .filter {p->values[p.key].orEmpty().isNotBlank() && values[p.key]!=p.defaultValue}
        .take(5).joinToString(" · ") {p->"${p.displayName}: ${values[p.key]}"}
        .ifBlank {"Valores del modelo"}
    ModalBottomSheet(onDismissRequest=onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            item {Text("Mis ajustes",style=MaterialTheme.typography.headlineSmall);Text(model.displayName,style=MaterialTheme.typography.bodyMedium)}
            item {Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text("Recordar últimos parámetros");Text("Si hay un ajuste marcado con la estrella, manda ese.",style=MaterialTheme.typography.bodySmall)}
                Switch(rememberValues,{rememberValues=it;prefs.setRememberParameters(it)})}}
            item {StudioTextField(name,{name=it},label={Text("Nombre del ajuste (p. ej. «Vertical 4K»)")},singleLine=true,modifier=Modifier.fillMaxWidth())}
            item {Button(onClick={prefs.savePreset(model,name,state.parameters,modality);entries=prefs.presets(model,modality);name=""},enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth()) {Text("Guardar opciones actuales")}}
            if(entries.isEmpty()) item {Text("Aún no has guardado ajustes para este modelo.",style=MaterialTheme.typography.bodyMedium)}
            items(entries,key={it.name}) {entry ->
                val isDefault=defaultName==entry.name
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                Text(summary(entry.values),style=MaterialTheme.typography.bodySmall,maxLines=2)
                            }
                            IconButton(onClick={val next=if(isDefault) null else entry.name;prefs.setDefaultPreset(model,next,modality);defaultName=next}) {
                                Icon(if(isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,if(isDefault) "Quitar de «al abrir»" else "Aplicar siempre al abrir este modelo")
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            FilledTonalButton(onClick={vm.applyPreset(entry);onDismiss()}) {Text("Aplicar")}
                            TextButton(onClick={prefs.savePreset(model,entry.name,state.parameters,modality);entries=prefs.presets(model,modality)}) {Text("Actualizar")}
                            TextButton(onClick={deleting=entry}) {Text("Eliminar")}
                        }
                    }
                }
            }
            item {Text("Los ajustes guardan opciones, nunca claves, textos ni archivos. Cuántos se conservan por modelo se configura en Ajustes → Automatización.",style=MaterialTheme.typography.bodySmall)}
            item {TextButton(onClick={vm.restoreModelDefaults();onDismiss()}) {Text("Restaurar valores del modelo")}}
        }
    }
    deleting?.let {target->
        AlertDialog(onDismissRequest={deleting=null},title={Text("¿Eliminar «${target.name}»?")},
            text={Text("Solo se borra este ajuste guardado; el modelo y tus creaciones no cambian.")},
            confirmButton={Button(onClick={prefs.removePreset(model,target.name,modality);entries=prefs.presets(model,modality);defaultName=prefs.defaultPresetName(model,modality);deleting=null}) {Text("Eliminar")}},
            dismissButton={TextButton(onClick={deleting=null}) {Text("Cancelar")}})
    }
}
