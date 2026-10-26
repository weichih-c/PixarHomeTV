package com.livingroom.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * The door hall: doors hanging from a ceiling rail, the focused one in the middle under a spotlight, the rest
 * turned towards it. Opening a door swings its leaf in on the hinge, lets the light out, sends the monster's
 * silhouette running through and pushes the camera in after it.
 *
 * Layout is in a 1920x1080 design space scaled to the view height.
 */
final class DoorHallView extends View {

    interface OpenListener {
        void onDoorOpened(Door door);
    }

    // Design-space geometry of the focused door.
    private static final float LEAF_W = 300;
    private static final float LEAF_H = 560;
    private static final float FRAME_T = 26;
    private static final float FLOOR_Y = 930;
    private static final float RAIL_Y = 92;

    // Opening timeline, in ms.
    private static final long RATTLE_END = 200;
    private static final long SWING_END = 760;
    private static final long RUN_START = 380;
    private static final long RUN_END = 2080;
    private static final long PUSH_START = 1880;
    private static final long OPEN_END = 2520;
    /** Hops the slime takes from the edge of the screen to the threshold. */
    private static final int HOPS = 3;

    private final List<Door> doors = new ArrayList<>();
    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private final Matrix cameraMatrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Silhouette monster = new Silhouette();
    private final float[] dust = new float[24 * 3];
    private final float[] point = new float[2];

    private float k = 1f;
    private int focus;
    private float focusPos;
    private ValueAnimator slide;
    private long rattleAt;

    private Door opening;
    private long openAt;
    private OpenListener listener;
    private boolean bitmapsReady;

    private Shader background;
    private Shader floor;

    DoorHallView(Context context) {
        super(context);
        text.setColor(Color.WHITE);
        Random rnd = new Random(3);
        for (int i = 0; i < dust.length; i += 3) {
            dust[i] = rnd.nextFloat();
            dust[i + 1] = rnd.nextFloat();
            dust[i + 2] = rnd.nextFloat();
        }
    }

    void setDoors(List<Door> list, int focused) {
        doors.clear();
        doors.addAll(list);
        focus = Math.max(0, Math.min(focused, doors.size() - 1));
        focusPos = focus;
        bitmapsReady = false;
        invalidate();
    }

    int focusIndex() {
        return focus;
    }

    Door focusedDoor() {
        return doors.isEmpty() ? null : doors.get(focus);
    }

    boolean isOpening() {
        return opening != null;
    }

    void move(int delta) {
        int next = Math.max(0, Math.min(doors.size() - 1, focus + delta));
        if (next == focus) {
            rattleAt = SystemClock.uptimeMillis(); // a bump at either end of the rail
            return;
        }
        focus = next;
        if (slide != null) slide.cancel();
        slide = ValueAnimator.ofFloat(focusPos, focus);
        slide.setDuration(340);
        slide.setInterpolator(new DecelerateInterpolator(1.8f));
        slide.addUpdateListener(a -> focusPos = (float) a.getAnimatedValue());
        slide.start();
        rattleAt = SystemClock.uptimeMillis() + 200;
    }

    void open(OpenListener l) {
        opening = doors.get(focus);
        openAt = SystemClock.uptimeMillis();
        listener = l;
        invalidate();
    }

    /** Close everything and stop any opening; back to the hall as it was. */
    void reset() {
        opening = null;
        listener = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        k = h / 1080f;
        bitmapsReady = false;
        background = new LinearGradient(0, 0, 0, FLOOR_Y * k,
                new int[]{Color.rgb(10, 16, 28), Color.rgb(22, 34, 54), Color.rgb(34, 44, 62)}, new float[]{0, 0.6f, 1},
                Shader.TileMode.CLAMP);
        floor = new LinearGradient(0, FLOOR_Y * k, 0, h, Color.rgb(30, 36, 48), Color.rgb(8, 10, 16), Shader.TileMode.CLAMP);
    }

    private void prepareBitmaps() {
        int lw = Math.round(LEAF_W * k);
        int lh = Math.round(LEAF_H * k);
        int t = Math.round(FRAME_T * k);
        for (Door d : doors) {
            d.frame = DoorArt.frame(lw + 2 * t, lh + t, t, d.color);
            d.leaf = DoorArt.leaf(d, lw, lh);
        }
        bitmapsReady = true;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (doors.isEmpty()) return;
        if (!bitmapsReady) prepareBitmaps();
        long now = SystemClock.uptimeMillis();
        long el = opening == null ? -1 : now - openAt;
        int w = getWidth();
        int h = getHeight();

        float push = el < 0 ? 0 : clamp01((el - PUSH_START) / (float) (OPEN_END - PUSH_START));
        c.save();
        if (push > 0) {
            float zoom = 1 + 9 * push * push * push;
            float leafH = LEAF_H * k;
            c.scale(zoom, zoom, w / 2f, FLOOR_Y * k - leafH * 0.45f);
        }
        drawHall(c, w, h, now);
        drawSpotlight(c, w, now);

        // Far doors first.
        int n = doors.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(Math.abs(b - focusPos), Math.abs(a - focusPos)));
        for (int i : order) drawDoor(c, doors.get(i), i - focusPos, w, now, el);

        if (el >= RUN_START && el < RUN_END + 200) drawMonster(c, w, h, el);
        c.restore();

        if (el >= 0) {
            float flash = clamp01((el - (PUSH_START + 300)) / (float) (OPEN_END - PUSH_START - 300));
            if (flash > 0) {
                paint.setColor(opening.light);
                paint.setAlpha((int) (255 * flash));
                c.drawRect(0, 0, w, h, paint);
                paint.setAlpha(255);
            }
            if (el >= OPEN_END && listener != null) {
                OpenListener l = listener;
                listener = null;
                l.onDoorOpened(opening);
            }
        }
        drawClock(c);
        if (el < 0) drawLabel(c, w);
        postInvalidateOnAnimation();
    }

    private void drawHall(Canvas c, int w, int h, long now) {
        paint.setShader(background);
        c.drawRect(0, 0, w, FLOOR_Y * k, paint);
        paint.setShader(floor);
        c.drawRect(0, FLOOR_Y * k, w, h, paint);
        paint.setShader(null);
        // A far row of doors in the dark, sliding slower than the near ones.
        float far = 150 * k;
        float off = -(focusPos * 0.35f * far) % far;
        for (float x = off - far; x < w + far; x += far) {
            paint.setColor(Color.argb(22, 150, 170, 200));
            c.drawRoundRect(x + 52 * k, 500 * k, x + 98 * k, 590 * k, 4 * k, 4 * k, paint);
            paint.setColor(Color.argb(60, 255, 90, 70));
            c.drawCircle(x + 75 * k, 492 * k, 2.5f * k, paint);
        }
        // Ceiling rail.
        paint.setShader(new LinearGradient(0, (RAIL_Y - 10) * k, 0, (RAIL_Y + 12) * k, Color.rgb(120, 128, 140),
                Color.rgb(30, 34, 40), Shader.TileMode.CLAMP));
        c.drawRect(0, (RAIL_Y - 10) * k, w, (RAIL_Y + 12) * k, paint);
        paint.setShader(null);
        // Floor line.
        paint.setColor(Color.argb(120, 0, 0, 0));
        c.drawRect(0, FLOOR_Y * k - 2 * k, w, FLOOR_Y * k + 3 * k, paint);
    }

    private void drawSpotlight(Canvas c, int w, long now) {
        float cx = w / 2f;
        path.reset();
        path.moveTo(cx - 60 * k, RAIL_Y * k);
        path.lineTo(cx + 60 * k, RAIL_Y * k);
        path.lineTo(cx + 300 * k, FLOOR_Y * k);
        path.lineTo(cx - 300 * k, FLOOR_Y * k);
        path.close();
        paint.setShader(new LinearGradient(0, RAIL_Y * k, 0, FLOOR_Y * k, Color.argb(70, 255, 236, 200),
                Color.argb(18, 255, 236, 200), Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(new RadialGradient(cx, FLOOR_Y * k, 330 * k, Color.argb(90, 255, 236, 200), Color.argb(0, 255, 236, 200),
                Shader.TileMode.CLAMP));
        c.drawOval(cx - 330 * k, (FLOOR_Y - 40) * k, cx + 330 * k, (FLOOR_Y + 60) * k, paint);
        paint.setShader(null);
        // Dust drifting up through the beam.
        float t = (now % 60000) / 1000f;
        for (int i = 0; i < dust.length; i += 3) {
            float y = 1f - ((dust[i + 1] + t * (0.015f + dust[i + 2] * 0.02f)) % 1f);
            float spread = 60 + 240 * y;
            float x = cx + (dust[i] * 2 - 1) * spread * k + (float) Math.sin(t * 0.7 + i) * 8 * k;
            float yy = (RAIL_Y + y * (FLOOR_Y - RAIL_Y)) * k;
            int a = (int) (90 * (0.5 + 0.5 * Math.sin(t * 1.3 + i * 1.7)));
            paint.setColor(Color.argb(a, 255, 240, 210));
            c.drawCircle(x, yy, (1.5f + dust[i + 2] * 2f) * k, paint);
        }
    }

    private void drawDoor(Canvas c, Door d, float o, int w, long now, long el) {
        float ao = Math.abs(o);
        float s = 1f - 0.17f * Math.min(ao, 3f);
        float side = Math.signum(o);
        float x = w / 2f + side * (ao <= 1 ? ao * 400 : 400 + (ao - 1) * 290) * k;
        float floorY = FLOOR_Y * k;
        // Turned to face the spotlight: the edge nearer the middle comes towards the viewer.
        float angle = Math.max(-48, Math.min(48, o * 26));
        boolean isOpening = d == opening && el >= 0;
        boolean focused = ao < 0.5f;
        if (focused) angle += rattle(now, isOpening ? el : -1);

        Bitmap frame = d.frame;
        float fw = frame.getWidth();
        float fh = frame.getHeight();
        float t = FRAME_T * k;

        // Shadow on the floor.
        paint.setShader(new RadialGradient(x, floorY, fw * s * 0.65f, Color.argb(150, 0, 0, 0), Color.argb(0, 0, 0, 0),
                Shader.TileMode.CLAMP));
        c.drawOval(x - fw * s * 0.65f, floorY - 18 * k * s, x + fw * s * 0.65f, floorY + 26 * k * s, paint);
        paint.setShader(null);

        float swing = isOpening ? easeOut(clamp01((el - RATTLE_END) / (float) (SWING_END - RATTLE_END))) : 0;
        if (swing > 0) {
            // Light spilling out across the floor.
            float lw = (fw - 2 * t) * s;
            path.reset();
            path.moveTo(x - lw / 2, floorY);
            path.lineTo(x + lw / 2, floorY);
            path.lineTo(x + lw * 1.5f, getHeight());
            path.lineTo(x - lw * 1.5f, getHeight());
            path.close();
            paint.setShader(new LinearGradient(0, floorY, 0, getHeight(), withAlpha(d.light, (int) (150 * swing)),
                    withAlpha(d.light, 0), Shader.TileMode.CLAMP));
            c.drawPath(path, paint);
            paint.setShader(null);
        }

        // Turn about the door's own centre, so the perspective is even top and bottom.
        matrix.setTranslate(-fw / 2f, -fh / 2f);
        matrix.postScale(s, s);
        camera.save();
        camera.setLocation(0, 0, -16);
        camera.rotateY(angle);
        camera.getMatrix(cameraMatrix);
        camera.restore();
        matrix.postConcat(cameraMatrix);
        matrix.postTranslate(x, floorY - fh * s / 2f);

        // Hanger rod from the rail down to the lamp housing on top of the frame, wherever the turn put it.
        point[0] = fw / 2f;
        point[1] = -24 * k;
        matrix.mapPoints(point);
        paint.setColor(Color.rgb(54, 58, 66));
        c.drawRect(point[0] - 5 * k * s, RAIL_Y * k, point[0] + 5 * k * s, point[1], paint);
        paint.setColor(Color.rgb(90, 96, 108));
        c.drawRoundRect(point[0] - 18 * k * s, (RAIL_Y - 14) * k, point[0] + 18 * k * s, (RAIL_Y + 16) * k, 6 * k, 6 * k, paint);

        c.save();
        c.concat(matrix);
        if (swing > 0) {
            // The room behind the door.
            paint.setShader(new LinearGradient(0, t, 0, fh, Door.shade(d.light, 0.85f), d.light, Shader.TileMode.CLAMP));
            c.drawRect(t, t, fw - t, fh, paint);
            paint.setShader(new RadialGradient(fw / 2f, fh * 0.7f, fw * 0.6f, Color.argb(200, 255, 255, 255),
                    Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
            c.drawRect(t, t, fw - t, fh, paint);
            paint.setShader(null);
        }
        c.drawBitmap(frame, 0, 0, paint);
        float leafH = d.leaf.getHeight();
        if (swing > 0) {
            c.save();
            c.translate(t, t + leafH / 2f);
            camera.save();
            camera.setLocation(0, 0, -16);
            // Swings into the room, away from the viewer, so the leaf's face stays the right way round.
            camera.rotateY(82 * swing);
            camera.applyToCanvas(c);
            camera.restore();
            c.drawBitmap(d.leaf, 0, -leafH / 2f, paint);
            // The leaf darkens as it turns away from the light.
            paint.setColor(Color.argb((int) (150 * swing), 0, 0, 0));
            c.drawRect(0, -leafH / 2f, d.leaf.getWidth(), leafH / 2f, paint);
            c.restore();
        } else {
            c.drawBitmap(d.leaf, t, t, paint);
        }
        // Doors away from the spotlight sit in the dark.
        float dim = Math.min(ao, 3f) * 0.22f + (focused ? 0 : 0.08f);
        if (dim > 0) {
            paint.setColor(Color.argb((int) (255 * Math.min(0.75f, dim)), 6, 10, 18));
            c.drawRect(0, 0, fw, fh, paint);
        }
        drawLamp(c, fw, focused, isOpening, now);
        c.restore();
    }

    private void drawLamp(Canvas c, float fw, boolean focused, boolean isOpening, long now) {
        float cx = fw / 2f;
        float cy = -16 * k;
        float r = 9 * k;
        paint.setColor(Color.rgb(40, 42, 48));
        c.drawRoundRect(cx - r * 2, cy - r * 1.4f, cx + r * 2, cy + r * 1.4f, r, r, paint);
        int on = isOpening ? Color.rgb(90, 255, 140) : Color.rgb(255, 80, 60);
        float pulse = focused ? 0.6f + 0.4f * (float) Math.sin(now / 260.0) : 0f;
        if (focused || isOpening) {
            float g = isOpening ? 1f : pulse;
            paint.setShader(new RadialGradient(cx, cy, r * 5, withAlpha(on, (int) (160 * g)), withAlpha(on, 0), Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 5, paint);
            paint.setShader(null);
            paint.setColor(on);
        } else {
            paint.setColor(Color.rgb(90, 40, 36));
        }
        c.drawCircle(cx, cy, r, paint);
    }

    /** A short shake when a door arrives in the spotlight, and a harder one as it is about to open. */
    private float rattle(long now, long el) {
        if (el >= 0 && el < RATTLE_END) {
            return 4f * (float) Math.sin(el / 18.0);
        }
        long since = now - rattleAt;
        if (since < 0 || since > 600) return 0;
        float t = since / 1000f;
        return 3f * (float) Math.sin(t * 38) * (float) Math.exp(-t * 7);
    }

    private void drawMonster(Canvas c, int w, int h, long el) {
        float t = clamp01((el - RUN_START) / (float) (RUN_END - RUN_START));
        // HOPS hops: each starts with a squash on the ground, then a jump that carries it forward. It only moves
        // while in the air, so the travel comes in bounds.
        float ht = Math.min(t * HOPS, HOPS - 1e-4f);
        int hop = (int) ht;
        float f = ht - hop;
        float ground = 0.28f;
        float air = clamp01((f - ground) / (1 - ground));
        float travel = (hop + air * air * (3 - 2 * air)) / HOPS;
        float e = 1 - (1 - travel) * (1 - travel);
        float doorX = w / 2f;
        // Side-on, heading left: half in view at the right edge for the first squash, then across the floor and
        // into the door.
        float startX = w + 60 * k;
        float x = startX + (doorX - startX) * e;
        float y = (1060 + (FLOOR_Y - 1060) * e) * k;
        float scale = 1.9f + (0.62f - 1.9f) * e;
        float sx;
        float sy;
        float trail;
        if (f < ground) {
            // Landing from the last hop and gathering for the next: squashed flat, tuft and tail settling.
            float g = (float) Math.sin(f / ground * Math.PI);
            sy = 1 - 0.26f * g;
            sx = 1 + 0.22f * g;
            trail = -0.6f * g;
        } else {
            // In the air: stretched tall on the way up, rounding out at the top, tail streaming behind.
            float a = (float) Math.sin(air * Math.PI);
            sy = 1 + 0.14f * (float) Math.cos(air * Math.PI * 0.5) * a + 0.04f;
            sx = 1 / sy;
            trail = (float) Math.cos(air * Math.PI);
        }
        float jump = (float) Math.sin(air * Math.PI) * 130 * scale * k;
        float tilt = 7 * (float) Math.sin(air * Math.PI);
        if (t > 0.85f) {
            // Over the threshold: only what is in front of the light shows, shrinking into the room.
            float in = (t - 0.85f) / 0.15f;
            scale *= 1 - 0.45f * in;
            float lw = LEAF_W * k;
            float lh = LEAF_H * k;
            c.save();
            c.clipRect(doorX - lw / 2, FLOOR_Y * k - lh, doorX + lw / 2, FLOOR_Y * k);
            monster.draw(c, x, y - 40 * k * in - jump, scale, sx, sy, tilt, trail, -1, k, opening.light);
            c.restore();
        } else {
            monster.draw(c, x, y - jump, scale, sx, sy, tilt, trail, -1, k, opening.light);
        }
    }

    private void drawClock(Canvas c) {
        Calendar now = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        String time = DateFormat.format(h24 ? "H:mm" : "h:mm", now).toString();
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(84 * k);
        text.setColor(Color.argb(235, 255, 255, 255));
        c.drawText(time, 96 * k, 210 * k, text);
        text.setTextSize(30 * k);
        text.setColor(Color.argb(170, 255, 255, 255));
        c.drawText(DateFormat.format("M月d日 EEEE", now).toString(), 100 * k, 258 * k, text);
    }

    private void drawLabel(Canvas c, int w) {
        Door d = doors.get(focus);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(46 * k);
        float settle = 1f - Math.min(1f, Math.abs(focusPos - focus) * 2f);
        text.setColor(Color.argb((int) (240 * settle), 255, 255, 255));
        c.drawText(d.label, w / 2f, 1010 * k, text);
    }

    private static int withAlpha(int color, int a) {
        return Color.argb(Math.max(0, Math.min(255, a)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float easeOut(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
