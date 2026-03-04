# Events Data Pipeline — Developer Guide

This document describes how **text-based events** are added, stored, and uploaded in the Screenomics app. Use it to implement new event types and keep the same pipeline structure.

**Step-by-step adoption:** For a concise checklist and code examples when adding a new event type, see **09_Data_Pipeline_Adoption_Guide.md** (Part A: Text-based event data pipeline).

---

## Table of Contents

1. [Overview](#1-overview)
2. [Pipeline Architecture](#2-pipeline-architecture)
3. [Key Components](#3-key-components)
4. [Adding a New Event Type (Standard Method)](#4-adding-a-new-event-type-standard-method)
5. [Event Flow: From Module to Firebase](#5-event-flow-from-module-to-firebase)
6. [Two Ways to Emit Events](#6-two-ways-to-emit-events)
7. [Firebase Destinations](#7-firebase-destinations)
8. [Data Shapes and Conventions](#8-data-shapes-and-conventions)
9. [Threading, Batching, and Timing](#9-threading-batching-and-timing)
10. [Offline and Low-Memory Behavior](#10-offline-and-low-memory-behavior)
11. [Checklist for New Events](#11-checklist-for-new-events)
12. [Reference: Class and File Locations](#12-reference-class-and-file-locations)

---

## 1. Overview

Events are **text-based, key-value payloads** (e.g., step count, screenshot taken, login, battery state). They are:

- **Added** by feature modules (screenshots, locations, steps, etc.) via a single entry point.
- **Buffered** in memory, then written to **SQLite** in batches.
- **Uploaded** to Firebase in two places:
  - **Ticker** document: latest “summary” timestamps per event type (for dashboards).
  - **`users/{subjectId}/events`** collection: full event documents (for analysis).

The pipeline is designed for **offline-first** behavior: events are persisted locally and uploaded when the network is available.

---

## 2. Pipeline Architecture

High-level flow:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  MODULES (e.g. ScreenshotsUpdater, ActivitiesUpdater, LocationsUpdater)        │
│  Call: EventOperationManager.getInstance(context).addEvent(moduleInfo, map)  │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  EventOperationManager                                                       │
│  • Builds EventData (type, uniqueId, time, time-local, + your fields)        │
│  • Adds to in-memory eventBuffer                                             │
│  • If updateTicker=1: also writes to DataStorage (ticker summary)            │
│  • When buffer size ≥ BATCH_SIZE (50) or every 60s: flushEventsToDB()       │
└─────────────────────────────────────────────────────────────────────────────┘
                    │                                    │
                    │ (flush)                            │ (if updateTicker)
                    ▼                                    ▼
┌──────────────────────────────┐    ┌──────────────────────────────────────┐
│  EventDatabaseHelper          │    │  DataStorage (in-memory)              │
│  SQLite: events table         │    │  eventName → "timestamp [extra]"      │
│  (event_name, event_data)     │    │  Used for ticker document             │
└──────────────────────────────┘    └──────────────────────────────────────┘
                    │                                    │
                    │                                    │
                    ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  UPLOAD LAYER                                                                │
│  • EventUploader: every 5s uploads DataStorage → Firestore ticker/{subjectId}│
│  • On ticker success: EventUploaderToFireStore.startUploadOfflineToOnline…  │
│  • EventUploaderToFireStore: reads SQLite (e.g. 300 events), batch uploads   │
│    to users/{subjectId}/events/{eventDocumentId}, then deletes from SQLite  │
└─────────────────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  FIREBASE                                                                    │
│  • ticker/{subjectId}        — merge of latest event timestamps (ticker)     │
│  • users/{subjectId}/events/{docId} — one document per event (full payload)  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Components

| Component | Package / Class | Role |
|-----------|------------------|------|
| **EventOperationManager** | `TextBasedData.EventOperationManager` | Single entry point for adding events: buffer, optional ticker update, batch flush to SQLite. |
| **EventData** | `TextBasedData.EventData` | Immutable event model: `time`, `time-local`, `type`, `UniqueEventId`, plus custom fields. Built via `EventData.Builder`. |
| **EventDatabaseHelper** | `TextBasedData.EventDatabaseHelper` | SQLite helper: table `events` with `id`, `event_name`, `event_data` (JSON). |
| **DataStorage** | `TextBasedData.DataStorage` | In-memory map of event name → ticker string; fed to ticker upload. |
| **EventUploader** | `TextBasedData.EventUploader` | Periodically (every 5s) uploads DataStorage to `ticker/{subjectId}`; on success triggers offline-events upload. |
| **EventUploaderToFireStore** | `TextBasedData.EventUploaderToFireStore` | Uploads SQLite events to `users/{subjectId}/events` in batches; supports `uploadSingleEvent` for immediate one-off events. |
| **ModuleCharacteristics** | `modulemanager.ModuleCharacteristics` | Supplies per–event-type metadata: `className`, `type`, `updateTicker`. |
| **ModuleCharacteristicsData** | `modulemanager.ModuleCharacteristicsData` | Builds the `moduleInfo` map: `className`, `type`, `updateTicker`, `id`, `timestamp`, `timestampLocal`. |

---

## 4. Adding a New Event Type (Standard Method)

To add a new event so it follows the same pipeline as existing ones:

### Step 1: Register the event in ModuleCharacteristics

In `c_ModuleManager/.../ModuleCharacteristics.java`, add a getter that returns a `Map<String, String>` built from `ModuleCharacteristicsData`:

```java
public Map<String, String> getMyNewEventCharacteristics() {
    return new ModuleCharacteristicsData("MyNewEvent", "my-new-event", "1").toMap();
}
```

- **First argument (`className`)**: logical event name used as document ID prefix and in ticker (e.g. `"MyNewEvent"`).
- **Second argument (`type`)**: category string stored in the event payload (e.g. `"my-new-event"`).
- **Third argument (`updateTicker`)**: `"1"` if this event should update the ticker document; `"0"` otherwise.

### Step 2: Add ticker summary logic (if updateTicker is "1")

If your event type updates the ticker, add a case in `EventOperationManager.UpdateTickerField()` so the ticker string is meaningful:

```java
} else if (Objects.equals(ModuleInfo, "MyNewEvent")) {
    result.append(EventData.get("yourKey"));  // or a short summary
} else {
```

This controls what appears after the timestamp in the ticker (e.g. `"2025-02-13 10:00:00 (PT) [yourKey]"`).

### Step 3: Emit the event from your module

Where your feature detects the condition (e.g. sensor callback, broadcast, timer):

1. Build a `HashMap<String, String>` with the event fields (prefer `HashMapPool.getMap()` and `HashMapPool.releaseMap()` when done).
2. Call `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)`.

Example (pattern used in `ActivitiesUpdater`, `LocationsUpdater`, `SpecsUpdater`, etc.):

```java
HashMap<String, String> payload = HashMapPool.getMap();
payload.put("count", String.valueOf(steps));
// add any other keys your event needs

EventOperationManager.getInstance(context).addEvent(
    ModuleCharacteristics.getInstance().getStepCountEventCharacteristics(),
    payload
);

HashMapPool.releaseMap(payload);
```

Required keys are **not** enforced in code; the pipeline adds `time`, `time-local`, `type`, and a unique document ID. Your map should contain **all other fields** you want in the event document.

### Step 4: (Optional) Use EventMapBuilder for standard time/type fields

For events that need the same shape as login/logout or screenshot pause (e.g. `time`, `time-local`, `type`), you can use:

```java
HashMap<String, String> eventData = EventMapBuilder.buildCompleteMap(additionalFields, "your-type");
```

Then pass `eventData` as the second argument to `addEvent` (or to `uploadSingleEvent` if you use the immediate path).

---

## 5. Event Flow: From Module to Firebase

### 5.1 When you call `addEvent(moduleInfo, eventDetails)`

1. **EventOperationManager** builds an `EventData` via `EventData.Builder(moduleInfo.get("type"), moduleInfo.get("className"))` and `.addFields(eventDetails)`. The builder adds:
   - `time` (GMT)
   - `time-local` (system clock)
   - `type` (from moduleInfo)
   - `UniqueEventId` = `className + " " + timestamp + "_" + locationId` (used as Firestore document ID and SQLite `event_name`).
2. The event is appended to **eventBuffer**.
3. If `moduleInfo.get("updateTicker")` is `"1"`:
   - `DataStorage.getInstance().addEvent("MostRecentEventTime", ...)` is called.
   - `DataStorage.getInstance().addEvent(className, timestamp + UpdateTickerField(...))` is called.
4. If `eventBuffer.size() >= BATCH_SIZE` (50), **flushEventsToDB()** is invoked: events are serialized with `MapUtils.serializeMap(event.toMap())`, then inserted into SQLite via `EventDatabaseHelper.insertEvent(uniqueEventId, json)`.

### 5.2 Periodic flush

A handler runs every `PERIODIC_FLUSH_INTERVAL_MS` (60 seconds). If the buffer is not empty, it calls **flushEventsToDB()**, so events reach SQLite even when volume is low.

### 5.3 From SQLite to Firestore

- **EventUploader** runs every 5 seconds. It uploads the current **DataStorage** map to `ticker/{subjectId}` with `SetOptions.merge()`. After a successful write it clears DataStorage and calls **EventUploaderToFireStore.startUploadOfflineToOnlineEvents(false)**.
- **EventUploaderToFireStore** reads up to 300 events from SQLite (`getLimitedEvents(300)`), batches them into a Firestore `WriteBatch`, and writes each event as a document in `users/{subjectId}/events` with document ID = `event_name` (the UniqueEventId). On success it deletes those rows from SQLite.

So the **standard path** is: module → buffer → SQLite → (triggered after ticker upload) → `users/.../events`.

---

## 6. Two Ways to Emit Events

### 6.1 Buffered path (normal): `EventOperationManager.addEvent(...)`

- Use for: high-volume or non–time-critical events (screenshots, steps, location, interactions, battery, network, etc.).
- Flow: in-memory buffer → batch write to SQLite → upload to Firestore when ticker runs and then offline upload runs.
- Supports offline: events sit in SQLite until upload succeeds.

### 6.2 Immediate path: `EventUploaderToFireStore.uploadSingleEvent(...)`

- Use for: one-off, important events that should be sent as soon as possible (e.g. login, logout, screenshot pause/resume).
- Requires network: if there is no internet, the call returns without persisting to SQLite (no offline queue in this path).
- It:
  - Writes the event document to `users/{subjectId}/events/{GenerateEventId(eventName)}`.
  - Pushes the same summary to the ticker via `EventUploader.getInstance(context).uploadImmediately(...)`.

Example (login):

```java
EventUploaderToFireStore.getInstance(context).uploadSingleEvent(
    "LogInOutEvent",
    EventMapBuilder.buildCompleteMap(null, "login"),
    context
);
```

Use **addEvent** for everything that should be durable and batched; use **uploadSingleEvent** only when you need immediate visibility and can tolerate no offline queue.

---

## 7. Firebase Destinations

| Destination | Path | Content | Updated by |
|-------------|------|---------|------------|
| **Ticker** | `ticker/{subjectId}` | One document per subject; fields are event class names (e.g. `ScreenshotEvent`, `MostRecentEventTime`) mapping to a string like `"2025-02-13 10:00:00 (PT) [extra]"`. | EventUploader (periodic + uploadImmediately), and when uploadSingleEvent runs. |
| **Events collection** | `users/{subjectId}/events/{documentId}` | One document per event. `documentId` = `className + " " + timestamp + "_" + locationId`. Document body = serialized event map (time, time-local, type, plus your fields). | EventUploaderToFireStore (batch from SQLite, or uploadSingleEvent). |

Subject ID comes from `UtilsForFirebaseSettings.getCodeAndNumber(context)`.

---

## 8. Data Shapes and Conventions

### 8.1 EventData / event document

- **time**: GMT timestamp string (from EventTimestamp).
- **time-local**: Device local time string.
- **type**: From module characteristics (e.g. `"screenshot"`, `"step-count"`).
- **UniqueEventId**: Generated in builder; used as Firestore document ID and SQLite `event_name`.
- **Additional fields**: Everything you put in the `HashMap` passed to `addEvent` (or in the map passed to `uploadSingleEvent`).

All values are strings in the pipeline and in Firestore for these event documents.

### 8.2 Module info map (from ModuleCharacteristicsData)

- **className**: Event name used in ticker and as part of document ID (e.g. `"StepCountEvent"`).
- **type**: Event type in payload (e.g. `"step-count"`).
- **updateTicker**: `"1"` or `"0"`.
- **id**, **timestamp**, **timestampLocal**: From ModuleCharacteristicsData (e.g. for module instance identification).

### 8.3 Ticker document

- Keys: event class names (e.g. `MostRecentEventTime`, `ScreenshotEvent`, `LogInOutEvent`).
- Values: human-readable timestamp plus optional suffix from `UpdateTickerField` (e.g. `"2025-02-13 10:00:00 (PT) [Paused]"`).

---

## 9. Threading, Batching, and Timing

- **EventOperationManager**: All buffer access and flush decisions are under a single `LOCK`. Writes to SQLite are done on a single-threaded executor to avoid blocking the caller.
- **Batch size**: Events are flushed to SQLite when the buffer size reaches **50** or when the 60-second periodic run executes.
- **EventUploader**: Runs on the main looper every **5 seconds**; uses a lock so only one upload runs at a time.
- **EventUploaderToFireStore**: Uses an atomic “uploading” flag; reads at most **300** events per run from SQLite and uploads them in one batch. After success, those rows are deleted.

---

## 10. Offline and Low-Memory Behavior

- **Offline**: Events added via **addEvent** are written to SQLite. Ticker and Firestore uploads are skipped when `NetworkUtils.isInternetAvailable(context)` is false. When the network returns, the next ticker cycle can run and then trigger `startUploadOfflineToOnlineEvents`, which drains SQLite into `users/.../events`.
- **Low memory**: `EventUploaderToFireStore.SetIsMemoryLow(true)` is used to pause the offline-to-online upload (e.g. from CaptureUploadService). When memory is normal again, set it to false so uploads can resume.
- **Logout**: On logout, the app may call `startUploadOfflineToOnlineEvents(true)` to try to upload remaining SQLite events before clearing or deleting the database.

---

## 11. Checklist for New Events

- [ ] Add a getter in **ModuleCharacteristics** that returns `ModuleCharacteristicsData(className, type, updateTicker).toMap()`.
- [ ] If `updateTicker` is `"1"`, add a branch in **EventOperationManager.UpdateTickerField** for your `className`.
- [ ] In your module, build a `HashMap<String, String>` of payload fields (use **HashMapPool** where appropriate).
- [ ] Call **EventOperationManager.getInstance(context).addEvent(moduleInfo, payload)** (or **uploadSingleEvent** only if you need immediate, online-only behavior).
- [ ] If you used a pooled map, call **HashMapPool.releaseMap(payload)** after `addEvent`.
- [ ] Ensure **EventUploader** is started (e.g. in **ScreenMonitorService.initializeComponents()** via `EventUploader.getInstance(this).startUploading()`), so ticker and offline events are uploaded.

---

## 12. Reference: Class and File Locations

| Class | Module / Path |
|-------|----------------|
| EventOperationManager | c_DatabaseManager / `.../TextBasedData/EventOperationManager.java` |
| EventData | c_DatabaseManager / `.../TextBasedData/EventData.java` |
| EventDatabaseHelper | c_DatabaseManager / `.../TextBasedData/EventDatabaseHelper.java` |
| DataStorage | c_DatabaseManager / `.../TextBasedData/DataStorage.java` |
| EventUploader | c_DatabaseManager / `.../TextBasedData/EventUploader.java` |
| EventUploaderToFireStore | c_DatabaseManager / `.../TextBasedData/EventUploaderToFireStore.java` |
| ModuleCharacteristics | c_ModuleManager / `.../modulemanager/ModuleCharacteristics.java` |
| ModuleCharacteristicsData | c_ModuleManager / `.../modulemanager/ModuleCharacteristicsData.java` |
| EventMapBuilder | c_DatabaseManager / `.../TextBasedData/EventMapBuilder.java` |
| MapUtils | c_DatabaseManager / `.../TextBasedData/MapUtils.java` |
| HashMapPool | c_DatabaseManager / `.../TextBasedData/HashMapPool.java` |

Upload lifecycle is started in **ScreenMonitorService** (`app` module) when the capture service starts; **EventUploaderToFireStore** is also used from **CaptureUploadService**, **LoginActivity**, and **Utils** (e.g. logout, screenshot pause).

---

This pipeline ensures that all text-based events are recorded consistently, survive app restarts via SQLite, and appear in both the ticker (for monitoring) and in `users/{subjectId}/events` (for analysis). New event types should follow the same path: register in ModuleCharacteristics, optionally support the ticker, and call **addEvent** (or **uploadSingleEvent** only when appropriate).
