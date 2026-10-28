package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import com.vidadomu.nexus.ui.components.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelsScreen(onSelectModelAndCreate: (NexusModel) -> Unit,
    onSelectModelWithMedia: ((NexusModel, List<Pair<android.net.Uri, String>>) -> Unit)? = null, onLocalInpaint: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val models by app.modelCatalogRepository.models.collectAsState()
    val loading by app.modelCatalogRepository.isLoading.collectAsState()
    val error by app.modelCatalogRepository.errorMessage.collectAsState()
    val hiddenEntries by app.modelCatalogRepository.hiddenEntries.collectAsState()
    val runtime=remember {com.vidadomu.nexus.settings.RuntimePreferences.get(app)}
    val rules by runtime.state.collectAsState()
    // Entering ArtBox opens Vídeos/Imágenes (the modality you chose, saved below), never Herramientas by accident
    // (revision video 1, 10:01). Tools stay open while you browse them; leaving the tab resets this.
    var toolsView by remember {mutableStateOf(false)}
    var showLegacy by rememberSaveable {mutableStateOf(false)}
    var personalizing by rememberSaveable { mutableStateOf(false) }
    var manageHidden by rememberSaveable { mutableStateOf(false) }
    var pendingHide by remember { mutableStateOf<Pair<String,String>?>(null) }
    val prefs = remember { app.getSharedPreferences("catalog_grid_v2", 0) }
    var compactCards by remember {mutableStateOf(prefs.getBoolean("compact_cards",true))}
    var gridCards by remember {mutableStateOf(prefs.getBoolean("grid_cards",false))}
    // Columnas elegidas por el usuario (0 = automático). Se cambian pellizcando la cuadrícula o desde el menú ⋮.
    var userColumns by remember {mutableStateOf(prefs.getInt("columns_user",0))}
    var moreMenu by remember {mutableStateOf(false)}
    LaunchedEffect(models) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {models.forEach {modelSearchText(it)}}
    }
    var modality by rememberSaveable { mutableStateOf(runCatching { Modality.valueOf(prefs.getString("browser_modality", "VIDEO")!!) }.getOrDefault(Modality.VIDEO)) }
    var family by rememberSaveable { mutableStateOf<String?>(null) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable {mutableStateOf(false)}
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
    val placementRev=CatalogPlacements.revision.intValue
    val visible = remember(placementRev,models, modality, query, use, favorites, favoritesOnly,providerFilter,imageReferences,toolsView,rules,showLegacy) {
        val months=runtime.int("catalog.ageMonths")
        val available=models.filter {model->showLegacy || months==0 || modelRelease(model)?.month?.let {
            java.time.YearMonth.parse(it)>=java.time.YearMonth.now().minusMonths(months.toLong())
        }!=false}
        if(toolsView) available.filter {toolCategory(it)!=null && (query.isBlank() || modelSearchText(it).contains(query.lowercase())) && (!favoritesOnly || modelBookmark(it) in favorites)} else
        browseCatalog(available.filter {
            // Upscalers, lip-sync, extend, motion, inpaint… live only in Herramientas, never repeated under Vídeos/Imágenes.
            (modality !in setOf(Modality.VIDEO,Modality.IMAGE) || toolCategory(it)==null) &&
            (providerFilter==null || it.providerId==providerFilter) && (modality != Modality.IMAGE || !imageReferences || ImageModelChoices.acceptsReferences(it))}, modality, use, query, null, favoritesOnly, favorites, CatalogOrder.PRIORITY)
    }
    val orderScope = "order:${if(toolsView) "TOOLS" else modality.name}:${family ?: if(query.isBlank() && !favoritesOnly) "families" else "search-models"}"
    var savedOrder by remember(orderScope) { mutableStateOf(prefs.getString(orderScope, "").orEmpty().split("\n").filter {it.isNotBlank()}) }
    var contextTarget by remember { mutableStateOf<Triple<String,String,NexusModel?>?>(null) }
    val sections = remember(placementRev,visible, favorites,savedOrder,modality,toolsView,catalogSort,jobs,onlyDated) { sortSections((if(toolsView) visible.groupBy {toolCategory(it)!!}.map {(name,rows)->CatalogSection(name,rows)} else catalogSections(visible, favorites)).sortedWith(
        compareBy<CatalogSection> {savedOrder.indexOf(it.family).let {index -> if(index<0) Int.MAX_VALUE else index}}
            .thenByDescending { section -> section.models.any { modelBookmark(it) in favorites } }
            .thenBy { section -> if(modality == Modality.IMAGE) ImageModelChoices.priority(section.family) else 0 }
            .thenByDescending { section -> section.models.any { CatalogFocus.forModel(it)!=null } }
    ),catalogSort,jobs,onlyDated) }
    val groups = remember(placementRev,visible, family, favorites,savedOrder,catalogSort,jobs,onlyDated,toolsView,rules) { filterDatedGroups(sortGroups(catalogModelGroups(visible.filter { family == null || (if(toolsView) toolCategory(it) else catalogFamily(it)) == family }, favorites).sortedBy {savedOrder.indexOf(it.key).let {index -> if(index<0) Int.MAX_VALUE else index}},catalogSort,jobs).let {list->if(catalogSort!=CatalogSort.PERSONAL) list else list.sortedWith(compareByDescending<CatalogModelGroup> {it.routes.any {m->m.isFlagship}}.thenByDescending {it.routes.maxOf {m->m.versionNumeric}}.thenBy {group->val order=runtime.value(if(modality==Modality.VIDEO) "catalog.videoOrder" else "catalog.imageOrder").split(',');group.routes.minOf {m->order.indexOf(configuredWorkflowKey(m)).takeIf {it>=0} ?: Int.MAX_VALUE}})},catalogSort==CatalogSort.RELEASE && onlyDated) }
    val rootLevel = catalogAtRoot(family, query, favoritesOnly)
    val displayedKeys = if(rootLevel) sections.map {it.family} else groups.map {it.key}
    fun saveOrder(next:List<String>) { savedOrder=next; prefs.edit().putString(orderScope,next.joinToString("\n")).apply() }
    val listState=rememberLazyGridState()
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val autoColumns = catalogColumnCount(runtime.int("catalog.columns"), screenWidth, fontScale)
    val rootColumns = if(userColumns>0) userColumns else autoColumns
    // Two columns on any screen; in the grid the logo sits above the text so names keep their width. Very large fonts stay in a list.
    val useGrid=userColumns>1 || (gridCards && groupKey==null && androidx.compose.ui.platform.LocalDensity.current.fontScale<1.5f)
    LaunchedEffect(modality,family,groupKey,query,use,favoritesOnly,catalogSort,onlyDated) {listState.scrollToItem(0)}
    val dragView=androidx.compose.ui.platform.LocalView.current
    val labels=rememberCatalogLabels()
    var moveTarget by remember {mutableStateOf<Pair<String,String>?>(null)}
    var labelTarget by remember {mutableStateOf<Triple<String,String,String>?>(null)}
    // A long press that lifts a card but never moves it means «personalizar», not «ordenar».
    val dragMoved=remember {booleanArrayOf(false)}
    val reorderState=rememberReorderableLazyGridState(listState) {from,to ->
        val fromKey=from.key as? String
        val toKey=to.key as? String
        if(fromKey!=null && toKey!=null) {
            dragMoved[0]=true
            saveOrder(moveCatalogItem(savedOrder,displayedKeys,fromKey,toKey))
            // One soft tick each time the lifted card settles into a new slot.
            DragHaptics.slot(dragView)
        }
    }
    val activeGroup = groups.firstOrNull { it.key == groupKey }
    // Tools ignore provider/use/reference filters, so they never count (or show) there.
    val activeFilterItems = buildList {
        if(!toolsView) {
            providerFilter?.let {id -> add(ActiveFilter("Proveedor: ${providerName(id)}") {providerFilter=null}) }
            use?.let {value -> add(ActiveFilter("Uso: ${value.label}") {use=null}) }
            if(modality==Modality.IMAGE && imageReferences) add(ActiveFilter("Con referencias") {imageReferences=false;use=null;groupKey=null})
        }
    }
    val activeFilters = activeFilterItems.map {it.label}
    val duplicatedHighlights = remember(groups) {
        groups.mapNotNull {modelGroupHighlight(it.routes)}.groupingBy {it}.eachCount().filterValues {it>1}.keys
    }
    val canReorder = catalogSort==CatalogSort.PERSONAL
    fun moveBy(key:String, offset:Int) { saveOrder(moveCatalogItemBy(savedOrder,displayedKeys,key,offset)) }
    fun reorderActions(key:String): Pair<(()->Unit)?,(()->Unit)?> {
        val index=displayedKeys.indexOf(key)
        if(!canReorder || index<0) return null to null
        return (if(index>0) ({moveBy(key,-1)}) else null) to (if(index<displayedKeys.lastIndex) ({moveBy(key,1)}) else null)
    }
    fun back() { if(groupKey != null) groupKey = null else family = null }
    BackHandler(family != null || groupKey != null) { back() }
    // Inside a family or a model sheet the sideways swipe from the edge means «Atrás»; it must not slide to the previous tab.
    val nested = family != null || groupKey != null
    LaunchedEffect(nested) { com.vidadomu.nexus.ui.theme.ChromeVisibility.nestedLevel.value = nested }
    DisposableEffect(Unit) { onDispose { com.vidadomu.nexus.ui.theme.ChromeVisibility.nestedLevel.value = false } }
    fun toggleFavorite(model: NexusModel) {
        val key = modelBookmark(model)
        favorites = if(key in favorites) favorites - key else favorites + key
        prefs.edit().putStringSet("model_favorites", favorites).apply()
    }
    val catalogMenu: @Composable () -> Unit = {
                Box {
                    IconButton(onClick={moreMenu=true}) {Icon(Icons.Default.MoreVert,"Opciones del catálogo")}
                    DropdownMenu(expanded=moreMenu,onDismissRequest={moreMenu=false}) {
                        DropdownMenuItem(text={Text("Editar región · local")},onClick={moreMenu=false;onLocalInpaint()})
                        if(personalizing) DropdownMenuItem(text={Text("Terminar personalización")},onClick={personalizing=false;moreMenu=false})
                        DropdownMenuItem(text={Text("Personalizar y ordenar")},onClick={catalogSort=CatalogSort.PERSONAL;prefs.edit().putString("sort",catalogSort.name).apply();personalizing=true;moreMenu=false})
                        DropdownMenuItem(text={Text("Modelos ocultos (${hiddenEntries.size})")},onClick={manageHidden=true;moreMenu=false})
                        if(groupKey==null) DropdownMenuItem(text={Text(if(filters) "Ocultar filtros" else "Filtros y orden")},onClick={filters=!filters;moreMenu=false})
                        DropdownMenuItem(text={Text("Más columnas · ahora "+(if(rootLevel) rootColumns else if(userColumns>0) userColumns else if(useGrid) 2 else 1))},leadingIcon={Icon(Icons.Default.GridView,null)},
                            onClick={val c=((if(rootLevel) rootColumns else if(userColumns>0) userColumns else if(useGrid) 2 else 1)+1).coerceAtMost(6);userColumns=c;prefs.edit().putInt("columns_user",c).apply()})
                        DropdownMenuItem(text={Text("Menos columnas")},leadingIcon={Icon(Icons.Default.ViewList,null)},
                            onClick={val c=((if(rootLevel) rootColumns else if(userColumns>0) userColumns else if(useGrid) 2 else 1)-1).coerceAtLeast(1);userColumns=c;prefs.edit().putInt("columns_user",c).apply()})
                        if(userColumns>0) DropdownMenuItem(text={Text("Columnas automáticas")},onClick={userColumns=0;prefs.edit().putInt("columns_user",0).apply();moreMenu=false})
                        DropdownMenuItem(text={Text(if(compactCards) "Usar tarjetas amplias" else "Usar tarjetas compactas")},onClick={compactCards=!compactCards;prefs.edit().putBoolean("compact_cards",compactCards).apply();moreMenu=false})
                        if(groupKey==null && family==null && !toolsView) DropdownMenuItem(text={Text(if(showSecondary) "Ocultar audio y texto" else "Mostrar audio y texto")},
                            onClick={showSecondary=!showSecondary;moreMenu=false})
                        DropdownMenuItem(text={Text("Actualizar catálogo")},enabled=!loading,onClick={app.modelCatalogRepository.refreshModels(true);moreMenu=false})
                    }
                }
    }
    // The root header already pads for the status bar itself; the scaffold must not add the inset a second time.
    Scaffold(containerColor = ScreenBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        if(family!=null || groupKey!=null) TopAppBar(title = { Text("ArtBox",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle(),fontWeight=FontWeight.SemiBold) },
            navigationIcon = { if(family != null || groupKey != null) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = {
                catalogMenu()
            })
    }) { padding ->
        if(activeGroup!=null) {
            ModelOptionsScreen(activeGroup,favorites,::toggleFavorite,onSelectModelAndCreate,
                if(personalizing) ({model -> pendingHide=routeVisibilityKey(model) to (model.displayName+" · "+providerName(model.providerId))}) else null,
                modifier=Modifier.fillMaxSize().padding(padding),onUseWithMedia=onSelectModelWithMedia)
        } else {
        key(modality, family, groupKey) {
        LazyVerticalGrid(columns=GridCells.Fixed(if(rootLevel) rootColumns else if(userColumns>0) userColumns else if(useGrid) 2 else 1),modifier=Modifier.fillMaxSize().padding(padding).pinchColumns(columns=if(rootLevel) rootColumns else if(userColumns>0) userColumns else if(useGrid) 2 else 1,max=6) {c->userColumns=c;prefs.edit().putInt("columns_user",c).apply()}, state=listState, contentPadding = PaddingValues(horizontal=NexusTokens.space12, vertical=NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8), horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            if(family==null) item(span={GridItemSpan(maxLineSpan)}) {
                Row(Modifier.fillMaxWidth().statusBarsPadding(),verticalAlignment=Alignment.CenterVertically) {
                    Icon(androidx.compose.ui.res.painterResource(com.vidadomu.nexus.R.drawable.artbox_mark),null,tint=androidx.compose.ui.graphics.Color.Unspecified,modifier=Modifier.size(28.dp))
                    Text("ArtBox",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(start=8.dp).weight(1f))
                    catalogMenu()
                    NexusSettingsButton()
                }
            }
            if(family!=null || favoritesOnly) item(span={GridItemSpan(maxLineSpan)}) {
                // Favoritos filters the tab underneath, so the heading names both instead of contradicting the active tab.
                val sectionName = if(toolsView) "Herramientas" else when(modality) {
                    Modality.VIDEO -> "Vídeos"; Modality.IMAGE -> "Imágenes"; Modality.AUDIO -> "Audio"; else -> "Texto" }
                Text(if(family != null) family.orEmpty()
                    else "Favoritos · $sectionName", color = TextSecondary, fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    modifier = Modifier.semantics { heading() })
            }
            // A quiet entry, not a full-width «máscaras» button that breaks the hierarchy; the main way in is
            // Biblioteca/visor → «Editar foto».
            if(toolsView && family==null) item(span={GridItemSpan(maxLineSpan)}) {
                AssistChip(onClick=onLocalInpaint,label={Text("Editar una foto en el móvil")},
                    leadingIcon={Icon(Icons.Default.Brush,null,Modifier.size(18.dp))})
            }
            if(personalizing) item(span={GridItemSpan(maxLineSpan)}) {
                Text("Arrastra el asa, mantén pulsada una tarjeta o toca el asa para subirla o bajarla. Tu orden se guarda.",color=TextSecondary,fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            }
                if(family == null) item(span={GridItemSpan(maxLineSpan)}) {
                    fun pick(value:Modality) {toolsView=false;favoritesOnly=false;modality=value; family=null; groupKey=null; use=null; query=""; prefs.edit().putString("browser_modality",value.name).apply()}
                    ArtBoxTabs(buildList {
                        add(ArtBoxTab("Vídeos",!toolsView && !favoritesOnly && modality==Modality.VIDEO,null) {pick(Modality.VIDEO)})
                        add(ArtBoxTab("Imágenes",!toolsView && !favoritesOnly && modality==Modality.IMAGE,null) {pick(Modality.IMAGE)})
                        if(showSecondary || modality in listOf(Modality.AUDIO,Modality.CHAT)) {
                            add(ArtBoxTab("Audio",!toolsView && !favoritesOnly && modality==Modality.AUDIO,null) {pick(Modality.AUDIO)})
                            add(ArtBoxTab("Texto",!toolsView && !favoritesOnly && modality==Modality.CHAT,null) {pick(Modality.CHAT)})
                        }
                        add(ArtBoxTab("Herramientas",toolsView,null) {toolsView=true;favoritesOnly=false;family=null;groupKey=null;use=null;query=""})
                        add(ArtBoxTab("Favoritos",favoritesOnly,Icons.Default.StarBorder) {favoritesOnly=!favoritesOnly;family=null})
                    })
                }
                val inbox=ArtBoxInbox.images.value
                if(inbox.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) {ArtBoxInboxNotice(inbox.size) {ArtBoxInbox.images.value=emptyList()}}
                item(span={GridItemSpan(maxLineSpan)}) {
                    ArtBoxSearchBar(query,{query=it},filters || activeFilters.isNotEmpty(),filterButtonDescription(activeFilters.size),{filters=!filters})
                }
                if(activeFilterItems.isNotEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                    ActiveFilterChips(activeFilterItems,{use=null;providerFilter=null;imageReferences=false;groupKey=null},
                        resultLabel=if(rootLevel) "${visible.size} ${if(visible.size==1) "modelo" else "modelos"}" else null)
                }
                if(loading) item(span={GridItemSpan(maxLineSpan)}) {LinearProgressIndicator(Modifier.fillMaxWidth())}
                // Say which provider did not refresh and why; «verificando contratos n/m» is progress, not a failure.
                // «n contratos no verificados» is routine (they are checked when the model is opened): not a warning.
                val catalogProblem = error?.split(" · ")
                    ?.filterNot { it.contains("verificando contratos") || it.contains("contratos no verificados") }
                    ?.joinToString(" · ")?.takeIf { it.isNotBlank() }
                if(catalogProblem!=null) item(span={GridItemSpan(maxLineSpan)}) {
                    InlineNotice(catalogProblem.split(" · ").joinToString("\n"),isError=false,
                        actionLabel=if(loading) null else "Reintentar",onAction={app.modelCatalogRepository.refreshModels(true)})
                } else if(loading && error!=null) item(span={GridItemSpan(maxLineSpan)}) {
                    Text(error.orEmpty(),style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                }
                val priceRoutes=if(toolsView && family==null) emptyList() else if(rootLevel) visible else groups.flatMap {it.routes}
                val priceNote=catalogPriceNote(priceRoutes,anyPrice=priceRoutes.any {(referencePrice(it) ?: 0.0)>0.0})
                // La nota «Precios desde · por 5 s» se retiró: el precio se ve al abrir cada modelo.
                if(rootLevel) {
                    val prioritySections = sections.filter { it.family in ImageModelChoices.families || it.models.any { m -> modelBookmark(m) in favorites } || it.family in savedOrder }
                    val shownSections = sections
                    items(shownSections,key={it.family}) { section ->
                        val children=catalogModelGroups(section.models,favorites)
                        ReorderableItem(reorderState,key=section.family) { isDragging ->
                        val (moveUp,moveDown)=reorderActions(section.family)
                        ArtBoxFamilyCard(section,onOpen={family=section.family;filters=false},onOptions={contextTarget=Triple(if(toolsView) toolVisibilityKey(section.family) else familyVisibilityKey(section.family),section.family,null)},toolCategory=toolsView,
                            dragModifier=if(canReorder) Modifier.longPressDraggableHandle(
                                onDragStarted={dragMoved[0]=false;DragHaptics.lift(dragView)},
                                onDragStopped={DragHaptics.drop(dragView)
                                    if(!dragMoved[0]) labelTarget=Triple("family:"+section.family,section.family,"")}) else null,
                            dragging=isDragging,onMoveUp=moveUp,onMoveDown=moveDown,title=labels.name("family:"+section.family,section.family),columns=rootColumns)
                        }
                    }
                    if(false) item(span={GridItemSpan(maxLineSpan)}) {
                        TextButton(onClick={allImageFamilies=!allImageFamilies}, modifier=Modifier.fillMaxWidth()) {Text(if(allImageFamilies) "Ver solo mis familias" else "Otras familias")}
                    }
                } else {
                    items(groups,key={it.key}) { group ->
                        ReorderableItem(reorderState,key=group.key) { isDragging ->
                        val focusAdvice=group.routes.firstNotNullOfOrNull(CatalogFocus::forModel)
                        val (moveUp,moveDown)=reorderActions(group.key)
                        NavigationCard(labels.name("group:"+group.key,group.title),catalogFamily(group.routes.first()),
                            labels.description("group:"+group.key,focusAdvice?.let {fitCardSummary("Bueno para: ${it.good}")} ?: catalogCardSummary(group.routes)),group.routes,modality,
                            onClick={groupKey=group.key;filters=false},onQuickUse={
                                val saved=app.getSharedPreferences("catalog_choices_v1",0)
                                val order=rules.value(if(modality==Modality.VIDEO) "catalog.videoOrder" else "catalog.imageOrder").split(',')
                                val task=restoredWorkflow(group,saved.getString("task:${group.key}",null),saved.getString("use:${group.key}",null),order)
                                val routes=workflowRoutes(group,order).getValue(task)
                                val chosen=routes.firstOrNull {modelBookmark(it)==saved.getString("task-route:${group.key}:${task.name}",null)} ?: routes.first()
                                onSelectModelAndCreate(chosen)
                            },releaseLabel=groupReleaseLabel(group.routes).takeUnless {it=="Fecha no verificada"},compact=compactCards,grid=useGrid,
                            onLongClick=if(canReorder) null else ({contextTarget=Triple(groupVisibilityKey(group.routes.first()),group.title,group.routes.first())}),
                            onOptions={contextTarget=Triple(groupVisibilityKey(group.routes.first()),group.title,group.routes.first())},
                            dragModifier=if(personalizing) Modifier.draggableHandle() else null,
                            dragging=isDragging,glyphKey="group:"+group.key,
                            cardModifier=if(canReorder) Modifier.longPressDraggableHandle(
                                onDragStarted={dragMoved[0]=false;DragHaptics.lift(dragView)},
                                onDragStopped={DragHaptics.drop(dragView)
                                    if(!dragMoved[0]) labelTarget=Triple("group:"+group.key,group.title,
                                        focusAdvice?.let {fitCardSummary("Bueno para: ${it.good}")} ?: catalogCardSummary(group.routes))}) else Modifier,
                            onHide=if(personalizing) ({pendingHide=groupVisibilityKey(group.routes.first()) to group.title}) else null,
                            highlight=(focusAdvice?.let {"Evítalo si: ${it.avoid}"} ?: modelGroupHighlight(group.routes))?.takeUnless {it in duplicatedHighlights},
                            onMoveUp=moveUp,onMoveDown=moveDown)
                        }
                    }
                }
                if(visible.isEmpty() || if(rootLevel) sections.isEmpty() else groups.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                    CatalogEmptyState(loading=loading && models.isEmpty(),favoritesOnly=favoritesOnly,
                        onShowAll={favoritesOnly=false},
                        onClear={query="";use=null;favoritesOnly=false;providerFilter=null;onlyDated=false;prefs.edit().putBoolean("release_only_dated",false).apply()})
                }
            item(span={GridItemSpan(maxLineSpan)}) {Text("Precios base orientativos. El total se calcula al configurar.",color=TextSecondary,fontSize = MaterialTheme.typography.labelMedium.fontSize)}
            item(span={GridItemSpan(maxLineSpan)}) {Spacer(Modifier.height(NexusTokens.space24))}
        }
        }
        }
    }
    if(filters && activeGroup==null) ModalBottomSheet(onDismissRequest={filters=false},shape=NexusTokens.sheet,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Filtros",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f).semantics { heading() })
                IconButton(onClick={filters=false}) {Icon(Icons.Default.Close,"Cerrar filtros")}
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        item {StudioFilterChip(catalogSort==CatalogSort.RELEASE,{
                            catalogSort=if(catalogSort==CatalogSort.RELEASE) CatalogSort.PERSONAL else CatalogSort.RELEASE
                            personalizing=false
                            prefs.edit().putString("sort",catalogSort.name).apply()
                        },label={Text("Más nuevos")},leadingIcon={Icon(Icons.Default.NewReleases,null,Modifier.size(16.dp))})}
                        if(catalogSort==CatalogSort.RELEASE) item {StudioFilterChip(onlyDated,{
                            onlyDated=!onlyDated;prefs.edit().putBoolean("release_only_dated",onlyDated).apply()
                        },label={Text("Solo con fecha")})}
            }
            if(catalogSort==CatalogSort.RELEASE) {
                    Text("Lanzamiento: del más reciente al más antiguo. " + if(onlyDated) "Solo fechas verificadas." else "Los modelos sin fecha aparecen al final.",fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)
            }
                    Row(verticalAlignment=Alignment.CenterVertically) {Text("Mostrar modelos antiguos",Modifier.weight(1f));Switch(showLegacy,{showLegacy=it})}
                    if(!toolsView && modality==Modality.IMAGE) Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Solo modelos con referencias",Modifier.weight(1f))
                        Switch(imageReferences,{imageReferences=it;use=null;groupKey=null})
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        Text("Orden",style=MaterialTheme.typography.labelMedium,color=TextSecondary,modifier=Modifier.semantics { heading() })
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                            CatalogSort.entries.forEach {entry -> StudioFilterChip(catalogSort==entry,{
                                catalogSort=entry;prefs.edit().putString("sort",entry.name).apply()
                            },label={Text(entry.label)},shape=NexusTokens.filter)}
                        }
                        Text("Proveedor",style=MaterialTheme.typography.labelMedium,color=TextSecondary,modifier=Modifier.semantics { heading() })
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                            StudioFilterChip(providerFilter==null,{providerFilter=null},label={Text("Todos")},shape=NexusTokens.filter)
                            models.map {it.providerId}.distinct().forEach {id -> StudioFilterChip(providerFilter==id,{providerFilter=id},label={Text(providerName(id))},shape=NexusTokens.filter)}
                        }
                        StudioFilterChip(favoritesOnly,{favoritesOnly=!favoritesOnly},label={Text("Mis favoritos")})
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
                            verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            StudioFilterChip(use==null,{use=null},label={Text("Todos los usos")})
                            CreationUse.entries.filter {it.modality==modality}.forEach { value -> StudioFilterChip(use==value,{use=if(use==value)null else value},label={Text(value.label)}) }
                        }
                        TextButton(onClick={app.modelCatalogRepository.refreshModels(true)},enabled=!loading) {Text("Actualizar catálogo y tarifas")}
                    }
        }
    }
    contextTarget?.let { (key,label,model) ->
        val itemKey=if(model==null) label else groups.firstOrNull {groupVisibilityKey(it.routes.first())==key}?.key
        val labelKey=if(model==null) "family:$label" else "group:$itemKey"
        val targetGroup=if(model==null) null else groups.firstOrNull {it.key==itemKey}
        val originalDescription=targetGroup?.let {g -> CatalogFocus.forModel(g.routes.first())?.let {fitCardSummary("Bueno para: ${it.good}")} ?: catalogCardSummary(g.routes)}.orEmpty()
        AlertDialog(onDismissRequest={contextTarget=null},title={Text(labels.name(labelKey,label))},text={Column {
            TextButton(onClick={labelTarget=Triple(labelKey,label,originalDescription);contextTarget=null}) {Text("Personalizar icono, nombre y tamaño")}
            Text("Para mover una tarjeta, mantenla pulsada y arrástrala.",style=MaterialTheme.typography.bodySmall,color=TextSecondary,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
            // Order changes by long-press + drag; up/down stay only as TalkBack actions on each card.
            TextButton(onClick={
                if(itemKey!=null) saveOrder(listOf(itemKey)+(savedOrder+displayedKeys).distinct().filterNot {it==itemKey})
                contextTarget=null
            }) {Text("Poner al principio")}
            if(model!=null) TextButton(onClick={toggleFavorite(model);contextTarget=null}) {Text(if(modelBookmark(model) in favorites) "Quitar favorito" else "Marcar favorito")}
            if(!key.startsWith("tool:")) TextButton(onClick={moveTarget=key to labels.name(labelKey,label);contextTarget=null}) {Text("Mover a otra sección…")}
            TextButton(onClick={pendingHide=key to label;contextTarget=null}) {Text("Quitar de mi catálogo")}
            TextButton(onClick={saveOrder(emptyList());contextTarget=null}) {Text("Restaurar orden predeterminado")}
        }},confirmButton={TextButton(onClick={contextTarget=null}) {Text("Cerrar")}})
    }
    moveTarget?.let {(key,name) ->
        val current=CatalogPlacements.target(key)
        AlertDialog(onDismissRequest={moveTarget=null},title={Text("Mover «$name»")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            Text("Elige dónde quieres verlo. Solo cambia dónde aparece en ArtBox.",style=MaterialTheme.typography.bodySmall,color=TextSecondary,modifier=Modifier.padding(bottom=8.dp))
            TextButton(onClick={CatalogPlacements.set(key,"none");moveTarget=null}) {Text(if(current=="none") "✓ Vídeos / Imágenes (fuera de Herramientas)" else "Vídeos / Imágenes (fuera de Herramientas)")}
            CatalogPlacements.toolCategories.forEach {category ->
                val chosen=current=="tool:$category"
                TextButton(onClick={CatalogPlacements.set(key,"tool:$category");moveTarget=null}) {Text((if(chosen) "✓ " else "")+"Herramientas · $category")}
            }
            if(current!=null) TextButton(onClick={CatalogPlacements.set(key,null);moveTarget=null}) {Text("Volver a su sección original")}
        }},confirmButton={TextButton(onClick={moveTarget=null}) {Text("Cerrar")}})
    }
    labelTarget?.let {(key,name,description) ->
        CatalogLabelEditor(key,name,description,labels,onDismiss={labelTarget=null},withDescription=key.startsWith("group:"))
    }
    pendingHide?.let { (key,label) ->
        AlertDialog(onDismissRequest={pendingHide=null},title={Text("Quitar $label")},
            text={Text("Dejará de aparecer en tu catálogo y al elegir modelos para crear. Tu historial y archivos se conservan. Puedes recuperarlo en Modelos ocultos.")},
            confirmButton={TextButton(onClick={app.modelCatalogRepository.setCatalogHidden(key,label,true);pendingHide=null;groupKey=null}) {Text("Quitar")}},
            dismissButton={TextButton(onClick={pendingHide=null}) {Text("Cancelar")}})
    }
    if(manageHidden) AlertDialog(onDismissRequest={manageHidden=false},title={Text("Modelos ocultos")},
        text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            if(hiddenEntries.isEmpty()) item {Text("No has ocultado ningún modelo.")}
            items(hiddenEntries.toList(),key={it.first}) { (key,label) ->
                Column {
                    Text(label,fontWeight=FontWeight.SemiBold)
                    Text(when {key.startsWith("tool:")->"Categoría de herramientas";key.startsWith("family:")->"Familia completa";key.startsWith("group:")->"Modelo y todas sus variantes";else->"Variante concreta"},fontSize = MaterialTheme.typography.labelMedium.fontSize,color=TextSecondary)
                    TextButton(onClick={app.modelCatalogRepository.setCatalogHidden(key,label,false)}) {Text("Recuperar")}
                }
            }
        }},confirmButton={TextButton(onClick={manageHidden=false}) {Text("Cerrar")}})

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationCard(title:String,family:String,subtitle:String,routes:List<NexusModel>,modality:Modality,onClick:()->Unit,highlight:String?=null,onHide:(()->Unit)?=null,onLongClick:(()->Unit)?=null,onOptions:(()->Unit)?=null,glyphKey:String="",dragging:Boolean=false,dragModifier:Modifier?=null,cardModifier:Modifier=Modifier,compact:Boolean=false,grid:Boolean=false,releaseLabel:String?=null,
    onMoveUp:(()->Unit)?=null,onMoveDown:(()->Unit)?=null,onQuickUse:(()->Unit)?=null) {
    val app=LocalContext.current.applicationContext as NexusApplication
    val currency by app.currencyManager.state.collectAsState()
    // Only real, positive published prices: never «Desde 0,00» or an empty «Desde».
    val price=routes.mapNotNull(::referencePrice).filter {it.isFinite() && it>0}.minOrNull()
    val displayPrice=remember(price,currency) {price?.let(app.currencyManager::formatUnitPrice)}
    // Con fuente grande no se recorta texto esencial; con 100 % el resumen está escrito para dos líneas.
    val largeFont=androidx.compose.ui.platform.LocalDensity.current.fontScale>=1.3f
    val stackedPrice = grid || largeFont || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
    val reorderActions=listOfNotNull(
        onMoveUp?.let {action -> CustomAccessibilityAction("Subir $title") {action();true}},
        onMoveDown?.let {action -> CustomAccessibilityAction("Bajar $title") {action();true}})
    // Altura mínima fija en grid para que las tarjetas no bailen cuando falta releaseLabel/highlight.
    val lift by androidx.compose.animation.core.animateFloatAsState(if(dragging) 1.03f else 1f,androidx.compose.animation.core.spring(dampingRatio=.62f,stiffness=420f),label="Row lift")
    Surface(modifier=cardModifier.fillMaxWidth().graphicsLayer {scaleX=lift;scaleY=lift}.then(if(grid) Modifier.heightIn(min=116.dp) else Modifier).combinedClickable(onClick=onClick,
        onLongClickLabel=if(onLongClick!=null) "Opciones de $title" else null,onLongClick=onLongClick)
        .then(if(reorderActions.isEmpty()) Modifier else Modifier.semantics { customActions=reorderActions }),shape=NexusTokens.card,
        color=if(dragging) MaterialTheme.colorScheme.surfaceContainerHigh else SurfaceDark,shadowElevation=if(dragging) 18.dp else 0.dp,
        border=if(dragging) BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(horizontal=NexusTokens.space12,vertical=NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            val glyph:@Composable ()->Unit = {CatalogGlyph(glyphKey,if(grid) 36.dp else 44.dp,rememberCatalogLabels()) {size -> BrandLogo(family=family,modelId=routes.firstOrNull()?.id,size=size,bare=true)}}
            val texts:@Composable ColumnScope.()->Unit = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Text(title,style=MaterialTheme.typography.titleMedium,
                        fontSize=if(maxWidth<130.dp) 14.sp else 17.sp,color=TextPrimary,minLines=if(largeFont) 1 else 3,maxLines=Int.MAX_VALUE)
                }
                Text(subtitle,style=MaterialTheme.typography.labelMedium,color=TextSecondary,
                    minLines=when {largeFont -> 1; grid || !compact -> 3; else -> 2},maxLines=Int.MAX_VALUE)
                Text(releaseLabel.orEmpty(),style=MaterialTheme.typography.labelMedium,color=TextSecondary,minLines=1,maxLines=1)
            }
            if(grid) {glyph();Column(Modifier.fillMaxWidth(),content=texts)}
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                glyph()
                Column(Modifier.weight(1f),content=texts)
                if(!grid) Text(displayPrice ?: "Sin tarifa",
                    Modifier.widthIn(max=92.dp),style=MaterialTheme.typography.bodyMedium.tabularMoney(),color=TextSecondary)
                // Mantener pulsado levanta y arrastra la tarjeta; las opciones (renombrar, ocultar…) viven en este ⋮.
                if(!grid && dragModifier==null && onOptions!=null) IconButton(onClick=onOptions,modifier=Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert,"Opciones de $title",Modifier.size(18.dp),tint=TextSecondary)
                }
                if(dragModifier!=null) IconButton(onClick=onOptions ?: onLongClick ?: {},modifier=dragModifier) {Icon(Icons.Default.DragHandle,"Ordenar $title",tint=TextSecondary)}
            }
            if(stackedPrice) Row(Modifier.heightIn(min=NexusTokens.minimumTouch),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                // Sin precio publicado no se repite una píldora vacía: solo se muestra cuando aporta dato real.
                if(displayPrice!=null) Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=NexusTokens.filter,modifier=Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
                        Text("Desde",style=MaterialTheme.typography.labelSmall,color=TextSecondary)
                        Text(displayPrice,style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
                    }
                } else Spacer(Modifier.weight(1f))
                if(dragModifier==null) IconButton(onClick=onOptions ?: onLongClick ?: {},modifier=Modifier.sizeIn(minWidth=NexusTokens.minimumTouch,minHeight=NexusTokens.minimumTouch)) {
                    Icon(Icons.Default.MoreVert,"Opciones de $title",tint=TextSecondary)
                } else IconButton(onClick=onOptions ?: onLongClick ?: {},modifier=dragModifier) {Icon(Icons.Default.DragHandle,"Ordenar $title",tint=TextSecondary)}
            }
            Text(highlight.orEmpty(),style=MaterialTheme.typography.labelMedium,color=TextSecondary,minLines=if(largeFont) 1 else 3)
            Button(onClick=onQuickUse ?: onClick,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=NexusTokens.field) {
                Text("Crear",style=MaterialTheme.typography.labelLarge)
            }
            onHide?.let {action -> TextButton(onClick=action) {Icon(Icons.Default.VisibilityOff,null);Text("Quitar de mi catálogo",color=TextSecondary)}}
        }
    }
}

/** Empty catalog: a centred illustration, one clear message and the action that actually gets out of it. */
@Composable
private fun CatalogEmptyState(loading: Boolean, favoritesOnly: Boolean, onShowAll: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = NexusTokens.space32, horizontal = NexusTokens.space16),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        if (loading) { CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp); Text("Preparando tu catálogo…", color = TextSecondary); return@Column }
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(if (favoritesOnly) Icons.Default.StarBorder else Icons.Default.SearchOff, null,
                Modifier.padding(18.dp).size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(if (favoritesOnly) "Aún no tienes favoritos aquí" else "No hay resultados con estos filtros",
            style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(if (favoritesOnly) "Abre un modelo y toca la estrella para tenerlo siempre a mano."
            else "Prueba con otra búsqueda o quita los filtros activos.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (favoritesOnly) Button(onClick = onShowAll) { Text("Ver todos los modelos") }
        else FilledTonalButton(onClick = onClear) { Text("Limpiar filtros") }
    }
}
