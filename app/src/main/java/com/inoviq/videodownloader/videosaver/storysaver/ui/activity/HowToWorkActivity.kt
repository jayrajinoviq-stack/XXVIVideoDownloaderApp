package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.adapter.HowToWorkAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityHowToWorkBinding

class HowToWorkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHowToWorkBinding
    private lateinit var adapter: HowToWorkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        binding = ActivityHowToWorkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupViewPager()
        setupListeners()
    }

    private fun setupWindowInsets() {
        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(binding.llHowToWorkRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.howToWorkTopBar.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )
            binding.howToWorkTopBar.layoutParams.height = (56 * density).toInt() + systemBars.top

            val lp = binding.btnHowToWorkAction.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = (24 * density).toInt() + systemBars.bottom
            lp.leftMargin = (20 * density).toInt() + systemBars.left
            lp.rightMargin = (20 * density).toInt() + systemBars.right
            binding.btnHowToWorkAction.layoutParams = lp

            insets
        }
    }

    private fun setupViewPager() {
        adapter = HowToWorkAdapter()
        binding.vpHowToWork.adapter = adapter
        binding.vpHowToWork.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateStepIndicator(position)
            }
        })
    }

    private fun setupListeners() {
        binding.btnHowToWorkBack.setOnClickListener {
            finish()
        }

        binding.btnHowToWorkAction.setOnClickListener {
            val current = binding.vpHowToWork.currentItem
            if (current < 3) {
                binding.vpHowToWork.currentItem = current + 1
            } else {
                finish()
            }
        }
    }

    private fun updateStepIndicator(position: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        dots.forEachIndexed { index, dot ->
            dot.setBackgroundResource(
                if (index == position) R.drawable.bg_indicator_dot_active
                else R.drawable.bg_indicator_dot_inactive
            )
        }
        binding.btnHowToWorkAction.text = if (position == 3) "Got IT" else "Next"
    }
}
