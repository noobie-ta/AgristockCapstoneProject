package com.example.agristockcapstoneproject.utils

import android.app.Activity
import android.graphics.Color
import androidx.core.view.WindowCompat

object StatusBarUtil {
    /**
     * Makes the status bar transparent to show phone status
     * Call this in onCreate() of your activity
     */
    fun makeTransparent(activity: Activity, lightIcons: Boolean = true) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = Color.TRANSPARENT
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insetsController.isAppearanceLightStatusBars = lightIcons
    }
    
    /**
     * Sets a solid color status bar (for screens with dark backgrounds)
     */
    fun setSolidColor(activity: Activity, color: Int, lightIcons: Boolean = false) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        activity.window.statusBarColor = color
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insetsController.isAppearanceLightStatusBars = lightIcons
    }
}


