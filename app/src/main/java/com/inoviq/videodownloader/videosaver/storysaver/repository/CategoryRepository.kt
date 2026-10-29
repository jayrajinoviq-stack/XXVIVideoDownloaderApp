package com.inoviq.videodownloader.videosaver.storysaver.repository

import com.inoviq.videodownloader.videosaver.storysaver.R
import com.inoviq.videodownloader.videosaver.storysaver.model.CategoryItem

class CategoryRepository {

    fun getCategories(selectedId: String = "all"): List<CategoryItem> {
        return listOf(
            CategoryItem(id = "all", title = "All Video Download", iconRes = R.drawable.ic_cat_download, isSelected = selectedId == "all"),
            CategoryItem(id = "insta", title = "Insta Download", iconRes = R.drawable.ic_cat_insta, isSelected = selectedId == "insta"),
            CategoryItem(id = "fb", title = "FB Download", iconRes = R.drawable.ic_cat_facebook, isSelected = selectedId == "fb"),
            CategoryItem(id = "tiktok", title = "TikTok Download", iconRes = R.drawable.ic_cat_tiktok, isSelected = selectedId == "tiktok"),
            CategoryItem(id = "twitter", title = "Twitter Download", iconRes = R.drawable.ic_cat_twitter, isSelected = selectedId == "twitter"),
            CategoryItem(id = "mitron", title = "Mitron Download", iconRes = R.drawable.ic_cat_mitron, isSelected = selectedId == "mitron")
        )
    }
}
