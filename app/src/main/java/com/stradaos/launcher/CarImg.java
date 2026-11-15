package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.InputStream;

/**
 * My Car · صور السيارة: ثلاث خانات يضعها المستخدم (خلفية مباشرة، من فوق وخلف، جانبية) تُعمَّم على كل التصاميم.
 * إذا لم توضع صورة تُرسم السيارة (النوع واللون من الإعدادات). لا يوجد مجسّم جاهز.
 */
public class CarImg {

    static final String[] SLOTS = {"rear", "top", "side", "rear_lit", "top_lit", "side_lit"};

    static File slotFile(Context c, String slot) { return new File(c.getFilesDir(), "car_" + slot + ".png"); }

    /** صورة المستخدم لهذه الخانة (null إن لم توجد). الجانبية تقرأ أيضاً الملف القديم car_custom.png. */
    static Bitmap user(Context c, String slot) {
        try {
            File f = slotFile(c, slot);
            if (f.exists()) { Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath()); if (b != null) return b; }
            if ("side".equals(slot)) return CarPhoto.load(c);
        } catch (Throwable ignored) { }
        return null;
    }

    static boolean has(Context c, String slot) {
        return slotFile(c, slot).exists() || ("side".equals(slot) && new File(c.getFilesDir(), CarPhoto.FILE).exists());
    }

    static boolean hasAny(Context c) {
        for (String s : SLOTS) if (has(c, s)) return true;
        return false;
    }

    /** أول صورة متاحة بالترتيب؛ في الوضع المضاء تُفضَّل الصورة المضاءة لكل خانة، وإلا العادية. */
    static Bitmap first(Context c, boolean lit, String... order) {
        for (String s : order) {
            if (lit) { Bitmap l = user(c, s + "_lit"); if (l != null) return l; }
            Bitmap b = user(c, s);
            if (b != null) return b;
        }
        return art(c);
    }

    /** للعدّاد: الخلفية، وإلا الأقرب لها. */
    public static Bitmap rear(Context c, boolean lit) { return first(c, lit, "rear", "top", "side"); }
    /** لوضع القيادة على الطريق: من فوق وخلف. */
    public static Bitmap top(Context c, boolean lit) { return first(c, lit, "top", "rear", "side"); }
    /** لبطاقة السيارة (تيسلا والكلاسيكي): الجانبية. */
    public static Bitmap side(Context c, boolean lit) { return first(c, lit, "side", "top", "rear"); }

    /** رسم السيارة بالنوع واللون المختارين (عند عدم وجود صورة). */
    static Bitmap art(Context c) {
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        String t = p.getString("car_art", "suv");
        if ("none".equals(t)) t = "suv";
        return CarArt.render(t, p.getInt("car_art_color", CarArt.COLORS[0]), 800, 336);
    }

    static void clearAll(Context c) {
        for (String s : SLOTS) slotFile(c, s).delete();
        CarPhoto.delete(c);
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


    /** يحفظ صورة خانة (rear/top/side وكل واحدة ومعها _lit): بدون أي إزالة للخلفية. */
    public static boolean saveSlot(Context c, String slot, Bitmap src) {
        try {
            // الصورة تُستعمل كما هي (المستخدم يزيل الخلفية مسبقاً): تصغير وقصّ الأطراف الشفافة فقط
            Bitmap b = trim(CarPhoto.scaled(src, CarPhoto.MAX));
            java.io.FileOutputStream os = new java.io.FileOutputStream(slotFile(c, slot));
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            if ("side".equals(slot)) CarPhoto.delete(c);   // الملف القديم يحل محله الجديد
            return true;
        } catch (Throwable e) {
            Guard.log("CarImg.saveSlot: " + e);
            return false;
        }
    }
}
