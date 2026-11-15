package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

/**
 * My Car · ملخص الرحلة من GPS: المسافة ومدة القيادة للرحلة الحالية، وآخر رحلة. رحلة جديدة تبدأ بعد توقف 20 دقيقة.
 */
public class Trip {

    static final long GAP = 20 * 60 * 1000L;
    private static double lastLat = Double.NaN, lastLon;
    private static long lastAt, lastSave;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("launcher", 0); }

    public static void feed(Context c, Location l) {
        if (l == null || (l.hasAccuracy() && l.getAccuracy() > 60)) return;
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        float kmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
        long lastMove = p.getLong("trip_move_at", 0);
        if (kmh > 4 && lastMove > 0 && now - lastMove > GAP && p.getFloat("trip_km", 0) > 0.05f) {
            // رحلة جديدة: الحالية تصير «آخر رحلة»
            p.edit().putFloat("trip_last_km", p.getFloat("trip_km", 0)).putLong("trip_last_ms", p.getLong("trip_ms", 0))
                .putLong("trip_last_end", lastMove).putFloat("trip_km", 0).putLong("trip_ms", 0).apply();
        }
        if (!Double.isNaN(lastLat) && now - lastAt < 60000) {
            float[] d = new float[1];
            Location.distanceBetween(lastLat, lastLon, l.getLatitude(), l.getLongitude(), d);
            if (kmh > 4 && d[0] > 2 && d[0] < 3000) {
                SharedPreferences.Editor e = p.edit()
                    .putFloat("trip_km", p.getFloat("trip_km", 0) + d[0] / 1000f)
                    .putLong("trip_ms", p.getLong("trip_ms", 0) + (now - lastAt))
                    .putLong("trip_move_at", now);
                if (now - lastSave > 15000) { e.apply(); lastSave = now; } else e.apply();
            }
        }
        lastLat = l.getLatitude(); lastLon = l.getLongitude(); lastAt = now;
    }

    public static float km(Context c) { return prefs(c).getFloat("trip_km", 0); }
    public static long ms(Context c) { return prefs(c).getLong("trip_ms", 0); }
    public static float lastKm(Context c) { return prefs(c).getFloat("trip_last_km", -1); }
    public static long lastMs(Context c) { return prefs(c).getLong("trip_last_ms", 0); }

    public static String dist(float km, boolean mph) {
        if (km < 0) return "--";
        float v = mph ? km * 0.621371f : km;
        return (v < 10 ? String.format(java.util.Locale.US, "%.1f", v) : String.valueOf(Math.round(v))) + (mph ? " mi" : I18n.L(" كم", " km"));
    }

    public static String dur(long ms) {
        long m = ms / 60000;
        if (m < 60) return m + I18n.L(" دقيقة", " min");
        return (m / 60) + I18n.L(" س ", " h ") + (m % 60) + I18n.L(" د", " min");
    }
}
