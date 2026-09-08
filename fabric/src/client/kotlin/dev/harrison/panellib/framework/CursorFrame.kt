package dev.harrison.panellib.framework

import imgui.ImGui

/** Queue the current input before Dear ImGui resolves hover/capture for this frame. */
object CursorFrame {
    fun begin(focused: Boolean, feedCursor: () -> Unit) {
        val io = ImGui.getIO()
        if (focused) feedCursor() else {
            io.addMousePosEvent(-Float.MAX_VALUE, -Float.MAX_VALUE)
            for (button in 0 until 5) io.addMouseButtonEvent(button, false)
        }
        ImGui.newFrame()
    }
}
