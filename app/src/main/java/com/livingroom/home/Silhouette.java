package com.livingroom.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * A little slime monster as a clean black cut-out with a rim of the doorway's light: a soft dome of a body that
 * hops along, squashing into the ground and stretching into each jump, with two small horns, a tuft and a long
 * curled tail that swing a beat behind, and one big bright eye. About 260 design pixels tall at rest, resting on
 * (x, y), drawn facing +x and mirrored for a negative facing.
 */
final class Silhouette {

    private static final int INK = Color.rgb(8, 8, 14);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eyeWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path whole = new Path();
    private final Path part = new Path();
    private final Matrix m = new Matrix();

    Silhouette() {
        fill.setColor(INK);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeJoin(Paint.Join.ROUND);
        rim.setStrokeCap(Paint.Cap.ROUND);
        eyeWhite.setColor(Color.rgb(250, 246, 236));
        pupil.setColor(INK);
    }

    /**
     * sx, sy: squash and stretch about the base; tilt: lean in degrees, positive forward; trail: -1..1, how far
     * the tuft and tail swing back behind the motion; k: design-to-screen scale; light: the rim light's colour.
     */
    void draw(Canvas c, float x, float y, float scale, float sx, float sy, float tilt, float trail, int facing,
              float k, int light) {
        m.reset();
        m.setScale(sx, sy, 0, 0);
        m.postRotate(tilt, 0, 0);

        whole.reset();
        // The dome: wide soft base, rounded top leaning a little forward.
        part.reset();
        part.moveTo(-118, 0);
        part.cubicTo(-128, -70, -96, -228, 6, -232);
        part.cubicTo(104, -236, 132, -86, 120, 0);
        part.cubicTo(80, 10, -78, 10, -118, 0);
        part.close();
        add(part);
        horn(10, -226, 1f);
        horn(-30, -222, 0.85f);
        // Tuft on top, swinging back behind the hop.
        part.reset();
        float tuft = -24 * trail;
        part.moveTo(-40, -220);
        part.quadTo(-56 + tuft, -272, -86 + tuft * 1.4f, -276);
        part.quadTo(-62 + tuft * 0.6f, -250, -14, -226);
        part.close();
        add(part);
        tail(trail);

        c.save();
        c.translate(x, y);
        c.scale(facing * scale * k, scale * k);
        rim.setColor(Color.argb(190, Color.red(light), Color.green(light), Color.blue(light)));
        rim.setStrokeWidth(9f);
        c.drawPath(whole, rim);
        c.drawPath(whole, fill);
        // One big eye and a grin, riding along with the squash.
        c.save();
        c.concat(m);
        c.drawCircle(56, -150, 30, eyeWhite);
        c.drawCircle(68, -148, 14, pupil);
        c.drawCircle(73, -155, 5, eyeWhite);
        rim.setStrokeWidth(5f);
        c.drawArc(46, -118, 100, -80, 20, 110, false, rim);
        c.restore();
        c.restore();
    }

    private void add(Path p) {
        p.transform(m);
        whole.op(p, Path.Op.UNION);
    }

    /** A small horn sweeping back from the top of the head at (x, baseY). */
    private void horn(float x, float baseY, float size) {
        part.reset();
        part.moveTo(-12, 4);
        part.cubicTo(-10, -30, -30, -48, -48, -42);
        part.cubicTo(-30, -32, 0, -24, 12, 4);
        part.close();
        Matrix h = new Matrix();
        h.setScale(size, size);
        h.postTranslate(x, baseY);
        part.transform(h);
        add(part);
    }

    /**
     * A long tail from the lower back that sweeps out behind and rolls up into a spiral, thick at the root and
     * thin at the tip. trail swings the whole curl back and up as the slime jumps.
     */
    private void tail(float trail) {
        final int n = 48;
        float[] px = new float[n];
        float[] py = new float[n];
        float[] pw = new float[n];
        // A sweep out and up from the lower back to P, then a spiral that starts at P heading up and winds back
        // over itself, its radius shrinking as it goes.
        float baseX = -104;
        float baseY = -46;
        float lift = 1 + trail * 0.6f;
        float pX = -176;
        float pY = -60 - 40 * lift;
        float r0 = 46;
        float cX = pX - r0;
        float cY = pY;
        int sweep = 16;
        for (int i = 0; i < n; i++) {
            if (i < sweep) {
                float u = i / (float) sweep;
                // Quadratic curve from the base, bulging down a little before it rises to P.
                float qx = baseX + (pX - baseX) * 0.5f;
                float qy = baseY + 8;
                px[i] = (1 - u) * (1 - u) * baseX + 2 * (1 - u) * u * qx + u * u * pX;
                py[i] = (1 - u) * (1 - u) * baseY + 2 * (1 - u) * u * qy + u * u * pY;
            } else {
                float u = (i - sweep) / (float) (n - 1 - sweep);
                double a = -u * Math.PI * 1.85 - trail * 0.3;
                float r = r0 * (1 - 0.7f * u);
                px[i] = cX + (float) Math.cos(a) * r;
                py[i] = cY + (float) Math.sin(a) * r;
            }
            float t = i / (float) (n - 1);
            pw[i] = 28 * (1 - t) + 6 * t;
        }
        // A tapered ribbon along the points.
        part.reset();
        for (int i = 0; i < n; i++) {
            float[] nrm = normal(px, py, i);
            float ox = nrm[0] * pw[i] / 2;
            float oy = nrm[1] * pw[i] / 2;
            if (i == 0) part.moveTo(px[i] + ox, py[i] + oy);
            else part.lineTo(px[i] + ox, py[i] + oy);
        }
        for (int i = n - 1; i >= 0; i--) {
            float[] nrm = normal(px, py, i);
            part.lineTo(px[i] - nrm[0] * pw[i] / 2, py[i] - nrm[1] * pw[i] / 2);
        }
        part.close();
        part.addCircle(px[n - 1], py[n - 1], pw[n - 1] * 0.9f, Path.Direction.CW);
        add(part);
    }

    private static float[] normal(float[] px, float[] py, int i) {
        int a = Math.max(0, i - 1);
        int b = Math.min(px.length - 1, i + 1);
        float dx = px[b] - px[a];
        float dy = py[b] - py[a];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return new float[]{0, 0};
        return new float[]{-dy / len, dx / len};
    }
}
