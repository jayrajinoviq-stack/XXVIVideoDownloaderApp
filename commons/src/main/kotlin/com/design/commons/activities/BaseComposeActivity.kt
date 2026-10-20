package com.design.commons.activities

import android.content.Context
import androidx.activity.ComponentActivity
import com.design.commons.extensions.baseConfig
import com.design.commons.helpers.MyContextWrapper
import com.design.commons.helpers.REQUEST_APP_UNLOCK
import com.design.commons.helpers.isTiramisuPlus

abstract class BaseComposeActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        maybeLaunchAppUnlockActivity(REQUEST_APP_UNLOCK)
    }

    override fun attachBaseContext(newBase: Context) {
        if (newBase.baseConfig.useEnglish && !isTiramisuPlus()) {
            super.attachBaseContext(MyContextWrapper(newBase).wrap(newBase, "en"))
        } else {
            super.attachBaseContext(newBase)
        }
    }
}
