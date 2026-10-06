package com.vidadomu.nexus.ui.models

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.*

enum class EscaparateSort(val displayName: String) {
    POPULARITY("Popularidad"),
    PRICE("Precio"),
    SPEED("Velocidad"),
    NAME("Nombre A–Z")
}

enum class EscaparateTypeFilter(val displayName: String) {
    IMAGE_AND_VIDEO("Imagen y vídeo"),
    ALL("Todos"),
    IMAGE("Imagen"),
    VIDEO("Vídeo"),
    CHAT("Chat / LLM"),
    AUDIO("Audio")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyModelsScreen(
    onSelectModelAndCreate: (NexusModel) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = context.applicationContext as NexusApplication
    val models by app.modelCatalogRepository.models.collectAsState()
    val isLoading by app.modelCatalogRepository.isLoading.collectAsState()
    val errorMessage by app.modelCatalogRepository.errorMessage.collectAsState()
    val currencyManager = app.currencyManager

    // --- Search, Sort & Filter States ---
    var searchQuery by remember { mutableStateOf("") }
    var activeSort by remember { mutableStateOf(EscaparateSort.POPULARITY) }
    var activeTypeFilter by remember { mutableStateOf(EscaparateTypeFilter.IMAGE_AND_VIDEO) } // PRESELECTED BY DEFAULT
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var filterDiscountOnly by remember { mutableStateOf(false) }

    // Toggle for full exhaustive catalog view
    var isFullListView by remember { mutableStateOf(false) }
    var expandedDetailModelId by remember { mutableStateOf<String?>(null) }

    // Curated Priority Providers for Fast Filtering
    val priorityProviders = remember {
        listOf("Todos", "ByteDance", "Kling", "MiniMax", "Alibaba", "OpenAI", "Black Forest Labs", "Google")
    }

    // Dynamic providers list from loaded models
    val availableProviders = remember(models) {
        models.map { it.organization }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    // --- Core Filtered and Sorted Models List ---
    val filteredModels = remember(
        models,
        searchQuery,
        activeSort,
        activeTypeFilter,
        selectedProvider,
        filterDiscountOnly,
        isFullListView
    ) {
        var list = models

        // 1. Text Search Filter
        if (searchQuery.isNotBlank()) {
            val tokens = searchQuery.trim().lowercase().split(" ").filter { it.isNotBlank() }
            list = list.filter { model ->
                val searchableText = buildString {
                    append(model.displayName.lowercase()).append(" ")
                    append(model.id.lowercase()).append(" ")
                    append(model.organization.lowercase()).append(" ")
                    append(model.shortDescription.lowercase()).append(" ")
                    model.family?.let { append(it.lowercase()).append(" ") }
                    model.description.let { append(it.lowercase()).append(" ") }
                    model.tags.forEach { append(it.lowercase()).append(" ") }
                }
                tokens.all { searchableText.contains(it) }
            }
        }

        // 2. Provider Filter
        if (!selectedProvider.isNullOrBlank() && selectedProvider != "Todos") {
            list = list.filter {
                it.organization.contains(selectedProvider!!, ignoreCase = true) ||
                        it.id.contains(selectedProvider!!.lowercase())
            }
        }

        // 3. Discount / Deals Filter
        if (filterDiscountOnly) {
            list = list.filter { !it.discount.isNullOrBlank() && it.discount != "0" }
        }

        // 4. Modality / Type Filter
        list = when (activeTypeFilter) {
            EscaparateTypeFilter.IMAGE_AND_VIDEO -> list.filter {
                it.modalities.contains(Modality.IMAGE) || it.modalities.contains(Modality.VIDEO)
            }
            EscaparateTypeFilter.IMAGE -> list.filter { it.modalities.contains(Modality.IMAGE) }
            EscaparateTypeFilter.VIDEO -> list.filter { it.modalities.contains(Modality.VIDEO) }
            EscaparateTypeFilter.CHAT -> list.filter { it.modalities.contains(Modality.CHAT) }
            EscaparateTypeFilter.AUDIO -> list.filter { it.modalities.contains(Modality.AUDIO) }
            EscaparateTypeFilter.ALL -> list
        }

        // 5. Sorting
        when (activeSort) {
            EscaparateSort.POPULARITY -> list.sortedWith(NexusModel.DEFAULT_COMPARATOR)
            EscaparateSort.PRICE -> list.sortedWith(
                compareBy<NexusModel> { it.basePriceUsd ?: Double.MAX_VALUE }
                    .then(NexusModel.DEFAULT_COMPARATOR)
            )
            EscaparateSort.SPEED -> list.sortedWith(
                compareBy<NexusModel> {
                    when (it.speedQualityTag) {
                        "Ultrarrápido" -> 0
                        "Equilibrado" -> 1
                        else -> 2
                    }
                }.then(NexusModel.DEFAULT_COMPARATOR)
            )
            EscaparateSort.NAME -> list.sortedWith(
                compareBy<NexusModel> { it.displayName.lowercase() }
                    .thenByDescending { it.versionNumeric }
            )
        }
    }

    // Split models into sections for Escaparate View
    val thirtyDaysAgo = remember { System.currentTimeMillis() - 30L * 86_400_000L }
    val newReleases = remember(filteredModels) {
        filteredModels.filter { it.releaseDateMillis >= thirtyDaysAgo }
            .sortedWith(NexusModel.DEFAULT_COMPARATOR)
            .take(10)
    }

    val imageAndVideoModels = remember(filteredModels, activeTypeFilter) {
        if (activeTypeFilter == EscaparateTypeFilter.CHAT || activeTypeFilter == EscaparateTypeFilter.AUDIO) {
            emptyList()
        } else {
            filteredModels.filter {
                it.modalities.contains(Modality.IMAGE) || it.modalities.contains(Modality.VIDEO)
            }
        }
    }

    val imageAndVideoPairs = remember(imageAndVideoModels) {
        imageAndVideoModels.chunked(2)
    }

    val secondaryModels = remember(filteredModels, activeTypeFilter) {
        if (activeTypeFilter == EscaparateTypeFilter.IMAGE || activeTypeFilter == EscaparateTypeFilter.VIDEO) {
            emptyList()
        } else {
            filteredModels.filter {
                !it.modalities.contains(Modality.IMAGE) && !it.modalities.contains(Modality.VIDEO)
            }
        }
    }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Section 1: Header Título + Subtítulo con métricas
                        Text(
                            text = "Modelos",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "${models.size} modelos de ${availableProviders.size} proveedores líderes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                },
                actions = {
                    // Quick reload / refresh
                    IconButton(onClick = { app.modelCatalogRepository.refreshModels(forceNetwork = true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar catálogo",
                            tint = if (isLoading) PrimaryNeon else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // SECCIÓN 2: BUSCADOR
            // ==========================================
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Buscar por nombre, tarea o proveedor...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (searchQuery.isNotBlank()) PrimaryNeon else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = PrimaryNeon.copy(alpha = 0.8f),
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryNeon
                        )
                    )
                }
            }

            // ==========================================
            // SECCIÓN 3: ORDENAR POR (CHIPS - UNO SIEMPRE ACTIVO)
            // ==========================================
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ORDENAR POR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EscaparateSort.entries.forEach { sort ->
                            val isSelected = activeSort == sort
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activeSort = sort
                                    },
                                color = if (isSelected) PrimaryNeon.copy(alpha = 0.14f) else SurfaceDark,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) PrimaryNeon else BorderSubtle
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sort.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) PrimaryNeon else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SECCIÓN 4: FILTROS (TIPO PRESELECCIONADO "IMAGEN Y VÍDEO" + PROVEEDOR)
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Filtro por Tipo
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TIPO DE MODELO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(EscaparateTypeFilter.entries) { filter ->
                                val isSelected = activeTypeFilter == filter
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            activeTypeFilter = filter
                                        },
                                    color = if (isSelected) PrimaryNeon.copy(alpha = 0.14f) else SurfaceDark,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) PrimaryNeon else BorderSubtle
                                    )
                                ) {
                                    Text(
                                        text = filter.displayName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) PrimaryNeon else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // Chip de Ofertas integrado
                            item {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            filterDiscountOnly = !filterDiscountOnly
                                        },
                                    color = if (filterDiscountOnly) AccentCoral.copy(alpha = 0.15f) else SurfaceDark,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (filterDiscountOnly) AccentCoral else BorderSubtle
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalOffer,
                                            contentDescription = null,
                                            tint = if (filterDiscountOnly) AccentCoral else TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Ofertas",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (filterDiscountOnly) AccentCoral else TextSecondary,
                                                fontWeight = if (filterDiscountOnly) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Filtro por Proveedor
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PROVEEDORES DESTACADOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(priorityProviders) { provider ->
                                val isSelected = (selectedProvider == null && provider == "Todos") ||
                                        (selectedProvider.equals(provider, ignoreCase = true))
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedProvider = if (provider == "Todos") null else provider
                                        },
                                    color = if (isSelected) SecondaryViolet.copy(alpha = 0.18f) else SurfaceDark,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) SecondaryViolet else BorderSubtle
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        if (provider == "Todos") {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = PrimaryNeon,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            BrandLogo(
                                                organization = provider,
                                                modelId = provider,
                                                size = 18.dp,
                                                containerSize = 26.dp
                                            )
                                        }
                                        Text(
                                            text = provider,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) TextPrimary else TextSecondary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Vista Completa Exhaustiva (si el usuario pulsa "Ver los N modelos")
            if (isFullListView) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isFullListView = false },
                        color = SurfaceVariantDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "← Volver al escaparate visual",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = PrimaryNeon,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Mostrando ${filteredModels.size} modelos",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                            )
                        }
                    }
                }

                items(filteredModels, key = { it.id }) { model ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FullCatalogModelRow(
                            model = model,
                            currencyManager = currencyManager,
                            isExpanded = expandedDetailModelId == model.id,
                            onToggleExpand = {
                                expandedDetailModelId = if (expandedDetailModelId == model.id) null else model.id
                            },
                            onUseModel = { onSelectModelAndCreate(model) }
                        )
                    }
                }
            } else {
                // ==========================================
                // SECCIÓN 5: NOVEDADES (ÚLTIMOS 30 DÍAS)
                // ==========================================
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOVEDADES RECIENTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Últimos 30 días",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentEmerald,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        if (newReleases.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(newReleases) { model ->
                                    NewReleaseHeroCard(
                                        model = model,
                                        currencyManager = currencyManager,
                                        onClick = { onSelectModelAndCreate(model) }
                                    )
                                }
                            }
                        } else {
                            // Estado alternativo explicativo si no hay novedades en el filtro
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sin novedades esta semana · Todos los modelos se encuentran en sus versiones oficiales estables.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECCIÓN 6: IMAGEN Y VÍDEO (BLOQUE PRINCIPAL - GRID 2 COLUMNAS)
                // ==========================================
                if (imageAndVideoModels.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "IMAGEN Y VÍDEO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Generación y edición con los motores insignia líderes de la industria",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    // Grid de 2 columnas con tarjetas de tamaño medio
                    items(imageAndVideoPairs) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                EscaparateMediaCard(
                                    model = pair[0],
                                    currencyManager = currencyManager,
                                    onClick = { onSelectModelAndCreate(pair[0]) }
                                )
                            }
                            if (pair.size > 1) {
                                Box(modifier = Modifier.weight(1f)) {
                                    EscaparateMediaCard(
                                        model = pair[1],
                                        currencyManager = currencyManager,
                                        onClick = { onSelectModelAndCreate(pair[1]) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ==========================================
                // SECCIÓN 7: CHAT, AUDIO Y OTROS (TRATAMIENTO SECUNDARIO DISCRETO)
                // ==========================================
                if (secondaryModels.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "CHAT, AUDIO Y OTROS SERVICIOS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Modelos complementarios de lenguaje conversacional y síntesis musical",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    items(secondaryModels) { model ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SecondaryCompactModelRow(
                                model = model,
                                currencyManager = currencyManager,
                                onClick = { onSelectModelAndCreate(model) }
                            )
                        }
                    }
                }

                // ==========================================
                // SECCIÓN 8: CTA FINAL "VER LOS N MODELOS"
                // ==========================================
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isFullListView = true
                                },
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewList,
                                    contentDescription = null,
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ver los ${filteredModels.size} modelos del catálogo completo",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = PrimaryNeon,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENTES DEL ESCAPARATE VISUAL
// ==========================================

/**
 * Tarjeta Grande para el Carrusel de Novedades (Sección 5)
 */
@Composable
private fun NewReleaseHeroCard(
    model: NexusModel,
    currencyManager: CurrencyManager,
    onClick: () -> Unit
) {
    val formattedPrice = remember(model.basePriceUsd, model.priceUnit, model.pricingInfo) {
        currencyManager.formatModelPricing(model.basePriceUsd, model.priceUnit, model.pricingInfo.ifBlank { "Consultar" })
    }

    val daysAgo = remember(model.releaseDateMillis) {
        val days = ((System.currentTimeMillis() - model.releaseDateMillis) / 86_400_000L).coerceAtLeast(1)
        if (days == 1L) "Nuevo · hoy" else "Nuevo · hace ${days}d"
    }

    Surface(
        modifier = Modifier
            .width(260.dp)
            .height(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Proveedor + Badge Temporal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BrandLogo(
                        organization = model.organization,
                        modelId = model.id,
                        size = 18.dp
                    )
                    Text(
                        text = model.organization,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentEmerald.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = daysAgo,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Cuerpo: Nombre + Frase de Uso
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.shortDescription.ifBlank { "Modelo generativo insignia de alta fidelidad." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Footer: Precio + Botón Probar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentEmerald,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PrimaryNeon.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNeon.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Probar ↗",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PrimaryNeon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de Tamaño Medio en Grid de 2 Columnas para Imagen y Vídeo (Sección 6)
 */
@Composable
private fun EscaparateMediaCard(
    model: NexusModel,
    currencyManager: CurrencyManager,
    onClick: () -> Unit
) {
    val formattedPrice = remember(model.basePriceUsd, model.priceUnit, model.pricingInfo) {
        currencyManager.formatModelPricing(model.basePriceUsd, model.priceUnit, model.pricingInfo.ifBlank { "Consultar" })
    }

    val speedTag = model.speedQualityTag.ifBlank { "Equilibrado" }
    val tagColor = when (speedTag) {
        "Ultrarrápido" -> PrimaryNeon
        "Ultra Calidad" -> SecondaryViolet
        "Económico" -> AccentEmerald
        else -> TextSecondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Proveedor + Etiqueta de Velocidad/Calidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BrandLogo(
                        organization = model.organization,
                        modelId = model.id,
                        size = 18.dp
                    )
                    Text(
                        text = model.organization,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tagColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, tagColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = speedTag,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = tagColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Cuerpo: Nombre + Frase Corta Orientada a Uso ("Ideal para...")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.shortDescription.ifBlank { "Generación visual de alta resolución y consistencia." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Footer: Precio + Icono de Entrada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentEmerald,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PrimaryNeon,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Fila Compacta Discreta para Chat, Audio y otros (Sección 7)
 */
@Composable
private fun SecondaryCompactModelRow(
    model: NexusModel,
    currencyManager: CurrencyManager,
    onClick: () -> Unit
) {
    val formattedPrice = remember(model.basePriceUsd, model.priceUnit, model.pricingInfo) {
        currencyManager.formatModelPricing(model.basePriceUsd, model.priceUnit, model.pricingInfo.ifBlank { "Consultar" })
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = SurfaceDark, // Tratamiento secundario en gris apagado
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icono + Nombre + Proveedor
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                BrandLogo(
                    organization = model.organization,
                    modelId = model.id,
                    size = 16.dp
                )
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "• ${model.organization}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Precio apagado + Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Fila Técnica Expandible para la Vista Exhaustiva Completa
 */
@Composable
private fun FullCatalogModelRow(
    model: NexusModel,
    currencyManager: CurrencyManager,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUseModel: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val formattedPrice = remember(model.basePriceUsd, model.priceUnit, model.pricingInfo) {
        currencyManager.formatModelPricing(model.basePriceUsd, model.priceUnit, model.pricingInfo.ifBlank { "Consultar" })
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggleExpand() },
        color = SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BrandLogo(
                        organization = model.organization,
                        modelId = model.id,
                        size = 20.dp
                    )
                    Column {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${model.organization} • ${model.modalities.joinToString { it.name }}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedPrice,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentEmerald,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                    Text(
                        text = model.description.ifBlank { model.shortDescription },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    // Copiar ID técnico
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BackgroundDark)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(model.id))
                                Toast.makeText(context, "ID copiado: ${model.id}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ID: ${model.id}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar ID",
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onUseModel,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Usar en Lienzo ↗",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
