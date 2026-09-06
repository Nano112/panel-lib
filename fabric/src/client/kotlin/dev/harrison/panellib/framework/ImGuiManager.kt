package dev.harrison.panellib.framework

import dev.harrison.panellib.compat.Compat
import dev.harrison.panellib.theme.Fonts
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiConfigFlags
import imgui.glfw.ImGuiImplGlfw
import imgui.internal.ImGuiContext
import org.lwjgl.glfw.GLFW

/** Owns the ImGui context, GLFW backend glue and frame lifecycle. Render thread only. */
object ImGuiManager {
    private val imGuiGlfw = ImGuiImplGlfw()
    @Volatile var initialized = false; private set
    private var windowHandle = 0L
    private var context: ImGuiContext? = null
    private var previousFrameContext: ImGuiContext? = null
    private var frameOpen = false
    private var lastMonitorPoll = 0L
    private var platformWindowsHidden = false
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
    private val capturedKeys = mutableSetOf<Int>()
    private val capturedButtons = mutableSetOf<Int>()

    fun initIfNeeded() {
        if (initialized) return
        val previous = ImGui.getCurrentContext()
        try {
            val wh = Compat.windowHandle()
            windowHandle = wh
            context = ImGui.createContext()
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
            // imgui-java 1.89 installs a global monitor callback even with installCallbacks=false.
            // Restore its predecessor before returning to GLFW's event loop; poll our monitor list instead.
            val previousMonitor = GLFW.glfwSetMonitorCallback(null)
            try { imGuiGlfw.init(wh, false) } finally {
                val installed = GLFW.glfwSetMonitorCallback(previousMonitor)
                if (installed != null && installed.address() != previousMonitor?.address()) installed.free()
            }
            // ImGuiImplGlfw only polls the cursor when mouseWindow != -1, which only a cursor-enter
            // callback sets; with installCallbacks=false we must prime it and keep it updated.
            imGuiGlfw.cursorEnterCallback(wh, true)
            // Cursor presence is polled when our frame starts. Do not replace another mod's callback.
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
        } finally {
            ImGui.setCurrentContext(previous)
        }
    }

    /** Framebuffer px per logical window unit (2 on Retina), from GLFW. */
    private fun pixelRatio(window: Long): Float {
        val fw = IntArray(1); val fh = IntArray(1); val ww = IntArray(1); val whh = IntArray(1)
        GLFW.glfwGetFramebufferSize(window, fw, fh); GLFW.glfwGetWindowSize(window, ww, whh)
        return if (ww[0] > 0) fw[0].toFloat() / ww[0] else 1f
    }

    fun startFrame(focused: Boolean) {
        previousFrameContext = ImGui.getCurrentContext()
        ImGui.setCurrentContext(context)
        imGuiGlfw.cursorEnterCallback(windowHandle,
            GLFW.glfwGetWindowAttrib(windowHandle, GLFW.GLFW_HOVERED) == GLFW.GLFW_TRUE || syntheticRecently())
        val now = System.nanoTime()
        if (now - lastMonitorPoll > 1_000_000_000L) {
            imGuiGlfw.monitorCallback(0L, 0)
            lastMonitorPoll = now
        }
        if (platformWindowsHidden) {
            val pio = ImGui.getPlatformIO()
            for (i in 1 until pio.viewportsSize) {
                val handle = pio.getViewports(i).platformHandle
                if (handle != 0L) GLFW.glfwShowWindow(handle)
            }
            platformWindowsHidden = false
        }
        imGuiGlfw.newFrame()
        ImGui.newFrame()
        frameOpen = true
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
        frameOpen = false
        ImGuiGl3Renderer.render(ImGui.getDrawData())
        if (viewportsActive) {
            // Create/move/render the external windows, then give Minecraft its context back.
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(windowHandle)
        }
    }

    /** Also restores the previous context when a panel or renderer throws. */
    fun restoreFrameContext() {
        try { if (frameOpen) ImGui.endFrame() } finally {
            frameOpen = false
            previousFrameContext?.let { ImGui.setCurrentContext(it) }
            previousFrameContext = null
        }
    }

    private fun <T> withContext(block: () -> T): T {
        val previous = ImGui.getCurrentContext()
        ImGui.setCurrentContext(context)
        return try { block() } finally { ImGui.setCurrentContext(previous) }
    }

    @JvmStatic fun wantsKeyboard(): Boolean = initialized && withContext { ImGui.getIO().wantCaptureKeyboard }
    @JvmStatic fun wantsMouse(): Boolean = initialized && withContext { ImGui.getIO().wantCaptureMouse }

    fun suspendInput() {
        if (!initialized) return
        withContext {
            val io = ImGui.getIO()
            io.clearEventsQueue()
            for (key in io.keysDown.indices) io.setKeysDown(key, false)
            // Mouse aliases are maintained by ImGui; AddKeyEvent rejects them.
            for (key in imgui.flag.ImGuiKey.NamedKey_BEGIN until imgui.flag.ImGuiKey.MouseLeft)
                io.addKeyEvent(key, false)
            for (modifier in intArrayOf(imgui.flag.ImGuiKey.ModCtrl, imgui.flag.ImGuiKey.ModShift,
                imgui.flag.ImGuiKey.ModAlt, imgui.flag.ImGuiKey.ModSuper)) io.addKeyEvent(modifier, false)
            io.keyCtrl = false; io.keyShift = false; io.keyAlt = false; io.keySuper = false
            for (button in 0 until 5) ImGui.getIO().setMouseDown(button, false)
            if (viewportsActive) {
                // DestroyPlatformWindows also clears the main viewport's backend data.
                // Keep that data for resume; hide only our secondary OS windows.
                val pio = ImGui.getPlatformIO()
                for (i in 1 until pio.viewportsSize) {
                    val handle = pio.getViewports(i).platformHandle
                    if (handle != 0L) GLFW.glfwHideWindow(handle)
                }
                platformWindowsHidden = true
            }
        }
        typedChars.clear()
        capturedKeys.clear()
        capturedButtons.clear()
    }

    fun shutdown() {
        if (!initialized) return
        val previous = ImGui.getCurrentContext()
        ImGui.setCurrentContext(context)
        GameViewport.restore()
        GameViewport.shutdown()
        ImGuiGl3Renderer.shutdown()
        imGuiGlfw.shutdown()
        ImGui.destroyContext(context)
        if (previous.ptr != context?.ptr) ImGui.setCurrentContext(previous)
        context = null
        initialized = false
        windowHandle = 0L
    }

    // Input forwarders (called from the mixins).
    @JvmStatic fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) = withContext {
        if (action == GLFW.GLFW_RELEASE) capturedButtons.remove(button) else capturedButtons.add(button)
        imGuiGlfw.mouseButtonCallback(window, button, action, mods)
    }
    @JvmStatic fun scrollCallback(window: Long, xOffset: Double, yOffset: Double) = withContext { imGuiGlfw.scrollCallback(window, xOffset, yOffset) }
    @JvmStatic fun keyCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) = withContext {
        if (action == GLFW.GLFW_RELEASE) capturedKeys.remove(key) else capturedKeys.add(key)
        imGuiGlfw.keyCallback(window, key, scancode, action, mods)
    }
    @JvmStatic fun releaseKey(window: Long, key: Int, scancode: Int, mods: Int): Boolean {
        if (!initialized || key !in capturedKeys) return false
        keyCallback(window, key, scancode, GLFW.GLFW_RELEASE, mods)
        return true
    }
    @JvmStatic fun releaseButton(window: Long, button: Int, mods: Int): Boolean {
        if (!initialized || button !in capturedButtons) return false
        mouseButtonCallback(window, button, GLFW.GLFW_RELEASE, mods)
        return true
    }
    @JvmStatic fun charCallback(window: Long, codepoint: Int) = withContext { imGuiGlfw.charCallback(window, codepoint); typedChars.add(codepoint) }

    /** Drain typed characters (oldest first) for custom text widgets. */
    fun drainTypedChars(): List<Int> {
        if (typedChars.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        while (true) out.add(typedChars.poll() ?: break)
        return out
    }
}
