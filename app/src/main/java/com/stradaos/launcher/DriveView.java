package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * My Car · وضع القيادة: سماء ليلية بنجوم، طريق بمنظور ثلاثي الأبعاد تتحرك خطوطه مع السرعة، سيارتان أمامنا، قوس سرعة
 * كبير في الأعلى مع الرقم، وسيارتنا من الخلف. يرسم 25 إطاراً في الثانية فقط والسيارة تتحرك، وإلا صورة ثابتة (شاشة ضعيفة).
 */
public class DriveView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private float[] stars;
    private float speed, shown;
    private boolean ok;
    private int max = 200;
    private String unit = "km/h";
    private int accent = 0xFFFFC107;
    private Bitmap car;
    private float offset;
    private long lastFrame;
    private boolean running = true;
    private Typeface font, black;
    private Bitmap sky;

    public DriveView(Context c) { super(c); }

    public void setup(int max, String unit, int accent, Bitmap car, Typeface font, Typeface black) {
        this.max = max; this.unit = unit; this.accent = accent; this.car = car; this.font = font; this.black = black;
        invalidate();
    }

    public void setSpeed(float v, boolean ok) {
        this.ok = ok;
        speed = Math.max(0, v);
        invalidate();
    }

    public void setRunning(boolean r) { running = r; if (r) invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        Random rnd = new Random(11);
        stars = new float[160 * 3];
        for (int i = 0; i < 160; i++) {
            stars[i * 3] = rnd.nextFloat() * w;
            stars[i * 3 + 1] = rnd.nextFloat() * h * 0.42f;
            stars[i * 3 + 2] = 0.6f + rnd.nextFloat() * 1.8f;
        }
        sky = null;
    }

    /** السماء والأفق (ثابتة) تُرسم مرة واحدة في صورة. */
    private void buildSky(int w, int h) {
        try {
            sky = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(sky);
            Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
            float hz = h * 0.42f;
            q.setShader(new LinearGradient(0, 0, 0, hz, new int[]{0xFF0A1028, 0xFF1A2A6C, 0xFF2D3E8F}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, hz, q);
            q.setShader(null);
            for (int i = 0; stars != null && i < stars.length / 3; i++) {
                q.setColor(0xCCFFFFFF);
                c.drawCircle(stars[i * 3], stars[i * 3 + 1], stars[i * 3 + 2], q);
            }
            // مبانٍ بعيدة خافتة وأشجار
            Random rnd = new Random(5);
            q.setColor(0xFF1C2660);
            float x = 0;
            while (x < w) {
                float bw = w * (0.03f + rnd.nextFloat() * 0.04f), bh = h * (0.04f + rnd.nextFloat() * 0.09f);
                c.drawRect(x, hz - bh, x + bw, hz, q);
                x += bw + w * 0.01f;
            }
            q.setColor(0xFF1F5A3A);
            for (int i = 0; i < 6; i++) {
                float tx = w * (0.08f + i * 0.17f), tr = h * 0.035f;
                c.drawCircle(tx, hz - tr * 0.6f, tr, q);
            }
            q.setShader(new LinearGradient(0, hz, 0, h, 0xFF2B3550, 0xFF121726, Shader.TileMode.CLAMP));
            c.drawRect(0, hz, w, h, q);
        } catch (Throwable e) { sky = null; }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (sky == null) buildSky(w, h);
        if (sky != null) c.drawBitmap(sky, 0, 0, null);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float ds = speed - shown;
        shown += Math.abs(ds) < 0.3f ? ds : ds * 0.15f;
        offset = (offset + dt * (0.15f + shown / 60f)) % 1f;

        float hz = h * 0.42f, cx = w / 2f;
        // الطريق
        float topW = w * 0.16f, botW = w * 1.25f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, hz, 0, h, 0xFF2A2E38, 0xFF16181D, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(cx - topW / 2, hz);
        path.lineTo(cx + topW / 2, hz);
        path.lineTo(cx + botW / 2, h);
        path.lineTo(cx - botW / 2, h);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
        // حافتان صفراوان
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(h * 0.008f);
        p.setColor(0xFFE0A93A);
        c.drawLine(cx - topW / 2, hz, cx - botW / 2, h, p);
        c.drawLine(cx + topW / 2, hz, cx + botW / 2, h, p);
        // خطوط المسارب المتقطعة (تتحرك)
        p.setColor(0xEEFFFFFF);
        for (int lane = -1; lane <= 1; lane += 2) {
            float lx0 = cx + lane * topW * 0.17f, lx1 = cx + lane * botW * 0.17f;
            for (int i = 0; i < 9; i++) {
                float t0 = ((i + offset) / 9f), t1 = t0 + 0.045f;
                if (t1 > 1) continue;
                float e0 = t0 * t0, e1 = t1 * t1;   // منظور: الخطوط تكبر قرب الكاميرا
                float y0 = hz + (h - hz) * e0, y1 = hz + (h - hz) * e1;
                float x0 = lx0 + (lx1 - lx0) * e0, x1 = lx0 + (lx1 - lx0) * e1;
                p.setStrokeWidth(Math.max(2, h * 0.012f * (0.3f + e0)));
                c.drawLine(x0, y0, x1, y1, p);
            }
        }
        // أعمدة إنارة ولوحات عاكسة على حافتي الطريق تمرّ مع السرعة
        final int M = 6;
        for (int i = 0; i < M; i++) {
            float t = (i + offset) / M;
            float e = t * t;
            if (e < 0.02f) continue;
            float y = hz + (h - hz) * e;
            float hw = (topW + (botW - topW) * e) / 2f;
            float ph = h * 0.30f * e, off = w * 0.05f * e + 3;
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = cx + sd * (hw + off);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, h * 0.010f * e + 1));
                p.setColor(0xAA8E9AB4);
                c.drawLine(x, y, x, y - ph, p);
                c.drawLine(x, y - ph, x - sd * w * 0.025f * e, y - ph - h * 0.01f * e, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFD27A);
                c.drawCircle(x - sd * w * 0.025f * e, y - ph - h * 0.01f * e, Math.max(2f, h * 0.018f * e + 1), p);
            }
        }
        // سيارتان أمامنا (ظل داكن وأضواء خلفية)
        drawLead(c, cx - botW * 0.30f, hz + (h - hz) * 0.38f, w * 0.09f);
        drawLead(c, cx + botW * 0.30f, hz + (h - hz) * 0.26f, w * 0.075f);

        // قوس السرعة في الأعلى
        float arcR = w * 0.75f, arcCy = h * 0.07f + arcR;
        rf.set(cx - arcR, arcCy - arcR, cx + arcR, arcCy - arcR + arcR * 2);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.006f);
        p.setColor(0x99AEC4FF);
        float startA = 238, sweepA = 64;
        c.drawArc(rf, startA, sweepA, false, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(font);
        for (int v = 0; v <= max; v += 10) {
            double a = Math.toRadians(startA + sweepA * v / (float) max);
            boolean major = v % 40 == 0;
            float r1 = arcR + h * 0.012f, r2 = arcR - h * (major ? 0.06f : 0.035f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(h * (major ? 0.007f : 0.004f));
            p.setColor(v <= shown ? accent : 0xDDFFFFFF);
            c.drawLine(cx + (float) Math.cos(a) * r1, arcCy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, arcCy + (float) Math.sin(a) * r2, p);
            if (major && v > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xDDFFFFFF);
                p.setTextSize(h * 0.035f);
                float rl = arcR - h * 0.11f;
                c.drawText(String.valueOf(v), cx + (float) Math.cos(a) * rl, arcCy + (float) Math.sin(a) * rl, p);
            }
        }
        // الرقم الكبير
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(black != null ? black : font);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(h * 0.14f);
        p.setShadowLayer(h * 0.01f, 0, 0, 0xAA000000);
        c.drawText(ok ? String.valueOf(Math.round(shown < 1 ? 0 : shown)) : "0", cx, h * 0.30f, p);
        p.setTypeface(font);
        p.setTextSize(h * 0.035f);
        p.setColor(accent);
        c.drawText(unit, cx, h * 0.35f, p);
        p.clearShadowLayer();

        // سيارتنا من الخلف
        if (car != null) {
            float cw = w * 0.2f, ch = cw * car.getHeight() / (float) car.getWidth();
            rf.set(cx - cw / 2, h - ch - h * 0.02f, cx + cw / 2, h - h * 0.02f);
            p.setFilterBitmap(true);
            c.drawBitmap(car, null, rf, p);
        }

        if (running && (shown > 0.5f || Math.abs(ds) > 0.3f)) postInvalidateDelayed(40);
        else lastFrame = 0;
    }

    private void drawLead(Canvas c, float x, float y, float wd) {
        float ht = wd * 0.55f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1A1D24);
        rf.set(x - wd / 2, y - ht, x + wd / 2, y);
        c.drawRoundRect(rf, wd * 0.12f, wd * 0.12f, p);
        p.setColor(0xFF2A3348);
        rf.set(x - wd * 0.36f, y - ht * 1.25f, x + wd * 0.36f, y - ht * 0.75f);
        c.drawRoundRect(rf, wd * 0.1f, wd * 0.1f, p);
        p.setColor(0xFFFF3B30);
        rf.set(x - wd * 0.42f, y - ht * 0.62f, x - wd * 0.22f, y - ht * 0.5f);
        c.drawRoundRect(rf, 4, 4, p);
        rf.set(x + wd * 0.22f, y - ht * 0.62f, x + wd * 0.42f, y - ht * 0.5f);
        c.drawRoundRect(rf, 4, 4, p);
    }
}
