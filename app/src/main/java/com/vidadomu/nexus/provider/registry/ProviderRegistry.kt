package com.vidadomu.nexus.provider.registry

import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.api.NexusProvider
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.mock.MockNexusProvider
import com.vidadomu.nexus.provider.crun.CrunProvider
import com.vidadomu.nexus.provider.runway.RunwayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderRegistry(
    private val atlasCloudProvider: AtlasCloudProvider,
    private val crunProvider: CrunProvider,
    private val runwayProvider: RunwayProvider,
    private val mockNexusProvider: MockNexusProvider,
    private val secureStorage: SecureStorage
) {
    private val providers = mapOf(
        AtlasCloudProvider.PROVIDER_ID to atlasCloudProvider,
        CrunProvider.PROVIDER_ID to crunProvider,
        RunwayProvider.PROVIDER_ID to runwayProvider,
        MockNexusProvider.PROVIDER_ID to mockNexusProvider
    )

    private val _activeProviderId = MutableStateFlow(
        if (secureStorage.isMockModeEnabled()) MockNexusProvider.PROVIDER_ID else AtlasCloudProvider.PROVIDER_ID
    )
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    fun getActiveProvider(): NexusProvider {
        val currentId = _activeProviderId.value
        return providers[currentId] ?: atlasCloudProvider
    }

    fun getProviderById(providerId: String): NexusProvider? {
        return providers[providerId]
    }

    fun getAllProviders(): List<NexusProvider> {
        return providers.values.toList()
    }

    fun getCatalogProviders(): List<NexusProvider> = if (secureStorage.isMockModeEnabled()) {
        listOf(mockNexusProvider)
    } else {
        providers.values.filterNot { it.id == MockNexusProvider.PROVIDER_ID }
    }

    fun setMockMode(enabled: Boolean) {
        secureStorage.setMockModeEnabled(enabled)
        _activeProviderId.value = if (enabled) MockNexusProvider.PROVIDER_ID else AtlasCloudProvider.PROVIDER_ID
    }
}
