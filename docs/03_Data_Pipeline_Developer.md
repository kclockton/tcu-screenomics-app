# Data Collection and Processing / Transfer Pipeline — Developer Documentation

This document describes how data flows from collection to local storage and to Firestore/Storage: event structure, buffering, upload paths, and Firestore dynamic parameters.

---

## 1. Overview

- **Text-like events** (e.g. location, battery, interactions, alarm notifications, pause/resume): Buffered in memory → written to SQLite → uploaded to **Firestore** (`users/{subjectId}/events/` and optionally `users/{subjectId}/specs/`). A **ticker** (latest event timestamps) is written to **Firestore** `ticker/{subjectId}`.
- **Screenshots**: Saved to app storage, then uploaded to **Firebase Storage** under the subject ID. Screenshot-related **events** (e.g. capture start, upload, failure) go through the same text pipeline to Firestore.

---

## 2. Text Event Pipeline (Firestore)

### 2.1 Producers

Modules (or app code) call:

```text
EventOperationManager.getInstance(context).addEvent(moduleCharacteristics.getXxxCharacteristics(), eventDetailsMap);
```

`moduleCharacteristics` comes from `ModuleCharacteristics` (c_ModuleManager): each getter returns a map with `className`, `type`, `updateTicker`, `id`, `timestamp`, `timestampLocal`. The **className** (e.g. `ScreenshotPauseEvent`, `GPSLocationEvent`) identifies the event type; **updateTicker** controls whether this event updates the ticker.

### 2.2 EventOperationManager

- **addEvent(moduleInfo, eventDetails)**:
  - Builds an `EventData` via `EventData.Builder(type, className)` and adds `eventDetails`.
  - Appends to an in-memory **buffer**.
  - If **updateTicker** is `"1"`: updates `DataStorage` with `MostRecentEventTime` and a ticker field for this event (using `UpdateTickerField()` for display strings).
  - If **className** is `SpecsEvent`: calls `forceFlushToDB()` so specs reach SQLite quickly.
  - When buffer size reaches batch size (50), flushes to SQLite.
- **flushEventsToDB()**: Copies buffer to a batch, clears buffer, and inserts batch into SQLite (`EventDatabaseHelper`) asynchronously.
- **forceFlushToDB()**: Triggers a flush so data is available for upload (e.g. before reading for Firestore upload).

### 2.3 EventDatabaseHelper (SQLite)

- **Table**: `events` with columns `id`, `event_name`, `event_data`.
- **event_name**: Unique document ID used later for Firestore (from `EventData`’s unique ID).
- **event_data**: JSON-serialized event map (time, time-local, type, plus module-specific fields).
- **getLimitedEvents(limit)**: Returns oldest events for upload (e.g. 300).
- **deleteEvent(id)**: Deletes after successful upload.

### 2.4 EventUploaderToFireStore

- **startUploadOfflineToOnlineEvents(IsUserTryingToLogout)**:
  - Calls `EventOperationManager.getInstance(context).forceFlushToDB()` then, after a short delay, reads from SQLite via `getLimitedEvents(300)`.
  - Subject ID from `UtilsForFirebaseSettings.getCodeAndNumber(context)`.
  - **Firestore paths**:
    - **events**: `users/{subjectId}/events/{eventName}` — document ID = event name from SQLite; document body = parsed event_data (map). Used for all event types except specs.
    - **specs**: `users/{subjectId}/specs/{eventName}` — same structure; used when event type is `android-specs` (SpecsEvent).
  - Batch write via Firestore `WriteBatch`; on success, deletes uploaded rows from SQLite. On logout, can keep uploading until DB is empty then delete DB.
- **uploadSingleEvent(eventName, eventData, context)**:
  - Writes one event directly to `users/{subjectId}/events/{GenerateEventId(eventName)}` with the given map.
  - Also updates ticker: builds ticker map (event name → timestamp + ticker string, and `MostRecentEventTime`), then `EventUploader.getInstance(context).uploadImmediately(map)` so ticker is written to Firestore.

### 2.5 Ticker (Firestore)

- **DataStorage** (in-memory): Map of event class names (and `MostRecentEventTime`) to “last occurrence” strings (timestamp + optional suffix from `UpdateTickerField`).
- **EventUploader** (c_DatabaseManager):
  - **uploadEvents()**: Every 5 seconds, if network OK and subject ID set, uploads `DataStorage.getEvents()` to **Firestore** `ticker/{subjectId}` with `SetOptions.merge()`.
  - On success, clears DataStorage and triggers `EventUploaderToFireStore.startUploadOfflineToOnlineEvents(false)` so SQLite events are uploaded.
  - **uploadImmediately(map)**: Writes the given map to `ticker/{subjectId}` with merge (used e.g. when sending a single notification event so ticker reflects it immediately).

So: **ticker** = real-time “latest event” summary per user; **users/{id}/events** (and **specs**) = durable event log.

### 2.6 Event Structure (EventData / EventMapBuilder)

- **Default fields** (from `EventMapBuilder.buildCompleteMap(additionalFields, type)`): `time` (GMT), `time-local`, `type`.
- **additionalFields**: Module-specific (e.g. `cause` for ScreenshotPauseEvent, `power` for SystemPowerEvent).
- **EventData.Builder**: Builds unique ID from className + timestamp + module id; adds time, timeLocal, type, and additional fields. Stored in SQLite as JSON; uploaded to Firestore as the same map.

### 2.7 Event Types and Characteristics (ModuleCharacteristics)

Defined in `ModuleCharacteristics` (c_ModuleManager). Each returns a map suitable for `addEvent()`:

- Location: `GPSLocationEvent`, type `location`
- Power: `ScreenOnOffEvent`, `system-power`; Alarm: `AlarmManagerNotificationEvent`, `alarm-manager-notification`
- Interactions: `InteractionEvent`; Network: `InternetEvent`; Steps: `StepCountEvent`; Battery: `BatteryStateEvent`, `BatteryChargingEvent`
- Specs: `SpecsEvent`, type `android-specs` (→ specs collection)
- Screenshot: `ScreenshotEvent`, `ScreenshotUploadEvent`, `ScreenshotFailureEvent`, `ScreenshotPauseEvent` (Paused/Resumed), `CaptureStartupEvent`, `LowMemoryEvent`
- Foreground app: `NewForegroundAppEvent`

`updateTicker` is `"1"` for most; `"0"` for e.g. screenshot failure and low memory so they don’t dominate the ticker.

---

## 3. Non-Text Pipeline: Screenshots (Firebase Storage)

### 3.1 Capture (m01_Screenshots)

- **ScreenshotsUpdater**: Uses MediaProjection and ImageReader; at each tick (interval from SettingsManager), captures a frame, encodes to JPEG (quality from `force-image-quality`), writes to `getExternalFilesDir(null)/screenshots/`. Posts screenshot/screenshot-failure events via ScreenshotsInfo and EventOperationManager.

### 3.2 Upload (ImageUploaderToCloudStorage)

- **Location**: `m01_Screenshots` (screenshots package). Triggered from `CaptureUploadService` (also in m01) at `data-nontext-upload-interval` (and respects `data-nontext-upload-wifi-only`, `kill-switch`).
- **Process**: Moves files from `.../screenshots/` to `.../uploadscreenshots/`, then uploads each file to **Firebase Storage**.
- **Storage path**: Root from `R.string.cloud_storage_url` (c_SharedResources). Child path: `{subjectId}/{filename}` — i.e. one folder per participant, file name = local file name.
- **Auth**: Uses Firebase Auth with subject ID and stored password to ensure the uploader is signed in.
- **Events**: Can report upload start/complete/failure via EventOperationManager (e.g. ScreenshotUploadEvent).

---

## 4. Firestore Dynamic Parameters (SettingsManager)

Sampling and upload intervals are **not** hardcoded; they are read from **SettingsManager**, which loads from:

1. **Local defaults** in `resetToLocalDefaults()` (e.g. `screenshot-interval`, `gps-location-interval`, `data-text-upload-interval`).
2. **Disk**: SharedPreferences keys prefixed with `setting_` (saved after fetch).
3. **Firestore**: `users/{subjectId}/settings/user_settings` — can be a snapshot listener. If `settings-group-override` is set, settings are cloned from **settings_profiles** (`_default_` then group code) into the user’s `user_settings` and saved.

**Key setting names** (used by app and modules):

- `screenshot-interval`, `screenshot-check-interval`, `screenshot-absolute-timing`
- `data-nontext-upload-interval`, `data-nontext-upload-wifi-only`
- `foreground-app-check-interval`, `gps-location-interval`, `gps-enabled`
- `pa-stepcounts-interval`, `pa-enabled`
- `data-text-upload-interval`, `data-text-upload-wifi-only`
- `specs-check-interval`, `settings-refresh-interval`
- `kill-switch`, `force-image-quality`

Access: `SettingsManager.get().getVal(name)` or `SettingsManager.val(name)`.

---

## 5. Summary: Where Data Is Reported

| Data | Destination | Path / structure |
|------|-------------|-------------------|
| Text events (all modules) | Firestore | `users/{subjectId}/events/{eventDocId}` (or `specs/{eventDocId}` for SpecsEvent) |
| Ticker (latest event times) | Firestore | `ticker/{subjectId}` (merge) |
| Screenshot images | Firebase Storage | `{storageRef}/{subjectId}/{filename}` |
| User settings (remote) | Firestore | `users/{subjectId}/settings/user_settings`; templates: `settings_profiles/_default_`, `settings_profiles/{groupCode}` |

All subject-based paths use the same subject identifier from `UtilsForFirebaseSettings.getCodeAndNumber(context)`.
