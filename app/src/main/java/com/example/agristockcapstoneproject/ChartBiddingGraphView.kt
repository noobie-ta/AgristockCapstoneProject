package com.example.agristockcapstoneproject

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class ChartBiddingGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LineChart(context, attrs, defStyleAttr) {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    data class BidData(
        val amount: Float,
        val timestamp: Long,
        val bidderName: String = "Bidder"
    )

    init {
        setupChart()
    }

    private fun setupChart() {
        // Configure chart appearance
        description.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
        setBackgroundColor(Color.parseColor("#F5F5F5"))
        
        // Configure legend
        legend.isEnabled = false
        
        // Configure X-axis
        val xAxis = xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(true)
        xAxis.textColor = Color.parseColor("#666666")
        xAxis.textSize = 10f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return dateFormat.format(Date(value.toLong()))
            }
        }
        
        // Configure Y-axis (left)
        val leftAxis = axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.setDrawAxisLine(true)
        leftAxis.textColor = Color.parseColor("#666666")
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₱${String.format("%.0f", value)}"
            }
        }
        
        // Configure Y-axis (right)
        val rightAxis = axisRight
        rightAxis.isEnabled = false
        
        // Configure grid
        setDrawGridBackground(false)
        setDrawBorders(true)
        setBorderColor(Color.parseColor("#E0E0E0"))
        setBorderWidth(1f)
    }

    fun updateBids(bids: List<BidData>) {
        if (bids.isEmpty()) {
            clear()
            return
        }

        // Sort bids by timestamp
        val sortedBids = bids.sortedBy { it.timestamp }
        
        // Create entries for the chart
        val entries = sortedBids.mapIndexed { index, bid ->
            Entry(index.toFloat(), bid.amount)
        }
        
        // Create dataset
        val dataSet = LineDataSet(entries, "Bidding Amount")
        dataSet.color = Color.parseColor("#4CAF50")
        dataSet.setCircleColor(Color.parseColor("#FF5722"))
        dataSet.lineWidth = 3f
        dataSet.circleRadius = 6f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#804CAF50")
        dataSet.setDrawValues(false)
        dataSet.setDrawCircleHole(false)
        
        // Create line data
        val lineData = LineData(dataSet)
        lineData.setValueTextColor(Color.parseColor("#333333"))
        lineData.setValueTextSize(10f)
        
        // Set data and animate
        data = lineData
        animateX(1000)
        animateY(1000)
        
        // Fit screen
        fitScreen()
    }
}



