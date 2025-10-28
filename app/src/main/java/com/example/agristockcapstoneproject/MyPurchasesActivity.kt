package com.example.agristockcapstoneproject

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.agristockcapstoneproject.utils.StatusBarUtil
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MyPurchasesActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_purchases)

        // Make status bar transparent
        StatusBarUtil.makeTransparent(this, lightIcons = true)

        setupViews()
        setupViewPager()
    }

    private fun setupViews() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
    }

    private fun setupViewPager() {
        val adapter = PurchasesPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Bought Items"
                1 -> "Won Bids"
                else -> ""
            }
        }.attach()
    }

    private inner class PurchasesPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> PurchasedItemsFragment.newInstance("BOUGHT")
                1 -> PurchasedItemsFragment.newInstance("WON")
                else -> PurchasedItemsFragment.newInstance("BOUGHT")
            }
        }
    }
}


