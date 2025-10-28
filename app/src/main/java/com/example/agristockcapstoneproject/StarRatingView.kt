package com.example.agristockcapstoneproject

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class StarRatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rating: Float = 0.0f
    private var maxStars: Int = 5
    private var starSize: Float = 0f
    private var starSpacing: Float = 0f
    
    private val filledPaint = Paint().apply {
        color = 0xFFFFD700.toInt() // Gold color
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val emptyPaint = Paint().apply {
        color = 0xFFCCCCCC.toInt() // Light gray
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val strokePaint = Paint().apply {
        color = 0xFF666666.toInt() // Dark gray
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        starSize = (height * 0.8f).coerceAtMost((width - (maxStars - 1) * paddingHorizontal) / maxStars)
        starSpacing = (width - maxStars * starSize) / (maxStars - 1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerY = height / 2f
        val startX = (width - (maxStars * starSize + (maxStars - 1) * starSpacing)) / 2f
        
        for (i in 0 until maxStars) {
            val x = startX + i * (starSize + starSpacing) + starSize / 2f
            val y = centerY
            
            if (i < rating.toInt()) {
                // Filled star
                drawStar(canvas, x, y, starSize / 2f, filledPaint)
            } else if (i == rating.toInt() && rating % 1 != 0f) {
                // Partial star
                drawPartialStar(canvas, x, y, starSize / 2f, rating % 1)
            } else {
                // Empty star
                drawStar(canvas, x, y, starSize / 2f, emptyPaint)
            }
            
            // Draw star outline
            drawStar(canvas, x, y, starSize / 2f, strokePaint)
        }
    }
    
    private fun drawStar(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, paint: Paint) {
        val path = Path()
        val outerRadius = radius
        val innerRadius = radius * 0.4f
        
        for (i in 0..9) {
            val angle = (i * Math.PI / 5) - Math.PI / 2
            val currentRadius = if (i % 2 == 0) outerRadius else innerRadius
            val x = centerX + (currentRadius * Math.cos(angle)).toFloat()
            val y = centerY + (currentRadius * Math.sin(angle)).toFloat()
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        
        canvas.drawPath(path, paint)
    }
    
    private fun drawPartialStar(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, fillRatio: Float) {
        // Draw empty star first
        drawStar(canvas, centerX, centerY, radius, emptyPaint)
        
        // Draw filled portion
        canvas.save()
        canvas.clipRect(centerX - radius, centerY - radius, centerX - radius + (radius * 2 * fillRatio), centerY + radius)
        drawStar(canvas, centerX, centerY, radius, filledPaint)
        canvas.restore()
    }
    
    fun setRating(rating: Float) {
        this.rating = rating.coerceIn(0f, maxStars.toFloat())
        invalidate()
    }
    
    fun getRating(): Float = rating
    
    fun setMaxStars(maxStars: Int) {
        this.maxStars = maxStars.coerceAtLeast(1)
        requestLayout()
    }
    
    private val paddingHorizontal: Float
        get() = (paddingLeft + paddingRight).toFloat()
}





