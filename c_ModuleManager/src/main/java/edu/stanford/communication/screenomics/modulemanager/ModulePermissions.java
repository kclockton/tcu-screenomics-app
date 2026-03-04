package edu.stanford.communication.screenomics.modulemanager;

/**
 * Permission gating: only request/show permissions for enabled modules.
 * Delegates to {@link ModuleController} ENABLE_* flags.
 */
public final class ModulePermissions {

    private ModulePermissions() {}

    /** Usage access (app usage stats) is required for m02_Apps (foreground app capture). */
    public static boolean needUsageAccess() {
        return ModuleController.ENABLE_APPS;
    }

    /** Accessibility service is required for m03_Interactions (user interaction capture). */
    public static boolean needAccessibility() {
        return ModuleController.ENABLE_INTERACTIONS;
    }

    /** Activity recognition is required for m05_Activites (step/user activity). Use with Firestore "pa-enabled". */
    public static boolean needActivityRecognition() {
        return ModuleController.ENABLE_ACTIVITIES;
    }

    /** Location is required for m04_Locations. Use with Firestore "gps-enabled". */
    public static boolean needLocation() {
        return ModuleController.ENABLE_LOCATIONS;
    }

    /** Media projection (screen capture) is required for m01_Screenshots. */
    public static boolean needMediaProjection() {
        return ModuleController.ENABLE_SCREENSHOTS;
    }

    /** Notification listener is used for m02_Apps (foreground/notification context). */
    public static boolean needNotificationListener() {
        return ModuleController.ENABLE_APPS;
    }
}
