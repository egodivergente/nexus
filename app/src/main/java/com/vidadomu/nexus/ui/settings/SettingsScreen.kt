package com.vidadomu.nexus.ui.settings

import com.vidadomu.nexus.ui.components.NexusSheet
import com.vidadomu.nexus.ui.components.NexusSheetGroup
import com.vidadomu.nexus.ui.components.StudioCredentialField
import com.vidadomu.nexus.ui.components.NexusTopAppBar as TopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.components.presentBalance
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyPreference
import com.vidadomu.nexus.core.model.AccountBalance
import java.util.Locale
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onCreateInFlow: () -> Unit = {}, assistantViewModel: com.vidadomu.nexus.assistant.AssistantViewModel? = null,
    initialPage: String? = null, onCloseInitialPage: (() -> Unit)? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val settingsRepo = app.settingsRepository
    val currencyManager = app.currencyManager
    val scope = rememberCoroutineScope()
    val appearance = rememberAppearancePreferences()
    val selectedTheme by appearance.themeState()
    val currencyState by currencyManager.state.collectAsState()

    var accountBalance by remember { mutableStateOf<AccountBalance?>(app.secureStorage.getCachedBalance()) }
    var syncOpen by remember { mutableStateOf(false) }
    var isRefreshingBalance by remember { mutableStateOf(false) }
    var atlasCheck by remember { mutableStateOf<ProviderCheck>(ProviderCheck.NotChecked) }

    fun refreshBalance() {
        scope.launch {
            isRefreshingBalance = true
            val res = app.atlasProvider.fetchBalance()
            val at = System.currentTimeMillis()
            if (res.isSuccess) {
                accountBalance = res.getOrNull()
                atlasCheck = ProviderCheck.Succeeded(at)
            } else {
                atlasCheck = ProviderCheck.Failed(at, noticeMessage(res.exceptionOrNull()?.message, "No se pudo consultar el saldo."))
            }
            isRefreshingBalance = false
        }
    }

    LaunchedEffect(Unit) {
        refreshBalance()
    }

    var maskedApiKey by remember { mutableStateOf(settingsRepo.getMaskedAtlasApiKey()) }
    var showEditKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var providerKeyDialogId by remember { mutableStateOf<String?>(null) }
    var providerKeyInput by remember { mutableStateOf("") }
    var providerKeyRevision by remember { mutableIntStateOf(0) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionStatusMessage by remember { mutableStateOf<String?>(null) }
    var isConnectionSuccess by remember { mutableStateOf(false) }

    var isMockMode by remember { mutableStateOf(settingsRepo.isMockModeEnabled()) }
    var showDiagnostics by remember { mutableStateOf(false) }

    var appearanceOpen by remember {mutableStateOf(false)}
    var currencyOpen by remember {mutableStateOf(false)}
    var currencyDetails by remember {mutableStateOf(false)}
    var settingsSearch by rememberSaveable {mutableStateOf("")}
    var pageName by rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    val closePage: () -> Unit = {if(initialPage!=null && onCloseInitialPage!=null) onCloseInitialPage() else pageName=null}
    val page = pageName?.let { n -> SettingsPage.entries.firstOrNull { it.name == n } }
    var assistantSettingsOpen by remember { mutableStateOf(false) }
    var advancedAppearance by rememberSaveable {mutableStateOf(false)}
    androidx.activity.compose.BackHandler(enabled = page != null) { closePage() }
    val keysConfigured = remember(maskedApiKey, providerKeyRevision) {
        listOf(maskedApiKey != "Not configured", app.secureStorage.getMaskedProviderApiKey("crun") != "Not configured", app.secureStorage.getMaskedProviderApiKey("runway") != "Not configured").count { it }
    }
    val flowLinked = remember(providerKeyRevision) { app.secureStorage.getProviderApiKey(FLOW_RECOVERY_KEY).isNotBlank() }
    Scaffold(containerColor=ScreenBackground,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) { page?.let { CategoryTile(it.icon,it.tint) }; Text(page?.title ?: "Ajustes",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle()) }},
        showBalance=page != SettingsPage.BYTEPLUS,
        navigationIcon={ if(page != null) IconButton(onClick=closePage) {Icon(Icons.AutoMirrored.Filled.ArrowBack,if(initialPage!=null) "Volver al Lienzo" else "Volver a Ajustes")} })}) {padding ->
        if(page in setOf(SettingsPage.RULES,SettingsPage.PROJECTS,SettingsPage.CATALOG,SettingsPage.AUTOMATION) || page==SettingsPage.VISUAL && advancedAppearance) {
            Box(Modifier.padding(padding)) {com.vidadomu.nexus.settings.RuntimeSettingsEditor(when(page) {SettingsPage.PROJECTS->"Proyectos y conversaciones";SettingsPage.CATALOG->"Catálogo y workflows";SettingsPage.AUTOMATION->"Automatización";SettingsPage.VISUAL->"Personalización";else->null})}
        } else LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            when(page) {
                SettingsPage.RULES, SettingsPage.PROJECTS, SettingsPage.CATALOG, SettingsPage.AUTOMATION -> Unit
                SettingsPage.VISUAL -> item {SettingsCard {AppearanceControls {advancedAppearance=true}}}
                null -> {
                    item {com.vidadomu.nexus.ui.components.StudioTextField(settingsSearch,{settingsSearch=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Buscar en Ajustes")},leadingIcon={Icon(Icons.Default.Search,null)})}
                    val sections=listOf(
                        SettingsPage.APPEARANCE to "Tema, tipografía, fondo, movimiento y háptica",
                        SettingsPage.PROJECTS to "Inicio, conversaciones y destino al compartir",
                        SettingsPage.AGENT to "Modelo, memoria, herramientas y consumo",
                        SettingsPage.CATALOG to "Densidad, antigüedad y orden de workflows",
                        SettingsPage.PROVIDERS to "Cuentas, BytePlus y generadores propios",
                        SettingsPage.CONNECTIONS to "Flow, dispositivos y sincronización",
                        SettingsPage.AUTOMATION to "Concurrencia y ejecución",
                        SettingsPage.RULES to "Preferencias por ámbito, exportar e importar",
                        SettingsPage.SECURITY to "Permisos y almacenamiento privado",
                        SettingsPage.SYSTEM to "Diagnóstico y administración")
                    sections.filter {settingsSearch.isBlank() || "${it.first.title} ${it.second}".contains(settingsSearch,true)}.forEach {(target,summary)->
                        item {HubRow(target,summary) {pageName=target.name}}
                    }
                    if(settingsSearch.isNotBlank()) item {TextButton(onClick={pageName=SettingsPage.RULES.name}) {Text("Buscar también en todas las reglas")}}
                }
                SettingsPage.CONNECTIONS -> {
                    item {SettingsCard {FlowRecoverySettings(onCreateInFlow)}}
                    item {SettingsCard {com.vidadomu.nexus.sync.DriveSyncSettings()}}
                    item {SettingsCard {SettingRow("Sincronización entre dispositivos","Chats, memoria y archivos",{syncOpen=true})}}
                }
                SettingsPage.SECURITY -> {
                    item {SettingsCard {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Permisos de Android",style=MaterialTheme.typography.titleMedium)
                        Text("Micrófono, notificaciones y acceso a archivos se gestionan desde el sistema.",style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}) {Text("Abrir permisos de Nexus")}
                        Text("Las credenciales se conservan en el almacén privado y no se incluyen en la exportación de ajustes.",style=MaterialTheme.typography.bodySmall)
                    }}}
                }
                SettingsPage.APPEARANCE -> {
                    item {HubRow(SettingsPage.VISUAL,"Fondo, foto propia, movimiento y háptica") {pageName=SettingsPage.VISUAL.name}}
            item {SettingsCard {
                SettingRow("Apariencia",selectedTheme.label,{appearanceOpen=true})
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                FontScaleRow(appearance)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("Moneda",currencyState.preference.displayName,{currencyOpen=true})
            }}
                }
                SettingsPage.BYTEPLUS -> { item { BytePlusConsumptionSettings() } }
                SettingsPage.AGENT -> {
                    item {SettingsCard {com.vidadomu.nexus.sync.DriveSyncSettings()}}
                    item {SettingsCard {SettingRow("Sincronización entre móviles","Chats, memoria, skills, galería y adjuntos",{syncOpen=true})}}
                    if(assistantViewModel != null) item {SettingsCard {SettingRow("Ajustes del asistente","Proveedor, modelo, respuesta y herramientas",{assistantSettingsOpen=true})}}
                    item {SettingsHeader("Cerebros adicionales",Icons.Default.AutoAwesome,top=NexusTokens.space12)}
                    item {SettingsCard {BrainsSettingsSection()}}
                }
                SettingsPage.PROVIDERS -> {
                    item {HubRow(SettingsPage.BYTEPLUS,"Tokens, facturación y paquetes gratuitos") {pageName=SettingsPage.BYTEPLUS.name}}
                    item {HubRow(SettingsPage.CUSTOM,"BytePlus y otros generadores por API") {pageName=SettingsPage.CUSTOM.name}}
                    item {val atlasPill=providerPill(maskedApiKey!="Not configured",atlasCheck)
                        ProviderCard(name="Atlas Cloud",subtitle="Imagen y vídeo · consumo oficial por uso",icon=Icons.Default.CloudQueue,tint=ProviderTint,connected=atlasPill.healthy,idleLabel=atlasPill.label) {
                        ProviderKeyRow(maskedApiKey.takeUnless{it=="Not configured"},{apiKeyInput=settingsRepo.getAtlasApiKey();showEditKeyDialog=true},{app.secureStorage.clearProviderApiKey("atlas_cloud");maskedApiKey=settingsRepo.getMaskedAtlasApiKey();accountBalance=null;atlasCheck=ProviderCheck.NotChecked})
                        ProviderBalance(accountBalance?.let{currencyManager.formatBalance(it.availableUsd)} ?: "Sin consultar",accountBalance?.let{"${currencyManager.formatUsdDetails(it.availableUsd)} · Efectivo ${currencyManager.formatBalance(it.cashUsd)} · Bonos ${currencyManager.formatBalance(it.bonusUsd)} · ${readingAge(it.lastUpdated,System.currentTimeMillis(),java.time.ZoneId.systemDefault())}"},isRefreshingBalance,{refreshBalance()})
                        (atlasCheck as? ProviderCheck.Failed)?.takeIf {connectionStatusMessage==null}?.let {InlineNotice(it.message)}
                        ProviderTest(enabled=maskedApiKey!="Not configured",testing=isTestingConnection,result=connectionStatusMessage?.let{isConnectionSuccess to it},onTest={
                            isTestingConnection=true;connectionStatusMessage=null
                            scope.launch {
                                val result=settingsRepo.testConnection(settingsRepo.getAtlasApiKey())
                                isTestingConnection=false
                                if(result.isSuccess) {isConnectionSuccess=true;connectionStatusMessage="Conexión exitosa con Atlas Cloud.";atlasCheck=ProviderCheck.Succeeded(System.currentTimeMillis())}
                                else {isConnectionSuccess=false;connectionStatusMessage=result.exceptionOrNull()?.message ?: "Fallo de conexión.";atlasCheck=ProviderCheck.Failed(System.currentTimeMillis(),noticeMessage(connectionStatusMessage,"Fallo de conexión."))}
                            }
                        })
                    }}
                    item {
                        @Suppress("UNUSED_EXPRESSION")
                        providerKeyRevision
                        Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                            app.providerRegistry.getAllProviders().filter {it.id in setOf("crun","runway")}.forEach {provider->
                                ProviderCredentialCard(provider,app.secureStorage.getMaskedProviderApiKey(provider.id),{app.secureStorage.getProviderApiKey(provider.id)},
                                    {providerKeyDialogId=provider.id;providerKeyInput=app.secureStorage.getProviderApiKey(provider.id)},
                                    {app.secureStorage.clearProviderApiKey(provider.id);providerKeyRevision++})
                            }
                        }
                    }
                    item {val flowState=flowPill(flowLinked)
                        ProviderCard(name="Google Flow",subtitle="Proyecto activo del puente (AXELPC o ASUS)",icon=Icons.Default.AutoAwesome,tint=ProviderTint,connected=flowState.healthy,idleLabel=flowState.label) {
                        FlowRecoverySettings(onCreateInFlow)
                    }}
                }
                SettingsPage.CUSTOM -> {
            item {SettingsCard {MediaProvidersSettingsSection()}}
                }
                SettingsPage.SYSTEM -> {
            item {SettingsCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("Proveedor simulado",style=MaterialTheme.typography.bodyLarge);Text("Generaciones locales de prueba sin consumir créditos.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                    Switch(isMockMode,{checked->isMockMode=checked;settingsRepo.setMockMode(checked);app.modelCatalogRepository.refreshModels()},modifier=Modifier.semantics {contentDescription="Proveedor simulado"})
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("Diagnóstico del sistema","Nexus ${com.vidadomu.nexus.BuildConfig.VERSION_NAME} «${com.vidadomu.nexus.BuildConfig.CODENAME}»",{showDiagnostics=true})
                Text("Compilación ${com.vidadomu.nexus.BuildConfig.VERSION_CODE} · ${com.vidadomu.nexus.BuildConfig.BUILD_TYPE}\n${com.vidadomu.nexus.BuildConfig.SOURCE_REVISION} · ${com.vidadomu.nexus.BuildConfig.BUILD_DATE}",style=MaterialTheme.typography.labelSmall,color=TextSecondary)
            }}
                }
            }
        }
    }
    if(syncOpen) DeviceSyncSettings {syncOpen=false}
    if(assistantSettingsOpen && assistantViewModel != null) com.vidadomu.nexus.assistant.AssistantSettingsSheetHost(assistantViewModel) {assistantSettingsOpen=false}
    if(appearanceOpen) NexusSheet("Apariencia", {appearanceOpen=false}, icon=Icons.Default.Palette) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            NexusSheetGroup("Tema", Icons.Default.Palette) {
            NexusAppearance.entries.forEach {option->
                val preview = remember(option) { palette(option) }
                Row(Modifier.fillMaxWidth().selectable(selectedTheme==option,role=Role.RadioButton,onClick={appearance.setTheme(option)})
                    .heightIn(min=NexusTokens.minimumTouch),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selectedTheme==option,null)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                        Text(option.label,style=MaterialTheme.typography.bodyLarge)
                        Text(option.description,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
                        Surface(shape=NexusTokens.field,color=preview.background) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space8,vertical=NexusTokens.space4),
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                                Surface(shape=NexusTokens.thumbnail,color=preview.surface,modifier=Modifier.size(22.dp)) {}
                                Surface(shape=NexusTokens.thumbnail,color=preview.primary,modifier=Modifier.size(22.dp)) {}
                                Text("Aa",style=MaterialTheme.typography.labelMedium,color=preview.onBackground)
                                Spacer(Modifier.weight(1f))
                                Text("Vista previa",style=MaterialTheme.typography.labelSmall,color=preview.onBackground.copy(alpha=.72f))
                            }
                        }
                    }
                }
            }
            TextButton(onClick={appearance.setTheme(NexusAppearance.AURORA)}) {Text("Restaurar predeterminado",color=TextSecondary)}
            }
        }
    }
    if(currencyOpen) NexusSheet("Moneda", {currencyOpen=false}, icon=Icons.Default.CurrencyExchange) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            NexusSheetGroup("Preferencia de moneda", Icons.Default.CurrencyExchange) {
            CurrencyPreference.entries.forEach {pref->Row(Modifier.fillMaxWidth().clickable { currencyManager.setPreference(pref) }
                .heightIn(min=NexusTokens.minimumTouch),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(currencyState.preference==pref,{currencyManager.setPreference(pref)})
                Column(Modifier.weight(1f)) {Text(pref.displayName,style=MaterialTheme.typography.bodyLarge);Text(pref.description,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
            }}
            }
            NexusSheetGroup("Conversión", Icons.Default.Sync) {
            Text(currencyState.rateSummaryText,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            Text("Fuente: BCE / Frankfurter",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            Text("Última sincronización: ${currencyState.formattedLastUpdated}",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            TextButton(onClick={currencyDetails=!currencyDetails}) {Text("Detalles de conversión",color=TextSecondary)}
            if(currencyDetails) Text("Ejemplo: ${currencyManager.formatUsdDetails(.015)} → ${currencyManager.formatCost(.015)}",style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
            currencyState.error?.let {InlineNotice(noticeMessage(it,"No se pudo actualizar la tasa. Se conserva la guardada."),technicalDetails=it)}
            FilledTonalButton(onClick={scope.launch {currencyManager.refreshRate()}},enabled=!currencyState.isUpdating,colors=ButtonDefaults.filledTonalButtonColors(contentColor=TextSecondary)) {Text(if(currencyState.isUpdating) "Sincronizando…" else "Actualizar tasa")}
            TextButton(onClick={currencyManager.setPreference(CurrencyPreference.EUR_SMART)}) {Text("Restaurar predeterminado",color=TextSecondary)}
            }
        }
    }
    if(showDiagnostics) NexusSheet("Diagnóstico del sistema", {showDiagnostics=false}, icon=Icons.Default.Info) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            NexusSheetGroup("Estado del dispositivo", Icons.Default.Info) {
            Text("Nexus ${com.vidadomu.nexus.BuildConfig.VERSION_NAME} «${com.vidadomu.nexus.BuildConfig.CODENAME}» · ${com.vidadomu.nexus.BuildConfig.VERSION_CODE}",style=MaterialTheme.typography.bodyMedium)
            Text("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}",style=MaterialTheme.typography.bodyMedium)
            Text("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",style=MaterialTheme.typography.bodyMedium)
            Text(if(app.secureStorage.credentialStorageAvailable) "Almacenamiento cifrado disponible" else "Almacenamiento cifrado no disponible",style=MaterialTheme.typography.bodyMedium)
            Text("Media3 ExoPlayer 1.4.1 · WorkManager 2.9",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            Text("Divisas: BCE / Frankfurter",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            }
        }
    }

    // Diálogo de configuración de API Key
    if (showEditKeyDialog) {
        AlertDialog(
            onDismissRequest = { showEditKeyDialog = false },
            title = {
                Text(
                    text = "Configurar Clave API de Atlas",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    Text(
                        text = "Tu clave API se almacena cifrada con AES256-GCM respaldada por el Keystore de Android.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                    StudioCredentialField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Clave API") },
                        placeholder = { Text("apikey-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    , shape=NexusTokens.field)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if(!app.secureStorage.credentialStorageAvailable) {
                            android.widget.Toast.makeText(context,"Almacenamiento seguro no disponible. No se ha guardado la clave.",android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        settingsRepo.saveAtlasApiKey(apiKeyInput.trim())
                        maskedApiKey = settingsRepo.getMaskedAtlasApiKey()
                        atlasCheck = ProviderCheck.NotChecked
                        showEditKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Guardar clave", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditKeyDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            tonalElevation = 8.dp,
            shape = NexusTokens.card
        )
    }

    providerKeyDialogId?.let { providerId ->
        val provider = app.providerRegistry.getProviderById(providerId)
        AlertDialog(
            onDismissRequest = { providerKeyDialogId = null },
            title = { Text("Configurar clave de ${provider?.displayName ?: providerId}", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    Text("La clave se guarda cifrada en el Keystore de Android.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    StudioCredentialField(
                        value = providerKeyInput,
                        onValueChange = { providerKeyInput = it },
                        label = { Text("Clave API") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    , shape=NexusTokens.field)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if(!app.secureStorage.credentialStorageAvailable) {
                        android.widget.Toast.makeText(context,"Almacenamiento seguro no disponible. No se ha guardado la clave.",android.widget.Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    app.secureStorage.saveProviderApiKey(providerId, providerKeyInput.trim())
                    providerKeyRevision++
                    providerKeyDialogId = null
                }) { Text("Guardar clave", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { providerKeyDialogId = null }) { Text("Cancelar") } },
            containerColor = SurfaceDark,
            shape = NexusTokens.card
        )
    }
}

@Composable
private fun ProviderCredentialCard(
    provider: com.vidadomu.nexus.provider.api.NexusProvider,
    maskedKey: String,
    apiKey: () -> String,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val scope = rememberCoroutineScope()
    var testing by remember(provider.id) { mutableStateOf(false) }
    var resultText by remember(provider.id) { mutableStateOf<String?>(null) }
    var success by remember(provider.id) { mutableStateOf(false) }
    var accountStatus by remember(provider.id, maskedKey) { mutableStateOf<com.vidadomu.nexus.provider.api.ProviderAccountStatus?>(null) }
    var loadingBalance by remember(provider.id, maskedKey) { mutableStateOf(false) }
    var balanceError by remember(provider.id, maskedKey) { mutableStateOf<String?>(null) }
    var lastCheck by remember(provider.id, maskedKey) { mutableStateOf<ProviderCheck>(ProviderCheck.NotChecked) }
    suspend fun refreshBalance() {
        val key = apiKey()
        if (key.isBlank()) return
        val capability = provider.fetchAccountStatus(key) ?: return
        capability.onSuccess { accountStatus = it; balanceError = null; lastCheck = ProviderCheck.Succeeded(System.currentTimeMillis()) }
            .onFailure { balanceError = it.message ?: "No se pudo consultar el saldo."; lastCheck = ProviderCheck.Failed(System.currentTimeMillis(), noticeMessage(balanceError, "No se pudo consultar el saldo.")) }
    }
    LaunchedEffect(provider.id, maskedKey) {
        if (apiKey().isNotBlank()) {
            loadingBalance = true
            refreshBalance()
            loadingBalance = false
        }
    }
    val hasKey = maskedKey != "Not configured"
    val pill = providerPill(hasKey, lastCheck)
    ProviderCard(name = provider.displayName, subtitle = provider.description, icon = Icons.Default.CloudQueue,
        tint = ProviderTint, connected = pill.healthy, idleLabel = pill.label) {
        ProviderKeyRow(if (hasKey) maskedKey else null, onEdit, if (hasKey) onClear else null)
        if (accountStatus != null || loadingBalance) ProviderBalance(
            value = accountStatus?.balanceLabel?.let { presentBalance(it, app.currencyManager) } ?: "Consultando…",
            detail = accountStatus?.details,
            busy = loadingBalance,
            onRefresh = { scope.launch { loadingBalance = true; refreshBalance(); loadingBalance = false } })
        balanceError?.let { InlineNotice(noticeMessage(it, "No se pudo consultar el saldo."), technicalDetails = it) }
        ProviderTest(enabled = hasKey, testing = testing, result = resultText?.let { success to it }, onTest = {
            testing = true
            resultText = null
            scope.launch {
                val result = provider.testConnection(apiKey())
                testing = false
                success = result.isSuccess
                resultText = if (result.isSuccess) "Conexión correcta." else result.exceptionOrNull()?.message ?: "Fallo de conexión."
                lastCheck = if (result.isSuccess) ProviderCheck.Succeeded(System.currentTimeMillis())
                    else ProviderCheck.Failed(System.currentTimeMillis(), noticeMessage(resultText, "Fallo de conexión."))
                if (result.isSuccess) refreshBalance()
            }
        })
    }
}
private enum class SettingsPage(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: androidx.compose.ui.graphics.Color) {
    CATALOG("Catálogo y workflows", Icons.Default.GridView, androidx.compose.ui.graphics.Color(0xFF68DFE0)),
    CONNECTIONS("Conexiones", Icons.Default.Cable, androidx.compose.ui.graphics.Color(0xFF34D399)),
    AUTOMATION("Automatización", Icons.Default.PlayCircleOutline, androidx.compose.ui.graphics.Color(0xFFA78BFA)),
    SECURITY("Seguridad", Icons.Default.Security, androidx.compose.ui.graphics.Color(0xFFFBBF24)),
    VISUAL("Fondo, movimiento y háptica", Icons.Default.Palette, androidx.compose.ui.graphics.Color(0xFFA78BFA)),
    RULES("Reglas y límites", Icons.Default.Tune, androidx.compose.ui.graphics.Color(0xFF4EBAE8)),
    PROJECTS("Proyectos y conversaciones", Icons.Default.FolderOpen, androidx.compose.ui.graphics.Color(0xFF34D399)),
    APPEARANCE("Personalización", Icons.Default.Palette, androidx.compose.ui.graphics.Color(0xFFA78BFA)),
    BYTEPLUS("Consumo BytePlus", Icons.Default.AccountBalanceWallet, androidx.compose.ui.graphics.Color(0xFF4EBAE8)),
    AGENT("Agentes y memoria", Icons.Default.AutoAwesome, androidx.compose.ui.graphics.Color(0xFF4EBAE8)),
    PROVIDERS("Proveedores de generación", Icons.Default.CloudQueue, androidx.compose.ui.graphics.Color(0xFF34D399)),
    CUSTOM("Generadores propios", Icons.Default.Extension, androidx.compose.ui.graphics.Color(0xFFFBBF24)),
    SYSTEM("Sistema y diagnóstico", Icons.Default.Settings, androidx.compose.ui.graphics.Color(0xFF94A3B8)),
}

/** Fila de categoría de Ajustes: baldosa del color de su categoría, título y resumen de su estado. */
@Composable
private fun HubRow(page: SettingsPage, summary: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = NexusTokens.minimumTouch + NexusTokens.space16).padding(NexusTokens.space12),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            CategoryTile(page.icon, page.tint)
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)
            }
            Icon(Icons.Default.ChevronRight, null, tint = page.tint)
        }
    }
}
@Composable
private fun SettingRow(title:String,subtitle:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch).clickable(role=Role.Button,onClick=onClick)
        .padding(vertical=NexusTokens.space8),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
        Icon(Icons.Default.ChevronRight,null,tint=TextSecondary)
    }
}

@Composable
private fun SettingsHeader(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,top:androidx.compose.ui.unit.Dp=0.dp) {
    Row(Modifier.fillMaxWidth().padding(top=top,start=NexusTokens.space4),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
        Text(title,style=MaterialTheme.typography.titleLarge,color=TextPrimary)
    }
}

/** Contenedor de una sección: tarjeta elevada sobre el fondo, con sus filas separadas por filetes. */
@Composable
internal fun SettingsCard(content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=NexusTokens.card,color=MaterialTheme.colorScheme.surfaceContainerHigh,tonalElevation=2.dp) {
        Column(Modifier.padding(horizontal=NexusTokens.space16,vertical=NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8),content=content)
    }
}

/** Tamaño de letra de toda la app; se aplica al soltar el control. */
@Composable
private fun FontScaleRow(appearance:AppearancePreferences) {
    val saved by appearance.fontScaleState()
    var draft by remember(saved) {mutableStateOf(saved)}
    Column(Modifier.fillMaxWidth().padding(vertical=NexusTokens.space4)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Tamaño de letra",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            Text("${(draft*100).toInt()} %",style=MaterialTheme.typography.labelLarge.tabularMoney(),color=TextSecondary)
            TextButton(onClick={draft=1f;appearance.setFontScale(1f)},enabled=saved!=1f||draft!=1f) {Text("Restablecer")}
        }
        Slider(draft,{draft=it},valueRange=.8f..1.4f,steps=11,onValueChangeFinished={appearance.setFontScale(draft)})
        Text("Así se verá el texto de Nexus.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        // Más espacio para el chat: la barra de pestañas se esconde al bajar y vuelve al subir.
        val context=LocalContext.current
        val pinned by com.vidadomu.nexus.ui.theme.ChromeVisibility.pinned
        Row(Modifier.fillMaxWidth().padding(top=NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Navegación siempre visible",style=MaterialTheme.typography.bodyLarge)
                Text("Si la ocultas, desliza hacia arriba desde el borde inferior.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            }
            Switch(pinned,{com.vidadomu.nexus.ui.theme.ChromeVisibility.setPinned(context,it)})
        }
    }
}
