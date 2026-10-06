package com.vidadomu.nexus.ui.accounts

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class AccountReading(val name: String, val value: String, val time: Long? = null, val stale: Boolean = false)

class AccountBalanceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NexusApplication
    private val readings = MutableStateFlow<List<AccountReading>>(emptyList())
    val balances = readings.asStateFlow()
    private val loading = MutableStateFlow(false)
    val busy = loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (loading.value || BuildConfig.OFFLINE_QA) return
        loading.value = true
        viewModelScope.launch {
            try {
                val providers = app.providerRegistry.getCatalogProviders().filter {
                    app.secureStorage.hasProviderApiKey(it.id)
                }
                val previous = readings.value.associateBy { it.name }
                readings.value = providers.map { previous[it.displayName] ?: AccountReading(it.displayName, "Consultando…") }
                providers.forEach { provider ->
                    val result = try {
                        if (provider.id == "atlas_cloud") app.atlasProvider.fetchBalance().map {
                            String.format(Locale.getDefault(), "%.4f USD", it.availableUsd)
                        } else provider.fetchAccountStatus(app.secureStorage.getProviderApiKey(provider.id))
                            ?.map { it.balanceLabel } ?: Result.failure(IllegalStateException("No disponible"))
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) { Result.failure(error) }
                    val old = previous[provider.displayName]
                    val next = result.fold(
                        onSuccess = { AccountReading(provider.displayName, it, System.currentTimeMillis()) },
                        onFailure = { old?.takeIf { it.time != null }?.copy(stale = true) ?: AccountReading(provider.displayName, "No disponible") }
                    )
                    readings.value = readings.value.map { if (it.name == provider.displayName) next else it }
                }
            } finally { loading.value = false }
        }
    }
}

@Composable
fun AccountBalanceBar(vm: AccountBalanceViewModel = viewModel()) {
    val balances by vm.balances.collectAsState()
    val busy by vm.busy.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (balances.isEmpty()) Text("Saldos: sin proveedores configurados", style = MaterialTheme.typography.labelSmall)
                balances.forEach { item ->
                    Column {
                        Text(item.name + " · " + item.value, style = MaterialTheme.typography.labelMedium)
                        item.time?.let {
                            Text((if (item.stale) "Último dato " else "Consulta ") + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            IconButton(onClick = vm::refresh, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Actualizar saldos")
            }
        }
    }
}
