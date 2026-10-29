package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.ui.theme.NexusTokens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.util.Locale

private enum class LiveState { LISTENING, THINKING, SPEAKING, PAUSED }

/** What the voice reads: the reply without code, interface blocks, links or markdown symbols. */
internal fun speakable(text: String): String = text
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("<nexus-ui[\\s\\S]*?</nexus-ui>"), " ")
    .replace(Regex("https?://\\S+"), "enlace")
    .replace(Regex("[*_#`>|~]+"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

/** Splits [text] after its last complete sentence: (ready to read, rest still being written). */
internal fun completeSentences(text: String): Pair<String, String> {
    val end = Regex("[.!?…:;](\\s|$)|\\n").findAll(text).lastOrNull()?.range?.last ?: return "" to text
    return text.substring(0, end + 1) to text.substring(end + 1)
}

/**
 * Hands-free voice conversation with the agent: it listens, sends what you said when you stop talking, reads the answer
 * aloud as it is written and listens again. Everything stays written in the chat. A tap on the circle cuts it off and
 * listens; the microphone button pauses.
 */
@Composable
internal fun LiveVoiceDialog(ui: AssistantUiState, onSend: (String) -> Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var state by remember { mutableStateOf(LiveState.PAUSED) }
    var heard by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var problem by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (!it) problem = "Sin permiso de micrófono no puedo escucharte." }
    val latestUi by rememberUpdatedState(ui)

    // Text already read aloud of the answer in progress, and the reply ids that existed before each question.
    var spoken by remember { mutableStateOf("") }
    var answeredBefore by remember { mutableStateOf(ui.items.filterIsInstance<ChatItem.Assistant>().map { it.id }.toSet()) }
    var pendingUtterances by remember { mutableIntStateOf(0) }
    var silences by remember { mutableIntStateOf(0) }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    // The default recognizer, not createOnDeviceSpeechRecognizer: the on-device one fails («no match», language
    // unavailable) when the Spanish pack is not downloaded. The system service still runs locally when it can.
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    fun listen() {
        if (!granted) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        tts.value?.stop(); pendingUtterances = 0
        heard = ""; problem = null; state = LiveState.LISTENING
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }
    fun speak(text: String, last: Boolean) {
        val clean = speakable(text)
        val engine = tts.value
        if (clean.isBlank() || engine == null) { if (last && pendingUtterances == 0) listen(); return }
        state = LiveState.SPEAKING
        pendingUtterances++
        engine.speak(clean, TextToSpeech.QUEUE_ADD, null, (if (last) "last:" else "part:") + System.nanoTime())
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("es", "ES")
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) = view.post {
                        pendingUtterances = (pendingUtterances - 1).coerceAtLeast(0)
                        // Once the whole answer has been read, it listens again on its own.
                        if (pendingUtterances == 0 && !latestUi.thinking && state == LiveState.SPEAKING) listen()
                    }.let {}
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) = onDone(id)
                })
                tts.value = engine
            } else problem = "No hay voz disponible en este móvil (Ajustes → Texto a voz)."
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (text.isBlank()) { listen(); return }
                heard = text; silences = 0
                answeredBefore = latestUi.items.filterIsInstance<ChatItem.Assistant>().map { it.id }.toSet()
                spoken = ""
                if (onSend(text)) state = LiveState.THINKING
                else { state = LiveState.PAUSED; problem = "No se pudo enviar ahora. Toca para volver a hablar." }
            }
            override fun onError(error: Int) {
                level = 0f
                when (error) {
                    // Silence: keep listening a couple of times, then wait for a tap.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (++silences < 3) listen() else { state = LiveState.PAUSED; silences = 0 }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { state = LiveState.PAUSED; problem = "Sin permiso de micrófono no puedo escucharte." }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> Unit
                    else -> { state = LiveState.PAUSED; problem = "No se pudo escuchar (código $error). Toca para intentarlo otra vez." }
                }
            }
        })
        onDispose {
            view.keepScreenOn = false
            recognizer.destroy()
            engine?.stop(); engine?.shutdown()
        }
    }
    // Starts listening as soon as the voice is ready and the microphone allowed.
    LaunchedEffect(granted, tts.value != null) { if (granted && tts.value != null && state == LiveState.PAUSED && problem == null) listen() }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.RECORD_AUDIO) }

    // Reads the answer while it is being written, sentence by sentence; the rest when it is complete.
    LaunchedEffect(ui.streamingText) {
        if (state != LiveState.THINKING && state != LiveState.SPEAKING) return@LaunchedEffect
        val text = ui.streamingText
        if (text.length > spoken.length && text.startsWith(spoken)) {
            val (ready, _) = completeSentences(text.substring(spoken.length))
            if (ready.isNotBlank()) { spoken += ready; speak(ready, last = false) }
        }
    }
    LaunchedEffect(ui.thinking, ui.items.size) {
        if (ui.thinking || (state != LiveState.THINKING && state != LiveState.SPEAKING)) return@LaunchedEffect
        val reply = ui.items.filterIsInstance<ChatItem.Assistant>().lastOrNull { it.id !in answeredBefore }
        val failure = ui.items.lastOrNull() as? ChatItem.Notice
        when {
            reply != null -> {
                answeredBefore = answeredBefore + reply.id
                val rest = if (spoken.isNotEmpty() && reply.text.startsWith(spoken)) reply.text.substring(spoken.length)
                    else if (spoken.isEmpty()) reply.text else ""
                spoken = ""
                speak(rest, last = true)
            }
            failure?.error == true -> { state = LiveState.PAUSED; problem = "El agente no ha podido contestar. Toca para volver a hablar." }
            else -> if (pendingUtterances == 0) listen()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = NexusTokens.space32, start = NexusTokens.space24, end = NexusTokens.space24), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(agentLabel(ui.settings), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The circle breathes with your voice while listening and pulses while it talks. Tap: cut it off and listen.
            // With reduced motion the circle stays still while it thinks or talks.
            val motion = com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed(state == LiveState.SPEAKING || state == LiveState.THINKING)
            val breathing = if (motion) {
                val pulse = rememberInfiniteTransition(label = "live")
                pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath").value
            } else 1f
            val scale = when (state) {
                LiveState.LISTENING -> 1f + level * .35f
                LiveState.SPEAKING, LiveState.THINKING -> breathing
                LiveState.PAUSED -> .9f
            }
            val accent = MaterialTheme.colorScheme.primary
            Box(Modifier.align(Alignment.Center).size(190.dp).scale(scale).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent, accent.copy(alpha = if (state == LiveState.PAUSED) .25f else .55f), Color.Transparent)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Hablar ahora") { listen() })
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp, start = NexusTokens.space24, end = NexusTokens.space24),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(problem ?: when (state) {
                    LiveState.LISTENING -> heard.ifBlank { "Te escucho…" }
                    LiveState.THINKING -> "Pensando…"
                    LiveState.SPEAKING -> "Toca el círculo para cortarle y hablar"
                    LiveState.PAUSED -> "En pausa · toca el círculo para hablar"
                }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space32)) {
                    FilledTonalIconButton(onClick = {
                        if (state == LiveState.LISTENING) { recognizer.cancel(); level = 0f; state = LiveState.PAUSED } else listen()
                    }, modifier = Modifier.size(64.dp)) {
                        Icon(if (state == LiveState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                            if (state == LiveState.LISTENING) "Pausar el micrófono" else "Hablar")
                    }
                    FilledIconButton(onClick = onClose, modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Close, "Terminar la conversación por voz")
                    }
                }
            }
        }
    }
}
