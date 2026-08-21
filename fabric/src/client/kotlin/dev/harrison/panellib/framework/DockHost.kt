package dev.harrison.panellib.framework

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiDir
import imgui.flag.ImGuiDockNodeFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt

/**
 * Full-viewport invisible host window: top menu bar (the shared toolbar) + a transparent dockspace
 * whose central node passes through to the game (visible + clickable). Panels dock to the edges.
 */
object DockHost {
    const val DOCKSPACE_ID = "PanelLibDockSpace"

    private val HOST_FLAGS = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.NoResize or
        ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus or
        ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoScrollbar or
        ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.MenuBar

    private var firstFrame = true
    private var pendingLayoutRebuild = false

    fun resetLayout() { pendingLayoutRebuild = true }

    fun render() {
        val vp = ImGui.getMainViewport()
        if (vp != null) {
            ImGui.setNextWindowPos(vp.workPosX, vp.workPosY, ImGuiCond.Always)
            ImGui.setNextWindowSize(vp.workSizeX, vp.workSizeY, ImGuiCond.Always)
            ImGui.setNextWindowViewport(vp.id)
        } else {
            val io = ImGui.getIO()
            ImGui.setNextWindowPos(0f, 0f, ImGuiCond.Always)
            ImGui.setNextWindowSize(io.displaySizeX, io.displaySizeY, ImGuiCond.Always)
        }
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.begin("##PanelLibDockHost", HOST_FLAGS)
        ImGui.popStyleVar()

        if (ImGui.beginMenuBar()) { Toolbar.renderMenuBar(); ImGui.endMenuBar() }

        val dockId = ImGui.getID(DOCKSPACE_ID)
        if (firstFrame) {
            // Fresh ini → no node yet → build the default layout once.
            if (imgui.internal.ImGui.dockBuilderGetNode(dockId).ptr == 0L) pendingLayoutRebuild = true
            firstFrame = false
        }
        ImGui.dockSpace(dockId, 0f, 0f, ImGuiDockNodeFlags.PassthruCentralNode or ImGuiDockNodeFlags.NoDockingInCentralNode)
        maybeBuildDefaultLayout(dockId)

        ImGui.end()
        ImGui.popStyleVar(2)
    }

    /** Default layout: every registered panel docked into one right split (~38%); centre stays pass-through. */
    private fun maybeBuildDefaultLayout(dockId: Int) {
        if (!pendingLayoutRebuild) return
        pendingLayoutRebuild = false
        val io = ImGui.getIO()
        imgui.internal.ImGui.dockBuilderRemoveNode(dockId)
        imgui.internal.ImGui.dockBuilderAddNode(dockId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace)
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockId, io.displaySizeX, io.displaySizeY)
        val rightId = ImInt(); val centralId = ImInt()
        imgui.internal.ImGui.dockBuilderSplitNode(dockId, ImGuiDir.Right, 0.38f, rightId, centralId)
        for (panel in Toolbar.registry().panels()) imgui.internal.ImGui.dockBuilderDockWindow(panel.windowLabel, rightId.get())
        imgui.internal.ImGui.dockBuilderFinish(dockId)
    }
}
