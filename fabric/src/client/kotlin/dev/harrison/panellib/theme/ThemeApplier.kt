package dev.harrison.panellib.theme

import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar

/**
 * Pushes a [Theme] onto ImGui's style stacks for one frame. [apply]/[unapply] always balance.
 * EVERY ImGuiCol slot is mapped (test-enforced) so no stock Dear-ImGui blue ever shows.
 */
object ThemeApplier {
    fun colorMap(t: Theme): Map<Int, ImVec4> = linkedMapOf(
        ImGuiCol.Text to t.text,
        ImGuiCol.TextDisabled to t.textMuted,
        ImGuiCol.WindowBg to t.bg,
        ImGuiCol.ChildBg to t.surface,
        ImGuiCol.PopupBg to t.surfaceAlt,
        ImGuiCol.Border to t.borderSubtle,
        ImGuiCol.BorderShadow to Theme.TRANSPARENT,
        ImGuiCol.FrameBg to t.surfaceAlt,
        ImGuiCol.FrameBgHovered to t.surfaceHover,
        ImGuiCol.FrameBgActive to t.surfaceHover,
        ImGuiCol.TitleBg to t.surface,
        ImGuiCol.TitleBgActive to t.surfaceRaised,
        ImGuiCol.TitleBgCollapsed to t.surface,
        ImGuiCol.MenuBarBg to t.surface,
        ImGuiCol.ScrollbarBg to Theme.TRANSPARENT,
        ImGuiCol.ScrollbarGrab to t.border,
        ImGuiCol.ScrollbarGrabHovered to t.textFaint,
        ImGuiCol.ScrollbarGrabActive to t.textMuted,
        ImGuiCol.CheckMark to t.accent,
        ImGuiCol.SliderGrab to t.accent,
        ImGuiCol.SliderGrabActive to t.accentHover,
        ImGuiCol.Button to t.surfaceAlt,
        ImGuiCol.ButtonHovered to t.surfaceHover,
        ImGuiCol.ButtonActive to t.accentDim,
        ImGuiCol.Header to t.accentDim,
        ImGuiCol.HeaderHovered to t.surfaceHover,
        ImGuiCol.HeaderActive to t.accentDim,
        ImGuiCol.Separator to t.borderSubtle,
        ImGuiCol.SeparatorHovered to t.accentDim,
        ImGuiCol.SeparatorActive to t.accent,
        ImGuiCol.ResizeGrip to Theme.TRANSPARENT,
        ImGuiCol.ResizeGripHovered to t.accentDim,
        ImGuiCol.ResizeGripActive to t.accent,
        ImGuiCol.Tab to t.surface,
        ImGuiCol.TabHovered to t.surfaceHover,
        ImGuiCol.TabActive to t.surfaceRaised,
        ImGuiCol.TabUnfocused to t.surface,
        ImGuiCol.TabUnfocusedActive to t.surfaceAlt,
        ImGuiCol.DockingPreview to t.accentMuted,
        ImGuiCol.DockingEmptyBg to Theme.TRANSPARENT,
        ImGuiCol.PlotLines to t.accent,
        ImGuiCol.PlotLinesHovered to t.accentHover,
        ImGuiCol.PlotHistogram to t.accent,
        ImGuiCol.PlotHistogramHovered to t.accentHover,
        ImGuiCol.TableHeaderBg to t.surfaceAlt,
        ImGuiCol.TableBorderStrong to t.border,
        ImGuiCol.TableBorderLight to t.borderSubtle,
        ImGuiCol.TableRowBg to Theme.TRANSPARENT,
        ImGuiCol.TableRowBgAlt to t.stripe,
        ImGuiCol.TextSelectedBg to t.accentMuted,
        ImGuiCol.DragDropTarget to t.accent,
        ImGuiCol.NavHighlight to t.accent,
        ImGuiCol.NavWindowingHighlight to t.accent,
        ImGuiCol.NavWindowingDimBg to t.scrim,
        ImGuiCol.ModalWindowDimBg to t.scrim,
    )

    private const val VAR2_COUNT = 5
    private const val VAR1_COUNT = 13

    fun apply(t: Theme = Theme.current) {
        for ((slot, c) in colorMap(t)) ImGui.pushStyleColor(slot, c.x, c.y, c.z, c.w)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, t.windowPadding, t.windowPadding)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, t.framePaddingX, t.framePaddingY)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, t.itemSpacingX, t.itemSpacingY)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemInnerSpacing, 4f, 4f)
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 6f, 3f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, t.windowRounding)
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, t.frameRounding + 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, t.frameRounding)
        ImGui.pushStyleVar(ImGuiStyleVar.PopupRounding, t.popupRounding)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarRounding, 12f)
        ImGui.pushStyleVar(ImGuiStyleVar.GrabRounding, t.frameRounding)
        ImGui.pushStyleVar(ImGuiStyleVar.TabRounding, t.frameRounding)
        ImGui.pushStyleVar(ImGuiStyleVar.ScrollbarSize, t.scrollbarSize)
        ImGui.pushStyleVar(ImGuiStyleVar.GrabMinSize, 10f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 1f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f)
    }

    fun unapply(t: Theme = Theme.current) {
        ImGui.popStyleVar(VAR2_COUNT + VAR1_COUNT)
        ImGui.popStyleColor(colorMap(t).size)
    }
}
