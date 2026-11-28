package com.stradaos.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * My Car · رسائل شاشة TW التي كشفها «كاشف لانشر المصنع»:
 *  - الليل/النهار (مع أضواء السيارة): com.tw.service يرسل notify_theme_change، وتطبيق ربط الموبايل يستقبل OUT_DARK_START/STOP.
 *  - تشغيل السيارة/استيقاظ الشاشة: com.unisound.intent.action.ACC_ON / DO_WAKEUP و com.android.action_acc_on.
 * نسمعها كلها، نسجّلها، ونتصرّف: صورة الأضواء، والعودة لـ My Car عند تشغيل السيارة.
 */
final class CarSignals {

    static final String[] LIGHT = {
        "com.zjinnova.zlink.action.OUT_DARK_START", "com.zjinnova.zlink.action.OUT_DARK_STOP",
        "notify_theme_change", "NOTIFY_THEME_CHANGE", "com.tw.notify_theme_change", "com.tw.theme.change",
        "REQ_NIGHT_MODE_CMD", "REQ_DAY_MODE_CMD", "com.tw.action.NIGHT_MODE", "com.tw.action.DAY_MODE",
        "android.intent.action.DAY_NIGHT_CHANGED",
    };
    static final String[] WAKE = {
        "com.unisound.intent.action.ACC_ON", "com.unisound.intent.action.DO_WAKEUP", "com.unisound.intent.action.DO_WAKEUP_DOFUN",
        "com.android.action_acc_on", "android.intent.action.ACC_ON", "YZS_ACC_ON",
    };
    static final String[] SLEEP = {
        "com.unisound.intent.action.ACC_OFF", "com.unisound.intent.action.DO_SLEEP", "com.unisound.intent.action.DO_SHUTDOWN",
        "com.android.action_acc_off", "YZS_ACC_OFF",
    };

    /** آخر حالة ليل/نهار من الشاشة: null = لم تصل رسالة واضحة بعد. */
    static volatile Boolean dark;
    static final LinkedList<String> log = new LinkedList<String>();
    private static boolean registered;
    static Runnable onLight;   // يعيد بناء الواجهة فوراً

    static void register(Context c) {
        if (registered) return;
        registered = true;
        final Context app = c.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("launcher", 0);
        if (p.contains("sig_dark") && System.currentTimeMillis() - p.getLong("sig_dark_at", 0) < 12 * 3600000L) dark = p.getBoolean("sig_dark", false);
        IntentFilter f = new IntentFilter();
        for (String a : LIGHT) f.addAction(a);
        for (String a : WAKE) f.addAction(a);
        for (String a : SLEEP) f.addAction(a);
        try { app.registerReceiver(new BroadcastReceiver() { public void onReceive(Context x, Intent i) { handle(x, i); } }, f); }
        catch (Throwable e) { Guard.log("CarSignals: " + e); }
    }

    static String describe(Intent i) {
        StringBuilder b = new StringBuilder(String.valueOf(i.getAction()));
        try {
            Bundle e = i.getExtras();
            if (e != null) for (String k : e.keySet()) b.append(' ').append(k).append('=').append(e.get(k));
        } catch (Throwable ignored) { }
        return b.toString();
    }

    static void handle(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        String d = describe(i);
        synchronized (log) {
            log.addFirst(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + d);
            while (log.size() > 60) log.removeLast();
        }
        Guard.log("signal: " + d);
        android.util.Log.i("MyCarSig", d + " -> night=" + nightOf(a, d));
        Boolean nd = nightOf(a, d);
        if (nd != null) {
            dark = nd;
            c.getSharedPreferences("launcher", 0).edit().putBoolean("sig_dark", nd).putLong("sig_dark_at", System.currentTimeMillis()).apply();
            Runnable r = onLight;
            if (r != null) try { r.run(); } catch (Throwable ignored) { }
        }
        for (String w : WAKE) if (w.equals(a)) { wake(c); break; }
    }

    /** نستخرج ليل/نهار من اسم الرسالة أو من محتواها. */
    static Boolean nightOf(String a, String d) {
        if (a.endsWith("OUT_DARK_START") || a.contains("NIGHT")) return true;
        if (a.endsWith("OUT_DARK_STOP") || a.contains("DAY_MODE")) return false;
        String low = d.toLowerCase(Locale.US);
        if (low.contains("night")) return true;
        if (low.contains("=day") || low.contains("day_")) return false;
        return null;
    }

    /** تشغيل السيارة: My Car للواجهة بالثيم المحفوظ، ثم الترحيب. */
    static void wake(Context c) {
        try {
            if (!c.getSharedPreferences("launcher", 0).getBoolean("boot_open", true)) return;
            final Context app = c.getApplicationContext();
            Runnable open = new Runnable() { public void run() {
                try { app.startActivity(new Intent(app, Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("from_wake", true)); }
                catch (Throwable ignored) { }
            }};
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(open);
            h.postDelayed(open, 3000);   // إن فتح لانشر المصنع نفسه بعدنا بقليل
        } catch (Throwable e) { Guard.log("wake: " + e); }
    }
}
