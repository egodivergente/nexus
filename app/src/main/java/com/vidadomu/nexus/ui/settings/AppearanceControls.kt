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
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.settings.RuntimePreferences

/** Everyday appearance controls write the same preferences as the advanced editor. */
@Composable internal fun AppearanceControls(onAdvanced: () -> Unit) {
    val context=LocalContext.current
    val store=remember {RuntimePreferences.get(context)}
    val configuration by store.state.collectAsState()
    var message by remember {mutableStateOf<String?>(null)}
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->
        if(uri!=null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            store.set("ui.photo",uri.toString())
        }.onFailure {message="No se pudo conservar esta foto. El fondo anterior se mantiene."}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf("ui.background" to "Fondo Nexus", "ui.motion" to "Animaciones", "ui.haptics" to "Respuesta háptica").forEach {(key,label) ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                Switch(configuration.value(key).toBoolean(),{store.set(key,it.toString())})
            }
        }
        listOf(Triple("ui.backgroundOpacity","Intensidad del fondo",0f..1f),Triple("ui.photoBlur","Desenfoque de la foto",0f..100f)).forEach {(key,label,range) ->
            val saved=configuration.value(key).toFloatOrNull() ?: range.start
            var draft by remember(saved) {mutableFloatStateOf(saved)}
            Text("$label · ${if(key.endsWith("Opacity")) (draft*100).toInt() else draft.toInt()} %",style=MaterialTheme.typography.labelLarge)
            Slider(draft,{draft=it},valueRange=range,onValueChangeFinished={store.set(key,draft.toString())})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Elegir foto")}
            if(configuration.value("ui.photo").isNotBlank()) TextButton(onClick={store.set("ui.photo","")}) {Text("Quitar foto")}
        }
        message?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        TextButton(onClick=onAdvanced) {Text("Avanzado · reglas por ámbito")}
    }
}
