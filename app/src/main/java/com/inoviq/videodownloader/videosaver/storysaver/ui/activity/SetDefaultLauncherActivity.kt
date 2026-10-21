package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.graphics.Color
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.inoviq.videodownloader.videosaver.storysaver.ads.AdManager
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivitySetDefaultLauncherBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.LauncherHelper

class SetDefaultLauncherActivity : BaseActivity() {

    private lateinit var binding: ActivitySetDefaultLauncherBinding

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (LauncherHelper.isDefaultLauncher(this)) {
            LauncherHelper.openLauncherHome(this)
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LauncherHelper.isDefaultLauncher(this)) {
            LauncherHelper.openLauncherHome(this)
            finish()
            return
        }

        binding = ActivitySetDefaultLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        val footerHtml = "You Can Change Or Disable These Features Anytime<br>From <font color='#FA9500'>Settings &gt; Apps &gt; Default Apps &gt; Home App</font>"
        binding.tvFooterInfo.text = Html.fromHtml(footerHtml, Html.FROM_HTML_MODE_COMPACT)

        val openNextAppScreen = {
            AdManager.showInterstitial(this) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        binding.btnLottie.setOnClickListener {
            LauncherHelper.requestDefaultLauncher(this@SetDefaultLauncherActivity, roleLauncher)
        }

        binding.btnSkip.setOnClickListener {
            openNextAppScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        if (LauncherHelper.isDefaultLauncher(this)) {
            LauncherHelper.openLauncherHome(this)
            finish()
        }
    }

    override fun handleBackPress() {
        AdManager.showInterstitial(this) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
