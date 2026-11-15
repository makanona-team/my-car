package com.stradaos.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * My Car · رسوم سيارات أصلية (منظر جانبي) بعدة أنواع وألوان، تظهر في بطاقة السيارة بدل الصورة.
 * الرسم كله بالكود (بدون صور جاهزة): هيكل بتدرج لوني، زجاج، عجلات بجنوط، أضواء، وظل.
 */
public class CarArt {

    public static final String[] TYPES = {"suv", "sedan", "taxi", "pickup", "hatch"};
    static final String[] TYPES_AR = {"جيب / SUV", "سيدان", "تكسي", "بيك أب", "هاتشباك"};
    static final String[] TYPES_EN = {"SUV / Jeep", "Sedan", "Taxi", "Pickup", "Hatchback"};

    public static final int[] COLORS = {0xFFF2F4F7, 0xFF1C1E22, 0xFFB8BEC6, 0xFF5F6670, 0xFFC62828, 0xFF1E5AA8,
            0xFF1B2A49, 0xFF2E7D32, 0xFFD7C9A7, 0xFFF9C80E, 0xFF6D4C41, 0xFFE65100};
    static final String[] COLORS_AR = {"أبيض", "أسود", "فضي", "رمادي", "أحمر", "أزرق", "كحلي", "أخضر", "بيج", "أصفر", "بني", "برتقالي"};
    static final String[] COLORS_EN = {"White", "Black", "Silver", "Grey", "Red", "Blue", "Navy", "Green", "Beige", "Yellow", "Brown", "Orange"};

    public static String typeName(int i) { return I18n.L(TYPES_AR[i], TYPES_EN[i]); }
    public static String colorName(int i) { return I18n.L(COLORS_AR[i], COLORS_EN[i]); }

    public static int typeIndex(String t) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(t)) return i;
        return 0;
    }

    // ── الأشكال على لوحة 1000×420 (السيارة متجهة لليمين) ──
    // كل شكل: نقاط الهيكل، النوافذ، مراكز العجلات ونصف قطرها
    private static final float[] SUV_BODY = {40, 335, 34, 190, 68, 116, 600, 108, 742, 192, 930, 214, 974, 248, 980, 318, 946, 344, 40, 344};
    private static final float[][] SUV_WIN = {{96, 192, 112, 134, 268, 130, 268, 192}, {284, 192, 284, 130, 468, 126, 468, 192}, {484, 192, 484, 126, 592, 122, 708, 192}};
    private static final float[] SUV_WHEELS = {222, 790, 335, 80};

    private static final float[] SEDAN_BODY = {40, 330, 30, 282, 58, 238, 212, 216, 304, 206, 384, 132, 600, 126, 724, 206, 904, 230, 966, 260, 976, 320, 942, 340, 40, 340};
    private static final float[][] SEDAN_WIN = {{318, 204, 392, 144, 484, 142, 484, 204}, {500, 204, 500, 142, 592, 140, 700, 204}};
    private static final float[] SEDAN_WHEELS = {232, 782, 330, 70};

    private static final float[] PICKUP_BODY = {40, 332, 34, 214, 424, 214, 432, 120, 604, 114, 724, 198, 924, 220, 972, 254, 978, 320, 942, 344, 40, 344};
    private static final float[][] PICKUP_WIN = {{450, 198, 450, 134, 592, 130, 700, 198}};
    private static final float[] PICKUP_WHEELS = {222, 790, 334, 78};

    private static final float[] HATCH_BODY = {40, 330, 34, 240, 60, 152, 150, 128, 520, 122, 690, 200, 890, 226, 960, 256, 970, 320, 936, 342, 40, 342};
    private static final float[][] HATCH_WIN = {{92, 200, 140, 146, 330, 138, 330, 200}, {346, 200, 346, 138, 500, 132, 660, 200}};
    private static final float[] HATCH_WHEELS = {212, 760, 330, 68};

    public static Bitmap render(String type, int color, int w, int h) {
        if (w <= 0 || h <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float sc = Math.min(w / 1000f, h / 420f);
        c.translate((w - 1000 * sc) / 2f, (h - 420 * sc) / 2f);
        c.scale(sc, sc);
        draw(c, type, color);
        return bmp;
    }

    static void draw(Canvas c, String type, int color) {
        float[] body, wheels;
        float[][] wins;
        if ("sedan".equals(type) || "taxi".equals(type)) { body = SEDAN_BODY; wins = SEDAN_WIN; wheels = SEDAN_WHEELS; }
        else if ("pickup".equals(type)) { body = PICKUP_BODY; wins = PICKUP_WIN; wheels = PICKUP_WHEELS; }
        else if ("hatch".equals(type)) { body = HATCH_BODY; wins = HATCH_WIN; wheels = HATCH_WHEELS; }
        else { body = SUV_BODY; wins = SUV_WIN; wheels = SUV_WHEELS; }
        if ("taxi".equals(type)) color = 0xFFF9C80E;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float wy = wheels[2], wr = wheels[3];

        // ظل على الأرض
        p.setShader(new RadialGradient(500, wy + wr * 0.95f, 520, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1, 0.12f, 500, wy + wr * 0.95f);
        c.drawCircle(500, wy + wr * 0.95f, 520, p);
        c.restore();
        p.setShader(null);

        // الهيكل بتدرج: أفتح في الأعلى وأغمق في الأسفل
        Path bp = poly(body);
        float top = minY(body), bottom = maxY(body);
        p.setShader(new LinearGradient(0, top, 0, bottom, new int[]{lighten(color, 0.28f), color, darken(color, 0.35f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(bp, p);
        p.setShader(null);

        // خط الكتف (لمعة)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        p.setColor(lighten(color, 0.45f) & 0x88FFFFFF);
        float shoulder = wins[0][1] + 18;
        c.drawLine(minX(body) + 40, shoulder, maxX(body) - 70, shoulder + 8, p);
        // خطوط الأبواب: بين النوافذ (عمود B) وأمام الباب الأمامي
        p.setStrokeWidth(3);
        p.setColor(darken(color, 0.45f));
        for (int i = 0; i + 1 < wins.length; i++) {
            float x = (maxX(wins[i]) + minX(wins[i + 1])) / 2f;
            c.drawLine(x, wins[i][1], x + 4, wy - wr * 0.2f, p);
        }
        float[] fwin = wins[wins.length - 1];
        c.drawLine(fwin[6] + 6, fwin[7], fwin[6] - 8, wy - wr * 0.2f, p);
        p.setStyle(Paint.Style.FILL);

        // الأقواس حول العجلات
        p.setColor(darken(color, 0.6f));
        for (int k = 0; k < 2; k++) c.drawCircle(wheels[k], wy, wr * 1.13f, p);
        // الحافة السفلية
        p.setColor(darken(color, 0.5f));
        c.drawRect(wheels[0] + wr, wy - 6, wheels[1] - wr, bottom, p);

        // الزجاج
        for (float[] wv : wins) {
            Path wp = poly(wv);
            p.setShader(new LinearGradient(wv[0], minY(wv), wv[0] + 120, maxY(wv), new int[]{0xFF56677A, 0xFF27313C, 0xFF1A2129},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(wp, p);
            p.setShader(null);
        }

        // مصابيح
        float fx = maxX(body), rx = minX(body);
        p.setColor(0xFFFFF5D6);
        c.drawRoundRect(new RectF(fx - 46, body[body.length - 9] - 8, fx - 6, body[body.length - 9] + 12), 8, 8, p);
        p.setColor(0xFFD32F2F);
        c.drawRoundRect(new RectF(rx - 2, body[3] + 8, rx + 22, body[3] + 40), 6, 6, p);
        // مقبض ومرآة
        p.setColor(darken(color, 0.3f));
        c.drawRoundRect(new RectF(wins[wins.length - 1][0] + 30, shoulder + 16, wins[wins.length - 1][0] + 72, shoulder + 24), 4, 4, p);
        float[] fw = wins[wins.length - 1];
        p.setColor(darken(color, 0.15f));
        c.drawRoundRect(new RectF(fw[6] - 52, fw[7] - 26, fw[6] - 12, fw[7] - 6), 6, 6, p);

        // التكسي: لافتة السقف وشريط مربعات
        if ("taxi".equals(type)) {
            float roofY = minY(body);
            p.setColor(0xFF1C1E22);
            c.drawRoundRect(new RectF(430, roofY - 40, 560, roofY + 2), 10, 10, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(30);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("TAXI", 495, roofY - 9, p);
            float sy = shoulder + 34;
            for (int i = 0; i < 26; i++) {
                float x = 300 + i * 18;
                p.setColor(i % 2 == 0 ? 0xFF1C1E22 : 0xFFFFFFFF);
                c.drawRect(x, sy, x + 18, sy + 12, p);
                p.setColor(i % 2 == 0 ? 0xFFFFFFFF : 0xFF1C1E22);
                c.drawRect(x, sy + 12, x + 18, sy + 24, p);
            }
        }

        // العجلات: إطار، جنط، براغي
        for (int k = 0; k < 2; k++) {
            float cx = wheels[k];
            p.setColor(0xFF15171A);
            c.drawCircle(cx, wy, wr, p);
            p.setShader(new RadialGradient(cx - wr * 0.2f, wy - wr * 0.2f, wr * 0.75f, 0xFFE3E7EC, 0xFF7B838D, Shader.TileMode.CLAMP));
            c.drawCircle(cx, wy, wr * 0.62f, p);
            p.setShader(null);
            p.setColor(0xFF3A4048);
            p.setStrokeWidth(wr * 0.09f);
            for (int s = 0; s < 5; s++) {
                double a = Math.toRadians(s * 72 + 18);
                c.drawLine(cx + (float) Math.cos(a) * wr * 0.18f, wy + (float) Math.sin(a) * wr * 0.18f,
                        cx + (float) Math.cos(a) * wr * 0.56f, wy + (float) Math.sin(a) * wr * 0.56f, p);
            }
            p.setColor(0xFF2B3036);
            c.drawCircle(cx, wy, wr * 0.16f, p);
        }
    }

    private static Path poly(float[] pts) {
        Path p = new Path();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i + 1 < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.close();
        return p;
    }

    private static float minY(float[] a) { float m = Float.MAX_VALUE; for (int i = 1; i < a.length; i += 2) m = Math.min(m, a[i]); return m; }
    private static float maxY(float[] a) { float m = -Float.MAX_VALUE; for (int i = 1; i < a.length; i += 2) m = Math.max(m, a[i]); return m; }
    private static float minX(float[] a) { float m = Float.MAX_VALUE; for (int i = 0; i < a.length; i += 2) m = Math.min(m, a[i]); return m; }
    private static float maxX(float[] a) { float m = -Float.MAX_VALUE; for (int i = 0; i < a.length; i += 2) m = Math.max(m, a[i]); return m; }

    static int lighten(int c, float f) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        return Color.argb(255, (int) (r + (255 - r) * f), (int) (g + (255 - g) * f), (int) (b + (255 - b) * f));
    }

    static int darken(int c, float f) {
        return Color.argb(255, (int) (Color.red(c) * (1 - f)), (int) (Color.green(c) * (1 - f)), (int) (Color.blue(c) * (1 - f)));
    }
}
