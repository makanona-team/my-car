package com.stradaos.launcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * My Car · قناة TW في عملية منفصلة (:tw). مكتبة الشاشة الصينية قد تنهار انهياراً لا يُلتقط (native)،
 * فتنهار هذه العملية وحدها ويبقى اللانشر وصفحة التشخيص شغّالين.
 * قبل فتح كل دفعة تُكتب في tw_stage.txt؛ إن وُجد الملف عند البدء فالدفعة السابقة سبّبت انهياراً فتُتخطّى بعدها دائماً.
 */
public class TwService extends Service {

    static final String MSG = "com.feras.mycar.TW_MSG", BEAT = "com.feras.mycar.TW_BEAT";
    static final int BATCH = 256, MAX_ID = 0x1000;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<TwSignal> open = new ArrayList<TwSignal>();
    private String status = "";
    private String mode = "";
    private int liveWhat = -1;

    static File stageFile(Context c) { return new File(c.getFilesDir(), "tw_stage.txt"); }
    static File badFile(Context c) { return new File(c.getFilesDir(), "tw_bad.txt"); }

    static String read(File f) {
        try {
            if (!f.exists()) return "";
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[(int) Math.min(f.length(), 4096)];
            int n = in.read(b); in.close();
            return n > 0 ? new String(b, 0, n, "UTF-8").trim() : "";
        } catch (Throwable e) { return ""; }
    }

    static void write(File f, String s, boolean append) {
        try { FileOutputStream o = new FileOutputStream(f, append); o.write(s.getBytes("UTF-8")); o.close(); } catch (Throwable ignored) { }
    }

    /** الدفعات/الأوضاع التي سبّبت انهياراً سابقاً (مع تسجيل آخر انهيار إن وُجد). */
    static Set<String> bad(Context c) {
        String st = read(stageFile(c));
        if (st.length() > 0) {   // بقي من تشغيل سابق لم يكتمل = انهيار
            write(badFile(c), st + "\n", true);
            stageFile(c).delete();
            Guard.log("TW crash at " + st);
        }
        Set<String> s = new HashSet<String>();
        for (String l : read(badFile(c)).split("\n")) if (l.trim().length() > 0) s.add(l.trim());
        return s;
    }

    static void start(Context c, String mode, int what) {
        try { c.startService(new Intent(c, TwService.class).putExtra("mode", mode).putExtra("what", what)); } catch (Throwable e) { Guard.log("TwService.start: " + e); }
    }

    static void stop(Context c) {
        try { c.startService(new Intent(c, TwService.class).putExtra("mode", "stop")); } catch (Throwable ignored) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String md = i == null ? "" : i.getStringExtra("mode");
        if (md == null) md = "";
        if ("stop".equals(md)) { closeAll(); stopSelf(); h.postDelayed(new Runnable() { public void run() { android.os.Process.killProcess(android.os.Process.myPid()); } }, 300); return START_NOT_STICKY; }
        closeAll();
        mode = md;
        final Set<String> bad = bad(this);
        if ("sniff".equals(md)) {
            h.removeCallbacks(beat); h.post(beat);
            sniffBatch(0, bad);
        } else if ("live".equals(md)) {
            liveWhat = i.getIntExtra("what", -1);
            if (liveWhat < 0 || bad.contains("live:" + liveWhat)) { status = "live skipped (crashed before)"; stopSelf(); return START_NOT_STICKY; }
            write(stageFile(this), "live:" + liveWhat, false);
            TwSignal t = new TwSignal();
            final int w = liveWhat;
            boolean ok = t.open(new short[]{(short) w}, new TwSignal.Sink() { public void on(int wh, String v) { if (wh == w) send(wh, v); } });
            stageFile(this).delete();
            if (ok) { open.add(t); t.request(w); } else { Guard.log("TW live: " + t.error); stopSelf(); }
        }
        return START_NOT_STICKY;
    }

    private void sniffBatch(final int from, final Set<String> bad) {
        if (!"sniff".equals(mode)) return;
        if (from >= MAX_ID) { status += "· " + open.size() + " " + "batches open"; return; }
        String key = String.format(Locale.US, "batch:%04X", from);
        if (bad.contains(key)) {
            status += String.format(Locale.US, "%04X:skip ", from);
        } else {
            short[] ids = new short[BATCH];
            for (int k = 0; k < BATCH; k++) ids[k] = (short) (from + k);
            write(stageFile(this), key, false);
            TwSignal t = new TwSignal();
            boolean ok = t.open(ids, new TwSignal.Sink() { public void on(int wh, String v) { send(wh, v); } });
            stageFile(this).delete();
            if (ok) open.add(t);
            status += String.format(Locale.US, "%04X:%s ", from, ok ? "ok" : "x");
            if (!ok && from == 0) status += "(" + t.error.split("\n")[0] + ") ";
        }
        h.postDelayed(new Runnable() { public void run() { sniffBatch(from + BATCH, bad); } }, 250);
    }

    private final Runnable beat = new Runnable() { public void run() {
        if (!"sniff".equals(mode)) return;
        try { sendBroadcast(new Intent(BEAT).setPackage(getPackageName()).putExtra("status", status)); } catch (Throwable ignored) { }
        h.postDelayed(this, 1000);
    }};

    private void send(int what, String v) {
        try { sendBroadcast(new Intent(MSG).setPackage(getPackageName()).putExtra("what", what).putExtra("val", v)); } catch (Throwable ignored) { }
    }

    private void closeAll() {
        h.removeCallbacks(beat);
        mode = "";
        for (TwSignal t : open) try { t.close(); } catch (Throwable ignored) { }
        open.clear();
        status = "";
    }

    @Override public void onDestroy() { closeAll(); super.onDestroy(); }
}
