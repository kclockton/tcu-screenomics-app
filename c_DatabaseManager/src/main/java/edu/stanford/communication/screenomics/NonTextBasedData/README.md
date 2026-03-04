# NonTextBasedData

This folder manages **non-text data** from non-text-based data collection modules (screenshots, audio, etc.) and uploads to **Cloud Storage**. It does **not** handle event data.

- **Event data** from all modules (including those that collect files) uses the **TextBasedData** folder: `EventOperationManager.addEvent(...)` → SQLite → Firestore. Use that for events like "capture started", "upload success".
- **File upload** is what this folder is for: use `CloudStorageUploadScheduler` with `CloudStorageUploadConfig` and your one-shot `Runnable` that uploads files to Storage. See `docs/08_NonText_Data_Pipeline.md` and `docs/09_Data_Pipeline_Adoption_Guide.md` (Part B).

Classes: `CloudStorageUploadConfig`, `CloudStorageUploadPolicy`, `CloudStorageUploadScheduler`.
