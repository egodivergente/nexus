package com.vidadomu.nexus.ui.settings

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onCreateInFlow: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val settingsRepo = app.settingsRepository
    val currencyManager = app.currencyManager
    val scope = rememberCoroutineScope()
    val appearance = rememberAppearancePreferences()
    val selectedTheme by appearance.themeState()
    var defaultSetting by remember {mutableStateOf<String?>(null)}
    defaultSetting?.let { setting ->
        val isAppearance=setting=="Apariencia"
        AlertDialog(onDismissRequest={defaultSetting=null},title={Text(setting)},
            text={Text("Predeterminado: "+if(isAppearance) NexusAppearance.AURORA.label else CurrencyPreference.EUR_SMART.displayName)},
            confirmButton={TextButton(onClick={
                if(isAppearance) appearance.setTheme(NexusAppearance.AURORA) else currencyManager.setPreference(CurrencyPreference.EUR_SMART)
                defaultSetting=null
            }, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Restaurar predeterminado")}},dismissButton={TextButton(onClick={defaultSetting=null}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Cerrar")}})
    }

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
    var accountDetails by remember {mutableStateOf(false)}
    Scaffold(containerColor=ScreenBackground,topBar={TopAppBar(title={Text("Ajustes",style=com.vidadomu.nexus.ui.components.nexusScreenTitleStyle())})}) {padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(NexusTokens.screenMargin),verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            item {Text("Personalización",style=MaterialTheme.typography.titleMedium)}
            item {SettingRow("Apariencia",selectedTheme.label,{appearanceOpen=true},{defaultSetting="Apariencia"})}
            item {SettingRow("Moneda",currencyState.preference.displayName,{currencyOpen=true},{defaultSetting="Moneda"})}
            item {HorizontalDivider();FlowRecoverySettings(onCreateInFlow)}
            item {HorizontalDivider();Text("Cuenta Atlas",style=MaterialTheme.typography.titleMedium)}
            item {Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(accountBalance?.let {currencyManager.formatBalance(it.availableUsd)} ?: "Saldo sin consultar",style=MaterialTheme.typography.titleMedium.tabularMoney(),modifier=Modifier.weight(1f))
                    IconButton(onClick={refreshBalance()},enabled=!isRefreshingBalance) {Icon(Icons.Default.Refresh,"Actualizar saldo Atlas")}
                }
                TextButton(onClick={accountDetails=!accountDetails},colors=ButtonDefaults.textButtonColors(contentColor=TextSecondary), shape=NexusTokens.field) {Text(if(accountDetails) "Ocultar cuenta" else "Detalles de cuenta")}
                if(accountDetails) accountBalance?.let {balance ->
                    Text(currencyManager.formatUsdDetails(balance.availableUsd),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
                    AccountAmount("Efectivo",currencyManager.formatBalance(balance.cashUsd))
                    AccountAmount("Bonos",currencyManager.formatBalance(balance.bonusUsd))
                    AccountAmount("Congelado",currencyManager.formatBalance(balance.frozenUsd))
                    Text("Consumo oficial por uso en Atlas Cloud",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                }
            }}
            item {HorizontalDivider();Text("Proveedores",style=MaterialTheme.typography.titleMedium)}
            item {Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Text("Atlas Cloud",style=MaterialTheme.typography.titleMedium)
                Text("URL base: https://api.atlascloud.ai",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                Text(if(maskedApiKey=="Not configured") "Sin configurar · Clave API (Keystore)" else "Clave API guardada (Keystore)",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
                Text(maskedApiKey.takeUnless {it=="Not configured"} ?: "Sin configurar",style=MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    OutlinedButton(onClick={apiKeyInput=settingsRepo.getAtlasApiKey();showEditKeyDialog=true},colors=ButtonDefaults.outlinedButtonColors(contentColor=TextSecondary), shape=NexusTokens.field) {Text("Modificar clave Atlas")}
                    OutlinedButton(enabled=!isTestingConnection,onClick={
                        isTestingConnection=true;connectionStatusMessage=null
                        scope.launch {
                            val key=settingsRepo.getAtlasApiKey()
                            val result=settingsRepo.testConnection(key)
                            isTestingConnection=false
                            if(result.isSuccess) {isConnectionSuccess=true;connectionStatusMessage="Conexión exitosa con Atlas Cloud."}
                            else {isConnectionSuccess=false;connectionStatusMessage=result.exceptionOrNull()?.message ?: "Fallo de conexión."}
                        }
                    },colors=ButtonDefaults.outlinedButtonColors(contentColor=TextSecondary), shape=NexusTokens.field) {Text(if(isTestingConnection) "Probando…" else "Probar conexión Atlas")}
                }
                connectionStatusMessage?.let {status->InlineNotice(if(isConnectionSuccess) "Conexión correcta." else noticeMessage(status,"No se pudo conectar con Atlas."),technicalDetails=if(isConnectionSuccess) null else status,isError=!isConnectionSuccess)}
            }}
            item {
                @Suppress("UNUSED_EXPRESSION")
                providerKeyRevision
                Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    app.providerRegistry.getAllProviders().filter {it.id in setOf("crun","runway")}.forEach {provider->
                        ProviderCredentialCard(provider,app.secureStorage.getMaskedProviderApiKey(provider.id),{app.secureStorage.getProviderApiKey(provider.id)},
                            {providerKeyDialogId=provider.id;providerKeyInput=app.secureStorage.getProviderApiKey(provider.id)})
                    }
                }
            }
            item {HorizontalDivider();Text("Pruebas y sistema",style=MaterialTheme.typography.titleMedium)}
            item {Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text("Proveedor simulado",style=MaterialTheme.typography.bodyLarge);Text("Generaciones locales de prueba sin consumir créditos de Atlas.",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
                Switch(isMockMode,{checked->isMockMode=checked;settingsRepo.setMockMode(checked);app.modelCatalogRepository.refreshModels()},modifier=Modifier.semantics {contentDescription="Proveedor simulado"})
            }}
            item {SettingRow("Diagnóstico del sistema","Aplicación y dispositivo",{showDiagnostics=true})}
        }
    }
    if(appearanceOpen) ModalBottomSheet(onDismissRequest={appearanceOpen=false},shape=NexusTokens.sheet,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Text("Apariencia",style=MaterialTheme.typography.titleLarge)
            NexusAppearance.entries.forEach {option->Row(Modifier.fillMaxWidth().selectable(selectedTheme==option,role=Role.RadioButton,onClick={appearance.setTheme(option)})
                .heightIn(min=NexusTokens.minimumTouch),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selectedTheme==option,null)
                Column(Modifier.weight(1f)) {Text(option.label,style=MaterialTheme.typography.bodyLarge);Text(option.description,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
            }}
            TextButton(onClick={appearance.setTheme(NexusAppearance.AURORA)}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Restaurar predeterminado",color=TextSecondary)}
            TextButton(onClick={appearanceOpen=false}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Cerrar apariencia",color=TextSecondary)}
        }
    }
    if(currencyOpen) ModalBottomSheet(onDismissRequest={currencyOpen=false},shape=NexusTokens.sheet,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Text("Moneda",style=MaterialTheme.typography.titleLarge)
            CurrencyPreference.entries.forEach {pref->Row(Modifier.fillMaxWidth().combinedClickable(onClick={currencyManager.setPreference(pref)},onLongClick={defaultSetting="Moneda"})
                .heightIn(min=NexusTokens.minimumTouch),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(currencyState.preference==pref,{currencyManager.setPreference(pref)})
                Column(Modifier.weight(1f)) {Text(pref.displayName,style=MaterialTheme.typography.bodyLarge);Text(pref.description,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
            }}
            Text(currencyState.rateSummaryText,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            Text("Fuente: BCE / Frankfurter",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            Text("Última sincronización: ${currencyState.formattedLastUpdated}",style=MaterialTheme.typography.labelMedium,color=TextSecondary)
            TextButton(onClick={currencyDetails=!currencyDetails}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Detalles de conversión",color=TextSecondary)}
            if(currencyDetails) Text("Ejemplo: ${currencyManager.formatUsdDetails(.015)} → ${currencyManager.formatCost(.015)}",style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
            currencyState.error?.let {InlineNotice(noticeMessage(it,"No se pudo actualizar la tasa. Se conserva la guardada."),technicalDetails=it)}
            OutlinedButton(onClick={scope.launch {currencyManager.refreshRate()}},enabled=!currencyState.isUpdating,colors=ButtonDefaults.outlinedButtonColors(contentColor=TextSecondary), shape=NexusTokens.field) {Text(if(currencyState.isUpdating) "Sincronizando…" else "Actualizar tasa")}
            TextButton(onClick={currencyManager.setPreference(CurrencyPreference.EUR_SMART)}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Restaurar predeterminado",color=TextSecondary)}
            TextButton(onClick={currencyOpen=false}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Cerrar moneda",color=TextSecondary)}
        }
    }
    if(showDiagnostics) ModalBottomSheet(onDismissRequest={showDiagnostics=false},shape=NexusTokens.sheet,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.screenMargin).padding(bottom=NexusTokens.space24),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Text("Diagnóstico del sistema",style=MaterialTheme.typography.titleLarge)
            Text("Nexus ${com.vidadomu.nexus.BuildConfig.VERSION_NAME} · ${com.vidadomu.nexus.BuildConfig.VERSION_CODE}",style=MaterialTheme.typography.bodyMedium)
            Text("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}",style=MaterialTheme.typography.bodyMedium)
            Text("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",style=MaterialTheme.typography.bodyMedium)
            Text(if(app.secureStorage.credentialStorageAvailable) "Almacenamiento cifrado disponible" else "Almacenamiento cifrado no disponible",style=MaterialTheme.typography.bodyMedium)
            Text("Media3 ExoPlayer 1.4.1 · WorkManager 2.9",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            Text("Divisas: BCE / Frankfurter",style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
            TextButton(onClick={showDiagnostics=false}, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {Text("Cerrar diagnóstico",color=TextSecondary)}
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
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Clave API") },
                        placeholder = { Text("apikey-...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryNeon,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
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
                , shape=NexusTokens.field) {
                    Text("Guardar clave", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditKeyDialog = false }, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {
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
                    Text("La clave se guarda cifrada en el Keystore de Android.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = providerKeyInput,
                        onValueChange = { providerKeyInput = it },
                        label = { Text("Clave API") },
                        visualTransformation = PasswordVisualTransformation(),
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
                }, shape=NexusTokens.field) { Text("Guardar clave", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { providerKeyDialogId = null }, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Cancelar") } },
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
    onEdit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var testing by remember(provider.id) { mutableStateOf(false) }
    var resultText by remember(provider.id) { mutableStateOf<String?>(null) }
    var success by remember(provider.id) { mutableStateOf(false) }
    var accountStatus by remember(provider.id, maskedKey) { mutableStateOf<com.vidadomu.nexus.provider.api.ProviderAccountStatus?>(null) }
    var loadingBalance by remember(provider.id, maskedKey) { mutableStateOf(false) }
    var balanceError by remember(provider.id, maskedKey) { mutableStateOf<String?>(null) }
    suspend fun refreshBalance() {
        val key = apiKey()
        if(key.isBlank()) return
        val capability = provider.fetchAccountStatus(key) ?: return
        capability.onSuccess { accountStatus = it; balanceError = null }
            .onFailure { balanceError = it.message ?: "No se pudo consultar el saldo." }
    }
    LaunchedEffect(provider.id, maskedKey) {
        if(apiKey().isNotBlank()) {
            loadingBalance = true
            refreshBalance()
            loadingBalance = false
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shape = NexusTokens.card
    ) {
        Column(Modifier.padding(NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TextSecondary)
                Spacer(Modifier.width(NexusTokens.space8))
                Column(Modifier.weight(1f)) {
                    Text(provider.displayName, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(provider.description, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Divider(color = BorderSubtle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(maskedKey.takeUnless {it=="Not configured"} ?: "Sin configurar", modifier = Modifier.weight(1f), color = if (maskedKey == "Not configured") AccentCoral else TextPrimary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                TextButton(onClick = onEdit, shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Modificar") }
            }
            if(accountStatus != null || loadingBalance) {
                Surface(color = PrimaryNeon.copy(alpha = 0.08f), shape = NexusTokens.field,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.28f))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TextSecondary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(NexusTokens.space8))
                        Column(Modifier.weight(1f)) {
                            Text("Saldo", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                            Text(accountStatus?.balanceLabel?.let {presentBalance(it,(LocalContext.current.applicationContext as NexusApplication).currencyManager)} ?: "Consultando…", color = TextPrimary,
                                fontWeight = FontWeight.Bold)
                        }
                        if(loadingBalance) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            balanceError?.let {InlineNotice(noticeMessage(it,"No se pudo consultar el saldo."),technicalDetails=it)}
            OutlinedButton(
                onClick = {
                    testing = true
                    resultText = null
                    scope.launch {
                        val result = provider.testConnection(apiKey())
                        testing = false
                        success = result.isSuccess
                        resultText = if (result.isSuccess) "Conexión correcta." else result.exceptionOrNull()?.message ?: "Fallo de conexión."
                        if(result.isSuccess) refreshBalance()
                    }
                },
                enabled = !testing && apiKey().isNotBlank()
            , shape=NexusTokens.field, colors=androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(NexusTokens.space4))
                Text(if (testing) "Probando…" else "Probar conexión")
            }
            resultText?.let {InlineNotice(if(success) "Conexión correcta." else noticeMessage(it,"No se pudo conectar con el proveedor."),technicalDetails=if(success) null else it,isError=!success)}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingRow(title:String,subtitle:String,onClick:()->Unit,onLongClick:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch).combinedClickable(role=Role.Button,onClick=onClick,onLongClick=onLongClick)
        .padding(vertical=NexusTokens.space8),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=TextSecondary)}
        Icon(Icons.Default.ChevronRight,null,tint=TextSecondary)
    }
}

@Composable
private fun AccountAmount(label:String,amount:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=TextSecondary)
        Text(amount,style=MaterialTheme.typography.bodyMedium.tabularMoney())
    }
}
