package com.stradaos.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * My Car 1.1 · التصاميم الجديدة للشاشة الرئيسية (مثل ثيمات MoCar، بتصميم خاص بنا):
 * center (الدائرة المركزية)، drive (القيادة)، garage (جراج 3D)، main (الرئيسي)، modes (أوضاع القيادة)، tesla (تيسلا البسيط).
 * لوحة افتراضية 1280×720 تُكبَّر/تُصغَّر لتملأ الشاشة. Main يمرّر السرعة والموقع؛ الباقي يتحدّث كل ثانية.
 */
public class Dash implements Obd.Listener {

    public static final String[] LAYOUTS = {"center", "drive", "garage", "main", "modes", "tesla", "classic"};
    static final String[] LAYOUTS_AR = {"الدائرة المركزية", "القيادة", "جراج 3D", "الرئيسي", "أوضاع القيادة", "تيسلا البسيط", "الكلاسيكي (StradaOS)"};
    static final String[] LAYOUTS_EN = {"Centre circle", "Drive", "Garage 3D", "Main", "Drive modes", "Tesla simple", "Classic (StradaOS)"};

    public static String layoutName(int i) { return I18n.L(LAYOUTS_AR[i], LAYOUTS_EN[i]); }

    public static int layoutIndex(String id) {
        for (int i = 0; i < LAYOUTS.length; i++) if (LAYOUTS[i].equals(id)) return i;
        return 2;
    }

    static final int GOLD = 0xFFFFC233, CARD_BG = 0x8C0E1218, CARD_LINE = 0x26FFFFFF;
    static final String[] MODES = {"night", "calm", "sport"};
    static final String[] MODES_AR = {"ليلي", "هادئ", "رياضي"};
    static final String[] MODES_EN = {"Night", "Calm", "Sport"};
    static final int[] MODE_COL = {0xFF3D8BFF, 0xFF20C997, 0xFFFF3B30};

    final Main m;
    final SharedPreferences prefs;
    final String layout;
    final FrameLayout view;
    final FrameLayout board;
    final float k;
    final int W, H;
    final Handler h = new Handler();
    Typeface font, black;
    private Bitmap wall;

    // عناصر تتحدث
    private Speedo speedo;
    private RoadView road;
    private DriveView drive;
    private TextView clockTv, dateTv, weatherTv, locTv, tripDist, tripDur, tripLast, mediaTitle, mediaSub, prayerBanner;
    private TextView obdState, obdRpm, obdTemp, obdFuel, obdVolt, obdThr, limitTv;
    private ImageView playIc, gpsDot, btDot, wifiDot;
    private LinearLayout prayerList;
    private TextView prayerTitleSub, nextName, nextLeft, prayerTileSub;
    private LiveMapView map;
    private TextView mapSpeed;
    private float lastKmh;
    private boolean gpsOk, paused;

    public Dash(Main main, FrameLayout parent, int w, int hgt, String layout) {
        this.m = main;
        this.prefs = main.getSharedPreferences("launcher", 0);
        this.layout = layout;
        this.W = w; this.H = hgt;
        k = Math.min(w / 1280f, hgt / 720f);
        try {
            font = Typeface.createFromAsset(main.getAssets(), "fonts/Cairo-Bold.ttf");
            black = Typeface.createFromAsset(main.getAssets(), "fonts/Cairo-Black.ttf");
        } catch (Throwable e) { font = Typeface.DEFAULT_BOLD; black = Typeface.DEFAULT_BOLD; }
        view = new FrameLayout(main);
        view.setClickable(true);
        // الخلفية
        String wk = Walls.current(prefs, layout);
        wall = Walls.fill(Walls.load(main, wk, w, hgt), w, hgt);
        if (wall != null) {
            view.setBackground(new BitmapDrawable(main.getResources(), wall));
            View dim = new View(main);
            dim.setBackgroundColor(0x40000000);
            view.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        } else {
            view.setBackgroundColor("drive".equals(layout) ? 0xFF0A1028 : 0xFF0B0E14);
        }
        board = new FrameLayout(main);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Math.round(1280 * k), Math.round(720 * k));
        blp.gravity = Gravity.CENTER;
        view.addView(board, blp);
        parent.addView(view, new FrameLayout.LayoutParams(-1, -1));

        if ("drive".equals(layout)) buildDrive();
        else {
            buildTop();
            if ("center".equals(layout)) buildCenter();
            else if ("tesla".equals(layout)) buildTesla();
            else buildGarage();
        }
        Obd.get(main).add(this);
        tick.run();
    }

    // ───────────────────────── أدوات ─────────────────────────

    int px(float v) { return Math.round(v * k); }

    void place(View v, float x, float y, float w, float hh) {
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(w < 0 ? -2 : px(w), hh < 0 ? -2 : px(hh));
        l.leftMargin = px(x);
        l.topMargin = px(y);
        board.addView(v, l);
    }

    TextView tv(String t, float size, int color, boolean heavy) {
        TextView v = new TextView(m);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k);
        v.setTypeface(heavy ? black : font);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setShadowLayer(4 * k, 0, 1 * k, 0xAA000000);
        return v;
    }

    ImageView icon(String name, int color) {
        ImageView v = new ImageView(m);
        try { v.setImageBitmap(m.asset("ic/" + name + ".png")); } catch (Throwable ignored) { }
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setColorFilter(color);
        return v;
    }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r * k);
        return g;
    }

    GradientDrawable cardBg() {
        GradientDrawable g = round(CARD_BG, 22);
        g.setStroke(Math.max(1, px(1)), CARD_LINE);
        return g;
    }

    GradientDrawable grad(int c1, int c2, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c1, c2});
        g.setCornerRadius(r * k);
        g.setStroke(Math.max(1, px(1)), 0x33FFFFFF);
        return g;
    }

    FrameLayout card(float x, float y, float w, float hh) {
        FrameLayout f = new FrameLayout(m);
        f.setBackground(cardBg());
        place(f, x, y, w, hh);
        return f;
    }

    void in(FrameLayout parent, View v, float x, float y, float w, float hh) {
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(w < 0 ? -2 : px(w), hh < 0 ? -2 : px(hh));
        l.leftMargin = px(x);
        l.topMargin = px(y);
        parent.addView(v, l);
    }

    /** نص بمحاذاة اليمين داخل عرض معيّن (للعربية). */
    TextView rtext(String t, float size, int color, boolean heavy) {
        TextView v = tv(t, size, color, heavy);
        v.setGravity(I18n.L("r", "l").equals("r") ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return v;
    }

    boolean ar() { return !"en".equals(I18n.lang); }

    /** هل تُعرض صور السيارة «المضاءة»؟ تلقائي = الشاشة في الوضع الليلي (عند تشغيل الأضواء)، أو يدوي. */
    boolean lit() { return litNow(m); }

    static boolean litNow(Main m) {
        String md = m.getSharedPreferences("launcher", 0).getString("car_lit", "auto");
        if ("on".equals(md)) return true;
        if ("off".equals(md)) return false;
        try {
            return (m.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable e) { return false; }
    }

    int accent() {
        if ("modes".equals(layout)) return MODE_COL[modeIndex()];
        return prefs.getInt("dash_accent", 0xFFFF3B30);
    }

    int modeIndex() {
        String md = prefs.getString("dash_mode", "night");
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(md)) return i;
        return 0;
    }

    boolean mph() { return m.useMph(); }

    // ───────────────────────── الشريط العلوي ─────────────────────────

    void buildTop() {
        float x = 20;
        x = pill(x, I18n.L("OBD", "OBD"), "speed", 0xFFC0392B, 0xFF7B1F16, 105, new Runnable() { public void run() { m.openScreen(ObdActivity.class); } });
        x = pill(x, I18n.L("الخريطة", "Map"), "map", 0xFF2E6FD8, 0xFF183F82, 125, new Runnable() { public void run() { m.runFnPublic("nav"); } });
        x = pill(x, I18n.L("الخلفية", "Background"), "palette", 0xFF8E44AD, 0xFF4E1F66, 130, new Runnable() { public void run() { chooseWall(); } });
        x = pill(x, I18n.L("ثيمات", "Themes"), null, 0xFF6C3BAA, 0xFF3C1F66, 112, new Runnable() { public void run() { chooseLayout(); } });
        if ("modes".equals(layout)) {
            int cur = modeIndex();
            for (int i = 0; i < MODES.length; i++) {
                final int mi = i;
                int c = MODE_COL[i];
                x = pill(x, I18n.L(MODES_AR[i], MODES_EN[i]), null, i == cur ? c : (c & 0x00FFFFFF) | 0x66000000,
                        i == cur ? CarArt.darken(c, 0.4f) : 0x66000000, 82, new Runnable() { public void run() {
                            prefs.edit().putString("dash_mode", MODES[mi]).apply(); m.rebuildPublic();
                        }});
            }
        }
        x = circleBtn(x + 4, "settings", 0xFF5B6B7A, 0xFFFFFFFF, new Runnable() { public void run() { m.showSettingsPublic(Main.C_SYRIA); } });
        x = circleBtn(x + 6, "directions_car", 0xFFF2F4F7, 0xFF20242C, new Runnable() { public void run() { enterDrive(); } });
        float ww = Math.min(230, 910 - (x + 12));
        if (ww >= 110) {   // في «أوضاع القيادة» أزرار الأوضاع تأخذ مكان الطقس
            weatherTv = tv("", 19, GOLD, false);
            weatherTv.setSingleLine(true);
            weatherTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            place(weatherTv, x + 12, 20, ww, 34);
        } else weatherTv = null;

        // يمين: GPS / بلوتوث / واي فاي + الاسم
        TextView brand = tv("My Car", 22, 0xFFFFFFFF, true);
        brand.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        place(brand, 1080, 16, 180, 40);
        wifiDot = dot(1030, "wifi", 0xFF2E86DE);
        btDot = dot(976, "bluetooth", 0xFF2C3E70);
        gpsDot = dot(922, "location_on", 0xFF27AE60);
        gpsDot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openScreen(PrayerActivity.class); } });
    }

    float pill(float x, String text, String ic, int c1, int c2, float w, final Runnable r) {
        LinearLayout b = new LinearLayout(m);
        b.setGravity(Gravity.CENTER);
        b.setBackground(grad(c1, c2, 25));
        if (ic != null) {
            ImageView iv = icon(ic, 0xFFFFFFFF);
            b.addView(iv, new LinearLayout.LayoutParams(px(22), px(22)));
        }
        TextView t = tv(text, 18, 0xFFFFFFFF, true);
        t.setPadding(px(6), 0, px(6), 0);
        if (ar()) b.addView(t, 0); else b.addView(t);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        place(b, x, 12, w, 48);
        return x + w + 10;
    }

    float circleBtn(float x, String ic, int bg, int fg, final Runnable r) {
        FrameLayout f = new FrameLayout(m);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bg);
        g.setStroke(Math.max(1, px(1)), 0x55FFFFFF);
        f.setBackground(g);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(26), px(26));
        l.gravity = Gravity.CENTER;
        f.addView(icon(ic, fg), l);
        f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        place(f, x, 11, 50, 50);
        return x + 50 + 8;
    }

    ImageView dot(float x, String ic, int bg) {
        ImageView v = icon(ic, 0xFFFFFFFF);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bg);
        v.setBackground(g);
        v.setPadding(px(11), px(11), px(11), px(11));
        place(v, x, 12, 48, 48);
        return v;
    }

    // ───────────────────────── جراج 3D / الرئيسي / أوضاع القيادة ─────────────────────────

    void buildGarage() {
        boolean small = "main".equals(layout);
        buildPrayerCard(20, 72, 450, 462);
        buildMiniPlayer(20, 542, 450, 70, true);
        // الوسط: العدّاد
        FrameLayout mid = new FrameLayout(m);
        place(mid, 480, 68, 600, 548);
        locTv = tv("", 15, 0xFFFFFFFF, true);
        locTv.setBackground(round(0xAA0E1218, 14));
        locTv.setPadding(px(12), px(4), px(12), px(4));
        in(mid, locTv, 20, 6, -1, -1);
        speedo = new Speedo(m);
        speedo.setup(mph() ? 140 : 220, mph() ? "mph" : I18n.L("كم/س", "km/h"), accent(), CarImg.rear(m, lit()), small ? 0.2f : 0.36f, small, font, black);
        if (small) { road = new RoadView(m); road.setup(small ? 0.2f : 0.36f); in(mid, road, 30, 40, 540, 508); }
        in(mid, speedo, 30, 40, 540, 508);
        // اليمين
        buildClock(1090, 72, 170, 140);
        if (small) buildObdMini(1090, 222, 170, 390);
        else buildTripCard(1090, 222, 170, 390);
        buildRoundDock(618);
    }

    // ───────────────────────── الدائرة المركزية ─────────────────────────

    void buildCenter() {
        tile(20, 72, I18n.L("الراديو", "Radio"), I18n.L("راديو FM", "FM radio"), "radio", "radio");
        tile(20, 262, I18n.L("التطبيقات", "Apps"), I18n.L("كل التطبيقات", "All apps"), "apps", "apps");
        prayerTileSub = tile(20, 452, I18n.L("الصلاة", "Prayer"), "", "mosque", "prayer");
        tile(980, 72, I18n.L("الملاحة", "Navigation"), I18n.L("الخرائط", "Maps"), "map", "nav");
        tile(980, 262, I18n.L("الهاتف", "Phone"), I18n.L("بلوتوث", "Bluetooth"), "call", "bt");
        tile(980, 452, I18n.L("الموسيقى", "Music"), I18n.L("موسيقى", "Music"), "music_note", "music");
        FrameLayout mid = card(310, 72, 660, 560);
        locTv = tv("", 15, 0xFFFFFFFF, true);
        locTv.setBackground(round(0xAA0E1218, 14));
        locTv.setPadding(px(12), px(4), px(12), px(4));
        in(mid, locTv, 18, 14, -1, -1);
        speedo = new Speedo(m);
        speedo.setup(mph() ? 140 : 220, mph() ? "mph" : I18n.L("كم/س", "km/h"), accent(), CarImg.rear(m, lit()), 0.2f, false, font, black);
        in(mid, speedo, 80, 50, 500, 500);
        buildMiniPlayer(20, 646, 1240, 66, false);
    }

    TextView tile(float x, float y, String title, String sub, String ic, final String fn) {
        FrameLayout t = card(x, y, 280, 180);
        in(t, icon(ic, 0xFFFFFFFF), 120, 22, 40, 40);
        TextView tt = rtext(title, 26, 0xFFFFFFFF, true);
        in(t, tt, 16, 82, 248, 44);
        TextView st = rtext(sub, 16, GOLD, false);
        in(t, st, 16, 128, 248, 30);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { run(fn); } });
        return st;
    }

    // ───────────────────────── تيسلا البسيط ─────────────────────────

    void buildTesla() {
        FrameLayout mc = card(20, 72, 620, 568);
        try {
            map = new LiveMapView(m, k, 22 * k);
            in(mc, map, 0, 0, 620, 568);
        } catch (Throwable e) { map = null; }
        mapSpeed = tv("--", 24, 0xFFFFFFFF, true);
        mapSpeed.setBackground(round(0xCC0E1218, 14));
        mapSpeed.setPadding(px(14), px(6), px(14), px(6));
        in(mc, mapSpeed, ar() ? 470 : 16, 14, -1, -1);
        locTv = tv("", 15, 0xFFFFFFFF, true);
        locTv.setBackground(round(0xCC0E1218, 14));
        locTv.setPadding(px(12), px(6), px(12), px(6));
        in(mc, locTv, ar() ? 300 : 16, 64, -1, -1);
        prayerBanner = rtext("", 18, 0xFF4ADE80, true);
        prayerBanner.setBackground(round(0xDD0E1218, 16));
        prayerBanner.setPadding(px(18), 0, px(18), 0);
        in(mc, prayerBanner, 0, 512, 620, 56);
        prayerBanner.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openScreen(PrayerActivity.class); } });

        FrameLayout rc = card(650, 72, 610, 568);
        TextView name = rtext(prefs.getString("car_name", "My Car"), 34, 0xFFFFFFFF, true);
        in(rc, name, 20, 16, 570, 54);
        ImageView car = new ImageView(m);
        car.setImageBitmap(CarImg.side(m, lit()));
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        in(rc, car, 40, 70, 530, 310);
        car.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { carMenu(); } });
        obdTemp = miniTile(rc, 410, 392, I18n.L("الحرارة", "Temp"), "thermostat");
        obdVolt = miniTile(rc, 215, 392, I18n.L("البطارية", "Battery"), "battery_charging_full");
        obdFuel = miniTile(rc, 20, 392, I18n.L("الوقود", "Fuel"), "local_gas_station");
        tripLast = stat(rc, 410, 486, I18n.L("آخر رحلة", "Last trip"));
        tripDur = stat(rc, 215, 486, I18n.L("مدة القيادة", "Drive time"));
        tripDist = stat(rc, 20, 486, I18n.L("المسافة", "Distance"));
        buildTextDock(648);
    }

    TextView miniTile(FrameLayout parent, float x, float y, String label, String ic) {
        FrameLayout t = new FrameLayout(m);
        t.setBackground(round(0xCC1A2030, 14));
        in(parent, t, x, y, 180, 82);
        LinearLayout lr = new LinearLayout(m);
        lr.setGravity(Gravity.CENTER_VERTICAL | (ar() ? Gravity.RIGHT : Gravity.LEFT));
        TextView l = tv(label, 15, GOLD, true);
        ImageView iv = icon(ic, GOLD);
        if (ar()) { lr.addView(l); lr.addView(iv, new LinearLayout.LayoutParams(px(18), px(18))); }
        else { lr.addView(iv, new LinearLayout.LayoutParams(px(18), px(18))); lr.addView(l); }
        in(t, lr, 12, 8, 156, 28);
        TextView v = rtext("--", 22, 0xFFFFFFFF, true);
        in(t, v, 12, 40, 156, 34);
        return v;
    }

    TextView stat(FrameLayout parent, float x, float y, String label) {
        TextView v = rtext("--", 26, 0xFFFFFFFF, true);
        in(parent, v, x, y, 180, 40);
        TextView l = rtext(label, 14, GOLD, false);
        in(parent, l, x, y + 42, 180, 24);
        return v;
    }

    // ───────────────────────── القيادة ─────────────────────────

    void buildDrive() {
        drive = new DriveView(m);
        drive.setup(mph() ? 130 : 200, mph() ? "mph" : I18n.L("كم/س", "km/h"), GOLD, CarImg.top(m, lit()), font, black);
        view.addView(drive, 0, new FrameLayout.LayoutParams(-1, -1));
        // أزرار علوية
        TextView exit = tv(I18n.L("خروج من وضع القيادة ✕", "Exit drive mode ✕"), 18, 0xFFFFFFFF, true);
        exit.setGravity(Gravity.CENTER);
        exit.setBackground(round(0x991A2440, 24));
        place(exit, 20, 14, 260, 48);
        exit.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { exitDrive(); } });
        TextView th = tv(I18n.L("ثيمات", "Themes"), 18, 0xFFFFFFFF, true);
        th.setGravity(Gravity.CENTER);
        th.setBackground(round(0x991A2440, 24));
        place(th, 292, 14, 120, 48);
        th.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseLayout(); } });
        clockTv = tv("", 54, 0xFFFFFFFF, true);
        clockTv.setGravity(Gravity.RIGHT);
        place(clockTv, 860, 10, 260, 70);
        dateTv = tv("", 16, GOLD, true);
        dateTv.setGravity(Gravity.RIGHT);
        place(dateTv, 860, 80, 260, 26);
        TextView brand = tv("My Car", 22, 0xFFFFFFFF, true);
        brand.setGravity(Gravity.RIGHT);
        place(brand, 1130, 22, 130, 36);
        // حد السرعة (اضغط لتغييره)
        limitTv = tv("", 30, 0xFF111111, true);
        limitTv.setGravity(Gravity.CENTER);
        GradientDrawable sign = new GradientDrawable();
        sign.setShape(GradientDrawable.OVAL);
        sign.setColor(0xFFFFFFFF);
        sign.setStroke(px(9), 0xFFE53935);
        limitTv.setBackground(sign);
        limitTv.setShadowLayer(0, 0, 0, 0);
        place(limitTv, 60, 230, 92, 92);
        limitTv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseLimit(); } });
        TextView limLbl = tv(I18n.L("تنبيه السرعة", "Speed alert"), 15, 0xFFFFFFFF, true);
        limLbl.setGravity(Gravity.CENTER);
        place(limLbl, 30, 330, 152, 28);
        // الموقع والحالة
        locTv = rtext("", 16, 0xFFFFFFFF, true);
        locTv.setBackground(round(0x991A2440, 16));
        locTv.setPadding(px(14), 0, px(14), 0);
        place(locTv, 900, 252, 360, 44);
        obdState = rtext("", 15, 0xFFFFFFFF, true);
        obdState.setBackground(round(0x991A2440, 16));
        obdState.setPadding(px(14), 0, px(14), 0);
        place(obdState, 900, 306, 360, 40);
        prayerBanner = tv("", 20, 0xFF4ADE80, true);
        prayerBanner.setGravity(Gravity.CENTER);
        prayerBanner.setBackground(round(0x991A2440, 18));
        place(prayerBanner, 440, 322, 400, 50);
        prayerBanner.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openScreen(PrayerActivity.class); } });
        // أزرار سفلية
        roundAt(40, 612, "map", 0xFF2E6FD8, "nav");
        roundAt(120, 612, "music_note", 0xFF8E44AD, "music");
        roundAt(1176, 612, "directions_car", 0xFFE53935, null);
    }

    void roundAt(float x, float y, String ic, int c, final String fn) {
        FrameLayout f = new FrameLayout(m);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CarArt.lighten(c, 0.2f), CarArt.darken(c, 0.25f)});
        g.setShape(GradientDrawable.OVAL);
        f.setBackground(g);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(32), px(32));
        l.gravity = Gravity.CENTER;
        f.addView(icon(ic, 0xFFFFFFFF), l);
        f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (fn == null) exitDrive(); else run(fn); } });
        place(f, x, y, 66, 66);
    }

    void enterDrive() {
        prefs.edit().putString("dash_prev", layout).putString("dash_layout", "drive").apply();
        m.rebuildPublic();
    }

    void exitDrive() {
        String prev = prefs.getString("dash_prev", "garage");
        if ("drive".equals(prev)) prev = "garage";
        prefs.edit().putString("dash_layout", prev).apply();
        m.rebuildPublic();
    }

    void chooseLimit() {
        final int[] vals = {0, 60, 80, 90, 100, 110, 120, 130, 140};
        String[] n = new String[vals.length];
        int cur = prefs.getInt("speed_limit", 120), sel = 6;
        for (int i = 0; i < vals.length; i++) {
            n[i] = vals[i] == 0 ? I18n.L("بدون تنبيه", "No alert") : vals[i] + (mph() ? " mph" : I18n.L(" كم/س", " km/h"));
            if (vals[i] == cur) sel = i;
        }
        new TB(m, Main.dlg()).raw().setTitle(I18n.L("التنبيه عند تجاوز السرعة", "Alert above speed"))
            .setSingleChoiceItems(n, sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss(); prefs.edit().putInt("speed_limit", vals[w]).apply(); updateLimit();
            }}).show();
    }

    void updateLimit() {
        if (limitTv == null) return;
        int lim = prefs.getInt("speed_limit", 120);
        limitTv.setText(lim == 0 ? "—" : String.valueOf(lim));
    }

    // ───────────────────────── البطاقات المشتركة ─────────────────────────

    void buildPrayerCard(float x, float y, float w, float hh) {
        FrameLayout c = card(x, y, w, hh);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openScreen(PrayerActivity.class); } });
        LinearLayout hd = new LinearLayout(m);
        hd.setGravity(Gravity.CENTER_VERTICAL | (ar() ? Gravity.RIGHT : Gravity.LEFT));
        TextView t = tv(I18n.L("أوقات الصلاة", "Prayer times"), 24, 0xFFFFFFFF, true);
        ImageView iv = icon("mosque", 0xFFFFFFFF);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(26), px(26));
        if (ar()) { hd.addView(t); il.leftMargin = px(8); hd.addView(iv, il); }
        else { il.rightMargin = px(8); hd.addView(iv, il); hd.addView(t); }
        in(c, hd, 18, 14, w - 36, 38);
        prayerTitleSub = rtext("", 15, GOLD, true);
        in(c, prayerTitleSub, 18, 54, w - 36, 26);
        FrameLayout nx = new FrameLayout(m);
        nx.setBackground(round(0x5527AE60, 16));
        in(c, nx, 18, 86, w - 36, 78);
        nextName = rtext("", 22, 0xFFFFFFFF, true);
        in(nx, nextName, 16, 8, w - 68, 34);
        nextLeft = rtext("", 16, GOLD, true);
        in(nx, nextLeft, 16, 44, w - 68, 26);
        prayerList = new LinearLayout(m);
        prayerList.setOrientation(LinearLayout.VERTICAL);
        in(c, prayerList, 18, 172, w - 36, hh - 186);
    }

    void buildMiniPlayer(float x, float y, float w, float hh, boolean green) {
        FrameLayout c = card(x, y, w, hh);
        float b = hh - 16;
        ImageView prev = playBtn("skip_previous", green ? 0xFF2E9E4F : 0xFF2A2F3A);
        playIc = playBtn("play_arrow", green ? 0xFF2E9E4F : 0xFFE53935);
        ImageView next = playBtn("skip_next", green ? 0xFF2E9E4F : 0xFF2A2F3A);
        prev.setOnClickListener(m.mediaClickPublic(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        playIc.setOnClickListener(m.mediaClickPublic(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        next.setOnClickListener(m.mediaClickPublic(KeyEvent.KEYCODE_MEDIA_NEXT));
        in(c, prev, 10, 8, b, b);
        in(c, playIc, 18 + b, 8, b, b);
        in(c, next, 26 + b * 2, 8, b, b);
        float tx = 40 + b * 3;
        mediaTitle = rtext(I18n.L("لا شيء قيد التشغيل", "Nothing playing"), 19, 0xFFFFFFFF, true);
        in(c, mediaTitle, tx, 8, w - tx - 18, 30);
        mediaSub = rtext(I18n.L("راديو أو بلوتوث أو USB", "Radio, Bluetooth or USB"), 13, GOLD, false);
        in(c, mediaSub, tx, 38, w - tx - 18, 22);
        View.OnClickListener open = new View.OnClickListener() { public void onClick(View v) { m.runFnPublic("music"); } };
        mediaTitle.setOnClickListener(open);
    }

    ImageView playBtn(String ic, int c) {
        ImageView v = icon(ic, 0xFFFFFFFF);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CarArt.lighten(c, 0.15f), CarArt.darken(c, 0.2f)});
        g.setShape(GradientDrawable.OVAL);
        v.setBackground(g);
        v.setPadding(px(12), px(12), px(12), px(12));
        return v;
    }

    void buildClock(float x, float y, float w, float hh) {
        FrameLayout c = card(x, y, w, hh);
        clockTv = rtext("", 36, 0xFFFFFFFF, true);
        clockTv.setMaxLines(1);
        clockTv.setEllipsize(null);
        in(c, clockTv, 10, 14, w - 20, 64);
        dateTv = rtext("", 14, GOLD, true);
        dateTv.setSingleLine(false);
        dateTv.setMaxLines(2);
        dateTv.setEllipsize(null);
        in(c, dateTv, 10, 82, w - 20, 48);
    }

    void buildTripCard(float x, float y, float w, float hh) {
        FrameLayout c = card(x, y, w, hh);
        TextView t = rtext(I18n.L("ملخص الرحلة", "Trip summary"), 16, 0xFFFFFFFF, true);
        in(c, t, 12, 14, w - 24, 28);
        float yy = 64;
        String[] lbl = {I18n.L("المسافة", "Distance"), I18n.L("مدة القيادة", "Drive time"), I18n.L("آخر رحلة", "Last trip")};
        TextView[] vals = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView l = rtext(lbl[i], 16, GOLD, true);
            in(c, l, 12, yy, w - 24, 26);
            vals[i] = rtext("--", 24, 0xFFFFFFFF, true);
            in(c, vals[i], 12, yy + 30, w - 24, 38);
            yy += 100;
        }
        tripDist = vals[0]; tripDur = vals[1]; tripLast = vals[2];
    }

    void buildObdMini(float x, float y, float w, float hh) {
        FrameLayout c = card(x, y, w, hh);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openScreen(ObdActivity.class); } });
        obdState = rtext("", 15, GOLD, true);
        in(c, obdState, 12, 14, w - 24, 26);
        obdRpm = rtext("--", 34, 0xFFFFFFFF, true);
        in(c, obdRpm, 12, 50, w - 24, 50);
        TextView rl = rtext("RPM", 14, GOLD, true);
        in(c, rl, 12, 100, w - 24, 22);
        String[] lbl = {I18n.L("الحرارة", "Temp"), I18n.L("البطارية", "Battery"), I18n.L("الدواسة", "Throttle")};
        TextView[] v = new TextView[3];
        float yy = 134;
        for (int i = 0; i < 3; i++) {
            TextView l = rtext(lbl[i], 15, GOLD, true);
            in(c, l, 12, yy, w - 24, 24);
            v[i] = rtext("--", 22, 0xFFFFFFFF, true);
            in(c, v[i], 12, yy + 26, w - 24, 32);
            yy += 66;
        }
        obdTemp = v[0]; obdVolt = v[1]; obdThr = v[2];
        TextView pick = tv(I18n.L("اختيار الوصلة", "Choose adapter"), 15, 0xFFFFFFFF, true);
        pick.setGravity(Gravity.CENTER);
        pick.setBackground(round(0xAA1E3A6E, 14));
        in(c, pick, 12, hh - 50, w - 24, 38);
        pick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { m.openScreen(ObdActivity.class); } });
    }

    // ───────────────────────── الأزرار السفلية ─────────────────────────

    static final String[] RD_DEF = {"bt", "music", "radio", "apps", "nav", "iradio"};
    static final int[] RD_COL = {0xFF27AE60, 0xFF8E44AD, 0xFFE74C3C, 0xFF16A2B8, 0xFF2E6FD8, 0xFF5DAE3C};

    String rdTarget(int i) { return prefs.getString("rd_" + i, "fn:" + RD_DEF[i]); }

    void buildRoundDock(float y) {
        int n = 6;
        float slot = 1240f / n;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String t = rdTarget(i);
            float cx = 20 + slot * (ar() ? (n - 1 - i) : i) + slot / 2;
            FrameLayout f = new FrameLayout(m);
            int c = RD_COL[i];
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CarArt.lighten(c, 0.25f), CarArt.darken(c, 0.25f)});
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(Math.max(1, px(1)), 0x44FFFFFF);
            f.setBackground(g);
            FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(30), px(30));
            l.gravity = Gravity.CENTER;
            Drawable appIc = t.startsWith("app:") ? appIcon(t.substring(4)) : null;
            if (appIc != null) {
                ImageView iv = new ImageView(m);
                iv.setImageDrawable(appIc);
                l = new FrameLayout.LayoutParams(px(40), px(40));
                l.gravity = Gravity.CENTER;
                f.addView(iv, l);
            } else f.addView(icon(fnIcon(t.substring(3)), 0xFFFFFFFF), l);
            place(f, cx - 33, y, 66, 66);
            TextView lbl = tv(rdName(t), 16, GOLD, true);
            lbl.setGravity(Gravity.CENTER);
            place(lbl, cx - slot / 2, y + 68, slot, 28);
            View.OnClickListener go = new View.OnClickListener() { public void onClick(View v) { runTarget(t); } };
            f.setOnClickListener(go);
            lbl.setOnClickListener(go);
            View.OnLongClickListener edit = new View.OnLongClickListener() { public boolean onLongClick(View v) { pickTarget(idx); return true; } };
            f.setOnLongClickListener(edit);
            lbl.setOnLongClickListener(edit);
        }
    }

    void buildTextDock(float y) {
        String[] ids = {"nav", "bt", "apps", "music", "radio", "settings"};
        String[] ics = {"map", "call", "apps", "music_note", "radio", "settings"};
        int n = ids.length;
        float slot = 1240f / n;
        for (int i = 0; i < n; i++) {
            final String id = ids[i];
            float cx = 20 + slot * (ar() ? i : (n - 1 - i)) + slot / 2;
            LinearLayout col = new LinearLayout(m);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(icon(ics[i], 0xFFFFFFFF), new LinearLayout.LayoutParams(px(30), px(30)));
            TextView l = tv(fnName(id), 16, GOLD, true);
            col.addView(l);
            col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { run(id); } });
            place(col, cx - slot / 2, y, slot, 66);
        }
    }

    String fnIcon(String id) {
        if ("bt".equals(id)) return "call";
        for (String[] f : Main.DOCK_FNS) if (f[0].equals(id)) return f[2];
        return "apps";
    }

    String fnName(String id) {
        if ("bt".equals(id)) return I18n.L("الهاتف", "Phone");
        if ("nav".equals(id)) return I18n.L("الملاحة", "Navigation");
        if ("apps".equals(id)) return I18n.L("التطبيقات", "Apps");
        if ("music".equals(id)) return I18n.L("الموسيقى", "Music");
        if ("radio".equals(id)) return I18n.L("الراديو", "Radio");
        if ("iradio".equals(id)) return I18n.L("راديو إنترنت", "Web radio");
        if ("settings".equals(id)) return I18n.L("الإعدادات", "Settings");
        for (String[] f : Main.DOCK_FNS) if (f[0].equals(id)) return I18n.t(f[1]);
        return id;
    }

    String rdName(String t) {
        if (t.startsWith("app:")) {
            try {
                ComponentName cn = ComponentName.unflattenFromString(t.substring(4));
                PackageManager pm = m.getPackageManager();
                return String.valueOf(pm.getActivityInfo(cn, 0).loadLabel(pm));
            } catch (Throwable e) { return "?"; }
        }
        return fnName(t.substring(3));
    }

    Drawable appIcon(String comp) {
        try {
            ComponentName cn = ComponentName.unflattenFromString(comp);
            return m.getPackageManager().getActivityIcon(cn);
        } catch (Throwable e) { return null; }
    }

    void run(String fn) {
        if ("prayer".equals(fn)) m.openScreen(PrayerActivity.class);
        else if ("iradio".equals(fn)) m.openScreen(RadioActivity.class);
        else if ("obd".equals(fn)) m.openScreen(ObdActivity.class);
        else if ("settings".equals(fn)) m.showSettingsPublic(Main.C_SYRIA);
        else m.runFnPublic(fn);
    }

    void runTarget(String t) {
        if (t.startsWith("app:")) {
            try {
                Intent it = new Intent(Intent.ACTION_MAIN);
                it.addCategory(Intent.CATEGORY_LAUNCHER);
                it.setComponent(ComponentName.unflattenFromString(t.substring(4)));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                m.startActivity(it);
            } catch (Throwable e) { m.toastPublic(I18n.L("تعذّر فتح التطبيق، اضغط مطولاً لتغييره", "Could not open the app; long-press to change it")); }
        } else run(t.substring(3));
    }

    /** ضغط مطوّل على زر سفلي: اختيار وظيفة أو أي تطبيق مثبّت. */
    void pickTarget(final int idx) {
        final List<String> targets = new ArrayList<String>();
        final List<String> names = new ArrayList<String>();
        String[] fns = {"bt", "music", "radio", "iradio", "apps", "nav", "prayer", "obd", "settings"};
        for (String f : fns) { targets.add("fn:" + f); names.add("★ " + fnName(f)); }
        try {
            PackageManager pm = m.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            final HashMap<ResolveInfo, String> lbl = new HashMap<ResolveInfo, String>();
            for (ResolveInfo r : ris) lbl.put(r, String.valueOf(r.loadLabel(pm)));
            Collections.sort(ris, new Comparator<ResolveInfo>() { public int compare(ResolveInfo a, ResolveInfo b) { return lbl.get(a).compareToIgnoreCase(lbl.get(b)); } });
            for (ResolveInfo r : ris) {
                if (r.activityInfo.packageName.equals(m.getPackageName())) continue;
                targets.add("app:" + new ComponentName(r.activityInfo.packageName, r.activityInfo.name).flattenToString());
                names.add(lbl.get(r));
            }
        } catch (Throwable ignored) { }
        names.add(I18n.L("↺ الافتراضي", "↺ Default"));
        new TB(m, Main.dlg()).raw().setTitle(I18n.L("ماذا يفتح هذا الزر؟", "What should this button open?"))
            .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w >= targets.size()) prefs.edit().remove("rd_" + idx).apply();
                else prefs.edit().putString("rd_" + idx, targets.get(w)).apply();
                m.rebuildPublic();
            }}).setNegativeButton(I18n.L("إلغاء", "Cancel"), null).show();
    }

    // ───────────────────────── التحديث ─────────────────────────

    private final Runnable tick = new Runnable() {
        public void run() {
            try { refresh(); } catch (Throwable e) { Guard.log("Dash: " + e); }
            if (!paused) h.postDelayed(this, 1000);
        }
    };

    private int refreshN;

    void refresh() {
        refreshN++;
        Calendar now = Calendar.getInstance();
        boolean h24 = m.use24h();
        if (clockTv != null) {
            String t = I18n.sdf(h24 ? "HH:mm" : "h:mm").format(now.getTime());
            clockTv.setText(h24 ? t : t + " " + (now.get(Calendar.AM_PM) == Calendar.AM ? I18n.L("ص", "AM") : I18n.L("م", "PM")));
        }
        if (dateTv != null) dateTv.setText(I18n.sdf(I18n.L("EEEE، d MMMM", "EEEE, d MMMM")).format(now.getTime()));
        Weather.Now wn = m.weatherNowPublic();
        if (weatherTv != null) weatherTv.setText(wn != null ? Math.round(wn.temp) + "°" + wn.unit + "  " + (wn.city != null ? wn.city : "") : "");
        Prayer.Place pl = Prayer.place(m);
        if (locTv != null) locTv.setText("📍 " + pl.name + (wn != null && "drive".equals(layout) ? "  ·  " + Math.round(wn.temp) + "°" : ""));
        if (refreshN % 5 == 1 || nextName == null) refreshPrayer(now, pl, h24);
        refreshMedia();
        refreshTrip();
        refreshStatus();
        if ("drive".equals(layout)) updateLimit();
    }

    void refreshPrayer(Calendar now, Prayer.Place pl, boolean h24) {
        long[] nx = Prayer.next(m, now.getTimeInMillis());
        String nn = nx == null ? "" : Prayer.name((int) nx[0]);
        Calendar nc = Calendar.getInstance();
        if (nx != null) nc.setTimeInMillis(nx[1]);
        String nt = nx == null ? "" : Prayer.hhmm(nc.get(Calendar.HOUR_OF_DAY) * 60 + nc.get(Calendar.MINUTE), h24);
        String left = nx == null ? "" : Prayer.remaining(nx[1] - now.getTimeInMillis());
        if (prayerBanner != null) prayerBanner.setText(nn + " " + nt + " · " + left);
        if (prayerTileSub != null) prayerTileSub.setText(nn + " " + nt);
        if (prayerTitleSub != null) prayerTitleSub.setText(pl.name + " · " + Prayer.hijriText(m, now));
        if (nextName != null) nextName.setText(nn + " · " + nt);
        if (nextLeft != null) nextLeft.setText(left);
        if (prayerList != null) {
            prayerList.removeAllViews();
            int[] t = Prayer.times(m, now);
            int[] order = {Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA};
            for (int i : order) {
                boolean hi = nx != null && (int) nx[0] == i && sameDay(nx[1], now.getTimeInMillis());
                LinearLayout r = new LinearLayout(m);
                r.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = tv(Prayer.name(i), 20, hi ? 0xFF4ADE80 : 0xFFFFFFFF, true);
                TextView tm = tv(Prayer.hhmm(t[i], h24), 20, hi ? 0xFF4ADE80 : GOLD, true);
                name.setGravity(ar() ? Gravity.RIGHT : Gravity.LEFT);
                if (ar()) { r.addView(tm); r.addView(name, new LinearLayout.LayoutParams(0, -2, 1)); }
                else { r.addView(name, new LinearLayout.LayoutParams(0, -2, 1)); r.addView(tm); }
                prayerList.addView(r, new LinearLayout.LayoutParams(-1, 0, 1));
            }
        }
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(); x.setTimeInMillis(a);
        Calendar y = Calendar.getInstance(); y.setTimeInMillis(b);
        return x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR) && x.get(Calendar.YEAR) == y.get(Calendar.YEAR);
    }

    void refreshMedia() {
        if (mediaTitle == null) return;
        String[] mi = m.mediaInfoPublic();
        boolean has = mi[0] != null && mi[0].length() > 0;
        mediaTitle.setText(has ? mi[0] : I18n.L("لا شيء قيد التشغيل", "Nothing playing"));
        mediaSub.setText(has ? (mi[1] != null ? mi[1] : "") : I18n.L("راديو أو بلوتوث أو USB", "Radio, Bluetooth or USB"));
        if (playIc != null) try { playIc.setImageBitmap(m.asset(m.mediaPlayingPublic() ? "ic/pause.png" : "ic/play_arrow.png")); } catch (Throwable ignored) { }
    }

    void refreshTrip() {
        if (tripDist == null) return;
        tripDist.setText(Trip.dist(Trip.km(m), mph()));
        tripDur.setText(Trip.dur(Trip.ms(m)));
        float lk = Trip.lastKm(m);
        tripLast.setText(lk < 0 ? "--" : Trip.dist(lk, mph()));
    }

    void refreshStatus() {
        if (gpsDot != null) gpsDot.setAlpha(gpsOk ? 1f : 0.4f);
        if (btDot != null) {
            boolean bt = false;
            try { android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter(); bt = a != null && a.isEnabled(); } catch (Throwable ignored) { }
            btDot.setAlpha(bt ? 1f : 0.4f);
        }
        if (wifiDot != null) {
            boolean wf = false;
            try { WifiManager wm = (WifiManager) m.getApplicationContext().getSystemService(Context.WIFI_SERVICE); wf = wm != null && wm.isWifiEnabled(); } catch (Throwable ignored) { }
            wifiDot.setAlpha(wf ? 1f : 0.4f);
        }
        Obd o = Obd.get(m);
        if (obdState != null) {
            String s = o.state() == Obd.READY ? I18n.L("OBD متصل ✓", "OBD connected ✓") : o.state() == Obd.CONNECTING ? I18n.L("OBD يتصل…", "OBD connecting…") : I18n.L("OBD غير متصل", "OBD not connected");
            obdState.setText("drive".equals(layout) ? (gpsOk ? "GPS ✓ · " : "GPS … · ") + s : s);
        }
    }

    // ───────────────────────── من Main ─────────────────────────

    public void onSpeed(boolean ok, float kmh) {
        gpsOk = ok;
        lastKmh = kmh;
        float v = mph() ? kmh * 0.621371f : kmh;
        if (kmh < 2) v = 0;
        if (speedo != null) speedo.setSpeed(v, ok);
        if (road != null) road.setSpeed(v);
        if (drive != null) drive.setSpeed(v, ok);
        if (mapSpeed != null) mapSpeed.setText((ok ? String.valueOf(Math.round(v)) : "--") + (mph() ? " mph" : I18n.L(" كم/س", " km/h")));
        int lim = prefs.getInt("speed_limit", 120);
        if (ok && lim > 0 && v > lim + 3) overSpeed();
    }

    private long lastBeep;

    void overSpeed() {
        long now = System.currentTimeMillis();
        if (now - lastBeep < 15000) return;
        lastBeep = now;
        try {
            final android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 500);
            h.postDelayed(new Runnable() { public void run() { tg.release(); } }, 800);
        } catch (Throwable ignored) { }
        if (limitTv != null) limitTv.animate().scaleX(1.25f).scaleY(1.25f).setDuration(200).withEndAction(new Runnable() { public void run() {
            if (limitTv != null) limitTv.animate().scaleX(1f).scaleY(1f).setDuration(200);
        }});
    }

    public void onLocation(Location l) {
        if (l == null) return;
        Trip.feed(m, l);
        if (map != null) {
            float kmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
            map.setPosition(l.getLatitude(), l.getLongitude(), l.hasBearing() && kmh > 4 ? l.getBearing() : Float.NaN, kmh);
        }
    }

    public void onState(int st, String msg) { refreshStatus(); }

    public void onValues(HashMap<String, Double> v) {
        Double rpm = v.get(Obd.RPM), t = v.get(Obd.COOLANT), vo = v.get(Obd.VOLT), th = v.get(Obd.THROTTLE), fu = v.get(Obd.FUEL);
        if (obdRpm != null) obdRpm.setText(rpm == null ? "--" : String.valueOf(Math.round(rpm)));
        if (obdTemp != null) obdTemp.setText(t == null ? "--" : Math.round(t) + "°C");
        if (obdVolt != null) obdVolt.setText(vo == null ? "--" : String.format(java.util.Locale.US, "%.1f V", vo));
        if (obdThr != null) obdThr.setText(th == null ? "--" : Math.round(th) + "%");
        if (obdFuel != null) obdFuel.setText(fu == null ? "--" : Math.round(fu) + "%");
    }

    public void pause() {
        paused = true;
        h.removeCallbacks(tick);
        if (drive != null) drive.setRunning(false);
        if (road != null) road.setRunning(false);
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        if (drive != null) drive.setRunning(true);
        if (road != null) road.setRunning(true);
        h.removeCallbacks(tick);
        tick.run();
    }

    public void destroy() {
        paused = true;
        h.removeCallbacksAndMessages(null);
        Obd.get(m).remove(this);
        try { ((ViewGroup) view.getParent()).removeView(view); } catch (Throwable ignored) { }
    }

    // ───────────────────────── اختيار التصميم والخلفية ─────────────────────────

    void chooseLayout() { chooseLayout(m); }

    /** نافذة «ثيم الشاشة»: صورة مصغّرة لكل تصميم + ثيمات جديدة من الإنترنت + لصق رابط. */
    public static void chooseLayout(final Main m) {
        final SharedPreferences p = m.getSharedPreferences("launcher", 0);
        final float s = m.getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(m);
        LinearLayout col = new LinearLayout(m);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (12 * s), (int) (4 * s), (int) (12 * s), 0);
        sv.addView(col);
        final android.app.Dialog[] dlg = new android.app.Dialog[1];
        String cur = p.getString("dash_layout", "garage");
        for (int i = 0; i < LAYOUTS.length; i++) {
            final String id = LAYOUTS[i];
            LinearLayout r = new LinearLayout(m);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, (int) (5 * s), 0, (int) (5 * s));
            ImageView th = new ImageView(m);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try { InputStream in = m.getAssets().open("previews/" + id + ".jpg"); th.setImageBitmap(BitmapFactory.decodeStream(in)); in.close(); }
            catch (Throwable e) { th.setImageBitmap(m.asset("ic/palette.png")); th.setScaleType(ImageView.ScaleType.FIT_CENTER); th.setColorFilter(0xFFFFFFFF); }
            GradientDrawable g = new GradientDrawable(); g.setColor(0xFF22262E); g.setCornerRadius(6 * s);
            th.setBackground(g);
            TextView t = new TextView(m);
            t.setText((id.equals(cur) ? "✓ " : "") + layoutName(i));
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(16);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding((int) (12 * s), 0, (int) (12 * s), 0);
            if (!"en".equals(I18n.lang)) { t.setGravity(Gravity.RIGHT); r.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); r.addView(th, new LinearLayout.LayoutParams((int) (112 * s), (int) (63 * s))); }
            else { r.addView(th, new LinearLayout.LayoutParams((int) (112 * s), (int) (63 * s))); r.addView(t, new LinearLayout.LayoutParams(0, -2, 1)); }
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (dlg[0] != null) dlg[0].dismiss();
                p.edit().putString("dash_layout", id).apply();
                m.rebuildPublic();
            }});
            col.addView(r);
        }
        TextView online = new TextView(m);
        online.setText(I18n.L("ثيمات جديدة من الإنترنت", "New themes from the internet"));
        online.setTextColor(0xFFFFFFFF); online.setTextSize(16); online.setTypeface(Typeface.DEFAULT_BOLD);
        online.setGravity(Gravity.CENTER);
        GradientDrawable og = new GradientDrawable(); og.setColor(0xFFE53935); og.setCornerRadius(10 * s);
        online.setBackground(og);
        online.setPadding(0, (int) (12 * s), 0, (int) (12 * s));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2); ol.topMargin = (int) (8 * s);
        col.addView(online, ol);
        online.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (dlg[0] != null) dlg[0].dismiss(); RemoteThemes.browse(m); } });
        TextView paste = new TextView(m);
        paste.setText(I18n.L("لصق رابط ثيم", "Paste a theme link"));
        paste.setTextColor(0xFFFFFFFF); paste.setTextSize(15);
        paste.setGravity(Gravity.CENTER);
        GradientDrawable pg = new GradientDrawable(); pg.setColor(0xFF2A303B); pg.setCornerRadius(10 * s);
        paste.setBackground(pg);
        paste.setPadding(0, (int) (11 * s), 0, (int) (11 * s));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2); pl.topMargin = (int) (8 * s); pl.bottomMargin = (int) (6 * s);
        col.addView(paste, pl);
        paste.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (dlg[0] != null) dlg[0].dismiss(); RemoteThemes.paste(m); } });
        TB b = new TB(m, Main.dlg());
        b.raw().setTitle(I18n.L("ثيم الشاشة", "Screen theme")).setView(sv).setNegativeButton(I18n.L("إغلاق", "Close"), null);
        dlg[0] = b.show();
    }

    void chooseWall() { chooseWall(m, layout); }

    public static void chooseWall(final Main m, String layout) {
        final SharedPreferences p = m.getSharedPreferences("launcher", 0);
        final List<String> keys = new ArrayList<String>();
        final List<String> names = new ArrayList<String>();
        String cur = p.getString("dash_wall", "theme");
        keys.add("theme"); names.add(I18n.L("تبع الثيم", "Follow the theme"));
        for (int i = 0; i < Walls.LIST.length; i++) { keys.add(Walls.LIST[i][0]); names.add(Walls.name(i)); }
        keys.add("none"); names.add(I18n.L("من غير خلفية", "No background"));
        keys.add("pick"); names.add(I18n.L("اختيار صورة (الشاشة أو USB)", "Choose a picture (screen or USB)"));
        int sel = Math.max(0, keys.indexOf(cur.startsWith("file:") ? "pick" : cur));
        new TB(m, Main.dlg()).raw().setTitle(I18n.L("الخلفية", "Background"))
            .setSingleChoiceItems(names.toArray(new String[0]), sel, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                d.dismiss();
                String k = keys.get(w);
                if ("pick".equals(k)) { pickSource(m, "wall"); return; }
                p.edit().putString("dash_wall", k).apply();
                m.rebuildPublic();
            }}).setNegativeButton(I18n.L("إغلاق", "Close"), null).show();
    }

    // ───────────────────────── صور من الشاشة/USB ─────────────────────────

    /** what: wall (خلفية) | rear (صورة السيارة من الخلف). يعرض الصور الموجودة في الذاكرة والفلاشات. */
    /** أول سؤال: من أين الصورة؟ فلاشة USB، ذاكرة الشاشة، أو المعرض (منتقي النظام). what = wall | rear | top | side */
    public static void pickSource(final Main m, final String what) {
        String[] items = {
            I18n.L("فلاشة USB", "USB stick"),
            I18n.L("ذاكرة الشاشة", "Screen storage"),
            I18n.L("المعرض (تطبيق الصور)", "Gallery (photos app)"),
        };
        new TB(m, Main.dlg()).raw().setTitle(I18n.L("من أين الصورة؟", "Where is the picture?"))
            .setItems(items, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                if (w == 2) m.pickGalleryPublic(what); else browse(m, what, w == 0);
            }}).setNegativeButton(I18n.L("إلغاء", "Cancel"), null).show();
    }

    static boolean isPrimary(File r, File primary) {
        try {
            String c = r.getCanonicalPath(), p = primary.getCanonicalPath();
            if (c.equals(p) || c.startsWith(p + "/") || p.startsWith(c + "/") || c.startsWith("/storage/emulated") || c.startsWith("/mnt/runtime")) return true;
        } catch (Throwable ignored) { }
        String n = r.getName().toLowerCase(java.util.Locale.US);
        return n.equals("sdcard") || n.equals("sdcard0") || n.equals("emulated") || n.equals("shell") || n.equals("obb") || n.equals("user")
            || n.equals("asec") || n.equals("secure") || n.equals("default") || n.equals("knox-emulated") || n.equals("runtime") || n.equals("self");
    }

    /** يعرض صور المصدر المختار بصور مصغّرة (الأحدث أولاً). */
    static void browse(final Main m, final String what, final boolean usb) {
        m.toastPublic(I18n.L("جارِ البحث عن الصور…", "Looking for pictures…"));
        new Thread(new Runnable() { public void run() {
            final List<File> found = new ArrayList<File>();
            File primary = android.os.Environment.getExternalStorageDirectory();
            if (usb) { for (File r : PrayerActivity.AudioFiles.roots()) if (!isPrimary(r, primary)) scanImages(r, 5, found); }
            else scanImages(primary, 5, found);
            Collections.sort(found, new Comparator<File>() { public int compare(File a, File b) { return Long.valueOf(b.lastModified()).compareTo(a.lastModified()); } });
            while (found.size() > 80) found.remove(found.size() - 1);
            final List<Bitmap> thumbs = new ArrayList<Bitmap>();
            for (File f : found) thumbs.add(thumb(f, 150));
            m.runOnUiThread(new Runnable() { public void run() {
                if (found.isEmpty()) {
                    m.toastPublic(usb ? I18n.L("لا توجد صور على فلاشة. تأكد أنها موصولة وفيها صور", "No pictures on a USB stick. Make sure it is plugged in and has pictures")
                                      : I18n.L("لا توجد صور بذاكرة الشاشة. انسخ الصورة إليها أو استعمل الفلاشة", "No pictures in the screen storage. Copy one here or use a USB stick"));
                    return;
                }
                final float dn = m.getResources().getDisplayMetrics().density;
                ListAdapter ad = new BaseAdapter() {
                    public int getCount() { return found.size(); }
                    public Object getItem(int i) { return found.get(i); }
                    public long getItemId(int i) { return i; }
                    public View getView(int i, View cv, ViewGroup p) {
                        LinearLayout row = new LinearLayout(m);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding((int) (10 * dn), (int) (6 * dn), (int) (10 * dn), (int) (6 * dn));
                        ImageView iv = new ImageView(m);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        if (thumbs.get(i) != null) iv.setImageBitmap(thumbs.get(i));
                        row.addView(iv, new LinearLayout.LayoutParams((int) (110 * dn), (int) (72 * dn)));
                        LinearLayout col = new LinearLayout(m);
                        col.setOrientation(LinearLayout.VERTICAL);
                        TextView n = new TextView(m); n.setText(found.get(i).getName()); n.setTextColor(0xFFFFFFFF); n.setTextSize(15); n.setSingleLine(true);
                        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                        TextView d2 = new TextView(m); d2.setText(found.get(i).getParent()); d2.setTextColor(0xFF9AA3B2); d2.setTextSize(12); d2.setSingleLine(true);
                        d2.setEllipsize(android.text.TextUtils.TruncateAt.START);
                        col.addView(n); col.addView(d2);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                        lp.leftMargin = (int) (12 * dn); lp.rightMargin = (int) (12 * dn);
                        row.addView(col, lp);
                        return row;
                    }
                };
                new TB(m, Main.dlg()).raw().setTitle(titleFor(what))
                    .setAdapter(ad, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { usePicked(m, what, found.get(w)); } })
                    .setNegativeButton(I18n.L("إلغاء", "Cancel"), null).show();
            }});
        }}).start();
    }

    static String titleFor(String what) {
        String lit = what.endsWith("_lit") ? I18n.L(" (والأضواء مضاءة)", " (lights on)") : "";
        if (what.startsWith("rear")) return I18n.L("صورة السيارة من الخلف", "Car photo from behind") + lit;
        if (what.startsWith("top")) return I18n.L("صورة السيارة من فوق وخلف", "Car photo from above-behind") + lit;
        if (what.startsWith("side")) return I18n.L("صورة السيارة الجانبية", "Car side photo") + lit;
        return I18n.L("صورة الخلفية", "Background picture");
    }

    static Bitmap thumb(File f, int target) {
        try {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), b);
            int sm = 1;
            while (b.outWidth / (sm * 2) >= target && b.outHeight / (sm * 2) >= target) sm *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sm;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable e) { return null; }
    }

    static void scanImages(File dir, int depth, List<File> out) {
        if (dir == null || depth < 0 || out.size() > 400) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName().toLowerCase(java.util.Locale.US);
            if (f.isDirectory()) { if (!n.startsWith(".") && !n.equals("android") && !n.equals("data")) scanImages(f, depth - 1, out); }
            else if ((n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) && f.length() > 20000) {
                boolean dup = false;
                for (File o : out) if (o.getAbsolutePath().equals(f.getAbsolutePath())) { dup = true; break; }
                if (!dup) out.add(f);
            }
        }
    }

    static void usePicked(final Main m, final String what, final File f) {
        usePicked(m, what, new CarPhoto.Opener() { public InputStream open() throws Exception { return new java.io.FileInputStream(f); } });
    }

    static void usePicked(final Main m, final String what, final CarPhoto.Opener op) {
        new Thread(new Runnable() { public void run() {
            boolean ok = false;
            try {
                if ("wall".equals(what)) {
                    File dst = new File(m.getFilesDir(), "dash_wall.jpg");
                    BitmapFactory.Options b = new BitmapFactory.Options();
                    b.inJustDecodeBounds = true;
                    InputStream in = op.open(); BitmapFactory.decodeStream(in, null, b); in.close();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = Walls.sample(b.outWidth, b.outHeight, 1600, 900);
                    in = op.open(); Bitmap bm = BitmapFactory.decodeStream(in, null, o); in.close();
                    java.io.FileOutputStream os = new java.io.FileOutputStream(dst);
                    bm.compress(Bitmap.CompressFormat.JPEG, 88, os);
                    os.close();
                    m.getSharedPreferences("launcher", 0).edit().putString("dash_wall", "file:" + dst.getAbsolutePath()).apply();
                    ok = true;
                } else {
                    ok = CarImg.saveSlot(m, what, CarPhoto.decode(op));
                }
            } catch (Throwable e) { Guard.log("usePicked: " + e); }
            final boolean fok = ok;
            m.runOnUiThread(new Runnable() { public void run() {
                m.toastPublic(fok ? I18n.L("تم ✓", "Done ✓") : I18n.L("تعذّر فتح الصورة", "Could not open the picture"));
                if (fok) m.rebuildPublic();
            }});
        }}).start();
    }

    /** قائمة صور السيارة: ثلاث خانات تُعمَّم على كل التصاميم، ونوع/لون الرسم عند عدم وجود صورة. */
    void carMenu() { carMenu(m); }

    public static void carMenu(final Main m) {
        final String[] slots = {"rear", "rear_lit", "top", "top_lit", "side", "side_lit"};
        String[] names = {
            I18n.L("صورة من الخلف (داخل العدّاد)", "From behind (in the speedometer)"),
            I18n.L("   ↳ والأضواء مضاءة (للوضع الليلي)", "   ↳ lights on (night mode)"),
            I18n.L("صورة من فوق وخلف (وضع القيادة)", "From above-behind (drive mode)"),
            I18n.L("   ↳ والأضواء مضاءة (للوضع الليلي)", "   ↳ lights on (night mode)"),
            I18n.L("صورة جانبية (تيسلا والكلاسيكي)", "Side photo (Tesla & Classic)"),
            I18n.L("   ↳ والأضواء مضاءة (للوضع الليلي)", "   ↳ lights on (night mode)"),
        };
        final List<String> list = new ArrayList<String>();
        final List<String> act = new ArrayList<String>();
        for (int i = 0; i < slots.length; i++) { list.add((CarImg.has(m, slots[i]) ? "✓  " : "") + names[i]); act.add(slots[i]); }
        String md = m.getSharedPreferences("launcher", 0).getString("car_lit", "auto");
        list.add(I18n.L("الإضاءة: ", "Lights: ") + ("on".equals(md) ? I18n.L("مضاءة دائماً", "always on") : "off".equals(md) ? I18n.L("عادية دائماً", "always off")
            : I18n.L("تلقائي (مع الوضع الليلي للشاشة)", "auto (with the screen's night mode)"))); act.add("lit");
        list.add(I18n.L("نوع السيارة المرسومة ولونها (عند عدم وجود صورة)", "Drawn car type & colour (when no photo)")); act.add("art");
        if (CarImg.hasAny(m)) { list.add(I18n.L("إزالة صوري كلها", "Remove all my photos")); act.add("clear"); }
        new TB(m, Main.dlg()).raw().setTitle(I18n.L("صورة السيارة", "Car picture"))
            .setItems(list.toArray(new String[0]), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String a = act.get(w);
                if ("art".equals(a)) m.carLookPublic();
                else if ("clear".equals(a)) { CarImg.clearAll(m); m.rebuildPublic(); }
                else if ("lit".equals(a)) {
                    SharedPreferences p = m.getSharedPreferences("launcher", 0);
                    String cur = p.getString("car_lit", "auto");
                    p.edit().putString("car_lit", "auto".equals(cur) ? "on" : "on".equals(cur) ? "off" : "auto").apply();
                    m.rebuildPublic();
                    carMenu(m);
                }
                else pickSource(m, a);
            }}).setNegativeButton(I18n.L("إغلاق", "Close"), null).show();
    }
}
