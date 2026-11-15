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
            // رحلة جديدة: الحالية تصير «آخر رحلة» وتُضاف لسجل الرحلات
            close(c, lastMove);
        }
        if (!Double.isNaN(lastLat) && now - lastAt < 60000) {
            float[] d = new float[1];
            Location.distanceBetween(lastLat, lastLon, l.getLatitude(), l.getLongitude(), d);
            if (kmh > 4 && d[0] > 2 && d[0] < 3000) {
                SharedPreferences.Editor e = p.edit()
                    .putFloat("trip_km", p.getFloat("trip_km", 0) + d[0] / 1000f)
                    .putLong("trip_ms", p.getLong("trip_ms", 0) + (now - lastAt))
                    .putLong("trip_move_at", now);
                if (kmh > p.getFloat("trip_max", 0)) e.putFloat("trip_max", kmh);
                if (now - lastSave > 15000) { e.apply(); lastSave = now; } else e.apply();
            }
        }
        lastLat = l.getLatitude(); lastLon = l.getLongitude(); lastAt = now;
    }

    /** ينهي الرحلة الحالية: تصير «آخر رحلة» وتُحفظ في السجل (آخر 20). */
    static void close(Context c, long end) {
        SharedPreferences p = prefs(c);
        float km = p.getFloat("trip_km", 0);
        if (km < 0.05f) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray(p.getString("trip_log", "[]"));
            org.json.JSONArray b = new org.json.JSONArray();
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("end", end).put("km", km).put("ms", p.getLong("trip_ms", 0)).put("max", p.getFloat("trip_max", 0)).put("fuel", p.getFloat("trip_fuel", 0));
            b.put(o);
            for (int i = 0; i < a.length() && b.length() < 20; i++) b.put(a.get(i));
            p.edit().putString("trip_log", b.toString()).apply();
        } catch (Throwable e) { Guard.log("Trip log: " + e); }
        p.edit().putFloat("trip_last_km", km).putLong("trip_last_ms", p.getLong("trip_ms", 0)).putLong("trip_last_end", end)
            .putFloat("trip_km", 0).putLong("trip_ms", 0).putFloat("trip_max", 0).putFloat("trip_fuel", 0).apply();
    }

    /** وقود محسوب من OBD (لترات) يضاف للرحلة الحالية. */
    static void addFuel(Context c, double litres) {
        if (!(litres > 0) || litres > 1) return;
        SharedPreferences p = prefs(c);
        p.edit().putFloat("trip_fuel", p.getFloat("trip_fuel", 0) + (float) litres).apply();
    }

    public static float fuel(Context c) { return prefs(c).getFloat("trip_fuel", 0); }

    /** «2.4 ل · 24,000 ل.س» (الكلفة إن حُدد سعر اللتر). */
    public static String fuelText(Context c, float l) {
        if (l <= 0.005f) return "--";
        String s = String.format(java.util.Locale.US, "%.1f", l) + I18n.L(" ل", " L");
        int price = prefs(c).getInt("fuel_price", 0);
        if (price > 0) s += " · " + String.format(java.util.Locale.US, "%,d", Math.round(l * price)) + I18n.L(" ل.س", " SYP");
        return s;
    }

    /** نافذة سجل الرحلات. */
    public static void showLog(final Main m) {
        SharedPreferences p = prefs(m);
        boolean mph = m.useMph();
        StringBuilder b = new StringBuilder();
        b.append(I18n.L("الرحلة الحالية: ", "Current trip: ")).append(dist(km(m), mph)).append(" · ").append(dur(ms(m)));
        if (fuel(m) > 0.005f) b.append(" · ").append(fuelText(m, fuel(m)));
        b.append("\n\n");
        try {
            org.json.JSONArray a = new org.json.JSONArray(p.getString("trip_log", "[]"));
            if (a.length() == 0) b.append(I18n.L("لا توجد رحلات منتهية بعد. تنتهي الرحلة بعد توقف 20 دقيقة أو إطفاء السيارة.", "No finished trips yet. A trip ends after a 20-minute stop or when the car is switched off."));
            java.text.SimpleDateFormat f = I18n.sdf(I18n.L("EEE d MMM · h:mm a", "EEE d MMM · h:mm a"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                b.append("• ").append(I18n.num(f.format(new java.util.Date(o.optLong("end"))))).append("\n   ")
                 .append(dist((float) o.optDouble("km"), mph)).append(" · ").append(dur(o.optLong("ms")));
                float mx = (float) o.optDouble("max", 0);
                if (mx > 1) b.append(" · ").append(I18n.L("أعلى سرعة ", "top ")).append(Math.round(mph ? mx * 0.621371f : mx)).append(mph ? " mph" : I18n.L(" كم/س", " km/h"));
                float fl = (float) o.optDouble("fuel", 0);
                if (fl > 0.005f) b.append("\n   ").append(fuelText(m, fl));
                b.append("\n\n");
            }
        } catch (Throwable e) { b.append(String.valueOf(e)); }
        new TB(m, Main.dlg()).raw().wide().setTitle(I18n.L("سجل الرحلات", "Trip log")).setMessage(I18n.num(b.toString().trim()))
            .setNeutralButton(I18n.L("مسح السجل", "Clear log"), new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface d, int w) {
                prefs(m).edit().putString("trip_log", "[]").apply();
            }})
            .setNegativeButton(I18n.L("إغلاق", "Close"), null).show();
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
