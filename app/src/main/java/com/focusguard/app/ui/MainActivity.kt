package com.focusguard.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.focusguard.app.R
import com.focusguard.app.databinding.ActivityMainBinding
import com.focusguard.app.service.FocusGuardForegroundService
import com.focusguard.app.utils.AccessibilityUtils
import com.focusguard.app.utils.PreferencesManager
import com.google.android.material.snackbar.Snackbar

/**
 * MainActivity — the primary user-facing screen.
 *
 * Provides:
 * - Accessibility service status indicator
 * - Focus mode enable/disable toggle
 * - Quick link to blocked sections configuration
 * - Shortcuts to required system settings
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        // Refresh accessibility status every time the user returns to this screen
        updateAccessibilityStatus()
        updateFocusToggleState()
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────

    private fun setupUI() {
        // Focus mode toggle
        binding.switchFocusMode.setOnCheckedChangeListener { _, isChecked ->
            onFocusModeToggled(isChecked)
        }

        // Enable Accessibility Service button
        binding.btnEnableAccessibility.setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
            Snackbar.make(
                binding.root,
                "Find 'FocusGuard' and enable it",
                Snackbar.LENGTH_LONG
            ).show()
        }

        // Manage blocked sections
        binding.btnManageSections.setOnClickListener {
            startActivity(Intent(this, BlockedSectionsActivity::class.java))
        }

        // Battery optimization
        binding.btnBatteryOptimization.setOnClickListener {
            AccessibilityUtils.openBatteryOptimizationSettings(this)
            Snackbar.make(
                binding.root,
                "Select 'Don't optimize' to prevent service interruption",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    // ─── State Updates ────────────────────────────────────────────────────────

    private fun updateAccessibilityStatus() {
        val isEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)

        binding.tvAccessibilityStatus.text = if (isEnabled) {
            "✓ Accessibility service is enabled"
        } else {
            "⚠ Accessibility service is disabled"
        }

        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (isEnabled) R.color.status_green else R.color.status_red)
        )

        // Show/hide the enable button based on status
        binding.btnEnableAccessibility.visibility = if (isEnabled) View.GONE else View.VISIBLE

        // Disable toggle if accessibility isn't granted
        binding.switchFocusMode.isEnabled = isEnabled
        if (!isEnabled) {
            binding.switchFocusMode.isChecked = false
            PreferencesManager.setFocusModeEnabled(this, false)
        }
    }

    private fun updateFocusToggleState() {
        val isEnabled = PreferencesManager.isFocusModeEnabled(this)
        // Temporarily remove listener to avoid triggering onCheckedChangeListener
        binding.switchFocusMode.setOnCheckedChangeListener(null)
        binding.switchFocusMode.isChecked = isEnabled
        binding.switchFocusMode.setOnCheckedChangeListener { _, checked ->
            onFocusModeToggled(checked)
        }

        updateStatusCard(isEnabled)
    }

    private fun updateStatusCard(isActive: Boolean) {
        binding.tvFocusStatus.text = if (isActive) {
            "Focus mode is ON\nInstagram Reels & Explore are blocked"
        } else {
            "Focus mode is OFF\nToggle the switch above to start blocking"
        }

        binding.cardStatus.setCardBackgroundColor(
            getColor(if (isActive) R.color.card_active else R.color.card_inactive)
        )
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun onFocusModeToggled(isEnabled: Boolean) {
        if (isEnabled && !AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            // Prevent enabling without accessibility permission
            binding.switchFocusMode.isChecked = false
            Snackbar.make(
                binding.root,
                "Please enable the Accessibility Service first",
                Snackbar.LENGTH_LONG
            ).setAction("Open Settings") {
                AccessibilityUtils.openAccessibilitySettings(this)
            }.show()
            return
        }

        PreferencesManager.setFocusModeEnabled(this, isEnabled)

        if (isEnabled) {
            FocusGuardForegroundService.start(this)
            Snackbar.make(binding.root, "Focus mode activated 🎯", Snackbar.LENGTH_SHORT).show()
        } else {
            FocusGuardForegroundService.stop(this)
            Snackbar.make(binding.root, "Focus mode deactivated", Snackbar.LENGTH_SHORT).show()
        }

        updateStatusCard(isEnabled)
    }
}
