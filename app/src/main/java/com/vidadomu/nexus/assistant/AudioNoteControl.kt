package com.vidadomu.nexus.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.vidadomu.nexus.settings.RuntimePreferences
import java.io.File

/** Voice notes are reviewed locally. Dictation remains separate from actual recorded audio. */
@Composable internal fun AudioNoteControl(onAudio: (Uri)->Unit, onDictate: ()->Unit) {
    val context=LocalContext.current
    var open by rememberSaveable {mutableStateOf(false)}
    var path by rememberSaveable {mutableStateOf<String?>(null)}
    var recording by remember {mutableStateOf(false)}
    var recorder by remember {mutableStateOf<MediaRecorder?>(null)}
    var player by remember {mutableStateOf<MediaPlayer?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    fun stop() {
        runCatching {recorder?.stop()}.onFailure {error="La grabación es demasiado corta o no está disponible"}
        recorder?.release();recorder=null;recording=false
    }
    @Suppress("DEPRECATION") fun beginRecording() {
        error=null
        runCatching {
            val rules=RuntimePreferences.get(context)
            val file=File(context.cacheDir,"voice_${System.currentTimeMillis()}.m4a")
            val rec=MediaRecorder()
            recorder=rec
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(rules.int("audio.bitrate"))
                setAudioSamplingRate(rules.int("audio.sampleRate"))
                val max=rules.int("audio.maxSeconds");if(max>0) setMaxDuration((max.toLong()*1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                setOutputFile(file.absolutePath);setOnInfoListener {_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop()}
                prepare();this.start()
            }
            path=file.absolutePath;recorder=rec;recording=true
        }.onFailure {recorder?.release();recorder=null;error="No se pudo abrir el micrófono. Revisa el permiso y los ajustes de audio."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted) beginRecording() else error="El micrófono necesita permiso para grabar."}
    DisposableEffect(Unit) {onDispose {runCatching {recorder?.stop()};recorder?.release();player?.release()}}
    IconButton(onClick={open=true}) {Icon(Icons.Default.Mic,"Grabar audio o dictar")}
    if(open) AlertDialog(onDismissRequest={if(recording) stop();player?.release();player=null;open=false},
        title={Text(if(recording) "Grabando…" else "Nota de voz")},text={Column {
            Text("Escucha la nota antes de adjuntarla al mensaje.")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Row {
                if(recording) TextButton(onClick={stop()}) {Text("Detener")}
                else TextButton(onClick={
                    player?.release();player=null
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) beginRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) {Text(if(path==null) "Grabar" else "Volver a grabar")}
                if(path!=null && !recording) TextButton(onClick={runCatching {player?.release();player=MediaPlayer().apply {setDataSource(path);prepare();this.start()}}.onFailure {error="No se pudo reproducir la nota"}}) {Text("Escuchar")}
            }
            if(!recording) TextButton(onClick={open=false;onDictate()}) {Text("Dictar texto")}
        }},confirmButton={TextButton(enabled=path!=null && !recording && error==null,onClick={
            player?.release();player=null;onAudio(Uri.fromFile(File(path!!)));path=null;open=false
        }) {Text("Adjuntar")}},dismissButton={TextButton(onClick={if(recording) stop();player?.release();player=null;path?.let {File(it).delete()};path=null;open=false}) {Text("Cancelar")}})
}
