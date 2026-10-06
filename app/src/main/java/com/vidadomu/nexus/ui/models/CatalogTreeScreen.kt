package com.vidadomu.nexus.ui.models

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onSelectModelAndCreate:(NexusModel)->Unit) {
    val app=LocalContext.current.applicationContext as NexusApplication
    val models by app.modelCatalogRepository.models.collectAsState()
    val loading by app.modelCatalogRepository.isLoading.collectAsState()
    val error by app.modelCatalogRepository.errorMessage.collectAsState()
    val currencyState by app.currencyManager.state.collectAsState()
    val prefs=remember { app.getSharedPreferences("catalog_grid_v2",0) }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns",2).coerceIn(1,3)) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites",emptySet())!!.toSet()) }
    var use by rememberSaveable { mutableStateOf(CreationUse.entries.firstOrNull { it.name==prefs.getString("use",null) } ?: CreationUse.IMAGE_REFS) }
    var provider by rememberSaveable { mutableStateOf<String?>(null) }
    var older by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var offers by rememberSaveable { mutableStateOf(false) }
    var starred by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<NexusModel?>(null) }
    val grid=rememberLazyGridState()
    LaunchedEffect(Unit) { app.modelCatalogRepository.refreshModels(forceNetwork=true) }
    LaunchedEffect(provider,older,use,query) { grid.scrollToItem(0) }
    fun back() { if(older) older=false else provider=null }
    BackHandler(provider!=null) { back() }
    fun setUse(value:CreationUse) { use=value; older=false; prefs.edit().putString("use",value.name).apply() }
    val filtered=remember(models,use,query,offers,starred,favorites) {
        models.distinctBy { it.providerId+":"+it.id }.filter {
            modelUse(it)==use && (query.isBlank() || (it.displayName+" "+it.id+" "+creator(it)).contains(query,true)) &&
                (!offers || savingPercent(it)!=null) && (!starred || creator(it) in favorites)
        }
    }
    val branch=recentModels(filtered.filter { provider==null || creator(it)==provider })
    val shortlist=if(older) branch.drop(4) else branch.take(4)
    val visible=shortlist
    fun price(m:NexusModel):String {
        currencyState.preference
        return "Coste final: Atlas lo confirma tras generar"
    }
    Scaffold(containerColor=BackgroundDark,topBar={
        TopAppBar(windowInsets=WindowInsets(0,0,0,0),title={Text(provider ?: "NEXUS",fontWeight=FontWeight.Bold)},
            navigationIcon={if(provider!=null) IconButton(onClick={back()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}},
            actions={
                IconButton(onClick={starred=!starred}){Icon(if(starred) Icons.Default.Star else Icons.Default.StarBorder,"Solo favoritos",tint=if(starred) AccentGold else TextSecondary)}
                IconButton(onClick={filters=!filters}){Icon(Icons.Default.Tune,"Tamaño y filtros",tint=PrimaryNeon)}
            },colors=TopAppBarDefaults.topAppBarColors(containerColor=BackgroundDark))
    }) { padding ->
        LazyVerticalGrid(columns=GridCells.Fixed(columns),state=grid,modifier=Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item(span={GridItemSpan(maxLineSpan)}) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query,{query=it;older=false},Modifier.fillMaxWidth(),singleLine=true,
                        placeholder={Text("Buscar",fontSize=14.sp)},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(16.dp))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(listOf(Modality.IMAGE to "Imagen",Modality.VIDEO to "Vídeo",Modality.AUDIO to "Audio",Modality.CHAT to "Texto / LLM")) { (modality,label) ->
                            FilterChip(use.modality==modality,{setUse(when(modality) {
                                Modality.IMAGE->CreationUse.IMAGE_REFS;Modality.VIDEO->CreationUse.VIDEO_IMAGE;Modality.AUDIO->CreationUse.AUDIO;Modality.CHAT->CreationUse.TEXT
                            })},label={Text(label)})
                        }
                    }
                    val uses=CreationUse.entries.filter { it.modality==use.modality }
                    if(uses.size>1) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(uses) { option -> FilterChip(use==option,{setUse(option)},label={Text(option.label,fontSize=12.sp)}) }
                    }
                    AnimatedVisibility(filters) {
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Tamaño de tarjetas",color=TextSecondary)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf(1 to "Grandes",2 to "Medianas",3 to "Pequeñas").forEach { (count,label) ->
                                    FilterChip(columns==count,{columns=count;prefs.edit().putInt("columns",count).apply()},label={Text(label,fontSize=11.sp)})
                                }
                            }
                            FilterChip(offers,{offers=!offers},label={Text("Solo ofertas")})
                            Text("El catálogo no siempre publica unidad ni extras. No se estima el coste antes de generar.",fontSize=11.sp,color=TextSecondary)
                            TextButton(onClick={app.modelCatalogRepository.refreshModels(true)},enabled=!loading){Text("Actualizar tarifas")}
                        }
                    }
                    if(provider!=null) Text(if(older) "Modelos anteriores" else minOf(4,branch.size).toString()+" modelos recientes · "+use.label,color=TextSecondary,fontSize=12.sp)
                    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=PrimaryNeon)
                    if(error!=null) Text("No se pudo actualizar; las tarifas guardadas pueden haber cambiado.",color=AccentAmber,fontSize=12.sp)
                }
            }
            if(provider==null) {
                val groups=filtered.groupBy(::creator).entries.sortedWith(compareByDescending<Map.Entry<String,List<NexusModel>>>{it.key in favorites}.thenByDescending { it.value.maxOf { model -> model.providerPriority } }.thenBy { it.key })
                items(groups,key={it.key}) { group ->
                    Surface(onClick={provider=group.key;older=false},modifier=Modifier.height(if(columns==3) 180.dp else 218.dp),shape=RoundedCornerShape(22.dp),color=SurfaceDark,border=BorderStroke(1.dp,BorderSubtle)) {
                        Column(Modifier.padding(if(columns==3) 10.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.fillMaxWidth()) {
                                BrandLogo(organization=group.key,modelId=group.value.first().id,size=if(columns==3) 62.dp else 82.dp)
                                IconButton(onClick={
                                    favorites=if(group.key in favorites) favorites-group.key else favorites+group.key
                                    prefs.edit().putStringSet("favorites",favorites).apply()
                                },modifier=Modifier.align(Alignment.TopEnd).size(32.dp)){
                                    Icon(if(group.key in favorites) Icons.Default.Star else Icons.Default.StarBorder,"Favorito",tint=if(group.key in favorites) AccentGold else TextSecondary,modifier=Modifier.size(18.dp))
                                }
                            }
                            Text(group.key,color=TextPrimary,fontSize=if(columns==3) 14.sp else 18.sp,fontWeight=FontWeight.Bold)
                            Text(group.value.size.toString()+if(group.value.size==1) " modelo" else " modelos",color=TextSecondary,fontSize=11.sp)
                        }
                    }
                }
            } else {
                items(visible,key={it.providerId+it.id}) { model ->
                    Surface(onClick={detail=model},shape=RoundedCornerShape(20.dp),color=SurfaceDark,border=BorderStroke(1.dp,BorderSubtle)) {
                        Column(Modifier.padding(if(columns==3) 10.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            BrandLogo(organization=creator(model),modelId=model.id,size=if(columns==3) 38.dp else 48.dp)
                            Text(model.displayName,color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=if(columns==3) 13.sp else 16.sp)
                            Text(use.label,color=TextSecondary,fontSize=11.sp)
                            Text(price(model),color=PrimaryNeon,fontSize=12.sp,fontWeight=FontWeight.Medium)
                            Button(onClick={onSelectModelAndCreate(model)},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(8.dp),shape=RoundedCornerShape(12.dp)){Text("Usar",fontSize=12.sp)}
                        }
                    }
                }
                if(!older && branch.size>4) item(key="older") {
                    Surface(onClick={older=true},shape=RoundedCornerShape(20.dp),color=SurfaceDark,border=BorderStroke(1.dp,BorderSubtle)) {
                        Column(Modifier.padding(18.dp).heightIn(min=150.dp),verticalArrangement=Arrangement.Center){
                            Icon(Icons.Default.History,null,tint=PrimaryNeon);Spacer(Modifier.height(14.dp))
                            Text("Ver modelos anteriores",color=TextPrimary,fontWeight=FontWeight.SemiBold)
                            Text((branch.size-4).toString()+" más",color=TextSecondary,fontSize=12.sp)
                        }
                    }
                }
            }
            if(filtered.isEmpty() || provider!=null && branch.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                Text("No hay modelos para este uso y estos filtros. Cambia de uso o proveedor.",color=TextSecondary)
            }
        }
    }
    detail?.let { model ->
        AlertDialog(onDismissRequest={detail=null},title={Text(model.displayName)},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(usefulDescription(model));Text(price(model),color=PrimaryNeon)
                Text("No se calcula una estimación previa: la tarifa publicada puede no incluir duración, resolución, cantidad o extras. El coste válido es el que devuelva Atlas al completar la petición.",fontSize=12.sp)
                Text("Las opciones y entradas concretas se comprueban al abrir el modelo.",fontSize=12.sp)
                Text(model.id,fontSize=10.sp,color=TextSecondary)
            }
        },confirmButton={TextButton(onClick={detail=null;onSelectModelAndCreate(model)}){Text("Usar modelo")}},dismissButton={TextButton(onClick={detail=null}){Text("Cerrar")}})
    }
}
