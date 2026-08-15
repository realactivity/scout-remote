package dev.lantern.app.brand

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import dev.lantern.core.domain.AgentState
import dev.lantern.core.domain.AgentStatus
import dev.lantern.core.domain.CommandStatus
import kotlin.time.Duration

val LocalBrandTokens = staticCompositionLocalOf<BrandTokens> {
    error("BrandTokens not provided")
}

val LocalBrandColors = staticCompositionLocalOf<BrandColors> {
    error("BrandColors not provided")
}

data class BrandColors(
    val midnight: Color,
    val steeple: Color,
    val bone: Color,
    val candle: Color,
    val idle: Color,
    val working: Color,
    val waiting: Color,
    val offline: Color,
    val error: Color,
    val cardFill: Color,
    val hairline: Color,
    val textSecondary: Color,
    val textLabel: Color,
    val textBody: Color,
) {
    companion object {
        fun from(tokens: BrandTokens): BrandColors = BrandColors(
            midnight = tokens.midnight.toColor(),
            steeple = tokens.steeple.toColor(),
            bone = tokens.bone.toColor(),
            candle = tokens.candle.toColor(),
            idle = tokens.idle.toColor(),
            working = tokens.working.toColor(),
            waiting = tokens.waiting.toColor(),
            offline = tokens.offline.toColor(),
            error = tokens.error.toColor(),
            cardFill = tokens.cardFill.toColor(),
            hairline = tokens.hairline.toColor(),
            textSecondary = tokens.textSecondary.toColor(),
            textLabel = tokens.textLabel.toColor(),
            textBody = tokens.textBody.toColor(),
        )
    }
}

fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))

/** Evidence: timestamps, IDs, protocol values. IBM Plex Mono role; system mono until fonts are vendored. */
val EvidenceMono: FontFamily = FontFamily.Monospace

/** Display / wordmark. Space Grotesk role. */
val DisplayFont: FontFamily = FontFamily.SansSerif

/** Product UI. Roboto Flex role; Roboto is native on Android. */
val UiFont: FontFamily = FontFamily.SansSerif

fun BrandColors.statusColor(
    status: AgentStatus?,
    nowUtc: kotlinx.datetime.Instant,
    staleThreshold: Duration,
): Color {
    if (status == null) {
        return offline
    }
    if (status.isStale(staleThreshold, nowUtc) || status.state == AgentState.UNKNOWN) {
        return offline
    }
    return when (status.state) {
        AgentState.IDLE -> idle
        AgentState.WORKING -> working
        AgentState.WAITING_FOR_DESKTOP -> waiting
        AgentState.UNKNOWN -> offline
    }
}

fun BrandColors.commandStatusColor(status: CommandStatus): Color = when (status) {
    CommandStatus.COMPLETED -> idle
    CommandStatus.ACKNOWLEDGED, CommandStatus.RUNNING -> working
    CommandStatus.WAITING_FOR_DESKTOP_APPROVAL -> waiting
    CommandStatus.FAILED -> error
    CommandStatus.EXPIRED, CommandStatus.CANCELED -> offline
}

@Composable
fun LanternTheme(tokens: BrandTokens, content: @Composable () -> Unit) {
    val colors = BrandColors.from(tokens)
    val scheme = darkColorScheme(
        primary = colors.candle,
        onPrimary = colors.midnight,
        background = colors.midnight,
        onBackground = colors.bone,
        surface = colors.steeple,
        onSurface = colors.bone,
        error = colors.error,
        onError = colors.bone,
        outline = colors.hairline,
    )
    CompositionLocalProvider(
        LocalBrandTokens provides tokens,
        LocalBrandColors provides colors,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
