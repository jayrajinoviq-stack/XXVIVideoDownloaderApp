package com.design.commons.interfaces

import androidx.recyclerview.widget.RecyclerView

interface StartReorderDragListener {
    fun requestDrag(viewHolder: RecyclerView.ViewHolder)
}
