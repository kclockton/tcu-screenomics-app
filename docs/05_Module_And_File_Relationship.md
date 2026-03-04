# Module and File Relationship — Functionality Focus

This document maps modules and key files to their responsibilities and how they depend on each other.

---

## 1. Module Map (Gradle / Packages)

| Module | Purpose | Key classes / packages |
|--------|---------|-------------------------|
| **:app** | UI, login, permissions, services, alarms | Activities, Services, Alarm, PermissionScreens |
| **:m01_Screenshots** | Screen capture, upload, and capture-service lifecycle | **screenshots.ScreenshotsCollectionController**, **screenshots.ScreenshotsInfo**, **screenshots.ScreenshotsUpdater** (main); supplementary: CaptureUploadStarter, CaptureUploadService, ImageUploaderToCloudStorage, ScreenshotAlarmReceiver, ScreenshotAlarmHandlerImpl, ScreenshotModuleHost, ImageUtils |
| **:m02_Apps** | Foreground app, screen on/off | apps.AppsCollectionController, AppsInfo, AppsUpdater |
| **:m03_Interactions** | User interaction (accessibility) | interactions.InteractionsCollectionController, InteractionsInfo, InteractionsUpdater |
| **:m04_Locations** | GPS location | locations.LocationsCollectionController, LocationsInfo, LocationsUpdater |
| **:m05_Activites** | Step count / physical activity | activites.ActivitiesCollectionController, ActivitiesInfo, ActivitiesUpdater |
| **:m06_Battery** | Battery state and charging | battery.BatteryCollectionController, BatteryInfo, BatteryUpdater |
| **:m07_Power** | System power (boot, power on) | power.PowerCollectionController, PowerInfo, PowerUpdater |
| **:m08_Network** | Network connectivity | network.NetworkCollectionController, NetworkInfo, NetworkUpdater |
| **:m09_Specs** | Device specs collection | specs.SpecsInfo, SpecsUpdater |
| **:c_ModuleManager** | Feature flags, event metadata, canonical timestamps | ModuleController, ModuleCharacteristics, ModulePermissions, ModuleCharacteristicsData, EventTimestamp |
| **:c_DatabaseManager** | Events DB, upload, Firebase settings, server-time sync | **TextBasedData.*** (text-based event data from all modules → Firestore), **NonTextBasedData.*** (file upload to Cloud Storage: config, policy, scheduler), **DatabaseHelper.*** (shared helpers for both), FirebaseSettings.*, ServerTimeSync |
| **:c_SharedResources** | Shared strings, resources | e.g. cloud_storage_url, config |

---

## 2. Dependency Flow (Functionality)

- **App** depends on all feature modules and on **c_DatabaseManager**, **c_ModuleManager**, **c_SharedResources**.
- **Feature modules** typically depend on **c_DatabaseManager** (EventOperationManager, EventMapBuilder, sometimes SettingsManager) and **c_ModuleManager** (ModuleController, ModuleCharacteristics). They do **not** depend on each other.
- **c_DatabaseManager** may depend on **c_ModuleManager** (e.g. ModuleCharacteristics for event types) and **c_SharedResources** (R for Storage URL).
- **c_ModuleManager** is standalone (no project dependency on other feature/libs). EventTimestamp lives here; c_DatabaseManager.ServerTimeSync fetches Firebase server time and calls EventTimestamp.setServerTime() so all events use one canonical timestamp source.

---

## 3. File-to-Functionality Map

### 3.1 App module

| File / path | Functionality |
|-------------|----------------|
| **ScreenomicsApplication** | Application lifecycle; app-in-foreground tracking. |
| **LoginActivity** | Auth UI; ensures login before starting capture/permissions. |
| **AppRunningActivity** | Main UI; start/stop capture; sets pause cause to "user" when user turns off. |
| *(CaptureUploadStarter in m01)* | Media projection request; starts CaptureUploadService when user grants capture. |
| *(CaptureUploadService in m01)* | Holds media projection; runs ScreenshotsUpdater and ImageUploader; on stop sets pause cause and schedules screenshot alarms via ScreenshotsCollectionController; on start clears pause cause and cancels those alarms. |
| **ScreenMonitorService** | Foreground service for non-screenshot modules (location, battery, network, specs, foreground app, steps); screen-on/off handling; attemptServiceRestart (popup suppressed 30 min after user pause); periodic text-event upload. |
| **SetAlarm** | Schedules all alarms: 0 (2 min), 1–2 (7 PM/7 AM), 5 (30 min ask-again), 6–7 (restart monitor), 8 (legacy 30 min user), 10–13 (user-pause 7 AM/7 PM). |
| **BroadcastReceiverForAlarm** | Handles alarm intents 1–2 (7 PM/7 AM notification + log), 6–7 (restart ScreenMonitorService). Codes 0, 5, 8, 10–13 are handled by m01 ScreenshotAlarmReceiver + ScreenshotAlarmHandlerImpl. |
| **NotificationManage** | App-level “resume capture” / “reopen app” notifications; used by BroadcastReceiverForAlarm (1, 2, 6, 7) and by m01’s ScreenshotAlarmHandlerImpl via host.sendResumeCaptureNotification(). |
| **PermissionParentActivity** | ViewPager permission flow; tracks permission-flow state (active, completed) to avoid showing capture popup during setup. |
| **PermissionChecker** | Requests runtime permissions (notification, location, activity) based on ModulePermissions and SettingsManager. |
| **UsagePermissionActivity** | Usage access / notification listener setup. |
| **CapturePermissionActivity** | Capture-related permission UI. |

### 3.2 c_ModuleManager

| File | Functionality |
|------|----------------|
| **ModuleController** | Static booleans ENABLE_SCREENSHOTS, ENABLE_APPS, … to turn modules on/off. |
| **ModuleCharacteristics** | Returns per-event-type metadata maps (className, type, updateTicker, id, …) for EventOperationManager.addEvent(). |
| **ModuleCharacteristicsData** | Builds those maps; generates short id and timestamps (uses EventTimestamp). |
| **EventTimestamp** | Canonical event timestamps (system/server time); setServerTime() called by ServerTimeSync after Firebase fetch. |
| **ModulePermissions** | needUsageAccess(), needAccessibility(), needActivityRecognition(), needLocation(), needMediaProjection(), needNotificationListener() — all gated by ModuleController. |

### 3.3 c_DatabaseManager

| File / path | Functionality |
|-------------|----------------|
| **EventOperationManager** | addEvent(moduleInfo, details); buffers events; updates DataStorage (ticker); flushes to SQLite in batches; forceFlushToDB(). |
| **EventDatabaseHelper** | SQLite events table; insert, getLimitedEvents, deleteEvent, getTotalEventCount. |
| **EventData** / **EventMapBuilder** | Event structure (time, time-local, type + extras); buildCompleteMap() for default fields. |
| **DataStorage** | In-memory ticker map (event name → last occurrence string). |
| **EventUploader** | Periodic and immediate upload of DataStorage to Firestore ticker/{subjectId}; triggers EventUploaderToFireStore batch upload. |
| **EventUploaderToFireStore** | Reads SQLite events, uploads to users/{id}/events/ or specs/; uploadSingleEvent() for direct + ticker update. |
| **HashMapPool** | Reusable HashMap pool for reducing allocations. |
| *(m01)* **ImageUploaderToCloudStorage** | Moves screenshots from capture dir to upload dir; uploads to Firebase Storage under subjectId; reports upload events. |
| **SettingsManager** | Load/save settings (defaults, disk, Firestore); user_settings and settings_profiles; getVal() / val(). |
| **FirebaseManagerSingleton** / **UtilsForFirebaseSettings** | Firestore instance; subject/group codes for paths. |
| **ServerTimeSync** | Fetches Firebase .info/serverTimeOffset; calls EventTimestamp.setServerTime() so all events use real-world time. |
| **InterCommunicationPreference** | Pause cause, user-pause timestamp, which activity started service, battery dialog state, etc. |
| **LogInPreference** | User credentials and shared prefs (including setting_* for SettingsManager). |

### 3.4 Feature modules (representative)

| Module | File | Functionality |
|--------|------|----------------|
| m01_Screenshots | **ScreenshotsCollectionController**, **ScreenshotsInfo**, **ScreenshotsUpdater** | Controller: alarm scheduling (2 min, 30 min ask-again, 7 AM/7 PM). Info: event map builders (capture startup, screenshot success/failure, upload). Updater: MediaProjection + ImageReader; periodic capture; write to external files dir; post events via ScreenshotsInfo; uses SettingsManager for interval and image quality. Supplementary: CaptureUploadService, ImageUploaderToCloudStorage, etc. |
| m02_Apps | **AppsInfo**, **AppsUpdater**, **AppsCollectionController** | Foreground app (interval) and screen on/off (receiver); NewForegroundAppEvent, ScreenOnOffEvent. |
| m03_Interactions | **InteractionsCollectionController**, **InteractionsInfo**, **InteractionsUpdater** | AccessibilityService; interaction events (scroll, click, long-click, touch exploration) via InteractionsInfo; gated by InteractionsCollectionController.shouldCollect(). |
| m04_Locations | **LocationsInfo**, **LocationsUpdater**, **LocationsCollectionController** | GPS at gps-location-interval; GPSLocationEvent. |
| m06_Battery | **BatteryInfo**, **BatteryUpdater**, **BatteryCollectionController** | Battery state and charging events. |
| m08_Network | **NetworkInfo**, **NetworkUpdater**, **NetworkCollectionController** | Connectivity events (InternetEvent). |
| m07_Power | **PowerInfo**, **PowerUpdater**, **PowerCollectionController** | SystemPowerEvent (boot, power on); PowerUpdater.BootReceiver in manifest. |
| m09_Specs | **SpecsInfo**, **SpecsUpdater**, **SpecsCollectionController** | Device spec data; periodic SpecsEvent collection via EventOperationManager. |

---

## 4. Cross-Cutting Relationships

- **Who uses ModuleController?**  
  App (CaptureUploadService, ScreenMonitorService for starting modules), PermissionChecker / PermissionParentActivity, and module code that checks ENABLE_* before registering or posting events.

- **Who uses ModuleCharacteristics?**  
  EventOperationManager (indirectly via addEvent(moduleInfo, …)); EventUploaderToFireStore (GenerateEventId); any code that posts events (CaptureUploadService, BroadcastReceiverForAlarm, ScreenMonitorService modules, ScreenshotsUpdater, etc.).

- **Who uses SettingsManager?**  
  CaptureUploadService (screenshot and upload params), ScreenMonitorService (all text-module intervals and flags), PermissionChecker/CapturePermissionActivity (gps-enabled, pa-enabled), NetworkUtils (data-text-upload-wifi-only), ImageUploaderToCloudStorage (via service params).

- **Who writes to Firestore?**  
  EventUploader (ticker); EventUploaderToFireStore (events, specs); SettingsManager (user_settings read/write, settings_profiles read).

- **Who writes to Storage?**  
  ImageUploaderToCloudStorage only (screenshots under subjectId).

This map should be read together with the **Developer guideline** (docs/06_Developer_Guideline_New_Module.md) for adding a new data-collection module and wiring it to the same pipeline.

---

## 5. App-level vs module-specific (audit)

**App module should keep only:**  
- **UI**: EntryActivity, LoginActivity, AppRunningActivity, PermissionParentActivity, PermissionChecker, PreviewFrag, CapturePermissionActivity, UsagePermissionActivity, ScreenOnActivity.  
- **Orchestration**: ScreenomicsApplication (implements ScreenshotModuleHost), ScreenMonitorService (starts/stops collection controllers and event upload pipeline), AutostartService (boot), SetAlarm, BroadcastReceiverForAlarm (handles alarm codes 1, 2, 6, 7).  
- **App-wide helpers**: NotificationManage (resume-capture notifications), RefreshSettings, Utils, Pref, InAppUpdate, SeekForNotification.  
- **Firebase/app init**: FirebaseManagerSingleton, FirebaseSettingsObserver (if in app).

**Moved into modules (no longer in app):**  
- **m01**: ScreenshotAlarmHandlerImpl, ImageUtils, CaptureUploadStarter, CaptureUploadService (and screenshot alarm codes 0, 5, 8, 10–13 handled by m01).  
- **m02–m09**: All collection logic and controllers live in their modules; app only calls `*CollectionController.startCollecting/stopCollecting` (and PowerCollectionController.reportPowerOn).

**Acceptable app → module references:**  
- **Starting/stopping capture**: App starts CaptureUploadStarter (m01) via Intent; stops capture via `ScreenshotModuleHost.stopCaptureService()`.  
- **Checking state**: Via host: `isCaptureServiceRunning()`, `launchCaptureStarterFromAlarm()`, etc. BroadcastReceiverForAlarm uses host for capture checks (not CaptureUploadService class).  
- **Instigator**: App passes `CaptureUploadService.EXTRA_STARTUP_INSTIGATOR` when starting CaptureUploadStarter or CaptureUploadService; m01 sets `ScreenshotsUpdater.startupInstigator` from the intent. App does **not** set ScreenshotsUpdater.startupInstigator directly.  
- **Settings callback**: ScreenMonitorService uses its own `SettingsDatabaseListener` (not CaptureUploadService.SettingsDatabaseListener).  
- **Permission/accessibility checks**: PermissionChecker and permission flows reference InteractionsUpdater, NetworkUpdater, etc., for “is this permission enabled?” — required for app UI.
