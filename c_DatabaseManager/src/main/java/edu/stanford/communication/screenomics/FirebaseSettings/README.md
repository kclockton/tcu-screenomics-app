# FirebaseSettings (shared)

This folder is **shared infrastructure**. It is used by **TextBasedData**, **NonTextBasedData**, the **app**, and **feature modules**—not owned by either text-based or non-text-based pipelines.

## What this folder provides

| Class | Purpose |
|-------|--------|
| **SettingsManager** | Firestore-backed key-value settings. Holds keys for **both** pipelines (e.g. `data-text-upload-interval`, `data-text-upload-wifi-only` for text; `data-nontext-upload-interval`, `data-nontext-upload-wifi-only`, `kill-switch` for non-text), plus module flags (e.g. `gps-enabled`, `pa-enabled`) and intervals (screenshot, specs, etc.). Load/save from Firestore and local defaults. |
| **UtilsForFirebaseSettings** | Subject and path helpers: `getCodeAndNumber(context)`, `getSubjectId(context)`, `getGroupCode(context)`. Used for Firestore paths (`users/{id}/...`) and Cloud Storage paths. |
| **FirebaseManagerSingleton** | Single Firestore instance: `getFirestore()`. Used for events, ticker, settings, and app-level Firestore access. |
| **ServerTimeSync** | Fetches Firebase server time (e.g. at app startup) so event timestamps are consistent. |

## Who uses it

- **TextBasedData**: EventUploader, EventUploaderToFireStore (Firestore, subject ID); NetworkUtils (text-upload settings).
- **NonTextBasedData**: CloudStorageUploadScheduler (interval from config); CloudStorageUploadPolicy (kill-switch, wifi-only from config). Modules like m01 use UtilsForFirebaseSettings for Storage paths.
- **DatabaseHelper**: SettingsManager uses LogInPreference for local persistence.
- **App**: Permission screens, RefreshSettings, ScreenMonitorService, LoginActivity, ScreenomicsApplication (Firestore init, ServerTimeSync).
- **Feature modules**: m01, m02, m04, m05, m07, m09 (intervals, flags, subject ID as needed).

Because FirebaseSettings is shared, it remains a **separate folder** in c_DatabaseManager alongside TextBasedData, NonTextBasedData, and DatabaseHelper.
