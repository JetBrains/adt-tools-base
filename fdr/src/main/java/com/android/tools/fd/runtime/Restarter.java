package com.android.tools.fd.runtime;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;

/**
 * Handler capable of restarting parts of the application in order for changes to become
 * apparent to the user:
 * <ul>
 *     <li> Apply a tiny change immediately (possible if we can detect that the change
 *          is only used in a limited context (such as in a layout) and we can directly
 *          poke the view hierarchy and schedule a paint
 *     <li> Apply a change to the current activity. We can restart just the activity
 *          while the app continues running.
 *     <li> Restart the app with state persistence (simulates what happens when a user
 *          puts an app in the background, then it gets killed by the memory monitor,
 *          and then restored when the user brings it back
 *     <li> Restart the app completely.
 * </ul>
 */
public class Restarter {
    /** Restart an activity. Should preserve as much state as possible. */
    public static void restartActivityOnUiThread(@NonNull final Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Resources updated: notify activities");
                }
                updateActivity(activity);
            }
        });
    }

    private static void restartActivity(@NonNull Activity activity) {
        // Directly supported by the framework!
        activity.recreate();
    }

    /**
     * Attempt to restart the app. Ideally this should also try to preserve as much state as
     * possible:
     * <ul>
     *     <li>The current activity</li>
     *     <li>If possible, state in the current activity</li>, and
     *     <li>The activity stack</li>
     * </ul>
     *
     * This may require some framework support. Apparently it may already be possible
     * (Dianne says to put the app in the background, kill it then restart it; need to
     * figure out how to do this.)
     */
    public static void restartApp(@NonNull Collection<Activity> knownActivities) {
        if (!knownActivities.isEmpty()) {
            // Can't live patch resources; instead, try to restart the current activity
            Activity foreground = getForegroundActivity();

            if (foreground != null) {
                // http://stackoverflow.com/questions/6609414/howto-programatically-restart-android-app
                //noinspection UnnecessaryLocalVariable
                showToast(foreground, "Restarting app to show changed resources");
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "RESTARTING APP");
                }
                @SuppressWarnings("UnnecessaryLocalVariable") // fore code clarify
                        Context context = foreground;
                Intent intent = new Intent(context, foreground.getClass());
                int intentId = 0;
                PendingIntent pendingIntent = PendingIntent.getActivity(context, intentId,
                        intent, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Scheduling activity " + foreground
                            + " to start after exiting process");
                }
            } else {
                showToast(knownActivities.iterator().next(), "Unable to restart app");
                if (Log.isLoggable(LOG_TAG, Log.INFO)) {
                    Log.i(LOG_TAG, "Couldn't find any foreground activities to restart " +
                            "for resource refresh");
                }
            }
            System.exit(0);
        }

        // TODO: Toast warning?
    }

    static void showToast(@NonNull final Activity activity, @NonNull final String text) {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) {
            Log.i(LOG_TAG, "About to show toast for activity " + activity + ": " + text);
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Avoid crashing when not available, e.g.
                    //   java.lang.RuntimeException: Can't create handler inside thread that has
                    //        not called Looper.prepare()
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                } catch (Throwable e) {
                    if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                        Log.w(LOG_TAG, "Couldn't show toast", e);
                    }
                }
            }
        });
    }

    @Nullable
    public static Activity getForegroundActivity() {
        List<Activity> list = getActivities(true);
        return list.isEmpty() ? null : list.get(0);
    }

    // http://stackoverflow.com/questions/11411395/how-to-get-current-foreground-activity-context-in-android
    @NonNull
    public static List<Activity> getActivities(boolean foregroundOnly) {
        List<Activity> list = new ArrayList<Activity>();
        try {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            @SuppressWarnings("unchecked")
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);

            // TODO: On older platforms, cast this to a HashMap

            Collection c;
            Object collection = activitiesField.get(activityThread);

            if (collection instanceof HashMap) {
                // Older platforms
                Map activities = (HashMap) collection;
                c = activities.values();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                    collection instanceof ArrayMap) {
                ArrayMap activities = (ArrayMap) collection;
                c = activities.values();
            } else {
                return list;
            }
            for (Object activityRecord : c) {
                Class activityRecordClass = activityRecord.getClass();
                if (foregroundOnly) {
                    Field pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (pausedField.getBoolean(activityRecord)) {
                        continue;
                    }
                }
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                if (activity != null) {
                    list.add(activity);
                }
            }
        } catch (Throwable ignore) {
        }
        return list;
    }

    private static void updateActivity(@NonNull Activity activity) {
        // This method can be called for activities that are not in the foreground, as long
        // as some of its resources have been updated. Therefore we'll need to make sure
        // that this activity is in the foreground, and if not do nothing. Ways to do
        // that are outlined here:
        // http://stackoverflow.com/questions/3667022/checking-if-an-android-application-is-running-in-the-background/5862048#5862048

        // Try to force re-layout; there are many approaches; see
        // http://stackoverflow.com/questions/5991968/how-to-force-an-entire-layout-view-refresh

        // This doesn't seem to update themes properly -- may need to do recreate() instead!
        //getWindow().getDecorView().findViewById(android.R.id.content).invalidate();

        // This is a bit of a sledgehammer. We should consider having an incremental updater,
        // similar to IntelliJ's Look & Feel updater which iterates to the view hierarchy
        // and tries to incrementally refresh the LAF delegates and force a repaint.
        // On the other hand, we may never be able to succeed with that, since there could be
        // UI elements on the screen cached from callbacks. I should probably *not* attempt
        // to try to poke the user's data models; recreating the current layout should be
        // enough (e.g. if a layout references @string/foo, we'll recreate those widgets
        //    if (mLastContentView != -1) {
        //        setContentView(mLastContentView);
        //    } else {
        //        recreate();
        //    }
        // -- nope, even that's iffy. I had code which *after* calling setContentView would
        // do some findViewById calls etc to reinitialize views.
        //
        // So what I should really try to do is have some knowledge about what changed,
        // and see if I can figure out that the change is minor (e.g. doesn't affect themes
        // or layout parameters etc), and if so, just try to poke the view hierarchy directly,
        // and if not, just recreate

        //    if (changeManager.isSimpleDelta()) {
        //        changeManager.applyDirectly(this);
        //    } else {


        // Note: This doesn't handle manifest changes like changing the application title

        restartActivity(activity);
    }
}
