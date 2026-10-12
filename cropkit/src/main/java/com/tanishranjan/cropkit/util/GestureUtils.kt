package com.tanishranjan.cropkit.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tanishranjan.cropkit.internal.DragHandle
import com.tanishranjan.cropkit.HandlesRect

internal object GestureUtils {

    /**
     * Calculate the new crop rectangle based on the drag amount and the active handle.
     *
     * Every moving edge is clamped between the image boundary and the opposite edge minus
     * [minCropSize], so the result always stays inside [imageRect] and is never inverted.
     * If the crop rectangle is already smaller than [minCropSize] along an axis (e.g. the image
     * itself is smaller than that), the edge may grow but not shrink further, instead of jumping
     * to satisfy a constraint that cannot be met.
     *
     * @param activeHandle The handle that is currently being dragged.
     * @param dragAmount The offset by which the handle is dragged.
     * @param imageRect The current image rect.
     * @param cropRect The current crop rect.
     * @param minCropSize The minimum size of crop rectangle that should be maintained.
     * @param lockAspectRatio Whether corner drags must keep the ratio of [dragAmount]. When true,
     * the drag is shortened uniformly to the first constraint it hits instead of per axis.
     *
     * @return The new crop rectangle, or null if [dragAmount] is not finite.
     */
    fun getNewRectMeasures(
        activeHandle: DragHandle,
        dragAmount: Offset,
        imageRect: Rect,
        cropRect: Rect,
        minCropSize: Float,
        lockAspectRatio: Boolean = false
    ): Rect? {
        if (!dragAmount.x.isFinite() || !dragAmount.y.isFinite()) return null

        val minSize = minCropSize.coerceAtLeast(0f)

        fun left(x: Float) = clampLowEdge(x, cropRect.left, cropRect.right, imageRect.left, minSize)
        fun top(y: Float) = clampLowEdge(y, cropRect.top, cropRect.bottom, imageRect.top, minSize)
        fun right(x: Float) =
            clampHighEdge(x, cropRect.right, cropRect.left, imageRect.right, minSize)
        fun bottom(y: Float) =
            clampHighEdge(y, cropRect.bottom, cropRect.top, imageRect.bottom, minSize)

        return when (activeHandle) {

            DragHandle.TopLeft -> {
                val corner =
                    dragCorner(cropRect.topLeft, dragAmount, ::left, ::top, lockAspectRatio)
                Rect(
                    left = corner.x,
                    top = corner.y,
                    right = cropRect.right,
                    bottom = cropRect.bottom
                )
            }

            DragHandle.TopRight -> {
                val corner =
                    dragCorner(cropRect.topRight, dragAmount, ::right, ::top, lockAspectRatio)
                Rect(
                    left = cropRect.left,
                    top = corner.y,
                    right = corner.x,
                    bottom = cropRect.bottom
                )
            }

            DragHandle.BottomLeft -> {
                val corner =
                    dragCorner(cropRect.bottomLeft, dragAmount, ::left, ::bottom, lockAspectRatio)
                Rect(
                    left = corner.x,
                    top = cropRect.top,
                    right = cropRect.right,
                    bottom = corner.y
                )
            }

            DragHandle.BottomRight -> {
                val corner =
                    dragCorner(cropRect.bottomRight, dragAmount, ::right, ::bottom, lockAspectRatio)
                Rect(
                    left = cropRect.left,
                    top = cropRect.top,
                    right = corner.x,
                    bottom = corner.y
                )
            }

            DragHandle.Top -> Rect(
                left = cropRect.left,
                top = top(cropRect.top + dragAmount.y),
                right = cropRect.right,
                bottom = cropRect.bottom
            )

            DragHandle.Bottom -> Rect(
                left = cropRect.left,
                top = cropRect.top,
                right = cropRect.right,
                bottom = bottom(cropRect.bottom + dragAmount.y)
            )

            DragHandle.Left -> Rect(
                left = left(cropRect.left + dragAmount.x),
                top = cropRect.top,
                right = cropRect.right,
                bottom = cropRect.bottom
            )

            DragHandle.Right -> Rect(
                left = cropRect.left,
                top = cropRect.top,
                right = right(cropRect.right + dragAmount.x),
                bottom = cropRect.bottom
            )
        }
    }

    /**
     * Clamp a left/top edge between the image [boundary] and the [opposite] edge minus [minSize].
     *
     * The upper bound never drops below the [current] position, so a crop that is already
     * smaller than [minSize] is not forced to jump, and the range can never be empty.
     */
    private fun clampLowEdge(
        value: Float,
        current: Float,
        opposite: Float,
        boundary: Float,
        minSize: Float
    ): Float {
        val max = maxOf(opposite - minSize, minOf(current, opposite))
        val min = minOf(boundary, max)
        return value.coerceIn(min, max)
    }

    /**
     * Clamp a right/bottom edge between the [opposite] edge plus [minSize] and the image [boundary].
     *
     * Mirror of [clampLowEdge].
     */
    private fun clampHighEdge(
        value: Float,
        current: Float,
        opposite: Float,
        boundary: Float,
        minSize: Float
    ): Float {
        val min = minOf(opposite + minSize, maxOf(current, opposite))
        val max = maxOf(boundary, min)
        return value.coerceIn(min, max)
    }

    /**
     * Move a corner by [dragAmount], clamping each axis with [clampX] and [clampY].
     *
     * With [lockAspectRatio], both axes are shortened by the same factor so the corner stops at the
     * first constraint it reaches while keeping the direction (and therefore the ratio) of the drag.
     */
    private fun dragCorner(
        corner: Offset,
        dragAmount: Offset,
        clampX: (Float) -> Float,
        clampY: (Float) -> Float,
        lockAspectRatio: Boolean
    ): Offset {
        val x = clampX(corner.x + dragAmount.x)
        val y = clampY(corner.y + dragAmount.y)
        if (!lockAspectRatio) return Offset(x, y)

        val factor = minOf(
            allowedFraction(x - corner.x, dragAmount.x),
            allowedFraction(y - corner.y, dragAmount.y)
        )
        return Offset(
            clampX(corner.x + dragAmount.x * factor),
            clampY(corner.y + dragAmount.y * factor)
        )
    }

    private fun allowedFraction(allowed: Float, requested: Float): Float =
        if (requested == 0f) 1f else (allowed / requested).coerceIn(0f, 1f)

    /**
     * Calculate the new handle rectangles based on the crop rectangle.
     *
     * @param cropRect The current crop rectangle.
     * @param handleRadius The radius of the handles.
     *
     * @return The new handle positions.
     */
    fun getNewHandleMeasures(
        cropRect: Rect,
        handleRadius: Float,
    ): HandlesRect {

        val topLeftOffset = cropRect.topLeft - Offset(handleRadius, handleRadius)
        val topLeftRect = Rect(
            left = topLeftOffset.x,
            top = topLeftOffset.y,
            right = topLeftOffset.x + handleRadius * 2,
            bottom = topLeftOffset.y + handleRadius * 2
        )

        val topRightOffset = cropRect.topRight - Offset(handleRadius, handleRadius)
        val topRightRect = Rect(
            left = topRightOffset.x,
            top = topRightOffset.y,
            right = topRightOffset.x + handleRadius * 2,
            bottom = topRightOffset.y + handleRadius * 2
        )

        val bottomLeftOffset = cropRect.bottomLeft - Offset(handleRadius, handleRadius)
        val bottomLeftRect = Rect(
            left = bottomLeftOffset.x,
            top = bottomLeftOffset.y,
            right = bottomLeftOffset.x + handleRadius * 2,
            bottom = bottomLeftOffset.y + handleRadius * 2
        )

        val bottomRightOffset = cropRect.bottomRight - Offset(handleRadius, handleRadius)
        val bottomRightRect = Rect(
            left = bottomRightOffset.x,
            top = bottomRightOffset.y,
            right = bottomRightOffset.x + handleRadius * 2,
            bottom = bottomRightOffset.y + handleRadius * 2
        )

        val halfWidth = (cropRect.width) / 2
        val halfHeight = (cropRect.height) / 2

        val topOffset =
            cropRect.topLeft + Offset(halfWidth, 0f) - Offset(handleRadius, handleRadius)
        val topRect = Rect(
            left = topOffset.x,
            top = topOffset.y,
            right = topOffset.x + handleRadius * 2,
            bottom = topOffset.y + handleRadius * 2
        )

        val bottomOffset =
            cropRect.bottomLeft + Offset(halfWidth, 0f) - Offset(handleRadius, handleRadius)
        val bottomRect = Rect(
            left = bottomOffset.x,
            top = bottomOffset.y,
            right = bottomOffset.x + handleRadius * 2,
            bottom = bottomOffset.y + handleRadius * 2
        )

        val leftOffset =
            cropRect.topLeft + Offset(0f, halfHeight) - Offset(handleRadius, handleRadius)
        val leftRect = Rect(
            left = leftOffset.x,
            top = leftOffset.y,
            right = leftOffset.x + handleRadius * 2,
            bottom = leftOffset.y + handleRadius * 2
        )

        val rightOffset =
            cropRect.topRight + Offset(0f, halfHeight) - Offset(handleRadius, handleRadius)
        val rightRect = Rect(
            left = rightOffset.x,
            top = rightOffset.y,
            right = rightOffset.x + handleRadius * 2,
            bottom = rightOffset.y + handleRadius * 2
        )

        return HandlesRect(
            topLeft = topLeftRect,
            topRight = topRightRect,
            bottomLeft = bottomLeftRect,
            bottomRight = bottomRightRect,
            top = topRect,
            bottom = bottomRect,
            left = leftRect,
            right = rightRect
        )

    }

}