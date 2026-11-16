package com.stradaos.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * My Car · قناة TW الداخلية (منصة Topway/TS7): مكتبة مخفية في النظام تنقل رسائل الـMCU (الأضواء، الإشعال، الرجوع…)
 * إلى تطبيقات com.tw. الواجهة غير موثقة، فكل شيء بالانعكاس (reflection) ومحمي من الأعطال.
 * الرسالة تُحفظ كنص «arg1,arg2,obj» ويُقارن بالقيمة التي اختارها المستخدم في صفحة التشخيص.
 */
final class TwSignal {

    static final String[] CLASSES = {"android.tw.john.TWUtil", "com.tw.john.TWUtil", "android.tw.TWUtil", "com.tw.util.TWUtil"};

    interface Sink { void on(int what, String value); }

    private Object util;
    private Class<?> cls;
    String error = "";

    static Class<?> find() {
        for (String n : CLASSES) try { return Class.forName(n); } catch (Throwable ignored) { }
        return null;
    }

    /** وصف الصنف وطرقه (لتقرير التشخيص). */
    static String describe() {
        Class<?> c = find();
        if (c == null) return I18n.L("مكتبة TW غير موجودة بالأسماء المعروفة", "TW library not found under known names");
        StringBuilder b = new StringBuilder(c.getName()).append('\n');
        for (Method mt : c.getDeclaredMethods()) {
            b.append("   ").append(mt.getName()).append('(');
            Class<?>[] ps = mt.getParameterTypes();
            for (int i = 0; i < ps.length; i++) b.append(i > 0 ? "," : "").append(ps[i].getSimpleName());
            b.append(")\n");
        }
        return b.toString();
    }

    static String value(Message msg) {
        StringBuilder b = new StringBuilder().append(msg.arg1).append(',').append(msg.arg2);
        if (msg.obj instanceof byte[]) {
            b.append(',');
            for (byte x : (byte[]) msg.obj) b.append(String.format(Locale.US, "%02X", x & 0xFF));
        } else if (msg.obj != null) b.append(',').append(msg.obj);
        return b.toString();
    }

    /** يفتح القناة على الأرقام المطلوبة ويرسل كل رسالة لـsink. */
    boolean open(short[] ids, final Sink sink) {
        try {
            cls = find();
            if (cls == null) { error = describe(); return false; }
            Object u = null;
            for (Constructor<?> k : cls.getDeclaredConstructors()) {
                k.setAccessible(true);
                Class<?>[] ps = k.getParameterTypes();
                try {
                    if (ps.length == 0) u = k.newInstance();
                    else if (ps.length == 1 && ps[0] == int.class) u = k.newInstance(0);
                } catch (Throwable ignored) { }
                if (u != null) break;
            }
            if (u == null) { error = "no constructor"; return false; }
            Integer r = null;
            for (Method mt : cls.getMethods()) {
                if (!mt.getName().equals("open")) continue;
                Class<?>[] ps = mt.getParameterTypes();
                if (ps.length == 1 && ps[0] == short[].class) { r = (Integer) mt.invoke(u, (Object) ids); break; }
                if (ps.length == 2 && ps[0] == short[].class && ps[1] == int.class) { r = (Integer) mt.invoke(u, ids, 0); break; }
            }
            if (r == null) { error = "no open(short[])"; return false; }
            Handler hd = new Handler(Looper.getMainLooper()) {
                @Override public void handleMessage(Message msg) { try { sink.on(msg.what, value(msg)); } catch (Throwable ignored) { } }
            };
            Method add = null;
            for (Method mt : cls.getMethods()) if (mt.getName().equals("addHandler") && mt.getParameterTypes().length == 2) add = mt;
            if (add == null) { error = "no addHandler"; return false; }
            add.invoke(u, "mycar", hd);
            try { cls.getMethod("start").invoke(u); } catch (Throwable ignored) { }
            util = u;
            error = "open=" + r;
            return true;
        } catch (Throwable e) {
            error = String.valueOf(e.getCause() != null ? e.getCause() : e);
            return false;
        }
    }

    /** يطلب من الـMCU إرسال الحالة الحالية لرقم (إن كانت الطريقة موجودة). */
    void request(int what) {
        try { cls.getMethod("write", int.class, int.class).invoke(util, what, 255); } catch (Throwable ignored) { }
    }

    void close() {
        if (util == null) return;
        try { cls.getMethod("removeHandler", String.class).invoke(util, "mycar"); } catch (Throwable ignored) { }
        try { cls.getMethod("stop").invoke(util); } catch (Throwable ignored) { }
        try { cls.getMethod("close").invoke(util); } catch (Throwable ignored) { }
        util = null;
    }

    // ── مستمع دائم للإشارة المختارة (tw:<what>) ──

    private static TwSignal live;
    private static int liveWhat = -1;
    static volatile String liveValue;

    static void ensure(Context c, String src) {
        try {
            if (src == null || !src.startsWith("tw:")) { if (live != null) { live.close(); live = null; liveWhat = -1; } return; }
            int what = Integer.parseInt(src.substring(3));
            if (live != null && liveWhat == what) return;
            if (live != null) live.close();
            live = new TwSignal();
            liveWhat = what;
            final int w = what;
            if (live.open(new short[]{(short) what}, new Sink() { public void on(int wh, String v) { if (wh == w) liveValue = v; } })) live.request(what);
            else { Guard.log("TwSignal: " + live.error); live = null; }
        } catch (Throwable e) { Guard.log("TwSignal: " + e); }
    }
}
