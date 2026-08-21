package dev.harrison.panellib.framework

import dev.harrison.panellib.mixin.WindowAccessor
import dev.harrison.panellib.theme.Theme
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import kotlin.math.roundToInt

/**
 * "Embedded game" mode: while the overlay is focused, Minecraft renders at the size of the dockspace's
 * central (pass-through) node instead of the whole window, and the finished frame is moved into that
 * rectangle before the swap. Panels therefore sit BESIDE the game, not on top of it, and the game keeps
 * correct aspect ratio, GUI scale and HUD layout. Implemented by feeding Minecraft a spoofed framebuffer
 * size through its own resize path (`Window.onFramebufferResize`), so the render targets, projection and
 * gui scale all follow — nothing about MC's rendering is patched.
 *
 * Render-thread only. Call order per frame (see [Overlay.render]): [central] is recorded while the dock
 * host renders, [composite] runs right before the ImGui draw, [applySizing] after it (it affects the
 * NEXT frame). When the overlay loses focus, [restore] puts the real size back.
 */
object GameViewport {
    /** Master switch (config `embed_game`, Layout ▾ toggle). */
    @Volatile var enabled: Boolean = true

    /** Central node rectangle in framebuffer pixels, top-left origin. Set by DockHost each frame. */
    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    @Volatile var central: Rect? = null
    private var spoofW = 0
    private var spoofH = 0
    private var fbo = 0
    private var tex = 0
    private var texW = 0
    private var texH = 0
    val isActive: Boolean get() = spoofW > 0

    private val mc get() = Minecraft.getInstance()

    private fun realFramebufferSize(): Pair<Int, Int> {
        val w = IntArray(1); val h = IntArray(1)
        GLFW.glfwGetFramebufferSize(dev.harrison.panellib.compat.Compat.windowHandle(), w, h)
        return w[0] to h[0]
    }

    /** Logical (ImGui) units → framebuffer pixels. */
    fun pixelRatio(): Float {
        val (fw, _) = realFramebufferSize()
        val sw = mc.window.screenWidth
        return if (sw > 0) fw.toFloat() / sw else 1f
    }

    /** Decide the size Minecraft should render at for the next frame and apply it if it changed. */
    fun applySizing() {
        val rect = central
        if (!enabled || rect == null || rect.w < 64 || rect.h < 64) { restore(); return }
        if (rect.w != mc.window.width || rect.h != mc.window.height) {
            resizeMinecraft(rect.w, rect.h)
        }
        spoofW = rect.w; spoofH = rect.h
    }

    /** Put the real window size back (overlay closed / embed disabled). */
    fun restore() {
        if (spoofW == 0) return
        val (rw, rh) = realFramebufferSize()
        spoofW = 0; spoofH = 0
        if (rw != mc.window.width || rh != mc.window.height) resizeMinecraft(rw, rh)
    }

    private fun resizeMinecraft(w: Int, h: Int) {
        (mc.window as WindowAccessor).`panellib$onFramebufferResize`(dev.harrison.panellib.compat.Compat.windowHandle(), w, h)
    }

    /**
     * FBO 0 holds the game frame at the bottom-left [spoofW]×[spoofH]. Copy it out, paint the background,
     * and put it back at the central rect. Runs before ImGui draws so panels end up on top of the bars.
     */
    fun composite() {
        val rect = central ?: return
        if (!isActive || spoofW != mc.window.width || spoofH != mc.window.height) return
        val (fw, fh) = realFramebufferSize()
        if (fw <= 0 || fh <= 0) return
        ensureTexture(spoofW, spoofH)

        val prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val clear = FloatArray(4).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, it) }
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            // 1. game (bottom-left of FBO 0) → our texture
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo)
            GL30.glBlitFramebuffer(0, 0, spoofW, spoofH, 0, 0, spoofW, spoofH, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST)
            // 2. clear the whole window to the theme background
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0)
            val bg = Theme.current.bg
            GL11.glClearColor(bg.x, bg.y, bg.z, 1f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            // 3. texture → central rect (GL y is bottom-up)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo)
            val dx0 = rect.x; val dy0 = fh - (rect.y + rect.h); val dx1 = rect.x + rect.w; val dy1 = fh - rect.y
            GL30.glBlitFramebuffer(0, 0, spoofW, spoofH, dx0, dy0, dx1, dy1, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST)
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw)
            GL11.glClearColor(clear[0], clear[1], clear[2], clear[3])
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
    }

    private fun ensureTexture(w: Int, h: Int) {
        if (fbo != 0 && texW == w && texH == h) return
        shutdown()
        tex = GL11.glGenTextures()
        val prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null as java.nio.ByteBuffer?)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex)
        fbo = GL30.glGenFramebuffers()
        val prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo)
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw)
        texW = w; texH = h
    }

    fun shutdown() {
        if (fbo != 0) GL30.glDeleteFramebuffers(fbo)
        if (tex != 0) GL11.glDeleteTextures(tex)
        fbo = 0; tex = 0; texW = 0; texH = 0
    }

    /** Helper for DockHost: convert the central node's logical rect to framebuffer pixels. */
    fun setCentralFromLogical(x: Float, y: Float, w: Float, h: Float) {
        val r = pixelRatio()
        central = Rect((x * r).roundToInt(), (y * r).roundToInt(), (w * r).roundToInt(), (h * r).roundToInt())
    }
}
