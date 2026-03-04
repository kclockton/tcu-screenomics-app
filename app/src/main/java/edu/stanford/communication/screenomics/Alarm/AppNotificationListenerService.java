package edu.stanford.communication.screenomics.Alarm;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import edu.stanford.communication.screenomics.DatabaseHelper.InterCommunicationPreference;

/**
 * Listens for posted notifications and updates app state so the alarm/UI can react.
 * (Formerly SeekForNotification.)
 */
public class AppNotificationListenerService extends NotificationListenerService {

    private InterCommunicationPreference prefrence;

    @Override
    public void onCreate() {
        super.onCreate();
        prefrence = new InterCommunicationPreference(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.isOngoing()) {
            prefrence.PutNewNotificationPopped(true);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    }
}
