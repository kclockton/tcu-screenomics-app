package edu.stanford.communication.screenomics.interactions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;

import edu.stanford.communication.screenomics.TextBasedData.EventOperationManager;
import edu.stanford.communication.screenomics.modulemanager.ModuleCharacteristics;

/**
 * AccessibilityService that records interaction events (scroll, click, long-click, touch exploration).
 * Uses InteractionsCollectionController.shouldCollect() and InteractionsInfo for event payloads.
 */
public class InteractionsUpdater extends AccessibilityService {

    private int scrolledX = 0;
    private int scrolledY = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!InteractionsCollectionController.shouldCollect(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                disableSelf();
            } else {
                stopSelf();
            }
            return;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (event.getScrollX() > scrolledX) {
                reportActivity("scroll-right");
                scrolledX = event.getScrollX();
            } else if (event.getScrollX() < scrolledX) {
                reportActivity("scroll-left");
                scrolledX = event.getScrollX();
            }
            if (event.getScrollY() > scrolledY) {
                reportActivity("scroll-up");
                scrolledY = event.getScrollY();
            } else if (event.getScrollY() < scrolledY) {
                reportActivity("scroll-down");
                scrolledY = event.getScrollY();
            }
        } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            reportActivity("clicked");
        } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            reportActivity("long-clicked");
        } else if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            reportActivity("touch-exploration-start");
        } else if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            reportActivity("touch-exploration-end");
        }
    }

    private void reportActivity(String activity) {
        HashMap<String, String> userInput = InteractionsInfo.buildInteractionEventMap(activity);
        EventOperationManager.getInstance(this).addEvent(
                ModuleCharacteristics.getInstance().getInteractionEventCharacteristics(), userInput);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START
                | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }
}
