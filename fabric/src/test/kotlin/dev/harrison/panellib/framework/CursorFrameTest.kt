package dev.harrison.panellib.framework

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real Dear ImGui hover/capture calculations; no GLFW window or graphics context required. */
class CursorFrameTest {
    private fun withContext(test: () -> Unit) {
        val previous = ImGui.getCurrentContext()
        val context = ImGui.createContext()
        try {
            val io = ImGui.getIO()
            io.iniFilename = null
            io.setDisplaySize(854f, 480f)
            io.deltaTime = 1f / 60f
            io.fonts.addFontDefault()
            io.fonts.build()
            frame(0f, 0f) // Establish the panel's bounds before checking hover.
            test()
        } finally {
            ImGui.destroyContext(context)
            ImGui.setCurrentContext(previous)
        }
    }

    private fun frame(x: Float, y: Float, focused: Boolean = true) {
        CursorFrame.begin(focused) { ImGui.getIO().addMousePosEvent(x, y) }
        ImGui.setNextWindowPos(100f, 100f, ImGuiCond.Always)
        ImGui.setNextWindowSize(300f, 250f, ImGuiCond.Always)
        ImGui.begin("Inspector", ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize)
        ImGui.text("Panel controls")
        ImGui.end()
        ImGui.render()
    }

    @Test fun movingFromGameToPanelUpdatesCaptureInTheSameFrame() = withContext {
        frame(0f, 0f)
        assertFalse(ImGui.getIO().wantCaptureMouse)
        frame(150f, 150f)
        assertTrue(ImGui.getIO().wantCaptureMouse, "A panel click must not be routed back to the game")
        frame(0f, 0f)
        assertFalse(ImGui.getIO().wantCaptureMouse, "The game area must still support click-through")
    }

    @Test fun returningFromGameFocusRestoresPanelHover() = withContext {
        frame(150f, 150f, focused = false)
        assertFalse(ImGui.getIO().wantCaptureMouse)
        frame(150f, 150f)
        assertTrue(ImGui.getIO().wantCaptureMouse)
    }

    @Test fun gameFocusReleasesCapturedMouseButtons() = withContext {
        ImGui.getIO().addMouseButtonEvent(0, true)
        frame(150f, 150f)
        assertTrue(ImGui.isMouseDown(0))
        frame(150f, 150f, focused = false)
        assertFalse(ImGui.isMouseDown(0))
    }
}
