package com.example.agristockcapstoneproject.utils

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

object CodenameGenerator {
    
    private val adjectives = listOf(
        "Swift", "Bright", "Golden", "Silver", "Bold", "Quick", "Sharp", "Smart",
        "Lucky", "Happy", "Brave", "Strong", "Wise", "Calm", "Cool", "Fast",
        "Wild", "Free", "Pure", "True", "Kind", "Fair", "Just", "Right"
    )
    
    private val nouns = listOf(
        "Tiger", "Eagle", "Wolf", "Lion", "Bear", "Fox", "Hawk", "Falcon",
        "Phoenix", "Dragon", "Shark", "Panther", "Lynx", "Raven", "Crow", "Owl",
        "Sparrow", "Robin", "Dove", "Swan", "Heron", "Crane", "Stork", "Egret"
    )
    
    private val colors = listOf(
        "Red", "Blue", "Green", "Gold", "Silver", "Purple", "Orange", "Pink",
        "Cyan", "Lime", "Coral", "Teal", "Indigo", "Violet", "Crimson", "Emerald"
    )
    
    /**
     * Generate a daily codename for a user based on their UID and current date
     * This ensures the same user gets the same codename on the same day
     */
    fun generateDailyCodename(userId: String): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val seed = "$userId-$date"
        val hash = hashString(seed)
        
        // Use hash to select words deterministically
        val adjectiveIndex = (hash.hashCode() % adjectives.size).let { if (it < 0) it + adjectives.size else it }
        val nounIndex = ((hash.hashCode() / 1000) % nouns.size).let { if (it < 0) it + nouns.size else it }
        val colorIndex = ((hash.hashCode() / 1000000) % colors.size).let { if (it < 0) it + colors.size else it }
        
        return "${colors[colorIndex]}${adjectives[adjectiveIndex]}${nouns[nounIndex]}"
    }
    
    /**
     * Generate a random codename (for testing or special cases)
     */
    fun generateRandomCodename(): String {
        val adjective = adjectives[Random.nextInt(adjectives.size)]
        val noun = nouns[Random.nextInt(nouns.size)]
        val color = colors[Random.nextInt(colors.size)]
        return "${color}${adjective}${noun}"
    }
    
    /**
     * Generate a secure hash from a string
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get codename for a specific date (useful for historical data)
     */
    fun getCodenameForDate(userId: String, date: String): String {
        val seed = "$userId-$date"
        val hash = hashString(seed)
        
        val adjectiveIndex = (hash.hashCode() % adjectives.size).let { if (it < 0) it + adjectives.size else it }
        val nounIndex = ((hash.hashCode() / 1000) % nouns.size).let { if (it < 0) it + nouns.size else it }
        val colorIndex = ((hash.hashCode() / 1000000) % colors.size).let { if (it < 0) it + colors.size else it }
        
        return "${colors[colorIndex]}${adjectives[adjectiveIndex]}${nouns[nounIndex]}"
    }
}
















