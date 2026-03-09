# FocusGuard — DM-Only Instagram for Android

**Transform Instagram into a messaging tool.** FocusGuard uses Android's Accessibility Service to detect when you've opened Reels or Explore and immediately redirects you back to Direct Messages.

---

## Project Structure

```
FocusGuard/
├── build.gradle                        # Root build config
├── settings.gradle
└── app/
    ├── build.gradle                    # App dependencies & SDK config
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/focusguard/app/
        │   ├── accessibility/
        │   │   └── FocusAccessibilityService.kt   ← Core logic
        │   ├── config/
        │   │   └── BlockedAppsRegistry.kt         ← Blocking rules + data models
        │   ├── service/
        │   │   ├── FocusGuardForegroundService.kt ← Keeps app alive in background
        │   │   └── BootReceiver.kt                ← Auto-restart on device boot
        │   ├── ui/
        │   │   ├── MainActivity.kt                ← Main toggle screen
        │   │   └── BlockedSectionsActivity.kt     ← Manage blocked rules
        │   └── utils/
        │       ├── AccessibilityUtils.kt
        │       └── PreferencesManager.kt
        └── res/
            ├── layout/          ← UI XML layouts
            ├── values/          ← Colors, strings, themes
            └── xml/
                └── accessibility_service_config.xml
```

---

## Setup Instructions

### 1. Open in Android Studio

1. Open **Android Studio** (Hedgehog or later recommended)
2. Select **Open** → navigate to the `FocusGuard/` folder
3. Wait for Gradle sync to complete
4. Connect a physical Android device via USB (API 26+, i.e. Android 8.0+)

> ⚠️ **Physical device required.** The Accessibility Service cannot be properly tested on an emulator.

---

### 2. Build & Install

In Android Studio:
- Click **Run ▶** or press `Shift+F10`
- Select your connected device

Or via command line:
```bash
./gradlew installDebug
```

---

### 3. Grant Required Permissions

After installing, open **FocusGuard** and follow these steps in order:

#### Step A — Enable Accessibility Service (Required)
1. Tap **"Enable in Settings"** button in the app
2. In the system screen that opens, find **"FocusGuard"** under Downloaded Apps
3. Tap it → tap **"Use FocusGuard"** → confirm
4. Return to FocusGuard — the status indicator should now show ✓ green

#### Step B — Disable Battery Optimization (Strongly Recommended)
1. Tap **"Disable Battery Optimization"** in the app
2. In the dialog, select **"Don't optimize"**
3. This prevents Android from killing the service on some OEMs (Xiaomi, Huawei, OnePlus)

#### Step C — Enable Focus Mode
1. Toggle the **"Focus Mode"** switch ON
2. The status card should turn green

---

### 4. Test the Blocking

1. Open **Instagram**
2. Tap the **Reels** tab (the clapperboard icon)
3. FocusGuard should detect this within ~1 second and navigate you back

**What to look for:**
- A `GLOBAL_ACTION_BACK` (or DM icon click) triggered automatically
- Logcat tag `FocusGuardA11y` shows detection events

**Logcat filter during testing:**
```
adb logcat -s FocusGuardA11y
```

---

## How It Works

### Detection Flow

```
Instagram opens
      ↓
AccessibilityEvent fired (TYPE_WINDOW_STATE_CHANGED or TYPE_WINDOW_CONTENT_CHANGED)
      ↓
FocusAccessibilityService.onAccessibilityEvent()
      ↓
Check: Is focus mode enabled? Is this a monitored package?
      ↓
Walk the view tree (BFS, max depth 8) collecting visible text
      ↓
Match against blocked section keywords ("reels", "explore", ...)
      ↓
Blocked section found? → triggerRedirect()
      ↓
Try clicking DM icon → fallback to GLOBAL_ACTION_BACK
```

### Battery Efficiency

- **No polling loops** — everything is event-driven via `onAccessibilityEvent`
- **Early exits**: if focus mode is off, or wrong package, logic returns immediately
- **BFS depth limit** (max 8 levels) caps view-tree traversal cost
- **Redirect cooldown** (2 seconds) prevents rapid re-triggers
- **Low-importance notification** for the foreground service — no sound/vibration

---

## Customizing Blocked Sections

Edit `BlockedAppsRegistry.kt` to add new apps or keywords:

```kotlin
BlockedAppConfig(
    packageName = "com.twitter.android",
    appLabel = "Twitter / X",
    sections = mutableListOf(
        BlockedSection(
            label = "For You Feed",
            keywords = listOf("for you", "trending"),
            isEnabled = true
        )
    )
)
```

Users can also toggle individual sections from within the app via **"Manage Blocked Sections"**.

---

## Reliability Notes by Device

| OEM | Known Issue | Fix |
|-----|-------------|-----|
| Xiaomi / MIUI | Aggressively kills background services | Enable "Autostart" for FocusGuard in MIUI settings |
| Huawei / EMUI | Battery manager kills accessibility services | Add FocusGuard to "Protected Apps" |
| Samsung One UI | May pause accessibility service on sleep | Enable "Remove permissions if app unused" = OFF |
| Stock Android | Works well out of the box | Disable battery optimization only |

---

## Improving Reliability (Advanced)

### Use View IDs Instead of Text (More Robust)

Instagram periodically changes UI text. For more robust detection, use resource IDs:

```kotlin
// In FocusAccessibilityService, find nodes by resource ID
val reelsNode = root.findAccessibilityNodeInfosByViewId(
    "com.instagram.android:id/clips_tab"
)
```

To discover resource IDs: use **uiautomatorviewer** from Android SDK tools:
```bash
$ANDROID_HOME/tools/bin/uiautomatorviewer
```

### Monitor Specific Screen Names

Instagram emits window state changes with class names. You can detect them:

```kotlin
val className = event.className?.toString() ?: ""
if (className.contains("Reels", ignoreCase = true)) { ... }
```

### Add Gentle User Feedback

Instead of silently redirecting, show a toast:

```kotlin
Toast.makeText(this, "Stay focused! Redirecting to DMs 🎯", Toast.LENGTH_SHORT).show()
```

---

## Permissions Summary

| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Core — required to monitor Instagram UI |
| `FOREGROUND_SERVICE` | Keep monitoring service alive |
| `RECEIVE_BOOT_COMPLETED` | Auto-restart after reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OS from killing service |

---

## Future Expansion

The architecture is designed for multi-app support. To add YouTube Shorts blocking:
1. Add a `BlockedAppConfig` entry for `com.google.android.youtube` in `BlockedAppsRegistry.kt`
2. The accessibility service XML only targets Instagram by default — update `packageNames` in `accessibility_service_config.xml`, or remove the filter entirely to monitor all apps.

---

## Troubleshooting

**Blocking stopped working after Instagram update**
→ Instagram may have changed its UI text. Update keywords in `BlockedAppsRegistry.kt` and check new IDs with uiautomatorviewer.

**Service keeps getting killed**
→ Disable battery optimization (Step B above). On Xiaomi/Huawei, also enable Autostart in system settings.

**Accessibility Service disappears from settings after reboot**
→ This is a known Android issue on some OEMs. The BootReceiver will re-attempt to start the foreground service, but the user may need to re-enable the accessibility service manually once after each reboot on affected devices.
