# Stanford Screenomics — Documentation Index

This folder contains detailed documentation for the app: behaviors, data pipeline, module structure, and developer guidelines.

---

## 1. App Behaviors

| Document | Audience | Description |
|----------|----------|-------------|
| [01_App_Behaviors_Developer.md](01_App_Behaviors_Developer.md) | Developers | Services, alarms (incl. SCHEDULE_EXACT_ALARM), pause/resume logic, permissions, and key files from an implementation perspective. |
| [02_App_Behaviors_Managers_Researchers.md](02_App_Behaviors_Managers_Researchers.md) | App managers / Researchers | What the app does for participants: data collection, pause/resume, notifications, popups, and study-design implications. |

---

## 2. Data Collection and Processing / Transfer Pipeline

| Document | Audience | Description |
|----------|----------|-------------|
| [03_Data_Pipeline_Developer.md](03_Data_Pipeline_Developer.md) | Developers | End-to-end flow: event buffering, SQLite, Firestore events/ticker, Storage upload, SettingsManager, and event structure. |
| [04_Data_Pipeline_Managers_Researchers.md](04_Data_Pipeline_Managers_Researchers.md) | App managers / Researchers | What data is collected, where it is sent (Firestore vs Storage), and how settings affect behavior. |

---

## 3. Module and File Relationship

| Document | Audience | Description |
|----------|----------|-------------|
| [05_Module_And_File_Relationship.md](05_Module_And_File_Relationship.md) | Developers / Architects | Map of modules and key files to functionality; dependency flow; who uses ModuleController, ModuleCharacteristics, SettingsManager, Firestore, and Storage. |
| [10_App_Level_Structure_Assessment.md](10_App_Level_Structure_Assessment.md) | Developers | App-level package layout, applied refactors (Pref→AppPreferences, NotificationHelper, permission activities, RefreshSettings, Utils split), and optional follow-ups. |
| [11_Notification_And_MediaProjection_Mechanism.md](11_Notification_And_MediaProjection_Mechanism.md) | Developers | How POST_NOTIFICATIONS, notification listener, and media projection are requested, checked, and used; permission flow order; media projection result flow. |

---

## 4. Developer Guideline (New Module)

| Document | Audience | Description |
|----------|----------|-------------|
| [06_Developer_Guideline_New_Module.md](06_Developer_Guideline_New_Module.md) | Developers | Step-by-step guide for adding a new data-collection module: (a) collection/processing logic, (b) module controller, (c) Firestore dynamic parameters, (d) event structure and wiring to Database/Module manager for Firestore/Storage, (e) permissions and permission screens. |

---

## Quick reference

- **App behavior (what happens when)**: 01 (dev), 02 (managers/researchers).
- **Data flow and Firestore/Storage**: 03 (dev), 04 (managers/researchers).
- **Where things live in code**: 05.
- **Adding a new module**: 06.
- **App structure (refactors, assessment)**: 10.
- **Notification and media projection flow**: 11.
