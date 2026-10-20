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
    OTHER("","elemento","otros elementos");
    fun count(n:Int)="$n "+if(n==1)singular else plural
    companion object {
        fun of(key:String)=entries.firstOrNull {it!=OTHER && it.collection==key.substringBefore(':')}?:OTHER
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
    ):DriveConflictSummary {
        val missing=(status["missingMedia"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0)?:0
        var malformed=0
        val items=(status["conflicts"] as? JsonArray).orEmpty().mapNotNull {entry->
            item(entry,projectNames,zone).also {if(it==null)malformed++}
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

    private fun item(entry:JsonElement,projectNames:Map<String,String>,zone:TimeZone):DriveConflictItem? {
        val conflict=entry as? JsonObject?:return null
        val key=conflict.string("key")?:return null
        val rawVersions=(conflict["versions"] as? JsonArray)?.map {it as? JsonObject?:return null}?:return null
        if(rawVersions.size<2)return null
        val kind=DriveConflictKind.of(key)
        val versions=rawVersions.mapIndexed {index,raw->
            val id=raw.string("id")?:return null
            val value=raw["value"] as? JsonObject
            if(raw["value"]!=null && raw["value"]!=JsonNull && value==null)return null
            DriveConflictVersion(
                id=id,ordinal=index+1,
                device=raw.string("device")?.takeIf {it.isNotBlank()},
                date=formatDate((raw["at"] as? JsonPrimitive)?.longOrNull,zone),
                removes=value==null,
                name=value?.let {name(kind,it,projectNames)},
                fields=value?.let {fields(kind,it,projectNames,zone)}.orEmpty(),
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

    private fun name(kind:DriveConflictKind,row:JsonObject,projects:Map<String,String>):String? = when(kind) {
        DriveConflictKind.ASSETS->row.string("displayName")
        DriveConflictKind.SCENES->row.string("title")?:row.string("code")
        DriveConflictKind.SHOTS->row.string("code")
        DriveConflictKind.GENERATIONS->row.string("prompt")?.let(::excerpt)?:row.string("modelId")
        DriveConflictKind.CONVERSATIONS->(row["items"] as? JsonArray)?.firstNotNullOfOrNull {(it as? JsonObject)?.string("text")}?.let {excerpt(it,40)}
        DriveConflictKind.MONTAGES->row.string("projectId")?.let {projects[it]}
        else->row.string("name")?:row.string("displayName")
    }?.takeIf {it.isNotBlank()}

    private fun fields(kind:DriveConflictKind,row:JsonObject,projects:Map<String,String>,zone:TimeZone):List<DriveConflictField> {
        val out=mutableListOf<DriveConflictField>()
        fun add(label:String,value:String?) {if(!value.isNullOrBlank())out+=DriveConflictField(label,value)}
        fun count(label:String,key:String,singular:String,plural:String) {
            (row[key] as? JsonArray)?.let {add(label,"${it.size} "+if(it.size==1)singular else plural)}
        }
        when(kind) {
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
