package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * My Car · عدّاد السرعة الكبير لتصاميم Dash: قوس أبيض، تدريجات وأرقام، منطقة حمراء، مؤشر بلون الوضع، الرقم في الوسط،
 * وصورة السيارة أسفل الوسط (مع طريق صغير اختياري). حركة المؤشر ناعمة ولا تُرسم إلا عند تغيّر السرعة.
 */
public class Speedo extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path path = new Path();
    private float shown = 0, target = 0;
    private boolean ok;
    private int max = 220;
    private String unit = "km/h";
    private int accent = 0xFFFF3B30;
    private Bitmap car;
    /** عرض السيارة نسبةً لقطر العدّاد. */
    private float carScale = 0.42f;
    private boolean road;
    private Typeface font, fontBlack;
    static final float START = 150, SWEEP = 240;

    public Speedo(Context c) {
        super(c);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        try { setLayerType(LAYER_TYPE_SOFTWARE, null); } catch (Throwable ignored) { }   // لظل التوهج
    }

    public void setup(int max, String unit, int accent, Bitmap car, float carScale, boolean road, Typeface font, Typeface black) {
        this.max = max; this.unit = unit; this.accent = accent; this.car = car; this.carScale = carScale; this.road = road;
        this.font = font; this.fontBlack = black;
        invalidate();
    }

    public void setAccent(int a) { accent = a; invalidate(); }

    public void setSpeed(float v, boolean ok) {
        this.ok = ok;
        target = Math.max(0, Math.min(max, v));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h * 1.08f) / 2f - 8;
        if (r < 30) return;
        float cx = w / 2f, cy = r + 8;
        // حركة ناعمة نحو السرعة الجديدة
        float d = target - shown;
        if (Math.abs(d) > 0.4f) { shown += d * 0.18f; postInvalidateOnAnimation(); } else shown = target;

        // القوس الأبيض
        oval.set(cx - r, cy - r, cx + r, cy + r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.028f);
        p.setColor(0xF2FFFFFF);
        p.setShadowLayer(r * 0.03f, 0, 0, 0x99FFFFFF);
        c.drawArc(oval, START, SWEEP, false, p);
        p.clearShadowLayer();
        // المنطقة الحمراء (آخر 18%)
        float rz = r * 0.955f;
        oval.set(cx - rz, cy - rz, cx + rz, cy + rz);
        p.setStrokeWidth(r * 0.03f);
        p.setColor(0xFFFF3B30);
        c.drawArc(oval, START + SWEEP * 0.80f, SWEEP * 0.18f, false, p);

        // التدريجات والأرقام
        int step = max > 160 ? 10 : 5, label = max > 160 ? 20 : 20;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(font);
        for (int v = 0; v <= max; v += step) {
            double a = Math.toRadians(START + SWEEP * v / (float) max);
            boolean major = v % label == 0;
            float r1 = r * 0.90f, r2 = r * (major ? 0.79f : 0.85f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * (major ? 0.022f : 0.010f));
            p.setColor(major ? 0xFFFFFFFF : 0x99FFFFFF);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
            if (major && v > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(r * 0.105f);
                float rl = r * 0.66f;
                c.drawText(String.valueOf(v), cx + (float) Math.cos(a) * rl, cy + (float) Math.sin(a) * rl + r * 0.04f, p);
            }
        }

        // الطريق الصغير تحت السيارة
        float carW = r * 2 * carScale;
        if (road) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, cy + r * 0.25f, 0, cy + r * 0.95f, 0x00101418, 0xEE15181D, Shader.TileMode.CLAMP));
            path.reset();
            path.moveTo(cx - carW * 0.18f, cy + r * 0.25f);
            path.lineTo(cx + carW * 0.18f, cy + r * 0.25f);
            path.lineTo(cx + carW * 0.95f, cy + r * 0.95f);
            path.lineTo(cx - carW * 0.95f, cy + r * 0.95f);
            path.close();
            c.drawPath(path, p);
            p.setShader(null);
            p.setColor(0xCCFFFFFF);
            for (int i = 0; i < 5; i++) {
                float y0 = cy + r * (0.42f + i * 0.11f), len = r * 0.05f * (1 + i * 0.25f), wd = r * 0.012f * (1 + i * 0.4f);
                c.drawRect(cx - wd, y0, cx + wd, y0 + len, p);
            }
        }

        // السيارة
        if (car != null) {
            float ch = carW * car.getHeight() / (float) car.getWidth();
            float bottom = cy + r * (road ? 0.92f : 0.98f);
            oval.set(cx - carW / 2, bottom - ch, cx + carW / 2, bottom);
            p.setStyle(Paint.Style.FILL);
            p.setFilterBitmap(true);
            c.drawBitmap(car, null, oval, p);
        }

        // المؤشر
        double a = Math.toRadians(START + SWEEP * shown / max);
        float n1 = r * 0.55f, n2 = r * 0.93f;
        float x1 = cx + (float) Math.cos(a) * n1, y1 = cy + (float) Math.sin(a) * n1;
        float x2 = cx + (float) Math.cos(a) * n2, y2 = cy + (float) Math.sin(a) * n2;
        glow.setColor(accent);
        glow.setStrokeWidth(r * 0.06f);
        glow.setMaskFilter(new BlurMaskFilter(r * 0.035f, BlurMaskFilter.Blur.NORMAL));
        c.drawLine(x1, y1, x2, y2, glow);
        glow.setMaskFilter(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.03f);
        p.setColor(0xFFFFFFFF);
        c.drawLine(x1, y1, x2, y2, p);

        // الرقم والوحدة
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(fontBlack != null ? fontBlack : font);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(r * 0.24f);
        p.setShadowLayer(r * 0.03f, 0, 0, 0x88000000);
        String sv = ok ? String.valueOf(Math.round(shown < 1 ? 0 : shown)) : "--";
        c.drawText(sv, cx, cy - r * 0.18f, p);
        p.setTypeface(font);
        p.setTextSize(r * 0.1f);
        p.setColor(accent);
        c.drawText(unit, cx, cy + r * 0.0f, p);
        p.clearShadowLayer();
    }
}
