package com.livingroom.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "DoorHall";

    private DoorHallView hall;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("hall", MODE_PRIVATE);
        hall = new DoorHallView(this);
        setContentView(hall);
        List<Door> doors;
        try {
            doors = loadDoors();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "doors.json", e);
            Toast.makeText(this, "doors.json: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        int focus = 0;
        String last = prefs.getString("focus", "");
        for (int i = 0; i < doors.size(); i++) if (doors.get(i).target.equals(last)) focus = i;
        hall.setDoors(doors, focus);
    }

    private List<Door> loadDoors() throws IOException, JSONException {
        String json = Assets.readText(this, "doors.json");
        PackageManager pm = getPackageManager();
        JSONArray arr = new JSONObject(json).getJSONArray("doors");
        List<Door> doors = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String target = o.getString("target");
            int color = Color.parseColor(o.getString("color"));
            String style = o.optString("style", "panels");
            if (Door.ALL_APPS.equals(target)) {
                doors.add(new Door(target, getString(R.string.all_apps), color, style, null, false, color));
                continue;
            }
            String pkg = Door.SETTINGS.equals(target) ? "com.android.tv.settings" : target;
            ApplicationInfo info;
            try {
                info = pm.getApplicationInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException missing) {
                if (Door.SETTINGS.equals(target)) {
                    doors.add(new Door(target, getString(R.string.settings), color, style, null, false, color));
                }
                continue; // an app that is not installed gets no door
            }
            if (!info.enabled && !Door.SETTINGS.equals(target)) continue;
            Drawable banner = pm.getApplicationBanner(info);
            Drawable art = banner != null ? banner : pm.getApplicationIcon(info);
            String label = Door.SETTINGS.equals(target) ? getString(R.string.settings) : pm.getApplicationLabel(info).toString();
            doors.add(new Door(target, label, color, style, art, banner != null, averageColor(art)));
        }
        return doors;
    }

    /** The art's average colour, to tint the light behind its door. */
    private static int averageColor(Drawable d) {
        Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 16, 9);
        d.draw(new Canvas(b));
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 16; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
                n++;
            }
        }
        b.recycle();
        return n == 0 ? Color.WHITE : Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    @Override
    protected void onResume() {
        super.onResume();
        hall.reset();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Door focused = hall.focusedDoor();
        if (focused == null) return super.onKeyDown(keyCode, event);
        if (hall.isOpening()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    // A second OK skips the rest of the animation.
                    hall.reset();
                    enter(focused);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    hall.reset();
                    return true;
                default:
                    return true;
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                hall.move(-1);
                remember();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                hall.move(1);
                remember();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    if (Door.ALL_APPS.equals(focused.target)) showAllApps();
                    else hall.open(this::enter);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void remember() {
        Door d = hall.focusedDoor();
        if (d != null) prefs.edit().putString("focus", d.target).apply();
    }

    private void enter(Door door) {
        Intent intent = Door.SETTINGS.equals(door.target) ? new Intent(Settings.ACTION_SETTINGS) : launchIntent(door.target);
        if (intent == null) {
            hall.reset();
            Toast.makeText(this, getString(R.string.not_installed, door.label), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException | SecurityException e) {
            hall.reset();
            Toast.makeText(this, getString(R.string.not_installed, door.label), Toast.LENGTH_SHORT).show();
        }
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private void showAllApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(query, 0));
        found.removeIf(ri -> ri.activityInfo.packageName.equals(getPackageName()));
        Collator collator = Collator.getInstance();
        found.sort((a, b) -> collator.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));
        CharSequence[] labels = new CharSequence[found.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = found.get(i).loadLabel(pm);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.all_apps)
                .setItems(labels, (dialog, which) -> {
                    ResolveInfo ri = found.get(which);
                    Intent open = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(open);
                    } catch (ActivityNotFoundException | SecurityException ignored) {
                    }
                })
                .show();
    }
}
