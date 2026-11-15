package com.stradaos.launcher;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** My Car · صفحة مواقيت الصلاة: المدينة من GPS، الصلاة القادمة، التاريخ الهجري، القبلة، والإعدادات. */
public class PrayerActivity extends Screen {

    private final Handler h = new Handler();
    private LinearLayout list;
    private TextView cityTv, citySubTv, dateTv, hijriTv, nextNameTv, nextLeftTv, qiblaTv, qiblaSubTv;
    private Qibla qibla;
    private LocationManager lm;
    private float heading = Float.NaN;
    private long headingAt;

    @Override protected String title() { return I18n.L("مواقيت الصلاة", "Prayer times"); }

    @Override
    protected void build(FrameLayout content) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(16), 0, px(16), px(14));
        content.addView(row, new FrameLayout.LayoutParams(-1, -1));

        // ── العمود الأول: المدينة والتاريخ والمواقيت ──
        LinearLayout left = cardBox();
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -1, 1.35f);
        marginEnd(llp, px(14));
        row.addView(left, llp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon("location_on", Main.RED), lp(px(26), px(26)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(px(10), 0, px(10), 0);
        cityTv = tv("", 22, Main.TEXT, true);
        citySubTv = tv("", 12, Main.MUTED, false);
        ht.addView(cityTv);
        ht.addView(citySubTv);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(pill("tune", I18n.L("المدينة", "City"), false, new View.OnClickListener() { public void onClick(View v) { chooseCity(); } }));
        left.addView(head);

        LinearLayout dates = new LinearLayout(this);
        dates.setPadding(0, px(10), 0, px(6));
        dateTv = tv("", 14, Main.SOFT, false);
        hijriTv = tv("", 14, Main.RED, true);
        dates.addView(dateTv, new LinearLayout.LayoutParams(0, -2, 1));
        dates.addView(hijriTv);
        left.addView(dates);

        LinearLayout next = new LinearLayout(this);
        next.setGravity(Gravity.CENTER_VERTICAL);
        next.setBackground(round(Main.INNER, 14));
        next.setPadding(px(14), px(10), px(14), px(10));
        next.addView(icon("mosque", Main.RED), lp(px(34), px(34)));
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        nt.setPadding(px(12), 0, px(12), 0);
        nextNameTv = tv("", 20, Main.TEXT, true);
        nextLeftTv = tv("", 14, Main.SOFT, false);
        nt.addView(nextNameTv);
        nt.addView(nextLeftTv);
        next.addView(nt);
        LinearLayout.LayoutParams nlp = lp(-1, -2);
        nlp.bottomMargin = px(8);
        left.addView(next, nlp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        left.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        // ── العمود الثاني: القبلة والإعدادات ──
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        row.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout qc = cardBox();
        qc.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout qh = new LinearLayout(this);
        qh.setGravity(Gravity.CENTER_VERTICAL);
        qh.addView(icon("explore", Main.ICON), lp(px(22), px(22)));
        TextView qt = tv(I18n.L("اتجاه القبلة", "Qibla direction"), 16, Main.TEXT, true);
        qt.setPadding(px(8), 0, px(8), 0);
        qh.addView(qt, new LinearLayout.LayoutParams(0, -2, 1));
        qiblaTv = tv("", 16, Main.RED, true);
        qh.addView(qiblaTv);
        qc.addView(qh, lp(-1, -2));
        qibla = new Qibla(this);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-1, 0, 1);
        qlp.topMargin = px(6);
        qc.addView(qibla, qlp);
        qiblaSubTv = multi("", 12, Main.MUTED);
        qiblaSubTv.setGravity(Gravity.CENTER);
        qiblaSubTv.setMaxLines(3);
        qc.addView(qiblaSubTv, lp(-1, -2));
        LinearLayout.LayoutParams qclp = new LinearLayout.LayoutParams(-1, 0, 1);
        qclp.bottomMargin = px(10);
        right.addView(qc, qclp);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        LinearLayout b1 = new LinearLayout(this);
        LinearLayout b2 = new LinearLayout(this);
        b1.addView(pill("tune", I18n.L("طريقة الحساب", "Method"), false, new View.OnClickListener() { public void onClick(View v) { chooseMethod(); } }), lpw(1));
        View sp1 = new View(this); b1.addView(sp1, lp(px(8), 1));
        b1.addView(pill("build", I18n.L("تعديل الدقائق", "Adjust"), false, new View.OnClickListener() { public void onClick(View v) { chooseAdjust(); } }), lpw(1));
        b2.addView(pill("music_note", I18n.L("الأذان", "Athan"), prefs.getBoolean("pr_alert", false), new View.OnClickListener() { public void onClick(View v) { athanMenu(); } }), lpw(1));
        View sp2 = new View(this); b2.addView(sp2, lp(px(8), 1));
        b2.addView(pill("autorenew", I18n.L("الهجري ±", "Hijri ±"), false, new View.OnClickListener() { public void onClick(View v) { chooseHijri(); } }), lpw(1));
        btns.addView(b1, lp(-1, px(46)));
        View gap = new View(this);
        btns.addView(gap, lp(1, px(8)));
        btns.addView(b2, lp(-1, px(46)));
        right.addView(btns, lp(-1, -2));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        startGps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacksAndMessages(null);
        stopGps();
    }

    private boolean h24() {
        String fmt = prefs.getString("clock", "auto");
        return "24".equals(fmt) || ("auto".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
    }

    private void refresh() {
        h.removeCallbacksAndMessages(null);
        Prayer.Place pl = Prayer.place(this);
        cityTv.setText(pl.name);
        String sub;
        if (!pl.auto) sub = I18n.L("مدينة مختارة يدوياً", "City chosen manually");
        else if (pl.fallback) sub = I18n.L("بانتظار إشارة GPS · مؤقتاً", "Waiting for GPS · temporary");
        else if (pl.city != null) sub = I18n.L("تلقائي من GPS · مواقيت مركز المدينة", "Automatic from GPS · city centre times");
        else sub = I18n.L("تلقائي من GPS · خارج سوريا", "Automatic from GPS · outside Syria");
        citySubTv.setText(sub + "  ·  " + Prayer.methodName(Prayer.methodIndex(prefs)));
        Calendar now = Calendar.getInstance();
        String d = I18n.sdf("EEEE، d MMMM yyyy").format(now.getTime());
        dateTv.setText(d);
        hijriTv.setText(Prayer.hijriText(this, now));

        int[] t = Prayer.times(this, now);
        long[] nx = Prayer.next(this, now.getTimeInMillis());
        int nextIdx = nx != null ? (int) nx[0] : -1;
        boolean tomorrow = nx != null && !sameDay(nx[1], now.getTimeInMillis());
        if (nx != null) {
            nextNameTv.setText(I18n.L("الصلاة القادمة: ", "Next: ") + Prayer.name(nextIdx) + "  " + Prayer.hhmm(minutesOf(nx[1]), h24()));
            nextLeftTv.setText(Prayer.remaining(nx[1] - now.getTimeInMillis()) + (tomorrow ? I18n.L(" · غداً", " · tomorrow") : ""));
        }
        list.removeAllViews();
        for (int i = 0; i < 6; i++) {
            boolean hi = i == nextIdx && !tomorrow;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(14), 0, px(14), 0);
            if (hi) r.setBackground(round(Main.RED, 12));
            boolean past = !hi && t[i] >= 0 && Prayer.at(now, t[i]) < now.getTimeInMillis();
            int col = hi ? Color.WHITE : past ? Main.MUTED : Main.TEXT;
            TextView n = tv(Prayer.name(i), 18, col, hi);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
            int adj = prefs.getInt("pr_adj_" + i, 0);
            if (adj != 0) {
                TextView a = tv((adj > 0 ? "+" : "") + adj, 11, hi ? Color.WHITE : Main.MUTED, false);
                a.setPadding(px(6), 0, px(6), 0);
                r.addView(a);
            }
            TextView tm = tv(Prayer.hhmm(t[i], h24()), 20, col, true);
            r.addView(tm);
            list.addView(r, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        updateQibla(pl);
        h.postDelayed(new Runnable() { public void run() { refresh(); } }, 30000);
    }

    private static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(); x.setTimeInMillis(a);
        Calendar y = Calendar.getInstance(); y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    private static int minutesOf(long ms) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    // ───────────────────────── القبلة ─────────────────────────

    private void updateQibla(Prayer.Place pl) {
        // القبلة تُحسب من موقع السيارة الحقيقي إن وُجد (أدق من مركز المدينة)
        double lat = pl.lat, lon = pl.lon;
        Location l = Prayer.lastKnown(this);
        if (pl.auto && l != null && System.currentTimeMillis() - l.getTime() < 6 * 3600 * 1000L) { lat = l.getLatitude(); lon = l.getLongitude(); }
        double q = Prayer.qibla(lat, lon);
        qiblaTv.setText(Math.round(q) + "° " + Prayer.compassName(q));
        boolean moving = !Float.isNaN(heading) && System.currentTimeMillis() - headingAt < 15000;
        qibla.set((float) q, moving ? heading : Float.NaN);
        if (moving) {
            double rel = ((q - heading) % 360 + 360) % 360;
            String side;
            if (rel < 20 || rel > 340) side = I18n.L("القبلة أمام السيارة تقريباً", "Qibla is roughly ahead");
            else if (rel >= 160 && rel <= 200) side = I18n.L("القبلة خلف السيارة", "Qibla is behind the car");
            else if (rel < 180) side = I18n.L("القبلة إلى يمين السيارة (" + Math.round(rel) + "°)", "Qibla is to the right (" + Math.round(rel) + "°)");
            else side = I18n.L("القبلة إلى يسار السيارة (" + Math.round(360 - rel) + "°)", "Qibla is to the left (" + Math.round(360 - rel) + "°)");
            qiblaSubTv.setText(side + I18n.L("\nالسهم الأحمر نحو القبلة، وأعلى الدائرة = مقدمة السيارة", "\nRed arrow = Qibla, top of the dial = front of the car"));
        } else {
            qiblaSubTv.setText(I18n.L("الشمال في الأعلى. الشاشة لا تحتوي بوصلة: عند سير السيارة يُعرف اتجاهها من GPS ويدور السهم معها.",
                "North is up. The screen has no compass: while driving, the car's direction comes from GPS and the arrow follows it."));
        }
    }

    private final LocationListener gps = new LocationListener() {
        public void onLocationChanged(Location l) {
            if (l == null) return;
            boolean changed = Prayer.saveFix(PrayerActivity.this, l.getLatitude(), l.getLongitude());
            if (l.hasBearing() && l.hasSpeed() && l.getSpeed() > 2.5f) { heading = l.getBearing(); headingAt = System.currentTimeMillis(); }
            if (changed) { Prayer.schedule(PrayerActivity.this); refresh(); }
            else updateQibla(Prayer.place(PrayerActivity.this));
        }
        public void onStatusChanged(String p, int st, Bundle e) { }
        public void onProviderEnabled(String p) { }
        public void onProviderDisabled(String p) { }
    };

    private void startGps() {
        try {
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gps);
        } catch (Throwable ignored) { }
    }

    private void stopGps() {
        try { if (lm != null) lm.removeUpdates(gps); } catch (Throwable ignored) { }
    }

    /** بوصلة القبلة: دائرة بالجهات وسهم أحمر نحو مكة. إذا كان اتجاه السيارة معروفاً تدور الدائرة لتكون مقدمة السيارة للأعلى. */
    static class Qibla extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float q, head = Float.NaN;

        Qibla(Context c) { super(c); }

        void set(float qibla, float heading) { q = qibla; head = heading; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            float r = Math.min(w, hh) / 2f - 6;
            if (r < 20) return;
            float cx = w / 2f, cy = hh / 2f;
            float rot = Float.isNaN(head) ? 0 : -head;   // دوران الدائرة (الشمال)
            p.setStyle(Paint.Style.FILL);
            p.setColor(Main.INNER);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.03f);
            p.setColor(Main.BTN);
            c.drawCircle(cx, cy, r * 0.97f, p);
            // علامات الجهات
            c.save();
            c.rotate(rot, cx, cy);
            for (int i = 0; i < 72; i++) {
                float a = i * 5;
                float len = i % 18 == 0 ? r * 0.14f : i % 2 == 0 ? r * 0.07f : r * 0.04f;
                p.setColor(i % 18 == 0 ? Main.TEXT : Main.MUTED);
                p.setStrokeWidth(i % 18 == 0 ? r * 0.025f : r * 0.012f);
                double rad = Math.toRadians(a);
                float x1 = cx + (float) Math.sin(rad) * r * 0.93f, y1 = cy - (float) Math.cos(rad) * r * 0.93f;
                float x2 = cx + (float) Math.sin(rad) * (r * 0.93f - len), y2 = cy - (float) Math.cos(rad) * (r * 0.93f - len);
                c.drawLine(x1, y1, x2, y2, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * 0.15f);
            String[] dirs = "en".equals(I18n.lang) ? new String[]{"N", "E", "S", "W"} : new String[]{"ش", "شر", "ج", "غ"};
            for (int i = 0; i < 4; i++) {
                double rad = Math.toRadians(i * 90);
                float x = cx + (float) Math.sin(rad) * r * 0.62f, y = cy - (float) Math.cos(rad) * r * 0.62f + r * 0.05f;
                p.setColor(i == 0 ? Main.RED : Main.SOFT);
                c.drawText(dirs[i], x, y, p);
            }
            // سهم القبلة
            c.rotate(q, cx, cy);
            Path a = new Path();
            a.moveTo(cx, cy - r * 0.86f);
            a.lineTo(cx + r * 0.11f, cy - r * 0.6f);
            a.lineTo(cx + r * 0.035f, cy - r * 0.6f);
            a.lineTo(cx + r * 0.035f, cy + r * 0.25f);
            a.lineTo(cx - r * 0.035f, cy + r * 0.25f);
            a.lineTo(cx - r * 0.035f, cy - r * 0.6f);
            a.lineTo(cx - r * 0.11f, cy - r * 0.6f);
            a.close();
            p.setColor(Main.RED);
            c.drawPath(a, p);
            // الكعبة: مربع صغير عند رأس السهم
            p.setColor(Main.TEXT);
            float k = r * 0.07f;
            c.drawRect(new RectF(cx - k, cy - r * 0.98f - k, cx + k, cy - r * 0.98f + k), p);
            c.restore();
            // مقدمة السيارة (عندما يكون الاتجاه معروفاً)
            if (!Float.isNaN(head)) {
                p.setColor(Main.TEXT);
                Path t = new Path();
                t.moveTo(cx, cy - r - 2);
                t.lineTo(cx - r * 0.07f, cy - r + r * 0.12f);
                t.lineTo(cx + r * 0.07f, cy - r + r * 0.12f);
                t.close();
                c.drawPath(t, p);
            }
            p.setColor(Main.TEXT);
            c.drawCircle(cx, cy, r * 0.05f, p);
        }
    }

    // ───────────────────────── الإعدادات ─────────────────────────

    private void changed() {
        Prayer.schedule(this);
        refresh();
    }

    private void chooseCity() {
        final List<Syria.City> cities = new ArrayList<Syria.City>(Arrays.asList(Syria.CITIES));
        Collections.sort(cities, new Comparator<Syria.City>() {
            public int compare(Syria.City a, Syria.City b) {
                if (a.capital != b.capital) return a.capital ? -1 : 1;
                return a.name().compareTo(b.name());
            }
        });
        final String[] items = new String[cities.size() + 2];
        items[0] = I18n.L("تلقائي من GPS · أقرب مدينة", "Automatic (GPS) · nearest city");
        items[1] = I18n.L("تلقائي من GPS · مراكز المحافظات فقط", "Automatic (GPS) · governorate centres only");
        String cur = prefs.getString("pr_city", "");
        int sel = cur.length() == 0 ? (prefs.getBoolean("pr_capitals", false) ? 1 : 0) : -1;
        for (int i = 0; i < cities.size(); i++) {
            Syria.City c = cities.get(i);
            items[i + 2] = c.name() + (c.capital ? I18n.L("  · مركز محافظة", "  · governorate centre") : "");
            if (c.ar.equals(cur)) sel = i + 2;
        }
        choose(I18n.L("مدينة المواقيت", "City for prayer times"), items, sel, new IntCb() { public void on(int w) {
            SharedPreferences.Editor e = prefs.edit();
            if (w <= 1) e.putString("pr_city", "").putBoolean("pr_capitals", w == 1);
            else e.putString("pr_city", cities.get(w - 2).ar);
            e.apply();
            changed();
        }});
    }

    private void chooseMethod() {
        final String[] names = new String[Prayer.METHOD_IDS.length + 2];
        for (int i = 0; i < Prayer.METHOD_IDS.length; i++) names[i] = Prayer.methodName(i);
        boolean hanafi = prefs.getInt("pr_asr", 0) == 1;
        names[Prayer.METHOD_IDS.length] = I18n.L("العصر: الجمهور (ظل المثل) ", "Asr: standard (Shafi'i) ") + (hanafi ? "" : "✓");
        names[Prayer.METHOD_IDS.length + 1] = I18n.L("العصر: الحنفي (ظل المثلين) ", "Asr: Hanafi ") + (hanafi ? "✓" : "");
        choose(I18n.L("طريقة الحساب", "Calculation method"), names, Prayer.methodIndex(prefs), new IntCb() { public void on(int w) {
            if (w < Prayer.METHOD_IDS.length) prefs.edit().putString("pr_method", Prayer.METHOD_IDS[w]).apply();
            else prefs.edit().putInt("pr_asr", w == Prayer.METHOD_IDS.length ? 0 : 1).apply();
            changed();
        }});
    }

    private void chooseAdjust() {
        String[] items = new String[6];
        for (int i = 0; i < 6; i++) {
            int a = prefs.getInt("pr_adj_" + i, 0);
            items[i] = Prayer.name(i) + "   " + (a > 0 ? "+" : "") + a + I18n.L(" دقيقة", " min");
        }
        choose(I18n.L("تعديل المواقيت لتطابق مسجد منطقتك", "Adjust times to match your mosque"), items, -1, new IntCb() { public void on(final int i) {
            adjustOne(i);
        }});
    }

    private void adjustOne(final int i) {
        final int[] v = {prefs.getInt("pr_adj_" + i, 0)};
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(px(20), px(16), px(20), px(8));
        final TextView val = tv("", 28, Main.TEXT, true);
        val.setGravity(Gravity.CENTER);
        View.OnClickListener minus = new View.OnClickListener() { public void onClick(View x) { v[0] = Math.max(-60, v[0] - 1); show(val, v[0]); } };
        View.OnClickListener plus = new View.OnClickListener() { public void onClick(View x) { v[0] = Math.min(60, v[0] + 1); show(val, v[0]); } };
        box.addView(pill(null, "  −  ", false, minus), lp(px(80), px(52)));
        box.addView(val, lp(px(160), -2));
        box.addView(pill(null, "  +  ", false, plus), lp(px(80), px(52)));
        show(val, v[0]);
        new TB(this, Main.dlg()).raw()
            .setTitle(I18n.L("تعديل ", "Adjust ") + Prayer.name(i))
            .setView(box)
            .setPositiveButton(I18n.L("حفظ", "Save"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().putInt("pr_adj_" + i, v[0]).apply(); changed();
            }})
            .setNeutralButton(I18n.L("صفر", "Reset"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                prefs.edit().remove("pr_adj_" + i).apply(); changed();
            }})
            .setNegativeButton(I18n.L("إلغاء", "Cancel"), null)
            .show();
    }

    private void show(TextView t, int v) { t.setText((v > 0 ? "+" : "") + v + I18n.L(" د", " min")); }

    private void chooseHijri() {
        final int[] vals = {-2, -1, 0, 1, 2};
        String[] items = new String[vals.length];
        Calendar now = Calendar.getInstance();
        int cur = prefs.getInt("pr_hijri_adj", 0), sel = 2;
        for (int i = 0; i < vals.length; i++) {
            int[] hj = Prayer.hijri(now, vals[i]);
            items[i] = (vals[i] > 0 ? "+" : "") + vals[i] + I18n.L(" يوم  ·  ", " day  ·  ") + hj[0] + " " + I18n.L(Prayer.HIJRI_AR[hj[1] - 1], Prayer.HIJRI_EN[hj[1] - 1]);
            if (vals[i] == cur) sel = i;
        }
        choose(I18n.L("تصحيح التاريخ الهجري (حسب رؤية الهلال)", "Hijri date correction (moon sighting)"), items, sel, new IntCb() { public void on(int w) {
            prefs.edit().putInt("pr_hijri_adj", vals[w]).apply(); refresh();
        }});
    }

    // ───────────────────────── الأذان ─────────────────────────

    private void athanMenu() {
        boolean on = prefs.getBoolean("pr_alert", false);
        String snd = prefs.getString("pr_sound", "");
        String[] items = {
            on ? I18n.L("إيقاف تنبيه الأذان", "Turn athan alert off") : I18n.L("تشغيل تنبيه الأذان", "Turn athan alert on"),
            I18n.L("الصلوات التي يُنبَّه لها", "Which prayers"),
            I18n.L("الصوت: ", "Sound: ") + (snd.length() == 0 ? I18n.L("نغمة الإشعار", "notification tone") : new File(snd).getName()),
            I18n.L("اختيار ملف صوت أذان (من الذاكرة أو USB)", "Choose an athan audio file (memory or USB)"),
            I18n.L("تجربة الصوت الآن", "Test the sound now"),
        };
        choose(I18n.L("تنبيه الأذان", "Athan alert"), items, -1, new IntCb() { public void on(int w) {
            if (w == 0) {
                prefs.edit().putBoolean("pr_alert", !prefs.getBoolean("pr_alert", false)).apply();
                Prayer.schedule(PrayerActivity.this);
                toast(prefs.getBoolean("pr_alert", false) ? I18n.L("سيعمل الأذان في أوقات الصلاة", "Athan alert is on") : I18n.L("تم إيقاف تنبيه الأذان", "Athan alert is off"));
                recreate();
            } else if (w == 1) chooseMask();
            else if (w == 2) { prefs.edit().remove("pr_sound").apply(); toast(I18n.L("الصوت: نغمة الإشعار", "Sound: notification tone")); }
            else if (w == 3) pickSound();
            else {
                Intent s = new Intent(PrayerActivity.this, PrayerAlarm.Athan.class);
                s.putExtra("prayer", Prayer.DHUHR);
                startService(s);
            }
        }});
    }

    private void chooseMask() {
        final int[] idx = {Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA};
        final boolean[] checked = new boolean[idx.length];
        String[] names = new String[idx.length];
        int mask = Prayer.alertMask(prefs);
        for (int i = 0; i < idx.length; i++) { names[i] = Prayer.name(idx[i]); checked[i] = (mask & (1 << idx[i])) != 0; }
        new android.app.AlertDialog.Builder(this, Main.dlg())
            .setTitle(I18n.L("الصلوات التي يُنبَّه لها", "Which prayers"))
            .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) { checked[w] = c; }
            })
            .setPositiveButton(I18n.L("حفظ", "Save"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                int m = 0;
                for (int i = 0; i < idx.length; i++) if (checked[i]) m |= 1 << idx[i];
                prefs.edit().putInt("pr_alert_mask", m).apply();
                Prayer.schedule(PrayerActivity.this);
            }})
            .setNegativeButton(I18n.L("إلغاء", "Cancel"), null)
            .show();
    }

    private void pickSound() {
        toast(I18n.L("جارِ البحث عن ملفات الصوت…", "Looking for audio files…"));
        new Thread(new Runnable() { public void run() {
            final List<File> found = new ArrayList<File>();
            for (File r : AudioFiles.roots()) AudioFiles.scan(r, 5, found);
            Collections.sort(found, new Comparator<File>() { public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); } });
            runOnUiThread(new Runnable() { public void run() {
                if (found.isEmpty()) {
                    toast(I18n.L("لا توجد ملفات صوت (mp3/m4a/ogg/wav). انسخ ملف الأذان إلى الذاكرة أو فلاشة USB", "No audio files found (mp3/m4a/ogg/wav). Copy an athan file to memory or a USB stick"));
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < found.size(); i++) names[i] = found.get(i).getName() + "\n" + found.get(i).getParent();
                choose(I18n.L("ملف صوت الأذان", "Athan audio file"), names, -1, new IntCb() { public void on(int w) {
                    prefs.edit().putString("pr_sound", found.get(w).getAbsolutePath()).apply();
                    toast(I18n.L("تم: ", "Saved: ") + found.get(w).getName());
                }});
            }});
        }}).start();
    }

    /** البحث عن ملفات الصوت في ذاكرة الشاشة والفلاشات. */
    static class AudioFiles {
        static List<File> roots() {
            List<File> r = new ArrayList<File>();
            try { r.add(Environment.getExternalStorageDirectory()); } catch (Throwable ignored) { }
            for (String p : new String[]{"/storage", "/mnt/media_rw", "/mnt/usb_storage", "/mnt/usbhost", "/mnt/udisk", "/mnt"}) {
                File f = new File(p);
                File[] ch = f.listFiles();
                if (ch == null) continue;
                for (File c : ch) if (c.isDirectory() && !c.getName().equals("self") && !c.getName().equals("emulated")) r.add(c);
            }
            return r;
        }

        static void scan(File dir, int depth, List<File> out) {
            if (dir == null || depth < 0 || out.size() > 300) return;
            File[] fs = dir.listFiles();
            if (fs == null) return;
            for (File f : fs) {
                String n = f.getName().toLowerCase(java.util.Locale.US);
                if (f.isDirectory()) {
                    if (n.startsWith(".") || n.equals("android") || n.equals("data")) continue;
                    scan(f, depth - 1, out);
                } else if (n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".aac")) {
                    boolean dup = false;
                    for (File o : out) if (o.getAbsolutePath().equals(f.getAbsolutePath())) { dup = true; break; }
                    if (!dup) out.add(f);
                }
            }
        }
    }
}
