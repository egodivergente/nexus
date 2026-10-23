package com.vidadomu.nexus.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.config.*
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Every registered rule gets an editor without adding another bespoke settings form. */
@Composable fun RuntimeSettingsEditor(initialSection: String? = null, embedded: Boolean = false) {
    val context = LocalContext.current
    val store = remember { RuntimePreferences.get(context) }
    val configuration by store.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var scopeType by rememberSaveable { mutableStateOf("Global") }
    var provider by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var workflow by rememberSaveable { mutableStateOf("") }
    var module by rememberSaveable { mutableStateOf("") }
    val scope = when(scopeType) {
        "Modelo / workflow" -> RuleScope(provider=provider.ifBlank { null },model=model.ifBlank { null },workflow=workflow.ifBlank { null })
        "Proveedor" -> RuleScope(provider=provider.ifBlank { null })
        "Módulo" -> RuleScope(module=module.ifBlank { null })
        else -> RuleScope()
    }
    val scopeReady = when(scopeType) {
        "Modelo / workflow" -> provider.isNotBlank() && model.isNotBlank()
        "Proveedor" -> provider.isNotBlank()
        "Módulo" -> module.isNotBlank()
        else -> true
    }
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) message=runCatching { context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(store.export()) }; "Ajustes exportados" }.getOrElse { "No se pudieron exportar" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) message=runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { store.import(it.readText()) }; "Ajustes importados" }.getOrElse { it.message ?: "No se pudo importar" }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri != null) message=runCatching {
            context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            store.set("ui.photo",uri.toString()); "Fondo seleccionado"
        }.getOrElse { "No se pudo conservar la imagen" }
    }
    val rules = RuntimeRules.definitions.filter {
        (section==null || it.section==section) && (scopeType=="Global" || it.scoped) &&
            "$it".contains(query,true)
    }
    LazyColumn(Modifier.fillMaxWidth().then(if(embedded) Modifier.heightIn(max=1600.dp) else Modifier.fillMaxSize()),
        contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { StudioTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Buscar ajustes y reglas")},leadingIcon={Icon(Icons.Default.Search,null)}) }
        item { Row { TextButton(onClick={export.launch("Nexus-ajustes.json")}) { Text("Exportar") }; TextButton(onClick={import.launch(arrayOf("application/json"))}) {Text("Importar")} } }
        if(initialSection==null) item {
            RuleChoices(listOf("Todas")+RuntimeRules.definitions.map {it.section}.distinct(),section ?: "Todas") { section=it.takeUnless {v->v=="Todas"} }
        }
        item { RuleChoices(listOf("Global","Módulo","Proveedor","Modelo / workflow"),scopeType) {scopeType=it} }
        if(scopeType=="Módulo") item { StudioTextField(module,{module=it},label={Text("Módulo: flow, artbox, agent…")},modifier=Modifier.fillMaxWidth()) }
        if(scopeType in setOf("Proveedor","Modelo / workflow")) item { StudioTextField(provider,{provider=it},label={Text("ID del proveedor")},modifier=Modifier.fillMaxWidth()) }
        if(scopeType=="Modelo / workflow") {
            item { StudioTextField(model,{model=it},label={Text("ID exacto del modelo")},modifier=Modifier.fillMaxWidth()) }
            item { StudioTextField(workflow,{workflow=it},label={Text("Workflow (opcional)")},modifier=Modifier.fillMaxWidth()) }
        }
        message?.let { item { Text(it,style=MaterialTheme.typography.bodySmall) } }
        if(!scopeReady) item { Text("Indica el destino para editar sus ajustes.",style=MaterialTheme.typography.bodyMedium) }
        items(if(scopeReady) rules else emptyList(),key={it.key}) {rule ->
            val value = configuration.value(rule.key,scope)
            var editing by rememberSaveable(rule.key,scope.key,value) {mutableStateOf(value)}
            val own = configuration.overrides[scope.key]?.containsKey(rule.key)==true
            Surface(shape=NexusTokens.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.title,style=MaterialTheme.typography.titleSmall)
                            Text(rule.section+(if(own) " · personalizado" else " · heredado"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(rule.type==RuleType.BOOLEAN) Switch(value.toBoolean(),{store.set(rule.key,it.toString(),scope)})
                        IconButton(onClick={store.set(rule.key,null,scope)}) { Icon(Icons.Default.RestartAlt,"Restaurar ${rule.title}",Modifier.size(18.dp)) }
                    }
                    if(rule.help.isNotBlank()) Text(rule.help,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(rule.type==RuleType.CHOICE) RuleChoices(rule.choices,value) {store.set(rule.key,it,scope)}
                    else if(rule.type!=RuleType.BOOLEAN && rule.key!="ui.photo") Row(verticalAlignment=Alignment.CenterVertically) {
                        StudioTextField(editing,{editing=it},Modifier.weight(1f),singleLine=true)
                        TextButton(enabled=editing!=value && rule.validate(editing),onClick={store.set(rule.key,editing,scope)}) {Text("Guardar")}
                    }
                    if(rule.key=="ui.photo") Row {
                        TextButton(onClick={photo.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Elegir foto")}
                        if(value.isNotBlank()) TextButton(onClick={store.set(rule.key,"")}) {Text("Quitar")}
                    }
                }
            }
        }
    }
}

@Composable private fun RuleChoices(choices: List<String>, value: String, onValue: (String)->Unit) {
    var open by remember {mutableStateOf(false)}
    Box { TextButton(onClick={open=true}) {Text("$value ▾")}
        DropdownMenu(open,{open=false}) { choices.forEach { choice -> DropdownMenuItem(text={Text(choice)},onClick={open=false;onValue(choice)}) } }
    }
}
