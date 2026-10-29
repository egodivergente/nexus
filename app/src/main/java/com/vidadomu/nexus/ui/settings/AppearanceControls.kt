package com.vidadomu.nexus.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vidadomu.nexus.ui.theme.*
import com.vidadomu.nexus.settings.RuntimePreferences

/** Everyday appearance controls write the same preferences as the advanced editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AppearanceControls(onAdvanced: () -> Unit) {
    val context=LocalContext.current
    val store=remember {RuntimePreferences.get(context)}
    val haptics=rememberNexusHaptics()
    val configuration by store.state.collectAsState()
    val appearance=rememberAppearancePreferences()
    val selected by appearance.selectionState()
    var pickerOpen by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->
        if(uri!=null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            store.set("ui.photo",uri.toString())
        }.onFailure {message="No se pudo conservar esta foto. El fondo anterior se mantiene."}
    }
    if(pickerOpen) PremiumAppearancePicker(appearance,onDismiss={pickerOpen=false})
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        FilledTonalButton(onClick={pickerOpen=true},modifier=Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch),shape=NexusTokens.field) {
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.Start) {
                Text("Temas y ambiente",style=MaterialTheme.typography.labelLarge)
                Text("${selected.theme.label} · ${selected.ambientMode.label}",style=MaterialTheme.typography.bodySmall)
            }
        }
        val checked=configuration.value("ui.haptics").toBoolean()
        Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)
            .toggleable(checked,role=Role.Switch,onValueChange={store.set("ui.haptics",it.toString());haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE,changed=it!=checked)}),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Text("Respuesta háptica",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Switch(checked,onCheckedChange=null)
        }
        if(!configuration.value("ui.background").toBoolean() || !configuration.value("ui.motion").toBoolean()) Text(
            "Una regla avanzada limita el fondo o sus animaciones.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(configuration.value("ui.photo").isNotBlank()) {
            val saved=configuration.value("ui.photoBlur").toFloatOrNull() ?: 0f
            var draft by remember(saved) {mutableFloatStateOf(saved)}
            Text("Desenfoque de la foto · ${draft.toInt()} %",style=MaterialTheme.typography.labelLarge)
            Slider(draft,{draft=it},valueRange=0f..100f,onValueChangeFinished={store.set("ui.photoBlur",draft.toString())},
                modifier=Modifier.semantics {contentDescription="Desenfoque de la foto"})
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
            OutlinedButton(onClick={photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Elegir foto")}
            if(configuration.value("ui.photo").isNotBlank()) TextButton(onClick={store.set("ui.photo","")}) {Text("Quitar foto")}
        }
        message?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        TextButton(onClick=onAdvanced) {Text("Avanzado · reglas por ámbito")}
    }
}
