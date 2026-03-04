# Data Pipeline Adoption Guide for Developers

**Use this document** when you extend the app with a new data collection module. It gives **step-by-step instructions with examples** for:

- **Text-based event data pipeline** — structured events (key-value) → SQLite → Firestore; adopt via `EventOperationManager.addEvent(...)`, `ModuleCharacteristics`, and optional ticker/SettingsManager.
- **File upload to Cloud Storage** — for modules that write files (screenshots, audio, etc.), adopt a one-shot upload runnable + `CloudStorageUploadScheduler` and `CloudStorageUploadConfig` from c_DatabaseManager. Event data from those modules still uses the **text-based** pipeline (EventOperationManager).
- **gs:// Cloud Storage URI** — read from `c_SharedResources/config.xml`; verification and usage are described below.

---

## Table of Contents

0. [Which pipeline should I use?](#0-which-pipeline-should-i-use)
1. [Cloud Storage configuration (gs:// URI)](#1-cloud-storage-configuration-gs-uri)
2. [Part A: Text-based event data pipeline](#2-part-a-text-based-event-data-pipeline)
3. [Part B: File upload to Cloud Storage](#3-part-b-file-upload-to-cloud-storage-for-modules-that-collect-files)
4. [Complete examples](#4-complete-examples)
5. [Quick reference](#5-quick-reference)

---

## 0. Which pipeline should I use?

| Your data | Pipeline | Destination |
|-----------|----------|-------------|
| **Structured events** (key-value: counts, states, timestamps) | **Text-based** | SQLite → Firestore `users/{id}/events` + optional ticker |
| **Binary / files** (images, audio, video) | **File upload** (CloudStorageUpload) | Local files → Firebase Storage `gs://bucket/{subjectId}/{type}/` |

- **Text-based**: call `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)`. All modules use this for *event* data—including modules that also collect files (screenshots, audio), which use TextBasedData for events like "capture started", "upload success".
- **File upload**: for modules that write files to disk and upload to Storage, implement a **one-shot runnable** that uploads to Storage; use **CloudStorageUploadScheduler** + **CloudStorageUploadConfig** for interval and policy. Event data from those modules still goes through TextBasedData.

---

## 1. Cloud Storage configuration (gs:// URI)

### Where the gs:// URI comes from

Non-text uploads (screenshots, future audio, etc.) go to **Firebase Storage**. The bucket is configured via a **gs:// URI** so deployments can point to different buckets without code changes.

| Item | Location | Purpose |
|------|----------|---------|
| **Config value** | `c_SharedResources/src/main/res/values/config.xml` | Defines `<string name="cloud_storage_url">gs://your-bucket-name</string>`. |
| **Usage** | Your upload code (e.g. in m01 or a new module) | Call `context.getString(R.string.cloud_storage_url)` and pass it to `FirebaseStorage.getInstance(cloudStorageUrl).getReference()`. |

### Verification: gs:// is read from config correctly

- **Config:** `c_SharedResources/.../config.xml` defines `cloud_storage_url`. **m01:** `ImageUploaderToCloudStorage` imports `edu.stanford.communication.screenomics.sharedresources.R` and uses `context.getString(R.string.cloud_storage_url)` then `FirebaseStorage.getInstance(cloudStorageUrl).getReference()` — so gs:// is read from config.
- Your module must **depend on c_SharedResources** and use the same R (or the app’s merged R that includes this string) so the bucket comes from one place. For new modules: add `implementation project(':c_SharedResources')` and use the same R.

### How to verify

1. Open `c_SharedResources/src/main/res/values/config.xml`.
2. Confirm `<string name="cloud_storage_url">gs://…</string>` is set to your Firebase Storage bucket (e.g. `gs://my-project.appspot.com`).
3. In your upload runnable, after `getInstance(cloudStorageUrl).getReference()`, the reference is the root of that bucket; you then use `.child(codeAndNumber).child("screenshots").child(filename)` (or `"audio"`, etc.) to build the path.

### Checklist for a new non-text module

- [ ] Your module has `implementation project(':c_SharedResources')` so you can use `R.string.cloud_storage_url`.
- [ ] You read the bucket once per upload cycle (or cache it) with `context.getString(R.string.cloud_storage_url)`.
- [ ] You pass that string to `FirebaseStorage.getInstance(cloudStorageUrl).getReference()` and build the rest of the path under the user (e.g. `getReference().child(UtilsForFirebaseSettings.getCodeAndNumber(context)).child("audio")`).

---

## 2. Part A: Text-based event data pipeline

Use this pipeline when your module produces **structured events** (key-value data) that should be stored in SQLite and uploaded to **Firestore** (`users/{subjectId}/events` and optionally the ticker).

### Overview

```
Your module → EventOperationManager.addEvent(moduleInfo, eventDetails)
    → buffer → SQLite (batch) → EventUploaderToFireStore → Firestore users/.../events
    → (if updateTicker) DataStorage → ticker document
```

### Step 1: Register your event type in ModuleCharacteristics

**File:** `c_ModuleManager/src/main/java/edu/stanford/communication/screenomics/modulemanager/ModuleCharacteristics.java`

Add a getter that returns a map for your event type:

```java
public Map<String, String> getMyCustomEventCharacteristics() {
    return new ModuleCharacteristicsData("MyCustomEvent", "my-custom-event", "1").toMap();
}
```

- **First argument (className):** Used as event name and in document IDs (e.g. `"MyCustomEvent"`).
- **Second argument (type):** Stored in the event payload as `type` (e.g. `"my-custom-event"`).
- **Third argument (updateTicker):** `"1"` to update the ticker; `"0"` to skip.

**Example (existing):** Step count event

```java
public Map<String, String> getStepCountEventCharacteristics() {
    return new ModuleCharacteristicsData("StepCountEvent", "step-count", "1").toMap();
}
```

### Step 2: (Optional) Add ticker summary in EventOperationManager

**File:** `c_DatabaseManager/.../TextBasedData/EventOperationManager.java`  
**Method:** `UpdateTickerField(String ModuleInfo, HashMap<String, String> EventData)` (around line 145).  
**When:** Only if you used `updateTicker` `"1"` and want a custom ticker suffix. If you do not add a branch, the ticker suffix for your event will be empty (`""`).

Add a branch for your `className`:

```java
} else if (Objects.equals(moduleInfo.get("className"), "MyCustomEvent")) {
    result.append(" ").append(eventDetails.get("value"));  // or a short summary
} else {
```

If you don’t add a branch, the ticker may still show a default; the exact behavior depends on the existing logic.

### Step 3: Add SettingsManager keys (if your module has an interval)

**File:** `c_DatabaseManager/src/main/java/edu/stanford/communication/screenomics/FirebaseSettings/SettingsManager.java`  
**Method:** `resetToLocalDefaults()` (around line 121).

If your module runs on a timer (e.g. every N seconds), add a key and default value:

```java
settings.put("my-module-interval", 60 * 1000);  // 1 minute in ms
```

Your module will read it with `SettingsManager.val("my-module-interval")`.

### Step 4: Emit events from your module

**Where:** Your capture/callback code (e.g. in your new module or in ScreenMonitorService).

1. Get the module info map: `ModuleCharacteristics.getInstance().getMyCustomEventCharacteristics()`.
2. Build a map of event details (use `HashMapPool.getMap()` and `HashMapPool.releaseMap()` when done).
3. Call `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)`.

**Example: periodic sensor event**

```java
// In your capture class (e.g. MyCustomCapture.java)
Context context = getApplicationContext();
ModuleCharacteristics characteristics = ModuleCharacteristics.getInstance();
EventOperationManager eventManager = EventOperationManager.getInstance(context);

HashMap<String, String> details = HashMapPool.getMap();
details.put("value", String.valueOf(sensorValue));
details.put("unit", "custom_unit");

eventManager.addEvent(characteristics.getMyCustomEventCharacteristics(), details);
HashMapPool.releaseMap(details);
```

**Example: one-off event (e.g. from an alarm)**

For events that must be sent immediately (no SQLite buffer), use:

```java
EventUploaderToFireStore.getInstance(context).uploadSingleEvent(
    "MyCustomEvent",
    EventMapBuilder.buildCompleteMap(additionalFieldsMap, "my-custom-event"),
    context
);
```

### Step 5: Start your capture from the app (if periodic)

**Where:** `ScreenMonitorService` (or the service that hosts your module).

- If your module is gated by `ModuleController.ENABLE_*`, check that flag.
- Create your capture with interval from `SettingsManager.val("my-module-interval")`.
- Call `YourCollectionController.startCollecting(context)` (or equivalent) so the capture is started and stopped with the service.

**Example (concept):** Starting a hypothetical custom module

```java
// In startMonitoringComponents() or equivalent
if (ModuleController.ENABLE_MY_MODULE) {
    MyCustomCollectionController.startCollecting(this);
}
// In onDestroy()
MyCustomCollectionController.stopCollecting(this);
```

### Text-based pipeline checklist

- [ ] Added `getMyXxxEventCharacteristics()` in `ModuleCharacteristics` with className, type, updateTicker.
- [ ] (Optional) Added ticker branch in `EventOperationManager.UpdateTickerField` for your className.
- [ ] (Optional) Added interval/key in `SettingsManager.resetToLocalDefaults()`.
- [ ] Your module calls `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)` (or `uploadSingleEvent` for one-off).
- [ ] Your module is started/stopped from the right service (e.g. ScreenMonitorService) and respects `ModuleController` and permissions.

**See also:** `docs/EVENTS_PIPELINE.md` for full pipeline architecture and `docs/06_Developer_Guideline_New_Module.md` for module setup (manifest, permissions, etc.).

---

## 3. Part B: File upload to Cloud Storage (for modules that collect files)

Use this when your module produces **files** (screenshots, audio, etc.) that must be uploaded to **Firebase Storage**. These modules still use **TextBasedData** for all *event* data (same structure as other modules); only the *file* upload uses the CloudStorageUpload package.

### Overview

```
CloudStorageUploadScheduler (interval + policy) → your one-shot Runnable
    → move files to staging (optional) → upload each file to Storage (gs:// bucket from config)
    → report events via EventOperationManager (text-based event pipeline, same as all modules)
```

### Step 1: Ensure Cloud Storage config (gs://)

- **Config file:** `c_SharedResources/src/main/res/values/config.xml` — must define `<string name="cloud_storage_url">gs://your-bucket-name</string>`.
- **In code:** Your module must depend on **c_SharedResources** and use **R** from that module: `import edu.stanford.communication.screenomics.sharedresources.R;` then `context.getString(R.string.cloud_storage_url)` when initializing Firebase Storage. See [§1](#1-cloud-storage-configuration-gs-uri) and the verification notes there.

### Step 2: Choose upload settings (shared vs custom)

**Folder:** `c_DatabaseManager/.../NonTextBasedData/`. **Classes:** `CloudStorageUploadConfig.java`, `CloudStorageUploadPolicy.java`, `CloudStorageUploadScheduler.java`.

- **Shared (same as screenshots):** Use `CloudStorageUploadConfig.DEFAULT`. It uses:
  - `data-nontext-upload-interval`
  - `data-nontext-upload-wifi-only`
  - `kill-switch`
  These are already in SettingsManager. No extra keys needed.

- **Custom (e.g. audio with its own interval):** Create a config and add keys to SettingsManager:

```java
CloudStorageUploadConfig audioConfig = new CloudStorageUploadConfig(
    "audio-upload-interval",
    "audio-upload-wifi-only",
    "kill-switch"
);
```

In `SettingsManager.resetToLocalDefaults()`:

```java
settings.put("audio-upload-interval", 2 * 60 * 1000);   // 2 minutes
settings.put("audio-upload-wifi-only", 1);
```

### Step 3: Implement a one-shot upload Runnable

Your runnable must perform **exactly one** upload cycle. It must **not** call `handler.postDelayed(this, ...)` or reschedule itself; the scheduler does that.

**Responsibilities of your runnable:**

1. (Optional) Check `uploadInProgress` to avoid overlapping runs.
2. Get user identity (e.g. `LogInPreference.GetUserSubjId()`, `UtilsForFirebaseSettings.getCodeAndNumber(context)`).
3. Get Firebase Storage reference: `FirebaseStorage.getInstance(context.getString(R.string.cloud_storage_url)).getReference().child(codeAndNumber).child("your-type")`.
4. List files to upload (e.g. from a staging directory).
5. For each file: upload to Storage, then delete or move the file; optionally report success/failure via `EventOperationManager.addEvent(...)`.
6. When done, clear `uploadInProgress` (if you use it).

**Minimal example structure (pseudocode):**

```java
public class MyNonTextUploader implements Runnable {
    private final Context context;
    private volatile boolean uploadInProgress = false;

    @Override
    public void run() {
        if (uploadInProgress) return;
        uploadInProgress = true;
        try {
            String bucketUrl = context.getString(R.string.cloud_storage_url);
            StorageReference rootRef = FirebaseStorage.getInstance(bucketUrl).getReference();
            String codeAndNumber = UtilsForFirebaseSettings.getCodeAndNumber(context);
            if (TextUtils.isEmpty(codeAndNumber)) return;

            StorageReference userRef = rootRef.child(codeAndNumber).child("my-data-type");
            File stagingDir = new File(context.getExternalFilesDir(null), "my-staging");
            File[] files = stagingDir.listFiles();
            if (files == null) return;

            for (File file : files) {
                StorageReference ref = userRef.child(file.getName());
                ref.putFile(Uri.fromFile(file)).addOnSuccessListener(...).addOnFailureListener(...);
                // optionally: EventOperationManager.getInstance(context).addEvent(...);
            }
        } finally {
            uploadInProgress = false;
        }
    }
}
```

**Real example:** See `m01_Screenshots/.../ImageUploaderToCloudStorage.java`: it implements one cycle (login check, move files to staging, upload each file to Storage, report events). It no longer reschedules itself; the scheduler does.

### Step 4: Wire the scheduler in your capture/service

Create the scheduler with your one-shot runnable and post it once (e.g. when your capture starts).

**Example (screenshots):**

```java
// In ScreenshotsUpdater (or wherever upload is started)
uploader = new ImageUploaderToCloudStorage(context, handler, useWifi, notTextInterval, KillSwitch);
CloudStorageUploadScheduler scheduler = new CloudStorageUploadScheduler(
    context,
    handler,
    CloudStorageUploadConfig.DEFAULT,
    uploader
);
handler.postDelayed(scheduler, notTextInterval);  // first run after delay
```

**Example (hypothetical audio module):**

```java
audioUploader = new AudioUploaderToCloudStorage(context, handler);
CloudStorageUploadScheduler scheduler = new CloudStorageUploadScheduler(
    context,
    handler,
    CloudStorageUploadConfig.DEFAULT,   // or your custom CloudStorageUploadConfig
    audioUploader
);
handler.post(scheduler);  // or postDelayed(scheduler, initialDelayMs);
```

### Step 5: (Optional) Report upload events as text events

So that “upload started”, “upload completed”, “upload failed” appear in Firestore, call the **text-based** pipeline from your upload runnable:

```java
EventOperationManager.getInstance(context).addEvent(
    ModuleCharacteristics.getInstance().getScreenshotUploadEventCharacteristics(),
    EventMapBuilder.buildCompleteMap(uploadDetailsMap, "screenshot-upload")
);
```

For a new event type, add a getter in ModuleCharacteristics (e.g. `getAudioUploadEventCharacteristics()`) and use it here.

### Non-text pipeline checklist

- [ ] gs:// URI is set in `c_SharedResources/config.xml` and your module uses `context.getString(R.string.cloud_storage_url)`.
- [ ] One-shot runnable does **not** reschedule itself; it only does one upload cycle.
- [ ] You use `CloudStorageUploadScheduler(context, handler, config, yourRunnable)` and post it once (or with initial delay).
- [ ] Config is either `CloudStorageUploadConfig.DEFAULT` or a custom config with keys added to SettingsManager.
- [ ] (Optional) You report upload lifecycle events via `EventOperationManager.addEvent(...)`.

**See also:** `docs/08_NonText_Data_Pipeline.md` and `docs/06_Developer_Guideline_New_Module.md`.

---

## 4. Complete examples

### Example A: Adding a new text-based event type (e.g. "TemperatureEvent")

Assume you are adding a module that periodically records a temperature reading and should send it through the text pipeline.

**1. Register the event** in `c_ModuleManager/.../ModuleCharacteristics.java`:

```java
public Map<String, String> getTemperatureEventCharacteristics() {
    return new ModuleCharacteristicsData("TemperatureEvent", "temperature", "1").toMap();
}
```

**2. (Optional) Ticker** in `c_DatabaseManager/.../EventOperationManager.java`, inside `UpdateTickerField()`, add:

```java
} else if (Objects.equals(ModuleInfo, "TemperatureEvent")) {
    result.append(EventData.get("celsius"));
} else {
```

**3. Add interval** in `c_DatabaseManager/.../SettingsManager.java` in `resetToLocalDefaults()`:

```java
settings.put("temperature-check-interval", 60 * 1000);  // 1 minute
```

**4. Emit from your capture class** (e.g. in your module or in ScreenMonitorService):

```java
HashMap<String, String> details = HashMapPool.getMap();
details.put("celsius", String.valueOf(readTemperature()));
EventOperationManager.getInstance(context).addEvent(
    ModuleCharacteristics.getInstance().getTemperatureEventCharacteristics(),
    details
);
HashMapPool.releaseMap(details);
```

**5. Start/stop** from the service that hosts your module (e.g. `YourCollectionController.startCollecting(context)` in `startMonitoringComponents()` and `stopCollecting(context)` in `onDestroy()`).

---

### Example B: Adding a new non-text upload (e.g. audio)

Assume you are adding a module that records audio files and uploads them to Firebase Storage using the same policy as screenshots.

**1. Config:** Ensure `c_SharedResources/.../config.xml` has `<string name="cloud_storage_url">gs://your-bucket</string>`.

**2. One-shot runnable** (skeleton). Your class must **not** call `handler.postDelayed(this, ...)`; the scheduler does that.

```java
import edu.stanford.communication.screenomics.sharedresources.R;

public class AudioUploaderToCloudStorage implements Runnable {
    private final Context context;
    private volatile boolean uploadInProgress = false;

    @Override
    public void run() {
        if (uploadInProgress) return;
        uploadInProgress = true;
        try {
            String codeAndNumber = UtilsForFirebaseSettings.getCodeAndNumber(context);
            if (TextUtils.isEmpty(codeAndNumber)) return;
            String bucketUrl = context.getString(R.string.cloud_storage_url);
            StorageReference userRef = FirebaseStorage.getInstance(bucketUrl)
                .getReference().child(codeAndNumber).child("audio");
            File stagingDir = new File(context.getExternalFilesDir(null), "audio_staging");
            File[] files = stagingDir.listFiles();
            if (files == null) return;
            for (File file : files) {
                userRef.child(file.getName()).putFile(Uri.fromFile(file))
                    .addOnSuccessListener(...)
                    .addOnFailureListener(...);
            }
        } finally {
            uploadInProgress = false;
        }
    }
}
```

**3. Wire the scheduler** where you start your capture (e.g. in your service or capture class):

```java
AudioUploaderToCloudStorage audioUploader = new AudioUploaderToCloudStorage(context);
CloudStorageUploadScheduler scheduler = new CloudStorageUploadScheduler(
    context,
    handler,
    CloudStorageUploadConfig.DEFAULT,   // uses data-nontext-upload-interval, etc.
    audioUploader
);
handler.postDelayed(scheduler, SettingsManager.val("data-nontext-upload-interval"));
```

**4. (Optional)** Report upload events via `EventOperationManager.addEvent(...)` with a new getter in ModuleCharacteristics (e.g. `getAudioUploadEventCharacteristics()`).

---

## 5. Quick reference

| Goal | Use |
|------|-----|
| Emit a structured event (buffer → SQLite → Firestore) | `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)` |
| Emit a one-off event immediately to Firestore | `EventUploaderToFireStore.getInstance(context).uploadSingleEvent(eventName, map, context)` |
| Register a new event type | Add getter in `ModuleCharacteristics` using `ModuleCharacteristicsData(className, type, updateTicker)` |
| Upload files to Cloud Storage on a schedule | Implement a one-shot `Runnable`, wrap with `CloudStorageUploadScheduler` + `CloudStorageUploadConfig`, post once |
| Get the Storage bucket (gs://) | `context.getString(R.string.cloud_storage_url)` from c_SharedResources |
| Check if file upload to Storage is allowed (kill-switch, wifi, login) | `CloudStorageUploadPolicy.shouldRunUpload(context, config)` |

---

*For full pipeline architecture, see **EVENTS_PIPELINE.md**. For new-module setup (manifest, permissions, ModuleController), see **06_Developer_Guideline_New_Module.md**. For gs:// config, see [§1](#1-cloud-storage-configuration-gs-uri) and [§4 Example B](#example-b-adding-a-new-non-text-upload-eg-audio).*
