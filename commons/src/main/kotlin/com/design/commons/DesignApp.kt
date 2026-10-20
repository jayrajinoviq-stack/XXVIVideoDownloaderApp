package com.design.commons

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.design.commons.extensions.appLockManager
import com.design.commons.extensions.checkUseEnglish

open class DesignApp : Application() {

    open val isAppLockFeatureAvailable = false

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        setupAppLockManager()
    }

    private fun setupAppLockManager() {
        if (isAppLockFeatureAvailable) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLockManager)
        }
    }
}
