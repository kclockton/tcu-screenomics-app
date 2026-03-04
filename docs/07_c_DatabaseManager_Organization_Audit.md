# c_DatabaseManager Organization Audit

This document answers: (1) how c_DatabaseManager is organized into **three folders** (TextBasedData, NonTextBasedData, DatabaseHelper); (2) whether **LogInPreference** and **InterCommunicationPreference** should stay in c_DatabaseManager or move to the app.

---

## 1. Three folders in c_DatabaseManager

c_DatabaseManager is organized into three main folders:

| Folder | Purpose |
|--------|--------|
| **TextBasedData** | Manages text-based (event) data from all data collection modules; uploads to Firestore. Every module—including those that also collect files—uses this for event data. |
| **NonTextBasedData** | Manages non-text data from non-text-based modules; upload scheduling and policy for Cloud Storage only (CloudStorageUploadConfig, CloudStorageUploadPolicy, CloudStorageUploadScheduler). Event data from these modules still goes through TextBasedData. |
| **DatabaseHelper** | Shared helpers (e.g. LogInPreference, InterCommunicationPreference) used by both TextBasedData and NonTextBasedData. |

*(Obsolete paragraph below.)*

**No separate "NonTextEventData" folder.** Data collection modules that collect both files (screenshots, audio) and event data use: (1) **TextBasedEventData** for all *event* data (same as every other module); (2) **CloudStorageUpload** in c_DatabaseManager only for the *file* upload pipeline to Firebase Storage (scheduling, policy). The **CloudStorageUpload** package provides **CloudStorageUploadConfig**, **CloudStorageUploadPolicy**, **CloudStorageUploadScheduler** so modules that upload files use a standardized mechanism without a separate "non-text event" concept.

*(Previous answer: "No" — there was no such folder; it has since been added.)*

*(Obsolete:)* **No.** There is only **TextBasedEventData** in c_DatabaseManager. The name “text-based” refers to **structured events** (key-value maps) that are buffered, stored in SQLite, and uploaded to Firestore (`users/{id}/events` and ticker). **Non-text data** (screenshots) is handled in **m01_Screenshots**: files on disk and upload via `ImageUploaderToCloudStorage` to Firebase Storage. So there is no parallel “NonTextBasedEventData” package in c_DatabaseManager to align with.

---

## 2. TextBasedData — current layout and consistency

**Current structure (single flat package):**

| Class | Role |
|-------|------|
| **EventData** | Immutable event model (time, type, id, extra fields). |
| **EventMapBuilder** | Builds event map with default timestamp fields. |
| **EventOperationManager** | Entry point: addEvent → buffer → batch flush to SQLite. |
| **EventDatabaseHelper** | SQLite helper (table `events`). |
| **EventUploaderToFireStore** | Reads SQLite, uploads to Firestore `users/.../events`; uploadSingleEvent for one-off. |
| **EventUploader** | Periodic upload of DataStorage (ticker) to Firestore; triggers offline-event upload. |
| **DataStorage** | In-memory ticker map (event name → last occurrence string). |
| **HashMapPool** | Pool of HashMaps for event maps. |
| **MapUtils** | Serialize/deserialize Map to JSON (for SQLite event_data). |
| **NetworkUtils** | isInternetAvailable; IsOnlyUploadTextOnWifi (uses SettingsManager). |

**Assessment:**

- **Consistent:** All are in one package, same naming style, and form one pipeline: add → buffer → SQLite → upload.
- **Could be clearer:** The package mixes several concerns (model, persistence, upload, shared utils). No subpackages, so “text vs non-text” isn’t the issue—there’s only one event pipeline here.

**Optional improvements (if you want tighter organization):**

1. **Subpackages** (no rename of “TextBasedEventData” required):
   - `TextBasedData.model` — EventData, EventMapBuilder.
   - `TextBasedData.storage` — EventDatabaseHelper, DataStorage.
   - `TextBasedData.upload` — EventUploader, EventUploaderToFireStore.
   - `TextBasedData.util` — MapUtils, HashMapPool, NetworkUtils (or leave NetworkUtils in upload if it’s only used there).

   That would make the pipeline layers explicit and keep the same public API (callers still use `EventOperationManager`, etc.).

2. **Naming:** “TextBasedEventData” is a bit vague. Alternatives could be `events` or `eventpipeline` to reflect “structured events to Firestore.” Rename is optional and would require updating imports across app and modules.

3. **NetworkUtils:** Generic (connectivity, wifi-only). If it’s only used for event upload, keeping it in TextBasedData is fine; if reused elsewhere, consider a shared util package (e.g. under c_SharedResources or a small `util` in c_DatabaseManager).

---

## 3. LogInPreference and InterCommunicationPreference — app vs c_DatabaseManager

**What they do:**

- **LogInPreference:** User/session identity: subject ID, user code, install code, email, number, password. Used for Firestore paths (`users/{subjectId}/...`), Firebase settings, and uploads.
- **InterCommunicationPreference:** App/module state: pause cause, user-pause timestamp, which activity started capture, battery-optimization dialog state, resume cause. Used by alarm logic, capture lifecycle, and UI.

**Who uses them:**

- **LogInPreference:** app (LoginActivity, Utils, ScreenomicsApplication), **m01** (CaptureUploadService, ScreenshotsUpdater, ScreenshotAlarmHandlerImpl, ImageUploaderToCloudStorage), **c_DatabaseManager** (EventUploaderToFireStore, EventUploader, SettingsManager, UtilsForFirebaseSettings), **m03** (InteractionsUpdater).
- **InterCommunicationPreference:** app (BroadcastReceiverForAlarm, ScreenMonitorService, AppRunningActivity, SeekForNotification), **m01** (CaptureUploadStarter, CaptureUploadService, ScreenshotAlarmHandlerImpl, ScreenshotsUpdater), **m02** (AppsUpdater).

**Conclusion: they should stay in c_DatabaseManager (DatabaseHelper).**

- The **app** owns the *semantics* (login, pause/resume), but the **storage** is shared: both the app and **multiple modules** (m01, m02, m03) and **c_DatabaseManager** itself need to read/write these preferences.
- If you move **LogInPreference** or **InterCommunicationPreference** into the **app** module:
  - **c_DatabaseManager** would have to depend on the app (SettingsManager, EventUploader, EventUploaderToFireStore use LogInPreference).
  - **m01, m02, m03** would have to depend on the app to get subject ID, pause cause, etc.
  That would **invert** the dependency: today the app and feature modules depend on c_DatabaseManager; you do not want core or feature modules depending on the app.
- Keeping them in **c_DatabaseManager / DatabaseHelper** keeps a single place for “shared persistence” that both the app and all modules can use without creating app dependencies. So they are in the right place.

**Optional refinement:** If you want the app to “own” the *interface* (e.g. “current user”, “pause reason”), you could introduce a thin interface in c_ModuleManager or c_SharedResources and have the app implement it by delegating to these prefs; the current design (modules and app both using the same pref classes in c_DatabaseManager) is already consistent and keeps dependencies clean.

---

## 4. Summary

| Question | Answer |
|----------|--------|
| TextBasedData vs NonTextBasedData | TextBasedData = event data from all modules → Firestore. NonTextBasedData = file upload to Cloud Storage only (scheduling/policy). DatabaseHelper = shared for both. |
| TextBasedData organization | Consistent single-package pipeline. Optional: add subpackages (model, storage, upload, util) for clarity. |
| LogInPreference / InterCommunicationPreference in DatabaseHelper | **Keep in c_DatabaseManager.** They are shared by app and modules; moving them to the app would force c_DatabaseManager and m01/m02/m03 to depend on the app. |
