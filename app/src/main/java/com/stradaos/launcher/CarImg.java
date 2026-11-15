package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.InputStream;

/**
 * My Car · صورة السيارة في التصاميم الجديدة: «خلفية» (للعدّاد ووضع القيادة) و«جانبية» (لبطاقة السيارة الكبيرة).
 * صورة المستخدم أولاً (من الشاشة أو USB، بعد إزالة الخلفية)، وإلا مجسّم Car Concept المصيَّر بلون السيارة المختار.
 */
public class CarImg {

    /** ألوان المجسّم المصيَّر (assets/car/<view>_<color>.png). */
    static final String[] COLORS = {"white", "silver", "black", "red", "blue", "gray"};
    static final int[] RGB = {0xFFF2F4F7, 0xFFB8BEC6, 0xFF1C1E22, 0xFFC62828, 0xFF1E5AA8, 0xFF5F6670};

    static File rearFile(Context c) { return new File(c.getFilesDir(), "car_rear.png"); }

    /** لون المجسّم الأقرب للون السيارة المختار في الإعدادات. */
    static String colorKey(Context c) {
        int want = c.getSharedPreferences("launcher", 0).getInt("car_art_color", CarArt.COLORS[0]);
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            double d = Math.pow(Color.red(want) - Color.red(RGB[i]), 2) + Math.pow(Color.green(want) - Color.green(RGB[i]), 2)
                    + Math.pow(Color.blue(want) - Color.blue(RGB[i]), 2);
            if (d < bd) { bd = d; best = i; }
        }
        return COLORS[best];
    }

    public static Bitmap rear(Context c) {
        File f = rearFile(c);
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        return asset(c, "car/rear_" + colorKey(c) + ".png");
    }

    public static Bitmap side(Context c) {
        try {
            Bitmap user = CarPhoto.load(c);
            if (user != null) return user;
        } catch (Throwable ignored) { }
        return asset(c, "car/side_" + colorKey(c) + ".png");
    }

    static Bitmap asset(Context c, String path) {
        try {
            InputStream in = c.getAssets().open(path);
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Throwable e) {
            try {
                InputStream in = c.getAssets().open(path.replaceAll("_[a-z]+\\.png$", "_white.png"));
                Bitmap b = BitmapFactory.decodeStream(in);
                in.close();
                return b;
            } catch (Throwable e2) { return null; }
        }
    }

    /** يقصّ الصورة على الجزء المرئي (غير الشفاف). */
    static Bitmap trim(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), minX = w, minY = h, maxX = -1, maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) if ((row[x] >>> 24) > 30) {
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) return b;
        return Bitmap.createBitmap(b, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** يحفظ صورة السيارة الخلفية بعد إزالة الخلفية وقصّها. */
    public static boolean saveRear(Context c, Bitmap src) {
        try {
            Bitmap b = CarPhoto.scaled(src, CarPhoto.MAX);
            if (!CarPhoto.hasTransparency(b)) b = CarPhoto.removeBackground(b, c.getSharedPreferences("launcher", 0).getInt("photo_cut", 1));
            b = trim(b);
            java.io.FileOutputStream os = new java.io.FileOutputStream(rearFile(c));
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            return true;
        } catch (Throwable e) {
            Guard.log("CarImg.saveRear: " + e);
            return false;
        }
    }
}
