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

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    var cropRect = RectF()
        private set

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
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                cropRect.offset(dx, dy)
                
                // Keep within bounds
                if (cropRect.left < 0) cropRect.offset(-cropRect.left, 0f)
                if (cropRect.top < 0) cropRect.offset(0f, -cropRect.top)
                if (cropRect.right > width) cropRect.offset(width - cropRect.right, 0f)
                if (cropRect.bottom > height) cropRect.offset(height - cropRect.bottom, 0f)
                
                lastX = event.x
                lastY = event.y
                invalidate()
            }
        }
        return true
    }
}
