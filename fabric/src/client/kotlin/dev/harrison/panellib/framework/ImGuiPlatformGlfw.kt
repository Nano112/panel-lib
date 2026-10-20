//? if <26.3 {
package dev.harrison.panellib.framework

import imgui.ImGui
import imgui.glfw.ImGuiImplGlfw
import org.lwjgl.glfw.GLFW

/**
 * ImGui platform glue for Minecraft's GLFW window (every target before 26.3).
 * Thin wrapper over imgui-java's [ImGuiImplGlfw]; 26.3+ uses the SDL3 variant in ImGuiPlatformSdl.kt.
 */
internal object ImGuiPlatform {
    private val imGuiGlfw = ImGuiImplGlfw()

    /** GLFW can host ImGui's secondary OS windows (config `external_windows`). */
    const val supportsViewports = true

    fun init(window: Long) {
        // imgui-java 1.89 installs a global monitor callback even with installCallbacks=false.
        // Restore its predecessor before returning to GLFW's event loop; poll our monitor list instead.
        val previousMonitor = GLFW.glfwSetMonitorCallback(null)
        try { imGuiGlfw.init(window, false) } finally {
            val installed = GLFW.glfwSetMonitorCallback(previousMonitor)
            if (installed != null && installed.address() != previousMonitor?.address()) installed.free()
        }
        // ImGuiImplGlfw only polls the cursor when mouseWindow != -1, which only a cursor-enter
        // callback sets; with installCallbacks=false we must prime it and keep it updated.
        imGuiGlfw.cursorEnterCallback(window, true)
    }

    fun shutdown() = imGuiGlfw.shutdown()

    fun newFrame() = imGuiGlfw.newFrame()

    /** No-op: on GLFW the cursor shape is owned by ImGuiImplGlfw, as it always was. */
    fun resetCursor() {}

    fun framebufferSize(window: Long): Pair<Int, Int> {
        val w = IntArray(1); val h = IntArray(1)
        GLFW.glfwGetFramebufferSize(window, w, h)
        return w[0] to h[0]
    }

    fun windowSize(window: Long): Pair<Int, Int> {
        val w = IntArray(1); val h = IntArray(1)
        GLFW.glfwGetWindowSize(window, w, h)
        return w[0] to h[0]
    }

    fun isHovered(window: Long): Boolean = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE

    fun cursorPos(window: Long): Pair<Double, Double> {
        val x = DoubleArray(1); val y = DoubleArray(1)
        GLFW.glfwGetCursorPos(window, x, y)
        return x[0] to y[0]
    }

    fun pollMonitors() = imGuiGlfw.monitorCallback(0L, 0)
    fun showWindow(window: Long) = GLFW.glfwShowWindow(window)
    fun hideWindow(window: Long) = GLFW.glfwHideWindow(window)
    fun makeContextCurrent(window: Long) = GLFW.glfwMakeContextCurrent(window)

    // Input forwarders. Values are Minecraft's (= GLFW's) key/button/action/modifier codes.
    fun cursorEnter(window: Long, entered: Boolean) = imGuiGlfw.cursorEnterCallback(window, entered)
    fun cursorPosEvent(window: Long, x: Double, y: Double) = imGuiGlfw.cursorPosCallback(window, x, y)
    fun mouseButton(window: Long, button: Int, action: Int, mods: Int) = imGuiGlfw.mouseButtonCallback(window, button, action, mods)
    fun scroll(window: Long, xOffset: Double, yOffset: Double) = imGuiGlfw.scrollCallback(window, xOffset, yOffset)
    fun key(window: Long, key: Int, scancode: Int, action: Int, mods: Int) = imGuiGlfw.keyCallback(window, key, scancode, action, mods)
    fun char(window: Long, codepoint: Int) = imGuiGlfw.charCallback(window, codepoint)

    /** Marks the main viewport hovered while synthetic (non-OS) input drives Minecraft. */
    fun markMainViewportHovered() = ImGui.getIO().addMouseViewportEvent(ImGui.getMainViewport().id)
}
//?}
