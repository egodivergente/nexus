@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.background
import com.vidadomu.nexus.ui.components.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.vidadomu.nexus.ui.components.NexusTopAppBar
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.vidadomu.nexus.agent.core.llm.AntigravityReasoning
import com.vidadomu.nexus.agent.core.llm.CodexReasoning
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.llm.BrainProtocol
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.PlanEdit
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.theme.*
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun UserBubble(item:ChatItem.User, onEdit: (() -> Boolean)? = null, onSelectMessages: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var actions by rememberSaveable(item.id) { mutableStateOf(false) }
    var selection by rememberSaveable(item.id) { mutableStateOf(false) }
    if (selection) MessageSelectionDialog(item.text) { selection = false }
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.End) {
        if(item.attachments.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {items(item.attachments,key={it.id}) {AttachmentThumb(it)}}
        // Tu mensaje: completo (nunca truncado), a la derecha y sobre un fondo apenas distinto del lienzo; sin borde,
        // brillo ni sombra. El menú discreto sigue al tocar la hora.
        val bubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp
        )
        val foldable = remember(item.text) { UserMessageFold.shouldFold(item.text) }
        var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
        Surface(
            modifier = Modifier
                .fillMaxWidth(.88f)
                .wrapContentWidth(Alignment.End)
                .padding(top = NexusTokens.space4),
            shape = bubbleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box {
                Column(Modifier.padding(horizontal = NexusTokens.space16, vertical = 10.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            item.text,
                            style = chatReadingStyles(MaterialTheme.typography).body,
                            color = TextPrimary,
                            maxLines = if (foldable && !expanded) UserMessageFold.PREVIEW_LINES else Int.MAX_VALUE,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    if (foldable) TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.align(Alignment.End).heightIn(min = NexusTokens.minimumTouch),
                    ) {
                        Text(if (expanded) "Contraer" else "Expandir", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        val bubbleHaptics = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
        Box(Modifier.padding(end = NexusTokens.space8, top = 2.dp)) {
            Row(
                // The time line is short: it answers across 48 dp of height without pushing the next message away.
                modifier = Modifier
                    .tallClickable(onClickLabel = "Acciones del mensaje", shape = NexusTokens.pill) {
                        bubbleHaptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
                        actions = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MessageMeta(item.at, 0L)
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Acciones del mensaje",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                DropdownMenuItem(text = { Text("Copiar mensaje íntegro") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                    bubbleHaptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.CONFIRMATION)
                    clipboard.setText(AnnotatedString(item.text)); actions = false
                })
                DropdownMenuItem(text = { Text("Seleccionar mensajes") }, onClick = { actions = false; onSelectMessages() })
                // Only on your last message, while its answer can still be taken back.
                onEdit?.let { edit -> DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = {
                    actions = false
                    if (edit()) bubbleHaptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
                }) }
                DropdownMenuItem(text = { Text("Compartir mensaje") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                    actions = false
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.text)
                        }, "Compartir mensaje"))
                    }.onFailure { Toast.makeText(context, "No se pudo compartir el mensaje", Toast.LENGTH_SHORT).show() }
                })
            }
        }
    }
}

@Composable
private fun MessageSelectionDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Seleccionar y copiar") },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

/**
 * Only the distinctive name of who answered, for the line under each reply: «AGY · Gemini 4 Argon Medium» → «Argón»,
 * «AGY · Gemini 4 Hydrogen Xhigh» → «Hidrógeno»,
 * «Bedrock · Claude Sonnet 4 6» → «Sonnet», «AGY · Gemini 3.8 Flash» → «Flash». Provider, family, version numbers
 * and effort words are dropped; a custom brain keeps its own name.
 */
internal fun shortModelName(full: String): String {
    if (full.isBlank()) return full
    val model = if (" · " in full) full.substringAfter(" · ") else full
    val noise = setOf("gemini", "claude", "gpt", "openai", "anthropic", "google", "low", "medium", "high", "xhigh", "preview", "latest")
    val words = model.split(' ').filter { w -> w.isNotBlank() && w.lowercase() !in noise && !w.any(Char::isDigit) }
    val name = words.joinToString(" ").ifBlank { model }
    return SPANISH_NAMES[name.lowercase()] ?: name
}

private val SPANISH_NAMES = mapOf("argon" to "Argón", "hydrogen" to "Hidrógeno", "neon" to "Neón", "xenon" to "Xenón", "kripton" to "Kriptón", "krypton" to "Kriptón")

/** Who answers, short: provider then model («AGY · Gemini 3.8 Flash», «Bedrock · Claude Sonnet 4 6»), or the custom brain's name. */
internal fun agentLabel(s: AssistantSettings): String {
    val provider = when {
        s.backend == AssistantSettings.BACKEND_ANTIGRAVITY -> "AGY"
        s.backend == AssistantSettings.BACKEND_CODEX -> "Codex"
        s.isCustom -> s.brain?.name?.ifBlank { null } ?: "Cerebro propio"
        s.backend == AssistantSettings.BACKEND_ANTHROPIC -> "Claude API"
        s.backend == AssistantSettings.BACKEND_OPENAI -> "OpenAI"
        else -> "Bedrock"
    }
    // «us.anthropic.claude-sonnet-4-6-v1:0» → «Claude Sonnet 4 6»; «gemini-3.8-flash» → «Gemini 3.8 Flash».
    val model = (if (s.isCustom) s.brain?.modelId else s.modelId).orEmpty()
        .substringAfterLast('/').substringBefore(':').let { id -> if (id.count { it == '.' } >= 2 && id.substringAfterLast('.').any(Char::isLetter)) id.substringAfterLast('.') else id }
        .removeSuffix("-v1").removeSuffix("-v2").split('-', '_').filter { it.isNotBlank() }
        .joinToString(" ") { part -> if (part.equals("gpt", ignoreCase = true)) "GPT" else part.replaceFirstChar { it.uppercase() } }
    return listOf(provider, model).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Time of the message and, for replies, who answered and how long it took: tiny and secondary so it never competes with the text. */
@Composable
internal fun MessageMeta(at: Long, tookMs: Long, modifier: Modifier = Modifier, by: String = "") {
    if(at<=0L) return
    val time=remember(at) {java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))}
    val label=listOfNotNull(shortModelName(by).ifBlank {null},time,formatTook(tookMs).takeIf {tookMs>0}).joinToString(" · ")
    Text(label,modifier,style=MaterialTheme.typography.labelSmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
}

internal fun formatTook(ms: Long): String = when {
    ms<1_000 -> "${ms} ms"
    ms<10_000 -> String.format(java.util.Locale("es","ES"),"%.1f s",ms/1000.0)
    ms<60_000 -> "${ms/1000} s"
    else -> "${ms/60_000} min ${(ms%60_000)/1000} s"
}

/** Lo que el usuario contestó justo después de este mensaje (para bloquear sus paneles y marcar la opción elegida). */
internal fun answerAfter(items: List<ChatItem>, item: ChatItem.Assistant): ChatItem.User? {
    val at = items.indexOfFirst { it.id == item.id }
    if (at < 0) return null
    return items.drop(at + 1).filterIsInstance<ChatItem.User>().firstOrNull()
}

@Composable
internal fun AssistantBubble(item:ChatItem.Assistant, onOpenHistory: () -> Unit = {}, answered: String? = null, onReply: ((String) -> Boolean)? = null, onSelectMessages: () -> Unit = {},
    /** Only on the answer to your last message, while that exchange can still be taken back. */
    onAnswerAgain: (() -> Boolean)? = null,
    widgetAnswer: com.vidadomu.nexus.agent.core.chat.WidgetAnswer? = null,
    onWidgetReply: ((com.vidadomu.nexus.agent.core.chat.WidgetReply) -> Boolean)? = null,
    /** This reply has just closed the turn that was on show; [textOnShow]: its words were already being written. */
    closesTurn: Boolean = false, textOnShow: Boolean = true) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val settle = rememberTurnSettle(item.id, closesTurn, bodyOnShow = textOnShow)
    var actionsExpanded by rememberSaveable(item.id) {mutableStateOf(false)}
    var selection by rememberSaveable(item.id) { mutableStateOf(false) }
    if (selection) MessageSelectionDialog(item.text) { selection = false }
    // How the answer was reached, kept with the message: the real steps of that turn, folded to one line. Messages
    // saved before the timeline existed only kept the tool names; those are listed without times.
    val savedSteps = remember(item.id, item.steps, item.toolCalls) { item.steps.ifEmpty { AgentActivityText.legacy(item.id, item.toolCalls) } }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        if (item.stop != "STREAMING") AgentActivitySaved(item.id, savedSteps, arrived = settle.arrived, liveGap = LiveActivityGap)
        if (item.text.isNotBlank()) {
            // The answer reads on the ground itself: no card, edge or sheen around the text.
            run {
                Box(Modifier.fillMaxWidth().then(settle.entering).padding(start = 2.dp, end = NexusTokens.space4, top = 2.dp, bottom = 2.dp)) {
                    val streaming = item.stop == "STREAMING"
                    val responseText = remember(item.text, streaming) {
                        if (item.text.trim() in setOf("…", "...")) {
                            if (streaming) "Recibiendo respuesta…" else "La respuesta guardada no contiene texto. Revisa los pasos de este turno."
                        } else hideOrderIds(item.text)
                    }
                    ChatMarkdownView(
                        responseText,
                        streaming = streaming,
                        answered = answered,
                        onReply = if (streaming) null else onReply,
                        selectable = !streaming,
                        widgetAnswer = widgetAnswer,
                        messageId = item.id,
                        onWidgetReply = if (streaming) null else onWidgetReply,
                    )
                }
            }
        }
        if (item.text.isNotBlank() && item.stop != "STREAMING") {
            val responseHaptics = com.vidadomu.nexus.ui.theme.rememberNexusHaptics()
            // Who answered and when is new under a reply that was being written: it opens its own room.
            SettleIn(settle.arrived) { Box {
                Row(
                    modifier = Modifier
                        .tallClickable(onClickLabel = "Acciones de la respuesta", shape = NexusTokens.pill) {
                            responseHaptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.SELECTION)
                            actionsExpanded = true
                        }
                        .padding(start = 2.dp, end = NexusTokens.space8, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MessageMeta(item.at, item.tookMs, by = item.by)
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Acciones de la respuesta",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(text = { Text("Copiar respuesta") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                        responseHaptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.CONFIRMATION)
                        clipboard.setText(AnnotatedString(item.text)); actionsExpanded = false
                        Toast.makeText(context, "Respuesta copiada", Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text("Seleccionar mensajes") }, onClick = { actionsExpanded = false; onSelectMessages() })
                    onAnswerAgain?.let { again -> DropdownMenuItem(text = { Text("Otra respuesta") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = {
                        actionsExpanded = false
                        if (again()) responseHaptics.perform(com.vidadomu.nexus.ui.theme.NexusHapticEvent.CONFIRMATION)
                    }) }
                    DropdownMenuItem(text = { Text("Compartir respuesta") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                        actionsExpanded = false
                        runCatching {
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.text) }
                            context.startActivity(Intent.createChooser(intent, "Compartir respuesta"))
                        }.onFailure { Toast.makeText(context, "No se pudo compartir la respuesta", Toast.LENGTH_SHORT).show() }
                    })
                }
            } }
        }
        if(com.vidadomu.nexus.ui.navigation.HistoryLinks.mentionsHistoryScreen(item.text)) HistoryLinkButton(onOpenHistory)
        if(item.media.isNotEmpty()) {
            val mediaState=androidx.compose.foundation.lazy.rememberLazyListState()
            // Dos o más imágenes seguidas se muestran como collage; vídeos, audios y documentos siguen en el carrusel.
            val pictures=item.media.filter {it.mimeType.startsWith("image/")}
            val collage=pictures.size>=2
            val others=if(collage) item.media.filterNot {it.mimeType.startsWith("image/")} else item.media
            if(collage) ImageCollage(pictures)
            if(others.isNotEmpty()) {
                // Separación mínima: las imágenes ya ocupan su caja sin franjas vacías y quedan juntas.
                LazyRow(state=mediaState,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space4),
                    contentPadding=PaddingValues(end=NexusTokens.space32)) {
                    items(others,key={it.id}) { AssistantMediaPreview(it) }
                }
                // Page indicator: with several media the row is a carousel, and the reader must know there is more.
                if(others.size>1) MediaPageDots(others.size,mediaState.firstVisibleItemIndex)
            }
        }
    }
}

@Composable
internal fun AssistantMediaPreview(media: Attachment) {
    val context=LocalContext.current
    when {
        media.mimeType in setOf("text/html","application/pdf","text/csv","application/json","text/plain","text/markdown") -> AssistantDocumentPreview(media)
        media.mimeType.startsWith("image/") -> AssistantImagePreview(media)
        media.mimeType.startsWith("video/") -> ChatVideoPreview(media)
        media.mimeType.startsWith("audio/") -> {
            val player=remember(media.uri) {
                ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.parse(media.uri))); prepare() }
            }
            var playing by remember(media.uri) { mutableStateOf(false) }
            DisposableEffect(player) {
                val listener=object:Player.Listener { override fun onIsPlayingChanged(isPlaying:Boolean) { playing=isPlaying } }
                player.addListener(listener)
                onDispose { player.removeListener(listener); player.release() }
            }
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=NexusTokens.field,modifier=Modifier.widthIn(min=220.dp,max=280.dp)) {
                Row(Modifier.padding(horizontal=NexusTokens.space8,vertical=NexusTokens.space4),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal=NexusTokens.space8)) {
                        Text(media.name ?: "Audio",maxLines=1,style=MaterialTheme.typography.bodyMedium)
                        media.durationSec?.let { Text(formatMediaDuration(it),style=MaterialTheme.typography.labelMedium,color=TextSecondary) }
                    }
                    IconButton(onClick={if(playing) player.pause() else player.play()}) {
                        Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,if(playing) "Pausar audio" else "Reproducir audio")
                    }
                }
            }
        }
        else -> Surface(
            color=MaterialTheme.colorScheme.surfaceVariant,
            shape=NexusTokens.field,
            modifier=Modifier.widthIn(min=220.dp,max=280.dp).clickable { openAssistantMedia(context,media) },
        ) {
            Row(Modifier.padding(NexusTokens.space12),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Icon(Icons.Default.Description,"Abrir archivo")
                Column(Modifier.weight(1f)) {
                    Text(media.name ?: "Archivo del asistente",maxLines=2,style=MaterialTheme.typography.bodyMedium)
                    Text(media.mimeType,style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                }
                Icon(Icons.Default.OpenInNew,"Abrir")
            }
        }
    }
}

private fun openAssistantMedia(context:android.content.Context,media:Attachment) {
    runCatching {
        val original=Uri.parse(media.uri)
        val uri=if(original.scheme=="file") FileProvider.getUriForFile(
            context,"${com.vidadomu.nexus.BuildConfig.APPLICATION_ID}.fileprovider",java.io.File(original.path!!)) else original
        val view=Intent(Intent.ACTION_VIEW).setDataAndType(uri,media.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(view,media.name ?: "Abrir archivo"))
    }.onFailure { Toast.makeText(context,"No hay una aplicación compatible para abrir este archivo.",Toast.LENGTH_SHORT).show() }
}

private fun formatMediaDuration(seconds:Double):String {
    val total=seconds.toInt().coerceAtLeast(0)
    return if(total>=3600) "%d:%02d:%02d".format(total/3600,(total%3600)/60,total%60)
        else "%d:%02d".format(total/60,total%60)
}

/**
 * Chat video: a poster frame with a large, always visible Play. The player is created only when the user taps Play
 * (it used to prepare one ExoPlayer per video as soon as the conversation was drawn) and released when it leaves.
 */
@Composable
private fun ChatVideoPreview(media: Attachment) {
    val context = LocalContext.current
    var active by remember(media.uri) { mutableStateOf(false) }
    var ratio by remember(media.uri) { mutableStateOf(mediaAspectRatio(media.width, media.height)) }
    var playbackError by remember(media.uri) { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 280.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio ?: (16f / 9f)).clip(NexusTokens.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (!active) {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context).data(media.uri)
                        .decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) }.build(),
                    contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize())
                Surface(onClick = { active = true; playbackError = false }, shape = androidx.compose.foundation.shape.CircleShape,
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f), modifier = Modifier.size(60.dp)
                        .semantics { contentDescription = "Reproducir vídeo" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(36.dp), tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
                media.durationSec?.let {
                    Text(formatMediaDuration(it), Modifier.align(Alignment.BottomEnd).padding(NexusTokens.space8)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .55f), NexusTokens.filter)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                }
            } else {
                val player = remember(media.uri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(media.uri))); prepare(); playWhenReady = true
                    }
                }
                DisposableEffect(player) {
                    val listener = object : Player.Listener {
                        override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                            ratio = mediaAspectRatio(size.width, size.height, size.pixelWidthHeightRatio, size.unappliedRotationDegrees) ?: ratio
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { playbackError = true; active = false }
                    }
                    player.addListener(listener)
                    onDispose { player.removeListener(listener); player.release() }
                }
                AndroidView(
                    factory = { viewContext -> PlayerView(viewContext).apply {
                        this.player = player; useController = true; controllerAutoShow = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } },
                    update = { it.player = player },
                    modifier = Modifier.matchParentSize())
            }
        }
        if (playbackError) Text("No se pudo reproducir el vídeo. Comprueba el archivo o vuelve a descargarlo.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun MediaPageDots(count: Int, current: Int) {
    Row(Modifier.padding(top = NexusTokens.space4).semantics(mergeDescendants = true) { contentDescription = "Elemento ${current + 1} de $count" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count.coerceAtMost(8)) { index ->
            val active = index == current.coerceAtMost(7)
            Box(Modifier.size(if (active) 7.dp else 5.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)))
        }
        Spacer(Modifier.width(NexusTokens.space4))
        Text("${current + 1} / $count", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
