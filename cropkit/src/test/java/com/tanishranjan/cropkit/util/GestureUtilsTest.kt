package com.tanishranjan.cropkit.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tanishranjan.cropkit.internal.DragHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class GestureUtilsTest {

    private val image = Rect(0f, 0f, 1000f, 800f)
    private val crop = Rect(200f, 200f, 700f, 600f) // 500 x 400, well inside the image

    private fun drag(
        handle: DragHandle,
        amount: Offset,
        imageRect: Rect = image,
        cropRect: Rect = crop,
        minSize: Float = MIN,
        lockAspectRatio: Boolean = false
    ): Rect {
        val result = GestureUtils.getNewRectMeasures(
            handle, amount, imageRect, cropRect, minSize, lockAspectRatio
        )
        assertNotNull("$handle by $amount returned null", result)
        assertValid(result!!, imageRect, cropRect, minSize, handle, amount)
        return result
    }

    /** Invariants that must hold after every drag. */
    private fun assertValid(
        result: Rect,
        imageRect: Rect,
        before: Rect,
        minSize: Float,
        handle: DragHandle,
        amount: Offset
    ) {
        val msg = "$handle by $amount on crop=$before image=$imageRect -> $result"
        assertTrue("not finite: $msg", listOf(result.left, result.top, result.right, result.bottom).all { it.isFinite() })
        assertTrue("inverted: $msg", result.left <= result.right && result.top <= result.bottom)
        assertTrue(
            "out of bounds: $msg",
            result.left >= imageRect.left - EPS && result.top >= imageRect.top - EPS &&
                result.right <= imageRect.right + EPS && result.bottom <= imageRect.bottom + EPS
        )
        // Min size holds whenever it held before, and an undersized crop never shrinks further.
        assertTrue("width shrank below min: $msg", result.width >= minOf(minSize, before.width) - EPS)
        assertTrue("height shrank below min: $msg", result.height >= minOf(minSize, before.height) - EPS)
        // No jumps: every edge moves at most by the drag amount and only in the drag direction.
        assertEdgeFollowsDrag(before.left, result.left, amount.x, msg)
        assertEdgeFollowsDrag(before.right, result.right, amount.x, msg)
        assertEdgeFollowsDrag(before.top, result.top, amount.y, msg)
        assertEdgeFollowsDrag(before.bottom, result.bottom, amount.y, msg)
    }

    private fun assertEdgeFollowsDrag(before: Float, after: Float, drag: Float, msg: String) {
        val moved = after - before
        if (abs(moved) <= EPS) return
        assertTrue("edge moved against the drag: $msg", moved * drag > 0)
        assertTrue("edge moved further than the drag: $msg", abs(moved) <= abs(drag) + EPS)
    }

    // region Regression for the reported crash

    @Test
    fun `top handle on image shorter than min size does not crash`() {
        // Numbers from the crash: imageRect.top = 417.2, cropRect.bottom - 250 = 368.8
        val shortImage = Rect(0f, 417.2f, 1080f, 618.8f)
        for (dy in listOf(-500f, -10f, 0f, 10f, 500f)) {
            val result = drag(DragHandle.Top, Offset(0f, dy), shortImage, shortImage)
            assertEquals(shortImage, result) // can neither grow (boundary) nor shrink (min size)
        }
    }

    @Test
    fun `every edge handle on image smaller than min size does not crash`() {
        val tiny = Rect(30f, 40f, 130f, 140f) // 100 x 100, min is 250
        for (handle in DragHandle.entries) for (amount in directions(1000f)) {
            assertEquals(tiny, drag(handle, amount, tiny, tiny))
        }
    }

    // endregion

    // region Valid drags keep the previous behaviour

    @Test
    fun `edge handles move freely inside the valid range`() {
        assertEquals(Rect(200f, 150f, 700f, 600f), drag(DragHandle.Top, Offset(37f, -50f)))
        assertEquals(Rect(200f, 200f, 700f, 650f), drag(DragHandle.Bottom, Offset(37f, 50f)))
        assertEquals(Rect(150f, 200f, 700f, 600f), drag(DragHandle.Left, Offset(-50f, 37f)))
        assertEquals(Rect(200f, 200f, 750f, 600f), drag(DragHandle.Right, Offset(50f, 37f)))
    }

    @Test
    fun `corner handles move freely inside the valid range`() {
        val d = Offset(-20f, 30f)
        assertEquals(Rect(180f, 230f, 700f, 600f), drag(DragHandle.TopLeft, d))
        assertEquals(Rect(200f, 230f, 680f, 600f), drag(DragHandle.TopRight, d))
        assertEquals(Rect(180f, 200f, 700f, 630f), drag(DragHandle.BottomLeft, d))
        assertEquals(Rect(200f, 200f, 680f, 630f), drag(DragHandle.BottomRight, d))
    }

    @Test
    fun `every handle in every direction stays valid`() {
        for (handle in DragHandle.entries) for (amount in directions(40f)) drag(handle, amount)
    }

    // endregion

    // region Boundaries and minimum size

    @Test
    fun `dragging outward stops at each image boundary`() {
        val big = 10_000f
        assertEquals(image.top, drag(DragHandle.Top, Offset(0f, -big)).top)
        assertEquals(image.bottom, drag(DragHandle.Bottom, Offset(0f, big)).bottom)
        assertEquals(image.left, drag(DragHandle.Left, Offset(-big, 0f)).left)
        assertEquals(image.right, drag(DragHandle.Right, Offset(big, 0f)).right)
        assertEquals(Rect(image.left, image.top, crop.right, crop.bottom), drag(DragHandle.TopLeft, Offset(-big, -big)))
        assertEquals(Rect(crop.left, image.top, image.right, crop.bottom), drag(DragHandle.TopRight, Offset(big, -big)))
        assertEquals(Rect(image.left, crop.top, crop.right, image.bottom), drag(DragHandle.BottomLeft, Offset(-big, big)))
        assertEquals(Rect(crop.left, crop.top, image.right, image.bottom), drag(DragHandle.BottomRight, Offset(big, big)))
    }

    @Test
    fun `dragging inward stops at min size`() {
        val big = 10_000f
        assertEquals(crop.bottom - MIN, drag(DragHandle.Top, Offset(0f, big)).top)
        assertEquals(crop.top + MIN, drag(DragHandle.Bottom, Offset(0f, -big)).bottom)
        assertEquals(crop.right - MIN, drag(DragHandle.Left, Offset(big, 0f)).left)
        assertEquals(crop.left + MIN, drag(DragHandle.Right, Offset(-big, 0f)).right)
        assertEquals(
            Rect(crop.right - MIN, crop.bottom - MIN, crop.right, crop.bottom),
            drag(DragHandle.TopLeft, Offset(big, big))
        )
        assertEquals(
            Rect(crop.left, crop.top, crop.left + MIN, crop.top + MIN),
            drag(DragHandle.BottomRight, Offset(-big, -big))
        )
    }

    @Test
    fun `free form corner slides along a boundary instead of freezing`() {
        // Previously the whole drag was rejected when only x was out of range.
        val atLeftEdge = Rect(0f, 200f, 700f, 600f)
        val result = drag(DragHandle.TopLeft, Offset(-15f, -20f), cropRect = atLeftEdge)
        assertEquals(Rect(0f, 180f, 700f, 600f), result)
    }

    @Test
    fun `crop touching all image boundaries`() {
        for (handle in DragHandle.entries) for (amount in directions(300f)) {
            drag(handle, amount, cropRect = image)
        }
        // Outward drags are no-ops.
        assertEquals(image, drag(DragHandle.TopLeft, Offset(-5f, -5f), cropRect = image))
        assertEquals(image, drag(DragHandle.Right, Offset(5f, 0f), cropRect = image))
    }

    @Test
    fun `crop smaller than min size can grow but not shrink`() {
        val small = Rect(400f, 300f, 500f, 400f) // 100 x 100
        assertEquals(Rect(400f, 300f, 500f, 400f), drag(DragHandle.Right, Offset(-30f, 0f), cropRect = small))
        assertEquals(Rect(400f, 300f, 530f, 400f), drag(DragHandle.Right, Offset(30f, 0f), cropRect = small))
        assertEquals(Rect(370f, 300f, 500f, 400f), drag(DragHandle.Left, Offset(-30f, 0f), cropRect = small))
        assertEquals(Rect(400f, 300f, 500f, 400f), drag(DragHandle.Top, Offset(0f, 30f), cropRect = small))
        assertEquals(Rect(370f, 270f, 500f, 400f), drag(DragHandle.TopLeft, Offset(-30f, -30f), cropRect = small))
        for (handle in DragHandle.entries) for (amount in directions(1000f)) {
            drag(handle, amount, cropRect = small)
        }
    }

    @Test
    fun `range where min would exceed max is handled for every handle`() {
        // Crop narrower than min and pinned against the opposite boundary on each side.
        val pinnedLeft = Rect(0f, 0f, 100f, 100f)
        val pinnedRight = Rect(900f, 700f, 1000f, 800f)
        for (cropRect in listOf(pinnedLeft, pinnedRight)) {
            for (handle in DragHandle.entries) for (amount in directions(500f)) {
                drag(handle, amount, cropRect = cropRect)
            }
        }
    }

    @Test
    fun `zero and negative min size are accepted`() {
        for (min in listOf(0f, -50f)) for (handle in DragHandle.entries) {
            for (amount in directions(1000f)) drag(handle, amount, minSize = min)
        }
    }

    @Test
    fun `large drag offsets are clamped`() {
        for (handle in DragHandle.entries) for (amount in directions(Float.MAX_VALUE)) {
            drag(handle, amount)
            drag(handle, amount, lockAspectRatio = true)
        }
    }

    @Test
    fun `non finite drag is rejected for every handle`() {
        for (handle in DragHandle.entries) {
            val amounts = listOf(
                Offset(Float.NaN, 0f), Offset(0f, Float.NaN),
                Offset(Float.POSITIVE_INFINITY, 0f), Offset(0f, Float.NEGATIVE_INFINITY)
            )
            for (amount in amounts) {
                assertNull(GestureUtils.getNewRectMeasures(handle, amount, image, crop, MIN))
            }
        }
    }

    // endregion

    // region Aspect ratio

    @Test
    fun `locked corner drag inside the range is unchanged`() {
        val square = Rect(200f, 200f, 600f, 600f)
        val result = drag(DragHandle.BottomRight, Offset(50f, 50f), cropRect = square, lockAspectRatio = true)
        assertEquals(Rect(200f, 200f, 650f, 650f), result)
    }

    @Test
    fun `locked corner drag stops at the first boundary and keeps the ratio`() {
        val square = Rect(200f, 200f, 600f, 600f)
        // Bottom has 200px of room, right has 400px: both axes must stop after 200px.
        val result = drag(DragHandle.BottomRight, Offset(300f, 300f), cropRect = square, lockAspectRatio = true)
        assertEquals(Rect(200f, 200f, 800f, 800f), result)
        assertEquals(1f, result.width / result.height, RATIO_EPS)
    }

    @Test
    fun `locked corner drag stops at min size and keeps the ratio`() {
        val wide = Rect(100f, 100f, 900f, 500f) // 2:1
        val result = drag(DragHandle.TopLeft, Offset(1000f, 500f), cropRect = wide, lockAspectRatio = true)
        assertEquals(Rect(400f, 250f, 900f, 500f), result) // height reached 250 first
        assertEquals(2f, result.width / result.height, RATIO_EPS)
    }

    @Test
    fun `locked corner cannot grow or shrink when both are blocked`() {
        // 1:1 crop on an image only 200px tall with min 250: initial state of CropShape.AspectRatio(1f).
        val shortImage = Rect(0f, 0f, 1000f, 200f)
        val square = Rect(400f, 0f, 600f, 200f)
        for (handle in CORNERS) for (amount in directions(100f)) {
            val ratioDrag = Offset(amount.x, amount.x * if (amount.y < 0) -1 else 1)
            assertEquals(square, drag(handle, ratioDrag, shortImage, square, lockAspectRatio = true))
        }
    }

    @Test
    fun `locked drags keep the ratio for every corner and ratio`() {
        val random = Random(7)
        for (ratio in listOf(1f, 16f / 9, 9f / 16, 4f / 3, 2f)) for (handle in CORNERS) {
            var cropRect = Rect(300f, 300f, 300f + 200f * ratio, 500f).let {
                if (it.right > image.right) Rect(300f, 300f, 600f, 300f + 300f / ratio) else it
            }
            repeat(200) {
                val dx = (random.nextFloat() - 0.5f) * 400f
                // Matches what CropStateManager.getDragAmountForShape produces: dy = ±dx / ratio.
                val sign = if (handle == DragHandle.TopLeft || handle == DragHandle.BottomRight) 1 else -1
                val amount = Offset(dx, sign * dx / ratio)
                cropRect = drag(handle, amount, cropRect = cropRect, lockAspectRatio = true)
                assertEquals("$handle $ratio $cropRect", ratio, cropRect.width / cropRect.height, 1e-3f)
            }
        }
    }

    // endregion

    @Test
    fun `random drag sequences keep the crop valid`() {
        val random = Random(42)
        repeat(300) {
            val w = random.nextFloat() * 1500f + 1f
            val h = random.nextFloat() * 1500f + 1f
            val imageRect = Rect(random.nextFloat() * 300f, random.nextFloat() * 500f, 0f, 0f)
                .let { Rect(it.left, it.top, it.left + w, it.top + h) }
            var cropRect = imageRect
            val handle = DragHandle.entries.random(random)
            repeat(100) {
                val scale = if (random.nextInt(10) == 0) 5000f else 60f
                val amount = Offset(
                    (random.nextFloat() - 0.5f) * scale,
                    (random.nextFloat() - 0.5f) * scale
                )
                cropRect = drag(handle, amount, imageRect, cropRect)
            }
        }
    }

    private fun directions(d: Float) = listOf(
        Offset(-d, -d), Offset(0f, -d), Offset(d, -d),
        Offset(-d, 0f), Offset.Zero, Offset(d, 0f),
        Offset(-d, d), Offset(0f, d), Offset(d, d)
    )

    private companion object {
        const val MIN = 250f
        const val EPS = 1e-3f
        const val RATIO_EPS = 1e-4f
        val CORNERS = listOf(
            DragHandle.TopLeft, DragHandle.TopRight, DragHandle.BottomLeft, DragHandle.BottomRight
        )
    }
}
