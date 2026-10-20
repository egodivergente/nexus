package com.vidadomu.nexus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.vidadomu.nexus.ui.navigation.adjacentTab
import com.vidadomu.nexus.ui.navigation.tabSwipe
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
    private var startupPreviewEpoch by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.vidadomu.nexus.sync.DeviceSyncService.resume(this)
        if (!BuildConfig.OFFLINE_QA && savedInstanceState == null && StartupPreview.consume(this)) startupPreviewEpoch = 1
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }

        handleIntent(intent)

        setContent {
            NexusTheme {
                val navController = rememberNavController()
                val activeEntry by navController.currentBackStackEntryAsState()
                val createViewModel: CreateViewModel = viewModel()
                // Ligado a la actividad (como el Lienzo): la conversación y el plan pendiente sobreviven a «Atrás» y al cambio de pestaña.
                val assistantViewModel: com.vidadomu.nexus.assistant.AssistantViewModel = viewModel()
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
                    modifier = Modifier.fillMaxSize().imePadding(),
                    containerColor = ScreenBackground,
                    bottomBar = {
                        if(!WindowInsets.isImeVisible) {
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
                                NexusBottomBar(navController = navController)
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Assistant.route,
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).tabSwipe(enabled = activeEntry?.destination?.route in Screen.bottomNavItems.map { it.route }) { direction ->
                            adjacentTab(activeEntry?.destination?.route, direction, Screen.bottomNavItems.map { it.route })?.let { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = route != Screen.Models.route && route != Screen.Assistant.route
                                }
                            }
                        }
                    ) {
                        composable(Screen.Assistant.route) {
                            com.vidadomu.nexus.assistant.AssistantScreen(
                                onOpenInCanvas = { state ->
                                    createViewModel.openAssistantPlan(state)
                                    navController.navigate(Screen.Create.route) { launchSingleTop = true }
                                },
                                onNavigateToOutput = { jobId -> navController.navigate("output/$jobId") },
                                onOpenSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                                onOpenHistory = { navController.openHistoryTab() },
                                vm = assistantViewModel
                            )
                        }
                        composable(Screen.Create.route) {
                            CreateScreen(
                                onBrowseModels = { navController.navigate(Screen.Models.route) { launchSingleTop = true } },
                                onNavigateToOutput = { jobId ->
                                    navController.navigate("output/$jobId")
                                },
                                viewModel = createViewModel
                            )
                        }
                        composable(Screen.Models.route) {
                            ModelsScreen(
                                onSelectModelAndCreate = { model ->
                                    createViewModel.selectModality(model.modalities.first())
                                    createViewModel.selectModel(model)
                                    navController.navigate(Screen.Create.route) {launchSingleTop=true}
                                }
                            )
                        }
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
                        composable(Screen.Library.route) {
                            LibraryScreen(
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
                        composable(Screen.Settings.route) {
                            SettingsScreen(assistantViewModel = assistantViewModel, onCreateInFlow = {
                                navController.navigate(Screen.Flow.route) { launchSingleTop = true }
                            })
                        }
                        composable(Screen.Flow.route) {
                            FlowCreateScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToOutput = { jobId -> navController.navigate("output/$jobId") }
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
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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

        // 2. Share Intent handling
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type
            if (type != null) {
                if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")) {
                    val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (streamUri != null) {
                        initialSharedUri = streamUri
                        initialSharedMime = type
                    }
                } else if (type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) {
                        initialSharedText = text
                    }
                }
            }
        }
    }
}

@Composable
private fun NexusStartupOnboardingOverlay(epoch: Int) {
    if (epoch <= 0) return
    var visible by remember(epoch) { mutableStateOf(true) }
    LaunchedEffect(epoch) {
        kotlinx.coroutines.delay(1900)
        visible = false
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(380))
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { visible = false },
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = ScreenBackground.copy(alpha = 0.96f)
            ) {}
            androidx.compose.material3.Surface(
                shape = com.vidadomu.nexus.ui.theme.NexusTokens.sheet,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, com.vidadomu.nexus.ui.theme.BorderSubtle),
                tonalElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.nexus_logo),
                        contentDescription = "Logo oficial de NEXUS",
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .padding(horizontal = 8.dp)
                            .sizeIn(maxWidth = 148.dp, maxHeight = 142.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                    ) {
                        androidx.compose.material3.Text(
                            text = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.CODENAME} · build ${BuildConfig.VERSION_CODE}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                    androidx.compose.material3.Text(
                        text = "TU ESPACIO CREATIVO",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = com.vidadomu.nexus.ui.theme.TextMuted
                    )
                    androidx.compose.material3.Text(
                        text = "Las buenas ideas empiezan aquí.",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = com.vidadomu.nexus.ui.theme.TextPrimary
                    )
                }
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
