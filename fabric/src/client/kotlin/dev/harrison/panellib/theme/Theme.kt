package dev.harrison.panellib.theme

import imgui.ImVec4

/** Parse `#RRGGBB` / `#AARRGGBB` / `0xAARRGGBB` into an ARGB int. */
fun parseArgb(s: String): Int {
    val h = s.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    require(h.length == 6 || h.length == 8) { "colour must be #RRGGBB or #AARRGGBB: '$s'" }
    val v = h.toLong(16)
    return if (h.length == 6) (0xFF000000L or v).toInt() else v.toInt()
}

/** ARGB int → ImVec4 (r, g, b, a) in 0..1. */
fun argbToImVec4(argb: Int): ImVec4 {
    val a = (argb ushr 24 and 0xFF) / 255f
    val r = (argb ushr 16 and 0xFF) / 255f
    val g = (argb ushr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return ImVec4(r, g, b, a)
}

fun rgba(hex: String): ImVec4 = argbToImVec4(parseArgb(hex))

/** Per-channel blend of [a] toward [b] by [t] (clamped 0..1). */
fun lerp(a: ImVec4, b: ImVec4, t: Float): ImVec4 {
    val tt = t.coerceIn(0f, 1f)
    return ImVec4(a.x + (b.x - a.x) * tt, a.y + (b.y - a.y) * tt, a.z + (b.z - a.z) * tt, a.w + (b.w - a.w) * tt)
}

fun ImVec4.withAlpha(alpha: Float): ImVec4 = ImVec4(x, y, z, alpha)

/**
 * The complete set of colour tokens and metrics the overlay renders with.
 * Consumers customise via `copy(...)` (e.g. `Themes.DEFAULT.copy(accent = rgba("#ff00aa"))`)
 * and `PanelLibApi.setTheme`. Widgets must only ever read colours from the current theme.
 */
data class Theme(
    val name: String,
    // surfaces
    val bg: ImVec4,
    val surface: ImVec4,
    val surfaceAlt: ImVec4,
    val surfaceHover: ImVec4,
    val surfaceRaised: ImVec4,
    // lines
    val border: ImVec4,
    val borderSubtle: ImVec4,
    // text
    val text: ImVec4,
    val textSecondary: ImVec4,
    val textMuted: ImVec4,
    val textFaint: ImVec4,
    // accent
    val accent: ImVec4,
    val accentHover: ImVec4,
    val accentDim: ImVec4,
    val accentMuted: ImVec4,
    // semantic
    val success: ImVec4,
    val danger: ImVec4,
    val warning: ImVec4,
    val info: ImVec4,
    // misc
    val scrim: ImVec4,
    val stripe: ImVec4,
    // metrics
    val windowPadding: Float = 12f,
    val framePaddingX: Float = 9f,
    val framePaddingY: Float = 6f,
    val itemSpacingX: Float = 8f,
    val itemSpacingY: Float = 6f,
    val windowRounding: Float = 6f,
    val frameRounding: Float = 4f,
    val popupRounding: Float = 6f,
    val scrollbarSize: Float = 10f,
    /** Base font size in pixels (body + semibold). Headings scale from it. */
    val fontSize: Float = 17f,
) {
    companion object {
        val TRANSPARENT = ImVec4(0f, 0f, 0f, 0f)

        /** The theme every frame is rendered with. Set via [PanelLibApi.setTheme]. */
        @Volatile var current: Theme = Themes.DEFAULT
    }
}
