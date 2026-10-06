package com.vidadomu.nexus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
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
import com.vidadomu.nexus.ui.navigation.Screen
import com.vidadomu.nexus.ui.output.OutputViewerScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }

        handleIntent(intent)

        setContent {
            NexusTheme {
                val navController = rememberNavController()
                val createViewModel: CreateViewModel = viewModel()
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

                Scaffold(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    containerColor = ScreenBackground,
                    bottomBar = {
                        if(!WindowInsets.isImeVisible) NexusBottomBar(navController = navController)
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Models.route,
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                    ) {
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
                                onPrepareAgain = {job -> createViewModel.preparePrevious(job);navController.navigate(Screen.Create.route) {launchSingleTop=true}}
                            )
                        }
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                onNavigateToOutput = { jobId ->
                                    navController.navigate("output/$jobId")
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen()
                        }
                        composable(
                            route = "output/{jobId}",
                            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                            OutputViewerScreen(
                                jobId = jobId,
                                onBack = { navController.popBackStack() },
                                onReuseAsInput = { content ->
                                    if (content.startsWith("http://") || content.startsWith("https://") || content.startsWith("content://")) {
                                        // User clicked "Animar" on an image: switch to video & attach image as input
                                        createViewModel.selectModality(com.vidadomu.nexus.core.model.Modality.VIDEO)
                                        createViewModel.setMediaInput(Uri.parse(content), "image/jpeg")
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
