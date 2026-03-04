# App-level structure assessment

This document summarizes the **app** module organization and the refactors that were applied (see “Applied refactors” below). The rest of the doc remains as reference for the rationale.

---

## Applied refactors (completed)

1. **Pref → AppUtils/AppPreferences** – Moved from `Alarm/` to `AppUtils/AppPreferences.java`; `LoginActivity` updated.
2. **NotificationManage → NotificationHelper** – Renamed in `Alarm/`; `BroadcastReceiverForAlarm` and `ScreenomicsApplication` updated.
3. **SeekForNotification → AppNotificationListenerService** – Renamed in `Alarm/`; `AndroidManifest.xml` updated.
4. **CapturePermissionActivity & UsagePermissionActivity → PermissionScreens/** – Both activities moved to `PermissionScreens/`; manifest and references in `PermissionParentActivity`, `PermissionChecker`, `PreviewFrag` updated; layout `tools:context` updated.
5. **RefreshSettings → Services/** – Moved to `Services/RefreshSettings.java`; `ScreenMonitorService` uses it (same package, no import change needed).
6. **Utils split** – Added `AppUtils/AuthHelper`, `AppUtils/DeviceUtils`, `AppUtils/PathUtils`; `Utils` now delegates to them so existing `Utils.*` call sites are unchanged.

---

## Current layout (summary)

| Location | Contents |
|----------|----------|
| **Root** `edu.stanford.communication.screenomics` | `Utils`, `ScreenomicsApplication` (+ entry-point activities as needed) |
| **Activity/** | `LoginActivity`, `EntryActivity`, `AppRunningActivity`, `ScreenOnActivity` |
| **Alarm/** | `BroadcastReceiverForAlarm`, `SetAlarm`, `NotificationHelper`, `AppNotificationListenerService` |
| **PermissionScreens/** | `PermissionParentActivity`, `PermissionChecker`, `PreviewFrag`, `ViewPagerAdapter`, `CapturePermissionActivity`, `UsagePermissionActivity` |
| **Services/** | `ScreenMonitorService`, `AutostartService`, `ScreenomicsAccessService`, `RefreshSettings` |
| **LogEvents/** | Seven event logger classes (one per app-level event) |
| **AppUtils/** | `InAppUpdate`, `AppPreferences`, `AuthHelper`, `DeviceUtils`, `PathUtils` |

---

## Issues and recommendations

### 1. Root package is a catch‑all

**Issue:** The root holds both application/entry classes and feature-specific activities. Some activities live under `Activity/`, others (`CapturePermissionActivity`, `UsagePermissionActivity`) in the root.

**Recommendation:** Move permission entry activities into the permission package for consistency, e.g.:

- `CapturePermissionActivity` → `PermissionScreens/` (or keep in root if they are true app entry points and you want them visible at top level).

Alternatively, move **all** activities under `Activity/` (e.g. `Activity.CapturePermissionActivity`, `Activity.UsagePermissionActivity`) so “all activities live in one package” is a simple rule.

---

### 2. Utils is doing too much

**Issue:** `Utils` covers: subject/group/install IDs, menu creation, phone spec map, crash-log path, battery percentage, logout flow (dialog + upload), and more. That makes it a “god class” and harder to test or change one concern without touching others.

**Recommendation (optional):** Split by domain, e.g.:

- **Auth/session:** subject ID, group code, data subject ID, logout flow → e.g. `AppUtils.AuthHelper` or `AppUtils.SessionHelper`.
- **Device:** phone spec map, battery % → e.g. `AppUtils.DeviceUtils`.
- **Paths/files:** crash log path, data paths → e.g. `AppUtils.PathUtils` or keep in a single `IOUtils`.
- **UI:** menu creation → keep in `Utils` or move to `AppUtils.MenuHelper`.

You can do this gradually: extract one group of methods at a time and replace call sites.

---

### 3. Alarm package mixes concerns and naming

**Issue:**

- **Pref** is app/session preferences (step count, consent dialog, time locale, etc.). It’s only used by `LoginActivity`, so it’s not really “alarm” logic. It lives under `Alarm` for historical reasons.
- **SeekForNotification** is a `NotificationListenerService` that sets a flag when a notification is posted. The name is unclear; “Seek” doesn’t convey “listen and notify.”
- **NotificationManage** is a typo (should be Manager, but that clashes with Android’s `NotificationManager`). It creates the channel and sends notifications (e.g. for foreground service).

**Recommendation:**

- Move **Pref** to a more neutral package, e.g. `AppUtils` or a new `prefs`/`Preferences` package, and consider renaming to something like `AppPreferences` or `SessionPreferences` so it’s clear it’s not alarm-specific.
- Rename **SeekForNotification** to something like `NotificationListener` or `AppNotificationListenerService` (and fix manifest reference). **Done:** renamed to `AppNotificationListenerService`; manifest updated.
- Rename **NotificationManage** to `NotificationHelper` or `ForegroundNotificationHelper` to avoid the typo and the clash with `NotificationManager`.

---

### 4. Permission-related code in two places

**Issue:** `CapturePermissionActivity` and `UsagePermissionActivity` are in the root; the rest of the permission flow (parent activity, checker, fragments) is in `PermissionScreens/`.

**Recommendation:** Either:

- Move the two entry activities into `PermissionScreens/`, or
- Keep them in root as “app entry points” but document that convention (e.g. in this file or in a short README in the app package).

---

### 5. RefreshSettings placement

**Issue:** `RefreshSettings` is tightly coupled to `ScreenMonitorService` (it calls into it to refresh settings). It currently lives in the root.

**Recommendation:** Optionally move it under `Services/` or into a small `SettingsRefresh` (or similar) package to keep “things that drive the capture service” in one place. Low priority if you prefer to keep the root minimal and leave it there.

---

### 6. AppUtils has a single class

**Issue:** `AppUtils` only contains `InAppUpdate`. The name suggests a bag of utilities.

**Recommendation:** Either:

- Put other extracted helpers (e.g. `DeviceUtils`, `AuthHelper`) here as you split `Utils`, or
- Rename the package to something like `update` if you expect only update-related code, and leave other helpers in `Utils` or a new package.

---

## Summary table (optional refactors)

| Item | Current | Optional change |
|------|---------|------------------|
| Permission entry activities | Root | Move to `PermissionScreens/` or document as entry points |
| Utils | One large class | Split by domain (auth, device, paths, menu) |
| Pref | Under Alarm | Move to `AppUtils` or `prefs`, rename to e.g. `AppPreferences` |
| SeekForNotification | Alarm, unclear name | Rename to e.g. `AppNotificationListenerService` |
| NotificationManage | Alarm, typo | Rename to `NotificationHelper` or `ForegroundNotificationHelper` |
| RefreshSettings | Root | Optionally move to `Services/` or dedicated package |
| AppUtils | Only InAppUpdate | Use for more helpers and/or rename package |

---

## What’s already in good shape

- **LogEvents/** is clear: one class per app-level event, used only from app code.
- **Services/** groups background services in one place.
- **Activity/** groups most activities; only the permission entry points are in the root.
- **PermissionScreens/** keeps the permission flow UI and logic together.

Implementing the optional refactors above would give a more consistent package layout and clearer naming without changing behavior. If you want to proceed, a practical order is: (1) rename Alarm classes and move Pref, (2) move permission activities, (3) split Utils incrementally.
