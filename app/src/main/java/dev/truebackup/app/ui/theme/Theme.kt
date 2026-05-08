package dev.truebackup.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── TrueBackup Dark Palette ──────────────────────────────────────────────────
val TbBackground       = Color(0xFF0D0D12)
val TbSurface          = Color(0xFF1A1A24)
val TbSurfaceVariant   = Color(0xFF252533)
val TbSurfaceContainer = Color(0xFF1E1E2C)

val TbPrimary          = Color(0xFF9B78FF)   // electric violet
val TbPrimaryContainer = Color(0xFF3A2080)
val TbOnPrimary        = Color(0xFFFFFFFF)
val TbOnPrimaryContainer = Color(0xFFE0CFFF)

val TbSecondary        = Color(0xFF625B71)
val TbSecondaryContainer = Color(0xFF4A4459)
val TbOnSecondary      = Color(0xFFFFFFFF)

val TbTertiary         = Color(0xFF7DDFFF)   // cyan accent
val TbTertiaryContainer= Color(0xFF0D4A5C)

val TbError            = Color(0xFFCF6679)
val TbErrorContainer   = Color(0xFF5C1622)
val TbOnError          = Color(0xFFFFFFFF)

val TbOutline          = Color(0xFF4A4A62)
val TbOutlineVariant   = Color(0xFF2E2E42)

val TbOnBackground     = Color(0xFFE8E8F2)
val TbOnSurface        = Color(0xFFE0DFEE)
val TbOnSurfaceVariant = Color(0xFFAFAEC2)

// Glow / accent colours for custom effects
val TbAccentViolet     = Color(0xFFBB86FC)
val TbAccentCyan       = Color(0xFF7DDFFF)
val TbAccentGreen      = Color(0xFF4CAF7D)
val TbAccentAmber      = Color(0xFFFFC107)

private val DarkColorScheme = darkColorScheme(
    primary            = TbPrimary,
    onPrimary          = TbOnPrimary,
    primaryContainer   = TbPrimaryContainer,
    onPrimaryContainer = TbOnPrimaryContainer,
    secondary          = TbSecondary,
    onSecondary        = TbOnSecondary,
    secondaryContainer = TbSecondaryContainer,
    onSecondaryContainer = Color(0xFFD0CADF),
    tertiary           = TbTertiary,
    tertiaryContainer  = TbTertiaryContainer,
    onTertiary         = Color(0xFF003543),
    error              = TbError,
    errorContainer     = TbErrorContainer,
    onError            = TbOnError,
    background         = TbBackground,
    onBackground       = TbOnBackground,
    surface            = TbSurface,
    onSurface          = TbOnSurface,
    surfaceVariant     = TbSurfaceVariant,
    onSurfaceVariant   = TbOnSurfaceVariant,
    outline            = TbOutline,
    outlineVariant     = TbOutlineVariant,
    scrim              = Color(0x80000000),
    inverseSurface     = Color(0xFFE5E1EE),
    inverseOnSurface   = Color(0xFF1A1A24),
    inversePrimary     = Color(0xFF5B2DA0),
)

@Composable
fun TrueBackupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = TrueBackupTypography,
        shapes      = TrueBackupShapes,
        content     = content
    )
}
