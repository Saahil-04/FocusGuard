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

        private const val ID_REELS_TAB   = "com.instagram.android:id/clips_tab"
        private const val ID_EXPLORE_TAB = "com.instagram.android:id/search_tab"
        private const val ID_DM_TAB      = "com.instagram.android:id/direct_tab"
        private const val ID_HOME_TAB    = "com.instagram.android:id/feed_tab"
        private const val ID_PROFILE_TAB = "com.instagram.android:id/profile_tab"

        private const val ID_REEL_VIDEO  = "com.instagram.android:id/clips_video_container"

        // DM inbox — if present, we are on DM list, never in a reel
        private const val ID_DM_INBOX    = "com.instagram.android:id/inbox_refreshable_thread_list_recyclerview"
        // DM chat thread — if present, we are in a chat, not fullscreen reel
        private const val ID_DM_THREAD   = "com.instagram.android:id/row_inbox_container"

        private const val SCROLL_GRACE_MS = 800L

        // FIX 1: Bumped from 1500 → 2500ms. Instagram's transition animations
        // run 600–800ms, then the a11y tree needs another cycle to settle.
        // 1500ms was cutting it too close and causing post-redirect false positives.
        private const val POST_REDIRECT_IGNORE_MS = 2500L

        // FIX 2: Throttle TYPE_WINDOW_CONTENT_CHANGED — fires on every minor DOM
        // change during transitions and reads stale rootInActiveWindow mid-animation.
        private const val CONTENT_CHANGE_THROTTLE_MS = 300L

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set
    }

    private var reelEntryTimeMs = 0L
    private var lastRedirectTimeMs = 0L
    private var lastContentChangeMs = 0L  // FIX 2: tracks last processed content change
    private var currentWindowPackage = ""

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
        if (!PreferencesManager.isFocusModeEnabled(this)) return
        if (isPostRedirectIgnoreActive()) return

        // FIX 2: Drop rapid-fire TYPE_WINDOW_CONTENT_CHANGED events.
        // These fire on every micro-change during transitions and are the
        // primary cause of stale-root false positives.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastContentChangeMs < CONTENT_CHANGE_THROTTLE_MS) return
            lastContentChangeMs = now
        }

        // Track foreground package from the event itself — more reliable than rootInActiveWindow
        val eventPackage = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentWindowPackage = eventPackage
        }

        // Only process Instagram events
        if (eventPackage != INSTAGRAM_PACKAGE) return

        val root = rootInActiveWindow ?: return

        // CRITICAL: verify root window is actually Instagram
        // rootInActiveWindow can return stale cached windows
        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) {
            Log.d(TAG, "rootInActiveWindow is stale (${root.packageName}) — skipping")
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(root)
            else -> handleWindowChange(root)
        }
    }

    private fun handleWindowChange(root: AccessibilityNodeInfo) {
        if (isTabSelected(root, ID_EXPLORE_TAB)) {
            Log.i(TAG, "🚫 Explore — blocking")
            redirect(root)
            return
        }

        if (isTabSelected(root, ID_REELS_TAB) && !isReelVideoOnScreen(root)) {
            Log.i(TAG, "🚫 Reels tab direct tap — blocking")
            redirect(root)
            return
        }

        val reelOnScreen = isReelVideoOnScreen(root)
        val reelTabSelected = isTabSelected(root, ID_REELS_TAB)

        when {
            // FIX 3: Reel is fullscreen AND reels tab is selected — block immediately.
            // Previously we only set reelEntryTimeMs and waited for a scroll event,
            // meaning a user who just watched without scrolling was never blocked.
            reelOnScreen && reelTabSelected -> {
                Log.i(TAG, "🚫 Reel fullscreen + reels tab confirmed — blocking immediately")
                redirect(root)
            }
            reelOnScreen && reelEntryTimeMs == 0L -> {
                reelEntryTimeMs = System.currentTimeMillis()
                Log.i(TAG, "▶ Reel entered via non-tab path — grace started")
            }
            !reelOnScreen && reelEntryTimeMs != 0L -> {
                Log.i(TAG, "✓ Left reel — reset")
                reelEntryTimeMs = 0L
            }
        }
    }

    private fun handleScroll(root: AccessibilityNodeInfo) {
        if (reelEntryTimeMs == 0L) return

        val elapsed = System.currentTimeMillis() - reelEntryTimeMs
        if (elapsed < SCROLL_GRACE_MS) {
            Log.d(TAG, "Scroll ignored — grace (${elapsed}ms)")
            return
        }

        if (!isReelVideoOnScreen(root)) {
            Log.d(TAG, "Scroll — reel gone, reset")
            reelEntryTimeMs = 0L
            return
        }

        Log.i(TAG, "📜 Scrolled past reel (${elapsed}ms) — blocking")
        redirect(root)
    }

    /**
     * A reel is on screen when:
     * 1. clips_video_container is present (the actual video player)
     * 2. DM inbox is NOT present (not on DM list)
     * 3. DM thread row is NOT present (not in a chat)
     * 4. Profile tab is NOT selected (not on profile)
     * 5. Home tab is NOT selected (not on home feed)
     *
     * Conditions 2-5 guard against the stale rootInActiveWindow
     * returning a cached reel window while we're actually elsewhere.
     */
    private fun isReelVideoOnScreen(root: AccessibilityNodeInfo): Boolean {
        if (!isNodePresent(root, ID_REEL_VIDEO)) return false

        if (isTabSelected(root, ID_HOME_TAB)) {
            Log.d(TAG, "clips_video_container but home tab selected — inline feed reel")
            return false
        }
        if (isTabSelected(root, ID_PROFILE_TAB)) {
            Log.d(TAG, "clips_video_container but profile tab selected — stale")
            return false
        }
        if (isTabSelected(root, ID_DM_TAB)) {
            Log.d(TAG, "clips_video_container but DM tab selected — background preload")
            return false
        }

        return true
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

    private fun redirect(root: AccessibilityNodeInfo) {
        reelEntryTimeMs = 0L
        lastRedirectTimeMs = System.currentTimeMillis()

        // FIX 4: Log node availability before attempting clicks so we can
        // diagnose why GLOBAL_ACTION_BACK fallback fires when tab clicks fail.
        val dmNodes = root.findAccessibilityNodeInfosByViewId(ID_DM_TAB)?.size ?: 0
        val homeNodes = root.findAccessibilityNodeInfosByViewId(ID_HOME_TAB)?.size ?: 0
        Log.d(TAG, "redirect() — root.pkg=${root.packageName} dm_tab_nodes=$dmNodes home_tab_nodes=$homeNodes")

        if (clickTab(root, ID_DM_TAB)) return
        if (clickTab(root, ID_HOME_TAB)) return
        Log.w(TAG, "↩ Last resort: GLOBAL_ACTION_BACK (dm=$dmNodes home=$homeNodes)")
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