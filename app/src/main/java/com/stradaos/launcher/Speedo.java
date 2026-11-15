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
        lite = c.getSharedPreferences("launcher", 0).getBoolean("dash_lite", false);
        // التوهج يحتاج رسماً برمجياً؛ في الوضع الخفيف يُرسم بالمعالج الرسومي بدون توهج
        if (!lite) try { setLayerType(LAYER_TYPE_SOFTWARE, null); } catch (Throwable ignored) { }
    }

    private final boolean lite;
    /** الأجزاء الثابتة (القوس والتدريجات والأرقام) تُرسم مرة واحدة في صورة، وكل إطار يرسم المؤشر والرقم فقط. */
    private Bitmap face;
    private BlurMaskFilter blur;
    private float blurR;
    private String faceKey;

    public void setup(int max, String unit, int accent, Bitmap car, float carScale, boolean road, Typeface font, Typeface black) {
        this.max = max; this.unit = unit; this.accent = accent; this.car = car; this.carScale = carScale; this.road = road;
        this.font = font; this.fontBlack = black;
        face = null;
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
        if (Math.abs(d) > 0.4f) { shown += d * 0.22f; postInvalidateDelayed(33); } else shown = target;

        String key = (int) w + "x" + (int) h + max + I18n.arabicDigits;
        if (face == null || !key.equals(faceKey)) {
            try {
                face = Bitmap.createBitmap((int) w, (int) h, Bitmap.Config.ARGB_8888);
                Canvas fc = new Canvas(face);
            // القوس الأبيض
            oval.set(cx - r, cy - r, cx + r, cy + r);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(r * 0.028f);
            p.setColor(0xF2FFFFFF);
            if (!lite) p.setShadowLayer(r * 0.03f, 0, 0, 0x99FFFFFF);
            fc.drawArc(oval, START, SWEEP, false, p);
            p.clearShadowLayer();
            // المنطقة الحمراء (آخر 18%)
            float rz = r * 0.955f;
            oval.set(cx - rz, cy - rz, cx + rz, cy + rz);
            p.setStrokeWidth(r * 0.03f);
            p.setColor(0xFFFF3B30);
            fc.drawArc(oval, START + SWEEP * 0.80f, SWEEP * 0.18f, false, p);

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
                fc.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
                if (major && v > 0) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTextSize(r * 0.092f);
                    float rl = r * 0.66f;
                    fc.drawText(I18n.num(String.valueOf(v)), cx + (float) Math.cos(a) * rl, cy + (float) Math.sin(a) * rl + r * 0.04f, p);
                }
            }


                faceKey = key;
            } catch (Throwable e) { face = null; }
        }
        if (face != null) c.drawBitmap(face, 0, 0, null);

        float carW = r * 2 * carScale;   // الطريق الصغير يرسمه RoadView تحت العدّاد

        // السيارة
        if (car != null) {
            // تحت رقم السرعة وداخل حدود العدّاد (لا تُقصّ من الأسفل)
            float bottom = Math.min(cy + r * (road ? 0.92f : 0.95f), getHeight() - 2);
            float top = cy + r * 0.34f;
            float cw = carW, ch = cw * car.getHeight() / (float) car.getWidth();
            if (ch > bottom - top) { ch = Math.max(bottom - top, r * 0.2f); cw = ch * car.getWidth() / (float) car.getHeight(); }
            oval.set(cx - cw / 2, bottom - ch, cx + cw / 2, bottom);
            p.setStyle(Paint.Style.FILL);
            p.setFilterBitmap(true);
            c.drawBitmap(car, null, oval, p);
        }

        // المؤشر
        double a = Math.toRadians(START + SWEEP * shown / max);
        float n1 = r * 0.75f, n2 = r * 0.95f;
        float x1 = cx + (float) Math.cos(a) * n1, y1 = cy + (float) Math.sin(a) * n1;
        float x2 = cx + (float) Math.cos(a) * n2, y2 = cy + (float) Math.sin(a) * n2;
        if (!lite) {
            glow.setColor(accent);
            glow.setStrokeWidth(r * 0.06f);
            if (blur == null || blurR != r) { blur = new BlurMaskFilter(r * 0.035f, BlurMaskFilter.Blur.NORMAL); blurR = r; }
            glow.setMaskFilter(blur);
            c.drawLine(x1, y1, x2, y2, glow);
            glow.setMaskFilter(null);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.05f);
            p.setColor((accent & 0x00FFFFFF) | 0x88000000);
            c.drawLine(x1, y1, x2, y2, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.03f);
        p.setColor(0xFFFFFFFF);
        c.drawLine(x1, y1, x2, y2, p);

        // الرقم والوحدة
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(fontBlack != null ? fontBlack : font);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(r * 0.24f);
        if (!lite) p.setShadowLayer(r * 0.03f, 0, 0, 0x88000000);
        String sv = I18n.num(ok ? String.valueOf(Math.round(shown < 1 ? 0 : shown)) : "--");
        c.drawText(sv, cx, cy - r * 0.18f, p);
        p.setTypeface(font);
        p.setTextSize(r * 0.1f);
        p.setColor(accent);
        c.drawText(unit, cx, cy + r * 0.0f, p);
        p.clearShadowLayer();
    }
}
