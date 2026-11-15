package com.stradaos.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.widget.Toast;

import java.util.HashMap;

/** My Car · تنبيهات OBD: حرارة المحرك المرتفعة، الدينامو لا يشحن، جهد شحن مرتفع، بطارية ضعيفة. */
public class ObdAlerts {

    private static long lastHot, lastNoCharge, lastOver, lastWeak, lowSince;

    public static int hotLimit(SharedPreferences p) { return p.getInt("obd_hot", 105); }

    static void check(Context c, HashMap<String, Double> v) {
        SharedPreferences p = c.getSharedPreferences("launcher", 0);
        if (!p.getBoolean("obd_alerts", true)) return;
        long now = System.currentTimeMillis();
        Double t = v.get(Obd.COOLANT), volt = v.get(Obd.VOLT), rpm = v.get(Obd.RPM);
        if (t != null && t >= hotLimit(p) && now - lastHot > 60000) {
            lastHot = now;
            alert(c, I18n.L("⚠ حرارة المحرك مرتفعة: ", "⚠ Engine temperature high: ") + Math.round(t) + "°C",
                I18n.L("خفّف السرعة وراقب الحرارة. إن استمرت بالارتفاع توقّف وأطفئ المحرك بأمان.", "Ease off and watch it. If it keeps rising, stop safely and turn the engine off."), true);
        }
        boolean running = rpm != null && rpm > 500;
        if (volt != null) {
            if (running && volt < 12.7) {
                if (lowSince == 0) lowSince = now;
                if (now - lowSince > 30000 && now - lastNoCharge > 10 * 60000) {
                    lastNoCharge = now;
                    alert(c, I18n.L("⚠ الدينامو لا يشحن: ", "⚠ Alternator not charging: ") + String.format(java.util.Locale.US, "%.1f V", volt),
                        I18n.L("جهد البطارية منخفض والمحرك يعمل. افحص الدينامو والسير.", "Battery voltage is low with the engine running. Check the alternator and belt."), false);
                }
            } else lowSince = 0;
            if (running && volt > 15.2 && now - lastOver > 10 * 60000) {
                lastOver = now;
                alert(c, I18n.L("⚠ جهد الشحن مرتفع: ", "⚠ Charging voltage high: ") + String.format(java.util.Locale.US, "%.1f V", volt),
                    I18n.L("قد يكون منظم الدينامو معطلاً.", "The alternator regulator may be faulty."), false);
            }
            if (!running && volt < 11.9 && volt > 5 && now - lastWeak > 30 * 60000) {
                lastWeak = now;
                alert(c, I18n.L("البطارية ضعيفة: ", "Weak battery: ") + String.format(java.util.Locale.US, "%.1f V", volt),
                    I18n.L("قد لا يدور المحرك بسهولة. اشحن البطارية أو افحصها.", "The engine may struggle to start. Charge or check the battery."), false);
            }
        }
    }

    private static void alert(Context c, String title, String text, boolean beep) {
        try { Toast.makeText(c, title, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        if (beep) {
            try {
                final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 1200);
                new android.os.Handler().postDelayed(new Runnable() { public void run() { tg.release(); } }, 1500);
            } catch (Throwable ignored) { }
        }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("obd", "OBD2", NotificationManager.IMPORTANCE_HIGH));
            }
            Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "obd") : new Notification.Builder(c);
            Intent open = new Intent(c, ObdActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setSmallIcon(c.getApplicationInfo().icon).setContentTitle(title).setContentText(text).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 7501, open, PendingIntent.FLAG_UPDATE_CURRENT));
            nm.notify(7500, b.build());
        } catch (Throwable ignored) { }
    }
}
