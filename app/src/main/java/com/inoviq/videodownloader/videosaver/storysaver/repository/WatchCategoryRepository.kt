package com.inoviq.videodownloader.videosaver.storysaver.repository

import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.model.WatchCategoryItem

class WatchCategoryRepository {

    fun getWatchCategories(selectedId: String = "disney"): List<WatchCategoryItem> {
        return listOf(
            WatchCategoryItem(id = "disney", title = "Disney Hotstar", logoRes = R.drawable.ic_ott_disney, isSelected = selectedId == "disney"),
            WatchCategoryItem(id = "prime", title = "Amazon Prime", logoRes = R.drawable.ic_ott_amazon_prime, isSelected = selectedId == "prime"),
            WatchCategoryItem(id = "sonyliv", title = "Sony Liv", logoRes = R.drawable.ic_ott_sony_liv, isSelected = selectedId == "sonyliv"),
            WatchCategoryItem(id = "jio", title = "Jio Cinema", logoRes = R.drawable.ic_ott_jio_cinema, isSelected = selectedId == "jio"),
            WatchCategoryItem(id = "apple", title = "Apple TV+", logoRes = R.drawable.ic_ott_apple_tv, isSelected = selectedId == "apple"),
            WatchCategoryItem(id = "netflix", title = "Netflix", logoRes = R.drawable.ic_ott_netflix, isSelected = selectedId == "netflix"),
            WatchCategoryItem(id = "sonysab", title = "Sony SAB", logoRes = R.drawable.ic_ott_sony_sab, isSelected = selectedId == "sonysab")
        )
    }
}
