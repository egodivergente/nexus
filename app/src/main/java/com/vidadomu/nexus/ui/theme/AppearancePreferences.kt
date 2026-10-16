package com.vidadomu.nexus.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray

enum class NexusAppearance(val label: String, val description: String) {
    AURORA("Aurora", "Estudio oscuro · cian suave · capas tonales"),
    OLED("Negro OLED", "Negro puro · cian suave · mínimo brillo"),
    QUARTZ("Cuarzo negro", "Oscuro cálido · champán · títulos editoriales"),
    OCEAN("Océano", "Azul noche · cian"),
    FOREST("Bosque", "Verde profundo · salvia"),
    SAND("Arena", "Crema cálida · terracota · luz suave"),
    ROSE("Rosa", "Marfil rosado · ciruela · superficies claras"),
    LIGHT("Claro", "Marfil · azul tinta · tarjetas blancas")
}

/** Preferences scoped to the local Android user. Never reads billing or credentials. */
class AppearancePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_appearance_user_v1", Context.MODE_PRIVATE)
    fun theme() = NexusAppearance.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: NexusAppearance.AURORA
    fun setTheme(theme: NexusAppearance) { prefs.edit().putString("theme", theme.name).apply() }
    fun order(filter: String): List<String> = runCatching {
        val json = JSONArray(prefs.getString("order_$filter", "[]"))
        List(json.length()) { json.getString(it) }.distinct()
    }.getOrDefault(emptyList())
    fun setOrder(filter: String, order: List<String>) { prefs.edit().putString("order_$filter", JSONArray(order.distinct()).toString()).apply() }
    fun observe(callback: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> callback() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

@Composable
fun rememberAppearancePreferences(): AppearancePreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppearancePreferences(context) }
}

@Composable
fun AppearancePreferences.themeState(): State<NexusAppearance> {
    val state = remember(this) { mutableStateOf(theme()) }
    DisposableEffect(this) {
        val unregister = observe { state.value = theme() }
        onDispose(unregister)
    }
    return state
}

/** Replace visible slots only; hidden providers keep their saved positions. */
fun mergeVisibleOrder(saved: List<String>, visible: List<String>): List<String> {
    val slots = saved.distinct().toMutableList()
    visible.forEach { if (it !in slots) slots.add(it) }
    val replacements = visible.iterator()
    return slots.map { if (it in visible) replacements.next() else it }
}
