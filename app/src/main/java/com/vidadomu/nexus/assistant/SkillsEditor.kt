package com.vidadomu.nexus.assistant

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.skills.Skill
import com.vidadomu.nexus.agent.core.skills.SkillParser
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary
import java.io.File

/**
 * Edición de skills. La edición nunca toca los archivos empaquetados en la app: se guarda una copia en `files/skills/<id>/SKILL.md`
 * (si la skill ya vive en `files/skills` o en Documents se reescribe ese archivo) y esa copia gana a la original.
 * «Restaurar original» borra la copia editada.
 */
internal object SkillsEditor {
    private fun fileFor(app: NexusApplication, origin: String): File? = when {
        origin.startsWith("files/skills/") -> File(app.filesDir, "skills/" + origin.removePrefix("files/skills/"))
        origin.startsWith("Documents/") -> File(Environment.getExternalStorageDirectory(), origin)
        else -> null
    }

    fun rawText(app: NexusApplication, skill: Skill): String? = runCatching {
        fileFor(app, skill.origin)?.takeIf { it.isFile }?.readText(Charsets.UTF_8)
            ?: app.assets.open(skill.origin.removePrefix("assets/")).bufferedReader().use { it.readText() }
    }.getOrNull()

    fun isEdited(app: NexusApplication, skill: Skill): Boolean = !skill.origin.startsWith("assets/")

    /** Devuelve un mensaje de error o null si se guardó. */
    fun save(app: NexusApplication, skill: Skill, text: String): String? {
        val parsed = runCatching { SkillParser.parse(text, skill.origin) }.getOrElse { return it.message ?: "La skill no es válida." }
        if (parsed.id != skill.id) return "No cambies el id de la skill (${skill.id}): la copia editada dejaría de sustituir a la original."
        val target = fileFor(app, skill.origin) ?: File(app.filesDir, "skills/${skill.id}/SKILL.md")
        return runCatching {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) { target.delete(); check(tmp.renameTo(target)) { "No se pudo escribir ${target.name}." } }
            null
        }.getOrElse { it.message ?: "No se pudo guardar." }
    }

    /** Borra la copia editada (solo la de files/skills); la skill vuelve a cargarse de la app. */
    fun restore(app: NexusApplication, skill: Skill): Boolean {
        val f = if (skill.origin.startsWith("files/skills/")) fileFor(app, skill.origin) else null
        return f?.delete() == true
    }
}

@Composable
internal fun SkillsEditorDialog(vm: AssistantViewModel, app: NexusApplication, onDismiss: () -> Unit) {
    var revision by remember { mutableStateOf(0) }
    val skills = remember(revision) { vm.runtime.skills.all().sortedWith(compareBy({ it.kind }, { it.title.lowercase() })) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Skill?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                val current = editing
                if (current == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Skills (${skills.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                    }
                    StudioTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Buscar skill") })
                    val shown = skills.filter { query.isBlank() || "${it.title} ${it.id}".contains(query, true) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { it.id }) { s ->
                            Column(Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = NexusTokens.space8)) {
                                Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(s.id, if (s.kind.name == "MODEL") "modelo" else "experto", "v${s.version}", if (SkillsEditor.isEdited(app, s)) "editada" else null)
                                    .filterNotNull().joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    var text by remember(current.id, revision) { mutableStateOf(SkillsEditor.rawText(app, current).orEmpty()) }
                    var error by remember(current.id) { mutableStateOf<String?>(null) }
                    var confirmRestore by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(current.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current.origin, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { editing = null }) { Text("Atrás") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        StudioTextField(text, { text = it; error = null }, Modifier.fillMaxWidth(), minLines = 12,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text("La copia editada se guarda en el teléfono y sustituye a la de la app. La cabecera (---) debe seguir siendo válida y el id no puede cambiar.",
                        style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            error = SkillsEditor.save(app, current, text)
                            if (error == null) { vm.reloadSkills(); revision++; editing = null }
                        }, Modifier.weight(1f)) { Text("Guardar") }
                        if (SkillsEditor.isEdited(app, current)) OutlinedButton(onClick = { confirmRestore = true }) { Text("Restaurar original") }
                    }
                    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false }, title = { Text("¿Restaurar la skill original?") },
                        text = { Text("Se descarta tu copia editada y se vuelve a usar la de la app.") },
                        confirmButton = { Button(onClick = {
                            confirmRestore = false
                            if (SkillsEditor.restore(app, current)) { vm.reloadSkills(); revision++; editing = null }
                            else error = "Esta copia vive en Documents/ALEXIA/Nexus/Skills: bórrala o edítala allí."
                        }) { Text("Restaurar") } },
                        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } })
                }
            }
        }
    }
}
