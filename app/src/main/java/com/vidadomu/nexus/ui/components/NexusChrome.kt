package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
        ?: return if(raw in setOf("No disponible","Consultando…")) raw else "Saldo no disponible"
    val number=match.groupValues[1]
    val normalized=if(',' in number && '.' in number) {
        if(number.lastIndexOf(',')>number.lastIndexOf('.')) number.replace(".","").replace(',','.')
        else number.replace(",","")
    } else number.replace(',','.')
    val value=normalized.toDoubleOrNull()?.takeIf {it.isFinite()} ?: return "Saldo no disponible"
    return if(match.groupValues[2].lowercase() in setOf("cr","credits","créditos")) currency.formatCredits(value)
        else currency.formatBalance(value)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    if(sheet) NexusSheet("Saldo y gasto", onDismiss={sheet=false}, subtitle="Saldos en vivo y libro mayor confirmado") {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.screenMargin),
                horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                TextButton(onClick=vm::refresh,enabled=!busy) { Text(if(busy) "Actualizando…" else "Actualizar saldos") }
            }
            Column(Modifier.padding(horizontal=NexusTokens.screenMargin,vertical=NexusTokens.space8),
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
                        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                            Text(item.name,style=MaterialTheme.typography.bodyMedium)
                            Text(presentBalance(item.value,app.currencyManager),style=MaterialTheme.typography.bodyMedium.tabularMoney())
                        }
                        if(presentBalance(item.value,app.currencyManager)=="Saldo no disponible") {
                            InlineNotice("No se pudo interpretar el saldo.",technicalDetails=item.value,isError=false)
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
                {sheet=false;onNavigateToOutput(it)},Modifier.weight(1f),showBalance=false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusTopAppBar(title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Entradas propias de la pantalla dentro del único botón de ajustes (p. ej. modelo y conexión del asistente). */
    settingsEntries: List<Pair<String, () -> Unit>> = emptyList(),
    showBalance: Boolean = true) {
    val chrome=LocalChrome.current
    val density = LocalDensity.current
    val titleLineHeight = with(density) { MaterialTheme.typography.titleLarge.lineHeight.toDp() }
    val topBarHeight = maxOf(NexusTokens.topBarHeight, titleLineHeight + NexusTokens.space16)
    // Preserve the title's space at large font sizes; the wallet still announces the full amount.
    val iconOnlyBalance = density.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360
    StudioChrome(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            TopAppBar(title=title,modifier=Modifier,navigationIcon=navigationIcon,colors=colors.copy(containerColor=Color.Transparent,scrolledContainerColor=Color.Transparent),
                windowInsets=windowInsets,scrollBehavior=scrollBehavior,
                expandedHeight=topBarHeight,
                actions={
                    actions()
                    chrome?.let {
                        if (showBalance) NexusBalanceChip(iconOnly = iconOnlyBalance)
                        it.openSettings?.let { open ->
                            if(settingsEntries.isEmpty()) IconButton(onClick=open) {
                                Icon(Icons.Outlined.Settings,contentDescription="Ajustes de Nexus")
                            } else {
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick={menu=true}) { Icon(Icons.Outlined.Settings,contentDescription="Ajustes") }
                                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                        settingsEntries.forEach { (label,go) -> DropdownMenuItem(text={Text(label)},onClick={menu=false;go()}) }
                                        DropdownMenuItem(text={Text("Aplicación")},onClick={menu=false;open()})
                                    }
                                }
                            }
                        }
                    }
                })
            HorizontalDivider(thickness=NexusTokens.divider,color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
        }
    }
}

@Composable
fun NexusBalanceChip(compact:Boolean=false, iconOnly: Boolean = false) {
    LocalChrome.current?.let { chrome ->
        StudioBalanceChip(chrome.balance,chrome.openBalance,compact,iconOnly)
    }
}

@Composable
internal fun StudioBalanceChip(balance:String,onClick:()->Unit,compact:Boolean=false,iconOnly:Boolean=false) {
    Box(
        contentAlignment=androidx.compose.ui.Alignment.Center,
        modifier=Modifier.widthIn(min=NexusTokens.minimumTouch,max=if(iconOnly) NexusTokens.minimumTouch else 148.dp)
            .heightIn(min=NexusTokens.minimumTouch)
            .semantics(mergeDescendants=true) { contentDescription="Saldo y gasto"; stateDescription=balance }
            .clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=onClick)
    ) {
        // Saldo discreto: texto sin fondo, para que la cabecera no cargue otro botón-pastilla.
        Surface(shape=NexusTokens.filter,color=Color.Transparent) {
            // Wallet glyph: the amount is recognisable as saldo/gasto without a label; tapping opens the breakdown.
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,modifier=Modifier.padding(
                horizontal=if(compact || iconOnly) NexusTokens.space8 else NexusTokens.space12,vertical=NexusTokens.space8)) {
                Icon(Icons.Outlined.AccountBalanceWallet,null,Modifier.size(NexusTokens.space16),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!iconOnly) {
                    Spacer(Modifier.width(NexusTokens.space4))
                    Text(balance,style=MaterialTheme.typography.labelMedium.tabularMoney(),color=MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Compact headings in chrome; the user's font scale still applies in full. */
@Composable
fun nexusScreenTitleStyle():TextStyle = MaterialTheme.typography.titleMedium

@Composable fun NexusSettingsButton() {LocalChrome.current?.openSettings?.let {go->IconButton(onClick=go) {Icon(Icons.Outlined.Settings,"Ajustes de Nexus",Modifier.size(NexusTokens.iconMedium))}}}
/** «Ajustes de Nexus» como entrada de un menú, para no duplicar un engranaje junto al menú ⋮. */
@Composable fun NexusSettingsMenuItem(onClick: () -> Unit) {
    LocalChrome.current?.openSettings?.let { go ->
        DropdownMenuItem(text = { Text("Ajustes de Nexus") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { onClick(); go() })
    }
}
