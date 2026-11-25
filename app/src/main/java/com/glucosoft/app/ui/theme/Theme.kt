package com.glucosoft.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.glucosoft.app.R
import com.glucosoft.app.model.GlucoseSeverity
import com.glucosoft.app.model.TextScaleMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val DmSansFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

// Base accent colors
val TealPrimary = Color(0xFF0B5D56)
val TealPrimaryDark = Color(0xFF2DD4BF)
val EmeraldNormal = Color(0xFF047857)
val EmeraldNormalDark = Color(0xFF34D399)
val AmberCaution = Color(0xFFB45309)
val AmberCautionDark = Color(0xFFFBBF24)
val RoseAlert = Color(0xFFBE123C)
val RoseAlertDark = Color(0xFFFB7185)

/**
 * Pareja de colores de alto contraste (mínimo 4,5:1 garantizado, >8:1 real)
 * para mostrar estados clínicos orientativos en tema claro y oscuro.
 */
@Immutable
data class StatusColorPair(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val dotColor: Color
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

fun statusColorPairFor(severity: GlucoseSeverity, isDark: Boolean): StatusColorPair {
    return if (!isDark) {
        when (severity) {
            GlucoseSeverity.NORMAL -> StatusColorPair(
                containerColor = Color(0xFFDCFCE7), // Verde esmeralda muy claro
                contentColor = Color(0xFF064E3B),   // Verde esmeralda profundo (contraste ~8.7:1)
                borderColor = Color(0xFF047857),
                dotColor = Color(0xFF047857)
            )
            GlucoseSeverity.CAUTION -> StatusColorPair(
                containerColor = Color(0xFFFEF3C7), // Ámbar crema claro
                contentColor = Color(0xFF78350F),   // Ámbar oscuro intenso (contraste ~9.3:1)
                borderColor = Color(0xFFB45309),
                dotColor = Color(0xFFB45309)
            )
            GlucoseSeverity.ALERT -> StatusColorPair(
                containerColor = Color(0xFFFFE4E6), // Rosa claro
                contentColor = Color(0xFF881337),   // Rosa carmesí profundo (contraste ~9.4:1)
                borderColor = Color(0xFFBE123C),
                dotColor = Color(0xFFBE123C)
            )
        }
    } else {
        when (severity) {
            GlucoseSeverity.NORMAL -> StatusColorPair(
                containerColor = Color(0xFF064E3B), // Fondo verde esmeralda profundo
                contentColor = Color(0xFFECFDF5),   // Texto verde menta muy claro (contraste ~9.2:1)
                borderColor = Color(0xFF34D399),
                dotColor = Color(0xFF34D399)
            )
            GlucoseSeverity.CAUTION -> StatusColorPair(
                containerColor = Color(0xFF451A03), // Fondo ámbar profundo
                contentColor = Color(0xFFFEF3C7),   // Texto ámbar crema luminoso (contraste ~12.7:1)
                borderColor = Color(0xFFFBBF24),
                dotColor = Color(0xFFFBBF24)
            )
            GlucoseSeverity.ALERT -> StatusColorPair(
                containerColor = Color(0xFF4C0519), // Fondo carmesí profundo
                contentColor = Color(0xFFFFE4E6),   // Texto rosa muy claro (contraste ~12.3:1)
                borderColor = Color(0xFFFB7185),
                dotColor = Color(0xFFFB7185)
            )
        }
    }
}

@Composable
fun rememberStatusColorPair(severity: GlucoseSeverity): StatusColorPair {
    val isDark = LocalIsDarkTheme.current
    return statusColorPairFor(severity, isDark)
}

/**
 * Calcula la razón de contraste WCAG 2.1 entre dos colores sRGB.
 */
fun calculateContrastRatio(foreground: Color, background: Color): Double {
    fun channelLuminance(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    val l1 = relativeLuminance(foreground)
    val l2 = relativeLuminance(background)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),
    tertiary = RoseAlert,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    error = RoseAlert,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = RoseAlertDark,
    onTertiary = Color(0xFF4C0519),
    background = Color(0xFF090D16),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    error = RoseAlertDark,
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFE4E6)
)

/**
 * Tipografía pensada para personas mayores:
 * Cuerpo base de 18–20 sp, interlineado cómodo (~1,45–1,5x), salto por palabras completas
 * (sin partir palabras a la mitad) y escala adicional en modo Letra Grande.
 */
fun buildTypography(scaleMode: TextScaleMode): Typography {
    val factor = if (scaleMode == TextScaleMode.LARGE) 1.15f else 1.0f
    return Typography(
        displayLarge = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (46 * factor).sp,
            lineHeight = (56 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        headlineMedium = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (24 * factor).sp,
            lineHeight = (35 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        titleLarge = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (21 * factor).sp,
            lineHeight = (30 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        titleMedium = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (19 * factor).sp,
            lineHeight = (28 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        bodyLarge = TextStyle(
            fontFamily = DmSansFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (19 * factor).sp,
            lineHeight = (28 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        bodyMedium = TextStyle(
            fontFamily = DmSansFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (18 * factor).sp,
            lineHeight = (27 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        labelLarge = TextStyle(
            fontFamily = DmSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (17 * factor).sp,
            lineHeight = (25 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        ),
        labelMedium = TextStyle(
            fontFamily = DmSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (15.5f * factor).sp,
            lineHeight = (23 * factor).sp,
            lineBreak = LineBreak.Simple,
            hyphens = Hyphens.None
        )
    )
}

@Composable
fun GlucosoftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScaleMode: TextScaleMode,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val typography = buildTypography(textScaleMode)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
