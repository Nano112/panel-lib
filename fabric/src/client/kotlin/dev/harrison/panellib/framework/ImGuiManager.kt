package dev.harrison.panellib.framework

import dev.harrison.panellib.compat.Compat
import dev.harrison.panellib.theme.Fonts
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiConfigFlags
import imgui.internal.ImGuiContext

/** Owns the ImGui context, platform glue ([ImGuiPlatform]) and frame lifecycle. Render thread only. */
object ImGuiManager {
    @Volatile var initialized = false; private set
    private var windowHandle = 0L
    private var context: ImGuiContext? = null
    private var previousFrameContext: ImGuiContext? = null
    private var frameOpen = false
    private var lastMonitorPoll = 0L
    private var platformWindowsHidden = false
    private var mainCursorPresent = false
    /** Opt-in (config `external_windows`): panels dragged outside the game become their own OS windows. */
    @Volatile var externalWindows: Boolean = false
    var viewportsActive = false
        private set
    /** See PanelLibApi.markSyntheticInput. */
    @Volatile var lastSyntheticInputAt: Long = 0
    private fun syntheticRecently() = System.currentTimeMillis() - lastSyntheticInputAt < 2_000
    private const val INI_FILENAME = "panellib-imgui.ini"
    private const val RELEASE = com.mojang.blaze3d.platform.InputConstants.RELEASE

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
            if (externalWindows && !ImGuiPlatform.supportsViewports) {
                PanelLibLog.LOGGER.info("[panel-lib] external_windows is not supported on this Minecraft version; panels stay in the game window")
            } else if (externalWindows) {
                // Multi-viewport: ImGui's GLFW backend creates secondary windows (sharing MC's GL context);
                // we render each one with our renderer from Renderer_RenderWindow.
                io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable)
                io.configViewportsNoAutoMerge = false
                io.configViewportsNoTaskBarIcon = true
                viewportsActive = true
            }
            io.fonts.clear()
            Fonts.load(io, pixelRatio = pixelRatio(wh))
            ImGuiPlatform.init(wh)
            mainCursorPresent = true
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

    /** Framebuffer px per logical window unit (2 on Retina), from the OS window. */
    private fun pixelRatio(window: Long): Float {
        val (fw, _) = ImGuiPlatform.framebufferSize(window)
        val (ww, _) = ImGuiPlatform.windowSize(window)
        return if (ww > 0) fw.toFloat() / ww else 1f
    }

    fun startFrame(focused: Boolean) {
        previousFrameContext = ImGui.getCurrentContext()
        ImGui.setCurrentContext(context)
        val synthetic = focused && syntheticRecently()
        val mainHovered = ImGuiPlatform.isHovered(windowHandle)
        val present = mainHovered || synthetic
        // Enter replays the backend's last valid position. It is an edge event, not a per-frame poll.
        if (present != mainCursorPresent) {
            ImGuiPlatform.cursorEnter(windowHandle, present)
            mainCursorPresent = present
        }
        val now = System.nanoTime()
        if (now - lastMonitorPoll > 1_000_000_000L) {
            ImGuiPlatform.pollMonitors()
            lastMonitorPoll = now
        }
        if (platformWindowsHidden) {
            val pio = ImGui.getPlatformIO()
            for (i in 1 until pio.viewportsSize) {
                val handle = pio.getViewports(i).platformHandle
                if (handle != 0L) ImGuiPlatform.showWindow(handle)
            }
            platformWindowsHidden = false
        }
        ImGuiPlatform.newFrame()
        val io = ImGui.getIO()
        // Feed input BEFORE NewFrame: it consumes queued events and decides which window owns the mouse.
        // Writing MousePos afterwards updates widget rendering but leaves WantCaptureMouse at the old point.
        CursorFrame.begin(focused) {
            if (!viewportsActive || synthetic) {
                val mh = net.minecraft.client.Minecraft.getInstance().mouseHandler
                // Also updates the GLFW backend's last valid position and handles desktop coordinates.
                ImGuiPlatform.cursorPosEvent(windowHandle, mh.xpos(), mh.ypos())
                if (synthetic && viewportsActive) {
                    // OS hover may point at another app while MCP supplies input to Minecraft.
                    ImGuiPlatform.markMainViewportHovered()
                }
            } else if (mainHovered) {
                val (x, y) = ImGuiPlatform.cursorPos(windowHandle)
                ImGuiPlatform.cursorPosEvent(windowHandle, x, y)
            }
            // Detached windows keep the input supplied by their own platform callbacks.
        }
        frameOpen = true
    }

    /** True when the OS cursor is over the game window or any of our external viewport windows. */
    private fun anyOwnWindowHovered(): Boolean {
        if (ImGuiPlatform.isHovered(windowHandle)) return true
        val pio = ImGui.getPlatformIO()
        for (i in 1 until pio.viewportsSize) {
            val h = pio.getViewports(i).platformHandle
            if (h != 0L && ImGuiPlatform.isHovered(h)) return true
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
            ImGuiPlatform.makeContextCurrent(windowHandle)
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
                    if (handle != 0L) ImGuiPlatform.hideWindow(handle)
                }
                platformWindowsHidden = true
            }
        }
        ImGuiPlatform.resetCursor()
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
        ImGuiPlatform.shutdown()
        ImGui.destroyContext(context)
        if (previous.ptr != context?.ptr) ImGui.setCurrentContext(previous)
        context = null
        initialized = false
        windowHandle = 0L
        mainCursorPresent = false
    }

    // Input forwarders (called from the mixins).
    @JvmStatic fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) = withContext {
        if (action == RELEASE) capturedButtons.remove(button) else capturedButtons.add(button)
        ImGuiPlatform.mouseButton(window, button, action, mods)
    }
    @JvmStatic fun scrollCallback(window: Long, xOffset: Double, yOffset: Double) = withContext { ImGuiPlatform.scroll(window, xOffset, yOffset) }
    /** [scancode] is the event's second code: the GLFW scancode before 26.3, the SDL keycode from 26.3. */
    @JvmStatic fun keyCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) = withContext {
        if (action == RELEASE) capturedKeys.remove(key) else capturedKeys.add(key)
        ImGuiPlatform.key(window, key, scancode, action, mods)
    }
    @JvmStatic fun releaseKey(window: Long, key: Int, scancode: Int, mods: Int): Boolean {
        if (!initialized || key !in capturedKeys) return false
        keyCallback(window, key, scancode, RELEASE, mods)
        return true
    }
    @JvmStatic fun releaseButton(window: Long, button: Int, mods: Int): Boolean {
        if (!initialized || button !in capturedButtons) return false
        mouseButtonCallback(window, button, RELEASE, mods)
        return true
    }
    @JvmStatic fun charCallback(window: Long, codepoint: Int) = withContext { ImGuiPlatform.char(window, codepoint); typedChars.add(codepoint) }

    /** Drain typed characters (oldest first) for custom text widgets. */
    fun drainTypedChars(): List<Int> {
        if (typedChars.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        while (true) out.add(typedChars.poll() ?: break)
        return out
    }
}
