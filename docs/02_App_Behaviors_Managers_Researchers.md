# App Behaviors — For App Managers & Researchers

This document describes what the app does from a study and operations perspective: what participants see, when data is collected, and how pause/resume and notifications work.

---

## 1. What the App Does

The app collects smartphone data for research. It can collect:

- **Screen screenshots** (periodic captures of the screen)
- **Foreground app** (which app is in front)
- **Interactions** (e.g. taps, scrolls, via accessibility)
- **Location** (GPS)
- **Battery and power** (charging, screen on/off)
- **Network** (connectivity)
- **Device specs** (once per day)
- **Physical activity / step counts** (if enabled)

Which of these are active can be configured per study (modules can be turned on/off, and some depend on Firestore settings like “gps-enabled” or “pa-enabled”).

---

## 2. When Data Is Collected

- **Screen capture** runs only when the participant has turned it on and granted “screen recording” (media projection). When they turn it off or the system revokes it, capture stops.
- **Other sensors** (e.g. location, battery, foreground app, specs) run while the main “monitor” service is running. That service is started when the participant completes the permission flow and can keep running in the background (with possible periodic restarts for stability).

So: **screenshots** require the participant to have capture “on” and permission granted; **other data** can continue as long as the app’s monitor service is running.

---

## 3. Pause and Resume

**Two types of “pause”:**

1. **User-initiated**: Participant turns off screen capture (or revokes permission).
2. **System-initiated**: e.g. system stops screen capture (e.g. screen off, or permission revoked by OS).

The app treats these differently for **notifications** and **popups**.

---

## 4. Notifications and Reminders

- **2 minutes after any pause**: The app sends a notification reminding the participant to reopen the app to resume (or that the app is not running). This is the **first** reminder and is logged to Firestore like other reminder events.

- **User-initiated pause only – next 30 minutes**: For 30 minutes after the participant pauses, the app does **not** show the automatic “resume screen capture” popup when they turn the screen on. After 30 minutes, that popup can show again on screen-on (same as before the pause).

- **User-initiated pause only – follow-up reminders**: If the participant still has not resumed, the app sends **up to four more** reminder notifications at fixed times (user’s local time):
  - Next day 7:00 AM  
  - Next day 7:00 PM  
  - Day after next 7:00 AM  
  - Day after next 7:00 PM  

  Each of these is the same kind of reminder (and logged the same way as the 2-minute one). There is **no** reminder after the fifth (day-after-next 7 PM).

- **When the participant resumes**: All reminder scheduling for that pause episode stops; the next pause starts a new 2-minute and (if user-initiated) 30-minute and 7 AM/7 PM schedule.

---

## 5. Popup (Media Projection Dialog)

- When capture is paused and the participant turns the screen on, the app may show a dialog asking them to start screen capture again.
- This popup is **not** shown:
  - During the first 30 minutes after a **user-initiated** pause (so we don’t prompt immediately after they chose to stop).
  - While the participant is still in the initial permission/setup flow (before they have reached the main app screen).
- After 30 minutes (for user pause) or for system-initiated pause, the popup can appear on screen-on as before.

---

## 6. Permissions and Setup

Participants go through a multi-step permission flow (e.g. notifications, location, physical activity if used). Which steps appear depends on study configuration (which modules and Firestore settings are enabled). Screen capture permission is separate and is requested when the participant turns capture on.

---

## 7. Summary Table (User-Initiated Pause)

| Time / event | What happens |
|--------------|----------------|
| Participant pauses | Cause stored as “user”; 2-min and 7 AM/7 PM reminders scheduled. |
| 2 min after pause | First notification sent; event logged to Firestore. |
| 0–30 min after pause | No media projection popup on screen-on. |
| After 30 min | Media projection popup can show again on screen-on. |
| Next day 7 AM | Second notification (if still paused); logged. |
| Next day 7 PM | Third notification; logged. |
| Day after next 7 AM | Fourth notification; logged. |
| Day after next 7 PM | Fifth (last) notification; logged. |
| Participant resumes | Reminders and popup logic reset for next time. |

---

## 8. For Study Design

- **Reminder load**: User-initiated pause generates at most five notifications (one at 2 min + four at 7 AM/7 PM over two days). System-initiated pause only gets the 2-minute notification (and normal popup behavior after that).
- **Popup behavior**: 30-minute suppression after user pause reduces repeated prompts right after the participant chose to stop.
- **Logging**: All these reminder notifications are logged to Firestore in the same way as the initial 2-minute reminder, so researchers can analyze reminder delivery and (with other events) correlate with resume behavior.
