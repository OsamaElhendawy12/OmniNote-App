package com.omninote.study

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.*

object GeometryHelper {

    fun distance(a: StrokePoint, b: StrokePoint): Float {
        return hypot(a.x - b.x, a.y - b.y)
    }

    fun strokeBounds(points: List<StrokePoint>): Rect {
        if (points.isEmpty()) return Rect.Zero
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return Rect(minX, minY, maxX, maxY)
    }

    fun isPointInsidePolygon(pt: Offset, polygon: List<StrokePoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val intersect = ((yi > pt.y) != (yj > pt.y)) &&
                    (pt.x < (xj - xi) * (pt.y - yi) / ((yj - yi).takeIf { abs(it) > 0.0001f } ?: 0.0001f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun snapStrokeToGeometricShape(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 6) return points
        val start = points.first()
        val end = points.last()

        var pathLen = 0f
        for (i in 1 until points.size) {
            pathLen += distance(points[i - 1], points[i])
        }
        val directDist = distance(start, end)

        if (directDist > pathLen * 0.82f) {
            val steps = 24
            return (0..steps).map { idx ->
                val t = idx / steps.toFloat()
                StrokePoint(
                    x = start.x + (end.x - start.x) * t,
                    y = start.y + (end.y - start.y) * t,
                    pressure = 0.65f
                )
            }
        }

        val bounds = strokeBounds(points)
        if (bounds.width < 25f || bounds.height < 25f) return points

        val cx = bounds.center.x
        val cy = bounds.center.y
        val rx = bounds.width / 2f
        val ry = bounds.height / 2f

        var radialErrorSum = 0f
        for (p in points) {
            val nx = (p.x - cx) / max(1f, rx)
            val ny = (p.y - cy) / max(1f, ry)
            val r = sqrt(nx * nx + ny * ny)
            radialErrorSum += abs(r - 1f)
        }
        val avgRadialError = radialErrorSum / points.size

        return if (avgRadialError < 0.22f) {
            val steps = 48
            (0..steps).map { idx ->
                val angle = (2.0 * Math.PI * idx) / steps
                StrokePoint(
                    x = cx + (rx * cos(angle)).toFloat(),
                    y = cy + (ry * sin(angle)).toFloat(),
                    pressure = 0.65f
                )
            }
        } else {
            val corners = listOf(
                StrokePoint(bounds.left, bounds.top),
                StrokePoint(bounds.right, bounds.top),
                StrokePoint(bounds.right, bounds.bottom),
                StrokePoint(bounds.left, bounds.bottom),
                StrokePoint(bounds.left, bounds.top)
            )
            val denseRect = mutableListOf<StrokePoint>()
            for (i in 0 until 4) {
                val a = corners[i]
                val b = corners[i + 1]
                for (step in 0..10) {
                    val t = step / 10f
                    denseRect.add(
                        StrokePoint(
                            x = a.x + (b.x - a.x) * t,
                            y = a.y + (b.y - a.y) * t,
                            pressure = 0.65f
                        )
                    )
                }
            }
            denseRect
        }
    }
}
