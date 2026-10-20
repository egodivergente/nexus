package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import kotlinx.serialization.json.*

class GetProjectOverviewTool(private val d:ToolDeps):BaseTool(
    "get_project_overview",ToolCategory.READ,
    "Vista global compacta del proyecto: memoria, escenas, planos, archivos y generaciones recientes. No carga imágenes ni el historial entero. Úsala al retomar trabajo o decidir el siguiente paso.",
    schema {string("project","Nombre o id; por defecto el proyecto activo.")},
) {
    override suspend fun execute(input:JsonObject,session:AgentSession):ToolOutcome {
        val p=d.assets.findProject(input.str("project") ?: session.activeProjectId ?: return fail("Indica el proyecto o usa open_project.")) ?: return fail("Proyecto desconocido.")
        val index=d.assets.snapshot()
        val media=index.assets.filter {it.projectId==p.id && !it.libraryDeleted}
        val jobs=index.generations.filter {it.projectId==p.id}.sortedByDescending {it.createdAt}
        return ok {
            put("project_id",p.id);put("name",p.name)
            put("memory",d.assets.projectContext(p.id,d.limits().maximum(d.limits().maxProjectContextChars)).orEmpty())
            put("asset_counts",buildJsonObject {media.groupingBy {it.type.name}.eachCount().forEach {(type,count)->put(type,count)}})
            put("generation_counts",buildJsonObject {jobs.groupingBy {it.status}.eachCount().forEach {(status,count)->put(status,count)}})
            put("scenes",buildJsonArray {index.scenes.filter {it.projectId==p.id}.take(d.limits().maximum(d.limits().maxOverviewScenes)).forEach {s->add(buildJsonObject {put("id",s.id);put("code",s.code);s.title?.let {put("title",it)}})}})
            put("shot_count",index.shots.count {it.projectId==p.id})
            put("recent_outputs",buildJsonArray {media.sortedByDescending {it.createdAt}.take(d.limits().maximum(d.limits().maxOverviewOutputs)).forEach {a->add(buildJsonObject {put("asset_id",a.id);put("name",a.displayName);put("type",a.type.name);a.shotId?.let {put("shot_id",it)}})}})
            put("recent_generations",buildJsonArray {jobs.take(d.limits().maximum(d.limits().maxOverviewGenerations)).forEach {j->add(buildJsonObject {put("id",j.id);j.nexusJobId?.let {put("job_id",it)};put("status",j.status);put("prompt",j.prompt.take(d.limits().maximum(d.limits().maxOverviewPromptChars)))})}})
            put("note","Para detalles antiguos usa search_project_memory; para archivos concretos usa search_assets. No hace falta cargar toda la biblioteca.")
        }
    }
}

class SearchProjectMemoryTool(private val d:ToolDeps):BaseTool(
    "search_project_memory",ToolCategory.READ,
    "Recupera decisiones antiguas, reglas, personajes o planos del proyecto por palabras clave, sin reenviar todo el historial. Búsqueda local, sin otra llamada a un modelo.",
    schema {string("query","Palabras clave.",required=true);string("project","Nombre o id; por defecto el activo.")},
) {
    override suspend fun execute(input:JsonObject,session:AgentSession):ToolOutcome {
        val p=d.assets.findProject(input.str("project") ?: session.activeProjectId ?: return fail("Indica el proyecto.")) ?: return fail("Proyecto desconocido.")
        val words=AssetIndex.norm(input.req("query")).split(' ').filter {it.isNotBlank()}
        val index=d.assets.snapshot()
        val entries=buildList<Pair<String,String>> {
            p.memory.synopsis?.let {add("sinopsis" to it)};p.memory.tone?.let {add("tono" to it)};p.memory.visualStyle?.let {add("estilo" to it)}
            p.memory.rules.forEach {add("regla" to it)}
            p.memory.notes.asReversed().forEach {add("nota ${it.at}" to it.text)}
            index.characters.filter {it.projectId==p.id}.forEach {add("personaje ${it.name}" to "${it.name}: ${it.description.orEmpty()}")}
            index.shots.filter {it.projectId==p.id}.forEach {add("plano ${it.code}" to "${it.code}: ${it.description.orEmpty()}")}
        }
        val hits=entries.map {entry->entry to words.count {it in AssetIndex.norm(entry.first+" "+entry.second)}}.filter {it.second>0}.sortedByDescending {it.second}
        return ok {put("project_id",p.id);put("matches",hits.size);put("results",buildJsonArray {hits.take(d.limits().maximum(d.limits().maxMemorySearchResults)).forEach {(entry,_)->add(buildJsonObject {put("source",entry.first);put("text",entry.second.take(d.limits().maximum(d.limits().maxMemoryExcerptChars)))})}})}
    }
}
