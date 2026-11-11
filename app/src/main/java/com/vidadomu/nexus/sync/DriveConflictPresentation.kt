package com.vidadomu.nexus.sync

import com.vidadomu.nexus.agent.core.sync.SyncBlobs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.*

enum class DriveConflictKind(val collection:String,val singular:String,val plural:String) {
    PROJECTS("projects","proyecto","proyectos"),
    SCENES("scenes","escena","escenas"),
    SHOTS("shots","plano","planos"),
    CHARACTERS("characters","personaje","personajes"),
    ASSETS("assets","archivo","archivos"),
    GENERATIONS("generations","generación","generaciones"),
    CONVERSATIONS("conversations","chat","chats"),
    MONTAGES("montages","montaje","montajes"),
    STORY_PROJECTS("story:projects","historia de proyecto","historias de proyecto"),
    STORY_SCENES("story:scenes","escena de guion","escenas de guion"),
    STORY_SHOTS("story:shots","plano de guion","planos de guion"),
    STORY_CHARACTERS("story:characters","personaje del guion","personajes del guion"),
    STORY_GENERATIONS("story:generations","contexto de generación","contextos de generación"),
    STORY_BLOCKS("story:blocks","fragmento de guion","fragmentos de guion"),
    STORY_LINKS("story:links","vínculo de material","vínculos de material"),
    STORY_ELEMENTS("story:elements","elemento de continuidad","elementos de continuidad"),
    STORY_PREFERENCES("story:preferences","indicación","indicaciones"),
    STORY_PROPOSALS("story:proposals","propuesta","propuestas"),
    STORY_TASKS("story:tasks","tarea del proyecto","tareas del proyecto"),
    STORY_HISTORY("story:history","cambio del proyecto","cambios del proyecto"),
    STORY_RECEIPTS("story:receipts","registro de operación","registros de operación"),
    STORY_ADOPTED("story:adopted","grupo de referencias heredadas","grupos de referencias heredadas"),
    OTHER("","elemento","otros elementos");
    fun count(n:Int)="$n "+if(n==1)singular else plural
    companion object {
        fun of(key:String):DriveConflictKind {
            val collection=if(key.startsWith("story:"))key.split(':',limit=3).take(2).joinToString(":") else key.substringBefore(':')
            return entries.firstOrNull {it!=OTHER && it.collection==collection}?:OTHER
        }
    }
}

/**
 * A `nexus-sync:` URI only proves the revision references a blob uploaded when it was captured;
 * current remote availability is not checked here. Any other URI means capture failed for that revision.
 */
enum class DriveOriginalState(val label:String) {
    REFERENCED_IN_DRIVE("Original referenciado en Drive (disponibilidad sin comprobar)"),
    NOT_REFERENCED_IN_DRIVE("Original sin referencia en Drive"),
}

data class DriveConflictField(val label:String,val value:String)

data class DriveConflictVersion(
    val id:String,
    val ordinal:Int,
    val device:String?,
    val date:String?,
    val removes:Boolean,
    val name:String?,
    val fields:List<DriveConflictField>,
    val original:DriveOriginalState?,
) {
    val deviceLabel get()=device?:"Dispositivo sin nombre"
    val dateLabel get()=date?:"Fecha no registrada"
}

data class DriveConflictItem(
    val key:String,
    val kind:DriveConflictKind,
    val title:String,
    val versions:List<DriveConflictVersion>,
    val differingFields:Set<String>,
) {
    val onlyHiddenDifferences get()=differingFields.isEmpty() && versions.none {it.removes}
    fun field(version:DriveConflictVersion,label:String)=version.fields.firstOrNull {it.label==label}?.value
}

data class DriveConflictGroup(val kind:DriveConflictKind,val items:List<DriveConflictItem>)

data class DriveConflictSummary(val groups:List<DriveConflictGroup>,val missingMedia:Int,val malformed:Int) {
    val total get()=groups.sumOf {it.items.size}
    val headline:String get()=when(total) {
        0->"Sin versiones para revisar"
        1->"1 elemento con versiones distintas"
        else->"$total elementos con versiones distintas"
    }
    val breakdown:String get()=groups.joinToString(" · ") {it.kind.count(it.items.size)}
    fun item(key:String)=groups.firstNotNullOfOrNull {group->group.items.firstOrNull {it.key==key}}
}

object DriveConflictPresentation {
    const val PAGE_SIZE=5
    private const val EXCERPT=80
    private val spanish=Locale("es","ES")

    fun summarize(
        status:JsonObject,
        projectNames:Map<String,String> = emptyMap(),
        zone:TimeZone=TimeZone.getDefault(),
        relatedNames:Map<String,String> = emptyMap(),
    ):DriveConflictSummary {
        val missing=(status["missingMedia"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0)?:0
        var malformed=0
        val items=(status["conflicts"] as? JsonArray).orEmpty().mapNotNull {entry->
            item(entry,projectNames,zone,relatedNames).also {if(it==null)malformed++}
        }
        val groups=DriveConflictKind.entries.mapNotNull {kind->
            items.filter {it.kind==kind}.takeIf {it.isNotEmpty()}?.let {DriveConflictGroup(kind,disambiguate(it))}
        }
        return DriveConflictSummary(groups,missing,malformed)
    }

    fun <T> page(items:List<T>,pages:Int,size:Int=PAGE_SIZE):List<T> = items.take((pages.coerceAtLeast(1)*size).coerceAtMost(items.size))

    fun formatDate(at:Long?,zone:TimeZone=TimeZone.getDefault()):String? {
        if(at==null || at<=0)return null
        return SimpleDateFormat("d MMM yyyy, HH:mm",spanish).apply {timeZone=zone}.format(Date(at))
    }

    private fun item(entry:JsonElement,projectNames:Map<String,String>,zone:TimeZone,relatedNames:Map<String,String>):DriveConflictItem? {
        val conflict=entry as? JsonObject?:return null
        val key=conflict.string("key")?:return null
        val rawVersions=(conflict["versions"] as? JsonArray)?.map {it as? JsonObject?:return null}?:return null
        if(rawVersions.size<2)return null
        val kind=DriveConflictKind.of(key)
        val versions=rawVersions.mapIndexed {index,raw->
            val id=raw.string("id")?:return null
            val rawValue=raw["value"] as? JsonObject
            if(raw["value"]!=null && raw["value"]!=JsonNull && rawValue==null)return null
            val value=rawValue?.let(::editorial)
            DriveConflictVersion(
                id=id,ordinal=index+1,
                device=raw.string("device")?.takeIf {it.isNotBlank()},
                date=formatDate((raw["at"] as? JsonPrimitive)?.longOrNull,zone),
                removes=value==null,
                name=value?.let {name(kind,it,projectNames,relatedNames)},
                fields=value?.let {fields(kind,it,projectNames,zone,relatedNames)}.orEmpty(),
                original=value?.takeIf {kind==DriveConflictKind.ASSETS}?.string("uri")?.let {
                    if(it.startsWith(SyncBlobs.PREFIX))DriveOriginalState.REFERENCED_IN_DRIVE else DriveOriginalState.NOT_REFERENCED_IN_DRIVE
                },
            )
        }
        val present=versions.filterNot {it.removes}
        val labels=present.flatMap {version->version.fields.map {it.label}}.distinct()
        val differing=labels.filter {label->present.map {version->version.fields.firstOrNull {it.label==label}?.value}.distinct().size>1}
            .toMutableSet()
        if(present.mapNotNull {it.original}.distinct().size>1)differing+=ORIGINAL
        val name=present.firstNotNullOfOrNull {it.name}
        val noun=kind.singular.replaceFirstChar {it.uppercase()}
        val title=if(name!=null)"$noun «$name»" else "$noun sin nombre"
        return DriveConflictItem(key,kind,title,versions,differing)
    }

    const val ORIGINAL="Original"

    private fun disambiguate(items:List<DriveConflictItem>):List<DriveConflictItem> {
        val repeated=items.groupingBy {it.title}.eachCount().filterValues {it>1}.keys
        val seen=mutableMapOf<String,Int>()
        return items.map {item->
            if(item.title !in repeated)item
            else {
                val n=(seen[item.title]?:0)+1
                seen[item.title]=n
                item.copy(title=item.title+" ($n)")
            }
        }
    }

    private fun editorial(value:JsonObject):JsonObject {
        val payload=value["payload"] as? JsonObject?:return value
        return JsonObject(payload+mapOf("id" to (payload["id"]?:value["id"]?:JsonNull),"projectId" to (payload["projectId"]?:value["projectId"]?:JsonNull)))
    }

    private fun baseKind(kind:DriveConflictKind)=when(kind) {
        DriveConflictKind.STORY_PROJECTS->DriveConflictKind.PROJECTS
        DriveConflictKind.STORY_SCENES->DriveConflictKind.SCENES
        DriveConflictKind.STORY_SHOTS->DriveConflictKind.SHOTS
        DriveConflictKind.STORY_CHARACTERS->DriveConflictKind.CHARACTERS
        DriveConflictKind.STORY_GENERATIONS->DriveConflictKind.GENERATIONS
        else->kind
    }

    private fun name(kind:DriveConflictKind,row:JsonObject,projects:Map<String,String>,relatedNames:Map<String,String>):String? = (when(baseKind(kind)) {
        DriveConflictKind.ASSETS->row.string("displayName")
        DriveConflictKind.SCENES->row.string("title")?:row.string("code")
        DriveConflictKind.SHOTS->row.string("code")
        DriveConflictKind.GENERATIONS->row.string("prompt")?.let(::excerpt)?:row.string("modelId")
        DriveConflictKind.CONVERSATIONS->(row["items"] as? JsonArray)?.firstNotNullOfOrNull {(it as? JsonObject)?.string("text")}?.let {excerpt(it,40)}
        DriveConflictKind.MONTAGES->row.string("projectId")?.let {projects[it]}
        DriveConflictKind.STORY_BLOCKS,DriveConflictKind.STORY_PREFERENCES->row.string("text")?.let {excerpt(it,40)}
        DriveConflictKind.STORY_LINKS->listOfNotNull(row.string("assetId")?.let {relatedNames[it]},row.string("targetId")?.let {relatedNames[it]?:projects[it]}).joinToString(" → ").takeIf {it.isNotBlank()}
        else->row.string("name")?:row.string("displayName")?:row.string("title")
    }?:row.string("id")?.let {relatedNames[it]?:projects[it]})?.takeIf {it.isNotBlank()}

    private fun fields(kind:DriveConflictKind,row:JsonObject,projects:Map<String,String>,zone:TimeZone,relatedNames:Map<String,String>):List<DriveConflictField> {
        val out=mutableListOf<DriveConflictField>()
        fun add(label:String,value:String?) {if(!value.isNullOrBlank()){out.removeAll {it.label==label};out+=DriveConflictField(label,value)}}
        fun count(label:String,key:String,singular:String,plural:String) {
            (row[key] as? JsonArray)?.let {add(label,"${it.size} "+if(it.size==1)singular else plural)}
        }
        when(baseKind(kind)) {
            DriveConflictKind.PROJECTS->{
                add("Nombre",row.string("name"))
                add("Descripción",row.string("description")?.let(::excerpt))
                (row["archived"] as? JsonPrimitive)?.booleanOrNull?.let {add("Archivado",if(it)"Sí" else "No")}
                ((row["memory"] as? JsonObject)?.get("updatedAt") as? JsonPrimitive)?.longOrNull?.let {add("Memoria actualizada",formatDate(it,zone))}
            }
            DriveConflictKind.SCENES->{add("Código",row.string("code"));add("Título",row.string("title"))}
            DriveConflictKind.SHOTS->{add("Código",row.string("code"));add("Descripción",row.string("description")?.let(::excerpt))}
            DriveConflictKind.CHARACTERS->{
                add("Nombre",row.string("name"));add("Descripción",row.string("description")?.let(::excerpt))
                count("Alias","aliases","alias","alias");count("Referencias","referenceAssetIds","referencia","referencias")
            }
            DriveConflictKind.ASSETS->{
                add("Nombre",row.string("displayName"))
                add("Tipo",when(row.string("type")){"IMAGE"->"Imagen";"VIDEO"->"Vídeo";"AUDIO"->"Audio";"OTHER"->"Otro";else->null})
                add("Formato",row.string("mimeType"))
                val width=(row["width"] as? JsonPrimitive)?.intOrNull;val height=(row["height"] as? JsonPrimitive)?.intOrNull
                if(width!=null && height!=null)add("Resolución","$width × $height")
                (row["durationSec"] as? JsonPrimitive)?.doubleOrNull?.let {add("Duración",String.format(spanish,"%.1f s",it))}
                (row["libraryDeleted"] as? JsonPrimitive)?.booleanOrNull?.let {add("Biblioteca",if(it)"Quitado de la biblioteca" else "Visible en la biblioteca")}
            }
            DriveConflictKind.GENERATIONS->{
                add("Modalidad",row.string("modality"));add("Modelo",row.string("modelId"));add("Estado",row.string("status"))
                add("Instrucción",row.string("prompt")?.let(::excerpt))
                count("Resultados","outputAssetIds","resultado","resultados")
            }
            DriveConflictKind.CONVERSATIONS->count("Mensajes","items","mensaje","mensajes")
            else->{}
        }
        if(kind.collection.startsWith("story:")) {
            for((key,label) in listOf("title" to "Título","description" to "Acción y descripción","text" to "Texto","speaker" to "Personaje que habla",
                "intent" to "Intención narrativa","framing" to "Encuadre","camera" to "Cámara","performance" to "Actuación","dialogue" to "Diálogo",
                "sound" to "Sonido","note" to "Nota","notes" to "Notas","treatment" to "Tratamiento","format" to "Formato","detail" to "Detalle"))add(label,row.string(key))
            row.string("role")?.let {add("Uso del material",when(it){"identity"->"Identidad";"costume"->"Vestuario";"location"->"Escenario";"composition"->"Encuadre";"lighting"->"Luz";"sketch"->"Boceto";"generated"->"Imagen generada";"candidate"->"Toma candidata";"chosen"->"Toma elegida";"discarded"->"Descartado";"audio"->"Audio";"output"->"Resultado";"final"->"Material final";"source"->"Material de origen";else->"Referencia"})}
            row.string("scope")?.let {scope->add("Alcance",when(scope){"project"->"Proyecto";"scene"->"Escena";"shot"->"Plano";"character"->"Personaje";"element"->"Elemento de continuidad";"block"->"Fragmento de guion";else->null})}
            (row.string("behavior")?:row.string("mode"))?.let {behavior->add("Referencias",when(behavior){"complement"->"Complementa las referencias generales";"replace"->"Sustituye las referencias generales";else->null})}
            (row["order"] as? JsonPrimitive)?.intOrNull?.let {add("Posición",(it+1).toString())}
            (row["durationSec"] as? JsonPrimitive)?.doubleOrNull?.let {add("Duración orientativa","$it s")}
            (row["selected"] as? JsonPrimitive)?.booleanOrNull?.let {add("Material elegido",if(it)"Sí" else "No")}
            (row["enabled"] as? JsonPrimitive)?.booleanOrNull?.let {add("Indicación activa",if(it)"Sí" else "No")}
            row.string("narrativeState")?.let {add("Lugar en la historia",if(it=="idea")"Idea por explorar" else "Incorporado")}
            (row.string("productionState")?:row.string("status"))?.let {add("Estado de trabajo",when(it){"pending"->"Pendiente";"prepared"->"Preparado";"generated"->"Generado";"chosen"->"Elegido";"queued"->"En cola";"pending_resume"->"Pendiente de reanudación";"complete"->"Terminado";"cancelled"->"Cancelado";"error"->"Error";"applied"->"Aplicado";"rejected"->"Descartado";else->null})}
            for((key,label) in listOf("assetId" to "Archivo","targetId" to "Destino","characterId" to "Personaje","sceneId" to "Escena"))
                row.string(key)?.let {add(label,relatedNames[it]?:projects[it]?:"Contexto aún no recibido")}
            for((key,label) in listOf("characterIds" to "Personajes","locationIds" to "Escenarios"))
                (row[key] as? JsonArray)?.let {array->add(label,array.mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.joinToString(", ") {relatedNames[it]?:"Contexto aún no recibido"}.ifBlank {"Ninguno"})}
            count("Cambios propuestos","changes","cambio","cambios")
        }
        row.string("projectId")?.let {id->add("Proyecto",projects[id]?:"Proyecto no disponible en este dispositivo")}
        return out
    }

    private fun excerpt(text:String)=excerpt(text,EXCERPT)
    private fun excerpt(text:String,limit:Int):String {
        val flat=text.replace(Regex("\\s+")," ").trim()
        return if(flat.length<=limit)flat else flat.take(limit-1).trimEnd()+"…"
    }
    private fun JsonObject.string(key:String)=(this[key] as? JsonPrimitive)?.takeIf {it.isString}?.content
}
