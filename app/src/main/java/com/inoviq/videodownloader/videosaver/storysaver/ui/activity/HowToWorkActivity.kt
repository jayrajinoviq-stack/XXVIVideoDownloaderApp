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

class HowToWorkActivity : BaseActivity() {

    private lateinit var binding: ActivityHowToWorkBinding
    private lateinit var adapter: HowToWorkAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHowToWorkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        setupViewPager()
        setupListeners()
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
            handleBackPress()
        }

        binding.btnHowToWorkAction.setOnClickListener {
            val current = binding.vpHowToWork.currentItem
            if (current < 3) {
                binding.vpHowToWork.currentItem = current + 1
            } else {
                handleBackPress()
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
