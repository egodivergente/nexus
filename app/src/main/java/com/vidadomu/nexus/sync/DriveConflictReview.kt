package com.vidadomu.nexus.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The manager keeps a single pending resolution, so a second choice before the next
 * sync would overwrite the first. Every choice is blocked while one is pending.
 */
@Composable fun DriveConflictReview(
    summary:DriveConflictSummary,
    pendingKey:String?,
    onResolve:(key:String,versionId:String)->Unit,
    modifier:Modifier=Modifier,
) {
    var submitted by remember(summary) {mutableStateOf<String?>(null)}
    val pending=pendingKey?:submitted
    var openKey by rememberSaveable {mutableStateOf<String?>(null)}
    var pages by remember(summary.total) {mutableStateOf(mapOf<String,Int>())}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(summary.missingMedia>0)MissingOriginalsNotice(summary.missingMedia)
        if(summary.malformed>0)Text(
            "${summary.malformed} "+(if(summary.malformed==1)"conflicto no se puede mostrar" else "conflictos no se pueden mostrar")+" con seguridad. No se ha elegido ninguna versión por ti.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,
        )
        if(summary.total==0)return@Column
        Text(summary.headline,style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics {heading()})
        Text(summary.breakdown,style=MaterialTheme.typography.bodySmall)
        Text("Toca un elemento para comparar sus versiones. Nada cambia hasta que confirmes una.",style=MaterialTheme.typography.bodySmall)
        pending?.let {key->
            val title=summary.item(key)?.title
            Text(
                (title?.let {"Elección guardada para $it. "}?:"Hay una elección guardada. ")+
                    "Se aplicará en la próxima sincronización; hasta entonces no puedes elegir otra versión.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,
                modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite},
            )
        }
        for(group in summary.groups) {
            val shown=pages[group.kind.name]?:1
            val visible=DriveConflictPresentation.page(group.items,shown)
            Text(
                group.kind.plural.replaceFirstChar {it.uppercase()}+" ("+group.items.size+")",
                style=MaterialTheme.typography.labelLarge,
                modifier=Modifier.padding(top=4.dp).semantics {heading()},
            )
            for(item in visible)ConflictRow(item,pending==item.key) {openKey=item.key}
            val remaining=group.items.size-visible.size
            if(remaining>0)TextButton(onClick={pages=pages+(group.kind.name to shown+1)}) {
                Text("Mostrar más "+group.kind.plural+" (quedan $remaining)")
            }
        }
    }
    val open=openKey?.let(summary::item)
    if(openKey!=null && open==null)SideEffect {openKey=null}
    if(open!=null)DriveConflictDetail(
        item=open,
        canChoose=pending==null,
        pendingHere=pending==open.key,
        onDismiss={openKey=null},
        onConfirmed={version->
            if(submitted==null && pendingKey==null) {
                submitted=open.key
                onResolve(open.key,version.id)
            }
            openKey=null
        },
    )
}

@Composable private fun MissingOriginalsNotice(count:Int) {
    val text=if(count==1)"Nexus no pudo leer 1 original para copiarlo a Drive."
        else "Nexus no pudo leer $count originales para copiarlos a Drive."
    Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.small,modifier=Modifier.fillMaxWidth().testTag("drive-missing-originals")) {
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("Originales que faltan en Drive",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.semantics {heading()})
            Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
            Text("Sus fichas y referencias se conservan. Sincroniza desde el dispositivo que tenga esos archivos para copiarlos.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable private fun ConflictRow(item:DriveConflictItem,pendingHere:Boolean,onOpen:()->Unit) {
    val devices=item.versions.map {it.deviceLabel}.distinct().joinToString(", ")
    val detail="${item.versions.size} versiones · $devices"
    Surface(
        tonalElevation=1.dp,shape=MaterialTheme.shapes.small,
        modifier=Modifier.fillMaxWidth().testTag("drive-conflict-row")
            .clickable(onClickLabel="Ver y comparar versiones",role=Role.Button,onClick=onOpen)
            .semantics(mergeDescendants=true) {if(pendingHere)stateDescription="Elección pendiente de sincronizar"},
    ) {
        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
            Text(item.title,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
            Text(detail,style=MaterialTheme.typography.bodySmall)
            if(item.versions.any {it.removes})Text("Una versión retira este elemento",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            if(item.versions.any {it.original==DriveOriginalState.NOT_REFERENCED_IN_DRIVE})Text(DriveOriginalState.NOT_REFERENCED_IN_DRIVE.label+" en alguna versión",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            if(pendingHere)Text("Elección pendiente de sincronizar",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun DriveConflictDetail(
    item:DriveConflictItem,canChoose:Boolean,pendingHere:Boolean,
    onDismiss:()->Unit,onConfirmed:(DriveConflictVersion)->Unit,
) {
    var confirming by remember(item.key) {mutableStateOf<DriveConflictVersion?>(null)}
    var technical by remember(item.key) {mutableStateOf(false)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f).testTag("drive-conflict-detail")) {
            Column(Modifier.padding(16.dp)) {
                Text(item.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Elige qué versión conservar en todos tus dispositivos. Las demás no se borran: quedan en el historial de sincronización, aunque desde aquí todavía no se pueden recuperar.",style=MaterialTheme.typography.bodySmall)
                    Text("Conservar ambas como elementos separados aún no está disponible.",style=MaterialTheme.typography.bodySmall)
                    if(item.onlyHiddenDifferences)Text("Los datos visibles coinciden; la diferencia está en información interna que esta pantalla no muestra.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                    if(pendingHere)Text("Ya elegiste una versión de este elemento. Se aplicará en la próxima sincronización.",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall,modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
                    else if(!canChoose)Text("Hay otra elección pendiente de sincronizar. Puedes comparar, pero no elegir hasta entonces.",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
                    for(version in item.versions)VersionCard(item,version,canChoose) {confirming=version}
                    TextButton(onClick={technical=!technical}) {Text(if(technical)"Ocultar identificadores técnicos" else "Mostrar identificadores técnicos")}
                    if(technical) {
                        Text("Clave: "+item.key,style=MaterialTheme.typography.bodySmall)
                        for(version in item.versions)Text("Versión ${version.ordinal}: "+version.id,style=MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {TextButton(onClick=onDismiss) {Text("Cerrar")}}
            }
        }
    }
    confirming?.let {version->
        val total=item.versions.size
        AlertDialog(
            onDismissRequest={confirming=null},
            title={Text(if(version.removes)"¿Retirar este elemento?" else "¿Conservar la versión ${version.ordinal}?")},
            text={Text(
                if(version.removes)"${item.title} dejará de aparecer en todos tus dispositivos tras la próxima sincronización. Elegido: versión ${version.ordinal} de $total (${version.deviceLabel}, ${version.dateLabel})."
                else "Se conservará la versión ${version.ordinal} de $total de ${item.title} (${version.deviceLabel}, ${version.dateLabel}) en todos tus dispositivos tras la próxima sincronización. Las otras versiones quedan en el historial."
            )},
            confirmButton={
                var sent by remember(version.id) {mutableStateOf(false)}
                Button(enabled=!sent,onClick={if(!sent){sent=true;confirming=null;onConfirmed(version)}}) {
                    Text(if(version.removes)"Sí, retirar" else "Sí, conservar esta")
                }
            },
            dismissButton={TextButton(onClick={confirming=null}) {Text("Cancelar")}},
        )
    }
}

@Composable private fun VersionCard(item:DriveConflictItem,version:DriveConflictVersion,canChoose:Boolean,onChoose:()->Unit) {
    val total=item.versions.size
    val state=listOfNotNull(
        if(version.removes)"Esta versión retira el elemento" else null,
        version.original?.label,
    ).joinToString(". ")
    OutlinedCard(Modifier.fillMaxWidth().testTag("drive-conflict-version")) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Versión ${version.ordinal} de $total",style=MaterialTheme.typography.labelLarge,modifier=Modifier.semantics {heading()})
            Text(version.deviceLabel+" · "+version.dateLabel,style=MaterialTheme.typography.bodySmall)
            if(state.isNotEmpty())Text(state,style=MaterialTheme.typography.bodySmall,
                color=if(version.removes || version.original==DriveOriginalState.NOT_REFERENCED_IN_DRIVE)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.semantics {stateDescription=state})
            for(field in version.fields) {
                val differs=field.label in item.differingFields
                Text(
                    field.label+": "+field.value+if(differs)" · distinto" else "",
                    style=MaterialTheme.typography.bodySmall,
                    fontWeight=if(differs)FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if(!version.removes)for(label in item.differingFields.filter {label->label!=DriveConflictPresentation.ORIGINAL && version.fields.none {it.label==label}})
                Text("$label: sin dato en esta versión · distinto",style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
            val action=if(version.removes)"Confirmar retirada" else "Conservar esta versión"
            val description="$action: versión ${version.ordinal} de $total, ${version.deviceLabel}, ${version.dateLabel}"
            if(version.removes)OutlinedButton(enabled=canChoose,onClick=onChoose,modifier=Modifier.semantics {contentDescription=description}) {Text(action)}
            else Button(enabled=canChoose,onClick=onChoose,modifier=Modifier.semantics {contentDescription=description}) {Text(action)}
        }
    }
}
