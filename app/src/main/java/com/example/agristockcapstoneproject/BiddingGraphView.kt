package com.example.agristockcapstoneproject

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class BiddingGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var bids: List<BidData> = emptyList()
    private var maxAmount: Float = 0f
    private var minAmount: Float = 0f
    private var timeRange: Long = 0L
    
    private val padding = 40f
    private val textSize = 12f
    private val gridColor = Color.parseColor("#E0E0E0")
    private val lineColor = Color.parseColor("#4CAF50")
    private val pointColor = Color.parseColor("#FF5722")
    private val backgroundColor = Color.parseColor("#F5F5F5")

    data class BidData(
        val amount: Float,
        val timestamp: Long,
        val bidderName: String = "Bidder"
    )

    init {
        textPaint.textSize = textSize
        textPaint.color = Color.parseColor("#666666")
        textPaint.textAlign = Paint.Align.CENTER
        
        gridPaint.color = gridColor
        gridPaint.strokeWidth = 1f
        
        paint.strokeWidth = 3f
        paint.color = lineColor
    }

    fun updateBids(bids: List<BidData>) {
        this.bids = bids.sortedBy { it.timestamp }
        if (bids.isNotEmpty()) {
            maxAmount = bids.maxOf { it.amount }
            minAmount = bids.minOf { it.amount }
            timeRange = bids.maxOf { it.timestamp } - bids.minOf { it.timestamp }
            
            // Add some padding to the range
            val amountRange = maxAmount - minAmount
            maxAmount += amountRange * 0.1f
            minAmount -= amountRange * 0.1f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (bids.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()
        
        // Clear background
        canvas.drawColor(backgroundColor)
        
        // Draw grid
        drawGrid(canvas, width, height)
        
        // Draw axes
        drawAxes(canvas, width, height)
        
        // Draw graph line and points
        drawGraph(canvas, width, height)
        
        // Draw labels
        drawLabels(canvas, width, height)
    }

    private fun drawEmptyState(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        
        canvas.drawColor(backgroundColor)
        
        // Draw "No Data" message
        textPaint.textSize = 16f
        textPaint.color = Color.parseColor("#999999")
        canvas.drawText("No Bidding Data Yet", width / 2, height / 2, textPaint)
        
        textPaint.textSize = 12f
        canvas.drawText("Bids will appear here", width / 2, height / 2 + 30, textPaint)
    }

    private fun drawGrid(canvas: Canvas, width: Float, height: Float) {
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding
        
        // Draw horizontal grid lines
        for (i in 0..5) {
            val y = padding + (graphHeight * i / 5)
            canvas.drawLine(padding, y, width - padding, y, gridPaint)
        }
        
        // Draw vertical grid lines
        for (i in 0..5) {
            val x = padding + (graphWidth * i / 5)
            canvas.drawLine(x, padding, x, height - padding, gridPaint)
        }
    }

    private fun drawAxes(canvas: Canvas, width: Float, height: Float) {
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        axisPaint.color = Color.parseColor("#333333")
        axisPaint.strokeWidth = 2f
        
        // Y-axis (amount)
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint)
        
        // X-axis (time)
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint)
    }

    private fun drawGraph(canvas: Canvas, width: Float, height: Float) {
        if (bids.size < 2) return
        
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding
        
        // Draw line connecting points
        paint.color = lineColor
        paint.strokeWidth = 3f
        
        val path = Path()
        var isFirst = true
        
        bids.forEachIndexed { index, bid ->
            val x = padding + (graphWidth * index / (bids.size - 1))
            val normalizedAmount = (bid.amount - minAmount) / (maxAmount - minAmount)
            val y = height - padding - (graphHeight * normalizedAmount)
            
            if (isFirst) {
                path.moveTo(x, y)
                isFirst = false
            } else {
                path.lineTo(x, y)
            }
        }
        
        canvas.drawPath(path, paint)
        
        // Draw points
        paint.color = pointColor
        paint.style = Paint.Style.FILL
        
        bids.forEachIndexed { index, bid ->
            val x = padding + (graphWidth * index / (bids.size - 1))
            val normalizedAmount = (bid.amount - minAmount) / (maxAmount - minAmount)
            val y = height - padding - (graphHeight * normalizedAmount)
            
            canvas.drawCircle(x, y, 6f, paint)
        }
    }

    private fun drawLabels(canvas: Canvas, width: Float, height: Float) {
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding
        
        // Y-axis labels (amounts)
        for (i in 0..5) {
            val amount = minAmount + (maxAmount - minAmount) * (5 - i) / 5
            val y = padding + (graphHeight * i / 5)
            
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 10f
            canvas.drawText("₱${String.format("%.0f", amount)}", padding - 10, y + 4, textPaint)
        }
        
        // X-axis labels (time)
        if (bids.isNotEmpty()) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startTime = bids.first().timestamp
            val endTime = bids.last().timestamp
            
            for (i in 0..4) {
                val time = startTime + (endTime - startTime) * i / 4
                val x = padding + (graphWidth * i / 4)
                
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = 10f
                canvas.drawText(timeFormat.format(Date(time)), x, height - padding + 20, textPaint)
            }
        }
    }
}



