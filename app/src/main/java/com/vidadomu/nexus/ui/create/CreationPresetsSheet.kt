package com.vidadomu.nexus.ui.create
import androidx.compose.foundation.layout.*
import com.vidadomu.nexus.ui.components.StudioTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CreationPresetsSheet(state:CreateUiState,vm:CreateViewModel,onDismiss:()->Unit) {
    val model=state.selectedModel ?: return
    val prefs=vm.creationPreferences
    var entries by remember(model.id) {mutableStateOf(prefs.presets(model))}
    var name by remember {mutableStateOf("")}
    var rememberValues by remember {mutableStateOf(prefs.rememberParameters())}
    ModalBottomSheet(onDismissRequest=onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text("Mis ajustes",style=MaterialTheme.typography.headlineSmall);Text(model.displayName,style=MaterialTheme.typography.bodyMedium)}
            item {Row {Text("Recordar últimos parámetros",Modifier.weight(1f));Switch(rememberValues,{rememberValues=it;prefs.setRememberParameters(it)})}}
            item {StudioTextField(name,{name=it.take(48)},label={Text("Nombre del preset")},singleLine=true,modifier=Modifier.fillMaxWidth())}
            item {Button(onClick={prefs.savePreset(model,name,state.parameters);entries=prefs.presets(model);name=""},enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth()) {Text("Guardar opciones actuales")}}
            items(entries,key={it.name}) {entry -> Row {
                TextButton(onClick={vm.applyPreset(entry);onDismiss()},modifier=Modifier.weight(1f)) {Text(entry.name)}
                TextButton(onClick={prefs.removePreset(model,entry.name);entries=prefs.presets(model)}) {Text("Eliminar")}
            }}
            item {Text("Los presets guardan opciones, sin prompt ni archivos. Hasta 12 por modelo.",style=MaterialTheme.typography.bodySmall)}
            item {TextButton(onClick={vm.restoreModelDefaults();onDismiss()}) {Text("Restaurar valores del modelo")}}
        }
    }
}
