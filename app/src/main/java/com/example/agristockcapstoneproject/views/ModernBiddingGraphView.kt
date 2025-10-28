package com.example.agristockcapstoneproject.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.agristockcapstoneproject.R
import com.example.agristockcapstoneproject.utils.CodenameGenerator
import java.text.NumberFormat
import java.util.*

class ModernBiddingGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var bidData = mutableListOf<BidData>()
    private var maxBid = 0.0
    private var minBid = 0.0
    
    data class BidData(
        val amount: Double,
        val timestamp: Long,
        val bidderId: String,
        val bidderCodename: String
    )
    
    init {
        setupPaints()
    }
    
    private fun setupPaints() {
        // Background paint (white)
        backgroundPaint.color = Color.WHITE
        backgroundPaint.style = Paint.Style.FILL
        
        // Bar paint with gradient
        paint.style = Paint.Style.FILL
        
        // Text paint
        textPaint.color = Color.BLACK
        textPaint.textSize = 24f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
    }
    
    fun updateBidData(bids: List<BidData>) {
        bidData.clear()
        bidData.addAll(bids)
        
        if (bidData.isNotEmpty()) {
            maxBid = bidData.maxOf { it.amount }
            minBid = bidData.minOf { it.amount }
        }
        
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw white background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        if (bidData.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        
        drawGraph(canvas)
    }
    
    private fun drawEmptyState(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        
        textPaint.color = Color.GRAY
        textPaint.textSize = 32f
        canvas.drawText("📊", centerX, centerY - 20, textPaint)
        
        textPaint.textSize = 16f
        canvas.drawText("No bids yet", centerX, centerY + 20, textPaint)
    }
    
    private fun drawGraph(canvas: Canvas) {
        val padding = 60f
        val barWidth = (width - padding * 2) / bidData.size
        val maxHeight = height - padding * 2
        
        val numberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
        
        bidData.forEachIndexed { index, bid ->
            val x = padding + index * barWidth + barWidth / 2
            val barHeight = if (maxBid > minBid) {
                ((bid.amount - minBid) / (maxBid - minBid)) * maxHeight
            } else {
                maxHeight * 0.5f
            }
            
            val y = height - padding - barHeight.toFloat()
            
            // Create gradient for the bar
            val gradient = LinearGradient(
                x - barWidth / 2, y,
                x + barWidth / 2, y + barHeight.toFloat(),
                getBarColor(bid.amount),
                getBarColor(bid.amount, true),
                Shader.TileMode.CLAMP
            )
            
            paint.shader = gradient
            
            // Draw bar with rounded corners
            val rect = RectF(
                x - barWidth / 2 + 4,
                y,
                x + barWidth / 2 - 4,
                y + barHeight.toFloat()
            )
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            
            // Draw bid amount on top of bar
            textPaint.color = Color.BLACK
            textPaint.textSize = 12f
            textPaint.textAlign = Paint.Align.CENTER
            
            val amountText = "₱${String.format("%.0f", bid.amount.toFloat())}"
            canvas.drawText(amountText, x, y - 8, textPaint)
            
            // Draw codename below bar
            textPaint.textSize = 10f
            textPaint.color = Color.GRAY
            canvas.drawText(bid.bidderCodename, x, height - padding + 20, textPaint)
            
            // Draw time indicator
            val timeText = formatTime(bid.timestamp)
            textPaint.textSize = 8f
            canvas.drawText(timeText, x, height - padding + 35, textPaint)
        }
        
        // Draw grid lines
        drawGridLines(canvas, padding, maxHeight)
        
        // Draw axis labels
        drawAxisLabels(canvas, padding, maxHeight)
    }
    
    private fun getBarColor(amount: Double, isEnd: Boolean = false): Int {
        val ratio = if (maxBid > minBid) (amount - minBid) / (maxBid - minBid) else 0.5
        return when {
            ratio >= 0.8 -> if (isEnd) Color.parseColor("#4CAF50") else Color.parseColor("#81C784")
            ratio >= 0.6 -> if (isEnd) Color.parseColor("#8BC34A") else Color.parseColor("#AED581")
            ratio >= 0.4 -> if (isEnd) Color.parseColor("#CDDC39") else Color.parseColor("#E6EE9C")
            ratio >= 0.2 -> if (isEnd) Color.parseColor("#FFC107") else Color.parseColor("#FFD54F")
            else -> if (isEnd) Color.parseColor("#FF9800") else Color.parseColor("#FFB74D")
        }
    }
    
    private fun drawGridLines(canvas: Canvas, padding: Float, maxHeight: Float) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gridPaint.color = Color.parseColor("#E0E0E0")
        gridPaint.strokeWidth = 1f
        
        // Horizontal grid lines
        for (i in 1..4) {
            val y = padding + (maxHeight * i / 5)
            canvas.drawLine(padding, y, width - padding, y, gridPaint)
        }
        
        // Vertical grid lines
        val barWidth = (width - padding * 2) / bidData.size
        for (i in 1 until bidData.size) {
            val x = padding + i * barWidth
            canvas.drawLine(x, padding, x, height - padding, gridPaint)
        }
    }
    
    private fun drawAxisLabels(canvas: Canvas, padding: Float, maxHeight: Float) {
        textPaint.color = Color.BLACK
        textPaint.textSize = 12f
        textPaint.textAlign = Paint.Align.LEFT
        
        // Y-axis labels (bid amounts)
        for (i in 0..4) {
            val value = minBid + (maxBid - minBid) * (4 - i) / 4.0
            val y = padding + (maxHeight * i / 4)
            val amountText = "₱${String.format("%.0f", value.toFloat())}"
            canvas.drawText(amountText, 8f, y + 4, textPaint)
        }
        
        // X-axis label
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f
        canvas.drawText("Recent Bids", width / 2f, height - 8f, textPaint)
    }
    
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "${diff / 1000}s ago"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }
}
