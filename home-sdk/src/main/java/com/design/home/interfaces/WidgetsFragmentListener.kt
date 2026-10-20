package com.design.home.interfaces

import com.design.home.models.AppWidget

interface WidgetsFragmentListener {
    fun onWidgetLongPressed(appWidget: AppWidget)
    fun onWidgetAddClicked(appWidget: AppWidget)
}
