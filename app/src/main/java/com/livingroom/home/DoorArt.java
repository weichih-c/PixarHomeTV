package com.livingroom.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Paints a door once, at the size it has when focused, so the hall only blits bitmaps per frame. The frame
 * leaves the doorway transparent, so the light behind an opening leaf can be drawn underneath it.
 */
final class DoorArt {

    private DoorArt() {
    }

    static Bitmap frame(int fw, int fh, int t, int doorColor) {
        Bitmap b = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int steel = Door.mix(Color.rgb(70, 76, 88), doorColor, 0.18f);
        Path outer = new Path();
        outer.addRect(0, 0, fw, fh, Path.Direction.CW);
        outer.addRect(t, t, fw - t, fh, Path.Direction.CCW);
        p.setShader(new LinearGradient(0, 0, fw, 0,
                new int[]{Door.shade(steel, 1.25f), steel, Door.shade(steel, 0.7f)}, null, Shader.TileMode.CLAMP));
        c.drawPath(outer, p);
        p.setShader(null);
        // Bevel: light top-left outer edge, shadow along the inside of the doorway.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, t * 0.12f));
        p.setColor(Color.argb(90, 255, 255, 255));
        c.drawLine(1, fh, 1, 1, p);
        c.drawLine(1, 1, fw - 1, 1, p);
        p.setColor(Color.argb(140, 0, 0, 0));
        c.drawLine(t, fh, t, t, p);
        c.drawLine(t, t, fw - t, t, p);
        p.setColor(Color.argb(70, 255, 255, 255));
        c.drawLine(fw - t, t, fw - t, fh, p);
        // Bolts on the header.
        p.setStyle(Paint.Style.FILL);
        for (float x : new float[]{t * 0.5f, fw / 2f, fw - t * 0.5f}) {
            bolt(c, p, x, t * 0.5f, t * 0.16f);
        }
        return b;
    }

    static Bitmap leaf(Door d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(d.color);
        c.drawRect(0, 0, w, h, p);
        float plateY = h * 0.27f;
        switch (d.style) {
            case "panels":
                panels(c, p, w, h, d.color);
                break;
            case "planks":
                planks(c, p, w, h, d.color);
                break;
            case "porthole":
                porthole(c, p, w, h, d.color);
                plateY = h * 0.5f;
                break;
            case "arched":
                arched(c, p, w, h, d.color);
                plateY = h * 0.52f;
                break;
            case "stripes":
                stripes(c, p, w, h, d.color);
                break;
            case "dots":
                dots(c, p, w, h, d.color);
                break;
            default:
                break;
        }
        // Light from the top left across the whole leaf.
        p.setShader(new LinearGradient(0, 0, w * 0.5f, h, Color.argb(60, 255, 250, 235), Color.argb(80, 0, 0, 0),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        kickPlate(c, p, w, h);
        nameplate(c, p, d, w, plateY);
        knob(c, p, w * 0.86f, h * 0.56f, w * 0.055f);
        // Leaf edge.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, w * 0.008f));
        p.setColor(Color.argb(110, 0, 0, 0));
        c.drawRect(0, 0, w, h, p);
        p.setStyle(Paint.Style.FILL);
        return b;
    }

    private static void recess(Canvas c, Paint p, RectF r, int color) {
        float e = Math.max(3f, r.width() * 0.05f);
        p.setColor(Door.shade(color, 0.82f));
        c.drawRect(r, p);
        p.setColor(Door.shade(color, 0.62f));
        Path top = new Path();
        top.moveTo(r.left, r.bottom);
        top.lineTo(r.left, r.top);
        top.lineTo(r.right, r.top);
        top.lineTo(r.right - e, r.top + e);
        top.lineTo(r.left + e, r.top + e);
        top.lineTo(r.left + e, r.bottom - e);
        top.close();
        c.drawPath(top, p);
        p.setColor(Door.shade(color, 1.18f));
        Path bottom = new Path();
        bottom.moveTo(r.right, r.top);
        bottom.lineTo(r.right, r.bottom);
        bottom.lineTo(r.left, r.bottom);
        bottom.lineTo(r.left + e, r.bottom - e);
        bottom.lineTo(r.right - e, r.bottom - e);
        bottom.lineTo(r.right - e, r.top + e);
        bottom.close();
        c.drawPath(bottom, p);
    }

    private static void panels(Canvas c, Paint p, int w, int h, int color) {
        float m = w * 0.11f;
        float gap = w * 0.07f;
        float colW = (w - 2 * m - gap) / 2f;
        float[] rows = {0.42f, 0.30f};
        float y = h * 0.42f;
        for (float rh : rows) {
            float hh = h * rh - gap;
            for (int i = 0; i < 2; i++) {
                float x = m + i * (colW + gap);
                recess(c, p, new RectF(x, y, x + colW, y + hh), color);
            }
            y += h * rh;
        }
        recess(c, p, new RectF(m, h * 0.08f, w - m, h * 0.38f), color);
    }

    private static void planks(Canvas c, Paint p, int w, int h, int color) {
        Random rnd = new Random(color);
        int n = 5;
        float pw = w / (float) n;
        for (int i = 0; i < n; i++) {
            p.setColor(Door.shade(color, 0.92f + rnd.nextFloat() * 0.16f));
            c.drawRect(i * pw, 0, (i + 1) * pw, h, p);
            p.setColor(Door.shade(color, 0.55f));
            c.drawRect(i * pw, 0, i * pw + Math.max(2f, w * 0.008f), h, p);
            // Grain.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, w * 0.003f));
            p.setColor(Color.argb(40, 0, 0, 0));
            for (int g = 0; g < 5; g++) {
                float gx = i * pw + pw * (0.2f + rnd.nextFloat() * 0.6f);
                Path grain = new Path();
                grain.moveTo(gx, 0);
                grain.cubicTo(gx + pw * 0.15f, h * 0.3f, gx - pw * 0.15f, h * 0.6f, gx + pw * 0.05f, h);
                c.drawPath(grain, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        // Z brace.
        p.setColor(Door.shade(color, 0.7f));
        float bh = h * 0.05f;
        c.drawRect(0, h * 0.18f, w, h * 0.18f + bh, p);
        c.drawRect(0, h * 0.78f, w, h * 0.78f + bh, p);
        Path brace = new Path();
        brace.moveTo(0, h * 0.78f);
        brace.lineTo(bh, h * 0.78f);
        brace.lineTo(w, h * 0.18f + bh);
        brace.lineTo(w - bh, h * 0.18f + bh);
        brace.close();
        c.drawPath(brace, p);
        for (float y : new float[]{h * 0.18f + bh / 2, h * 0.78f + bh / 2}) {
            for (int i = 0; i < n; i++) bolt(c, p, (i + 0.5f) * pw, y, bh * 0.18f);
        }
    }

    private static void porthole(Canvas c, Paint p, int w, int h, int color) {
        // Brushed metal.
        p.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{Door.shade(color, 1.15f), Door.shade(color, 0.9f), Door.shade(color, 1.1f), Door.shade(color, 0.85f)},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float m = w * 0.06f;
        for (int i = 0; i < 9; i++) {
            float y = m + i * (h - 2 * m) / 8f;
            bolt(c, p, m, y, w * 0.012f);
            bolt(c, p, w - m, y, w * 0.012f);
        }
        float cx = w / 2f;
        float cy = h * 0.22f;
        float r = w * 0.24f;
        p.setColor(Door.shade(color, 0.6f));
        c.drawCircle(cx, cy, r * 1.18f, p);
        p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, Color.rgb(60, 90, 120), Color.rgb(8, 14, 24),
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        // Glass reflection.
        p.setColor(Color.argb(70, 255, 255, 255));
        Path glint = new Path();
        glint.addArc(cx - r * 0.75f, cy - r * 0.75f, cx + r * 0.75f, cy + r * 0.75f, 200, 60);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.12f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawPath(glint, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            bolt(c, p, cx + (float) Math.cos(a) * r * 1.09f, cy + (float) Math.sin(a) * r * 1.09f, w * 0.011f);
        }
        recess(c, p, new RectF(w * 0.12f, h * 0.72f, w * 0.88f, h * 0.88f), Door.shade(color, 1.05f));
    }

    private static void arched(Canvas c, Paint p, int w, int h, int color) {
        float m = w * 0.12f;
        RectF arch = new RectF(m, h * 0.06f, w - m, h * 0.06f + (w - 2 * m));
        Path window = new Path();
        window.arcTo(arch, 180, 180);
        window.lineTo(w - m, h * 0.3f);
        window.lineTo(m, h * 0.3f);
        window.close();
        p.setColor(Door.shade(color, 0.55f));
        c.save();
        c.translate(0, 0);
        c.drawPath(window, p);
        c.restore();
        c.save();
        c.clipPath(window);
        p.setShader(new LinearGradient(0, arch.top, 0, h * 0.3f, Color.rgb(255, 214, 150), Color.rgb(190, 110, 70),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.3f, p);
        p.setShader(null);
        // Sunburst mullions.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.018f);
        p.setColor(Door.shade(color, 0.5f));
        float sx = w / 2f;
        float sy = h * 0.3f;
        for (int i = 1; i < 6; i++) {
            double a = Math.PI + i * Math.PI / 6;
            c.drawLine(sx, sy, sx + (float) Math.cos(a) * w, sy + (float) Math.sin(a) * w, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.022f);
        p.setColor(Door.shade(color, 0.45f));
        c.drawPath(window, p);
        p.setStyle(Paint.Style.FILL);
        recess(c, p, new RectF(m, h * 0.68f, w - m, h * 0.92f), color);
    }

    private static void stripes(Canvas c, Paint p, int w, int h, int color) {
        p.setColor(Door.shade(color, 1.18f));
        float sw = w * 0.16f;
        for (float x = -h; x < w + h; x += sw * 2) {
            Path s = new Path();
            s.moveTo(x, h);
            s.lineTo(x + sw, h);
            s.lineTo(x + sw + h * 0.6f, 0);
            s.lineTo(x + h * 0.6f, 0);
            s.close();
            c.drawPath(s, p);
        }
        recess(c, p, new RectF(w * 0.1f, h * 0.06f, w * 0.9f, h * 0.44f), color);
    }

    private static void dots(Canvas c, Paint p, int w, int h, int color) {
        p.setColor(Door.shade(color, 1.22f));
        float step = w * 0.17f;
        int row = 0;
        for (float y = step * 0.5f; y < h; y += step, row++) {
            for (float x = (row % 2 == 0 ? step * 0.5f : step); x < w; x += step) {
                c.drawCircle(x, y, step * 0.2f, p);
            }
        }
        recess(c, p, new RectF(w * 0.1f, h * 0.06f, w * 0.9f, h * 0.44f), color);
    }

    private static void kickPlate(Canvas c, Paint p, int w, int h) {
        float top = h * 0.93f;
        p.setShader(new LinearGradient(0, top, 0, h, Color.rgb(214, 218, 224), Color.rgb(120, 126, 136), Shader.TileMode.CLAMP));
        c.drawRect(0, top, w, h, p);
        p.setShader(null);
        for (float x : new float[]{w * 0.05f, w * 0.95f}) bolt(c, p, x, (top + h) / 2f, (h - top) * 0.14f);
    }

    private static void nameplate(Canvas c, Paint p, Door d, int w, float cy) {
        float pw = w * 0.7f;
        float ph = pw * 9f / 16f;
        RectF r = new RectF((w - pw) / 2f, cy - ph / 2f, (w + pw) / 2f, cy + ph / 2f);
        float rad = pw * 0.06f;
        // Drop shadow, brass rim, dark field.
        p.setColor(Color.argb(90, 0, 0, 0));
        c.drawRoundRect(r.left + pw * 0.02f, r.top + pw * 0.03f, r.right + pw * 0.02f, r.bottom + pw * 0.03f, rad, rad, p);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{Color.rgb(255, 228, 150), Color.rgb(196, 146, 52), Color.rgb(120, 82, 20)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        float rim = pw * 0.035f;
        RectF field = new RectF(r.left + rim, r.top + rim, r.right - rim, r.bottom - rim);
        p.setColor(Color.rgb(16, 16, 20));
        c.drawRoundRect(field, rad * 0.6f, rad * 0.6f, p);
        c.save();
        Path clip = new Path();
        clip.addRoundRect(field, rad * 0.6f, rad * 0.6f, Path.Direction.CW);
        c.clipPath(clip);
        if (d.art == null) {
            // The app list: a grid of tiles.
            p.setColor(Color.rgb(230, 230, 236));
            float cell = field.height() / 4f;
            float gx = field.centerX() - cell * 1.5f;
            float gy = field.centerY() - cell * 1.5f;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    c.drawRoundRect(gx + i * cell + cell * 0.12f, gy + j * cell + cell * 0.12f, gx + (i + 1) * cell - cell * 0.12f,
                            gy + (j + 1) * cell - cell * 0.12f, cell * 0.2f, cell * 0.2f, p);
                }
            }
        } else if (d.wideArt) {
            d.art.setBounds((int) field.left, (int) field.top, (int) field.right, (int) field.bottom);
            d.art.draw(c);
        } else {
            int s = (int) (field.height() * 0.8f);
            int l = (int) (field.centerX() - s / 2f);
            int t = (int) (field.centerY() - s / 2f);
            d.art.setBounds(l, t, l + s, t + s);
            d.art.draw(c);
        }
        c.restore();
        // Glass sheen over the plate.
        p.setShader(new LinearGradient(field.left, field.top, field.left, field.bottom, Color.argb(60, 255, 255, 255),
                Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        c.drawRect(field.left, field.top, field.right, field.centerY(), p);
        p.setShader(null);
        for (float x : new float[]{r.left + rim / 2, r.right - rim / 2}) {
            for (float y : new float[]{r.top + rim / 2, r.bottom - rim / 2}) bolt(c, p, x, y, rim * 0.28f);
        }
    }

    private static void knob(Canvas c, Paint p, float cx, float cy, float r) {
        p.setColor(Color.argb(90, 0, 0, 0));
        c.drawRoundRect(cx - r * 0.75f + r * 0.2f, cy - r * 2.4f + r * 0.3f, cx + r * 0.75f + r * 0.2f, cy + r * 2.4f + r * 0.3f,
                r * 0.6f, r * 0.6f, p);
        p.setShader(new LinearGradient(cx - r, 0, cx + r, 0, Color.rgb(230, 190, 100), Color.rgb(130, 90, 30), Shader.TileMode.CLAMP));
        c.drawRoundRect(cx - r * 0.75f, cy - r * 2.4f, cx + r * 0.75f, cy + r * 2.4f, r * 0.6f, r * 0.6f, p);
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.3f,
                new int[]{Color.rgb(255, 240, 190), Color.rgb(200, 150, 60), Color.rgb(100, 66, 20)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
    }

    private static void bolt(Canvas c, Paint p, float cx, float cy, float r) {
        p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.2f, Color.rgb(235, 235, 240), Color.rgb(80, 84, 92),
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
    }
}
