package com.vidadomu.nexus.ui.settings

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen() {
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
            }) {Text("Restaurar predeterminado")}},dismissButton={TextButton(onClick={defaultSetting=null}) {Text("Cerrar")}})
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

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ajustes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.sp,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Apariencia, cuentas y preferencias",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp)
        ) {
            item { FlowRecoverySettings() }
            item {
                Text("Apariencia", modifier=Modifier.fillMaxWidth().combinedClickable(onClick={defaultSetting="Apariencia"},onLongClick={defaultSetting="Apariencia"}).padding(vertical=8.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Surface(color=SurfaceDark, shape=RoundedCornerShape(20.dp), shadowElevation=1.dp) {
                    Column(Modifier.selectableGroup()) {
                        NexusAppearance.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().selectable(selectedTheme==option,role=Role.RadioButton,onClick={appearance.setTheme(option)}).padding(12.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selectedTheme==option, onClick=null)
                                Column(Modifier.padding(start=8.dp)) {
                                    Text(option.label, style=MaterialTheme.typography.titleMedium)
                                    Text(option.description, style=MaterialTheme.typography.bodySmall, color=TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            // Sección 0: Cuenta y Saldo Atlas Cloud
            item {
                Text(
                    text = "CUENTA Y SALDO ATLAS CLOUD",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon,
                        letterSpacing = 1.sp
                    )
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CyanNeon.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Saldo Disponible Real",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "Consumo oficial por uso en Atlas Cloud",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        val availUsd = accountBalance?.availableUsd ?: 0.0
                        val availEurStr = currencyManager.formatBalance(availUsd)
                        val availUsdStr = "(\$" + String.format(Locale.US, "%.2f", availUsd) + " USD)"

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = availEurStr,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = availUsdStr,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Efectivo (Cash)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(currencyManager.formatBalance(accountBalance?.cashUsd ?: 0.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Column {
                                Text("Bonos", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(currencyManager.formatBalance(accountBalance?.bonusUsd ?: 0.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Column {
                                Text("Congelado", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(currencyManager.formatBalance(accountBalance?.frozenUsd ?: 0.0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }

                        Button(
                            onClick = { refreshBalance() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRefreshingBalance) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Consultando saldo...", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Actualizar Saldo en Vivo", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Sección 1: Moneda y Tipo de Cambio
            item {
                Text(
                    text = "MONEDA Y TIPO DE CAMBIO",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon,
                        letterSpacing = 1.sp
                    )
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryNeon.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Euro,
                                    contentDescription = null,
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Divisa de Visualización",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = currencyState.rateSummaryText,
                                    style = MaterialTheme.typography.bodySmall.copy(color = AccentEmerald)
                                )
                            }
                        }

                        Text(
                            text = "Preferencia de visualización:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        // Selector de tipo de divisa
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CurrencyPreference.values().forEach { pref ->
                                val isSelected = currencyState.preference == pref
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(onClick={currencyManager.setPreference(pref)},onLongClick={defaultSetting="Moneda"}),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) PrimaryNeon.copy(alpha = 0.12f) else SurfaceVariantDark,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) PrimaryNeon else BorderSubtle
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { currencyManager.setPreference(pref) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = PrimaryNeon,
                                                unselectedColor = TextMuted
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = pref.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) PrimaryNeon else TextPrimary
                                                )
                                            )
                                            Text(
                                                text = pref.description,
                                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Detalle del tipo de cambio y botón de actualización
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Fuente: Banco Central Europeo (BCE)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            text = "Última sincronización: ${currencyState.formattedLastUpdated}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ejemplo: \$0.015 USD → ${currencyManager.formatUsd(0.015)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = PrimaryNeon,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                currencyManager.refreshRate()
                                            }
                                        },
                                        enabled = !currencyState.isUpdating,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryNeon),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        if (currencyState.isUpdating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = PrimaryNeon
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sincronizando...", style = MaterialTheme.typography.labelSmall)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Actualizar Tasa", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sección 2: Proveedor Principal (Atlas Cloud)
            item {
                Text(
                    text = "PROVEEDOR PRINCIPAL",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon,
                        letterSpacing = 1.sp
                    )
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryNeon.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Atlas Cloud",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "URL base: https://api.atlascloud.ai",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AccentEmerald.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Activo",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Divider(color = BorderSubtle, thickness = 0.8.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Clave API (Seguridad Keystore)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                                )
                                Text(
                                    text = maskedApiKey,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = if (maskedApiKey == "Not configured" || maskedApiKey == "No configurada") AccentCoral else TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Button(
                                onClick = {
                                    apiKeyInput = settingsRepo.getAtlasApiKey()
                                    showEditKeyDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceVariantDark,
                                    contentColor = PrimaryNeon
                                )
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Modificar Clave")
                            }
                        }

                        // Botón Probar Conexión
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isTestingConnection = true
                                    connectionStatusMessage = null
                                    scope.launch {
                                        val key = settingsRepo.getAtlasApiKey()
                                        val result = settingsRepo.testConnection(key)
                                        isTestingConnection = false
                                        if (result.isSuccess) {
                                            isConnectionSuccess = true
                                            connectionStatusMessage = "Conexión exitosa con Atlas Cloud."
                                        } else {
                                            isConnectionSuccess = false
                                            connectionStatusMessage = result.exceptionOrNull()?.message
                                                ?: "Fallo de conexión."
                                        }
                                    }
                                },
                                enabled = !isTestingConnection,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryNeon)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = PrimaryNeon
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probando...")
                                } else {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Probar Conexión")
                                }
                            }
                        }

                        connectionStatusMessage?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isConnectionSuccess) AccentEmerald.copy(alpha = 0.15f) else AccentCoral.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isConnectionSuccess) AccentEmerald else AccentCoral
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isConnectionSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isConnectionSuccess) AccentEmerald else AccentCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (isConnectionSuccess) AccentEmerald else AccentCoral
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "PROVEEDORES DE VÍDEO",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon,
                        letterSpacing = 1.sp
                    )
                )
            }

            item {
                @Suppress("UNUSED_EXPRESSION")
                providerKeyRevision
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    app.providerRegistry.getAllProviders()
                        .filter { it.id in setOf("crun", "runway") }
                        .forEach { provider ->
                            ProviderCredentialCard(
                                provider = provider,
                                maskedKey = app.secureStorage.getMaskedProviderApiKey(provider.id),
                                apiKey = { app.secureStorage.getProviderApiKey(provider.id) },
                                onEdit = {
                                    providerKeyDialogId = provider.id
                                    providerKeyInput = app.secureStorage.getProviderApiKey(provider.id)
                                }
                            )
                        }
                }
            }

            // Sección 3: Pruebas y Modo Simulación
            item {
                Text(
                    text = "PRUEBAS Y CONTROL DE COSTES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = SecondaryVioletLight,
                        letterSpacing = 1.sp
                    )
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo Proveedor Simulado (Mock)",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "Simula generaciones completas de imagen/vídeo de forma local sin consumir créditos de Atlas Cloud.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                )
                            }
                            Switch(
                                checked = isMockMode,
                                onCheckedChange = { checked ->
                                    isMockMode = checked
                                    settingsRepo.setMockMode(checked)
                                    app.modelCatalogRepository.refreshModels()
                                }
                            )
                        }

                        Divider(color = BorderSubtle, thickness = 0.8.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDiagnostics = !showDiagnostics },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Diagnóstico del Sistema y Dispositivo",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            )
                            Icon(
                                imageVector = if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }

                        AnimatedVisibility(visible = showDiagnostics) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceVariantDark)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Aplicación: Nexus v1.0.0 (Compilación Nativa)", style = MaterialTheme.typography.labelSmall, color = PrimaryNeon)
                                Text("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                Text("Versión Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("Cifrado Keystore: Activo (AES256-GCM Hardware)", style = MaterialTheme.typography.labelSmall, color = AccentEmerald)
                                Text("Motor Multimedia: Media3 ExoPlayer v1.4.1", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("Gestión de Fondo: Android WorkManager 2.9", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("Servicio de Divisas: BCE / Frankfurter API", style = MaterialTheme.typography.labelSmall, color = PrimaryNeon)
                            }
                        }
                    }
                }
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tu clave API se almacena cifrada con AES256-GCM respaldada por el Keystore de Android. Nunca se expone en registros ni trazas de red.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
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
                    )
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
                    Text("Guardar Clave", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditKeyDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        )
    }

    providerKeyDialogId?.let { providerId ->
        val provider = app.providerRegistry.getProviderById(providerId)
        AlertDialog(
            onDismissRequest = { providerKeyDialogId = null },
            title = { Text("Configurar clave de ${provider?.displayName ?: providerId}", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("La clave se guarda cifrada en el Keystore de Android.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = providerKeyInput,
                        onValueChange = { providerKeyInput = it },
                        label = { Text("Clave API") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
            shape = RoundedCornerShape(16.dp)
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
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = PrimaryNeon)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.displayName, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(provider.description, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Divider(color = BorderSubtle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(maskedKey, modifier = Modifier.weight(1f), color = if (maskedKey == "Not configured") AccentCoral else TextPrimary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                TextButton(onClick = onEdit) { Text("Modificar") }
            }
            if(accountStatus != null || loadingBalance) {
                Surface(color = PrimaryNeon.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.28f))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryNeon,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Saldo", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            Text(accountStatus?.balanceLabel ?: "Consultando…", color = TextPrimary,
                                fontWeight = FontWeight.Bold)
                        }
                        if(loadingBalance) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            balanceError?.let { Text("Saldo: $it", color = AccentAmber, style = MaterialTheme.typography.bodySmall) }
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
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (testing) "Probando…" else "Probar conexión")
            }
            resultText?.let { Text(it, color = if (success) AccentEmerald else AccentCoral, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
