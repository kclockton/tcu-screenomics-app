package edu.stanford.communication.screenomics.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import edu.stanford.communication.screenomics.screenshots.CaptureUploadService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ScreenomicsAccessService extends AccessibilityService {

    public static final String TAG = "ScrnmicsAccessService";

    private AccessibilityNodeInfo windowRoot;

    private BroadcastReceiver screenshotReceiver;

    public ScreenomicsAccessService() {
    }

    @Override
    protected void onServiceConnected()
    {
        Log.d(TAG, "accessibility service connected!");

        // Set event types, just in case the config XML didn't suffice.
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        setServiceInfo(info);

        // Register a receiver for ACTION_SCREENSHOT, sent by CaptureUploadService.
        IntentFilter filter = new IntentFilter(CaptureUploadService.ACTION_SCREENSHOT);
        screenshotReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("write-text-contents", false)) {
                    writeWindowContentsToFile(intent.getStringExtra("directory"),
                            intent.getStringExtra("name"));
                }
            }
        };
        ContextCompat.registerReceiver(this, screenshotReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenshotReceiver);
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event)
    {
        switch (event.getEventType())
        {
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                onWindowContentEvent(event);
        }
    }

    private void onWindowContentEvent(AccessibilityEvent event)
    {
        windowRoot = event.getSource();
        assert windowRoot != null;
    }

    /**
     * Creates a text file that sits alongside a screenshot, containing the current contents of
     * the window when that screenshot was taken. This should be called the moment the screenshot
     * is generated for accurate results.
     * @param screenshotDir The directory containing screenshots.
     * @param screenshotName The name of the relevant screenshot (NOT including .jpg)
     */
    public void writeWindowContentsToFile(String screenshotDir, String screenshotName)
    {
        // Create a TXT file.
        File windowfile = new File(screenshotDir, screenshotName + "_WND.txt");
        FileWriter writer;

        try {
            // Create a writer for the file.
            writer = new FileWriter(windowfile);

            // Refresh the window root, and make sure the window still exists.
            if (windowRoot == null || !windowRoot.refresh()) {
                writer.write("(WINDOW INVALIDATED SINCE LAST CONTENT EVENT)");
            }

            // If it does, recursively write the view tree.
            else {
                writeWindowComponent(writer, windowRoot);
            }

            writer.close();
        }
        catch (IOException e) {
            // TODO handle filewriting error
            Log.e(TAG, "Error writing text data for " + windowfile.getName());
            e.printStackTrace();
            return;
        }

        Log.i(TAG, "Wrote window text data to " + windowfile.getName());
    }

    /**
     * Recursive helper method that writes a component of the window to the file.
     * @param writer The FileWriter to write to
     * @param component Current top-level component
     */
    private void writeWindowComponent(FileWriter writer, AccessibilityNodeInfo component) throws IOException
    {
        if (writer != null && component != null)
        {
            // Write the text of this component to the file.
            if (!TextUtils.isEmpty(component.getText())) {
                writer.write(component.getText().toString() + "\n\n");
            }
            else if (!TextUtils.isEmpty(component.getContentDescription())) {
                writer.write(component.getContentDescription().toString() + "\n\n");
            }
            else {
                writer.write("(No text)\n\n");
            }

            // Recursively write component's children. The != check is a safeguard in case a node
            // has itself as a child, which someone on StackOverflow reported can actually happen.
            for (int i = 0; i < component.getChildCount(); i++) {
                if (component.getChild(i) != component) {
                    writeWindowComponent(writer, component.getChild(i));
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt()");
    }
}
