package com.vidadomu.nexus.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** Theme identities share semantic roles, never provider or generation rules. */
enum class AmbientStyle { AURORA, LIQUID, SILK, ATMOSPHERE, NEBULA }
@Immutable
data class NexusThemeSpec(val isDark:Boolean,val ambientStyle:AmbientStyle,val ambientColors:List<Color>,
    val colors:ColorScheme,val chrome:Color)

@Immutable
data class NexusVisualTokens(val promptSurface:Color,val promptBorder:Color,val chromeSurface:Color,
    val success:Color,val onSuccess:Color,val warning:Color,val onWarning:Color,
    val processing:Color,val onProcessing:Color,val disabledText:Color,val placeholder:Color,
    val conversationSurface:Color,val responseSurface:Color,val codeSurface:Color,
    val mediaSurface:Color,val dialogSurface:Color,val sheetSurface:Color,val separator:Color)

val LocalNexusAppearance=staticCompositionLocalOf {NexusAppearance.AURORA}
val LocalNexusVisuals=staticCompositionLocalOf {NexusThemeSpecs.visuals(NexusAppearance.AURORA)}

object NexusThemeSpecs {
    private fun c(value:Long)=Color(value)
    private fun dark(bg:Long,low:Long,high:Long,chrome:Long,primary:Long,secondary:Long,tertiary:Long,
        style:AmbientStyle,lights:List<Long>):NexusThemeSpec {
        val ink=c(0xFFF3F5FA);val muted=c(0xFFBBC6D5);val layer=c(high);val base=c(bg)
        val scheme=darkColorScheme(primary=c(primary),onPrimary=base,primaryContainer=layer,onPrimaryContainer=ink,
            secondary=c(secondary),onSecondary=base,secondaryContainer=layer,onSecondaryContainer=ink,
            tertiary=c(tertiary),onTertiary=base,tertiaryContainer=layer,onTertiaryContainer=ink,
            background=base,onBackground=ink,surface=c(low),onSurface=ink,surfaceVariant=layer,onSurfaceVariant=muted,
            surfaceContainerLowest=base,surfaceContainerLow=c(low),surfaceContainer=c(low),surfaceContainerHigh=layer,
            surfaceContainerHighest=layer,surfaceDim=base,surfaceBright=layer,
            outline=c(0xFF8A99AD),outlineVariant=c(0xFFBBC6D5).copy(alpha=.18f),
            error=c(0xFFFFB4BA),onError=c(0xFF350C15),errorContainer=c(0xFF3A1B26),onErrorContainer=c(0xFFFFDADD),
            inverseSurface=ink,inverseOnSurface=base,inversePrimary=c(0xFF244E67),surfaceTint=c(primary))
        return NexusThemeSpec(true,style,lights.map(::c),scheme,c(chrome))
    }
    private val aurora by lazy {dark(0xFF060F20,0xFF101D30,0xFF1D3045,0xFF14243B,
        0xFF83E5EC,0xFFBDB7FF,0xFF9DE8D4,AmbientStyle.AURORA,listOf(0xFF188EA2,0xFF3E598F,0xFF7554B0))}
    private val obsidian by lazy {dark(0xFF000000,0xFF101214,0xFF24282D,0xFF181C21,
        0xFFD8E1EC,0xFFAFBDCC,0xFFCCBEAC,AmbientStyle.LIQUID,listOf(0xFF647082,0xFF343C48,0xFF9AA4AF))}
    private val emerald by lazy {dark(0xFF04120F,0xFF10251F,0xFF1D362E,0xFF102A23,
        0xFF8DE4BD,0xFFA3DED5,0xFFCDDCA4,AmbientStyle.ATMOSPHERE,listOf(0xFF167F60,0xFF1F847E,0xFF466D3E))}
    private val nocturne by lazy {dark(0xFF100B20,0xFF1C172E,0xFF30223E,0xFF261B35,
        0xFFD4B8FF,0xFFF3B0D0,0xFFB8C8FF,AmbientStyle.NEBULA,listOf(0xFF624595,0xFF8F355E,0xFF364B82))}
    private val champagne by lazy {
        val ink=c(0xFF30241D);val muted=c(0xFF685346);val bg=c(0xFFFAF4E9);val surface=c(0xFFFFFBF3);val high=c(0xFFEDE0CE)
        NexusThemeSpec(false,AmbientStyle.SILK,listOf(c(0xFFD5B487),c(0xFFC59A7D),c(0xFFE0CEAA)),
            lightColorScheme(primary=c(0xFF754326),onPrimary=c(0xFFFFF7EB),primaryContainer=high,onPrimaryContainer=ink,
                secondary=c(0xFF68503D),onSecondary=c(0xFFFFF7EB),secondaryContainer=high,onSecondaryContainer=ink,
                tertiary=c(0xFF71533A),onTertiary=c(0xFFFFF7EB),tertiaryContainer=high,onTertiaryContainer=ink,
                background=bg,onBackground=ink,surface=surface,onSurface=ink,surfaceVariant=high,onSurfaceVariant=muted,
                surfaceContainerLowest=bg,surfaceContainerLow=surface,surfaceContainer=surface,surfaceContainerHigh=high,
                surfaceContainerHighest=high,surfaceDim=high,surfaceBright=surface,
                outline=c(0xFF8D7561),outlineVariant=c(0xFFD5C4AE),
                error=c(0xFF9A283D),onError=Color.White,errorContainer=c(0xFFFFDADF),onErrorContainer=c(0xFF3F0013),
                inverseSurface=ink,inverseOnSurface=bg,inversePrimary=c(0xFFE9BE9B),surfaceTint=c(0xFF754326)),c(0xFFECE0CE))
    }
    /** Legacy appearances keep their stored identities and palettes until the user confirms a replacement. */
    fun forAppearance(theme:NexusAppearance):NexusThemeSpec=when(theme) {
        NexusAppearance.AURORA->aurora
        NexusAppearance.OBSIDIAN->obsidian
        NexusAppearance.CHAMPAGNE->champagne
        NexusAppearance.EMERALD->emerald
        NexusAppearance.NOCTURNE->nocturne
        else->NexusThemeSpec(theme !in setOf(NexusAppearance.LIGHT,NexusAppearance.SAND,NexusAppearance.ROSE),
            if(theme in setOf(NexusAppearance.SAND,NexusAppearance.ROSE,NexusAppearance.LIGHT)) AmbientStyle.SILK else AmbientStyle.AURORA,
            if(theme in setOf(NexusAppearance.SAND,NexusAppearance.ROSE,NexusAppearance.LIGHT)) champagne.ambientColors else aurora.ambientColors,
            legacyPalette(theme),legacyChromeFor(theme))
    }
    fun visuals(theme:NexusAppearance):NexusVisualTokens {
        val spec=forAppearance(theme);val scheme=spec.colors;val dark=spec.isDark
        val success=if(dark)c(0xFF9DE2B8) else c(0xFF27633E)
        val warning=if(dark)c(0xFFECD099) else c(0xFF79520E)
        return NexusVisualTokens(scheme.surfaceContainerLow,scheme.outline,spec.chrome,
            success,if(dark)c(0xFF072115) else Color.White,warning,if(dark)c(0xFF292009) else Color.White,
            scheme.secondary,scheme.onSecondary,scheme.onSurfaceVariant,scheme.onSurfaceVariant,
            scheme.primaryContainer,scheme.surface,scheme.surfaceContainerHighest,scheme.surfaceContainerHigh,
            scheme.surfaceContainerLow,scheme.surfaceContainerLow,scheme.outlineVariant)
    }
}
