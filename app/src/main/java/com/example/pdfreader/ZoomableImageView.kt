package com.example.pdfreader

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/**
 * ImageView with pinch-to-zoom, drag-to-pan (when zoomed) and
 * double-tap to toggle zoom. Swipe callbacks fire on a horizontal
 * fling while not zoomed, so the reader can change page.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null

    private var currentScale = 1f
    private val lastTouch = PointF()
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = (currentScale * factor).coerceIn(1f, 5f)
                val applied = newScale / currentScale
                currentScale = newScale
                val m = Matrix(imageMatrix)
                m.postScale(applied, applied, detector.focusX, detector.focusY)
                imageMatrix = m
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1f) resetZoom() else zoomTo(2.5f, e.x, e.y)
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (currentScale <= 1f && e1 != null) {
                    val dx = e2.x - e1.x
                    if (abs(dx) > 90 && abs(velocityX) > 350) {
                        if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                        return true
                    }
                }
                return false
            }
        })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetZoom()
        // Fit the page to the view once layout sizes are known.
        post { fitToView() }
    }

    private fun fitToView() {
        val drawable = drawable ?: return
        if (width == 0 || height == 0) return
        val scale = minOf(
            width.toFloat() / drawable.intrinsicWidth,
            height.toFloat() / drawable.intrinsicHeight
        )
        val dx = (width - drawable.intrinsicWidth * scale) / 2f
        val dy = (height - drawable.intrinsicHeight * scale) / 2f
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(dx, dy)
        imageMatrix = m
        currentScale = 1f
        // Remember the fit scale as the base for pinch calculations.
        baseScale = scale
    }

    private var baseScale = 1f

    private fun zoomTo(targetRelativeScale: Float, x: Float, y: Float) {
        // Re-fit first, then scale up around the tap point.
        fitToView()
        val m = Matrix(imageMatrix)
        m.postScale(targetRelativeScale, targetRelativeScale, x, y)
        imageMatrix = m
        currentScale = targetRelativeScale
    }

    fun resetZoom() {
        currentScale = 1f
        fitToView()
    }

    fun isZoomed(): Boolean = currentScale > 1.01f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(event.x, event.y)
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && currentScale > 1f && event.pointerCount == 1 &&
                    !scaleDetector.isInProgress
                ) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y
                    val m = Matrix(imageMatrix)
                    m.postTranslate(dx, dy)
                    imageMatrix = m
                    lastTouch.set(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}
