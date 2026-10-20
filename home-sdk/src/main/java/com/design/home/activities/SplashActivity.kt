package com.design.home.activities

import android.content.Intent
import com.design.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, LauncherHomeActivity::class.java))
        finish()
    }
}
