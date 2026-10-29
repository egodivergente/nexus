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
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.settings.RuntimePreferences

/** Everyday appearance controls write the same preferences as the advanced editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AppearanceControls(onAdvanced: () -> Unit) {
    val context=LocalContext.current
    val store=remember {RuntimePreferences.get(context)}
    val haptics=rememberNexusHaptics()
    val configuration by store.state.collectAsState()
    var message by remember {mutableStateOf<String?>(null)}
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->
        if(uri!=null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            store.set("ui.photo",uri.toString())
        }.onFailure {message="No se pudo conservar esta foto. El fondo anterior se mantiene."}
    }
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        listOf("ui.background" to "Fondo Nexus", "ui.motion" to "Animaciones", "ui.haptics" to "Respuesta háptica").forEach {(key,label) ->
            val checked=configuration.value(key).toBoolean()
            Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)
                .toggleable(checked,role=Role.Switch,onValueChange={store.set(key,it.toString());haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE,changed=it!=checked)}),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                Switch(checked,onCheckedChange=null)
            }
        }
        listOf(Triple("ui.backgroundOpacity","Intensidad del fondo",0f..1f),Triple("ui.photoBlur","Desenfoque de la foto",0f..100f)).forEach {(key,label,range) ->
            val saved=configuration.value(key).toFloatOrNull() ?: range.start
            var draft by remember(saved) {mutableFloatStateOf(saved)}
            Text("$label · ${if(key.endsWith("Opacity")) (draft*100).toInt() else draft.toInt()} %",style=MaterialTheme.typography.labelLarge)
            Slider(draft,{draft=it},valueRange=range,onValueChangeFinished={store.set(key,draft.toString())},
                modifier=Modifier.semantics {contentDescription=label})
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
            OutlinedButton(onClick={photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Elegir foto")}
            if(configuration.value("ui.photo").isNotBlank()) TextButton(onClick={store.set("ui.photo","")}) {Text("Quitar foto")}
        }
        message?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        TextButton(onClick=onAdvanced) {Text("Avanzado · reglas por ámbito")}
    }
}
