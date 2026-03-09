package com.focusguard.app.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Utility functions for checking and managing the Accessibility Service state.
 */
object AccessibilityUtils {

    /**
     * Returns true if FocusGuard's accessibility service is currently enabled
     * in the system settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    /**
     * Opens the system Accessibility Settings screen so the user can enable
     * FocusGuard's accessibility service.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Opens battery optimization exemption settings for this app.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
