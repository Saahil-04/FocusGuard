package com.focusguard.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.focusguard.app.config.BlockedAppConfig
import com.focusguard.app.config.BlockedAppsRegistry

/**
 * Centralized SharedPreferences manager.
 * Handles: focus mode toggle, blocked config persistence.
 */
object PreferencesManager {

    private const val PREF_FILE = "focusguard_prefs"
    private const val KEY_FOCUS_ENABLED = "focus_mode_enabled"
    private const val KEY_BLOCKED_CONFIG = "blocked_apps_config"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun isFocusModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOCUS_ENABLED, false)

    fun setFocusModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOCUS_ENABLED, enabled).apply()
    }

    fun getBlockedAppsConfig(context: Context): List<BlockedAppConfig> {
        val json = prefs(context).getString(KEY_BLOCKED_CONFIG, null)
        return if (json != null) {
            try {
                BlockedAppsRegistry.fromJson(json)
            } catch (e: Exception) {
                BlockedAppsRegistry.defaultConfig()
            }
        } else {
            BlockedAppsRegistry.defaultConfig()
        }
    }

    fun saveBlockedAppsConfig(context: Context, configs: List<BlockedAppConfig>) {
        val json = BlockedAppsRegistry.toJson(configs)
        prefs(context).edit().putString(KEY_BLOCKED_CONFIG, json).apply()
    }
}
