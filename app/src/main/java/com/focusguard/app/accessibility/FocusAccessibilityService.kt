package com.focusguard.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.utils.PreferencesManager

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusGuardA11y"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val REDIRECT_COOLDOWN_MS = 2000L

        // Exact resource IDs from Instagram's APK
        private const val ID_REELS_TAB    = "com.instagram.android:id/clips_tab"
        private const val ID_EXPLORE_TAB  = "com.instagram.android:id/search_tab"

        // The DM inbox tab — redirect destination
        private const val ID_DM_TAB       = "com.instagram.android:id/direct_inbox_tab"

        // Fallback: home tab
        private const val ID_HOME_TAB     = "com.instagram.android:id/feed_tab"

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set
    }

    private var lastRedirectTimeMs = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "FocusGuard Accessibility Service connected ✓")
    }

    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onInterrupt() { Log.w(TAG, "Service interrupted") }

    // ── Core Event Handler ────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val pkg = event.packageName?.toString() ?: return
        if (pkg != INSTAGRAM_PACKAGE) return
        if (!PreferencesManager.isFocusModeEnabled(this)) return

        val root = rootInActiveWindow ?: return
        try {
            checkAndBlock(root)
        } finally {
            root.recycle()
        }
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    private fun checkAndBlock(root: AccessibilityNodeInfo) {
        // Check if a blocked tab is currently selected/focused
        if (isTabSelected(root, ID_REELS_TAB)) {
            Log.i(TAG, "🚫 Reels tab detected — redirecting")
            redirect(root)
            return
        }
        if (isTabSelected(root, ID_EXPLORE_TAB)) {
            Log.i(TAG, "🚫 Explore tab detected — redirecting")
            redirect(root)
            return
        }
    }

    /**
     * Returns true if the node with the given viewId exists AND is selected/focused.
     * We check isSelected so we only trigger when the user actually tapped it,
     * not just when it's visible in the nav bar.
     */
    private fun isTabSelected(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return false
        val result = nodes.any { node ->
            val selected = node.isSelected || node.isChecked || node.isFocused
            node.recycle()
            selected
        }
        return result
    }

    // ── Redirect ──────────────────────────────────────────────────────────────

    private fun redirect(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastRedirectTimeMs < REDIRECT_COOLDOWN_MS) return
        lastRedirectTimeMs = now

        // Try clicking DM tab first
        if (clickTab(root, ID_DM_TAB)) return
        // Try home tab
        if (clickTab(root, ID_HOME_TAB)) return
        // Final fallback
        Log.i(TAG, "No tab found — using BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun clickTab(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return false
        val node = nodes.first()
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        if (clicked) Log.i(TAG, "✓ Clicked tab: $viewId")
        return clicked
    }
}
