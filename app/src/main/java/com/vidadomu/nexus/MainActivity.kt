package com.vidadomu.nexus

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.vidadomu.nexus.assistant.PendingShares
import com.vidadomu.nexus.assistant.PendingShare
import com.vidadomu.nexus.assistant.ContinueProjectChooser
import com.vidadomu.nexus.settings.RuntimePreferences
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.vidadomu.nexus.core.work.GenerationPollingWorker
import com.vidadomu.nexus.ui.create.CreateScreen
import com.vidadomu.nexus.ui.create.CreateViewModel
import com.vidadomu.nexus.ui.generations.GenerationsScreen
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.models.ModelsScreen
import com.vidadomu.nexus.ui.navigation.NexusBottomBar
import com.vidadomu.nexus.ui.navigation.revealBarOnSwipeUp
import com.vidadomu.nexus.ui.navigation.adjacentTab
import com.vidadomu.nexus.ui.navigation.tabSwipe
import com.vidadomu.nexus.ui.navigation.tabEnter
import com.vidadomu.nexus.ui.navigation.tabExit
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.vidadomu.nexus.ui.navigation.Screen
import com.vidadomu.nexus.ui.navigation.openHistoryTab
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.ui.settings.FlowCreateScreen
import com.vidadomu.nexus.ui.settings.SettingsScreen
import com.vidadomu.nexus.ui.theme.BackgroundDark
import com.vidadomu.nexus.ui.theme.ScreenBackground
import com.vidadomu.nexus.ui.theme.NexusTheme

@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {

    private var initialSharedUri by mutableStateOf<Uri?>(null)
    private var initialSharedMime by mutableStateOf<String?>(null)
    private var initialSharedText by mutableStateOf<String?>(null)
    private var initialDeepLinkJobId by mutableStateOf<String?>(null)
    private var agentDraft by mutableStateOf<com.vidadomu.nexus.agent.AgentDraft?>(null)
    private var pendingShares by mutableStateOf<List<PendingShare>>(emptyList())
    private var shareImporting by mutableStateOf(false)
    private var shareError by mutableStateOf<String?>(null)
    private var projectChooser by mutableStateOf(false)
    private var startupPreviewEpoch by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.vidadomu.nexus.sync.DeviceSyncService.resume(this)
        if (!BuildConfig.OFFLINE_QA && savedInstanceState == null && StartupPreview.consume(this)) startupPreviewEpoch = 1
        enableEdgeToEdge()
        com.vidadomu.nexus.ui.theme.applyNexusFullscreen(window)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }

        pendingShares=PendingShares(this).pending()
        val rules=RuntimePreferences.get(this)
        // Al arrancar solo si llevabas fuera el tiempo de ausencia (4 h por defecto) o es la primera vez, no cada vez que Android reinicia la app.
        projectChooser=savedInstanceState==null && rules.bool("startup.enabled") && (rules.bool("startup.cold") || awayLongEnough(rules))
        if(savedInstanceState==null || intent?.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE)) handleIntent(intent)

        setContent {
            NexusTheme {
                // Runtime construction reads the asset index, skills and rate tables. Await its
                // synchronized warm-up off main before creating the ViewModel; keep the first
                // frame responsive instead of waiting for that lock during composition.
                var runtimeReady by remember { mutableStateOf(false) }
                var startupFailed by remember { mutableStateOf(false) }
                var startupAttempt by remember { mutableIntStateOf(0) }
                LaunchedEffect(startupAttempt) {
                    startupFailed = false
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            com.vidadomu.nexus.assistant.AssistantRuntime.get(this@MainActivity)
                        }
                        runtimeReady = true
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) {
                        android.util.Log.e("NexusStartup", e.javaClass.simpleName)
                        startupFailed = true
                    }
                }
                if (!runtimeReady) {
                    com.vidadomu.nexus.ui.components.NexusStartupState(startupFailed) { startupAttempt++ }
                    return@NexusTheme
                }
                val navController = rememberNavController()
                val activeEntry by navController.currentBackStackEntryAsState()
                val tabs=remember {Screen.bottomNavItems}
                LaunchedEffect(Unit) {com.vidadomu.nexus.ui.theme.ChromeVisibility.load(this@MainActivity)}
                // The bar leaves by itself a few seconds after it was brought in, unless the user pinned it.
                val barShown by com.vidadomu.nexus.ui.theme.ChromeVisibility.bottomBar
                LaunchedEffect(barShown) {if(barShown) {kotlinx.coroutines.delay(5_000);com.vidadomu.nexus.ui.theme.ChromeVisibility.bottomBar.value=false}}
                val tabPrefs=remember {getSharedPreferences("nexus_workspace",0)}
                val pager=rememberPagerState(initialPage=tabPrefs.getInt("page",0).coerceIn(0,tabs.lastIndex),pageCount={tabs.size})
                val holder=rememberSaveableStateHolder()
                var tabRequest by remember {mutableStateOf<Int?>(null)}
                val rules by RuntimePreferences.get(this).state.collectAsState()
                LaunchedEffect(pager.settledPage) {tabPrefs.edit().putInt("page",pager.settledPage).apply()}
                val createViewModel: CreateViewModel = viewModel()
                val assistantViewModel = (application as NexusApplication).assistantViewModel()
                // One path for «Continuar en…» whether it is drawn inside the Agent tab or as the full sheet used for shared files.
                val chooseProject: (String, com.vidadomu.nexus.assistant.PendingShare?) -> Unit = {id,sharedNow -> lifecycleScope.launch {
                    try {
                        assistantViewModel.selectProject(id)
                        check(assistantViewModel.ui.first {!it.thinking}.activeProjectId==id)
                        if(sharedNow!=null && sharedNow.ready) {assistantViewModel.acceptShared(sharedNow);PendingShares(this@MainActivity).consumed(sharedNow.id);pendingShares=PendingShares(this@MainActivity).pending()}
                        projectChooser=false;tabRequest=0;navController.popBackStack("workspace",false)
                    } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                    catch(e:Exception) {shareError="No se pudo abrir el destino. El historial y los archivos compartidos se conservan."}
                }; Unit}
                LaunchedEffect(projectChooser) {if(projectChooser) {tabRequest=0;navController.popBackStack("workspace",false)}}
                agentDraft?.let {draft ->
                    androidx.compose.material3.AlertDialog(onDismissRequest={agentDraft=null},
                        title={androidx.compose.material3.Text("Borrador de ChatGPT")},
                        text={androidx.compose.material3.Text("Modelo: ${draft.modelId}\n\n${draft.prompt.take(500)}\n\nSe abrirá en el lienzo para revisar archivos, ajustes y coste. Sustituirá el borrador actual. No se enviará ninguna generación.")},
                        confirmButton={androidx.compose.material3.TextButton(onClick={
                            createViewModel.prepareAgentDraft(draft);agentDraft=null
                            navController.navigate(Screen.Create.route) {launchSingleTop=true}
                        }) {androidx.compose.material3.Text("Revisar en el lienzo")}},
                        dismissButton={androidx.compose.material3.TextButton(onClick={agentDraft=null}) {androidx.compose.material3.Text("Cancelar")}})
                }

                LaunchedEffect(Unit) {
                    if (BuildConfig.OFFLINE_QA && intent.getBooleanExtra("qa_export", false)) {
                        val id = "qa-export-${System.currentTimeMillis()}"
                        val media = com.vidadomu.nexus.ui.create.LocalQaFixture.media(this@MainActivity,
                            com.vidadomu.nexus.ui.create.LocalQaFixture.model(this@MainActivity, "image")).first()
                        val output = com.vidadomu.nexus.core.model.GenerationOutput(id, id,
                            com.vidadomu.nexus.core.model.Modality.IMAGE, "https://example.invalid/qa.png", media.uriString, "image/png")
                        (application as NexusApplication).database.insertOrUpdateJob(com.vidadomu.nexus.core.model.GenerationJob(
                            id = id, providerId = "qa", modelId = "qa-export", modality = com.vidadomu.nexus.core.model.Modality.IMAGE,
                            prompt = "Prueba local de descarga, sin generación", status = com.vidadomu.nexus.core.model.GenerationStatus.COMPLETED,
                            outputs = listOf(output)))
                        navController.navigate("output/$id")
                    }
                    if(BuildConfig.OFFLINE_QA && (intent.getBooleanExtra("qa_motion",false) || intent.hasExtra("qa_fixture"))) {
                        val fixture=com.vidadomu.nexus.ui.create.LocalQaFixture.model(this@MainActivity,intent.getStringExtra("qa_fixture") ?: "motion")
                        createViewModel.loadLocalPreview(fixture,com.vidadomu.nexus.ui.create.LocalQaFixture.media(this@MainActivity,fixture,if(intent.getStringExtra("qa_fixture")=="references") 3 else 1))
                        navController.navigate(Screen.Create.route) {launchSingleTop=true}
                    }
                }
                // Inject shared intent data if present
                LaunchedEffect(initialSharedUri, initialSharedText) {
                    if (initialSharedUri != null) {
                        createViewModel.setMediaInput(initialSharedUri, initialSharedMime)
                        initialSharedUri=null
                    }
                    if (initialSharedText != null) {
                        createViewModel.updatePrompt(initialSharedText ?: "")
                        initialSharedText=null
                    }
                }

                // Deep link from notification
                LaunchedEffect(initialDeepLinkJobId) {
                    initialDeepLinkJobId?.let { jobId ->
                        navController.navigate("output/$jobId")
                        initialDeepLinkJobId = null
                    }
                }

                com.vidadomu.nexus.ui.components.NexusChrome(
                    onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                    onNavigateToOutput = { id -> navController.navigate("output/$id") },
                    settingsVisible = activeEntry?.destination?.route != Screen.Settings.route
                ) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .imePadding().revealBarOnSwipeUp(),
                    containerColor = ScreenBackground,
                    bottomBar = {
                        if(!WindowInsets.isImeVisible) {
                            androidx.compose.foundation.layout.Column {
                                val currentRoute = activeEntry?.destination?.route
                                // Flow already lists its live jobs above its own composer dock; a second floating layer
                                // there stacked two fixed bars over the content.
                                val onFlowTab = currentRoute == "workspace" && tabs[pager.currentPage] == Screen.Flow
                                if (currentRoute?.startsWith("output") != true && !onFlowTab) {
                                    com.vidadomu.nexus.ui.components.LiveRenderPill(
                                        onOpenOutput = { id -> navController.navigate("output/$id") },
                                        onSendToAgent = { jobId, outputId ->
                                            assistantViewModel.attachLibraryResult(jobId, outputId)
                                            if (!navController.popBackStack(Screen.Assistant.route, false)) {
                                                navController.navigate(Screen.Assistant.route) { launchSingleTop = true }
                                            }
                                        }
                                    )
                                }
                                NexusBottomBar(navController = navController, selectedRoute=if(activeEntry?.destination?.route=="workspace") tabs[pager.currentPage].route else null,
                                    onSelect={screen->tabRequest=tabs.indexOf(screen).takeIf {it>=0};navController.popBackStack("workspace",false);com.vidadomu.nexus.ui.theme.ChromeVisibility.bottomBar.value=false})
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController=navController,startDestination="workspace",
                        modifier=Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
                        composable("workspace") {
                            LaunchedEffect(tabRequest) {tabRequest?.let {pager.scrollToPage(it);tabRequest=null}}
                            // Predictable Back (revision video 1, 00:31): an edge swipe on ArtBox, Lanzadera, Flow or Biblioteca
                            // returns to Agente instead of leaving Nexus; only Back from Agente exits. Screens inside a tab
                            // (a family, a sheet) register their own handlers later, so they still take precedence.
                            val workspaceScope = rememberCoroutineScope()
                            androidx.activity.compose.BackHandler(enabled = pager.currentPage != 0) {
                                workspaceScope.launch { pager.animateScrollToPage(0) }
                            }
                            // Más sensible: con arrastrar un 20 % de la pantalla (o un gesto rápido) ya cambia de pestaña, en vez del 50 % por defecto.
                            HorizontalPager(state=pager,key={tabs[it].route},beyondViewportPageCount=0,userScrollEnabled=!com.vidadomu.nexus.ui.theme.ChromeVisibility.nestedLevel.value,
                                flingBehavior=androidx.compose.foundation.pager.PagerDefaults.flingBehavior(state=pager,snapPositionalThreshold=.2f),
                                // No system-gesture inset here: Android already gives the edge back-gesture priority, and that inset
                                // (~20–25 dp per side with gesture navigation) doubled every screen's own 16 dp margin.
                                modifier=Modifier.fillMaxSize()) {index ->
                                holder.SaveableStateProvider(tabs[index].route) {
                                // La pestaña que sale se atenúa y encoge un poco, y la que entra crece: el cambio se siente continuo con el dedo.
                                Box(Modifier.fillMaxSize().graphicsLayer {
                                    val distance=kotlin.math.abs((pager.currentPage-index)+pager.currentPageOffsetFraction).coerceAtMost(1f)
                                    alpha=1f-.35f*distance
                                    scaleX=1f-.05f*distance;scaleY=1f-.05f*distance
                                }) {
                                when(tabs[index]) {
                            Screen.Assistant -> {
                            com.vidadomu.nexus.assistant.AssistantScreen(
                                onOpenInCanvas = { state ->
                                    createViewModel.openAssistantPlan(state)
                                    navController.navigate(Screen.Create.route) { launchSingleTop = true }
                                },
                                onNavigateToOutput = { jobId -> navController.navigate("output/$jobId") },
                                onOpenSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                                onOpenHistory = { navController.openHistoryTab() },
                                projectPicker = if(projectChooser && pendingShares.firstOrNull()==null) ({
                                    ContinueProjectChooser(assistantViewModel,null,onChoose={id -> chooseProject(id,null)},onDismiss={projectChooser=false},embedded=true)
                                }) else null,
                                onPickerDismiss = { projectChooser = false },
                                vm = assistantViewModel
                            )
                        }
                            Screen.Create -> {
                            CreateScreen(
                                onBrowseModels = { navController.navigate(Screen.Models.route) { launchSingleTop = true } },
                                onOpenFlow = { navController.navigate(Screen.Flow.route) { launchSingleTop = true } },
                                onBytePlusSettings = {page -> navController.navigate("byteplus/$page") {launchSingleTop=true}},
                                onNavigateToOutput = { jobId ->
                                    navController.navigate("output/$jobId")
                                },
                                viewModel = createViewModel
                            )
                        }
                            Screen.Models -> {
                            ModelsScreen(
                                onLocalInpaint = { navController.navigate("local-inpaint") { launchSingleTop=true } },
                                onSelectModelAndCreate = { model ->
                                    createViewModel.selectModality(model.modalities.first())
                                    createViewModel.selectModel(model)
                                    navController.navigate(Screen.Create.route) {launchSingleTop=true}
                                },
                                onSelectModelWithMedia = { model, media ->
                                    createViewModel.prepareWithMedia(model, media)
                                    navController.navigate(Screen.Create.route) {launchSingleTop=true}
                                }
                            )
                        }
                            Screen.Library -> {
                            LibraryScreen(
                                onEditPhoto={uri -> navController.navigate("local-inpaint?uri=${Uri.encode(uri)}") {launchSingleTop=true}},
                                onOpenHistory={navController.navigate(Screen.Generations.route)},
                                onOpenArtBox={tabRequest=tabs.indexOf(Screen.Models)},
                                onNavigateToOutput = { jobId -> navController.navigate("output/$jobId") },
                                onNavigateToResult = { jobId, index -> navController.navigate("output/$jobId?index=$index") },
                                onUseInCanvas = { asset ->
                                    // Igual que «compartir con Nexus»: entra como entrada del modelo elegido en el Lienzo.
                                    if (asset.type == com.vidadomu.nexus.agent.core.assets.AssetType.VIDEO)
                                        createViewModel.selectModality(com.vidadomu.nexus.core.model.Modality.VIDEO)
                                    createViewModel.setMediaInput(Uri.parse(asset.uri),
                                        asset.mimeType ?: if (asset.type == com.vidadomu.nexus.agent.core.assets.AssetType.VIDEO) "video/mp4" else "image/jpeg")
                                    navController.navigate(Screen.Create.route) { launchSingleTop = true }
                                },
                                onSendResultToAgent = { jobId, outputId ->
                                    assistantViewModel.attachLibraryResult(jobId,outputId)
                                    if (!navController.popBackStack(Screen.Assistant.route,false)) navController.navigate(Screen.Assistant.route) {launchSingleTop=true}
                                },
                                onSendToAgent = { asset ->
                                    assistantViewModel.attachLibraryAsset(asset.id)
                                    if (!navController.popBackStack(Screen.Assistant.route, inclusive = false))
                                        navController.navigate(Screen.Assistant.route) { launchSingleTop = true }
                                }
                            )
                        }
                            Screen.Flow -> {
                            FlowCreateScreen(
                                onBack = {tabRequest=0},
                                onOpenModels = { tabRequest=2 },
                                onNavigateToOutput = { jobId -> navController.navigate("output/$jobId") }
                            )
                        }
                                    else -> Unit
                                }
                                }
                                }
                            }
                        }
                        tabs.forEach {tab ->composable(tab.route) {
                            LaunchedEffect(tab.route) {tabRequest=tabs.indexOf(tab).takeIf {it>=0};navController.popBackStack("workspace",false)}
                        }}
                        composable(Screen.Generations.route) {
                            GenerationsScreen(
                                onNavigateToOutput = { jobId ->
                                    navController.navigate("output/$jobId")
                                },
                                onPrepareAgain = {job -> createViewModel.preparePrevious(job);navController.navigate(Screen.Create.route) {launchSingleTop=true}},
                                onSendResultToAgent = { jobId, outputId ->
                                    assistantViewModel.attachLibraryResult(jobId,outputId)
                                    if (!navController.popBackStack(Screen.Assistant.route,false)) navController.navigate(Screen.Assistant.route) {launchSingleTop=true}
                                }
                            )
                        }
                        composable("local-inpaint?uri={uri}", arguments=listOf(androidx.navigation.navArgument("uri") {nullable=true;defaultValue=null})) {entry ->
                            com.vidadomu.nexus.inpaint.LocalInpaintScreen(onBack={navController.popBackStack()}, initialUri=entry.arguments?.getString("uri"))
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(assistantViewModel = assistantViewModel, onCreateInFlow = {
                                navController.navigate(Screen.Flow.route) { launchSingleTop = true }
                            })
                        }
                        composable("byteplus/{page}",arguments=listOf(navArgument("page") {type=NavType.StringType})) {entry ->
                            SettingsScreen(initialPage=entry.arguments?.getString("page"),onCloseInitialPage={navController.popBackStack()},assistantViewModel=assistantViewModel)
                        }
                        composable(
                            route = "output/{jobId}?index={index}",
                            arguments = listOf(navArgument("jobId") { type = NavType.StringType }, navArgument("index") {type=NavType.IntType;defaultValue=0})
                        ) { backStackEntry ->
                            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                            OutputViewerScreen(
                                jobId = jobId,
                                initialOutputIndex = backStackEntry.arguments?.getInt("index") ?: 0,
                                onBack = { navController.popBackStack() },
                                onOpenHistory = { navController.openHistoryTab() },
                                onOpenFlow = {navController.navigate(Screen.Flow.route) {launchSingleTop=true}},
                                onReuseAsInput = { content, mimeType ->
                                    if (mimeType != null) {
                                        // Media and prompt reuse are explicit, including prompts that happen to be URLs.
                                        createViewModel.reuseImageInput(Uri.parse(content), mimeType)
                                    } else {
                                        // User clicked "Remezclar": copy prompt
                                        createViewModel.updatePrompt(content)
                                    }
                                    navController.navigate(Screen.Create.route) {launchSingleTop=true}
                                }
                            )
                        }
                    }
                }
                }
                NexusStartupOnboardingOverlay(epoch = startupPreviewEpoch)
                val shared=pendingShares.firstOrNull()
                LaunchedEffect(shared?.id,rules.value("share.choose")) {
                    if(shared?.ready==true && !rules.value("share.choose").toBoolean()) {
                        try {assistantViewModel.acceptShared(shared);PendingShares(this@MainActivity).consumed(shared.id);pendingShares=PendingShares(this@MainActivity).pending();projectChooser=false;tabRequest=0}
                        catch(e:kotlinx.coroutines.CancellationException) {throw e}
                        catch(e:Exception) {shareError="No se pudo añadir el lote al chat. Tus archivos se conservan para reintentarlo."}
                    }
                }
                if(shared!=null && (projectChooser || rules.value("share.choose").toBoolean() || !shared.ready)) ContinueProjectChooser(assistantViewModel,shared,
                    onRetryShare={lifecycleScope.launch {PendingShares(this@MainActivity).retry(shared);pendingShares=PendingShares(this@MainActivity).pending()}},
                    onChoose={id->chooseProject(id,shared)}, onDismiss={projectChooser=false;if(shared!=null) {lifecycleScope.launch {
                        if(shared.ready) {assistantViewModel.acceptShared(shared);PendingShares(this@MainActivity).consumed(shared.id);pendingShares=PendingShares(this@MainActivity).pending()}
                    }}})
                if(shareImporting) androidx.compose.material3.Surface(Modifier.fillMaxSize(),color=androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Box(contentAlignment=androidx.compose.ui.Alignment.Center) {androidx.compose.material3.Text("Conservando archivos compartidos…")}
                }
                shareError?.let {message->androidx.compose.material3.AlertDialog(onDismissRequest={shareError=null},title={androidx.compose.material3.Text("Archivos compartidos")},text={androidx.compose.material3.Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick={shareError=null}) {androidx.compose.material3.Text("Entendido")}})}
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) com.vidadomu.nexus.ui.theme.applyNexusFullscreen(window)
    }

    override fun onResume() {
        super.onResume()
        com.vidadomu.nexus.ui.theme.applyNexusFullscreen(window)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if(BuildConfig.DEBUG) intent.getStringExtra("nexus_agent_draft_id")?.let {id ->
            agentDraft=runCatching {com.vidadomu.nexus.agent.AgentDraftStore.read(this,id)}.getOrNull()
            return
        }

        // 1. Notification deep link
        val deepLinkJobId = intent.getStringExtra(GenerationPollingWorker.EXTRA_NAVIGATE_JOB_ID)
        if (deepLinkJobId != null) {
            initialDeepLinkJobId = deepLinkJobId
            return
        }

        if(intent.action in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE) && !intent.getBooleanExtra("nexus_share_processed",false)) {
            shareImporting=true
            lifecycleScope.launch {
                try {
                    PendingShares(this@MainActivity).receive(intent);pendingShares=PendingShares(this@MainActivity).pending()
                    // Keep the Activity's identity stable while retiring transient URI grants/extras.
                    intent.putExtra("nexus_share_processed",true)
                    intent.removeExtra(Intent.EXTRA_STREAM);intent.removeExtra(Intent.EXTRA_TEXT);intent.clipData=null
                    setIntent(intent)
                }
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {pendingShares=PendingShares(this@MainActivity).pending();shareError="No se pudo terminar la copia. El lote disponible se conserva; revisa el almacenamiento y vuelve a compartir los archivos que falten."}
                finally {shareImporting=false}
            }
        }
    }

    override fun onStop() {
        getSharedPreferences("nexus_workspace",0).edit().putLong("leftAt",System.currentTimeMillis()).apply()
        super.onStop()
    }
    override fun onStart() {
        super.onStart()
        val rules=RuntimePreferences.get(this)
        val left=getSharedPreferences("nexus_workspace",0).getLong("leftAt",0)
        if(left>0 && rules.bool("startup.enabled") && awayLongEnough(rules)) projectChooser=true
    }

    /** First launch, or away at least «startup.idleMinutes» (0 = never by absence). */
    private fun awayLongEnough(rules: RuntimePreferences): Boolean {
        val left=getSharedPreferences("nexus_workspace",0).getLong("leftAt",0)
        val minutes=rules.int("startup.idleMinutes")
        return left==0L || (minutes>0 && System.currentTimeMillis()-left>=minutes*60_000L)
    }

}

@Composable
private fun NexusStartupOnboardingOverlay(epoch: Int) {
    if (epoch <= 0) return
    var visible by remember(epoch) { mutableStateOf(true) }
    var shown by remember(epoch) { mutableStateOf(false) }
    LaunchedEffect(epoch) {
        shown = true
        kotlinx.coroutines.delay(2800)
        visible = false
    }
    // El logo entra creciendo con un muelle suave; el resto aparece un instante después.
    val logoScale by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else .82f,
        androidx.compose.animation.core.spring(dampingRatio = .72f, stiffness = 140f), label = "Onboarding logo")
    val textAlpha by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f,
        androidx.compose.animation.core.tween(700, delayMillis = 350), label = "Onboarding text")
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(420))
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ScreenBackground, colors.primary.copy(alpha = .10f).compositeOver(ScreenBackground), ScreenBackground)))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { visible = false },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = NexusTokens.space24),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                // Sin recorte ni tarjeta: el logo entero (orejas incluidas) ocupa casi todo el ancho.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.nexus_logo),
                    contentDescription = "Logo oficial de NEXUS",
                    modifier = Modifier
                        .fillMaxWidth(.8f)
                        .heightIn(max = 380.dp)
                        .graphicsLayer { scaleX = logoScale; scaleY = logoScale },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Text(
                    text = "NEXUS",
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = com.vidadomu.nexus.ui.theme.TextPrimary,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = "Las buenas ideas empiezan aquí.",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = com.vidadomu.nexus.ui.theme.TextSecondary,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Surface(
                    shape = NexusTokens.pill,
                    color = colors.primary.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.graphicsLayer { alpha = textAlpha }
                ) {
                    androidx.compose.material3.Text(
                        text = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.CODENAME} · build ${BuildConfig.VERSION_CODE}",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
                androidx.compose.material3.Text(
                    text = "Toca para empezar",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = com.vidadomu.nexus.ui.theme.TextMuted,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha * .8f }
                )
            }
        }
    }
}


private object StartupPreview {
    private const val PREFS = "nexus_startup_v1"
    private const val KEY_SEEN = "preview_seen"

    @Synchronized
    fun consume(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.contains(KEY_SEEN)) return false

        // Only a genuinely new installation gets the preview. Existing installs that upgrade
        // before this preference existed should not be interrupted by it.
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val freshInstall = packageInfo.firstInstallTime >= packageInfo.lastUpdateTime - 30_000L
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
        return freshInstall
    }
}
