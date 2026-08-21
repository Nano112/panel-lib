package dev.harrison.panellib.framework

import dev.harrison.panellib.compat.Compat
import dev.harrison.panellib.theme.Fonts
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiConfigFlags
import imgui.glfw.ImGuiImplGlfw
import org.lwjgl.glfw.GLFW

/** Owns the ImGui context, GLFW backend glue and frame lifecycle. Render thread only. */
object ImGuiManager {
    private val imGuiGlfw = ImGuiImplGlfw()
    @Volatile var initialized = false; private set
    private var windowHandle = 0L
    private const val INI_FILENAME = "panellib-imgui.ini"

    /** Unicode codepoints typed since last drain (for custom text widgets that bypass InputText). */
    private val typedChars = java.util.concurrent.ConcurrentLinkedQueue<Int>()

    fun initIfNeeded() {
        if (initialized) return
        try {
            val wh = Compat.windowHandle()
            windowHandle = wh
            ImGui.createContext()
            val io: ImGuiIO = ImGui.getIO()
            io.iniFilename = INI_FILENAME
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)
            // Docking ON (full-viewport DockHost + dockable panels; layout persisted in the ini).
            // Multi-viewport stays OFF: single overlay into MC's framebuffer.
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
            io.fonts.clear()
            Fonts.load(io)
            imGuiGlfw.init(wh, false) // installCallbacks=false; our mixins forward input
            // ImGuiImplGlfw only polls the cursor when mouseWindow != -1, which only a cursor-enter
            // callback sets; with installCallbacks=false we must prime it and keep it updated.
            imGuiGlfw.cursorEnterCallback(wh, true)
            GLFW.glfwSetCursorEnterCallback(wh) { _, entered -> imGuiGlfw.cursorEnterCallback(wh, entered) }
            // Our renderer is the sole owner of the font atlas texture (see ImGuiGl3Renderer).
            ImGuiGl3Renderer.initIfNeeded()
            initialized = true
            PanelLibLog.LOGGER.info("[panel-lib] ImGui initialised")
        } catch (e: Throwable) {
            PanelLibLog.LOGGER.error("[panel-lib] ImGui init failed; overlay disabled", e)
        }
    }

    fun startFrame(focused: Boolean) {
        imGuiGlfw.newFrame()
        ImGui.newFrame()
        val io = ImGui.getIO()
        if (!focused) {
            io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in 0 until 5) io.setMouseDown(i, false)
        } else if (windowHandle != 0L) {
            // Safety net: push the real cursor position after newFrame so ImGui always has a valid one.
            val mx = DoubleArray(1); val my = DoubleArray(1)
            GLFW.glfwGetCursorPos(windowHandle, mx, my)
            io.setMousePos(mx[0].toFloat(), my[0].toFloat())
        }
    }

    fun endFrame() {
        ImGui.render()
        ImGuiGl3Renderer.render(ImGui.getDrawData())
    }

    fun shutdown() {
        if (!initialized) return
        if (windowHandle != 0L) GLFW.glfwSetCursorEnterCallback(windowHandle, null)?.free()
        ImGuiGl3Renderer.shutdown()
        imGuiGlfw.shutdown()
        ImGui.destroyContext()
        initialized = false
        windowHandle = 0L
    }

    // Input forwarders (called from the mixins).
    @JvmStatic fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) = imGuiGlfw.mouseButtonCallback(window, button, action, mods)
    @JvmStatic fun scrollCallback(window: Long, xOffset: Double, yOffset: Double) = imGuiGlfw.scrollCallback(window, xOffset, yOffset)
    @JvmStatic fun keyCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) = imGuiGlfw.keyCallback(window, key, scancode, action, mods)
    @JvmStatic fun charCallback(window: Long, codepoint: Int) { imGuiGlfw.charCallback(window, codepoint); typedChars.add(codepoint) }

    /** Drain typed characters (oldest first) for custom text widgets. */
    fun drainTypedChars(): List<Int> {
        if (typedChars.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        while (true) out.add(typedChars.poll() ?: break)
        return out
    }
}
