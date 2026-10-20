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
fun SettingsScreen(onCreateInFlow: () -> Unit = {}, assistantViewModel: com.vidadomu.nexus.assistant.AssistantViewModel? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val settingsRepo = app.settingsRepository
    val currencyManager = app.currencyManager
    val scope = rememberCoroutineScope()
    val appearance = rememberAppearancePreferences()
    val selectedTheme by appearance.themeState()
    val currencyState by currencyManager.state.collectAsState()

    var accountBalance by remember { mutableStateOf<AccountBalance?>(app.secureStorage.getCachedBalance()) }
    var isRefreshingBalance by remember { mutableStateOf(false) }

    fun refreshBalance() {
        scope.launch {
            isRefreshingBalance = true
            val res = app.atlasProvider.fetchBalance()
            if (res.isSuccess) {
                accountBalance = res.getOrNull()
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
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { n -> SettingsPage.entries.firstOrNull { it.name == n } }
    var assistantSettingsOpen by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = page != null) { pageName = null }
    val hasAtlasKey = remember(maskedApiKey, providerKeyRevision) { app.secureStorage.hasAtlasApiKey() }
    val keysConfigured = remember(maskedApiKey, providerKeyRevision) {
        listOf(hasAtlasKey, app.secureStorage.hasProviderApiKey("crun"), app.secureStorage.hasProviderApiKey("runway")).count { it }
    }
    val flowLinked = remember(providerKeyRevision) { app.secureStorage.getProviderApiKey(FLOW_RECOVERY_KEY).isNotBlank() }
    Scaffold(containerColor=ScreenBackground,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) { page?.let { CategoryTile(it.icon,it.tint) }; Text(page?.title ?: "Ajustes",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle()) }},
        showBalance=page != SettingsPage.BYTEPLUS,
        navigationIcon={ if(page != null) IconButton(onClick={pageName=null}) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver a Ajustes")} })}) {padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            when(page) {
                null -> {
                    item {HubRow(SettingsPage.APPEARANCE,"${selectedTheme.label} · ${currencyState.preference.displayName}") {pageName=SettingsPage.APPEARANCE.name}}
                    item {HubRow(SettingsPage.BYTEPLUS,"Tokens, facturación y paquetes gratuitos") {pageName=SettingsPage.BYTEPLUS.name}}
                    item {HubRow(SettingsPage.AGENT,"Modelo del asistente, consumo, herramientas y cerebros") {pageName=SettingsPage.AGENT.name}}
                    item {HubRow(SettingsPage.PROVIDERS,"$keysConfigured de 3 con clave · Google Flow") {pageName=SettingsPage.PROVIDERS.name}}
                    item {HubRow(SettingsPage.CUSTOM,"BytePlus y otros generadores por API") {pageName=SettingsPage.CUSTOM.name}}
                    item {HubRow(SettingsPage.SYSTEM,"Nexus ${com.vidadomu.nexus.BuildConfig.VERSION_NAME} «${com.vidadomu.nexus.BuildConfig.CODENAME}»" + (if(isMockMode) " · proveedor simulado activo" else "")) {pageName=SettingsPage.SYSTEM.name}}
                }
                SettingsPage.APPEARANCE -> {
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
                    if(assistantViewModel != null) item {SettingsCard {SettingRow("Ajustes del asistente","Modelo, respuesta, consumo, herramientas y skills",{assistantSettingsOpen=true})}}
                    item {SettingsHeader("Cerebros adicionales",Icons.Default.AutoAwesome,top=NexusTokens.space12)}
                    item {SettingsCard {BrainsSettingsSection()}}
                }
                SettingsPage.PROVIDERS -> {
                    item {ProviderCard(name="Atlas Cloud",subtitle="Imagen y vídeo · consumo oficial por uso",icon=Icons.Default.CloudQueue,tint=ProviderTint,connected=hasAtlasKey) {
                        ProviderKeyRow(maskedApiKey.takeIf{hasAtlasKey},{apiKeyInput=settingsRepo.getAtlasApiKey();showEditKeyDialog=true},{app.secureStorage.clearProviderApiKey("atlas_cloud");maskedApiKey=settingsRepo.getMaskedAtlasApiKey();accountBalance=null})
                        ProviderBalance(accountBalance?.let{currencyManager.formatBalance(it.availableUsd)} ?: "Sin consultar",accountBalance?.let{"${currencyManager.formatUsdDetails(it.availableUsd)} · Efectivo ${currencyManager.formatBalance(it.cashUsd)} · Bonos ${currencyManager.formatBalance(it.bonusUsd)}"},isRefreshingBalance,{refreshBalance()})
                        ProviderTest(enabled=hasAtlasKey,testing=isTestingConnection,result=connectionStatusMessage?.let{isConnectionSuccess to it},onTest={
                            isTestingConnection=true;connectionStatusMessage=null
                            scope.launch {
                                val result=settingsRepo.testConnection(settingsRepo.getAtlasApiKey())
                                isTestingConnection=false
                                if(result.isSuccess) {isConnectionSuccess=true;connectionStatusMessage="Conexión exitosa con Atlas Cloud."}
                                else {isConnectionSuccess=false;connectionStatusMessage=result.exceptionOrNull()?.message ?: "Fallo de conexión."}
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
                    item {ProviderCard(name="Google Flow",subtitle="Proyecto activo del puente (AXELPC o ASUS)",icon=Icons.Default.AutoAwesome,tint=ProviderTint,connected=flowLinked,idleLabel="Sin vincular") {
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
            }}
                }
            }
        }
    }
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
    suspend fun refreshBalance() {
        val key = apiKey()
        if (key.isBlank()) return
        val capability = provider.fetchAccountStatus(key) ?: return
        capability.onSuccess { accountStatus = it; balanceError = null }
            .onFailure { balanceError = it.message ?: "No se pudo consultar el saldo." }
    }
    LaunchedEffect(provider.id, maskedKey) {
        if (apiKey().isNotBlank()) {
            loadingBalance = true
            refreshBalance()
            loadingBalance = false
        }
    }
    val hasKey = apiKey().isNotBlank() && maskedKey != "Not configured"
    ProviderCard(name = provider.displayName, subtitle = provider.description, icon = Icons.Default.CloudQueue,
        tint = ProviderTint, connected = hasKey) {
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
                if (result.isSuccess) refreshBalance()
            }
        })
    }
}
private enum class SettingsPage(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: androidx.compose.ui.graphics.Color) {
    APPEARANCE("Apariencia y moneda", Icons.Default.Palette, androidx.compose.ui.graphics.Color(0xFFA78BFA)),
    BYTEPLUS("Consumo BytePlus", Icons.Default.AccountBalanceWallet, androidx.compose.ui.graphics.Color(0xFF4EBAE8)),
    AGENT("Agente y modelos", Icons.Default.AutoAwesome, androidx.compose.ui.graphics.Color(0xFF4EBAE8)),
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
        var autoHide by remember {mutableStateOf(appearance.autoHideBars())}
        Row(Modifier.fillMaxWidth().padding(top=NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ocultar la barra al bajar por el chat",style=MaterialTheme.typography.bodyLarge)
                Text("Vuelve en cuanto subes un poco.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            }
            Switch(autoHide,{autoHide=it;appearance.setAutoHideBars(it)})
        }
    }
}
