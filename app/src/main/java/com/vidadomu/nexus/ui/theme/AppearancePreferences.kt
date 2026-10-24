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
    /** Tamaño de letra de toda la app (0,8–1,4), encima del ajuste del sistema. */
    fun fontScale() = prefs.getFloat("font_scale", 1f).coerceIn(.8f, 1.4f)
    fun setFontScale(scale: Float) { prefs.edit().putFloat("font_scale", scale.coerceIn(.8f, 1.4f)).apply() }
    /** Ocultar la barra de navegación al bajar por el chat y mostrarla al subir. */
    fun autoHideBars() = prefs.getBoolean("auto_hide_bars", true)
    fun setAutoHideBars(on: Boolean) { prefs.edit().putBoolean("auto_hide_bars", on).apply() }
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

@Composable
fun AppearancePreferences.fontScaleState(): State<Float> {
    val state = remember(this) { mutableStateOf(fontScale()) }
    DisposableEffect(this) {
        val unregister = observe { state.value = fontScale() }
        onDispose(unregister)
    }
    return state
}

/** Si la barra inferior está visible. La pantalla del chat la oculta al bajar; el resto la deja siempre a la vista. */
object ChromeVisibility {
    /** The tab bar is tucked away by default; a swipe up from the bottom edge brings it in and it leaves again by itself. */
    val bottomBar = mutableStateOf(false)
    /** The user may prefer the bar to stay put (long-press menu of the chat). Persisted in `nexus_workspace`. */
    val pinned = mutableStateOf(false)

    fun load(context: Context) { pinned.value = context.getSharedPreferences("nexus_workspace", Context.MODE_PRIVATE).getBoolean("navigation_visible", true) }
    fun setPinned(context: Context, value: Boolean) {
        pinned.value = value
        context.getSharedPreferences("nexus_workspace", Context.MODE_PRIVATE).edit().putBoolean("bar_pinned", value).putBoolean("navigation_visible", value).apply()
    }
    val shown: Boolean get() = pinned.value || bottomBar.value
    /** True while a tab is showing a screen inside itself (e.g. an ArtBox family or model sheet): the edge swipe is «Atrás», not «next tab». */
    val nestedLevel = mutableStateOf(false)
}

/** Replace visible slots only; hidden providers keep their saved positions. */
fun mergeVisibleOrder(saved: List<String>, visible: List<String>): List<String> {
    val slots = saved.distinct().toMutableList()
    visible.forEach { if (it !in slots) slots.add(it) }
    val replacements = visible.iterator()
    return slots.map { if (it in visible) replacements.next() else it }
}
