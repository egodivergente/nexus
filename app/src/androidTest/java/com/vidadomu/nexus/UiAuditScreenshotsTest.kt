package com.vidadomu.nexus

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidadomu.nexus.ui.accounts.*
import com.vidadomu.nexus.core.currency.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.*
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.billing.BillingLedgerScreen
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.ui.generations.GenerationsScreen
import com.vidadomu.nexus.ui.library.LibraryScreen
import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.ui.output.OutputViewerScreen
import com.vidadomu.nexus.ui.settings.*
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real composables, synthetic local data. Never submits a plan or invokes the LLM. */
class UiAuditScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureScreenMatrixWithoutGenerating() {
        check(BuildConfig.OFFLINE_QA)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as NexusApplication
        check(app.packageName.endsWith(".uiqa"))
        runBlocking { app.ready.await(); app.database.loading.first { !it } }
        app.providerRegistry.setMockMode(true)
        app.modelCatalogRepository.refreshModels()
        runBlocking { app.modelCatalogRepository.isLoading.first { !it } }
        val initialJobs = app.generationRepository.allJobs.value
        val model=LocalQaFixture.model(app,"references").copy(providerId="mock_provider",basePriceUsd=.05,priceUnit="image",priceUnitVerified=true)
        val media=LocalQaFixture.media(app,model,3)
        val id="ui-audit-fixture"
        val fixtureTime=java.time.ZonedDateTime.of(2026,10,9,12,0,0,0,java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun asset(name:String):String {
            val file=File(app.cacheDir,"ui-matrix-$name")
            instrumentation.context.assets.open(name).use {input->file.outputStream().use {input.copyTo(it)}}
            return android.net.Uri.fromFile(file).toString()
        }
        fun picture(index:Int):String {
            val file=File(app.cacheDir,"ui-matrix-picture-$index.png")
            val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(android.graphics.Color.rgb(234-index*12,229-index*8,211))
            paint.color=android.graphics.Color.rgb(42+index*18,100+index*12,127+index*10)
            canvas.drawOval(140f,80f,372f,402f,paint)
            paint.color=android.graphics.Color.rgb(222,200,168);canvas.drawOval(187f,115f,325f,255f,paint)
            paint.color=android.graphics.Color.rgb(78,120,111);canvas.drawCircle(95f,383f,50f,paint)
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            return android.net.Uri.fromFile(file).toString()
        }
        val outputs=(1..3).map {i->val uri=picture(i);GenerationOutput("ui-audit-output-$i",id,Modality.IMAGE,uri,uri,"image/png",512,512)}
        val video=asset("ui-audit-video.mp4");val audio=asset("ui-audit-audio.wav")
        val videoModel=model.copy(id="ui-audit-video-model",displayName="Kling · Vídeo",organization="Kling",family="Kling",modelFamily="Kling",modalities=listOf(Modality.VIDEO),basePriceUsd=.02,priceUnit="second",inputMode=ModelInputMode.TEXT_ONLY)
        val audioModel=model.copy(id="ui-audit-audio-model",displayName="Audio de estudio",organization="Estudio",family="Audio",modelFamily="Audio",modalities=listOf(Modality.AUDIO),basePriceUsd=.01,priceUnit="request",inputMode=ModelInputMode.TEXT_ONLY)
        @Suppress("UNCHECKED_CAST")
        fun <T> flow(owner:Any,name:String)=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(owner) as MutableStateFlow<T>
        val modelFlow=flow<List<NexusModel>>(app.modelCatalogRepository,"_models")
        val allModelFlow=flow<List<NexusModel>>(app.modelCatalogRepository,"_allModels")
        val oldModels=modelFlow.value;val oldAllModels=allModelFlow.value
        val jobsFlow=flow<List<GenerationJob>>(app.database,"_jobsFlow")
        val currencyFlow=flow<CurrencyState>(app.currencyManager,"_state");val originalCurrency=currencyFlow.value
        val fixtures=listOf(
            GenerationJob(id,providerId="atlas_cloud",modelId=model.id,modality=Modality.IMAGE,prompt="Escultura de cerámica azul, luz suave",status=GenerationStatus.COMPLETED,outputs=outputs,actualCostUsd=.05,startedAt=fixtureTime,completedAt=fixtureTime+1000),
            GenerationJob("ui-audit-video",providerId="mock_provider",modelId=videoModel.id,modality=Modality.VIDEO,prompt="Estudio de movimiento",status=GenerationStatus.COMPLETED,outputs=listOf(GenerationOutput("ui-video-output","ui-audit-video",Modality.VIDEO,video,video,"video/mp4",320,240,2f)),actualCostUsd=0.0,startedAt=fixtureTime-30000),
            GenerationJob("ui-audit-audio",providerId="mock_provider",modelId=audioModel.id,modality=Modality.AUDIO,prompt="Tono de estudio",status=GenerationStatus.COMPLETED,outputs=listOf(GenerationOutput("ui-audio-output","ui-audit-audio",Modality.AUDIO,audio,audio,"audio/wav",durationSeconds=2f)),actualCostUsd=0.0,startedAt=fixtureTime-60000),
            GenerationJob("ui-audit-processing",providerJobId="fixture-provider-id",providerId="atlas_cloud",modelId=videoModel.id,modality=Modality.VIDEO,prompt="Plano de un paisaje en movimiento",status=GenerationStatus.PROCESSING,progress=.4f,startedAt=fixtureTime-90000),
            GenerationJob("ui-audit-error",providerId="atlas_cloud",modelId=model.id,modality=Modality.IMAGE,prompt="Luz de tarde",status=GenerationStatus.FAILED,error="Unable to resolve host example.invalid",startedAt=fixtureTime-120000),
            GenerationJob("ui-audit-uncertain",providerId="atlas_cloud",modelId=videoModel.id,modality=Modality.VIDEO,prompt="Envío pendiente de confirmar",status=GenerationStatus.QUEUED,parameters=mapOf("__submission_uncertain" to "true"),startedAt=fixtureTime-150000))
        compose.runOnIdle {
            modelFlow.value=listOf(model,videoModel,audioModel);allModelFlow.value=modelFlow.value
            jobsFlow.value=fixtures+initialJobs.filterNot {job->fixtures.any {it.id==job.id}}
            currencyFlow.value=CurrencyState(rate=.8666,lastUpdated=fixtureTime,preference=CurrencyPreference.EUR_SMART)
        }
        lateinit var canvas: CreateViewModel
        lateinit var assistant: AssistantViewModel
        lateinit var comparison: ComparisonController
        compose.runOnIdle {
            canvas = CreateViewModel(app)
            canvas.loadLocalPreview(model, media)
            canvas.updatePrompt("Escultura de cerámica azul, luz suave")
            assistant = AssistantViewModel(app)
            comparison = canvas.comparison
            comparison.open(canvas.uiState.value)
        }
        val plan = GenerationPlan("ui-audit-plan", ModelRef("mock_provider",model.id), "GPT Image · Edición", "IMAGE",
            prompt="Escultura de cerámica azul, luz suave", params=mapOf("size" to "1024x1024"),
            effectiveParams=mapOf("size" to "1024x1024"), estimate=com.vidadomu.nexus.agent.core.cost.CostEstimate(.05,com.vidadomu.nexus.agent.core.cost.CostConfidence.NEXUS_QUOTE,"Fixture local"), rationale="Conservar la forma y la iluminación de las referencias.", media=media.map {MediaRef("reference",it.uriString,it.mimeType)}, createdAt=1)
        val project = Project("ui-audit-project", "Cerámica", description="Serie visual de estudio", createdAt=1)
        @Suppress("UNCHECKED_CAST")
        val assistantState = AssistantViewModel::class.java.getDeclaredField("_ui").apply { isAccessible=true }
            .get(assistant) as MutableStateFlow<AssistantUiState>
        compose.runOnIdle { assistantState.value = assistant.ui.value.copy(projects=listOf(project), activeProjectId=project.id,
            items=listOf(ChatItem.User("ui-audit-user", "Prepara una imagen con estas referencias", emptyList()),
                ChatItem.Assistant("ui-audit-assistant", "He preparado una propuesta. Revisa el prompt y el coste antes de generar.", emptyList(), ""),
                ChatItem.PlanCard("ui-audit-card",plan.id)), plans=mapOf(plan.id to PlanUi(plan,true,PlanState.PREPARED))) }
        val scene = mutableStateOf("lienzo")
        val scale = mutableFloatStateOf(1f)
        val preferences = AppearancePreferences(app)
        val originalTheme = preferences.theme()
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("audit_phase") ?: "before"
        val themes = args.getString("audit_theme")?.let {listOf(NexusAppearance.valueOf(it))} ?: if(phase=="after") listOf(NexusAppearance.AURORA,NexusAppearance.OLED,NexusAppearance.LIGHT,NexusAppearance.QUARTZ)
            else listOf(NexusAppearance.LIGHT)
        val scales = args.getString("audit_scale")?.let {listOf(it.toFloat())} ?: when(phase) { "before" -> listOf(1f,1.3f); "after" -> listOf(1f,1.3f,1.5f); else -> listOf(1.3f) }
        val scenes=args.getString("audit_scenes")?.split(",")
            ?: listOf("lienzo","historial","modelos","modelo-opciones","biblioteca","visor","asistente","proyecto","ajustes","flow","comparacion","costes")
        val folder = File(app.getExternalFilesDir(null),"ui-audit/$phase").apply { mkdirs() }
        var balanceFlow:MutableStateFlow<List<AccountReading>>?=null
        var originalBalances=listOf<AccountReading>()
        val layoutReport=StringBuilder()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.floatValue)) {
                NexusTheme {
                    val balanceVm:AccountBalanceViewModel=viewModel()
                    SideEffect {
                        if(balanceFlow==null) {
                            val readings=flow<List<AccountReading>>(balanceVm,"readings");balanceFlow=readings;originalBalances=readings.value
                            readings.value=listOf(AccountReading("Atlas Cloud","9.543 USD",fixtureTime),AccountReading("Crun","1622.28 cr",fixtureTime-3600000,true))
                        }
                    }
                    com.vidadomu.nexus.ui.components.NexusChrome({}, {}, settingsVisible=scene.value!="ajustes") {
                    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                        key(scene.value) {
                        when(scene.value) {
                            "lienzo", "parametros", "medios", "saldo" -> CreateScreen({}, {}, canvas)
                            "historial" -> GenerationsScreen({}, {})
                            "modelos" -> ModelsScreen({})
                            "modelo-opciones" -> Scaffold(topBar={com.vidadomu.nexus.ui.components.NexusTopAppBar(title={Text("Modelo",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle())})}) {pad->
                                ModelOptionsScreen(CatalogModelGroup("ui-audit","GPT Image",listOf(model)),emptySet(),{}, {},modifier=Modifier.padding(pad))
                            }
                            "biblioteca" -> LibraryScreen({})
                            "visor" -> OutputViewerScreen(id, {}, {})
                            "asistente", "proyecto", "llm" -> AssistantScreen({}, {}, {}, assistant)
                            "ajustes" -> SettingsScreen({})
                            "flow" -> FlowCreateScreen({}, {})
                            "comparacion" -> ComparisonScreen(comparison, {}, {})
                            "costes" -> Scaffold(topBar={com.vidadomu.nexus.ui.components.NexusTopAppBar(title={Text("Costes")})}) {pad->
                                BillingLedgerScreen(app.generationRepository.allJobs.value,null,app.currencyManager,{}, {},Modifier.fillMaxSize().padding(pad),showBalance=false)
                            }
                        }
                    }
                    }
                    }
                }
            }
        }
        try {
            for(theme in themes) for(fontScale in scales) {
                compose.runOnIdle { preferences.setTheme(theme); scale.floatValue=fontScale }
                for(name in scenes) {
                    compose.runOnIdle { scene.value=name }
                    compose.waitForIdle()
                    if(name=="proyecto") {
                        val old = compose.onAllNodesWithText("Memoria (vacía)")
                        if(old.fetchSemanticsNodes().isNotEmpty()) old[0].performClick()
                        else compose.onNodeWithContentDescription("Abrir proyecto").performClick()
                        compose.waitForIdle()
                    }
                    when(name) {
                        "parametros" -> compose.onNodeWithContentDescription("Parámetros del modelo").performClick()
                        "medios" -> compose.onNodeWithText("Añadir").performClick()
                        "saldo" -> compose.onNodeWithContentDescription("Saldo y gasto").performClick()
                        "llm" -> compose.onNodeWithContentDescription("Ajustes del asistente").performClick()
                    }
                    compose.waitForIdle()
                    // Record actual text layouts for review; intentional one-line summaries remain in the report.
                    compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),useUnmergedTree=true).fetchSemanticsNodes().forEach {node->
                        val layouts=mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                        layouts.filter {it.hasVisualOverflow}.forEach {result->layoutReport.append("${theme.name}-$fontScale-$name: ${result.layoutInput.text.text}\n")}
                    }
                    // UiAutomation captures the presented frame, not Compose's next draw.
                    // Give the emulator compositor time to present the settled screen.
                    android.os.SystemClock.sleep(1000)
                    instrumentation.waitForIdleSync()
                    // Capture the full display, including a dialog/sheet in a separate window.
                    val visiblePackage=instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                    check(visiblePackage==app.packageName) {"Capture blocked by window $visiblePackage for $theme/$fontScale/$name"}
                    var screenshot=instrumentation.uiAutomation.takeScreenshot()
                    repeat(2) {if(screenshot==null) {android.os.SystemClock.sleep(500);screenshot=instrumentation.uiAutomation.takeScreenshot()}}
                    val bitmap = checkNotNull(screenshot) {"No screenshot for $theme/$fontScale/$name"}
                    File(folder,"${theme.name}-${fontScale}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                    if(name=="saldo") {
                        androidx.test.espresso.Espresso.pressBack()
                        compose.waitForIdle()
                        compose.onNodeWithText("Saldo y gasto").assertDoesNotExist()
                    }
                }
            }
            // Captures cannot submit a generation. Existing jobs and provider ids survive unchanged.
            check(initialJobs.all { original -> app.generationRepository.allJobs.value.any { it.id==original.id && it.providerJobId==original.providerJobId } })
        } finally {
            File(folder,"text-overflow-${themes.joinToString {it.name}}-${scales.joinToString()}.txt").writeText(layoutReport.toString())
            preferences.setTheme(originalTheme)
            compose.runOnIdle {modelFlow.value=oldModels;allModelFlow.value=oldAllModels;jobsFlow.value=initialJobs;currencyFlow.value=originalCurrency
                balanceFlow?.value=originalBalances}
        }
    }
}
