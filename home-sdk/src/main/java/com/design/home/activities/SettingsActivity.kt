package com.design.home.activities

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.design.commons.dialogs.RadioGroupDialog
import com.design.commons.extensions.beVisibleIf
import com.design.commons.extensions.getProperPrimaryColor
import com.design.commons.extensions.updateTextColors
import com.design.commons.extensions.viewBinding
import com.design.commons.helpers.NavigationIcon
import com.design.commons.models.RadioItem
import com.design.home.R
import com.design.home.databinding.ActivityLauncherSettingsBinding
import com.design.home.extensions.config
import com.design.home.helpers.COLUMN_COUNT
import com.design.home.helpers.MAX_COLUMN_COUNT
import com.design.home.helpers.LeftPanelRegistry
import com.design.home.helpers.ROW_COUNT
import com.design.home.receivers.LockDeviceAdminReceiver

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityLauncherSettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.settingsAdContainer))
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)

        LeftPanelRegistry.provider?.loadSettingsNativeAd(this, binding.settingsAdContainer)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        // Ensure fixed home grid size: 6 rows, 5 columns
        config.homeRowCount = ROW_COUNT
        config.homeColumnCount = COLUMN_COUNT

        setupDoubleTapToLock()
        setupCloseAppDrawerOnOtherAppOpen()
        setupOpenKeyboardOnAppDrawer()
        setupDrawerColumnCount()
        setupDrawerSearchBar()
        setupShowDrawerAppLabels()
        setupShowHomeAppLabels()
        updateTextColors(binding.settingsHolder)

        arrayOf(
            binding.settingsGeneralSettingsLabel,
            binding.settingsDrawerSettingsLabel,
            binding.settingsHomeScreenLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupDoubleTapToLock() {
        val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        binding.settingsDoubleTapToLock.isChecked = devicePolicyManager.isAdminActive(
            ComponentName(this, LockDeviceAdminReceiver::class.java)
        )

        binding.settingsDoubleTapToLockHolder.setOnClickListener {
            val isLockDeviceAdminActive = devicePolicyManager.isAdminActive(
                ComponentName(this, LockDeviceAdminReceiver::class.java)
            )
            if (isLockDeviceAdminActive) {
                devicePolicyManager.removeActiveAdmin(
                    ComponentName(this, LockDeviceAdminReceiver::class.java)
                )
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, LockDeviceAdminReceiver::class.java)
                )
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.lock_device_admin_hint)
                )
                startActivity(intent)
            }
        }
    }

    private fun setupOpenKeyboardOnAppDrawer() {
        binding.settingsOpenKeyboardOnAppDrawerHolder.beVisibleIf(config.showSearchBar)
        binding.settingsOpenKeyboardOnAppDrawer.isChecked = config.autoShowKeyboardInAppDrawer
        binding.settingsOpenKeyboardOnAppDrawerHolder.setOnClickListener {
            binding.settingsOpenKeyboardOnAppDrawer.toggle()
            config.autoShowKeyboardInAppDrawer = binding.settingsOpenKeyboardOnAppDrawer.isChecked
        }
    }

    private fun setupCloseAppDrawerOnOtherAppOpen() {
        binding.settingsCloseAppDrawerOnOtherApp.isChecked = config.closeAppDrawer
        binding.settingsCloseAppDrawerOnOtherAppHolder.setOnClickListener {
            binding.settingsCloseAppDrawerOnOtherApp.toggle()
            config.closeAppDrawer = binding.settingsCloseAppDrawerOnOtherApp.isChecked
        }
    }

    private fun setupDrawerColumnCount() {
        val currentColumnCount = config.drawerColumnCount
        binding.settingsDrawerColumnCount.text = currentColumnCount.toString()
        binding.settingsDrawerColumnCountHolder.setOnClickListener {
            val items = ArrayList<RadioItem>()
            for (i in 1..MAX_COLUMN_COUNT) {
                items.add(
                    RadioItem(
                        id = i,
                        title = resources.getQuantityString(
                            com.design.commons.R.plurals.column_counts, i, i
                        )
                    )
                )
            }

            RadioGroupDialog(this, items, currentColumnCount) {
                val newColumnCount = it as Int
                if (currentColumnCount != newColumnCount) {
                    config.drawerColumnCount = newColumnCount
                    setupDrawerColumnCount()
                }
            }
        }
    }

    private fun setupDrawerSearchBar() {
        val showSearchBar = config.showSearchBar
        binding.settingsShowSearchBar.isChecked = showSearchBar
        binding.settingsDrawerSearchHolder.setOnClickListener {
            binding.settingsShowSearchBar.toggle()
            config.showSearchBar = binding.settingsShowSearchBar.isChecked
            binding.settingsOpenKeyboardOnAppDrawerHolder.beVisibleIf(config.showSearchBar)
        }
    }

    private fun setupShowDrawerAppLabels() {
        binding.settingsShowDrawerAppLabels.isChecked = config.showDrawerAppLabels
        binding.settingsShowDrawerAppLabelsHolder.setOnClickListener {
            binding.settingsShowDrawerAppLabels.toggle()
            config.showDrawerAppLabels = binding.settingsShowDrawerAppLabels.isChecked
        }
    }

    private fun setupShowHomeAppLabels() {
        binding.settingsShowHomeAppLabels.isChecked = config.showHomeAppLabels
        binding.settingsShowHomeAppLabelsHolder.setOnClickListener {
            binding.settingsShowHomeAppLabels.toggle()
            config.showHomeAppLabels = binding.settingsShowHomeAppLabels.isChecked
        }
    }
}
