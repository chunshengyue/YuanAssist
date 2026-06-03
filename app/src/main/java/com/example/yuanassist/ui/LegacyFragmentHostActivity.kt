package com.example.yuanassist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.yuanassist.R

class LegacyFragmentHostActivity : AppCompatActivity() {

    enum class Screen {
        DAILY_BIRD_FOOD,
        DAILY_MAINLINE_624,
        DAILY_STARGAZING,
        DAILY_AILAO_15_MIN,
        DAILY_INVENTORY_STITCH,
        CHARACTER_IMPORT,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legacy_fragment_host)

        if (savedInstanceState != null) {
            return
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, resolveFragment(resolveScreen(intent)))
            .commit()
    }

    private fun resolveScreen(intent: Intent?): Screen {
        val name = intent?.getStringExtra(EXTRA_SCREEN) ?: return Screen.DAILY_BIRD_FOOD
        return Screen.values().firstOrNull { it.name == name } ?: Screen.DAILY_BIRD_FOOD
    }

    private fun resolveFragment(screen: Screen): Fragment {
        return when (screen) {
            Screen.DAILY_BIRD_FOOD -> DailyBirdFoodFragment()
            Screen.DAILY_MAINLINE_624 -> DailyMainline624Fragment()
            Screen.DAILY_STARGAZING -> StargazingFragment()
            Screen.DAILY_AILAO_15_MIN -> Ailao15MinFragment()
            Screen.DAILY_INVENTORY_STITCH -> DailyInventoryStitchFragment()
            Screen.CHARACTER_IMPORT -> CharacterImportFragment()
        }
    }

    companion object {
        private const val EXTRA_SCREEN = "extra_screen"

        fun createIntent(context: Context, screen: Screen): Intent {
            return Intent(context, LegacyFragmentHostActivity::class.java).apply {
                putExtra(EXTRA_SCREEN, screen.name)
            }
        }
    }
}
