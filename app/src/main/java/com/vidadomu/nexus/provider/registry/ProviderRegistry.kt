package com.vidadomu.nexus.provider.registry

import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.api.NexusProvider
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.mock.MockNexusProvider
import com.vidadomu.nexus.provider.crun.CrunProvider
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import com.vidadomu.nexus.provider.runway.RunwayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderRegistry(
    private val atlasCloudProvider: AtlasCloudProvider,
    private val crunProvider: CrunProvider,
    private val runwayProvider: RunwayProvider,
    private val flowBridgeProvider: FlowBridgeProvider,
    private val mockNexusProvider: MockNexusProvider,
    private val secureStorage: SecureStorage,
    private val falProvider: com.vidadomu.nexus.provider.fal.FalProvider? = null
) {
    private val providers = mapOf(
        AtlasCloudProvider.PROVIDER_ID to atlasCloudProvider,
        CrunProvider.PROVIDER_ID to crunProvider,
        RunwayProvider.PROVIDER_ID to runwayProvider,
        FlowBridgeProvider.PROVIDER_ID to flowBridgeProvider,
        MockNexusProvider.PROVIDER_ID to mockNexusProvider
    ) + listOfNotNull(falProvider?.let { it.id to it }).toMap()

    /** Proveedores multimedia definidos por el usuario. Se suman a los de arriba; nunca los sustituyen. */
    private val _custom = MutableStateFlow<List<NexusProvider>>(emptyList())
    val customProviders: StateFlow<List<NexusProvider>> = _custom.asStateFlow()
    fun setCustomProviders(list: List<NexusProvider>) { _custom.value = list.filter { it.id !in providers.keys } }

    private val _activeProviderId = MutableStateFlow(
        if (secureStorage.isMockModeEnabled()) MockNexusProvider.PROVIDER_ID else AtlasCloudProvider.PROVIDER_ID
    )
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    fun getActiveProvider(): NexusProvider {
        val currentId = _activeProviderId.value
        return providers[currentId] ?: atlasCloudProvider
    }

    fun getProviderById(providerId: String): NexusProvider? {
        return providers[providerId] ?: _custom.value.firstOrNull { it.id == providerId }
    }

    fun getAllProviders(): List<NexusProvider> {
        return providers.values.toList() + _custom.value
    }

    fun getCatalogProviders(): List<NexusProvider> = if (secureStorage.isMockModeEnabled()) {
        listOf(mockNexusProvider)
    } else {
        providers.values.filterNot { it.id in setOf(MockNexusProvider.PROVIDER_ID, FlowBridgeProvider.PROVIDER_ID) } + _custom.value
    }

    fun setMockMode(enabled: Boolean) {
        secureStorage.setMockModeEnabled(enabled)
        _activeProviderId.value = if (enabled) MockNexusProvider.PROVIDER_ID else AtlasCloudProvider.PROVIDER_ID
    }
}
