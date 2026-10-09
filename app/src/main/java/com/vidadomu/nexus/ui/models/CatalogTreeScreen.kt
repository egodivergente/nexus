package com.vidadomu.nexus.ui.models

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.data.repository.*
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.components.rememberPriceEstimate
import com.vidadomu.nexus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onSelectModelAndCreate: (NexusModel) -> Unit) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val models by app.modelCatalogRepository.models.collectAsState()
    val loading by app.modelCatalogRepository.isLoading.collectAsState()
    val error by app.modelCatalogRepository.errorMessage.collectAsState()
    val hiddenEntries by app.modelCatalogRepository.hiddenEntries.collectAsState()
    var personalizing by rememberSaveable { mutableStateOf(false) }
    var manageHidden by rememberSaveable { mutableStateOf(false) }
    var pendingHide by remember { mutableStateOf<Pair<String,String>?>(null) }
    val prefs = remember { app.getSharedPreferences("catalog_grid_v2", 0) }
    var compactCards by remember {mutableStateOf(prefs.getBoolean("compact_cards",true))}
    var gridCards by remember {mutableStateOf(prefs.getBoolean("grid_cards",false))}
    var moreMenu by remember {mutableStateOf(false)}
    LaunchedEffect(models) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {models.forEach {modelSearchText(it)}}
    }
    var modality by rememberSaveable { mutableStateOf(runCatching { Modality.valueOf(prefs.getString("browser_modality", "VIDEO")!!) }.getOrDefault(Modality.VIDEO)) }
    var family by rememberSaveable { mutableStateOf<String?>(null) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by remember { mutableStateOf(prefs.getStringSet("model_favorites", emptySet())!!.toSet()) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    val jobs by app.generationRepository.allJobs.collectAsState()
    var catalogSort by remember {mutableStateOf(runCatching {CatalogSort.valueOf(prefs.getString("sort","PERSONAL")!!)}.getOrDefault(CatalogSort.PERSONAL))}
    var onlyDated by remember {mutableStateOf(prefs.getBoolean("release_only_dated",false))}
    var providerFilter by rememberSaveable {mutableStateOf<String?>(null)}
    var showSecondary by rememberSaveable {mutableStateOf(false)}
    var imageReferences by rememberSaveable { mutableStateOf(true) }
    var allImageFamilies by rememberSaveable { mutableStateOf(false) }
    var use by rememberSaveable { mutableStateOf<CreationUse?>(null) }
    val visible = remember(models, modality, query, use, favorites, favoritesOnly,providerFilter,imageReferences) {
        browseCatalog(models.filter {(providerFilter==null || it.providerId==providerFilter) && (modality != Modality.IMAGE || !imageReferences || ImageModelChoices.acceptsReferences(it))}, modality, use, query, null, favoritesOnly, favorites, CatalogOrder.PRIORITY)
    }
    val orderScope = "order:${modality.name}:${family ?: if(query.isBlank() && !favoritesOnly) "families" else "search-models"}"
    var savedOrder by remember(orderScope) { mutableStateOf(prefs.getString(orderScope, "").orEmpty().split("\n").filter {it.isNotBlank()}) }
    var contextTarget by remember { mutableStateOf<Triple<String,String,NexusModel?>?>(null) }
    val sections = remember(visible, favorites,savedOrder,modality) { catalogSections(visible, favorites).sortedWith(
        compareBy<CatalogSection> {savedOrder.indexOf(it.family).let {index -> if(index<0) Int.MAX_VALUE else index}}
            .thenByDescending { section -> section.models.any { modelBookmark(it) in favorites } }
            .thenBy { section -> if(modality == Modality.IMAGE) ImageModelChoices.priority(section.family) else 0 }
            .thenByDescending { section -> section.models.any { CatalogFocus.forModel(it)!=null } }
    ) }
    val groups = remember(visible, family, favorites,savedOrder,catalogSort,jobs,onlyDated) { filterDatedGroups(sortGroups(catalogModelGroups(visible.filter { family == null || catalogFamily(it) == family }, favorites).sortedBy {savedOrder.indexOf(it.key).let {index -> if(index<0) Int.MAX_VALUE else index}},catalogSort,jobs),catalogSort==CatalogSort.RELEASE && onlyDated) }
    val rootLevel = family==null && query.isBlank() && !favoritesOnly && catalogSort==CatalogSort.PERSONAL
    val displayedKeys = if(rootLevel) sections.map {it.family} else groups.map {it.key}
    fun saveOrder(next:List<String>) { savedOrder=next; prefs.edit().putString(orderScope,next.joinToString("\n")).apply() }
    val listState=rememberLazyGridState()
    val useGrid=gridCards && groupKey==null && androidx.compose.ui.platform.LocalDensity.current.fontScale<1.5f
    LaunchedEffect(modality,family,groupKey,query,use,favoritesOnly,catalogSort,onlyDated) {listState.scrollToItem(0)}
    val reorderState=rememberReorderableLazyGridState(listState) {from,to ->
        val fromKey=from.key as? String
        val toKey=to.key as? String
        if(fromKey!=null && toKey!=null) saveOrder(moveCatalogItem(savedOrder,displayedKeys,fromKey,toKey))
    }
    val activeGroup = groups.firstOrNull { it.key == groupKey }
    fun back() { if(groupKey != null) groupKey = null else family = null }
    BackHandler(family != null || groupKey != null) { back() }
    fun toggleFavorite(model: NexusModel) {
        val key = modelBookmark(model)
        favorites = if(key in favorites) favorites - key else favorites + key
        prefs.edit().putStringSet("model_favorites", favorites).apply()
    }
    Scaffold(containerColor = BackgroundDark, topBar = {
        TopAppBar(title = { Text(activeGroup?.title ?: family ?: "Modelos",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) },
            navigationIcon = { if(family != null || groupKey != null) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = {
                if(personalizing) IconButton(onClick={personalizing=false}) {Icon(Icons.Default.Check,"Terminar personalización")}
                else if(groupKey==null) IconButton(onClick={favoritesOnly=!favoritesOnly;family=null}) {
                    Icon(if(favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,"Mis favoritos",tint=if(favoritesOnly) PrimaryNeon else TextSecondary)
                }
                Box {
                    IconButton(onClick={moreMenu=true}) {Icon(Icons.Default.MoreVert,"Opciones del catálogo")}
                    DropdownMenu(expanded=moreMenu,onDismissRequest={moreMenu=false}) {
                        DropdownMenuItem(text={Text("Personalizar y ordenar")},onClick={catalogSort=CatalogSort.PERSONAL;prefs.edit().putString("sort",catalogSort.name).apply();personalizing=true;moreMenu=false})
                        DropdownMenuItem(text={Text("Modelos ocultos (${hiddenEntries.size})")},onClick={manageHidden=true;moreMenu=false})
                        if(groupKey==null) DropdownMenuItem(text={Text(if(filters) "Ocultar filtros" else "Filtros y orden")},onClick={filters=!filters;moreMenu=false})
                        if(groupKey==null) DropdownMenuItem(text={Text(if(gridCards) "Ver en lista" else "Ver en cuadrícula")},leadingIcon={Icon(if(gridCards) Icons.Default.ViewList else Icons.Default.GridView,null)},onClick={gridCards=!gridCards;prefs.edit().putBoolean("grid_cards",gridCards).apply();moreMenu=false})
                        DropdownMenuItem(text={Text(if(compactCards) "Usar tarjetas amplias" else "Usar tarjetas compactas")},onClick={compactCards=!compactCards;prefs.edit().putBoolean("compact_cards",compactCards).apply();moreMenu=false})
                        DropdownMenuItem(text={Text("Actualizar catálogo")},enabled=!loading,onClick={app.modelCatalogRepository.refreshModels(true);moreMenu=false})
                    }
                }
            })
    }) { padding ->
        if(activeGroup!=null) {
            ModelOptionsScreen(activeGroup,favorites,::toggleFavorite,onSelectModelAndCreate,
                if(personalizing) ({model -> pendingHide=routeVisibilityKey(model) to (model.displayName+" · "+providerName(model.providerId))}) else null,
                modifier=Modifier.fillMaxSize().padding(padding))
        } else {
        key(modality, family, groupKey) {
        LazyVerticalGrid(columns=GridCells.Fixed(if(useGrid) 2 else 1),modifier=Modifier.fillMaxSize().padding(padding), state=listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(family!=null || favoritesOnly) item(span={GridItemSpan(maxLineSpan)}) {
                Text(if(family != null) "Elige el modelo. Las variantes están dentro."
                    else "Tus favoritos", color = TextSecondary, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            }
            if(personalizing) item(span={GridItemSpan(maxLineSpan)}) {
                Text("Arrastra el asa para ordenar o mantén pulsada una tarjeta. Tu orden se guarda.",color=TextSecondary,fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            }
                if(family == null) item(span={GridItemSpan(maxLineSpan)}) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(Modality.VIDEO to "Vídeo", Modality.IMAGE to "Imagen") + if(showSecondary || modality in listOf(Modality.AUDIO,Modality.CHAT)) listOf(Modality.AUDIO to "Audio",Modality.CHAT to "Texto") else emptyList()) { (value,label) ->
                            FilterChip(modality == value, { modality=value; family=null; groupKey=null; use=null; query=""; prefs.edit().putString("browser_modality",value.name).apply() }, label={Text(label)})
                        }
                        item {FilterChip(showSecondary,{showSecondary=!showSecondary},label={Text("Más")})}
                    }
                }
                item(span={GridItemSpan(maxLineSpan)}) {
                    OutlinedTextField(query, { query=it }, Modifier.fillMaxWidth(), singleLine=true,
                        placeholder={Text("Buscar modelo o función")}, leadingIcon={Icon(Icons.Default.Search,null)},
                        trailingIcon={if(query.isNotBlank()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Borrar búsqueda")}}, shape=RoundedCornerShape(16.dp))
                }
                if(family==null && query.isBlank()) item(span={GridItemSpan(maxLineSpan)}) {
                    Text("Todas las familias siguen aquí. Busca una tarea o abre una familia; las fichas explican cuándo usar cada modelo.",fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)
                }
                item(span={GridItemSpan(maxLineSpan)}) {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(modality == Modality.IMAGE) item { FilterChip(imageReferences, { imageReferences = !imageReferences; use = null; groupKey = null }, label = { Text(if(imageReferences) "Con referencias" else "Todos los usos") }) }
                        item {FilterChip(catalogSort==CatalogSort.RELEASE,{
                            catalogSort=if(catalogSort==CatalogSort.RELEASE) CatalogSort.PERSONAL else CatalogSort.RELEASE
                            personalizing=false
                            prefs.edit().putString("sort",catalogSort.name).apply()
                        },label={Text("Más nuevos")},leadingIcon={Icon(Icons.Default.NewReleases,null,Modifier.size(16.dp))})}
                        if(catalogSort==CatalogSort.RELEASE) item {FilterChip(onlyDated,{
                            onlyDated=!onlyDated;prefs.edit().putBoolean("release_only_dated",onlyDated).apply()
                        },label={Text("Solo con fecha")})}
                        item {FilterChip(filters,{filters=!filters},label={Text("Filtros")},leadingIcon={Icon(Icons.Default.Tune,null,Modifier.size(16.dp))})}
                    }
                }
                if(catalogSort==CatalogSort.RELEASE) item(span={GridItemSpan(maxLineSpan)}) {
                    Text("Lanzamiento: del más reciente al más antiguo. " + if(onlyDated) "Solo fechas verificadas." else "Los modelos sin fecha aparecen al final.",fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)
                }
                if(filters) item(span={GridItemSpan(maxLineSpan)}) {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        var sortMenu by remember {mutableStateOf(false)}
                        var providerMenu by remember {mutableStateOf(false)}
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box {OutlinedButton(onClick={sortMenu=true}) {Text(catalogSort.label)}
                                DropdownMenu(sortMenu,{sortMenu=false}) {CatalogSort.entries.forEach {entry -> DropdownMenuItem(text={Text(entry.label)},onClick={catalogSort=entry;prefs.edit().putString("sort",entry.name).apply();sortMenu=false})}}}
                            Box {OutlinedButton(onClick={providerMenu=true}) {Text(providerFilter?.let(::providerName) ?: "Proveedores")}
                                DropdownMenu(providerMenu,{providerMenu=false}) {
                                    DropdownMenuItem(text={Text("Todos")},onClick={providerFilter=null;providerMenu=false})
                                    models.map {it.providerId}.distinct().forEach {id -> DropdownMenuItem(text={Text(providerName(id))},onClick={providerFilter=id;providerMenu=false})}
                                }}
                        }
                        FilterChip(favoritesOnly,{favoritesOnly=!favoritesOnly},label={Text("Mis favoritos")})
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(use==null,{use=null},label={Text("Todos los usos")}) }
                            items(CreationUse.entries.filter {it.modality==modality}) { value -> FilterChip(use==value,{use=if(use==value)null else value},label={Text(value.label)}) }
                        }
                        TextButton(onClick={app.modelCatalogRepository.refreshModels(true)},enabled=!loading) {Text("Actualizar catálogo y tarifas")}
                    }
                }
                if(use != null || favoritesOnly || providerFilter!=null) item(span={GridItemSpan(maxLineSpan)}) {
                    TextButton(onClick={use=null;favoritesOnly=false;providerFilter=null}) {Text("Quitar filtros activos")}
                }
                if(loading) item(span={GridItemSpan(maxLineSpan)}) {LinearProgressIndicator(Modifier.fillMaxWidth())}
                if(error!=null) item(span={GridItemSpan(maxLineSpan)}) {Text("Mostrando catálogo guardado mientras se recupera la conexión.",color=TextSecondary,fontSize = MaterialTheme.typography.labelMedium.fontSize)}
                if(rootLevel) {
                    val prioritySections = sections.filter { it.family in ImageModelChoices.families || it.models.any { m -> modelBookmark(m) in favorites } || it.family in savedOrder }
                    val shownSections = if(modality != Modality.IMAGE || allImageFamilies || personalizing || prioritySections.isEmpty()) sections else prioritySections
                    items(shownSections,key={it.family}) { section ->
                        val children=catalogModelGroups(section.models,favorites)
                        ReorderableItem(reorderState,key=section.family) { _ ->
                        val brief=section.models.firstNotNullOfOrNull(CatalogFocus::forModel)
                        NavigationCard(section.family,section.family,brief?.let {"${children.size} modelos · Ejemplo: ${it.good}"} ?: "${children.size} modelos",section.models,modality,
                            onClick={family=section.family;filters=false}, compact=compactCards,grid=useGrid,
                            onLongClick={contextTarget=Triple(familyVisibilityKey(section.family),section.family,null)},
                            dragModifier=if(personalizing) Modifier.draggableHandle() else null,
                            cardModifier=if(personalizing) Modifier.longPressDraggableHandle() else Modifier,
                            onHide=if(personalizing) ({pendingHide=familyVisibilityKey(section.family) to section.family}) else null)
                        }
                    }
                    if(modality == Modality.IMAGE && sections.size > prioritySections.size && !personalizing) item(span={GridItemSpan(maxLineSpan)}) {
                        TextButton(onClick={allImageFamilies=!allImageFamilies}, modifier=Modifier.fillMaxWidth()) {Text(if(allImageFamilies) "Ver solo mis familias" else "Otras familias")}
                    }
                } else {
                    items(groups,key={it.key}) { group ->
                        ReorderableItem(reorderState,key=group.key) { _ ->
                        val focusAdvice=group.routes.firstNotNullOfOrNull(CatalogFocus::forModel)
                        NavigationCard(group.title,catalogFamily(group.routes.first()),focusAdvice?.let {"Bueno para: ${it.good}"} ?: groupPurpose(group),group.routes,modality,
                            onClick={groupKey=group.key;filters=false},releaseLabel=groupReleaseLabel(group.routes),compact=compactCards,grid=useGrid,
                            onLongClick={contextTarget=Triple(groupVisibilityKey(group.routes.first()),group.title,group.routes.first())},
                            dragModifier=if(personalizing) Modifier.draggableHandle() else null,
                            cardModifier=if(personalizing) Modifier.longPressDraggableHandle() else Modifier,
                            onHide=if(personalizing) ({pendingHide=groupVisibilityKey(group.routes.first()) to group.title}) else null,
                            highlight=focusAdvice?.let {"Evítalo si: ${it.avoid}"} ?: modelGroupHighlight(group.routes))
                        }
                    }
                }
                if(visible.isEmpty() || !rootLevel && groups.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                    Text(if(loading && models.isEmpty()) "Preparando tu catálogo…" else if(favoritesOnly) "No hay favoritos en esta sección." else "No hay resultados con estos filtros.",color=TextSecondary)
                    TextButton(onClick={query="";use=null;favoritesOnly=false;providerFilter=null;onlyDated=false;prefs.edit().putBoolean("release_only_dated",false).apply()}) {Text("Limpiar filtros")}
                }
            item(span={GridItemSpan(maxLineSpan)}) {Text("Precios base orientativos. El total se calcula al configurar.",color=TextSecondary,fontSize = MaterialTheme.typography.labelMedium.fontSize)}
            item(span={GridItemSpan(maxLineSpan)}) {Spacer(Modifier.height(24.dp))}
        }
        }
        }
    }
    contextTarget?.let { (key,label,model) ->
        AlertDialog(onDismissRequest={contextTarget=null},title={Text(label)},text={Column {
            TextButton(onClick={catalogSort=CatalogSort.PERSONAL;prefs.edit().putString("sort",catalogSort.name).apply();personalizing=true;contextTarget=null}) {Text("Mover y ordenar")}
            TextButton(onClick={
                val itemKey=if(model==null) label else groups.firstOrNull {groupVisibilityKey(it.routes.first())==key}?.key
                if(itemKey!=null) saveOrder(listOf(itemKey)+(savedOrder+displayedKeys).distinct().filterNot {it==itemKey})
                contextTarget=null
            }) {Text("Poner al principio")}
            if(model!=null) TextButton(onClick={toggleFavorite(model);contextTarget=null}) {Text(if(modelBookmark(model) in favorites) "Quitar favorito" else "Marcar favorito")}
            TextButton(onClick={pendingHide=key to label;contextTarget=null}) {Text("Quitar de mi catálogo")}
            TextButton(onClick={saveOrder(emptyList());contextTarget=null}) {Text("Restaurar orden predeterminado")}
        }},confirmButton={TextButton(onClick={contextTarget=null}) {Text("Cerrar")}})
    }
    pendingHide?.let { (key,label) ->
        AlertDialog(onDismissRequest={pendingHide=null},title={Text("Quitar $label")},
            text={Text("Dejará de aparecer en tu catálogo y al elegir modelos para crear. Tu historial y archivos se conservan. Puedes recuperarlo en Modelos ocultos.")},
            confirmButton={TextButton(onClick={app.modelCatalogRepository.setCatalogHidden(key,label,true);pendingHide=null;groupKey=null}) {Text("Quitar")}},
            dismissButton={TextButton(onClick={pendingHide=null}) {Text("Cancelar")}})
    }
    if(manageHidden) AlertDialog(onDismissRequest={manageHidden=false},title={Text("Modelos ocultos")},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(hiddenEntries.isEmpty()) item {Text("No has ocultado ningún modelo.")}
            items(hiddenEntries.toList(),key={it.first}) { (key,label) ->
                Column {
                    Text(label,fontWeight=FontWeight.SemiBold)
                    Text(when {key.startsWith("family:")->"Familia completa";key.startsWith("group:")->"Modelo y todas sus variantes";else->"Variante concreta"},fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)
                    TextButton(onClick={app.modelCatalogRepository.setCatalogHidden(key,label,false)}) {Text("Recuperar")}
                }
            }
        }},confirmButton={TextButton(onClick={manageHidden=false}) {Text("Cerrar")}})

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationCard(title:String,family:String,subtitle:String,routes:List<NexusModel>,modality:Modality,onClick:()->Unit,highlight:String?=null,onHide:(()->Unit)?=null,onLongClick:()->Unit={},dragModifier:Modifier?=null,cardModifier:Modifier=Modifier,compact:Boolean=false,grid:Boolean=false,releaseLabel:String?=null) {
    val app=LocalContext.current.applicationContext as NexusApplication
    val currency by app.currencyManager.state.collectAsState()
    val price=routes.mapNotNull(::referencePrice).minOrNull()
    Surface(modifier=cardModifier.fillMaxWidth().combinedClickable(onClick=onClick,onLongClick=onLongClick),shape=RoundedCornerShape(20.dp),color=SurfaceDark) {
        Column(Modifier.padding(if(compact) 14.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                BrandLogo(family=family,size=if(compact) 34.dp else 40.dp,containerSize=if(compact) 44.dp else 52.dp)
                if(!grid) Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                    Text(title,fontSize = MaterialTheme.typography.titleLarge.fontSize,fontWeight=FontWeight.SemiBold,color=TextPrimary)
                    releaseLabel?.let {Text(it,fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)}
                    Text(subtitle,fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(compact) Text(price?.let { "Desde ≈ ${app.currencyManager.formatUsd(it)} / ${if(modality==Modality.VIDEO) "5 s" else "imagen"}" } ?: "Coste en opciones",fontSize = MaterialTheme.typography.bodyMedium.fontSize,fontWeight=FontWeight.Medium,color=PrimaryNeon)
                }
                if(grid) Spacer(Modifier.weight(1f))
                if(dragModifier!=null) IconButton(onClick={},modifier=dragModifier) {Icon(Icons.Default.DragHandle,"Arrastrar $title",tint=PrimaryNeon)}
                else Icon(Icons.Default.ChevronRight,"Abrir $title",tint=PrimaryNeon)
            }
            if(grid) {
                Text(title,fontSize = MaterialTheme.typography.titleMedium.fontSize,fontWeight=FontWeight.SemiBold,color=TextPrimary)
                releaseLabel?.let {Text(it,fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)}
                    Text(subtitle,fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary,maxLines=if(compact) 3 else 5,overflow=TextOverflow.Ellipsis)
            }
            highlight?.takeIf {!grid || !compact}?.let { Text(it,fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis) }
            currency.preference
            if(!compact || grid) {
                Text(price?.let { "Desde ≈ ${app.currencyManager.formatUsd(it)}" } ?: "Coste en opciones",color=PrimaryNeon,fontSize = MaterialTheme.typography.bodyMedium.fontSize,fontWeight=FontWeight.Medium)
                if(price!=null) Text(if(modality==Modality.VIDEO) "por 5 s" else "por imagen",color=TextSecondary,fontSize = MaterialTheme.typography.labelMedium.fontSize)
            }

            onHide?.let { action -> TextButton(onClick=action) {Icon(Icons.Default.VisibilityOff,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(grid) "Quitar" else "Quitar de mi catálogo")} }
        }
    }
}

