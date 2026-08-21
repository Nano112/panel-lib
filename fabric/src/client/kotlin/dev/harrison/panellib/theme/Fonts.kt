package dev.harrison.panellib.theme

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import imgui.ImGuiIO

/**
 * Font atlas faces. [load] runs exactly once from ImGuiManager.initIfNeeded, before the single
 * `io.fonts.build()` (our renderer is the sole owner of the atlas texture). Refs stay null in
 * headless/unit-test contexts; [withFont] no-ops on null.
 */
object Fonts {
    private const val DIR = "/assets/panellib/fonts"

    /** Inter Regular — body (icons merged). */
    var BODY: ImFont? = null; private set
    /** Inter SemiBold — buttons, table headers, emphasis (icons merged). */
    var SEMIBOLD: ImFont? = null; private set
    /** Inter SemiBold, +3px — section headers. */
    var H2: ImFont? = null; private set
    /** Inter SemiBold, +7px — panel titles. */
    var H1: ImFont? = null; private set

    fun load(io: ImGuiIO, base: Float = Theme.current.fontSize) {
        val regular = res("$DIR/Inter-Regular.ttf")
        val semibold = res("$DIR/Inter-SemiBold.ttf")
        val icons = res("$DIR/fa-solid-900.ttf")

        BODY = io.fonts.addFontFromMemoryTTF(regular, base)
        mergeIcons(io, icons, base)
        SEMIBOLD = io.fonts.addFontFromMemoryTTF(semibold, base)
        mergeIcons(io, icons, base)
        H2 = io.fonts.addFontFromMemoryTTF(semibold, base + 3f)
        H1 = io.fonts.addFontFromMemoryTTF(semibold, base + 7f)
        io.fonts.build()
    }

    private fun mergeIcons(io: ImGuiIO, bytes: ByteArray, size: Float) {
        val cfg = ImFontConfig()
        cfg.mergeMode = true
        cfg.pixelSnapH = true
        cfg.glyphMinAdvanceX = size
        io.fonts.addFontFromMemoryTTF(bytes, size, cfg, Icons.glyphRanges())
        cfg.destroy()
    }

    private fun res(path: String): ByteArray =
        Fonts::class.java.getResourceAsStream(path)?.readBytes() ?: error("Missing bundled font: $path")
}

/** Push [font] for [block]; no-op when null. */
inline fun <T> withFont(font: ImFont?, block: () -> T): T {
    if (font != null) ImGui.pushFont(font)
    try { return block() } finally { if (font != null) ImGui.popFont() }
}
