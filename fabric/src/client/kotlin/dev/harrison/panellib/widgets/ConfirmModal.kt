package dev.harrison.panellib.widgets

import dev.harrison.panellib.theme.Fonts
import dev.harrison.panellib.theme.Icons
import dev.harrison.panellib.theme.Theme
import dev.harrison.panellib.theme.withFont
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags

/** Single shared confirm dialog. [show] from any render-thread code; rendered by the overlay each frame. */
object ConfirmModal {
    private const val POPUP_ID = "##panellib-confirm"

    private var title = ""
    private var message = ""
    private var confirmLabel = "Confirm"
    private var danger = false
    private var onConfirm: (() -> Unit)? = null
    private var pendingOpen = false

    fun isOpen(): Boolean = pendingOpen || onConfirm != null

    fun show(title: String, message: String, confirmLabel: String = "Confirm", danger: Boolean = false, onConfirm: () -> Unit) {
        this.title = title; this.message = message; this.confirmLabel = confirmLabel; this.danger = danger
        this.onConfirm = onConfirm
        pendingOpen = true
    }

    fun render() {
        if (pendingOpen) { ImGui.openPopup(POPUP_ID); pendingOpen = false }
        val t = Theme.current
        val io = ImGui.getIO()
        ImGui.setNextWindowPos(io.displaySizeX / 2f, io.displaySizeY / 2f, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(340f, 0f)
        val flags = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.AlwaysAutoResize
        if (ImGui.beginPopupModal(POPUP_ID, flags)) {
            val c = if (danger) t.danger else t.text
            ImGui.textColored(c.x, c.y, c.z, c.w, if (danger) Icons.WARNING else Icons.INFO_CIRCLE)
            ImGui.sameLine(0f, 8f)
            withFont(Fonts.H2) { ImGui.textColored(c.x, c.y, c.z, c.w, title) }
            ImGui.spacing(); ImGui.textWrapped(message); ImGui.spacing(); ImGui.separator(); ImGui.spacing()
            val btnW = 100f
            val startX = ImGui.getWindowWidth() - ImGui.getStyle().windowPaddingX - (btnW * 2 + ImGui.getStyle().itemSpacingX)
            if (startX > ImGui.getCursorPosX()) ImGui.setCursorPosX(startX)
            if (Widgets.secondaryButton("Cancel", btnW)) { onConfirm = null; ImGui.closeCurrentPopup() }
            ImGui.sameLine()
            val confirmed = if (danger) Widgets.dangerButton(confirmLabel, btnW) else Widgets.primaryButton(confirmLabel, btnW)
            if (confirmed) { val cb = onConfirm; onConfirm = null; ImGui.closeCurrentPopup(); cb?.invoke() }
            ImGui.endPopup()
        }
    }
}
