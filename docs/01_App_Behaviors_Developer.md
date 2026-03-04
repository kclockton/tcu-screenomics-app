# App Behaviors — Developer Documentation

This document describes app behaviors from an implementation perspective: services, alarms, permissions, and user flows.

---

## 1. Architecture Overview

- **App module (`:app`)**: UI, login, permission flows, alarm handling, and orchestration. Depends on all feature modules and `c_DatabaseManager`, `c_ModuleManager`, `c_SharedResources`.
- **Feature modules (`m01_Screenshots`, `m02_Apps`, …)**: Data collection logic. Gated by `ModuleController` flags and optional Firestore settings (e.g. `gps-enabled`, `pa-enabled`).
- **c_ModuleManager**: `ModuleController` (enable/disable modules), `ModuleCharacteristics` (event types/ticker), `ModulePermissions` (which permissions to request).
- **c_DatabaseManager**: Event buffering, SQLite, Firestore/Storage upload, Firebase settings (SettingsManager).

---

## 2. Core Services

### 2.1 ScreenMonitorService

- **Role**: Long-running foreground service that runs when the app is “on” for data collection. Hosts text-based modules (location, battery, network, specs, foreground app, step count) and triggers periodic upload of text events. Does **not** capture screenshots; that is done by `CaptureUploadService`.
- **Start**: From `CaptureUploadStarter`, `AppRunningActivity`, or alarm-triggered restart (e.g. after 1 min or 4 hours). Can auto-restart after a random 5–6 hour run.
- **Behavior**:
  - Registers for `ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_USER_PRESENT`.
  - When capture is paused and screen turns on: calls `attemptServiceRestart()`, which may launch `CaptureUploadStarter` (media projection popup) **unless** it’s a user-initiated pause and still within 30 minutes of that pause (then popup is suppressed).
  - Uses `SettingsManager` for intervals: `settings-refresh-interval`, `specs-check-interval`, `pa-stepcounts-interval`, `pa-enabled`, `foreground-app-check-interval`, `gps-enabled`, `gps-location-interval`, `data-text-upload-interval`, `data-text-upload-wifi-only`.
- **Key class**: `app/.../Services/ScreenMonitorService.java`.

### 2.2 CaptureUploadService

- **Role**: Holds media projection and runs screenshot capture (m01_Screenshots) and image upload. Only runs when user has granted screen capture; when it stops, “pause” cause is set to `user` or `system`.
- **Start**: Only after user grants media projection (via `CaptureUploadStarter` or `AppRunningActivity`). Uses `SettingsManager` for: `screenshot-interval`, `screenshot-check-interval`, `screenshot-absolute-timing`, `data-nontext-upload-wifi-only`, `data-nontext-upload-interval`, `kill-switch`.
- **Stop**:
  - User revokes capture or turns off in app → `putPauseCause("user")` (or `"system"` if screen was off).
  - Media projection dies → callback stops service and sets pause cause.
- **Cleanup (`cleanupResources()`)**:
  - **User pause**: 2-min alarm (request code 0), 30-min “ask again” (5), and user-pause follow-up alarms (10–13 for next day 7 AM/7 PM, day-after-next 7 AM/7 PM). No 30-min single notification anymore.
  - **System pause**: 2-min alarm (0), 30-min “ask again” (5).
  - Cancels alarm 4 in both cases.
- **Resume**: When service starts again, `initializeComponents()` clears pause cause and user-pause timestamp, cancels alarms 0, 8, and 10–13.

---

## 3. Alarms (SetAlarm / BroadcastReceiverForAlarm)

- **Exact alarm permission**: The app uses **`SCHEDULE_EXACT_ALARM`** only (not `USE_EXACT_ALARM`), per policy for non–alarm-clock apps. All alarms use `AlarmManager.setExact(AlarmManager.RTC_WAKEUP, ...)`: request codes **0, 5, 10–13** are scheduled by **ScreenshotsCollectionController** (m01); **1, 2, 6, 7** by **SetAlarm** (app).

| Request code | Purpose | When scheduled |
|-------------|---------|-----------------|
| 0 | 2-min: show popup (system + screen on) or send notification; log to Firestore for user-pause path | On pause (user or system) |
| 1 | 7 PM: notification + log, then schedule 7 AM (2) | From existing 7 PM/7 AM chain (non–user-pause flow) |
| 2 | 7 AM: notification + log, then schedule 7 PM (1) | From 7 PM handler |
| 5 | 30 min: try to start CaptureUploadStarter (popup) if capture still paused | On pause (user or system) |
| 6 | 4 h: try to restart ScreenMonitorService if not running | Periodic |
| 7 | 1 min: restart ScreenMonitorService if not running | After certain events |
| 8 | 30-min user-pause notification (legacy; no longer set for new user pauses) | — |
| 10–13 | User-pause follow-up: next day 7 AM, next day 7 PM, day-after-next 7 AM, day-after-next 7 PM | On user pause only |

- **Case 0**: If cause is `"system"` and screen on and app not in foreground → start capture (popup). Else → send notification (and `LogEvent` for Firestore).
- **Cases 10–13**: Only act if `CaptureUploadService` not running and cause is still `"user"`. Send same notification and `LogEvent`; do not reschedule.

---

## 4. User-Initiated Pause vs System Pause

- **Pause cause** is stored in `InterCommunicationPreference`: `putPauseCause("user")` or `putPauseCause("system")`. For user pause, `UserPauseTimestampMs` is also stored.
- **2 min**: Both user and system get the 2-min alarm (0); notification is sent and logged.
- **30 min after user pause**: Screen-on popup is **suppressed** in `ScreenMonitorService.attemptServiceRestart()` if `getPauseCause() == "user"` and within 30 min of `getUserPauseTimestampMs()`. After 30 min, popup can show again on screen on.
- **Follow-up notifications (7 AM/7 PM)**: Only for user pause; alarms 10–13. When user resumes, `clearPauseCause()` and `CancelUserPauseFollowUpAlarms()` clear state and cancel 10–13.

---

## 5. Permission Flow

- **PermissionParentActivity**: ViewPager of permission steps. Tracks `sPermissionFlowActive`, `sPermissionFlowHasPausedOnce`, `sPermissionFlowCompleted`. Used to avoid showing media projection popup before user has finished setup.
- **PermissionChecker**: Requests runtime permissions (e.g. notification, activity recognition, location) based on `ModulePermissions` and `SettingsManager` (e.g. `pa-enabled`, `gps-enabled`). Does **not** grant media projection; that is a separate flow.
- **ModulePermissions**: Maps modules to permissions (usage access, accessibility, activity recognition, location, media projection, notification listener). All checks delegate to `ModuleController.ENABLE_*`.
- **Capture flow**: User must pass through permission screens and then grant media projection via `CaptureUploadStarter` or `AppRunningActivity` to start `CaptureUploadService`.

---

## 6. Login and Firebase Identity

- **LogInPreference** / **UtilsForFirebaseSettings**: Subject ID and group code used for Firestore paths (`users/{codeAndNumber}/...`) and Storage. Settings are loaded per user; group settings can be cloned from `settings_profiles/{groupCode}`.

---

## 7. Key Files Reference

| Area | Files |
|------|--------|
| Services | `app/.../Services/ScreenMonitorService.java`; `m01_Screenshots/.../screenshots/CaptureUploadService.java` |
| Alarms | `app/.../Alarm/SetAlarm.java`, `BroadcastReceiverForAlarm.java` |
| Pause state | `c_DatabaseManager/.../InterCommunicationPreference.java` |
| Permissions | `app/.../PermissionScreens/PermissionChecker.java`, `PermissionParentActivity.java` |
| Module gating | `c_ModuleManager/.../ModuleController.java`, `ModulePermissions.java` |
