# TextBasedData

This folder manages **text-based (event) data** from all data collection modules and uploads to the **Firestore** database. Every module—including those that also collect non-text data (screenshots, audio)—uses this pipeline for event data.

- **Event data**: `EventOperationManager.getInstance(context).addEvent(moduleInfo, eventDetails)` → buffer → SQLite → Firestore `users/{subjectId}/events` (and optional ticker). Use for all structured events (e.g. "capture started", "upload success", sensor readings).
- **DatabaseHelper** (sibling folder) provides shared preferences and helpers used by both TextBasedData and NonTextBasedData.

Key classes: `EventOperationManager`, `EventDatabaseHelper`, `DataStorage`, `EventUploader`, `EventUploaderToFireStore`, `EventData`, `EventMapBuilder`, `HashMapPool`, `MapUtils`, `NetworkUtils`.
