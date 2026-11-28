package com.stradaos.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** «Abrir StradaOS al encender»: para pantallas que siempre arrancan en su menú de fábrica (funciona en Android 4 a 9). */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a != null && !a.contains("BOOT") && !a.contains("QUICKBOOT")) { CarSignals.handle(c, i); return; }   // رسائل ACC/الليل من شاشة TW
        // My Car: إعادة ضبط منبّه الأذان بعد تشغيل الشاشة
        try { Prayer.schedule(c); } catch (Throwable ignored) { }
        if (!c.getSharedPreferences("launcher", 0).getBoolean("boot_open", true)) return;
        final Context app = c.getApplicationContext();
        final Runnable open = new Runnable() { public void run() {
            try {
                app.startActivity(new Intent(app, Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } catch (Throwable ignored) { }
        }};
        open.run();
        // بعض الشاشات تفتح لانشر المصنع بعد إشارة الإقلاع بقليل: نعود للواجهة مرة ثانية
        try { new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(open, 4000); } catch (Throwable ignored) { }
    }
}
