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

        private const val ID_REELS_TAB    = "com.instagram.android:id/clips_tab"
        private const val ID_EXPLORE_TAB  = "com.instagram.android:id/search_tab"
        private const val ID_DM_TAB       = "com.instagram.android:id/direct_tab"
        private const val ID_HOME_TAB     = "com.instagram.android:id/feed_tab"

        // clips_media_component: only present when a Reel is actually
        // rendered and visible. Its content-desc = "Reel by <username>."
        // This is absent during pre-loading, DM list, DM chat, home feed.
        private const val ID_REEL_MEDIA   = "com.instagram.android:id/clips_media_component"

        // clips_ufi_component: like/comment/share buttons — also only
        // present when a Reel is fully loaded and on screen
        private const val ID_REEL_UFI     = "com.instagram.android:id/clips_ufi_component"

        // Grace period after reel appears — covers opening animation scrolls
        private const val SCROLL_GRACE_MS = 800L

        // After redirect, ignore all events for this long — stops re-entry loops
        private const val POST_REDIRECT_IGNORE_MS = 1500L

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set
    }

    // When the reel media component first appeared. 0L = not in reel.
    private var reelEntryTimeMs = 0L
    private var lastRedirectTimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "FocusGuard connected ✓")
    }

    override fun onDestroy() { super.onDestroy(); instance = null }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (!PreferencesManager.isFocusModeEnabled(this)) return

        // Hard silence after every redirect — prevents re-entry loops
        if (isPostRedirectIgnoreActive()) return

        val root = rootInActiveWindow ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(root)
            else -> handleWindowChange(root)
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleWindowChange(root: AccessibilityNodeInfo) {
        // Explore tab — always block instantly
        if (isTabSelected(root, ID_EXPLORE_TAB)) {
            Log.i(TAG, "🚫 Explore — blocking")
            redirect(root)
            return
        }

        // Reels tab selected but no actual reel media visible = direct tab tap
        // (player not loaded yet, or pre-loading in background)
        if (isTabSelected(root, ID_REELS_TAB) && !isReelActuallyVisible(root)) {
            Log.i(TAG, "🚫 Reels tab — no media visible, blocking")
            redirect(root)
            return
        }

        val reelVisible = isReelActuallyVisible(root)

        when {
            reelVisible && reelEntryTimeMs == 0L -> {
                reelEntryTimeMs = System.currentTimeMillis()
                Log.i(TAG, "▶ Reel on screen — grace period started")
            }
            !reelVisible && reelEntryTimeMs != 0L -> {
                Log.i(TAG, "✓ Reel gone — reset")
                reelEntryTimeMs = 0L
            }
        }
    }

    private fun handleScroll(root: AccessibilityNodeInfo) {
        if (reelEntryTimeMs == 0L) return

        val elapsed = System.currentTimeMillis() - reelEntryTimeMs

        if (elapsed < SCROLL_GRACE_MS) {
            Log.d(TAG, "Scroll ignored — grace period (${elapsed}ms)")
            return
        }

        // Final confirmation: reel media must still be on screen
        // This eliminates any home feed or DM list scroll false positives
        if (!isReelActuallyVisible(root)) {
            Log.d(TAG, "Scroll ignored — no reel on screen, reset")
            reelEntryTimeMs = 0L
            return
        }

        Log.i(TAG, "📜 Scrolled past reel (${elapsed}ms) — blocking")
        redirect(root)
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Returns true only when a Reel is actually rendered and visible on screen.
     * Requires BOTH clips_media_component AND clips_ufi_component to be present.
     * This combination is absent during: pre-loading, DM list, DM chat, home feed,
     * profile page, and all other non-Reel screens.
     */
    private fun isReelActuallyVisible(root: AccessibilityNodeInfo): Boolean {
        val mediaPresent = isNodePresent(root, ID_REEL_MEDIA)
        val ufiPresent = isNodePresent(root, ID_REEL_UFI)
        return mediaPresent && ufiPresent
    }

    private fun isTabSelected(root: AccessibilityNodeInfo, tabId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(tabId) ?: return false
        for (node in nodes) {
            if (node.isSelected || node.isChecked) return true
            for (i in 0 until node.childCount) {
                if (node.getChild(i)?.isSelected == true) return true
            }
        }
        return false
    }

    private fun isNodePresent(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return !root.findAccessibilityNodeInfosByViewId(viewId).isNullOrEmpty()
    }

    // ── Redirect ──────────────────────────────────────────────────────────────

    private fun redirect(root: AccessibilityNodeInfo) {
        reelEntryTimeMs = 0L
        lastRedirectTimeMs = System.currentTimeMillis()

        if (clickTab(root, ID_DM_TAB)) return
        if (clickTab(root, ID_HOME_TAB)) return
        Log.w(TAG, "↩ Last resort: GLOBAL_ACTION_BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun clickTab(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        val clicked = nodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (clicked) Log.i(TAG, "✓ Redirected to: $viewId")
        return clicked
    }

    private fun isPostRedirectIgnoreActive(): Boolean {
        if (lastRedirectTimeMs == 0L) return false
        val elapsed = System.currentTimeMillis() - lastRedirectTimeMs
        if (elapsed < POST_REDIRECT_IGNORE_MS) {
            Log.d(TAG, "Post-redirect silence (${elapsed}ms)")
            return true
        }
        return false
    }
}
