//? if >=26.3 {
/*package dev.harrison.panellib.framework

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorType
import com.mojang.blaze3d.platform.cursor.CursorTypes
import imgui.ImGui
import imgui.callback.ImStrConsumer
import imgui.callback.ImStrSupplier
import imgui.flag.ImGuiBackendFlags
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseCursor
import net.minecraft.client.Minecraft
import org.lwjgl.sdl.SDLScancode.*
import org.lwjgl.sdl.SDLVideo
import org.lwjgl.system.MemoryStack

// ImGui platform glue for Minecraft's SDL3 window (26.3+, where Mojang replaced GLFW with SDL3).
// imgui-java ships no SDL backend, so this feeds ImGuiIO directly from the events Minecraft already
// dispatches to MouseHandler/KeyboardHandler (see MouseMixin/KeyboardMixin). Values arriving here are
// Minecraft's 26.3 codes: SDL scancodes + SDL keycodes, SDL mouse buttons (1 = left, 3 = right),
// SDL modifier bits, and actions PRESS = 1 / RELEASE = 0 / REPEAT = -1.
// Secondary OS windows (multi-viewport) are not supported on SDL: they need a full platform backend.
internal object ImGuiPlatform {
    const val supportsViewports = false

    private var lastFrameNanos = 0L
    private var lastValidX = -Float.MAX_VALUE
    private var lastValidY = -Float.MAX_VALUE
    private var lastCursor = -2

    private val mc get() = Minecraft.getInstance()

    fun init(window: Long) {
        val io = ImGui.getIO()
        io.setBackendPlatformName("panellib_mc_sdl3")
        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors)
        // Minecraft's KeyboardHandler wraps SDL's clipboard (and works for every target the same way).
        io.setGetClipboardTextFn(object : ImStrSupplier() {
            override fun get(): String = mc.keyboardHandler.clipboard
        })
        io.setSetClipboardTextFn(object : ImStrConsumer() {
            override fun accept(text: String) { mc.keyboardHandler.clipboard = text }
        })
        lastFrameNanos = 0L
        lastCursor = -2
    }

    fun shutdown() {
        resetCursor()
    }

    fun newFrame() {
        val window = dev.harrison.panellib.compat.Compat.windowHandle()
        val io = ImGui.getIO()
        val (w, h) = windowSize(window)
        val (fw, fh) = framebufferSize(window)
        io.setDisplaySize(w.toFloat(), h.toFloat())
        if (w > 0 && h > 0) io.setDisplayFramebufferScale(fw.toFloat() / w, fh.toFloat() / h)
        val now = System.nanoTime()
        io.setDeltaTime(if (lastFrameNanos > 0) ((now - lastFrameNanos) / 1e9f).coerceAtLeast(1e-5f) else 1f / 60f)
        lastFrameNanos = now
        updateCursor(io.configFlags)
    }

    private fun updateCursor(configFlags: Int) {
        if (configFlags and ImGuiConfigFlags.NoMouseCursorChange != 0 || mc.mouseHandler.isMouseGrabbed) return
        val wanted = ImGui.getMouseCursor()
        if (wanted == lastCursor) return
        lastCursor = wanted
        mc.window.selectCursor(cursorType(wanted))
    }

    private fun cursorType(imguiCursor: Int): CursorType = when (imguiCursor) {
        ImGuiMouseCursor.TextInput -> CursorTypes.IBEAM
        ImGuiMouseCursor.ResizeAll -> CursorTypes.RESIZE_ALL
        ImGuiMouseCursor.ResizeNS -> CursorTypes.RESIZE_NS
        ImGuiMouseCursor.ResizeEW -> CursorTypes.RESIZE_EW
        ImGuiMouseCursor.Hand -> CursorTypes.POINTING_HAND
        ImGuiMouseCursor.NotAllowed -> CursorTypes.NOT_ALLOWED
        // No diagonal resize cursors in Minecraft's set; ResizeAll is the closest match.
        ImGuiMouseCursor.ResizeNESW, ImGuiMouseCursor.ResizeNWSE -> CursorTypes.RESIZE_ALL
        else -> CursorTypes.ARROW
    }

    /** Hand the cursor back to Minecraft's default when the overlay stops driving it. */
    fun resetCursor() {
        if (lastCursor != -2 && lastCursor != ImGuiMouseCursor.Arrow) mc.window.selectCursor(CursorTypes.ARROW)
        lastCursor = -2
    }

    fun framebufferSize(window: Long): Pair<Int, Int> = MemoryStack.stackPush().use { stack ->
        val w = stack.mallocInt(1); val h = stack.mallocInt(1)
        SDLVideo.SDL_GetWindowSizeInPixels(window, w, h)
        w.get(0) to h.get(0)
    }

    fun windowSize(window: Long): Pair<Int, Int> = MemoryStack.stackPush().use { stack ->
        val w = stack.mallocInt(1); val h = stack.mallocInt(1)
        SDLVideo.SDL_GetWindowSize(window, w, h)
        w.get(0) to h.get(0)
    }

    fun isHovered(window: Long): Boolean = SDLVideo.SDL_GetWindowFlags(window) and SDLVideo.SDL_WINDOW_MOUSE_FOCUS != 0L

    fun cursorPos(window: Long): Pair<Double, Double> = mc.mouseHandler.xpos() to mc.mouseHandler.ypos()

    // Multi-viewport hooks: unreachable while supportsViewports is false.
    fun pollMonitors() {}
    fun showWindow(window: Long) {}
    fun hideWindow(window: Long) {}
    fun makeContextCurrent(window: Long) {}
    fun markMainViewportHovered() {}

    fun cursorEnter(window: Long, entered: Boolean) {
        val io = ImGui.getIO()
        if (entered) io.addMousePosEvent(lastValidX, lastValidY)
        else io.addMousePosEvent(-Float.MAX_VALUE, -Float.MAX_VALUE)
    }

    fun cursorPosEvent(window: Long, x: Double, y: Double) {
        lastValidX = x.toFloat(); lastValidY = y.toFloat()
        ImGui.getIO().addMousePosEvent(lastValidX, lastValidY)
    }

    fun mouseButton(window: Long, button: Int, action: Int, mods: Int) {
        val imguiButton = when (button) {
            1 -> 0 // left
            3 -> 1 // right
            2 -> 2 // middle
            4 -> 3 // X1
            5 -> 4 // X2
            else -> return
        }
        updateKeyModifiers(mods)
        ImGui.getIO().addMouseButtonEvent(imguiButton, action != InputConstants.RELEASE)
    }

    fun scroll(window: Long, xOffset: Double, yOffset: Double) {
        ImGui.getIO().addMouseWheelEvent(xOffset.toFloat(), yOffset.toFloat())
    }

    /** [scancode] is KeyEvent.key() on 26.3, [keycode] its layout-translated KeyEvent.keycode(). */
    fun key(window: Long, scancode: Int, keycode: Int, action: Int, mods: Int) {
        updateKeyModifiers(mods)
        val imguiKey = imguiKey(scancode, keycode)
        if (imguiKey != ImGuiKey.None) ImGui.getIO().addKeyEvent(imguiKey, action != InputConstants.RELEASE)
    }

    fun char(window: Long, codepoint: Int) = ImGui.getIO().addInputCharacter(codepoint)

    private fun updateKeyModifiers(mods: Int) {
        val io = ImGui.getIO()
        io.addKeyEvent(ImGuiKey.ModCtrl, mods and InputConstants.MOD_CONTROL != 0)
        io.addKeyEvent(ImGuiKey.ModShift, mods and InputConstants.MOD_SHIFT != 0)
        io.addKeyEvent(ImGuiKey.ModAlt, mods and InputConstants.MOD_ALT != 0)
        io.addKeyEvent(ImGuiKey.ModSuper, mods and InputConstants.MOD_SUPER != 0)
    }

    private fun imguiKey(scancode: Int, keycode: Int): Int {
        // Letters and digits follow the keyboard layout so shortcuts (Ctrl+A/C/V/X/Z) match the key caps.
        if (keycode in 'a'.code..'z'.code) return ImGuiKey.A + (keycode - 'a'.code)
        if (keycode in '0'.code..'9'.code) return ImGuiKey._0 + (keycode - '0'.code)
        if (scancode in SDL_SCANCODE_F1..SDL_SCANCODE_F12) return ImGuiKey.F1 + (scancode - SDL_SCANCODE_F1)
        if (scancode in SDL_SCANCODE_KP_1..SDL_SCANCODE_KP_9) return ImGuiKey.Keypad1 + (scancode - SDL_SCANCODE_KP_1)
        return when (scancode) {
            SDL_SCANCODE_TAB -> ImGuiKey.Tab
            SDL_SCANCODE_LEFT -> ImGuiKey.LeftArrow
            SDL_SCANCODE_RIGHT -> ImGuiKey.RightArrow
            SDL_SCANCODE_UP -> ImGuiKey.UpArrow
            SDL_SCANCODE_DOWN -> ImGuiKey.DownArrow
            SDL_SCANCODE_PAGEUP -> ImGuiKey.PageUp
            SDL_SCANCODE_PAGEDOWN -> ImGuiKey.PageDown
            SDL_SCANCODE_HOME -> ImGuiKey.Home
            SDL_SCANCODE_END -> ImGuiKey.End
            SDL_SCANCODE_INSERT -> ImGuiKey.Insert
            SDL_SCANCODE_DELETE -> ImGuiKey.Delete
            SDL_SCANCODE_BACKSPACE -> ImGuiKey.Backspace
            SDL_SCANCODE_SPACE -> ImGuiKey.Space
            SDL_SCANCODE_RETURN -> ImGuiKey.Enter
            SDL_SCANCODE_ESCAPE -> ImGuiKey.Escape
            SDL_SCANCODE_APOSTROPHE -> ImGuiKey.Apostrophe
            SDL_SCANCODE_COMMA -> ImGuiKey.Comma
            SDL_SCANCODE_MINUS -> ImGuiKey.Minus
            SDL_SCANCODE_PERIOD -> ImGuiKey.Period
            SDL_SCANCODE_SLASH -> ImGuiKey.Slash
            SDL_SCANCODE_SEMICOLON -> ImGuiKey.Semicolon
            SDL_SCANCODE_EQUALS -> ImGuiKey.Equal
            SDL_SCANCODE_LEFTBRACKET -> ImGuiKey.LeftBracket
            SDL_SCANCODE_BACKSLASH -> ImGuiKey.Backslash
            SDL_SCANCODE_RIGHTBRACKET -> ImGuiKey.RightBracket
            SDL_SCANCODE_GRAVE -> ImGuiKey.GraveAccent
            SDL_SCANCODE_CAPSLOCK -> ImGuiKey.CapsLock
            SDL_SCANCODE_SCROLLLOCK -> ImGuiKey.ScrollLock
            SDL_SCANCODE_NUMLOCKCLEAR -> ImGuiKey.NumLock
            SDL_SCANCODE_PRINTSCREEN -> ImGuiKey.PrintScreen
            SDL_SCANCODE_PAUSE -> ImGuiKey.Pause
            SDL_SCANCODE_KP_0 -> ImGuiKey.Keypad0
            SDL_SCANCODE_KP_PERIOD -> ImGuiKey.KeypadDecimal
            SDL_SCANCODE_KP_DIVIDE -> ImGuiKey.KeypadDivide
            SDL_SCANCODE_KP_MULTIPLY -> ImGuiKey.KeypadMultiply
            SDL_SCANCODE_KP_MINUS -> ImGuiKey.KeypadSubtract
            SDL_SCANCODE_KP_PLUS -> ImGuiKey.KeypadAdd
            SDL_SCANCODE_KP_ENTER -> ImGuiKey.KeypadEnter
            SDL_SCANCODE_KP_EQUALS -> ImGuiKey.KeypadEqual
            SDL_SCANCODE_LCTRL -> ImGuiKey.LeftCtrl
            SDL_SCANCODE_LSHIFT -> ImGuiKey.LeftShift
            SDL_SCANCODE_LALT -> ImGuiKey.LeftAlt
            SDL_SCANCODE_LGUI -> ImGuiKey.LeftSuper
            SDL_SCANCODE_RCTRL -> ImGuiKey.RightCtrl
            SDL_SCANCODE_RSHIFT -> ImGuiKey.RightShift
            SDL_SCANCODE_RALT -> ImGuiKey.RightAlt
            SDL_SCANCODE_RGUI -> ImGuiKey.RightSuper
            SDL_SCANCODE_APPLICATION -> ImGuiKey.Menu
            else -> ImGuiKey.None
        }
    }
}
*///?}
