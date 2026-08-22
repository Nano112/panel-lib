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
    /** Opt-in (config `external_windows`): panels dragged outside the game become their own OS windows. */
    @Volatile var externalWindows: Boolean = false
    var viewportsActive = false
        private set
    /** See PanelLibApi.markSyntheticInput. */
    @Volatile var lastSyntheticInputAt: Long = 0
    private fun syntheticRecently() = System.currentTimeMillis() - lastSyntheticInputAt < 2_000
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
            // Keyboard nav stays OFF: with it on, WantCaptureKeyboard is true whenever any ImGui window is
            // focused, which would swallow every game key (hotkeys, keybinds) while the overlay is open.
            // Without it, keys reach ImGui only while a text input is active or a modal is open.
            // Docking ON (full-viewport DockHost + dockable panels; layout persisted in the ini).
            // Multi-viewport stays OFF: single overlay into MC's framebuffer.
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
            if (externalWindows) {
                // Multi-viewport: ImGui's GLFW backend creates secondary windows (sharing MC's GL context);
                // we render each one with our renderer from Renderer_RenderWindow.
                io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                io.configViewportsNoAutoMerge = false
                io.configViewportsNoTaskBarIcon = true
                viewportsActive = true
            }
            io.fonts.clear()
            Fonts.load(io, pixelRatio = pixelRatio(wh))
            imGuiGlfw.init(wh, false) // installCallbacks=false; our mixins forward input
            // ImGuiImplGlfw only polls the cursor when mouseWindow != -1, which only a cursor-enter
            // callback sets; with installCallbacks=false we must prime it and keep it updated.
            imGuiGlfw.cursorEnterCallback(wh, true)
            GLFW.glfwSetCursorEnterCallback(wh) { _, entered -> imGuiGlfw.cursorEnterCallback(wh, entered) }
            // Our renderer is the sole owner of the font atlas texture (see ImGuiGl3Renderer).
            ImGuiGl3Renderer.initIfNeeded()
            if (viewportsActive) {
                // Tell ImGui our renderer can draw secondary viewports (the GLFW backend sets PlatformHasViewports).
                io.addBackendFlags(imgui.flag.ImGuiBackendFlags.RendererHasViewports)
                val pio = ImGui.getPlatformIO()
                pio.setRendererRenderWindow(object : imgui.callback.ImPlatformFuncViewport() {
                    override fun accept(vp: imgui.ImGuiViewport) { ImGuiGl3Renderer.renderViewport(vp.platformHandle, vp.drawData) }
                })
                pio.setRendererDestroyWindow(object : imgui.callback.ImPlatformFuncViewport() {
                    override fun accept(vp: imgui.ImGuiViewport) { ImGuiGl3Renderer.forgetViewport(vp.platformHandle) }
                })
            }
            initialized = true
            PanelLibLog.LOGGER.info("[panel-lib] ImGui initialised")
        } catch (e: Throwable) {
            PanelLibLog.LOGGER.error("[panel-lib] ImGui init failed; overlay disabled", e)
        }
    }

    /** Framebuffer px per logical window unit (2 on Retina), from GLFW. */
    private fun pixelRatio(window: Long): Float {
        val fw = IntArray(1); val fh = IntArray(1); val ww = IntArray(1); val whh = IntArray(1)
        GLFW.glfwGetFramebufferSize(window, fw, fh); GLFW.glfwGetWindowSize(window, ww, whh)
        return if (ww[0] > 0) fw[0].toFloat() / ww[0] else 1f
    }

    fun startFrame(focused: Boolean) {
        imGuiGlfw.newFrame()
        ImGui.newFrame()
        val io = ImGui.getIO()
        if (!focused) {
            io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in 0 until 5) io.setMouseDown(i, false)
        } else {
            // Use Minecraft's notion of the cursor (fed by MouseHandler.onMove) rather than polling GLFW:
            // it is always valid, and synthetic input injected at the MouseHandler level (automation,
            // MC-Inspector's MCP host) moves it while the OS cursor stays put.
            val mh = net.minecraft.client.Minecraft.getInstance().mouseHandler
            if (!viewportsActive) {
                io.setMousePos(mh.xpos().toFloat(), mh.ypos().toFloat())
            } else {
                // With viewports, MousePos is in desktop coordinates and we install no cursor callback on MC's window
                // (our mixins handle MC input), so positions for the game window must come from us every frame:
                //  - synthetic input recently (automation): Minecraft's cursor (MouseHandler) + window pos
                //  - real cursor over the game window: the OS cursor + window pos
                //  - real cursor over one of our external windows: their own GLFW callbacks feed ImGui; do nothing
                val mv = ImGui.getMainViewport()
                val ox = mv?.posX ?: 0f; val oy = mv?.posY ?: 0f
                if (syntheticRecently()) {
                    val x = ox + mh.xpos().toFloat(); val y = oy + mh.ypos().toFloat()
                    io.setMousePos(x, y); io.addMousePosEvent(x, y)
                } else if (GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE) {
                    val cx = DoubleArray(1); val cy = DoubleArray(1)
                    GLFW.glfwGetCursorPos(windowHandle, cx, cy)
                    val x = ox + cx[0].toFloat(); val y = oy + cy[0].toFloat()
                    io.setMousePos(x, y); io.addMousePosEvent(x, y)
                }
            }
        }
    }

    /** True when the OS cursor is over the game window or any of our external viewport windows. */
    private fun anyOwnWindowHovered(): Boolean {
        if (GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE) return true
        val pio = ImGui.getPlatformIO()
        for (i in 1 until pio.viewportsSize) {
            val h = pio.getViewports(i).platformHandle
            if (h != 0L && GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE) return true
        }
        return false
    }

    fun endFrame() {
        ImGui.render()
        ImGuiGl3Renderer.render(ImGui.getDrawData())
        if (viewportsActive) {
            // Create/move/render the external windows, then give Minecraft its context back.
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(windowHandle)
        }
    }

    fun shutdown() {
        if (!initialized) return
        if (windowHandle != 0L) GLFW.glfwSetCursorEnterCallback(windowHandle, null)?.free()
        GameViewport.restore()
        GameViewport.shutdown()
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
