# Data Collection and Processing / Transfer Pipeline — For App Managers & Researchers

This document describes what data is collected, how it is processed, and where it is sent (Firestore and Storage), in non-technical terms suitable for study managers and researchers.

---

## 1. Types of Data Collected

The app collects two broad categories of data:

1. **Event data (text/structured)**  
   Timestamped records such as: location, battery state, screen on/off, which app is in the foreground, interactions (if enabled), network status, device specs, step counts (if enabled), and app events (e.g. capture started, paused, resumed, reminders sent).

2. **Screen images (screenshots)**  
   Periodic screenshots of the device screen when the participant has screen capture turned on and has granted permission.

---

## 2. Where Data Is Stored and Sent

### 2.1 Event data → Firestore

- **Event log**: All event records are eventually sent to **Firestore** under the participant’s account:
  - **Path**: `users/{participant_id}/events/`  
  - Each record has a unique ID and contains timestamps, event type, and any extra fields (e.g. “paused”, “resumed”, “cause: user/system”).
- **Specs**: Device specification events are stored under the same participant in a separate subcollection: `users/{participant_id}/specs/`.
- **Ticker**: A separate “ticker” document per participant holds the **most recent** occurrence of key events (e.g. last time a reminder was sent, last screenshot event). This is used for real-time dashboards or monitoring and is updated frequently.

So: **events** = full history; **ticker** = latest status.

### 2.2 Screenshots → Firebase Storage

- Screenshots are uploaded to **Firebase Storage** (not Firestore).
- They are organized **by participant**: each participant has a folder (identified by the same participant ID as in Firestore), and each screenshot is a file in that folder.
- Metadata about screenshots (e.g. capture or upload success/failure) is sent as **events** to Firestore as above; the images themselves live only in Storage.

---

## 3. How Data Moves (High Level)

1. **On the device**  
   - Events are first buffered in memory, then written to a local SQLite database so they are not lost if the app is closed or the network is unavailable.  
   - Screenshots are saved to app storage, then moved to an “upload” folder and sent when the upload process runs.

2. **To the cloud**  
   - **Events**: The app periodically (and when possible) reads from the local event database and uploads batches to Firestore (`users/.../events/` and `.../specs/`). The “ticker” is updated in Firestore on a short interval (e.g. every few seconds) when there is new activity.  
   - **Screenshots**: Upload runs on a schedule (e.g. every few minutes), and can be restricted to Wi‑Fi only. Each image is uploaded to the participant’s folder in Firebase Storage.

3. **Settings that affect behavior**  
   - Intervals (e.g. how often to capture a screenshot, how often to upload) and options (e.g. Wi‑Fi-only upload) can be set **remotely** via Firestore (user or group settings). So study managers can change sampling or upload behavior without releasing a new app version.

---

## 4. What Is Reported to Firestore or Storage

| What | Where | Purpose |
|------|--------|--------|
| All event types (location, battery, pause/resume, reminders, etc.) | Firestore: `users/{id}/events/` | Full event history for analysis |
| Device specs | Firestore: `users/{id}/specs/` | Device and environment context |
| Latest event summary (ticker) | Firestore: `ticker/{id}` | Real-time status / monitoring |
| Screenshot images | Firebase Storage: by participant ID | Screen content for analysis |

---

## 5. Privacy and Compliance Notes

- All participant-identifying paths use the same anonymized participant ID (subject/code number).  
- Screenshots are stored only in Firebase Storage under that ID.  
- Event data in Firestore can include timestamps (GMT and local), event type, and module-specific fields; the structure is consistent so researchers can design analyses and exports knowing where each type of data lives.

---

## 6. For Study Design

- **Event data**: Use `users/{id}/events/` (and `specs/`) for any analysis that needs a full timeline (e.g. when capture was paused/resumed, when reminders were sent, sensor events).  
- **Ticker**: Use `ticker/{id}` for “last known activity” or simple dashboards; it does not replace the event log for research analysis.  
- **Screenshots**: Use Firebase Storage under the participant folder; link to events in Firestore by time or event IDs if you need to associate images with specific events.  
- **Remote control**: Use Firestore settings (user or group) to adjust sampling and upload intervals and options per study or per participant without app updates.
