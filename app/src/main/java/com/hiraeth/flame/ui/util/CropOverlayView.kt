package com.hiraeth.flame.ui.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    var cropRect = RectF()
        private set

    private val handleSize = 40f
    private var lastX = 0f
    private var lastY = 0f

    private enum class TouchMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private var touchMode = TouchMode.NONE

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val margin = 100f
        cropRect.set(margin, margin, w - margin, h - margin)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw dimmed background
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)

        // Draw crop rectangle
        canvas.drawRect(cropRect, paint)

        // Draw corners
        canvas.drawCircle(cropRect.left, cropRect.top, handleSize / 2, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, handleSize / 2, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleSize / 2, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleSize / 2, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchMode = getTouchMode(x, y)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                
                when (touchMode) {
                    TouchMode.MOVE -> {
                        cropRect.offset(dx, dy)
                    }
                    TouchMode.TOP_LEFT -> {
                        cropRect.left = Math.min(cropRect.right - handleSize * 2, cropRect.left + dx)
                        cropRect.top = Math.min(cropRect.bottom - handleSize * 2, cropRect.top + dy)
                    }
                    TouchMode.TOP_RIGHT -> {
                        cropRect.right = Math.max(cropRect.left + handleSize * 2, cropRect.right + dx)
                        cropRect.top = Math.min(cropRect.bottom - handleSize * 2, cropRect.top + dy)
                    }
                    TouchMode.BOTTOM_LEFT -> {
                        cropRect.left = Math.min(cropRect.right - handleSize * 2, cropRect.left + dx)
                        cropRect.bottom = Math.max(cropRect.top + handleSize * 2, cropRect.bottom + dy)
                    }
                    TouchMode.BOTTOM_RIGHT -> {
                        cropRect.right = Math.max(cropRect.left + handleSize * 2, cropRect.right + dx)
                        cropRect.bottom = Math.max(cropRect.top + handleSize * 2, cropRect.bottom + dy)
                    }
                    TouchMode.NONE -> {}
                }

                // Keep within bounds
                if (cropRect.left < 0) cropRect.left = 0f
                if (cropRect.top < 0) cropRect.top = 0f
                if (cropRect.right > width) cropRect.right = width.toFloat()
                if (cropRect.bottom > height) cropRect.bottom = height.toFloat()
                
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
            }
        }
        return true
    }

    private fun getTouchMode(x: Float, y: Float): TouchMode {
        return when {
            isNear(x, y, cropRect.left, cropRect.top) -> TouchMode.TOP_LEFT
            isNear(x, y, cropRect.right, cropRect.top) -> TouchMode.TOP_RIGHT
            isNear(x, y, cropRect.left, cropRect.bottom) -> TouchMode.BOTTOM_LEFT
            isNear(x, y, cropRect.right, cropRect.bottom) -> TouchMode.BOTTOM_RIGHT
            cropRect.contains(x, y) -> TouchMode.MOVE
            else -> TouchMode.NONE
        }
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dist = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
        return dist < handleSize * 2
    }
}
