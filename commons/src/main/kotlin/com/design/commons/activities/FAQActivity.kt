package com.design.commons.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.toImmutableList
import com.design.commons.compose.extensions.enableEdgeToEdgeSimple
import com.design.commons.compose.screens.FAQScreen
import com.design.commons.compose.theme.AppThemeSurface
import com.design.commons.helpers.APP_FAQ
import com.design.commons.models.FAQItem

class FAQActivity : BaseComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeSimple()
        setContent {
            AppThemeSurface {
                val faqItems = remember { intent.getSerializableExtra(APP_FAQ) as ArrayList<FAQItem> }
                FAQScreen(
                    goBack = ::finish,
                    faqItems = faqItems.toImmutableList()
                )
            }
        }
    }
}
