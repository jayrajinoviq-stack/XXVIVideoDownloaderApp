package com.design.commons.dialogs

import android.app.Activity
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.design.commons.R
import com.design.commons.compose.alert_dialog.*
import com.design.commons.compose.components.LinkifyTextComponent
import com.design.commons.compose.extensions.MyDevices
import com.design.commons.compose.extensions.getActivity
import com.design.commons.compose.extensions.rememberMutableInteractionSource
import com.design.commons.compose.theme.AppThemeSurface
import com.design.commons.compose.theme.SimpleTheme
import com.design.commons.databinding.DialogDonateBinding
import com.design.commons.extensions.*

class DonateDialog(val activity: Activity) {
    init {
        // Permanently disabled
    }
}

@Composable
fun DonateAlertDialog(
    alertDialogState: AlertDialogState,
    modifier: Modifier = Modifier
) {
    val localContext = LocalContext.current.getActivity()
    val donateIntent = {
        localContext.launchViewIntent(R.string.thank_you_url)
    }
    androidx.compose.material3.AlertDialog(
        containerColor = dialogContainerColor,
        modifier = modifier
            .dialogBorder(),
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        onDismissRequest = {},
        shape = dialogShape,
        tonalElevation = dialogElevation,
        dismissButton = {
            TextButton(onClick = {
                alertDialogState.hide()
            }) {
                Text(text = stringResource(id = R.string.later))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                donateIntent()
                alertDialogState.hide()
            }) {
                Text(text = stringResource(id = R.string.purchase))
            }
        },
        title = {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SimpleTheme.dimens.icon.large)
                        .clickable(
                            indication = null,
                            interactionSource = rememberMutableInteractionSource(),
                            onClick = {
                                donateIntent()
                                alertDialogState.hide()
                            }
                        ),
                    colorFilter = ColorFilter.tint(dialogTextColor)
                )
            }
        },
        text = {
            val source = stringResource(id = R.string.donate_short)
            LinkifyTextComponent(
                fontSize = 16.sp,
                removeUnderlines = false,
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER,
                modifier = Modifier.fillMaxWidth()
            ) {
                source.fromHtml()
            }
        }
    )
}

@Composable
@MyDevices
private fun DonateAlertDialogPreview() {
    AppThemeSurface {
        DonateAlertDialog(alertDialogState = rememberAlertDialogState())
    }
}


