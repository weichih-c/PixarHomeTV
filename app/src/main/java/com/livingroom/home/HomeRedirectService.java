package com.livingroom.home;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Sends the stock home screen back to the door hall. On this TV (MOKA R4_GTV) the stock launcher outranks any
 * third-party one for HOME and the firmware re-enables what it needs at every cold boot, so instead of fighting
 * for the default, this watches for the stock home's window and opens ours on top of it.
 *
 * It listens to window changes only and never asks to filter keys: a key-filtering service makes this firmware
 * drop held-key repeats, which is what stopped the volume ramping with the reference launcher's HomeKeyService.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.livingroom.home/.HomeRedirectService
 *   settings put secure accessibility_enabled 1
 */
public class HomeRedirectService extends AccessibilityService {

    private static final String[] STOCK_LAUNCHERS = {
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
    };
    /** How long after boot the first bind still counts as "at boot" for the probe. */
    private static final long BOOT_WINDOW_MS = 3 * 60 * 1000L;

    /** Don't open the replacement again while the app it replaces is still bringing up its windows. */
    private static final long REDIRECT_QUIET_MS = 2000;

    private final Set<String> stockHome = new HashSet<>();
    /** doors.json "app_redirects": an app the remote opens by a button of its own, and the app to open instead. */
    private final Map<String, String> redirects = new HashMap<>();
    private long lastRedirect;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        PackageManager pm = getPackageManager();
        for (String pkg : STOCK_LAUNCHERS) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
            for (ResolveInfo ri : pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
        }
        try {
            JSONObject map = new JSONObject(Assets.readText(this, "doors.json")).optJSONObject("app_redirects");
            if (map != null) {
                for (Iterator<String> it = map.keys(); it.hasNext(); ) {
                    String from = it.next();
                    redirects.put(from, map.getString(from));
                }
            }
        } catch (IOException | JSONException e) {
            Log.w("DoorHall", "app_redirects", e);
        }
        probeAtBoot();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (cls != null && stockHome.contains(cls.toString())) {
            openHome();
            return;
        }
        CharSequence pkg = event.getPackageName();
        String to = pkg == null ? null : redirects.get(pkg.toString());
        if (to != null) openInstead(to);
    }

    /**
     * Opens `to` in place of an app a remote button started (the Prime Video button is wired to Prime Video in the
     * firmware's read-only GlobalKeys.xml). TASK_ON_HOME puts it straight above the home screen, so Back from it
     * returns to the door hall, not to the app it replaced, which stays behind until Android reclaims it.
     */
    private void openInstead(String to) {
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirect < REDIRECT_QUIET_MS) return;
        lastRedirect = now;
        PackageManager pm = getPackageManager();
        Intent open = pm.getLeanbackLaunchIntentForPackage(to);
        if (open == null) open = pm.getLaunchIntentForPackage(to);
        if (open == null) return;
        try {
            startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    /** The stock home is often on screen before Android binds this service at boot, so no window event for it
     *  arrives. Once per boot, open a see-through activity that closes at once: whatever was in front comes back
     *  and reports itself, and only the stock home is redirected (an HDMI input resumed at boot is left alone). */
    private void probeAtBoot() {
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = createDeviceProtectedStorageContext().getSharedPreferences("boot", MODE_PRIVATE);
        if (boot < 0 || boot == prefs.getInt("probe_boot", -1) || SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) return;
        prefs.edit().putInt("probe_boot", boot).apply();
        try {
            startActivity(new Intent(this, ProbeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private void openHome() {
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
