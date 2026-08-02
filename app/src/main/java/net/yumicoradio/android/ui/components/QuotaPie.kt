// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Win98 "disk properties" pie, drawn the way the website draws it (`drawQuotaPie` in
 * yumiChat-v2.js): an elliptical top face squashed for perspective, extruded downward into a 3D
 * slice, blue for the used share and magenta for what is free. A faithful port so the app's quota
 * window reads as the same instrument, not a bar dressed up.
 *
 * Pure geometry from two numbers — no state, no theme — so it renders the same on every screen and
 * can be eyeballed in a preview.
 */
@Composable
fun QuotaPie(usedBytes: Long, totalBytes: Long, modifier: Modifier = Modifier) {
    Canvas(modifier.size(160.dp, 110.dp)) {
        val w = size.width
        val h = size.height

        val usedFrac = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0) else 0.0

        // Elliptical top face — squashed vertically for the 3D tilt, as the site's constants.
        val cx = w / 2f
        val cy = h * 0.43f
        val rx = w * 0.44f
        val ry = rx * 0.38f
        val depth = h * 0.10f

        val start = -PI / 2          // 12 o'clock
        val pi2 = PI * 2
        val usedEnd = start + pi2 * usedFrac
        val freeEnd = start + pi2

        // Exact Win98 disk colours: blue = used, magenta = free, each with a darker wall.
        val blueTop = Color(0xFF0000CC)
        val blueSide = Color(0xFF000088)
        val pinkTop = Color(0xFFFF00FF)
        val pinkSide = Color(0xFF990099)
        val black = Color(0xFF000000)

        val topLeft = Offset(cx - rx, cy - ry)
        val ovalSize = Size(rx * 2, ry * 2)

        // The 3D wall: extrude only the arc segments facing the viewer (lower half, sin >= 0)
        // downward by `depth`. Split into contiguous runs so a slice that wraps past the sides
        // does not draw a wall across the hidden back.
        fun sideArc(a1: Double, a2: Double, color: Color) {
            val steps = 64
            val arc = a2 - a1
            var seg = mutableListOf<Offset>()
            fun flush() {
                if (seg.size >= 2) {
                    val p = Path().apply {
                        moveTo(seg[0].x, seg[0].y)
                        seg.forEach { lineTo(it.x, it.y) }
                        for (i in seg.indices.reversed()) lineTo(seg[i].x, seg[i].y + depth)
                        close()
                    }
                    drawPath(p, color)
                    drawPath(p, black, style = Stroke(width = 0.5f))
                }
                seg = mutableListOf()
            }
            for (i in 0..steps) {
                val a = a1 + arc * i / steps
                if (sin(a) >= 0) {
                    seg.add(Offset((cx + rx * cos(a)).toFloat(), (cy + ry * sin(a)).toFloat()))
                } else {
                    flush()
                }
            }
            flush()
        }

        // A wedge of the elliptical top face, outlined in black like the site strokes each slice.
        fun topWedge(a1: Double, a2: Double, color: Color) {
            val startDeg = Math.toDegrees(a1).toFloat()
            val sweepDeg = Math.toDegrees(a2 - a1).toFloat()
            drawArc(color, startDeg, sweepDeg, useCenter = true, topLeft = topLeft, size = ovalSize)
            drawArc(black, startDeg, sweepDeg, useCenter = true, topLeft = topLeft, size = ovalSize, style = Stroke(width = 1f))
        }

        fun fullDisk(top: Color, side: Color) {
            sideArc(start, freeEnd, side)
            drawOval(top, topLeft, ovalSize)
            drawOval(black, topLeft, ovalSize, style = Stroke(width = 1f))
        }

        when {
            usedFrac <= 0.005 -> fullDisk(pinkTop, pinkSide)
            usedFrac >= 0.995 -> fullDisk(blueTop, blueSide)
            else -> {
                // Draw the wall of the slice that does NOT contain the nearest point (6 o'clock)
                // first, so the front slice's wall paints over it. Blue holds 6 o'clock once it
                // covers half the disk or more.
                val blueNearFront = usedFrac >= 0.5
                if (blueNearFront) {
                    sideArc(usedEnd, freeEnd, pinkSide)
                    sideArc(start, usedEnd, blueSide)
                } else {
                    sideArc(start, usedEnd, blueSide)
                    sideArc(usedEnd, freeEnd, pinkSide)
                }
                topWedge(usedEnd, freeEnd, pinkTop)
                topWedge(start, usedEnd, blueTop)
            }
        }
    }
}
