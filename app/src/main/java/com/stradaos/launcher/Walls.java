package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.InputStream;

/**
 * My Car · خلفيات التصاميم الجديدة: صور حقيقية مدمجة (Unsplash)، أو صورة من الشاشة/USB، أو ثيم من الإنترنت، أو بدون.
 * القيمة في dash_wall: theme | none | مفتاح صورة مدمجة | file:/مسار
 */
public class Walls {

    /** مفتاح، عربي، إنجليزي. الصورة في assets/walls/<مفتاح>.jpg */
    public static final String[][] LIST = {
        {"night_road", "طريق ليلي", "Night road"},
        {"nebula", "سديم", "Nebula"},
        {"city_night", "مدينة ليلية", "City at night"},
        {"desert_road", "طريق الصحراء", "Desert road"},
        {"neon", "أضواء النيون", "Neon lights"},
        {"forest", "غابة داكنة", "Dark forest"},
        {"ocean_night", "القمر", "Full moon"},
        {"palmyra", "تدمر عند الغروب", "Palmyra at sunset"},
    };

    public static String name(int i) { return I18n.L(LIST[i][1], LIST[i][2]); }

    /** الخلفية الافتراضية لكل تصميم («تبع الثيم»). */
    public static String forLayout(String layout) {
        if ("center".equals(layout)) return "night_road";
        if ("main".equals(layout)) return "desert_road";
        if ("tesla".equals(layout)) return "night_road";
        if ("drive".equals(layout)) return "none";
        return "nebula";   // garage, modes
    }

    public static String current(SharedPreferences p, String layout) {
        String w = p.getString("dash_wall", "theme");
        return "theme".equals(w) ? forLayout(layout) : w;
    }

    /** يحمّل الخلفية بحجم مناسب للشاشة (null = بدون). */
    public static Bitmap load(Context c, String key, int w, int h) {
        if (key == null || "none".equals(key)) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            if (key.startsWith("file:")) {
                File f = new File(key.substring(5));
                if (!f.exists()) return null;
                BitmapFactory.Options b = new BitmapFactory.Options();
                b.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), b);
                o.inSampleSize = sample(b.outWidth, b.outHeight, w, h);
                return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            }
            InputStream in = c.getAssets().open("walls/" + key + ".jpg");
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    static int sample(int sw, int sh, int w, int h) {
        int s = 1;
        while (sw / (s * 2) >= w && sh / (s * 2) >= h) s *= 2;
        return s;
    }

    /** قصّ الصورة لتملأ الشاشة (center crop). */
    public static Bitmap fill(Bitmap src, int w, int h) {
        if (src == null || w <= 0 || h <= 0) return src;
        float sc = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / sc), ch = Math.round(h / sc);
        int x = Math.max(0, (src.getWidth() - cw) / 2), y = Math.max(0, (src.getHeight() - ch) / 2);
        try {
            Bitmap crop = Bitmap.createBitmap(src, x, y, Math.min(cw, src.getWidth() - x), Math.min(ch, src.getHeight() - y));
            return Bitmap.createScaledBitmap(crop, w, h, true);
        } catch (Throwable e) {
            return src;
        }
    }
}
