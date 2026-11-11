@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vidadomu.nexus.assistant.story

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.AssistantViewModel
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens

/** A primary tab over the existing project; selecting it never invents or duplicates a project. */
@Composable
fun StoryTabScreen(vm: AssistantViewModel, isActive: Boolean, onAgent: () -> Unit,
    onGenerate: (String) -> Unit, onFlow: () -> Unit, requestedTab: Int? = null, onTabRequestConsumed: () -> Unit = {},
    onClose: () -> Unit = onAgent) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val runtime = remember(app) { AssistantRuntime.get(app) }
    val index by runtime.assets.changes.collectAsState()
    val ui by vm.ui.collectAsState()
    val projects = index.projects.filterNot { it.archived }
    val active = projects.firstOrNull { it.id == ui.activeProjectId }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }

    fun choose(project: Project) { vm.selectProject(project.id); choosing = false }
    fun create() {
        val title = name.trim()
        if (title.isNotEmpty()) { vm.createProject(title); name = ""; creating = false; choosing = false }
    }
    if (active != null) {
        key(active.id) {
            StoryWorkspaceScreen(active.id, onClose = onClose, onAgent = onAgent, onGenerate = onGenerate,
                onFlow = onFlow, embedded = true, isActive = isActive, onChooseProject = { choosing = true },
                requestedTab = requestedTab, onTabRequestConsumed = onTabRequestConsumed)
        }
    } else {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = NexusTokens.screenMargin),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                Spacer(Modifier.height(NexusTokens.space12))
                Text("Guion", style = MaterialTheme.typography.headlineMedium)
                Text("Tu historia, storyboard y material, en un mismo proyecto.", style = MaterialTheme.typography.bodyMedium)
                StoryProjectChoices(projects, null, ::choose, Modifier.weight(1f))
                StoryNewProject(name, { name = it }, ::create)
                Spacer(Modifier.height(NexusTokens.space8))
            }
        }
    }
    if (choosing && isActive) ModalBottomSheet(onDismissRequest = { choosing = false }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = NexusTokens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Text("Proyecto del guion", style = MaterialTheme.typography.titleLarge)
            StoryProjectChoices(projects, active?.id, ::choose, Modifier.heightIn(max = 360.dp))
            TextButton(onClick = { creating = !creating }) { Icon(Icons.Default.Add, null); Text("Nuevo proyecto") }
            if (creating) StoryNewProject(name, { name = it }, ::create)
            Spacer(Modifier.height(NexusTokens.space12))
        }
    }
}

@Composable
private fun StoryProjectChoices(projects: List<Project>, selected: String?, onSelect: (Project) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        if (projects.isEmpty()) item { Text("Empieza con un nombre. Las escenas y el material pueden llegar después.",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp)) }
        items(projects, key = { it.id }) { project ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(project) }.heightIn(min = NexusTokens.minimumTouch)
                .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MovieFilter, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    project.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (project.id == selected) Icon(Icons.Default.Check, "Proyecto activo")
            }
        }
    }
}

@Composable
private fun StoryNewProject(name: String, onName: (String) -> Unit, onCreate: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioTextField(name, onName, singleLine = true, label = { Text("Nombre del proyecto") }, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onCreate, enabled = name.isNotBlank()) { Text("Crear") }
    }
}
