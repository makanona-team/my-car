package com.stradaos.launcher;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * My Car · تشخيص الشاشة: معلومات الجهاز والذاكرة الحقيقية، مصادر الصور (USB أو ذاكرة)، تطبيقات وخدمات الشاشة (com.tw،
 * الراديو)، ومراقبة ما يتغير في النظام عند تشغيل الأضواء: الوضع الليلي، إعدادات النظام، وخصائص النظام (getprop).
 * أي تغيير يمكن اختياره «إشارة الأضواء» لتبديل صور السيارة المضاءة.
 */
public class DiagActivity extends Screen {

    static final Pattern INTEREST = Pattern.compile("(?i).*(ill|light|lamp|night|dim|bright|back|acc|tw|mcu|radio|fm|can|car|park|reverse|headl).*");

    private TextView log, watchTxt;
    private LinearLayout cands;
    private final StringBuilder report = new StringBuilder();
    private final StringBuilder events = new StringBuilder();
    private final Handler h = new Handler();
    private boolean watching;
    private Map<String, String> snap;
    private final List<ContentObserver> observers = new ArrayList<ContentObserver>();
    private final SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss", Locale.US);

    @Override protected String title() { return I18n.L("تشخيص الشاشة", "Screen diagnostics"); }

    @Override
    protected void build(FrameLayout content) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), 0, px(16), px(10));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = hbox();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout watch = pill("explore", I18n.L("ابدأ مراقبة الأضواء", "Start watching the lights"), true, new View.OnClickListener() {
            public void onClick(View v) { if (watching) stopWatch(); else startWatch(); }
        });
        watchTxt = (TextView) watch.getTag();
        bar.addView(watch);
        LinearLayout save = pill("check", I18n.L("حفظ التقرير", "Save report"), false, new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        marginStart(sl, px(10));
        bar.addView(save, sl);
        col.addView(bar, new LinearLayout.LayoutParams(-1, px(52)));

        TextView help = multi(I18n.L(
            "١) اضغط «ابدأ المراقبة» ٢) شغّل أضواء السيارة وانتظر ٥ ثوانٍ ٣) أطفئها وانتظر ٥ ثوانٍ ٤) اضغط «أوقف». ستظهر التغييرات بالأسفل؛ اضغط على التغيير الذي تبدّل مع الأضواء لاعتماده إشارةً للأضواء. ثم «حفظ التقرير» وصوّر الشاشة أو أرسل الملف.",
            "1) Tap Start 2) switch the car lights on and wait 5 s 3) switch them off and wait 5 s 4) tap Stop. Changes appear below; tap the one that followed the lights to use it as the light signal. Then Save report and take a photo or send the file."), 14, Main.MUTED);
        col.addView(help, new LinearLayout.LayoutParams(-1, -2));

        cands = new LinearLayout(this);
        cands.setOrientation(LinearLayout.VERTICAL);
        col.addView(cands, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        log = multi("", 13, Main.TEXT);
        log.setMaxLines(100000);
        log.setTextIsSelectable(true);
        log.setGravity(Gravity.LEFT);
        sv.addView(log);
        LinearLayout.LayoutParams lpS = new LinearLayout.LayoutParams(-1, 0, 1);
        lpS.topMargin = px(8);
        col.addView(sv, lpS);

        log.setText(I18n.L("جارِ جمع المعلومات…", "Collecting information…"));
        new Thread(new Runnable() { public void run() {
            final String r = collect();
            runOnUiThread(new Runnable() { public void run() { report.setLength(0); report.append(r); show(); } });
        }}).start();
    }

    private void show() { log.setText((events.length() > 0 ? "── " + I18n.L("التغييرات", "Changes") + " ──\n" + events + "\n" : "") + report); }

    // ───────────────────────── المعلومات ─────────────────────────

    private String collect() {
        StringBuilder b = new StringBuilder();
        try {
            b.append("── ").append(I18n.L("الجهاز", "Device")).append(" ──\n");
            b.append(Build.MANUFACTURER).append(" · ").append(Build.MODEL).append(" · board ").append(Build.BOARD).append(" · hw ").append(Build.HARDWARE).append('\n');
            b.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ").append(Build.DISPLAY).append('\n');
            DisplayMetrics dm = getResources().getDisplayMetrics();
            b.append(I18n.L("الشاشة: ", "Screen: ")).append(dm.widthPixels).append('x').append(dm.heightPixels).append(" · dpi ").append(dm.densityDpi).append('\n');
            b.append(I18n.L("الوضع الليلي الآن: ", "Night mode now: ")).append(night() ? I18n.L("نعم", "yes") : I18n.L("لا", "no")).append('\n');
            b.append(I18n.L("إشارة الأضواء المعتمدة: ", "Light signal in use: ")).append(prefs.getString("lit_src", "").length() > 0
                ? prefs.getString("lit_src", "") + " = " + prefs.getString("lit_on", "") : I18n.L("الوضع الليلي للشاشة", "screen night mode")).append("\n\n");

            b.append("── ").append(I18n.L("الذاكرة (RAM)", "Memory (RAM)")).append(" ──\n");
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            b.append(I18n.L("الحقيقية: ", "Real: ")).append(mb(mi.totalMem)).append(" · ").append(I18n.L("المتاحة: ", "free: ")).append(mb(mi.availMem))
             .append(" · ").append(I18n.L("حد التطبيق: ", "app limit: ")).append(am.getMemoryClass()).append(" MB").append(mi.lowMemory ? " · LOW" : "").append("\n\n");

            b.append("── ").append(I18n.L("مصادر الصور", "Picture sources")).append(" ──\n");
            File primary = Environment.getExternalStorageDirectory();
            b.append(I18n.L("ذاكرة الشاشة: ", "Screen storage: ")).append(primary).append(" · ").append(count(primary)).append(I18n.L(" صورة", " pictures")).append('\n');
            for (File r : PrayerActivity.AudioFiles.roots()) {
                boolean pr = Dash.isPrimary(r, primary);
                b.append(pr ? "   [" + I18n.L("ذاكرة", "internal") + "] " : "   [USB] ").append(r.getAbsolutePath());
                if (!pr) b.append(" · ").append(count(r)).append(I18n.L(" صورة", " pictures"));
                b.append('\n');
            }
            b.append('\n');

            b.append("── ").append(I18n.L("تطبيقات وخدمات الشاشة", "Screen apps & services")).append(" ──\n");
            PackageManager pm = getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(0)) {
                String pn = pi.packageName;
                String l = pn.toLowerCase(Locale.US);
                if (!(l.startsWith("com.tw") || l.contains("radio") || l.contains(".fm") || l.contains("carinfo") || l.contains("canbus") || l.contains("mcu"))) continue;
                b.append("• ").append(pn).append(" ").append(pi.versionName != null ? pi.versionName : "").append('\n');
                try {
                    PackageInfo full = pm.getPackageInfo(pn, PackageManager.GET_SERVICES | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS);
                    if (full.services != null) for (ServiceInfo s : full.services) if (s.exported) b.append("    S ").append(s.name).append('\n');
                    if (full.activities != null) for (ActivityInfo a : full.activities) if (a.exported) b.append("    A ").append(a.name).append('\n');
                    if (full.receivers != null) for (ActivityInfo a : full.receivers) if (a.exported) b.append("    R ").append(a.name).append('\n');
                } catch (Throwable ignored) { }
            }
            b.append('\n');

            b.append("── ").append(I18n.L("إعدادات النظام المتعلقة", "Related system settings")).append(" ──\n");
            for (Map.Entry<String, String> e : settings().entrySet()) if (INTEREST.matcher(e.getKey()).matches()) b.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            b.append('\n');
            b.append("── getprop ──\n");
            for (Map.Entry<String, String> e : props().entrySet()) if (INTEREST.matcher(e.getKey()).matches()) b.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        } catch (Throwable e) {
            b.append("\n").append(e).append('\n');
        }
        return b.toString();
    }

    private static String mb(long v) { return (v / (1024 * 1024)) + " MB"; }

    private static int count(File dir) {
        List<File> out = new ArrayList<File>();
        try { Dash.scanImages(dir, 3, out); } catch (Throwable ignored) { }
        return out.size();
    }

    private boolean night() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** كل إعدادات النظام (system / global / secure) بأسماء مسبوقة. */
    private Map<String, String> settings() {
        Map<String, String> m = new HashMap<String, String>();
        read(Settings.System.CONTENT_URI, "sys:", m);
        if (Build.VERSION.SDK_INT >= 17) read(Settings.Global.CONTENT_URI, "glob:", m);
        read(Settings.Secure.CONTENT_URI, "sec:", m);
        return m;
    }

    private void read(Uri u, String pre, Map<String, String> m) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[]{"name", "value"}, null, null, null);
            while (c != null && c.moveToNext()) m.put(pre + c.getString(0), c.getString(1));
        } catch (Throwable ignored) {
        } finally { if (c != null) c.close(); }
    }

    static Map<String, String> props() {
        Map<String, String> m = new HashMap<String, String>();
        try {
            Process p = Runtime.getRuntime().exec("getprop");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                int a = line.indexOf("]: [");
                if (line.startsWith("[") && a > 0 && line.endsWith("]")) m.put("prop:" + line.substring(1, a), line.substring(a + 4, line.length() - 1));
            }
            r.close();
            p.destroy();
        } catch (Throwable ignored) { }
        return m;
    }

    // ───────────────────────── مراقبة الأضواء ─────────────────────────

    private final BroadcastReceiver br = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) { event("broadcast: " + i.getAction(), null, null); }
    };

    private void startWatch() {
        watching = true;
        watchTxt.setText(I18n.L("أوقف المراقبة", "Stop watching"));
        events.setLength(0);
        cands.removeAllViews();
        event(I18n.L("بدأت المراقبة · الوضع الليلي: ", "Watching started · night mode: ") + night(), null, null);
        new Thread(new Runnable() { public void run() {
            final Map<String, String> s = new HashMap<String, String>(settings());
            s.putAll(props());
            runOnUiThread(new Runnable() { public void run() { snap = s; h.postDelayed(poll, 1000); } });
        }}).start();
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            f.addAction(Intent.ACTION_POWER_CONNECTED);
            f.addAction(Intent.ACTION_POWER_DISCONNECTED);
            for (String a : new String[]{"com.tw.service.acc", "com.tw.acc", "com.tw.ill", "com.tw.light", "android.intent.action.ILL", "com.android.ill"}) f.addAction(a);
            registerReceiver(br, f);
        } catch (Throwable ignored) { }
        for (Uri u : new Uri[]{Settings.System.CONTENT_URI, Settings.Secure.CONTENT_URI}) {
            ContentObserver o = new ContentObserver(h) {
                @Override public void onChange(boolean self, Uri uri) { h.removeCallbacks(poll); h.postDelayed(poll, 150); }
            };
            try { getContentResolver().registerContentObserver(u, true, o); observers.add(o); } catch (Throwable ignored) { }
        }
    }

    private void stopWatch() {
        watching = false;
        watchTxt.setText(I18n.L("ابدأ مراقبة الأضواء", "Start watching the lights"));
        h.removeCallbacks(poll);
        try { unregisterReceiver(br); } catch (Throwable ignored) { }
        for (ContentObserver o : observers) try { getContentResolver().unregisterContentObserver(o); } catch (Throwable ignored) { }
        observers.clear();
        event(I18n.L("انتهت المراقبة", "Watching stopped"), null, null);
    }

    private boolean polling;
    private final Runnable poll = new Runnable() {
        public void run() {
            if (!watching || snap == null || polling) return;
            polling = true;
            new Thread(new Runnable() { public void run() {
                final Map<String, String> now = new HashMap<String, String>(settings());
                now.putAll(props());
                runOnUiThread(new Runnable() { public void run() {
                    polling = false;
                    if (!watching) return;
                    for (Map.Entry<String, String> e : now.entrySet()) {
                        String k = e.getKey(), v = e.getValue(), old = snap.get(k);
                        if (v == null ? old == null : v.equals(old)) continue;
                        if (k.contains("time") || k.contains("uptime") || k.contains("boottime") || k.endsWith(".stat")) continue;
                        event(k + ": " + old + " → " + v, k, v);
                    }
                    snap = now;
                    h.postDelayed(poll, 1500);
                }});
            }}).start();
        }
    };

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (watching) event(I18n.L("الوضع الليلي: ", "Night mode: ") + night(), "night", String.valueOf(night()));
    }

    private void event(String text, final String key, final String val) {
        String line = tf.format(new Date()) + "  " + text;
        events.append(line).append('\n');
        show();
        if (key == null) return;
        TextView t = tv("👉 " + line, 15, Main.RED, true);
        t.setPadding(px(6), px(8), px(6), px(8));
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if ("night".equals(key)) prefs.edit().remove("lit_src").remove("lit_on").apply();
            else prefs.edit().putString("lit_src", key).putString("lit_on", val).apply();
            toast(I18n.L("اعتُمدت إشارة الأضواء: ", "Light signal set: ") + ("night".equals(key) ? I18n.L("الوضع الليلي", "night mode") : key + " = " + val));
        }});
        cands.addView(t, 0);
        while (cands.getChildCount() > 6) cands.removeViewAt(cands.getChildCount() - 1);
    }

    private void save() {
        String text = "My Car diagnostics · " + new Date() + "\n\n" + (events.length() > 0 ? "── Changes ──\n" + events + "\n" : "") + report;
        List<String> ok = new ArrayList<String>();
        List<File> dirs = new ArrayList<File>();
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        File primary = Environment.getExternalStorageDirectory();
        for (File r : PrayerActivity.AudioFiles.roots()) if (!Dash.isPrimary(r, primary)) dirs.add(r);
        dirs.add(getExternalFilesDir(null));
        for (File d : dirs) {
            try {
                if (d == null) continue;
                d.mkdirs();
                File f = new File(d, "MyCar-diag.txt");
                FileOutputStream os = new FileOutputStream(f);
                os.write(text.getBytes("UTF-8"));
                os.close();
                ok.add(f.getAbsolutePath());
            } catch (Throwable ignored) { }
        }
        toast(ok.isEmpty() ? I18n.L("تعذّر الحفظ", "Could not save") : I18n.L("حُفظ في: ", "Saved to: ") + ok.get(0) + (ok.size() > 1 ? I18n.L(" وعلى الفلاشة", " and the USB stick") : ""));
    }

    @Override
    protected void onDestroy() {
        if (watching) stopWatch();
        super.onDestroy();
    }

    // ───────────────────────── قراءة الإشارة المعتمدة (يستعملها Dash) ─────────────────────────

    /** القيمة الحالية لإشارة مثل sys:key أو prop:key؛ null إن تعذّرت. */
    static String signal(Context c, String src) {
        try {
            if (src.startsWith("sys:")) return Settings.System.getString(c.getContentResolver(), src.substring(4));
            if (src.startsWith("sec:")) return Settings.Secure.getString(c.getContentResolver(), src.substring(4));
            if (src.startsWith("glob:") && Build.VERSION.SDK_INT >= 17) return Settings.Global.getString(c.getContentResolver(), src.substring(5));
            if (src.startsWith("prop:")) {
                Class<?> sp = Class.forName("android.os.SystemProperties");
                return (String) sp.getMethod("get", String.class).invoke(null, src.substring(5));
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
