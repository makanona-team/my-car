package com.stradaos.launcher;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * My Car · ثيمات جاهزة (لون التمييز + الخلفية + شكل العدّاد) وخلفيات مرسومة بالكود (ليل، صحراء، بحر، سديم، كربون، مدينة).
 */
public class Themes {

    public static final String[] WALLS = {"none", "black", "night", "desert", "ocean", "nebula", "carbon", "city"};
    static final String[] WALLS_AR = {"بدون (لون الثيم)", "أسود كامل", "ليل صافٍ", "صحراء الفرات", "البحر", "سديم", "كربون", "ليل المدينة"};
    static final String[] WALLS_EN = {"None (theme colour)", "Pure black", "Clear night", "Euphrates desert", "Sea", "Nebula", "Carbon", "City night"};

    public static String wallName(int i) { return I18n.L(WALLS_AR[i], WALLS_EN[i]); }

    /** id، عربي، إنجليزي، الوضع (dark/light)، لون التمييز، الخلفية، العدّاد (0 رقمي، 1 عقارب، 2 مختلط)، شكل أيقونات الشريط. */
    public static final Object[][] PRESETS = {
        {"classic", "الكلاسيكي", "Classic", "dark", 0xFFFE3211, "none", 0, "circle"},
        {"tesla", "تيسلا البسيط", "Minimal (Tesla)", "dark", 0xFFE82127, "black", 0, "circle"},
        {"drive", "أوضاع القيادة", "Drive modes", "dark", 0xFF8E5CFF, "nebula", 2, "circle"},
        {"desert", "صحراء الفرات", "Euphrates desert", "dark", 0xFFFF8F1F, "desert", 1, "circle"},
        {"ocean", "البحر", "Sea", "dark", 0xFF14B8A6, "ocean", 1, "circle"},
        {"garage", "الكراج الرياضي", "Sport garage", "dark", 0xFFFFC107, "carbon", 1, "apps"},
        {"city", "ليل المدينة", "City night", "dark", 0xFF3D8BFF, "city", 2, "circle"},
        {"night", "ليل صافٍ", "Clear night", "dark", 0xFF5DADE2, "night", 0, "circle"},
        {"day", "النهار", "Daylight", "light", 0xFF1E5AA8, "none", 0, "circle"},
    };

    public static String presetName(int i) { return I18n.L((String) PRESETS[i][1], (String) PRESETS[i][2]); }

    public static void applyPreset(SharedPreferences p, int i) {
        Object[] t = PRESETS[i];
        p.edit().putString("theme_pack", (String) t[0])
            .putString("theme", (String) t[3])
            .putInt("accent", (Integer) t[4])
            .putString("wallpaper", (String) t[5])
            .putInt("speedo", (Integer) t[6])
            .putString("dock_style", (String) t[7])
            .apply();
    }

    public static int presetIndex(SharedPreferences p) {
        String id = p.getString("theme_pack", "classic");
        for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i][0].equals(id)) return i;
        return 0;
    }

    public static int wallIndex(String w) {
        for (int i = 0; i < WALLS.length; i++) if (WALLS[i].equals(w)) return i;
        return 0;
    }

    /** يرسم الخلفية بحجم الشاشة. null = بدون خلفية. */
    public static Bitmap render(String name, int w, int h) {
        if (w <= 0 || h <= 0 || name == null || "none".equals(name)) return null;
        // نرسم بدقة أقل ثم يكبّرها النظام (أخف على الشاشات القديمة)
        int bw = Math.min(w, 1280), bh = Math.max(1, (int) (h * (bw / (float) w)));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(7);
        if ("black".equals(name)) {
            c.drawColor(0xFF000000);
        } else if ("night".equals(name)) {
            p.setShader(new LinearGradient(0, 0, 0, bh, new int[]{0xFF050A18, 0xFF0B1A33, 0xFF13294B}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, p);
            stars(c, p, r, bw, bh, 160, 0.75f);
            p.setShader(new RadialGradient(bw * 0.82f, bh * 0.18f, bh * 0.09f, 0xFFF5F3E7, 0x00F5F3E7, Shader.TileMode.CLAMP));
            c.drawCircle(bw * 0.82f, bh * 0.18f, bh * 0.09f, p);
            p.setShader(null);
        } else if ("desert".equals(name)) {
            p.setShader(new LinearGradient(0, 0, 0, bh, new int[]{0xFF2B1B3D, 0xFF8A3B3B, 0xFFE07B39, 0xFFF4B860}, new float[]{0f, 0.38f, 0.62f, 0.8f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, p);
            p.setShader(new RadialGradient(bw * 0.3f, bh * 0.62f, bh * 0.16f, 0xFFFFE3A3, 0x00FFE3A3, Shader.TileMode.CLAMP));
            c.drawCircle(bw * 0.3f, bh * 0.62f, bh * 0.16f, p);
            p.setShader(null);
            dune(c, p, bw, bh, 0.70f, 0xFF7A3E22, 0.05f, 1.3f);
            dune(c, p, bw, bh, 0.78f, 0xFF4E2716, 0.06f, 0.9f);
            dune(c, p, bw, bh, 0.88f, 0xFF2A150C, 0.04f, 1.7f);
        } else if ("ocean".equals(name)) {
            p.setShader(new LinearGradient(0, 0, 0, bh, new int[]{0xFF04202B, 0xFF07485A, 0xFF0A7A86}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, p);
            p.setShader(null);
            for (int i = 0; i < 6; i++) {
                float y = bh * (0.45f + i * 0.1f);
                int a = 40 + i * 18;
                p.setColor((a << 24) | 0x0D9DA8);
                wave(c, p, bw, bh, y, bh * 0.03f, 1.5f + i * 0.3f, i * 0.9f);
            }
        } else if ("nebula".equals(name)) {
            c.drawColor(0xFF07060F);
            blob(c, p, bw * 0.25f, bh * 0.35f, bh * 0.7f, 0x995B2A86);
            blob(c, p, bw * 0.7f, bh * 0.6f, bh * 0.8f, 0x88203A8C);
            blob(c, p, bw * 0.55f, bh * 0.2f, bh * 0.5f, 0x66C2185B);
            stars(c, p, r, bw, bh, 220, 0.9f);
        } else if ("carbon".equals(name)) {
            c.drawColor(0xFF111214);
            float t = Math.max(6, bh / 60f);
            for (float y = 0; y < bh; y += t) {
                for (float x = 0; x < bw; x += t) {
                    boolean k = ((int) (x / t) + (int) (y / t)) % 2 == 0;
                    p.setShader(new LinearGradient(x, y, x + t, y + t, k ? 0xFF24272C : 0xFF181A1D, k ? 0xFF16181B : 0xFF222529, Shader.TileMode.CLAMP));
                    c.drawRect(x, y, x + t, y + t, p);
                }
            }
            p.setShader(new RadialGradient(bw / 2f, bh / 2f, bw * 0.7f, 0x00000000, 0xAA000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, p);
            p.setShader(null);
        } else if ("city".equals(name)) {
            p.setShader(new LinearGradient(0, 0, 0, bh, new int[]{0xFF070B16, 0xFF141E3A, 0xFF2B2550}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, p);
            p.setShader(null);
            stars(c, p, r, bw, (int) (bh * 0.5f), 70, 0.5f);
            float x = 0;
            while (x < bw) {
                float bwid = bw * (0.03f + r.nextFloat() * 0.05f);
                float bht = bh * (0.18f + r.nextFloat() * 0.32f);
                p.setColor(0xFF0A0E1A);
                c.drawRect(x, bh - bht, x + bwid, bh, p);
                // نوافذ مضاءة
                for (float wy = bh - bht + 8; wy < bh - 6; wy += 12) {
                    for (float wx = x + 5; wx < x + bwid - 5; wx += 9) {
                        if (r.nextFloat() < 0.18f) { p.setColor(0x99FFD27A); c.drawRect(wx, wy, wx + 4, wy + 5, p); }
                    }
                }
                x += bwid + 2;
            }
        }
        return b;
    }

    private static void stars(Canvas c, Paint p, Random r, int w, int h, int n, float maxA) {
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = 0.6f + r.nextFloat() * 1.6f;
            int a = (int) (255 * maxA * (0.3f + r.nextFloat() * 0.7f));
            p.setColor((a << 24) | 0xFFFFFF);
            c.drawCircle(x, y, s, p);
        }
    }

    private static void blob(Canvas c, Paint p, float x, float y, float rad, int col) {
        p.setShader(new RadialGradient(x, y, rad, col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad, p);
        p.setShader(null);
    }

    private static void dune(Canvas c, Paint p, int w, int h, float base, int col, float amp, float freq) {
        Path path = new Path();
        path.moveTo(0, h);
        for (int x = 0; x <= w; x += 8) {
            double y = h * base - Math.sin(x / (double) w * Math.PI * freq + base * 5) * h * amp;
            path.lineTo(x, (float) y);
        }
        path.lineTo(w, h);
        path.close();
        p.setColor(col);
        c.drawPath(path, p);
    }

    private static void wave(Canvas c, Paint p, int w, int h, float y0, float amp, float freq, float phase) {
        Path path = new Path();
        path.moveTo(0, h);
        for (int x = 0; x <= w; x += 8) path.lineTo(x, (float) (y0 + Math.sin(x / (double) w * Math.PI * 2 * freq + phase) * amp));
        path.lineTo(w, h);
        path.close();
        c.drawPath(path, p);
    }
}
