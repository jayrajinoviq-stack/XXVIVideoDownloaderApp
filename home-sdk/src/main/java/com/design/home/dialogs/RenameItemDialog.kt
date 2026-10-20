package com.design.home.dialogs

import android.app.Activity
import android.app.AlertDialog
import com.design.home.databinding.DialogRenameItemBinding
import com.design.commons.extensions.*
import com.design.commons.helpers.ensureBackgroundThread
import com.design.home.extensions.homeScreenGridItemsDB
import com.design.home.models.HomeScreenGridItem

class RenameItemDialog(val activity: Activity, val item: HomeScreenGridItem, val callback: () -> Unit) {

    init {
        val binding = DialogRenameItemBinding.inflate(activity.layoutInflater)
        val view = binding.root
        binding.renameItemEdittext.setText(item.title)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.design.commons.R.string.ok, null)
            .setNegativeButton(com.design.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, com.design.commons.R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameItemEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameItemEdittext.value
                        if (newTitle.isNotEmpty()) {
                            ensureBackgroundThread {
                                val result = activity.homeScreenGridItemsDB.updateItemTitle(newTitle, item.id!!)
                                if (result == 1) {
                                    callback()
                                    alertDialog.dismiss()
                                } else {
                                    activity.toast(com.design.commons.R.string.unknown_error_occurred)
                                }
                            }
                        } else {
                            activity.toast(com.design.commons.R.string.value_cannot_be_empty)
                        }
                    }
                }
            }
    }
}
