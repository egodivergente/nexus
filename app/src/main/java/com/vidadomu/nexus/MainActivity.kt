package com.vidadomu.nexus

import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.agent.core.assets.storyGenerationContext

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
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
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
import com.vidadomu.nexus.ui.navigation.NotificationNavigation
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.cut.NexusCut
import com.vidadomu.nexus.ui.cut.CutEditorScreen
import com.vidadomu.nexus.ui.cut.CutHomeScreen
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
        // Vidal 01-11: el chat del Agente abre directo, sin la lista «Elige un proyecto» encima. Solo se elige destino al compartir archivos.
        projectChooser=false
        // Keep a pending result across recreation while startup is still warming up.
        initialDeepLinkJobId = savedInstanceState?.getString(STATE_PENDING_JOB)
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
                            com.vidadomu.nexus.assistant.AssistantRuntime.get(application as NexusApplication)
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
                // The open tab is remembered by route: removing Lanzadera from the bar (Vidal 01-11) shifted every index.
                val pager=rememberPagerState(initialPage=tabs.indexOfFirst {it.route==tabPrefs.getString("pageRoute",null)}.coerceAtLeast(0),pageCount={tabs.size})
                val holder=rememberSaveableStateHolder()
                var tabRequest by remember {mutableStateOf<Int?>(null)}
                val rules by RuntimePreferences.get(this).state.collectAsState()
                LaunchedEffect(pager.settledPage) {tabPrefs.edit().putString("pageRoute",tabs[pager.settledPage].route).apply()}
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                var previousPage by remember(pager) { mutableIntStateOf(pager.settledPage) }
                LaunchedEffect(pager.settledPage) {
                    if (previousPage != pager.settledPage) {
                        // A swipe can change tabs while the chat/search field still owns focus.
                        // Release that focus without changing its persisted draft.
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                        previousPage = pager.settledPage
                    }
                }
                val createViewModel: CreateViewModel = viewModel()
                val assistantViewModel = (application as NexusApplication).assistantViewModel()
                val cutRuntime = remember { com.vidadomu.nexus.assistant.AssistantRuntime.get(application as NexusApplication) }
                val projectNavigationScope = rememberCoroutineScope()
                val projectNavigationMutex = remember { kotlinx.coroutines.sync.Mutex() }
                val pendingProjectLinks = remember { mutableSetOf<String>() }
                var requestedStoryTab by rememberSaveable { mutableStateOf<Int?>(null) }
                var cutReturnTab by rememberSaveable { mutableStateOf<String?>(null) }
                var observedWorkspaceRoute by remember { mutableStateOf(tabs[pager.settledPage].route) }
                var cutImportProjectId by rememberSaveable { mutableStateOf<String?>(null) }
                var cutImportPending by rememberSaveable { mutableStateOf(false) }
                var cutImporting by remember { mutableStateOf(false) }
                val cutMediaPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
                ) { uris ->
                    // The destination belongs to the picker request, never to whichever project is now active.
                    val destination = cutImportProjectId
                    cutImportPending = false
                    if (uris.isNotEmpty()) {
                        cutImporting = true
                        lifecycleScope.launch {
                            var imported = 0
                            var failed = 0
                            try {
                                require(destination == null || cutRuntime.assets.snapshot().projects.firstOrNull { it.id == destination }?.archived == false) {
                                    "El proyecto de destino ya no está disponible. Los originales se conservan."
                                }
                                for (uri in uris.distinct()) {
                                    try {
                                        com.vidadomu.nexus.assistant.AgentMediaStore.persist(
                                            this@MainActivity, uri, contentResolver.getType(uri), destination,
                                        )
                                        imported++
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                    catch (_: Exception) { failed++ }
                                }
                                val detail = if (failed > 0) " No se pudieron importar $failed; los originales se conservan." else ""
                                android.widget.Toast.makeText(this@MainActivity, "$imported archivos importados.$detail", android.widget.Toast.LENGTH_LONG).show()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (error: Exception) {
                                android.widget.Toast.makeText(this@MainActivity, error.message ?: "No se pudo importar el material.", android.widget.Toast.LENGTH_LONG).show()
                            } finally { cutImporting = false }
                        }
                    }
                }
                fun openProjectFromCut(projectId: String, destination: Screen? = null, storyTab: Int? = null, draft: String? = null,
                    resultToAttach: Pair<String, String>? = null) {
                    val linkKey = listOf(projectId, destination?.route.orEmpty(), storyTab?.toString().orEmpty(), draft.orEmpty(), resultToAttach?.toString().orEmpty()).joinToString("\u0000")
                    if (!pendingProjectLinks.add(linkKey)) return
                    projectNavigationScope.launch {
                        try {
                            projectNavigationMutex.withLock {
                                assistantViewModel.awaitChatsReady()
                                val project = cutRuntime.assets.snapshot().projects.firstOrNull { it.id == projectId }
                                require(project != null && !project.archived) { "El proyecto ya no está disponible." }
                                val before = assistantViewModel.ui.value
                                if (before.activeProjectId != projectId) {
                                    require(!before.thinking && !before.carrying && before.plans.values.none { it.busy }) {
                                        "Espera a que termine la operación del agente antes de abrir otro proyecto."
                                    }
                                    assistantViewModel.selectProject(projectId)
                                    val selected = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                                        assistantViewModel.ui.first {
                                            it.activeProjectId == projectId || (!it.thinking && it.message != before.message)
                                        }
                                    } ?: error("El proyecto todavía se está abriendo. Vuelve a intentarlo; no se ha enviado ninguna petición.")
                                    check(selected.activeProjectId == projectId) { "No se pudo abrir la conversación de este proyecto. El historial se conserva." }
                                }
                                // switchConversation publishes the ID before synchronously restoring its saved items.
                                kotlinx.coroutines.yield()
                                check(assistantViewModel.ui.value.activeProjectId == projectId) { "El proyecto activo cambió. Vuelve a abrir el destino." }
                                val conversationId = assistantViewModel.ui.value.conversationId
                                com.vidadomu.nexus.assistant.story.StorySelectionStore.clear()
                                if (draft != null) {
                                    val text = listOf("Proyecto: «${project.name}».", draft.trim())
                                        .filter { it.isNotBlank() }.joinToString("\n\n")
                                    // Existing per-conversation draft text is appended to, with no call to send().
                                    assistantViewModel.acceptShared(PendingShare(java.util.UUID.randomUUID().toString(), text = text))
                                    check(assistantViewModel.ui.value.activeProjectId == projectId && assistantViewModel.ui.value.conversationId == conversationId) {
                                        "La conversación cambió mientras se preparaba el borrador. Revisa el proyecto antes de enviarlo."
                                    }
                                }
                                resultToAttach?.let { (jobId, outputId) -> assistantViewModel.attachLibraryResult(jobId, outputId) }
                                if (destination == null) return@withLock
                                focusManager.clearFocus(force = true)
                                keyboard?.hide()
                                if (destination == Screen.Generations) {
                                    navController.navigate("${Screen.Generations.route}?projectId=${Uri.encode(projectId)}") { launchSingleTop = true }
                                } else {
                                    if (destination == Screen.Story) requestedStoryTab = storyTab ?: 0
                                    cutReturnTab = destination.route.takeIf { destination == Screen.Story || destination == Screen.Assistant }
                                    navController.popBackStack("workspace", false)
                                    tabRequest = tabs.indexOf(destination)
                                }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            android.widget.Toast.makeText(this@MainActivity, error.message ?: "No se pudo abrir el proyecto.", android.widget.Toast.LENGTH_LONG).show()
                        } finally { pendingProjectLinks.remove(linkKey) }
                    }
                }
                LaunchedEffect(pager.settledPage) {
                    val route = tabs[pager.settledPage].route
                    if (cutReturnTab != null && route != cutReturnTab && (route != Screen.Cut.route || observedWorkspaceRoute == cutReturnTab)) cutReturnTab = null
                    observedWorkspaceRoute = route
                }
                LaunchedEffect(navController) {
                    NexusCut.openRequests.collect { projectId ->
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                        navController.navigate("cut/${Uri.encode(projectId)}") { launchSingleTop = true }
                    }
                }
                LaunchedEffect(Unit) {
                    com.vidadomu.nexus.assistant.story.StoryNavigation.requests.collect { destination ->
                        val selection = com.vidadomu.nexus.assistant.story.StorySelectionStore.selection.value
                        when (destination) {
                            com.vidadomu.nexus.assistant.story.StoryDestination.FLOW -> {
                                navController.popBackStack("workspace", false)
                                tabRequest = tabs.indexOf(Screen.Flow)
                            }
                            com.vidadomu.nexus.assistant.story.StoryDestination.GENERATE -> {
                                if (selection?.shotId != null) {
                                    val index = com.vidadomu.nexus.assistant.AssistantRuntime.get(application as NexusApplication).assets
                                    val origin = runCatching { index.storyGenerationContext(selection.projectId, selection.shotId) }.getOrNull()
                                    if (origin != null) {
                                        createViewModel.prepareStoryShot(origin)
                                        navController.navigate(Screen.Create.route) { launchSingleTop = true }
                                    } else android.widget.Toast.makeText(this@MainActivity, "El plano cambió o ya no está disponible. Vuelve a abrirlo desde el guion.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
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
                        navController.navigate("output/${Uri.encode(jobId)}") { launchSingleTop = true }
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
                        if(!WindowInsets.isImeVisible && activeEntry?.destination?.route?.startsWith("cut/") != true) {
                            androidx.compose.foundation.layout.Column {
                                val currentRoute = activeEntry?.destination?.route
                                if (currentRoute?.startsWith("output") != true) {
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
                                    onSelect={screen->cutReturnTab=null;tabRequest=tabs.indexOf(screen).takeIf {it>=0};navController.popBackStack("workspace",false);com.vidadomu.nexus.ui.theme.ChromeVisibility.bottomBar.value=false})
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController=navController,startDestination="workspace",
                        modifier=Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
                        composable("workspace") {
                            LaunchedEffect(tabRequest) {tabRequest?.let {pager.scrollToPage(it);tabRequest=null}}
                            // Predictable Back (revision video 1, 00:31): an edge swipe on ArtBox, Lanzadera or Biblioteca
                            // returns to Agente instead of leaving Nexus; only Back from Agente exits. Screens inside a tab
                            // (a family, a sheet) register their own handlers later, so they still take precedence.
                            val workspaceScope = rememberCoroutineScope()
                            androidx.activity.compose.BackHandler(enabled = pager.currentPage != 0) {
                                workspaceScope.launch { pager.animateScrollToPage(0) }
                            }
                            androidx.activity.compose.BackHandler(enabled = cutReturnTab == tabs[pager.currentPage].route) {
                                cutReturnTab = null
                                tabRequest = tabs.indexOf(Screen.Cut)
                            }
                            // Más sensible: con arrastrar un 20 % de la pantalla (o un gesto rápido) ya cambia de pestaña, en vez del 50 % por defecto.
                            HorizontalPager(state=pager,key={tabs[it].route},beyondViewportPageCount=0,
                                // En Flow los arrastres son de la página en directo: no cambian de pestaña.
                                userScrollEnabled=!com.vidadomu.nexus.ui.theme.ChromeVisibility.nestedLevel.value && tabs[pager.currentPage]!=Screen.Flow,
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
                                projectPicker = null,
                                onPickerDismiss = { projectChooser = false },
                                vm = assistantViewModel
                            )
                        }
                            Screen.Story -> com.vidadomu.nexus.assistant.story.StoryTabScreen(
                                vm = assistantViewModel,
                                isActive = pager.currentPage == index,
                                onAgent = {
                                    if (cutReturnTab == Screen.Story.route) cutReturnTab = Screen.Assistant.route
                                    tabRequest = tabs.indexOf(Screen.Assistant)
                                },
                                onGenerate = { com.vidadomu.nexus.assistant.story.StoryNavigation.request(com.vidadomu.nexus.assistant.story.StoryDestination.GENERATE) },
                                onFlow = { tabRequest = tabs.indexOf(Screen.Flow) },
                                requestedTab = requestedStoryTab,
                                onTabRequestConsumed = { requestedStoryTab = null },
                                onClose = {
                                    val fromCut = cutReturnTab == Screen.Story.route
                                    cutReturnTab = null
                                    tabRequest = tabs.indexOf(if (fromCut) Screen.Cut else Screen.Assistant)
                                },
                            )
                            Screen.Models -> {
                            ModelsScreen(
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
                            Screen.Cut -> CutHomeScreen(
                                isActive = pager.currentPage == index,
                                onOpenStory = { openProjectFromCut(it, Screen.Story, storyTab = 0) },
                                onOpenMaterial = { openProjectFromCut(it, Screen.Story, storyTab = 2) },
                                onOpenGenerations = { openProjectFromCut(it, Screen.Generations) },
                                onOpenAgent = { projectId, prompt -> openProjectFromCut(projectId, Screen.Assistant, draft = prompt) },
                                onSelectProject = { openProjectFromCut(it) },
                                onArchivedProject = { projectId ->
                                    projectNavigationScope.launch {
                                        projectNavigationMutex.withLock {
                                            assistantViewModel.awaitChatsReady()
                                            val before = assistantViewModel.ui.value
                                            if (!assistantViewModel.leaveArchivedProject(projectId)) {
                                                android.widget.Toast.makeText(this@MainActivity,
                                                    "El proyecto está archivado. Al terminar la operación del agente se dejará su contexto automáticamente; el historial se conserva.",
                                                    android.widget.Toast.LENGTH_LONG).show()
                                            } else if (before.activeProjectId == projectId) {
                                                val changed = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                                                    assistantViewModel.ui.first { it.activeProjectId != projectId || (!it.thinking && it.message != before.message) }
                                                }
                                                if (changed == null || changed.activeProjectId == projectId) {
                                                    android.widget.Toast.makeText(this@MainActivity,
                                                        "No se pudo dejar el contexto del proyecto archivado. Su conversación se conserva.",
                                                        android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onImportMedia = { projectId ->
                                    if (cutImportPending || cutImporting) {
                                        android.widget.Toast.makeText(this@MainActivity, "Hay una importación en curso.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        cutImportProjectId = projectId
                                        cutImportPending = true
                                        cutMediaPicker.launch(arrayOf("image/*", "video/*", "audio/*"))
                                    }
                                },
                            )
                            Screen.Library -> {
                            LibraryScreen(
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
                            Screen.Flow -> com.vidadomu.nexus.ui.flow.FlowLiveScreen(
                                onOpenLibrary={tabRequest=tabs.indexOf(Screen.Library)}
                            )
                                    else -> Unit
                                }
                                }
                                }
                            }
                        }
                        tabs.forEach {tab ->composable(tab.route) {
                            LaunchedEffect(tab.route) {tabRequest=tabs.indexOf(tab).takeIf {it>=0};navController.popBackStack("workspace",false)}
                        }}
                        // Vidal 01-11 (vídeo, 08:01): Lanzadera ya no es una pestaña. Solo se abre encima cuando algo la necesita
                        // («Crear con este modelo», «Abrir en el lienzo», «Reutilizar»…) y Atrás vuelve a donde estabas.
                        composable(Screen.Create.route) {
                            CreateScreen(
                                onBrowseModels = { navController.navigate(Screen.Models.route) { launchSingleTop = true } },
                                onBytePlusSettings = {page -> navController.navigate("byteplus/$page") {launchSingleTop=true}},
                                onNavigateToOutput = { jobId ->
                                    navController.navigate("output/$jobId")
                                },
                                viewModel = createViewModel
                            )
                        }
                        composable(
                            "${Screen.Generations.route}?projectId={projectId}",
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { entry ->
                            GenerationsScreen(
                                projectId = entry.arguments?.getString("projectId"),
                                onNavigateToResult = { jobId, outputIndex -> navController.navigate("output/$jobId?index=$outputIndex") },
                                onNavigateToOutput = { jobId ->
                                    navController.navigate("output/$jobId")
                                },
                                onPrepareAgain = {job -> createViewModel.preparePrevious(job);navController.navigate(Screen.Create.route) {launchSingleTop=true}},
                                onSendResultToAgent = { jobId, outputId ->
                                    val projectId = entry.arguments?.getString("projectId")
                                    if (projectId != null) openProjectFromCut(projectId, Screen.Assistant, resultToAttach = jobId to outputId)
                                    else {
                                        assistantViewModel.attachLibraryResult(jobId,outputId)
                                        if (!navController.popBackStack(Screen.Assistant.route,false)) navController.navigate(Screen.Assistant.route) {launchSingleTop=true}
                                    }
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(assistantViewModel = assistantViewModel)
                        }
                        composable("byteplus/{page}",arguments=listOf(navArgument("page") {type=NavType.StringType})) {entry ->
                            SettingsScreen(initialPage=entry.arguments?.getString("page"),onCloseInitialPage={navController.popBackStack()},assistantViewModel=assistantViewModel)
                        }
                        composable("cut/{projectId}", arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
                            CutEditorScreen(
                                projectId = requireNotNull(entry.arguments?.getString("projectId")),
                                onBack = { navController.popBackStack() },
                                onOpenLibraryAsset = {
                                    navController.popBackStack("workspace", false)
                                    tabRequest = tabs.indexOf(Screen.Library)
                                },
                            )
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

    override fun onSaveInstanceState(outState: Bundle) {
        initialDeepLinkJobId?.let { outState.putString(STATE_PENDING_JOB, it) }
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_PENDING_JOB = "nexus_pending_notification_job"
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
        val deepLinkJobId = NotificationNavigation.takeJobId(intent)
        if (deepLinkJobId != null) {
            projectChooser = false
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
