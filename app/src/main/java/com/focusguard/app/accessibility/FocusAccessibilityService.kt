package com.focusguard.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.config.BlockedAppConfig
import com.focusguard.app.config.BlockedSection
import com.focusguard.app.utils.PreferencesManager

/**
 * FocusAccessibilityService
 *
 * Monitors Instagram (and optionally other apps) for distraction-heavy sections.
 * When a blocked section is detected, it performs a back action or clicks the
 * Direct Messages icon to redirect the user.
 *
 * Architecture:
 * - Event-driven only (no polling loops) for battery efficiency.
 * - Only runs heavy logic when the target package is in the foreground.
 * - Config is loaded fresh on each relevant event to reflect live preference changes.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusGuardA11y"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        // Cooldown to avoid redirect loops: don't redirect more than once per 2 seconds
        private const val REDIRECT_COOLDOWN_MS = 2000L

        // Singleton reference so other components can check if service is running
        @Volatile
        var instance: FocusAccessibilityService? = null
            private set
    }

    private var lastRedirectTimeMs = 0L
    private var lastDetectedPackage: String? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "FocusGuard Accessibility Service connected")

        // Dynamically configure the service (belt-and-suspenders alongside XML config)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "FocusGuard Accessibility Service destroyed")
    }

    override fun onInterrupt() {
        Log.w(TAG, "FocusGuard Accessibility Service interrupted")
    }

    // ─── Core Event Handler ───────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Skip irrelevant event types early
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // Only act on configured packages
        val configs = PreferencesManager.getBlockedAppsConfig(this)
        val appConfig = configs.find { it.packageName == packageName } ?: return

        // Focus mode must be enabled
        if (!PreferencesManager.isFocusModeEnabled(this)) return

        // Track package transitions for logging
        if (packageName != lastDetectedPackage) {
            Log.d(TAG, "Now monitoring: $packageName")
            lastDetectedPackage = packageName
        }

        // Inspect the current window's node tree
        val rootNode = rootInActiveWindow ?: return
        try {
            evaluateWindowForBlocking(rootNode, appConfig)
        } finally {
            rootNode.recycle()
        }
    }

    // ─── Detection & Redirect Logic ───────────────────────────────────────────

    /**
     * Walk the accessibility node tree and check if any enabled blocked section
     * is currently visible. If so, trigger a redirect.
     */
    private fun evaluateWindowForBlocking(
        root: AccessibilityNodeInfo,
        appConfig: BlockedAppConfig
    ) {
        val enabledSections = appConfig.sections.filter { it.isEnabled }
        if (enabledSections.isEmpty()) return

        // Collect all visible text from the node tree (limited depth for performance)
        val visibleTexts = collectVisibleTexts(root, maxDepth = 8)

        for (section in enabledSections) {
            if (isBlockedSectionVisible(visibleTexts, section)) {
                Log.i(TAG, "Blocked section detected: ${section.label} in ${appConfig.packageName}")
                triggerRedirect(appConfig.packageName)
                return // One redirect per event cycle is enough
            }
        }
    }

    /**
     * Check if any of the section's keywords appear in visible text.
     * Uses case-insensitive substring match.
     */
    private fun isBlockedSectionVisible(
        visibleTexts: List<String>,
        section: BlockedSection
    ): Boolean {
        return visibleTexts.any { text ->
            section.keywords.any { keyword ->
                text.contains(keyword, ignoreCase = true)
            }
        }
    }

    /**
     * BFS traversal of the node tree collecting non-empty text strings.
     * Depth-limited to avoid excessive traversal on complex UIs.
     */
    private fun collectVisibleTexts(root: AccessibilityNodeInfo, maxDepth: Int): List<String> {
        val texts = mutableListOf<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(root, 0))

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            if (depth > maxDepth) continue

            // Collect text and content description
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }

            // Enqueue children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    queue.add(Pair(child, depth + 1))
                }
            }
        }

        return texts
    }

    // ─── Redirect Actions ─────────────────────────────────────────────────────

    /**
     * Performs the redirect action with cooldown to prevent loops.
     * For Instagram: try to navigate to DMs first, fall back to global back.
     */
    private fun triggerRedirect(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastRedirectTimeMs < REDIRECT_COOLDOWN_MS) {
            Log.d(TAG, "Redirect skipped (cooldown active)")
            return
        }
        lastRedirectTimeMs = now

        when (packageName) {
            INSTAGRAM_PACKAGE -> redirectInstagramToDMs()
            else -> performGlobalBack()
        }
    }

    /**
     * Attempts to click the Instagram Direct Messages button in the nav bar.
     * Falls back to global back if the DM button cannot be found.
     */
    private fun redirectInstagramToDMs() {
        val root = rootInActiveWindow ?: run {
            performGlobalBack()
            return
        }

        try {
            // Instagram's DM icon typically has content description containing "Direct" or "Messages"
            val dmKeywords = listOf("direct", "messages", "dm", "messenger", "chats", "inbox")
            val dmNode = findNodeByContentDescription(root, dmKeywords)

            if (dmNode != null) {
                Log.i(TAG, "Clicking DM button to redirect")
                dmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                dmNode.recycle()
            } else {
                Log.i(TAG, "DM button not found, performing global back")
                performGlobalBack()
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * Searches the node tree for a clickable node whose content description
     * matches any of the given keywords.
     */
    private fun findNodeByContentDescription(
        root: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val combined = "$desc $text".lowercase()

            if (keywords.any { combined.contains(it) } && node.isClickable) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Performs the global BACK action — the safe fallback redirect.
     */
    private fun performGlobalBack() {
        Log.i(TAG, "Performing GLOBAL_ACTION_BACK")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
