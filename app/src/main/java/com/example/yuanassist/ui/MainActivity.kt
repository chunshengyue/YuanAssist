package com.example.yuanassist.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.yuanassist.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val TARGET_TAB_HOME = "home"
        const val TARGET_TAB_DAILY = "daily"
        const val TARGET_TAB_PROFILE = "profile"
        const val TARGET_TAB_STRATEGY = "strategy"
    }

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        bottomNav = findViewById(R.id.bottom_nav)
        configureBottomNav()
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadRootFragment(HomeFragment())
                    true
                }
                R.id.nav_strategy -> {
                    loadRootFragment(StrategyFragment())
                    true
                }
                R.id.nav_daily -> {
                    loadRootFragment(DailyFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadRootFragment(MineFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            navigateToTab(resolveTargetTab(intent?.getStringExtra(EXTRA_TARGET_TAB)))
        }
    }

    fun navigateToTab(@IdRes itemId: Int) {
        if (bottomNav.selectedItemId == itemId) {
            bottomNav.menu.findItem(itemId)?.isChecked = true
            when (itemId) {
                R.id.nav_home -> loadRootFragment(HomeFragment())
                R.id.nav_strategy -> loadRootFragment(StrategyFragment())
                R.id.nav_daily -> loadRootFragment(DailyFragment())
                R.id.nav_profile -> loadRootFragment(MineFragment())
            }
        } else {
            bottomNav.selectedItemId = itemId
        }
    }

    private fun configureBottomNav() {
        bottomNav.post {
            val menuView = bottomNav.getChildAt(0) as? ViewGroup ?: return@post
            for (i in 0 until menuView.childCount) {
                val itemView = menuView.getChildAt(i) as? ViewGroup ?: continue
                itemView.minimumHeight = 0
                itemView.setPadding(0, 0, 0, 0)

                itemView.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view)
                    ?.visibility = View.GONE

                itemView.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_labels_group)
                    ?.let { labelsGroup ->
                        labelsGroup.setPadding(0, 0, 0, 0)
                        val params = labelsGroup.layoutParams
                        if (params is FrameLayout.LayoutParams) {
                            params.gravity = Gravity.CENTER
                            labelsGroup.layoutParams = params
                        }
                    }
            }
        }
    }

    private fun resolveTargetTab(target: String?): Int {
        return when (target) {
            TARGET_TAB_DAILY -> R.id.nav_daily
            TARGET_TAB_PROFILE -> R.id.nav_profile
            TARGET_TAB_STRATEGY -> R.id.nav_strategy
            else -> R.id.nav_home
        }
    }

    private fun loadRootFragment(fragment: Fragment) {
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
