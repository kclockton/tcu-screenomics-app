# Developer Guideline: Adding a New Data-Collection Module

This document explains how to add a new data-collection module so it follows the same patterns as existing modules: collection/processing logic, module controller gating, Firestore-driven sampling intervals, event structure and upload to Firestore/Storage, and permissions with permission screens.

**Pipeline adoption (step-by-step with examples):** For detailed instructions and code examples to adopt the **text-based** event pipeline or the **file-upload (Cloud Storage)** pipeline in a new module, see **09_Data_Pipeline_Adoption_Guide.md**.

---

## (a) How to Add Data Collection and Processing Logic

### 1. Create a feature module (optional but recommended)

- Add a new Gradle module (e.g. `m10_YourModule`) in the project root and include it in `settings.gradle`.
- In `app/build.gradle`, add `implementation project(':m10_YourModule')`.
- The module should depend on `c_DatabaseManager` and `c_ModuleManager` (and `c_SharedResources` if you need shared resources).

### 2. Implement the capture/sensor logic

- **Option A – Runs inside ScreenMonitorService (text-only, periodic)**  
  Follow the pattern of `ModulenameCollectionController`, `ModulenameInfo`, `ModulenameUpdater` (e.g. SpecsCollectionController, SpecsInfo, SpecsUpdater; or LocationsCollectionController, LocationsInfo, LocationsUpdater):
  - Your class receives a `Context` and an **interval** (milliseconds). It schedules periodic work (e.g. `Handler.postDelayed` or a `Runnable` that reschedules itself).
  - On each tick, perform the sensor/API read, build an event map (see (d)), and call:
    ```text
    EventOperationManager.getInstance(context).addEvent(
        ModuleCharacteristics.getInstance().getYourEventCharacteristics(),
        eventDetailsMap
    );
    ```
  - **Registration**: In `ScreenMonitorService.onCreate()` (or after settings load), if `ModuleController.ENABLE_YOUR_MODULE` is true (and any Firestore flag like `your-enabled` if you add one), instantiate your capture class with the interval from `SettingsManager.val("your-interval")` and start it. Store a reference so you can stop it in `onDestroy()`.

- **Option B – Runs inside CaptureUploadService (e.g. screen or media)**  
  Follow the pattern of the three main files (e.g. Screenshots: ScreenshotsCollectionController, ScreenshotsInfo, ScreenshotsUpdater):
  - The service creates your capture object with params from `SettingsManager` and starts it. Your object uses a `Handler` or similar to run at the desired interval, writes data (e.g. to files or memory), and can post events via `EventOperationManager.addEvent(...)`.
  - If your data is **files** (e.g. images, audio), write files to app storage and use the **CloudStorageUpload** package (CloudStorageUploadScheduler + CloudStorageUploadConfig; see docs/08_NonText_Data_Pipeline.md and 09 Part B) and an uploader (e.g. `ImageUploaderToCloudStorage`) to push to Firebase Storage under the subject ID. Post *event* data (e.g. “capture started”, “upload success”) through `EventOperationManager` so they go to Firestore via the same text-based event pipeline as all other modules.

### 3. Processing

- Prefer minimal processing on the device: format the event map and pass it to `addEvent()`. Heavy processing (e.g. ML) can be documented as a separate pipeline; the app’s standard pipeline is “event map → EventOperationManager → SQLite → Firestore” (and optionally files → Storage).

---

## (b) How to Connect the Module Controller

### 1. Add a feature flag in ModuleController

In `c_ModuleManager/.../ModuleController.java`:

```java
public static boolean ENABLE_YOUR_MODULE = true;  // or false by default
```

### 2. Gate your code

- **Starting the capture**: Only create/start your capture object if `ModuleController.ENABLE_YOUR_MODULE` is true (e.g. in `ScreenMonitorService` or `CaptureUploadService`).
- **Posting events**: You can also guard `addEvent()` with `if (ModuleController.ENABLE_YOUR_MODULE) { ... }` so no events are produced when the module is off.

### 3. Optional Firestore kill switch

If you want a remote “enabled” flag (like `gps-enabled` or `pa-enabled`), add a setting name (e.g. `your-module-enabled`) in `SettingsManager.resetToLocalDefaults()` and in the Firestore settings documents. Then gate both permission screens and capture start on `SettingsManager.val("your-module-enabled") == 1 && ModuleController.ENABLE_YOUR_MODULE`.

---

## (c) How to Implement Firestore Dynamic Parameters for Sampling Intervals

### 1. Define the setting name

Choose a unique key, e.g. `your-module-interval` (milliseconds).

### 2. Add a default in SettingsManager

In `c_DatabaseManager/.../FirebaseSettings/SettingsManager.java`, in `resetToLocalDefaults()`:

```java
settings.put("your-module-interval", 60 * 1000);  // example: 1 minute
```

### 3. Load from Firestore

Settings are already loaded from `users/{subjectId}/settings/user_settings` (and optionally from `settings_profiles/_default_` and `settings_profiles/{groupCode}`). No extra code is needed for loading; just use the key.

### 4. Use the value in your module

When creating your capture class (e.g. in `ScreenMonitorService`):

```java
int intervalMs = SettingsManager.val("your-module-interval");
if (intervalMs <= 0) intervalMs = 60_000;  // fallback
YourCapture capture = new YourCapture(this, intervalMs);
```

### 5. Optional: React to remote changes

If you want the app to apply new intervals without restart, use `SettingsManager`’s snapshot listener (already used for `user_settings`). When settings change, `FirebaseSettingsObserver.onSettingsChanged(List<String> changedSettings)` is called. In `ScreenMonitorService`, `RefreshSettings` already uses `settings-refresh-interval`; you can extend the observer or refresh logic to recreate your capture with the new `your-module-interval` when it appears in `changedSettings`.

### 6. Document for researchers

Add the key to the list in `03_Data_Pipeline_Developer.md` (and in any researcher-facing settings doc). Study managers can then set `your-module-interval` in Firestore (user or group settings) to control sampling rate per study or per user.

---

## (d) How to Structure and Wire Events to the Database Manager / Module Manager (Firestore / Storage)

### 1. Define event characteristics in ModuleCharacteristics

In `c_ModuleManager/.../ModuleCharacteristics.java` add a getter that returns a map for your event type:

```java
public Map<String, String> getYourEventCharacteristics() {
    return new ModuleCharacteristicsData(
        "YourEventClassName",   // className — used as event type and in ticker
        "your-event-type",      // type — e.g. for Firestore or routing
        "1"                    // updateTicker: "1" = update ticker, "0" = do not
    ).toMap();
}
```

Use a **unique** `className` (e.g. `YourEventClassName`) so it doesn’t collide with existing events. The same class can be used for multiple event subtypes by passing different `type` or extra fields in the event map.

### 2. Build the event payload

Use `EventMapBuilder.buildCompleteMap(additionalFields, type)` so every event has `time`, `time-local`, and `type`. Add module-specific fields in `additionalFields`:

```java
HashMap<String, String> extra = new HashMap<>();
extra.put("your_field", value);
// ...
Map<String, String> eventDetails = EventMapBuilder.buildCompleteMap(extra, "SubTypeOrLabel");
EventOperationManager.getInstance(context).addEvent(
    ModuleCharacteristics.getInstance().getYourEventCharacteristics(),
    eventDetails
);
```

If you don’t need defaults, you can still build a map manually and pass it as the second argument; the first argument must be the map from `getYourEventCharacteristics()` so the pipeline knows `className`, `type`, and `updateTicker`.

### 3. Send to Firestore (text events)

- **Buffered path**: `EventOperationManager.addEvent(...)` buffers the event, then writes to SQLite. `EventUploaderToFireStore.startUploadOfflineToOnlineEvents()` reads from SQLite and uploads to **Firestore** `users/{subjectId}/events/{eventDocId}` (or `specs/` for specs). So by using `addEvent()` you automatically get Firestore upload with no extra wiring.
- **Direct path**: For one-off events (e.g. from an alarm), use `EventUploaderToFireStore.getInstance(context).uploadSingleEvent(eventName, eventDataMap, context)`. That writes to `users/{subjectId}/events/{generatedId}` and updates the ticker. Use the **className** as `eventName` if you want consistency with the ticker (and ensure `EventOperationManager.UpdateTickerField()` knows your className if you want a custom ticker string).

### 4. Ticker (optional)

If your getter uses `updateTicker` `"1"`, then `EventOperationManager` will update `DataStorage` with the latest occurrence of your event. That gets merged to Firestore `ticker/{subjectId}` by `EventUploader`. If you need a custom ticker string (e.g. for pause/resume), add a branch in `EventOperationManager.UpdateTickerField(moduleInfo, eventDetails)` for your `className` and return the suffix string.

### 5. Non-text data (e.g. images) to Storage

- Save files under the app’s external files dir (e.g. a subfolder). Use the same subject ID as elsewhere (`UtilsForFirebaseSettings.getCodeAndNumber(context)`).
- Either use `ImageUploaderToCloudStorage` (which uploads from a staging folder to `StorageRef/{subjectId}/{filename}`) or implement a similar runnable that:
  - Lists files in your folder,
  - Uploads each to Firebase Storage under `{subjectId}/` (or a subpath),
  - Deletes or moves the file on success,
  - Optionally posts a “upload completed” or “upload failure” event via `EventOperationManager.addEvent(...)` so it appears in Firestore.

### 6. Event log structure summary

- **Required**: `time`, `time-local`, `type` (via `EventMapBuilder` or manual).
- **Optional**: any key-value pairs in `additionalFields`. Keep keys consistent so researchers can parse them (e.g. `cause`, `activity`, `screen`).
- **Storage**: Events are stored in SQLite as JSON; uploaded to Firestore as documents with the same structure. Document ID is generated from className + timestamp + id so each event is unique.

---

## (e) How to Add a Specific Permission and Adjust Permission Screens

### 1. Add the permission in AndroidManifest

In `app/src/main/AndroidManifest.xml` add the appropriate `<uses-permission>` (and if needed a `<uses-feature>`). For a **runtime** permission (e.g. a new dangerous permission), no extra manifest entry is needed beyond the permission tag; you request it at runtime.

**Exact alarms:** If your module schedules exact alarms via `AlarmManager.setExact()` (or similar), the app should use **`SCHEDULE_EXACT_ALARM`** only—do **not** add `USE_EXACT_ALARM` unless the app’s core purpose is an alarm clock. Policy: permission use must be directly related to the app’s core purpose; `SCHEDULE_EXACT_ALARM` is the correct choice for reminders and follow-up notifications.

### 2. Add a gating method in ModulePermissions

In `c_ModuleManager/.../ModulePermissions.java`:

```java
public static boolean needYourPermission() {
    return ModuleController.ENABLE_YOUR_MODULE;
}
```

Use this so the permission is only requested when the module is enabled.

### 3. Check and request the permission in PermissionChecker

In `app/.../PermissionScreens/PermissionChecker.java`:

- In **checkAndRequestPermissions()**: If `ModulePermissions.needYourPermission()` (and any Firestore flag like `your-enabled`) is true, check whether the permission is granted; if not, call `AskForPermission(Manifest.permission.YOUR_PERMISSION, "Your Permission")` and include “Your Permission” in the description string shown when multiple are missing.
- In **isAllPermissionGranted()** (or equivalent): Add a check so that if `needYourPermission()` is true and the permission is not granted, the method returns false so the user cannot proceed until they grant it (or open settings).

### 4. Add a permission screen in the ViewPager (PermissionParentActivity)

In `app/.../PermissionScreens/PermissionParentActivity.java`, inside the `run()` that runs after settings load (in `AddViewPageAccordingToPermission()`), add a block similar to the existing ones:

```java
if (ModulePermissions.needYourPermission() && !hasYourPermission(PermissionParentActivity.this)) {
    viewPagerAdapter.add(new PreviewFrag(
        getString(R.string.your_permission_header),
        getString(R.string.your_permission_description)
    ));
    TotalPages = TotalPages + 1;
}
```

Implement `hasYourPermission(Context)` (e.g. in `PermissionChecker` or your module) to reflect whether the permission (or system setting) is granted.

### 5. Add string resources

In `app/src/main/res/values/strings.xml` (or the appropriate locale):

```xml
<string name="your_permission_header">Your Permission</string>
<string name="your_permission_description">This app needs … to …</string>
```

### 6. Optional: Custom permission screen or settings redirect

If your permission requires sending the user to **Settings** (e.g. usage access, notification listener, accessibility), follow the pattern of `UsagePermissionActivity` or the notification-listener / accessibility checks in `PermissionChecker` and `PreviewFrag`: check the corresponding system setting and, if not enabled, show a `PreviewFrag` that explains and has a button to open the right Settings screen (e.g. `Settings.ACTION_*`). The fragment can call `PermissionChecker.isXxxEnabled(context)` and refresh when returning from Settings.

### 7. Summary checklist for a new permission

- Manifest: `uses-permission` (and optional `uses-feature`).
- `ModulePermissions.needYourPermission()` gated by `ModuleController.ENABLE_YOUR_MODULE` (and optional Firestore flag).
- `PermissionChecker`: request and include in “all granted” check.
- `PermissionParentActivity`: add a `PreviewFrag` when `needYourPermission()` and permission not granted.
- Strings for title and description.

This keeps the new module and its permission consistent with how location, activity recognition, usage access, notification listener, and accessibility are handled.
