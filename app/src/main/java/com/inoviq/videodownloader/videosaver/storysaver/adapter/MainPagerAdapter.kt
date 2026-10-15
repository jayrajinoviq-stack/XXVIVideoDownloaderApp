package com.inoviq.videodownloader.videosaver.storysaver.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.BrowserFragment
import com.inoviq.videodownloader.videosaver.storysaver.ui.fragment.FilesFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val browserFragment = BrowserFragment()
    val filesFragment = FilesFragment()

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> browserFragment
            1 -> filesFragment
            else -> browserFragment
        }
    }
}
