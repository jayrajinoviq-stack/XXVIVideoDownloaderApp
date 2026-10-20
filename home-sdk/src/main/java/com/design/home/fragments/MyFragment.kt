package com.design.home.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import com.design.home.activities.LauncherHomeActivity

abstract class MyFragment<BINDING : ViewBinding>(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet) {
    protected var activity: LauncherHomeActivity? = null
    protected lateinit var binding: BINDING

    abstract fun setupFragment(activity: LauncherHomeActivity)
}
