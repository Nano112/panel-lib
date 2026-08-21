package dev.harrison.panellib.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/** Version shims shared by several files. */
object Compat {
    private val mc get() = Minecraft.getInstance()

    @JvmStatic
    fun screen(): Screen? {
        //? if >=26.2 {
        /*return mc.gui.screen()
        *///?} else {
        return mc.screen
        //?}
    }

    fun windowHandle(): Long {
        //? if >=1.21.9 {
        return mc.window.handle()
        //?} else {
        /*return mc.window.getWindow()
        *///?}
    }
}
