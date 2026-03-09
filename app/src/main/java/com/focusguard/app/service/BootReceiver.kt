package com.focusguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.utils.PreferencesManager

/**
 * BootReceiver
 *
 * Restarts the FocusGuard foreground service automatically after device reboot,
 * but only if focus mode was enabled before the reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PreferencesManager.isFocusModeEnabled(context)) {
                FocusGuardForegroundService.start(context)
            }
        }
    }
}
