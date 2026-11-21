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

/**
 * Elegir proyecto y editar su memoria son pasos distintos: la lista cambia el contexto en un toque y
 * «Editar memoria» abre el formulario de ESE proyecto. Los borradores viven en [drafts] por proyecto.
 */
@Composable
internal fun ProjectSheet(ui:AssistantUiState,vm:AssistantViewModel,drafts:MutableMap<String,ProjectMemoryDraft>,
    editingId:String?,onEditingChange:(String?)->Unit,onDraftKept:(String,String)->Unit,onClose:()->Unit) {
    var storyProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember {mutableStateOf(false)}
    var name by rememberSaveable {mutableStateOf("")}
    var leaving by remember {mutableStateOf(false)}
    var saveWarning by remember(editingId) {mutableStateOf<String?>(null)}
    val savingIds by vm.savingProjectMemories.collectAsState()
    val currentEditingId by rememberUpdatedState(editingId)
    var reviewedSnapshot by remember(editingId) {mutableStateOf<com.vidadomu.nexus.agent.core.assets.Project?>(null)}
    val active=ui.projects.firstOrNull {it.id==ui.activeProjectId}
    val editing=editingId?.let {id->ui.projects.firstOrNull {it.id==id}}
    storyProjectId?.let { projectId ->
        com.vidadomu.nexus.assistant.story.StoryWorkspaceScreen(projectId,
            onClose = { storyProjectId = null },
            onAgent = { vm.selectProject(projectId); storyProjectId = null; onClose() },
            onGenerate = { storyProjectId = null; onClose(); com.vidadomu.nexus.assistant.story.StoryNavigation.request(com.vidadomu.nexus.assistant.story.StoryDestination.GENERATE) },
            onFlow = { storyProjectId = null; onClose(); com.vidadomu.nexus.assistant.story.StoryNavigation.request(com.vidadomu.nexus.assistant.story.StoryDestination.FLOW) })
        return
    }
    val draft=editing?.let {drafts[it.id]}
    LaunchedEffect(editingId,editing) {if(editingId!=null && editing==null) onEditingChange(null)}
    LaunchedEffect(editing?.id,draft==null) {if(editing!=null && draft==null) drafts[editing.id]=ProjectMemoryDraft.from(editing)}
    val dirty=editing!=null && draft?.isDirty(editing)==true
    // Read from the ViewModel, not the composition: re-entering the editor still sees a save in flight.
    val saving=editing!=null && editing.id in savingIds
    val syncRevision by com.vidadomu.nexus.sync.DeviceSync.get(androidx.compose.ui.platform.LocalContext.current).revision.collectAsState()
    val conversationIndex by vm.conversationIndex.collectAsState()
    LaunchedEffect(syncRevision) {vm.refreshConversations()}
    val conversations=remember(conversationIndex,ui.activeProjectId) {vm.conversations()}
    val draftIds=ui.projects.filter {p->drafts[p.id]?.isDirty(p)==true}.map {it.id}.toSet()
    fun startEditing(p:com.vidadomu.nexus.agent.core.assets.Project) {
        if(drafts[p.id]==null) drafts[p.id]=ProjectMemoryDraft.from(p)
        onEditingChange(p.id)
    }
    fun leaveEditor(keep:Boolean) {
        if(saving) return
        editing?.let {p-> if(!keep) drafts.remove(p.id)}
        leaving=false; onEditingChange(null)
    }
    // exitOnSuccess: only «Guardar» in the leave dialog leaves the editor, and only once the write succeeded.
    fun save(exitOnSuccess:Boolean) {
        if(saving) return
        val p=editing ?: return
        val d=drafts[p.id] ?: return
        val checked=d.prepareSave(p)
        if(checked is ProjectMemorySaveDecision.Blocked) {
            drafts[p.id]=checked.draft
            saveWarning=checked.message
            leaving=false
            return
        }
        val pending=(checked as ProjectMemorySaveDecision.Ready).draft
        saveWarning=null
        drafts[p.id]=pending
        val started=vm.saveProjectMemory(pending) { result ->
            // The callback may outlive this editor; pruning settles a draft left behind by a closed or switched editor.
            if(currentEditingId!=p.id || drafts[p.id]!=pending) return@saveProjectMemory
            result.onSuccess { saved ->
                if(exitOnSuccess) { drafts.remove(p.id); leaving=false; onEditingChange(null) }
                else drafts[p.id]=ProjectMemoryDraft.from(saved)
            }.onFailure {
                drafts[p.id]=pending.copy(saveRequested=false)
                saveWarning=projectMemorySaveFailureMessage(it,pending)
                leaving=false
            }
        }
        if(!started) { drafts[p.id]=d; leaving=false }
        else if(!exitOnSuccess) leaving=false
    }
    // Cerrar la hoja con cambios no guarda nada: conserva el borrador y lo avisa. Durante un guardado no se cierra.
    val dismiss:()->Unit=dismiss@{
        if(saving) return@dismiss
        if(editing!=null) {
            if(dirty) onDraftKept(editing.id,editing.name) else drafts.remove(editing.id)
            onEditingChange(null)
        }
        onClose()
    }
    if(leaving && editing!=null) ProjectLeaveDialog(editing.name,onSave={save(exitOnSuccess=true)},onKeep={leaveEditor(keep=true)},
        onDiscard={leaveEditor(keep=false)},onStay={if(!saving) leaving=false},saving=saving)
    if(editing!=null && draft!=null) {
        NexusSheet("Memoria de ${editing.name}", dismiss, subtitle = if(dirty) "Cambios sin guardar" else "Lo que el agente lee en cada mensaje", icon = Icons.Default.EditNote,
            dismissEnabled = !saving,
            primaryAction = { Column {
                saveWarning?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,
                    modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite}) }
                Button(onClick={save(exitOnSuccess=false)}, enabled=!saving, modifier=Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)) {
                    Text(if(saving) "Guardando memoria…" else "Guardar memoria")
                }
            } }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin)
                .padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                TextButton(onClick={if(dirty) leaving=true else leaveEditor(keep=false)},enabled=!saving,modifier=Modifier.heightIn(min=NexusTokens.minimumTouch)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Spacer(Modifier.width(NexusTokens.space8));Text("Volver a proyectos")
                }
                if(draft.isStale(editing)) InlineNotice("La memoria de este proyecto cambió mientras editabas. Tu borrador se conserva.",
                    actionLabel="Comparar antes de guardar",onAction={if(!saving) reviewedSnapshot=editing})
                val characters=remember(editing.id) {vm.projectCharacters(editing.id)}
                val assetCount=remember(editing.id) {vm.projectAssetCount(editing.id)}
                ProjectMemoryForm(draft,{if(!saving) {drafts[editing.id]=it;saveWarning=null}},assetCount,characters,editing.memory.notes,
                    onValidationError={saveWarning=it},enabled=!saving)
            }
        }
        reviewedSnapshot?.let { snapshot ->
            ProjectMemoryConflictReview(snapshot,draft,onAcceptSnapshot={
                drafts[editing.id]=draft.afterReview(snapshot)
                saveWarning=null
                reviewedSnapshot=null
            },onDismiss={reviewedSnapshot=null})
        }
        return
    }
    NexusSheet(active?.name ?: "Proyecto", onClose, subtitle = if(active==null) "Sin proyecto activo" else "Proyecto actual", icon = Icons.Default.MenuBook,
        primaryAction = { FilledTonalButton(onClick=onClose, modifier=Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)) { Text("Listo") } }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin)
            .padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            if(active!=null) {
                active.description?.takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                FilledTonalButton(onClick={storyProjectId=active.id},modifier=Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)) {
                    Icon(Icons.Default.MovieFilter,null);Spacer(Modifier.width(NexusTokens.space8));Text("Guion y storyboard")
                }
                FilledTonalButton(onClick={startEditing(active)},modifier=Modifier.heightIn(min=NexusTokens.minimumTouch)) {
                    Icon(Icons.Default.EditNote,null);Spacer(Modifier.width(NexusTokens.space8))
                    Text(if(active.id in draftIds) "Retomar borrador de memoria" else "Editar memoria")
                }
            }
            Text("Cambiar de proyecto (${ui.projects.size})",style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
            ProjectPickerList(ui.projects,ui.activeProjectId,draftIds,onSelect={vm.selectProject(it)})
            ui.projects.filter {it.id in draftIds && it.id!=active?.id}.forEach {p->
                TextButton(onClick={startEditing(p)},modifier=Modifier.heightIn(min=NexusTokens.minimumTouch)) {Text("Retomar borrador de «${p.name}»")}
            }
            TextButton(onClick={creating=!creating},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary),modifier=Modifier.heightIn(min=NexusTokens.minimumTouch)) {Icon(Icons.Default.Add,null);Text("Nuevo proyecto")}
            if(creating) {
                StudioTextField(name,{name=it.take(80)},singleLine=true,label={Text("Nombre")},shape=NexusTokens.field,modifier=Modifier.fillMaxWidth())
                FilledTonalButton(onClick={vm.createProject(name);name="";creating=false},enabled=name.isNotBlank()) {Text("Crear")}
            }
            ProjectReferences(ui,vm,expandedByDefault=true)
            HorizontalDivider()
            Text("Conversaciones",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={vm.newConversation();onClose()},enabled=!ui.thinking) {Icon(Icons.Default.AddComment,null);Text("Nueva conversación",color=TextSecondary)}
            if(conversations.isEmpty()) Text("Aún no hay conversaciones guardadas",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            conversations.forEach {entry->TextButton(onClick={vm.openConversation(entry.id);onClose()},enabled=!ui.thinking) {Text(entry.title,color=TextSecondary,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}}
        }
    }
}
