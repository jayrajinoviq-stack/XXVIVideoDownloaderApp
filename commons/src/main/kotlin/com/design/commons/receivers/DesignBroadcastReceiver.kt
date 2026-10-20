package com.design.commons.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.design.commons.extensions.syncGlobalConfig
import com.design.commons.helpers.MyContentProvider

class DesignBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == MyContentProvider.ACTION_GLOBAL_CONFIG_UPDATED) {
            context?.syncGlobalConfig()
        }
    }
}
