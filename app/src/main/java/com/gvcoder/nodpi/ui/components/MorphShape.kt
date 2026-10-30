package com.gvcoder.nodpi.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import kotlin.math.cos
import kotlin.math.sin

/** Shape of [morph] at [progress], rotated by [rotation] degrees around its center and fitted into the bounds */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bounds = morph.calculateMaxBounds()
        val left = bounds[0]
        val top = bounds[1]
        val width = bounds[2] - left
        val height = bounds[3] - top
        val cx = left + width / 2
        val cy = top + height / 2
        val radians = Math.toRadians(rotation.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()

        fun x(px: Float, py: Float) = ((px - cx) * cos - (py - cy) * sin + width / 2) / width * size.width
        fun y(px: Float, py: Float) = ((px - cx) * sin + (py - cy) * cos + height / 2) / height * size.height

        val path = Path()
        val cubics = morph.asCubics(progress)
        if (cubics.isEmpty()) {
            return Outline.Rectangle(size.toRect())
        }
        val first = cubics.first()
        path.moveTo(x(first.anchor0X, first.anchor0Y), y(first.anchor0X, first.anchor0Y))
        for (c in cubics) {
            path.cubicTo(
                x(c.control0X, c.control0Y), y(c.control0X, c.control0Y),
                x(c.control1X, c.control1Y), y(c.control1X, c.control1Y),
                x(c.anchor1X, c.anchor1Y), y(c.anchor1X, c.anchor1Y),
            )
        }
        path.close()
        return Outline.Generic(path)
    }

    private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)
}
