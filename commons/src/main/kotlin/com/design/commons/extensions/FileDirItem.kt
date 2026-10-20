package com.design.commons.extensions

import android.content.Context
import com.design.commons.models.FileDirItem

fun FileDirItem.isRecycleBinPath(context: Context): Boolean {
    return path.startsWith(context.recycleBinPath)
}
