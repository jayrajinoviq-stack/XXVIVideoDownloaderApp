package com.design.commons.compose.extensions

import android.content.Context
import com.design.commons.helpers.BaseConfig

val Context.config: BaseConfig get() = BaseConfig.newInstance(applicationContext)
