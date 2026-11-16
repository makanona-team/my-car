package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * My Car · الطريق الصغير تحت السيارة داخل العدّاد: خطوط متقطعة وأعمدة إنارة على الحافتين تمرّ بسرعة تتناسب مع السرعة.
 * يوضع تحت العدّاد بنفس حجمه. يرسم 20 إطاراً في الثانية فقط والسيارة تتحرك.
 */
public class RoadView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float carScale = 0.2f, speed, shown, offset;
    private long last;
    private boolean running = true;

    public RoadView(Context c) { super(c); }

    public void setup(float carScale) { this.carScale = carScale; invalidate(); }

    public void setSpeed(float v) { speed = Math.max(0, v); invalidate(); }

    public void setRunning(boolean r) { running = r; if (r) invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h * 1.08f) / 2f - 8;
        if (r < 30) return;
        float cx = w / 2f, cy = r + 8, carW = r * 2 * carScale;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        float d = speed - shown;
        shown += lite() || Math.abs(d) < 0.3f ? d : d * 0.15f;
        offset = (offset + dt * (0.2f + shown / 70f)) % 1f;

        float y0 = cy + r * 0.25f, y1 = cy + r * 0.95f, hw0 = carW * 0.18f, hw1 = carW * 0.95f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, y0, 0, y1, 0x00101418, 0xEE15181D, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(cx - hw0, y0); path.lineTo(cx + hw0, y0); path.lineTo(cx + hw1, y1); path.lineTo(cx - hw1, y1);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
        // حافتان صفراوان خافتتان
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * 0.008f));
        p.setColor(0x66E0A93A);
        c.drawLine(cx - hw0, y0, cx - hw1, y1, p);
        c.drawLine(cx + hw0, y0, cx + hw1, y1, p);
        // خط المنتصف المتقطع
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xCCFFFFFF);
        final int N = 7;
        for (int i = 0; i < N; i++) {
            float t0 = (i + offset) / N, t1 = t0 + 0.05f;
            if (t1 > 1) continue;
            float e0 = t0 * t0, e1 = t1 * t1;
            p.setStrokeWidth(Math.max(1.5f, r * 0.026f * (0.25f + e0)));
            c.drawLine(cx, y0 + (y1 - y0) * e0, cx, y0 + (y1 - y0) * e1, p);
        }
        // أعمدة إنارة على الحافتين (متباعدة قليلاً بالتبادل)
        final int M = 5;
        for (int i = 0; i < M; i++) {
            float t = (i + offset) / M;
            float e = t * t;
            if (e < 0.03f) continue;
            float y = y0 + (y1 - y0) * e;
            float hw = hw0 + (hw1 - hw0) * e;
            float ph = r * 0.20f * e, off = carW * 0.10f * e + 2;
            p.setStrokeWidth(Math.max(1f, r * 0.012f * e + 1));
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = cx + sd * (hw + off);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x99AEB8C8);
                c.drawLine(x, y, x, y - ph, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFD27A);
                c.drawCircle(x, y - ph, Math.max(1.5f, r * 0.022f * e + 1), p);
            }
        }
        if (running && !lite() && (shown > 0.5f || Math.abs(d) > 0.3f)) postInvalidateDelayed(50);
        else last = 0;
    }

    private Boolean liteCache;
    private boolean lite() {
        if (liteCache == null) liteCache = Dash.lite(getContext());
        return liteCache;
    }
}
