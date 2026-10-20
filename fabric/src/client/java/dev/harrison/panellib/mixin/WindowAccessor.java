package dev.harrison.panellib.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets GameViewport drive Minecraft's own resize path with a spoofed framebuffer size. */
@Mixin(Window.class)
public interface WindowAccessor {
    // 26.3 (SDL3) dropped the window-handle parameter.
    //? if >=26.3 {
    /*@Invoker("onFramebufferResize")
    void panellib$onFramebufferResize(int width, int height);
    *///?} else {
    @Invoker("onFramebufferResize")
    void panellib$onFramebufferResize(long window, int width, int height);
    //?}
}
