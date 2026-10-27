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
import com.vidadomu.nexus.ui.components.NexusDropdownSelector
import com.vidadomu.nexus.ui.components.NexusStatusTag
import com.vidadomu.nexus.ui.components.NexusStepper
import com.vidadomu.nexus.ui.components.NexusTonalAction
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Every registered rule gets an editor without adding another bespoke settings form. */
@OptIn(ExperimentalLayoutApi::class)
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
        // Ámbito first and clearly a selector: it changes the meaning of every value below.
        item {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(initialSection==null) NexusDropdownSelector("Sección",section ?: "Todas",
                    listOf("Todas")+RuntimeRules.definitions.map {it.section}.distinct()) { section=it.takeUnless {v->v=="Todas"} }
                NexusDropdownSelector("Ámbito",scopeType,listOf("Global","Módulo","Proveedor","Modelo / workflow"),icon=Icons.Default.Layers) {scopeType=it}
            }
        }
        if(scopeType=="Módulo") item { StudioTextField(module,{module=it},label={Text("Módulo: flow, artbox, agent…")},modifier=Modifier.fillMaxWidth()) }
        if(scopeType in setOf("Proveedor","Modelo / workflow")) item { StudioTextField(provider,{provider=it},label={Text("ID del proveedor")},modifier=Modifier.fillMaxWidth()) }
        if(scopeType=="Modelo / workflow") {
            item { StudioTextField(model,{model=it},label={Text("ID exacto del modelo")},modifier=Modifier.fillMaxWidth()) }
            item { StudioTextField(workflow,{workflow=it},label={Text("Workflow (opcional)")},modifier=Modifier.fillMaxWidth()) }
        }
        message?.let { item { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) } }
        if(!scopeReady) item { Text("Indica el destino para editar sus ajustes.",style=MaterialTheme.typography.bodyMedium) }
        items(if(scopeReady) rules else emptyList(),key={it.key}) {rule ->
            val value = configuration.value(rule.key,scope)
            val own = configuration.overrides[scope.key]?.containsKey(rule.key)==true
            // What «Restaurar» would leave: the next scope up, or the built-in default at Global.
            val inherited = remember(configuration,rule.key,scope.key) { configuration.set(rule.key,null,scope).value(rule.key,scope) }
            val origin = if(scope==RuleScope()) "Predeterminado" else "Heredado"
            RuleRow(rule,value,own,inherited,origin,showSection=initialSection==null,
                onSet={store.set(rule.key,it,scope)},
                onPhoto={photo.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))})
        }
        // Backup actions are secondary: at the end, as real buttons, away from the values being edited.
        item {
            Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                NexusTonalAction("Exportar",{export.launch("Nexus-ajustes.json")},icon=Icons.Default.FileUpload,description="Exportar ajustes a un archivo")
                NexusTonalAction("Importar",{import.launch(arrayOf("application/json"))},icon=Icons.Default.FileDownload,description="Importar ajustes desde un archivo")
            }
        }
    }
}

/** Zero means «no local limit» for these rules: say it in words instead of showing a bare 0. */
private fun zeroMeansUnlimited(rule: RuleDefinition) = rule.help.contains("0 = sin límite")

private fun display(rule: RuleDefinition, value: String): String = when {
    rule.type==RuleType.BOOLEAN -> if(value.toBoolean()) "activado" else "desactivado"
    value=="0" && zeroMeansUnlimited(rule) -> "sin límite"
    value.isBlank() -> "vacío"
    else -> value
}

/** Small integer limits get − N +; long ranges (seconds, minutes, MB) keep a field. */
private fun usesStepper(rule: RuleDefinition) = rule.type==RuleType.INTEGER && rule.minimum!=null &&
    (rule.default.toIntOrNull() ?: Int.MAX_VALUE) <= 30 && (rule.maximum==null || rule.maximum!! <= 1000)

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RuleRow(rule: RuleDefinition, value: String, own: Boolean, inherited: String, origin: String,
    showSection: Boolean, onSet: (String?)->Unit, onPhoto: ()->Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape=NexusTokens.card,color=colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(rule.title,style=MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                        NexusStatusTag(if(own) "Personalizado" else origin,emphasized=own)
                        if(showSection) Text(rule.section,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                    }
                }
                if(rule.type==RuleType.BOOLEAN) Switch(value.toBoolean(),{onSet(it.toString())})
                // Restoring only makes sense when this scope holds its own value.
                if(own) IconButton(onClick={onSet(null)}) {
                    Icon(Icons.Default.RestartAlt,"Restaurar ${rule.title} a ${display(rule,inherited)}",Modifier.size(20.dp))
                }
            }
            if(rule.help.isNotBlank()) Text(rule.help,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            if(own && rule.type!=RuleType.BOOLEAN && rule.key!="ui.photo")
                Text("$origin: ${display(rule,inherited)}",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
            when {
                rule.key=="ui.photo" -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    NexusTonalAction("Elegir foto",onPhoto,icon=Icons.Default.Image)
                    if(value.isNotBlank()) NexusTonalAction("Quitar",{onSet("")},icon=Icons.Default.Close,description="Quitar foto de fondo")
                }
                rule.type==RuleType.CHOICE -> NexusDropdownSelector("Valor",value,rule.choices,onValue={onSet(it)})
                usesStepper(rule) -> {
                    val current = value.toIntOrNull() ?: 0
                    val minimum = rule.minimum!!.toInt()
                    NexusStepper(current,{next -> next.toString().takeIf(rule::validate)?.let(onSet)},minimum,rule.maximum?.toInt(),
                        rule.title,zeroLabel=if(zeroMeansUnlimited(rule)) "Sin límite" else null)
                }
                rule.type!=RuleType.BOOLEAN -> {
                    // Empty field = inherit. The placeholder shows what applies; «Guardar» appears only with a real change.
                    var editing by rememberSaveable(rule.key,own,value) {mutableStateOf(if(own) value else "")}
                    val baseline = if(own) value else ""
                    val changed = editing!=baseline && editing.isNotBlank() && rule.validate(editing)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        StudioTextField(editing,{editing=it},Modifier.weight(1f),singleLine=true,
                            placeholder={Text("$origin: ${display(rule,inherited)}")},
                            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=when(rule.type) {
                                RuleType.INTEGER -> androidx.compose.ui.text.input.KeyboardType.Number
                                RuleType.DECIMAL -> androidx.compose.ui.text.input.KeyboardType.Decimal
                                else -> androidx.compose.ui.text.input.KeyboardType.Text }))
                        if(changed) FilledTonalButton(onClick={onSet(editing)}) {Text("Guardar")}
                    }
                    if(editing.isNotBlank() && !rule.validate(editing)) Text("Valor no válido",style=MaterialTheme.typography.labelSmall,color=colors.error)
                }
            }
        }
    }
}
