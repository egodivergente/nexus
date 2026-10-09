package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.ui.accounts.AccountBalanceViewModel
import com.vidadomu.nexus.ui.billing.BillingLedgerScreen
import com.vidadomu.nexus.ui.theme.*
import java.text.DateFormat
import java.util.Date

private data class ChromeActions(val balance: String, val openBalance: () -> Unit,
    val openSettings: (() -> Unit)?)
private val LocalChrome = staticCompositionLocalOf<ChromeActions?> { null }

/** Formats an existing provider reading; does not query, sum or alter account balances. */
internal fun presentBalance(raw: String, currency: CurrencyManager): String {
    val match=Regex("^\\s*([+-]?[0-9.,]+)\\s*(USD|US\\$|\\$|cr|credits|créditos)\\s*$", RegexOption.IGNORE_CASE).matchEntire(raw)
        ?: return raw
    val value=match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return raw
    return if(match.groupValues[2].lowercase() in setOf("cr","credits","créditos")) currency.formatCredits(value)
        else currency.formatBalance(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusChrome(onSettings: () -> Unit, onNavigateToOutput: (String) -> Unit,
    settingsVisible: Boolean = true, content: @Composable () -> Unit) {
    val app=LocalContext.current.applicationContext as NexusApplication
    val vm: AccountBalanceViewModel=viewModel()
    val balances by vm.balances.collectAsState()
    val busy by vm.busy.collectAsState()
    val currency by app.currencyManager.state.collectAsState()
    val jobs by app.generationRepository.allJobs.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val cached=remember(balances) { app.secureStorage.getCachedBalance() }
    val atlas=balances.firstOrNull { it.name.contains("atlas",ignoreCase=true) }
    val reading=atlas ?: balances.firstOrNull()
    val label=remember(reading,cached,currency) {
        reading?.takeIf { Regex(".*[0-9].*").matches(it.value) }?.let { presentBalance(it.value,app.currencyManager) }
            ?: cached?.let { app.currencyManager.formatBalance(it.availableUsd) } ?: "Saldo"
    }
    CompositionLocalProvider(LocalChrome provides ChromeActions(label,{sheet=true},onSettings.takeIf {settingsVisible})) {
        content()
    }
    if(sheet) ModalBottomSheet(onDismissRequest={sheet=false},shape=NexusTokens.sheet,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Saldo y gasto",style=MaterialTheme.typography.titleLarge)
                TextButton(onClick=vm::refresh,enabled=!busy) { Text(if(busy) "Actualizando…" else "Actualizar") }
            }
            Column(Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space12),
                verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                if(balances.isEmpty()) {
                    Text(cached?.let { "Atlas · ${app.currencyManager.formatBalance(it.availableUsd)} · último dato" }
                        ?: "Sin proveedores configurados",style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    cached?.let { Text(app.currencyManager.formatUsdDetails(it.availableUsd),
                        style=MaterialTheme.typography.labelMedium.tabularMoney(),color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                balances.forEach { item ->
                    Column {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                            Text(item.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                            Text(presentBalance(item.value,app.currencyManager),style=MaterialTheme.typography.bodyMedium.tabularMoney())
                        }
                        item.time?.let { time ->
                            Text((if(item.stale) "Último dato · " else "Consultado · ")+DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)),
                                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(item.name.contains("atlas",true)) {
                            val usd=item.value.removeSuffix(" USD").replace(',', '.').toDoubleOrNull()
                            usd?.let { Text(app.currencyManager.formatUsdDetails(it),style=MaterialTheme.typography.labelMedium.tabularMoney(),
                                color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            BillingLedgerScreen(jobs,cached,app.currencyManager,vm::refresh,
                {sheet=false;onNavigateToOutput(it)},Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusTopAppBar(title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null) {
    val chrome=LocalChrome.current
    TopAppBar(title=title,modifier=modifier,navigationIcon=navigationIcon,colors=colors,
        windowInsets=windowInsets,scrollBehavior=scrollBehavior,expandedHeight=NexusTokens.topBarHeight,
        actions={
            actions()
            chrome?.let {
                NexusBalanceChip()
                it.openSettings?.let { open -> IconButton(onClick=open) {
                    Icon(Icons.Outlined.Settings,contentDescription="Ajustes de Nexus")
                } }
            }
        })
}

@Composable
fun NexusBalanceChip() {
    LocalChrome.current?.let { chrome ->
        Surface(onClick=chrome.openBalance,shape=NexusTokens.field,color=MaterialTheme.colorScheme.surfaceVariant,
            modifier=Modifier.heightIn(min=NexusTokens.minimumTouch).semantics { contentDescription="Saldo y gasto" }) {
            Box(contentAlignment=androidx.compose.ui.Alignment.Center,modifier=Modifier.padding(horizontal=NexusTokens.space12)) {
                Text(chrome.balance,style=MaterialTheme.typography.labelMedium.tabularMoney(),color=MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
