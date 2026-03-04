# File upload to Cloud Storage (screenshots, audio, etc.)

**Important:** Data collection modules that collect **non-text data** (images, audio) still use the **same text-based event data pipeline** as all other modules for their *event* data (e.g. "capture started", "upload success"). They use **TextBasedData** (EventOperationManager, ModuleCharacteristics, etc.) for those events. Only the **file upload** to Firebase Storage uses the **CloudStorageUpload** package.

Non-text *data* (files) is collected by feature modules and uploaded to **Firebase Storage** under the user path. The **CloudStorageUpload** package in c_DatabaseManager provides a **standardized mechanism** for the *upload* scheduling and policy only.

## Pipeline components (c_DatabaseManager / NonTextBasedData folder; classes: CloudStorageUpload*)

| Component | Role |
|-----------|------|
| **CloudStorageUploadConfig** | Which SettingsManager keys to use for upload interval, wifi-only, and kill-switch. Use `DEFAULT` for the shared pipeline (screenshots) or create a custom config for a new type (e.g. audio with `audio-upload-interval`). |
| **CloudStorageUploadPolicy** | `shouldRunUpload(context, config)` — central check: kill-switch off, network/wifi ok, user logged in. |
| **CloudStorageUploadScheduler** | Runnable that runs at the configured interval, checks the policy, then runs the module's **one-shot upload runnable**. |

## How modules use it

1. **Event data** (text): Use **TextBasedData** — `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)` for "upload started", "screenshot taken", etc., same as every other module.
2. **File data**: Collect files into a local directory (e.g. `.../screenshots/`, `.../audio/`).
3. Implement a **one-shot runnable** that: moves files to staging (if needed), uploads to Firebase Storage, and optionally reports events via `EventOperationManager`.
4. **Start the upload pipeline**: create `CloudStorageUploadScheduler(context, handler, config, yourOneShotRunnable)` and post it (e.g. `handler.postDelayed(scheduler, initialDelayMs)`).

The scheduler handles rescheduling and policy; the module does not reschedule itself.

## Current adoption

- **m01_Screenshots**: Event data (ScreenshotEvent, ScreenshotUploadEvent, etc.) goes through **TextBasedData**. File upload uses `ImageUploaderToCloudStorage` (one-shot runnable) and `CloudStorageUploadScheduler` with `CloudStorageUploadConfig.DEFAULT`. Settings: `data-nontext-upload-interval`, `data-nontext-upload-wifi-only`, `kill-switch`.

## Adding a new type that uploads files (e.g. audio)

See **09_Data_Pipeline_Adoption_Guide.md** (Part B and §4 Example B). Register your *event* types in ModuleCharacteristics and use EventOperationManager for events; for *file* upload, implement a one-shot runnable, read the gs:// bucket from `R.string.cloud_storage_url`, and wrap with `CloudStorageUploadScheduler` and `CloudStorageUploadConfig.DEFAULT` (or a custom config).
