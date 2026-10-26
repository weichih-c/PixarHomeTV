package com.livingroom.home;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

/** One door in the hall: what it opens, how it looks, and its pre-painted frame and leaf. */
final class Door {

    /** "@settings" and "@apps" in place of a package open the TV's Settings and the app list. */
    static final String SETTINGS = "@settings";
    static final String ALL_APPS = "@apps";

    final String target;
    final String label;
    final int color;
    final String style;
    /** The app's TV banner (16:9) when it has one, otherwise its icon; null draws a grid (the app list). */
    final Drawable art;
    final boolean wideArt;
    /** Light pouring out when the door opens: warm white tinted with the app's own colour. */
    final int light;

    Bitmap frame;
    Bitmap leaf;

    Door(String target, String label, int color, String style, Drawable art, boolean wideArt, int artColor) {
        this.target = target;
        this.label = label;
        this.color = color;
        this.style = style;
        this.art = art;
        this.wideArt = wideArt;
        this.light = mix(Color.rgb(255, 236, 196), artColor, 0.35f);
    }

    static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int shade(int c, float f) {
        return Color.rgb(Math.min(255, (int) (Color.red(c) * f)), Math.min(255, (int) (Color.green(c) * f)),
                Math.min(255, (int) (Color.blue(c) * f)));
    }
}
