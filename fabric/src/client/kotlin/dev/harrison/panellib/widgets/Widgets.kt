package dev.harrison.panellib.widgets

import dev.harrison.panellib.theme.Fonts
import dev.harrison.panellib.theme.Icons
import dev.harrison.panellib.theme.Theme
import dev.harrison.panellib.theme.lerp
import dev.harrison.panellib.theme.withFont
import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString

/**
 * The panel-lib widget kit — thin themed helpers over Dear ImGui. Every colour comes from
 * [Theme.current]. Buttons: primary (accent) for THE action of a view, secondary for everything
 * else, ghost for inline/low emphasis, danger for destructive.
 */
object Widgets {
    private val t: Theme get() = Theme.current

    enum class Tone { SUCCESS, WARNING, DANGER, INFO, NEUTRAL }

    // ---------------------------------------------------------------- buttons

    fun primaryButton(label: String, width: Float = 0f): Boolean =
        fadingButton(label, width, t.accent, t.accentHover, t.accentDim)

    fun dangerButton(label: String, width: Float = 0f): Boolean =
        fadingButton(label, width, t.danger, lerp(t.danger, t.text, 0.2f), lerp(t.danger, t.bg, 0.35f))

    private fun fadingButton(label: String, width: Float, base: ImVec4, hover: ImVec4, active: ImVec4): Boolean {
        val id = ImGui.getID(label)
        val bg = lerp(base, hover, Anim.peek(id))
        ImGui.pushStyleColor(ImGuiCol.Button, bg.x, bg.y, bg.z, bg.w)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, bg.x, bg.y, bg.z, bg.w)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, active.x, active.y, active.z, active.w)
        ImGui.pushStyleColor(ImGuiCol.Text, 1f, 1f, 1f, 1f)
        val clicked = withFont(Fonts.SEMIBOLD) { if (width > 0f) ImGui.button(label, width, 0f) else ImGui.button(label) }
        ImGui.popStyleColor(4)
        Anim.advance(id, ImGui.isItemHovered(), ImGui.getIO().deltaTime)
        return clicked
    }

    /** Neutral surface button with a subtle border — the default choice. */
    fun secondaryButton(label: String, width: Float = 0f): Boolean {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
        ImGui.pushStyleColor(ImGuiCol.Border, t.border.x, t.border.y, t.border.z, t.border.w)
        val clicked = if (width > 0f) ImGui.button(label, width, 0f) else ImGui.button(label)
        ImGui.popStyleColor(1)
        ImGui.popStyleVar(1)
        return clicked
    }

    /** Frameless low-emphasis button: transparent at rest, faint surface on hover. */
    fun ghostButton(label: String): Boolean {
        ImGui.pushStyleColor(ImGuiCol.Button, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, t.surfaceHover.x, t.surfaceHover.y, t.surfaceHover.z, t.surfaceHover.w)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, t.accentDim.x, t.accentDim.y, t.accentDim.z, t.accentDim.w)
        val clicked = ImGui.button(label)
        ImGui.popStyleColor(3)
        return clicked
    }

    /** Square frameless icon button with optional tooltip. */
    fun iconButton(icon: String, tooltip: String? = null): Boolean {
        val clicked = ghostButton(icon)
        if (tooltip != null && ImGui.isItemHovered()) ImGui.setTooltip(tooltip)
        return clicked
    }

    /** Toggle rendered as a pill-style button: accent when on. */
    fun toggleButton(label: String, on: Boolean, width: Float = 0f): Boolean =
        if (on) primaryButton(label, width) else secondaryButton(label, width)

    // ---------------------------------------------------------------- typography

    fun h1(text: String) = withFont(Fonts.H1) { ImGui.text(text) }
    fun h2(text: String) = withFont(Fonts.H2) { ImGui.text(text) }
    fun semibold(text: String) = withFont(Fonts.SEMIBOLD) { ImGui.text(text) }

    fun sectionHeader(text: String) { ImGui.spacing(); h2(text); ImGui.spacing() }

    fun muted(text: String) = ImGui.textColored(t.textMuted.x, t.textMuted.y, t.textMuted.z, t.textMuted.w, text)
    fun faint(text: String) = ImGui.textColored(t.textFaint.x, t.textFaint.y, t.textFaint.z, t.textFaint.w, text)
    fun colored(text: String, c: ImVec4) = ImGui.textColored(c.x, c.y, c.z, c.w, text)

    /** Uppercase faint section label with a tight bottom gap. */
    fun label(text: String) { faint(text.uppercase()); ImGui.dummy(0f, 1f) }

    /** Muted `label: value` row with the value column aligned at [valueX]. */
    fun kvRow(label: String, value: String, valueX: Float = 150f) {
        faint(label)
        ImGui.sameLine(valueX)
        ImGui.text(value)
    }

    // ---------------------------------------------------------------- status & structure

    /** Rounded tinted pill — role/status chips. */
    fun badge(text: String, tone: Tone) {
        val c = toneColor(tone)
        val padX = 7f; val padY = 2f
        val size = ImGui.calcTextSize(text)
        val x = ImGui.getCursorScreenPosX(); val y = ImGui.getCursorScreenPosY()
        val w = size.x + padX * 2; val h = size.y + padY * 2
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(x, y, x + w, y + h, ImGui.getColorU32(c.x, c.y, c.z, 0.16f), h / 2f)
        dl.addRect(x, y, x + w, y + h, ImGui.getColorU32(c.x, c.y, c.z, 0.5f), h / 2f)
        dl.addText(x + padX, y + padY, ImGui.getColorU32(c.x, c.y, c.z, 1f), text)
        ImGui.dummy(w, h)
    }

    /** Small filled dot + text, e.g. connection status. */
    fun dot(text: String, tone: Tone) {
        val c = toneColor(tone)
        val y = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() / 2f
        val x = ImGui.getCursorScreenPosX() + 5f
        ImGui.getWindowDrawList().addCircleFilled(x, y, 4f, ImGui.getColorU32(c.x, c.y, c.z, 1f))
        ImGui.dummy(12f, ImGui.getTextLineHeight())
        ImGui.sameLine(0f, 4f)
        ImGui.text(text)
    }

    /** Centered icon + title (+ optional hint) filling the remaining region — zero states. */
    fun emptyState(icon: String, title: String, hint: String? = null) {
        val availX = ImGui.getContentRegionAvailX()
        val availY = ImGui.getContentRegionAvailY()
        val lineH = ImGui.getTextLineHeightWithSpacing()
        val blockH = lineH * (if (hint != null) 3 else 2)
        if (availY > blockH) ImGui.dummy(0f, (availY - blockH) / 2f)
        fun centered(text: String, c: ImVec4) {
            val w = ImGui.calcTextSize(text).x
            ImGui.setCursorPosX(ImGui.getCursorPosX() + ((availX - w) / 2f).coerceAtLeast(0f))
            ImGui.textColored(c.x, c.y, c.z, c.w, text)
        }
        centered(icon, t.textFaint)
        withFont(Fonts.SEMIBOLD) { centered(title, t.textMuted) }
        if (hint != null) {
            if (ImGui.calcTextSize(hint).x <= availX) centered(hint, t.textFaint) else {
                ImGui.pushStyleColor(ImGuiCol.Text, t.textFaint.x, t.textFaint.y, t.textFaint.z, t.textFaint.w)
                ImGui.textWrapped(hint)
                ImGui.popStyleColor(1)
            }
        }
    }

    /** Coloured status line with a matching leading icon. */
    fun statusText(text: String, tone: Tone) {
        val c = toneColor(tone)
        val icon = when (tone) {
            Tone.SUCCESS -> Icons.CHECK_CIRCLE; Tone.DANGER -> Icons.XMARK_CIRCLE
            Tone.WARNING -> Icons.WARNING; Tone.INFO -> Icons.INFO_CIRCLE; Tone.NEUTRAL -> Icons.CIRCLE
        }
        ImGui.textColored(c.x, c.y, c.z, c.w, "$icon  $text")
    }

    fun toneColor(tone: Tone): ImVec4 = when (tone) {
        Tone.SUCCESS -> t.success; Tone.WARNING -> t.warning; Tone.DANGER -> t.danger
        Tone.INFO -> t.info; Tone.NEUTRAL -> t.textMuted
    }

    // ---------------------------------------------------------------- inputs

    /** Single-line text input — the one control that keeps a visible 1px frame border. */
    fun textField(label: String, state: ImString, hint: String? = null, width: Float = 0f): Boolean {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 1f)
        ImGui.pushStyleColor(ImGuiCol.Border, t.border.x, t.border.y, t.border.z, t.border.w)
        if (width > 0f) ImGui.setNextItemWidth(width)
        val changed = if (hint != null) ImGui.inputTextWithHint(label, hint, state) else ImGui.inputText(label, state)
        ImGui.popStyleColor(1)
        ImGui.popStyleVar(1)
        return changed
    }

    fun tabBar(id: String, tabs: List<Pair<String, () -> Unit>>) {
        if (ImGui.beginTabBar(id)) {
            for ((title, content) in tabs) {
                if (ImGui.beginTabItem(title)) { content(); ImGui.endTabItem() }
            }
            ImGui.endTabBar()
        }
    }

    /** Striped table with horizontal inner borders and vertical scrolling. */
    inline fun table(id: String, columns: Int, extraFlags: Int = 0, block: () -> Unit) {
        val flags = ImGuiTableFlags.RowBg or ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.ScrollY or extraFlags
        if (ImGui.beginTable(id, columns, flags)) {
            try { block() } finally { ImGui.endTable() }
        }
    }
}
