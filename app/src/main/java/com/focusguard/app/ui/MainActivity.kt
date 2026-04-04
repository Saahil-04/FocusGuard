package com.focusguard.app.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.focusguard.app.R
import com.focusguard.app.databinding.ActivityMainBinding
import com.focusguard.app.service.FocusGuardForegroundService
import com.focusguard.app.utils.AccessibilityUtils
import com.focusguard.app.utils.PreferencesManager
import com.google.android.material.snackbar.Snackbar

/**
 * MainActivity — the primary user-facing screen.
 *
 * Design:
 *  - Edge-to-edge layout (WindowCompat.setDecorFitsSystemWindows = false)
 *  - Hero gradient card shows live focus status + inline switch
 *  - Accessibility status card updates on every resume
 *  - Theme toggle persisted to PreferencesManager
 *  - Card row tap targets for Manage Sections and Battery Optimization
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: let content draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyStoredTheme()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateFocusToggleState()
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

    private fun applyStoredTheme() {
        val isDark = PreferencesManager.isDarkMode(this)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ─── Click wiring ─────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Theme toggle
        binding.btnThemeToggle.setOnClickListener {
            val isDark = PreferencesManager.isDarkMode(this)
            PreferencesManager.setDarkMode(this, !isDark)
            AppCompatDelegate.setDefaultNightMode(
                if (!isDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Focus mode switch
        binding.switchFocusMode.setOnCheckedChangeListener { _, isChecked ->
            onFocusModeToggled(isChecked)
        }

        // Accessibility enable button (visible only when disabled)
        binding.btnEnableAccessibility.setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
            showSnackbar(getString(R.string.a11y_guide))
        }

        // Manage sections — full card is tappable
        binding.cardManageSections.setOnClickListener {
            startActivity(Intent(this, BlockedSectionsActivity::class.java))
        }

        // Battery optimization — full card is tappable
        binding.cardBattery.setOnClickListener {
            AccessibilityUtils.openBatteryOptimizationSettings(this)
            showSnackbar(getString(R.string.battery_guide))
        }
    }

    // ─── State updates ────────────────────────────────────────────────────────

    private fun updateAccessibilityStatus() {
        val isEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)

        binding.tvAccessibilityStatus.text = if (isEnabled) {
            getString(R.string.a11y_enabled)
        } else {
            getString(R.string.a11y_disabled)
        }

        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (isEnabled) R.color.md_primary else R.color.md_error)
        )

        binding.btnEnableAccessibility.visibility =
            if (isEnabled) View.GONE else View.VISIBLE

        // Disable toggle if accessibility not granted
        binding.switchFocusMode.isEnabled = isEnabled
        if (!isEnabled) {
            binding.switchFocusMode.setOnCheckedChangeListener(null)
            binding.switchFocusMode.isChecked = false
            PreferencesManager.setFocusModeEnabled(this, false)
            binding.switchFocusMode.setOnCheckedChangeListener { _, checked ->
                onFocusModeToggled(checked)
            }
        }
    }

    private fun updateFocusToggleState() {
        val isEnabled = PreferencesManager.isFocusModeEnabled(this)

        // Update switch without triggering listener
        binding.switchFocusMode.setOnCheckedChangeListener(null)
        binding.switchFocusMode.isChecked = isEnabled
        binding.switchFocusMode.setOnCheckedChangeListener { _, checked ->
            onFocusModeToggled(checked)
        }

        applyHeroState(isEnabled)
    }

    /**
     * Applies the full visual state to the hero card:
     * - Title and subtitle text
     * - Status dot drawable (filled = active, outline = inactive)
     * - Subtle scale pulse animation when activating
     */
    private fun applyHeroState(isActive: Boolean) {
        if (isActive) {
            binding.tvHeroTitle.text = getString(R.string.status_active)
            binding.tvFocusStatus.text = getString(R.string.status_active_sub)
            binding.tvHeroStatusLabel.text = getString(R.string.status_active)
            binding.ivStatusDot.setImageResource(R.drawable.ic_dot_active)
            animateStatusDot(binding.ivStatusDot)
        } else {
            binding.tvHeroTitle.text = getString(R.string.status_inactive)
            binding.tvFocusStatus.text = getString(R.string.status_inactive_sub)
            binding.tvHeroStatusLabel.text = getString(R.string.status_inactive)
            binding.ivStatusDot.setImageResource(R.drawable.ic_dot_inactive)
        }
    }

    /**
     * Pulses the status dot with a scale animation when focus mode activates.
     * Gives satisfying tactile feedback without being distracting.
     */
    private fun animateStatusDot(view: View) {
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.6f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.6f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun onFocusModeToggled(isEnabled: Boolean) {
        if (isEnabled && !AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            // Revert switch without firing listener again
            binding.switchFocusMode.setOnCheckedChangeListener(null)
            binding.switchFocusMode.isChecked = false
            binding.switchFocusMode.setOnCheckedChangeListener { _, checked ->
                onFocusModeToggled(checked)
            }

            showSnackbar(
                message = getString(R.string.focus_no_a11y),
                actionLabel = getString(R.string.action_open_settings)
            ) {
                AccessibilityUtils.openAccessibilitySettings(this)
            }
            return
        }

        PreferencesManager.setFocusModeEnabled(this, isEnabled)

        if (isEnabled) {
            FocusGuardForegroundService.start(this)
            showSnackbar(getString(R.string.focus_on_toast))
        } else {
            FocusGuardForegroundService.stop(this)
            showSnackbar(getString(R.string.focus_off_toast))
        }

        applyHeroState(isEnabled)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null
    ) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        if (actionLabel != null && action != null) {
            snack.setAction(actionLabel) { action() }
        }
        snack.show()
    }
}
