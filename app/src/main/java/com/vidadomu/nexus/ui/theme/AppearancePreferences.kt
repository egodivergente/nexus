package com.vidadomu.nexus.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray

enum class NexusAppearance(val label: String, val description: String) {
    AURORA("Aurora Borealis", "Azul noche · auroras de cian y violeta"),
    OLED("Negro OLED", "Negro puro · cian suave · mínimo brillo"),
    QUARTZ("Cuarzo negro", "Oscuro cálido · champán · títulos editoriales"),
    OCEAN("Océano", "Azul noche · cian"),
    FOREST("Bosque", "Verde profundo · salvia"),
    SAND("Arena", "Crema cálida · terracota · luz suave"),
    ROSE("Rosa", "Marfil rosado · ciruela · superficies claras"),
    LIGHT("Claro", "Marfil · azul tinta · tarjetas blancas"),
    OBSIDIAN("Obsidian Liquid", "Negro OLED · grafito y reflejos de plata"),
    CHAMPAGNE("Champagne Silk", "Marfil satinado · cobre y calidez editorial"),
    EMERALD("Emerald Atmosphere", "Verde profundo · luz orgánica de menta"),
    NOCTURNE("Nocturne Nebula", "Índigo · borgoña y niebla magenta");
    companion object { val premiumThemes = listOf(AURORA, OBSIDIAN, CHAMPAGNE, EMERALD, NOCTURNE) }
}

enum class AmbientMode(val label: String) { STATIC("Estático"), SUBTLE("Sutil"), IMMERSIVE("Inmersivo") }

@Immutable
data class AppearanceSelection(val theme: NexusAppearance, val ambientMode: AmbientMode = AmbientMode.SUBTLE,
    val intensity: Float = .55f, val reduceMotion: Boolean = false)

internal fun sanitizedAppearance(value: AppearanceSelection) = value.copy(
    intensity = if(value.intensity.isFinite()) value.intensity.coerceIn(0f, 1f) else .55f)

/** Preferences scoped to the local Android user. Never reads billing or credentials. */
class AppearancePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_appearance_user_v1", Context.MODE_PRIVATE)
    fun theme() = NexusAppearance.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: NexusAppearance.AURORA
    fun setTheme(theme: NexusAppearance) { applyAppearance(selection().copy(theme=theme)) }
    fun selection() = sanitizedAppearance(AppearanceSelection(theme(),
        AmbientMode.entries.firstOrNull {it.name==prefs.getString("ambient_mode", null)} ?: AmbientMode.SUBTLE,
        prefs.getFloat("ambient_intensity", .55f), prefs.getBoolean("reduce_motion", false)))
    fun previewAppearance(selection: AppearanceSelection?) { preview.value=selection?.let(::sanitizedAppearance) }
    fun previewTheme(theme: NexusAppearance?) { previewAppearance(theme?.let {selection().copy(theme=it)}) }
    fun applyAppearance(selection: AppearanceSelection) {
        val value=sanitizedAppearance(selection)
        prefs.edit().putString("theme", value.theme.name).putString("ambient_mode",value.ambientMode.name)
            .putFloat("ambient_intensity",value.intensity).putBoolean("reduce_motion",value.reduceMotion).apply()
        preview.value=null
    }
    fun ambientMode()=selection().ambientMode
    fun setAmbientMode(value:AmbientMode) {applyAppearance(selection().copy(ambientMode=value))}
    fun ambientIntensity()=selection().intensity
    fun setAmbientIntensity(value:Float) {applyAppearance(selection().copy(intensity=value))}
    fun reduceMotion()=selection().reduceMotion
    fun setReduceMotion(value:Boolean) {applyAppearance(selection().copy(reduceMotion=value))}
    companion object { internal val preview=mutableStateOf<AppearanceSelection?>(null) }
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
fun AppearancePreferences.selectionState(): State<AppearanceSelection> {
    val saved=remember(this) {mutableStateOf(selection())}
    DisposableEffect(this) {val unregister=observe {saved.value=selection()};onDispose(unregister)}
    return remember(this) {derivedStateOf {AppearancePreferences.preview.value ?: saved.value}}
}

@Composable
fun AppearancePreferences.themeState(): State<NexusAppearance> {
    val selection=selectionState()
    return remember(this) {derivedStateOf {selection.value.theme}}
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
