# Notification Settings and Media Projection Permission — Exact Mechanism

This document describes how notification permissions and media projection (screen capture) permission are requested, checked, and used across the codebase.

---

## 1. Overview

| Concern | What it is | Where requested | Gated by |
|--------|-------------|------------------|----------|
| **POST_NOTIFICATIONS** | Runtime permission to show notifications (Android 13+) | Permission flow + CapturePermissionActivity | `ModulePermissions` (always requested on TIRAMISU+ in flow) |
| **Notification Listener** | System setting: app can “read” notifications | Settings → Notification access | `ModulePermissions.needNotificationListener()` (= `ENABLE_APPS`) |
| **Media Projection** | System permission to capture screen (result from `createScreenCaptureIntent()`) | CaptureUploadStarter or AppRunningActivity | `ModulePermissions.needMediaProjection()` (= `ENABLE_SCREENSHOTS`) |

---

## 2. Notification Settings

### 2.1 POST_NOTIFICATIONS (show notifications)

- **Manifest:** `app/AndroidManifest.xml` declares `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- **When requested:**
  - **Permission flow (PermissionParentActivity):** After loading SettingsManager, if `Build.VERSION.SDK_INT >= TIRAMISU` and permission not granted, a **PreviewFrag** is added with header `R.string.notification_permission_header` (“Push Notification Permission”). User taps “Allow” → **PreviewFrag** calls `AskForPermission(Manifest.permission.POST_NOTIFICATIONS)` (Dexter). On grant, next page; on deny, dialog to open app settings.
  - **PermissionChecker** (used from LoginActivity, AppRunningActivity): If TIRAMISU+ and not granted, calls `AskForPermission(POST_NOTIFICATIONS, ...)` (Dexter) or shows “Permission(s) Required” dialog with “Open Settings”.
  - **CapturePermissionActivity** (alternate entry for capture): On “Start” tap, if TIRAMISU+ and not granted, calls `ActivityCompat.requestPermissions(this, new String[]{POST_NOTIFICATIONS}, 0)`. After grant (or if already granted), proceeds to `CheckForNotificationAccess()`.
- **Check:** `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED`.
- **Usage:** **NotificationHelper** (Alarm) checks before calling `manager.notify(...)`; if not granted, returns without showing.

### 2.2 Notification Listener (read notifications)

- **Purpose:** Used when **m02_Apps** (foreground app) is enabled (`ModuleController.ENABLE_APPS`). The app needs to be in the list of “notification listeners” so it can react to notification events (e.g. for context).
- **Manifest:** A **NotificationListenerService** is declared in `app/AndroidManifest.xml`:
  - `<service android:name=".Alarm.AppNotificationListenerService" ... android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">` with `<action android:name="android.service.notification.NotificationListenerService" />`.
- **Implementation:** **AppNotificationListenerService** (app, Alarm package) extends `NotificationListenerService`. In `onNotificationPosted(StatusBarNotification)` it sets `InterCommunicationPreference.PutNewNotificationPopped(true)` so other code (e.g. alarm/UI) can react. No UI; user must enable the service in **Settings → Apps → Special app access → Notification access**.
- **When requested (in permission flow):** Only if `ModulePermissions.needNotificationListener()` is true (= `ENABLE_APPS`). **PermissionParentActivity** adds a **PreviewFrag** with `R.string.read_notification_permission_header` (“Read Notification Permission”). On “Allow”, **PreviewFrag** starts `Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")`. On return, **PermissionParentActivity.onResume()** checks `PermissionChecker.isNotificationServiceEnabled(this)`; if true, advances to next page.
- **Check (whether enabled):**
  - **PermissionChecker:** `Settings.Secure.getString(contentResolver, "enabled_notification_listeners")` contains `getPackageName()`.
  - **PermissionChecker.isNotificationServiceEnabled(Context):** Parses `enabled_notification_listeners` (split by `:`), unflattens ComponentName, compares package.
- **CapturePermissionActivity** also enforces “notification access” before starting capture: in `CheckForNotificationAccess()` it checks `enabled_notification_listeners`; if app not in list, starts `ACTION_NOTIFICATION_LISTENER_SETTINGS` and shows a toast. So from CapturePermissionActivity, both POST_NOTIFICATIONS and notification listener are required before media projection is requested.

### 2.3 Order in permission flow (PermissionParentActivity)

1. Usage access (if `needUsageAccess()`)
2. POST_NOTIFICATIONS (if TIRAMISU+ and not granted)
3. Notification listener (if `needNotificationListener()` and app not in `enabled_notification_listeners`)
4. Accessibility (if `needAccessibility()` and InteractionsUpdater not enabled)
5. Activity recognition (if `pa-enabled` and `needActivityRecognition()` and not granted)
6. Location (if `gps-enabled` and `needLocation()` and not granted)
7. Media projection (if `needMediaProjection()` and CaptureUploadService not running) — last screen; tapping “Allow” leads to media projection request.

---

## 3. Media Projection Permission Request

### 3.1 Why an activity is required

Media projection is not a normal runtime permission. The app must call `MediaProjectionManager.createScreenCaptureIntent()` and pass it to `startActivityForResult()`. The system shows its own dialog; when the user approves, the activity receives a **result code** and an **Intent**. The **MediaProjection** object is then created from that result code and intent (e.g. `mediaProjectionManager.getMediaProjection(resultCode, data)`). Only an **Activity** can use `startActivityForResult`, so the request is always made from an activity.

### 3.2 Two entry points that can request media projection

- **CaptureUploadStarter** (m01_Screenshots): Invisible activity; used when starting capture from the **permission flow** or when the system/alarm re-requests capture (e.g. after media projection died).
- **AppRunningActivity** (app): Main “app running” screen; user can turn the service **on** via a switch; that triggers a media projection request from this activity.

Which one was used is stored so the **service** can later get the correct result code and intent.

### 3.3 Storing “which activity” and the result

- **InterCommunicationPreference** (c_DatabaseManager) stores:
  - `WhichActivityStartedService`: **0** = CaptureUploadStarter, **1** = AppRunningActivity.
- Before starting the capture flow, the activity that will request media projection sets this:
  - **CaptureUploadStarter.onCreate():** `interCommunicationPrefrence.WhichActivityCalledStartService(0)` then `startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)`.
  - **AppRunningActivity** (when user turns switch on): sets `WhichActivityCalledStartService(1)` implicitly via `interCommunicationPrefrence.WhichActivityCalledStartService(1)` in onCreate; when actually launching the intent it uses `mediaProjectionManager.createScreenCaptureIntent()` and `startActivityForResult(..., REQUEST_CODE)`.
- After user approves:
  - **CaptureUploadStarter.onActivityResult:** Saves `ResultCode = resultCode`, `intent1 = data`, then starts **CaptureUploadService** and finishes. It does **not** create a `MediaProjection`; it only stores result code and intent.
  - **AppRunningActivity.onActivityResult:** Saves `ResultCode = resultCode`, `intent1 = intent`, starts **CaptureUploadService**, does **not** call `getMediaProjection(resultCode, intent)` (that line is commented out).

So both activities only **store** the result code and intent. The **CaptureUploadService** creates the **MediaProjection** itself.

### 3.4 How the service gets MediaProjection

- **CaptureUploadService** (m01) gets the **ScreenshotModuleHost** from the Application: `getHost()` → `(ScreenshotModuleHost) getApplication()` (ScreenomicsApplication).
- In **AskForScreenCapture()**:
  - `int which = getHost().getWhichActivityStartedService();` — reads from InterCommunicationPreference (0 or 1).
  - On **Android O+**: `mediaProjection = mediaProjectionManager.getMediaProjection(getHost().getMediaProjectionResultCode(which), getHost().getMediaProjectionIntent(which))`. So the **service** creates the MediaProjection using the result code and intent provided by the host.
  - On **pre-O**: `mediaProjection = getHost().getMediaProjection(which)` — host returns the activity’s MediaProjection (CaptureUploadStarter.getMediaProjection() or AppRunningActivity.getMediaProjection()). In the current code both activities leave their static `mediaProjection` unset (AppRunningActivity’s assignment is commented out), so pre-O path may be effectively unused or legacy.
- **ScreenomicsApplication** implements ScreenshotModuleHost:
  - `getWhichActivityStartedService()` → InterCommunicationPreference.`GET_WhichActivityStartedService()`.
  - `getMediaProjectionResultCode(which)` → `which == 0 ? CaptureUploadStarter.getResultCode() : AppRunningActivity.getResultCode()`.
  - `getMediaProjectionIntent(which)` → `which == 0 ? CaptureUploadStarter.getIntents() : AppRunningActivity.getIntents()`.
  - `getMediaProjection(which)` → `which == 0 ? CaptureUploadStarter.getMediaProjection() : AppRunningActivity.getMediaProjection()` (for pre-O).

So the **exact mechanism** is: activity requests with `createScreenCaptureIntent()` → user approves → activity saves result code and intent in static fields → activity starts CaptureUploadService → service asks host for result code and intent for the stored “which” activity → service calls `mediaProjectionManager.getMediaProjection(resultCode, intent)` and uses that MediaProjection for ScreenshotsUpdater.

### 3.5 When media projection is requested (entry paths)

1. **Permission flow (last step):** User reaches the “Media projection” PreviewFrag. Taps “Allow” → **PreviewFrag.beginCapture()** checks no other permission left, then starts **CaptureUploadStarter** with `EXTRA_FROM_PERMISSION_FLOW` and `EXTRA_STARTUP_INSTIGATOR`. CaptureUploadStarter runs `createScreenCaptureIntent()` and `startActivityForResult()`. On OK, starts CaptureUploadService and finishes; permission flow finishes.
2. **AppRunningActivity (switch on):** User turns the service switch on. If permissions are complete and permission flow was completed, activity calls `mediaProjectionManager.createScreenCaptureIntent()` and `startActivityForResult()`. On OK, starts CaptureUploadService and updates UI. **InterCommunicationPreference** is set to 1 (AppRunningActivity) in onCreate; the result is stored in AppRunningActivity’s statics.
3. **CapturePermissionActivity:** Not used in the main flow (EntryActivity goes to PermissionParentActivity). If used, after POST_NOTIFICATIONS and notification listener and other checks, it starts CaptureUploadStarter (which does the media projection request).
4. **Alarm / re-request:** When media projection is null (e.g. system killed it), **ScreenshotAlarmHandlerImpl** / host can call `launchCaptureStarter(justRestarted)` or `launchCaptureStarterFromAlarm(context)`, which starts **CaptureUploadStarter**. CaptureUploadStarter only shows the system dialog if not from permission flow when `!getHost().isPermissionFlowActive() && !getHost().isAppInForeground()` (otherwise it finishes without showing).

### 3.6 Suppression of automatic media projection popup

- **PermissionParentActivity** sets:
  - `sPermissionFlowActive` (true while activity is in foreground),
  - `sPermissionFlowHasPausedOnce` (true after first onPause),
  - `sPermissionFlowCompleted` (true when user has passed all screens and reached main interface).
- **CaptureUploadStarter** (when not from permission flow): if `getHost().isPermissionFlowActive()` or `getHost().isAppInForeground()`, it finishes without showing the dialog. So the system/media-projection re-request does not show the dialog during initial permission flow or when app is in foreground.
- **AppRunningActivity** when turning on: if `!PermissionParentActivity.hasUserCompletedPermissionFlow()`, it sends the user to PermissionParentActivity instead of showing the media projection dialog.

---

## 4. Manifest and module summary

- **app/AndroidManifest.xml:** POST_NOTIFICATIONS, AppNotificationListenerService (Alarm.AppNotificationListenerService), CapturePermissionActivity, PermissionParentActivity, AppRunningActivity, ScreenMonitorService, etc. CaptureUploadStarter and CaptureUploadService are declared in **m01_Screenshots/AndroidManifest.xml** (merged into app).
- **m01_Screenshots/AndroidManifest.xml:** CaptureUploadStarter (activity, translucent, excludeFromRecents), CaptureUploadService (foregroundServiceType mediaProjection), ScreenshotAlarmReceiver.

---

## 5. Flow diagrams (concise)

**Notification (POST_NOTIFICATIONS + listener):**
- Permission flow: add PreviewFrag for “Push Notification Permission” (TIRAMISU+) → Allow → Dexter request → on grant go next. Add PreviewFrag for “Read Notification Permission” (if ENABLE_APPS) → Allow → open ACTION_NOTIFICATION_LISTENER_SETTINGS → onResume check enabled → next.
- CapturePermissionActivity: Start → if no POST_NOTIFICATIONS request it → then CheckForNotificationAccess() (listener + accessibility + activity recognition + location) → then beginCapture() → CaptureUploadStarter.

**Media projection:**
- Permission flow last page → beginCapture() → CaptureUploadStarter → createScreenCaptureIntent() → startActivityForResult() → user approves → save result/intent in CaptureUploadStarter statics, start CaptureUploadService → finish.
- AppRunningActivity switch on → (if permissions and flow completed) createScreenCaptureIntent() → startActivityForResult() → user approves → save result/intent in AppRunningActivity statics, start CaptureUploadService.
- CaptureUploadService: getWhichActivityStartedService() → getMediaProjectionResultCode(which), getMediaProjectionIntent(which) from host → mediaProjectionManager.getMediaProjection(resultCode, intent) → pass to ScreenshotsUpdater.

This is the exact mechanism of notification settings and media projection permission request in the codebase.
