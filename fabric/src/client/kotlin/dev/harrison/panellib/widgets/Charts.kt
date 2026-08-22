package dev.harrison.panellib.widgets

import dev.harrison.panellib.theme.Theme
import imgui.ImGui
import imgui.ImVec4
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small immediate-mode chart kit drawn with the window draw list: categorical palette, pie/donut,
 * stacked time-series bars, simple bars. All colours derive from [Theme] or a stable per-key palette.
 */
object Charts {
    /** Stable, distinguishable colour for a category key (hue from hash, theme-friendly saturation). */
    fun colorFor(key: String): ImVec4 {
        val h = ((key.hashCode() and 0x7fffffff) % 360) / 360f
        return hsv(h, 0.55f, 0.92f)
    }

    fun hsv(h: Float, s: Float, v: Float): ImVec4 {
        val i = (h * 6).toInt(); val f = h * 6 - i
        val p = v * (1 - s); val q = v * (1 - f * s); val t = v * (1 - (1 - f) * s)
        val (r, g, b) = when (i % 6) { 0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t); 3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q) }
        return ImVec4(r, g, b, 1f)
    }

    private fun u32(c: ImVec4, a: Float = c.w) = ImGui.getColorU32(c.x, c.y, c.z, a)

    data class Slice(val label: String, val value: Double, val color: ImVec4)

    /**
     * Pie / donut with a legend to the right. Returns the hovered slice label, if any.
     * [hole] 0 = pie, 0.55 = donut.
     */
    fun pie(id: String, slices: List<Slice>, radius: Float = 64f, hole: Float = 0.55f, legend: Boolean = true): String? {
        val total = slices.sumOf { it.value }
        val t = Theme.current
        val x0 = ImGui.getCursorScreenPosX(); val y0 = ImGui.getCursorScreenPosY()
        val cx = x0 + radius + 4f; val cy = y0 + radius + 4f
        val dl = ImGui.getWindowDrawList()
        val mx = ImGui.getMousePosX(); val my = ImGui.getMousePosY()
        var hovered: String? = null
        if (total <= 0) {
            dl.addCircle(cx, cy, radius, u32(t.border), 48, 1.5f)
            dl.addText(cx - 14f, cy - 7f, u32(t.textFaint), "no data")
        } else {
            var a0 = -PI.toFloat() / 2
            // Adjacent AA-filled quads show hairline seams; fill without AA and draw one AA outline per slice.
            val savedFlags = dl.flags
            dl.flags = savedFlags and imgui.flag.ImDrawListFlags.AntiAliasedFill.inv()
            val mdx = mx - cx; val mdy = my - cy
            val mdist = kotlin.math.sqrt(mdx * mdx + mdy * mdy)
            val mAngle = (kotlin.math.atan2(mdy, mdx) + 2 * PI.toFloat() + PI.toFloat() / 2) % (2 * PI.toFloat())
            var acc = 0f
            for (s in slices) {
                val sweep = (s.value / total * 2 * PI).toFloat()
                if (sweep <= 0f) continue
                val a1 = a0 + sweep
                val isHover = mdist <= radius && mdist >= radius * hole && mAngle >= acc && mAngle < acc + sweep
                if (isHover) hovered = s.label
                val r = if (isHover) radius + 3f else radius
                val ri = radius * hole
                val col = u32(s.color, if (isHover) 1f else 0.9f)
                // An annular sector is not convex: fill it as a fan of small quads (≤ 6° each), each of which is.
                val steps = maxOf(2, (sweep / (PI.toFloat() / 30f)).toInt() + 1)
                val segs = steps * 2
                for (k in 0 until steps) {
                    val b0 = a0 + sweep * k / steps; val b1 = a0 + sweep * (k + 1) / steps
                    dl.pathClear()
                    if (hole > 0f) {
                        dl.pathLineTo(cx + cos(b0) * ri, cy + sin(b0) * ri)
                        dl.pathLineTo(cx + cos(b0) * r, cy + sin(b0) * r)
                        dl.pathLineTo(cx + cos(b1) * r, cy + sin(b1) * r)
                        dl.pathLineTo(cx + cos(b1) * ri, cy + sin(b1) * ri)
                    } else {
                        dl.pathLineTo(cx, cy)
                        dl.pathLineTo(cx + cos(b0) * r, cy + sin(b0) * r)
                        dl.pathLineTo(cx + cos(b1) * r, cy + sin(b1) * r)
                    }
                    dl.pathFillConvex(col)
                }
                // AA outline of the whole slice (outer arc, edge, inner arc, edge).
                dl.flags = savedFlags
                dl.pathClear()
                dl.pathArcTo(cx, cy, r, a0, a1, segs)
                if (hole > 0f) dl.pathArcTo(cx, cy, ri, a1, a0, segs) else dl.pathLineTo(cx, cy)
                dl.pathStroke(col, imgui.flag.ImDrawFlags.Closed, 1.2f)
                dl.flags = savedFlags and imgui.flag.ImDrawListFlags.AntiAliasedFill.inv()
                a0 = a1; acc += sweep
            }
            dl.flags = savedFlags
            // Slice borders in bg colour so adjacent colours separate.
            var b0 = -PI.toFloat() / 2
            for (s in slices) {
                val sweep = (s.value / total * 2 * PI).toFloat(); if (sweep <= 0f) continue
                dl.addLine(cx + cos(b0) * radius * hole, cy + sin(b0) * radius * hole, cx + cos(b0) * radius, cy + sin(b0) * radius, u32(t.bg), 1.5f)
                b0 += sweep
            }
        }
        ImGui.dummy(radius * 2 + 8f, radius * 2 + 8f)
        if (legend) {
            ImGui.sameLine(0f, 12f)
            ImGui.beginGroup()
            for (s in slices.take(12)) {
                val lx = ImGui.getCursorScreenPosX(); val ly = ImGui.getCursorScreenPosY() + 3f
                dl.addRectFilled(lx, ly, lx + 10f, ly + 10f, u32(s.color), 2f)
                ImGui.dummy(12f, ImGui.getTextLineHeight()); ImGui.sameLine(0f, 4f)
                val pct = if (total > 0) s.value / total * 100 else 0.0
                // ImGui.text is printf-style: escape the literal percent sign.
                val txt = "${s.label}  ${"%.1f".format(pct)}%%"
                if (s.label == hovered) Widgets.semibold(txt) else ImGui.text(txt)
            }
            ImGui.endGroup()
        }
        return hovered
    }

    /**
     * Stacked bars over time: [series] label → values (same length, oldest first). Legend below.
     * Returns the hovered column index or -1.
     */
    fun stackedBars(id: String, series: List<Pair<String, FloatArray>>, colors: Map<String, ImVec4>, width: Float, height: Float, overlay: String? = null): Int {
        val t = Theme.current
        val n = series.firstOrNull()?.second?.size ?: 0
        val x0 = ImGui.getCursorScreenPosX(); val y0 = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(x0, y0, x0 + width, y0 + height, u32(t.surfaceAlt), 3f)
        var hovered = -1
        if (n > 0) {
            val totals = FloatArray(n) { i -> series.sumOf { it.second[i].toDouble() }.toFloat() }
            val max = maxOf(totals.maxOrNull() ?: 1f, 1f)
            val bw = width / n
            val mx = ImGui.getMousePosX(); val my = ImGui.getMousePosY()
            for (i in 0 until n) {
                val bx0 = x0 + i * bw; val bx1 = bx0 + maxOf(bw - 1f, 1f)
                if (mx >= bx0 && mx < bx1 && my >= y0 && my <= y0 + height) hovered = i
                var yTop = y0 + height
                for ((label, values) in series) {
                    val v = values[i]; if (v <= 0f) continue
                    val h = v / max * (height - 2f)
                    dl.addRectFilled(bx0, yTop - h, bx1, yTop, u32(colors[label] ?: t.accent, if (hovered == i) 1f else 0.85f))
                    yTop -= h
                }
            }
            if (hovered >= 0) {
                val parts = series.filter { it.second[hovered] > 0f }.sortedByDescending { it.second[hovered] }.take(6)
                    .joinToString("\n") { "${it.first}: ${it.second[hovered].toInt()}" }
                ImGui.setTooltip("t-${n - 1 - hovered}s · ${totals[hovered].toInt()} total\n$parts")
            }
        }
        overlay?.let { dl.addText(x0 + 6f, y0 + 4f, u32(t.textSecondary), it) }
        ImGui.dummy(width, height)
        // legend
        var first = true
        for ((label, _) in series) {
            if (!first) ImGui.sameLine(0f, 10f); first = false
            val lx = ImGui.getCursorScreenPosX(); val ly = ImGui.getCursorScreenPosY() + 3f
            dl.addRectFilled(lx, ly, lx + 10f, ly + 10f, u32(colors[label] ?: t.accent), 2f)
            ImGui.dummy(12f, ImGui.getTextLineHeight()); ImGui.sameLine(0f, 4f); Widgets.muted(label)
        }
        return hovered
    }

    /** Horizontal bars with labels; values are drawn proportionally to the max. */
    fun hbars(items: List<Triple<String, Double, ImVec4>>, width: Float, valueText: (Double) -> String = { "%.0f".format(it) }) {
        val max = items.maxOfOrNull { it.second } ?: 1.0
        val dl = ImGui.getWindowDrawList()
        for ((label, v, c) in items) {
            val x = ImGui.getCursorScreenPosX(); val y = ImGui.getCursorScreenPosY()
            val h = ImGui.getTextLineHeight()
            val bw = (width * 0.55 * (if (max > 0) v / max else 0.0)).toFloat()
            dl.addRectFilled(x, y + 2f, x + bw, y + h - 2f, u32(c, 0.45f), 3f)
            ImGui.text("  $label")
            ImGui.sameLine(width * 0.58f); Widgets.muted(valueText(v))
        }
    }

    /** Vertical bars with labels under them (e.g. a histogram). */
    fun vbars(id: String, labels: List<String>, values: List<Double>, width: Float, height: Float, color: ImVec4 = Theme.current.accent) {
        val t = Theme.current
        val n = values.size; if (n == 0) return
        val x0 = ImGui.getCursorScreenPosX(); val y0 = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        val max = maxOf(values.maxOrNull() ?: 1.0, 1.0)
        val bw = width / n
        val mx = ImGui.getMousePosX(); val my = ImGui.getMousePosY()
        for (i in 0 until n) {
            val bx0 = x0 + i * bw + 2f; val bx1 = x0 + (i + 1) * bw - 2f
            val h = (values[i] / max * (height - 16f)).toFloat()
            val hov = mx >= bx0 && mx < bx1 && my >= y0 && my <= y0 + height
            dl.addRectFilled(bx0, y0 + height - 16f - h, bx1, y0 + height - 16f, u32(color, if (hov) 1f else 0.7f), 2f)
            val lw = ImGui.calcTextSize(labels[i]).x
            dl.addText(x0 + i * bw + (bw - lw) / 2f, y0 + height - 14f, u32(t.textFaint), labels[i])
            if (hov) ImGui.setTooltip("${labels[i]}: ${"%.0f".format(values[i])}")
        }
        ImGui.dummy(width, height)
    }
}
