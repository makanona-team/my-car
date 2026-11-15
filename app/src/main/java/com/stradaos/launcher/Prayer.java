package com.stradaos.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * My Car · مواقيت الصلاة والتاريخ الهجري واتجاه القبلة. كل شيء يُحسب على الشاشة نفسها، بدون إنترنت.
 *
 * الحساب الفلكي على طريقة PrayTimes.org (موقع الشمس، معادلة الزمن، زوايا الفجر والعشاء). المكان: أقرب مركز مدينة
 * سورية لموقع السيارة (Syria.nearest)، أو مدينة يختارها المستخدم، أو موقع GPS نفسه خارج سوريا.
 */
public class Prayer {

    static final String PREFS = "launcher";

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    static final String[] NAMES_AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static final String[] NAMES_EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};

    public static String name(int i) { return I18n.L(NAMES_AR[i], NAMES_EN[i]); }

    // ── طرق الحساب: id، الاسم، زاوية الفجر، زاوية العشاء (أو دقائق بعد المغرب إذا كانت سالبة) ──
    static final String[] METHOD_IDS = {"mwl", "egypt", "makkah", "karachi", "isna"};
    static final String[] METHOD_AR = {"رابطة العالم الإسلامي (18° / 17°)", "الهيئة المصرية العامة للمساحة (19.5° / 17.5°)",
            "أم القرى · مكة (18.5° / 90 دقيقة)", "جامعة العلوم الإسلامية · كراتشي (18° / 18°)", "أمريكا الشمالية ISNA (15° / 15°)"};
    static final String[] METHOD_EN = {"Muslim World League (18° / 17°)", "Egyptian General Authority (19.5° / 17.5°)",
            "Umm al-Qura, Makkah (18.5° / 90 min)", "University of Islamic Sciences, Karachi (18° / 18°)", "ISNA, North America (15° / 15°)"};
    static final double[] METHOD_FAJR = {18, 19.5, 18.5, 18, 15};
    static final double[] METHOD_ISHA = {17, 17.5, -90, 18, 15};

    /** المكان المستخدم للحساب. */
    public static class Place {
        public double lat, lon;
        public String name;
        /** true = من GPS (تلقائي)؛ false = مدينة مختارة يدوياً. */
        public boolean auto;
        /** true = لم يصل أي موقع بعد (مدينة افتراضية). */
        public boolean fallback;
        public Syria.City city;
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }

    public static int methodIndex(SharedPreferences p) {
        String m = p.getString("pr_method", "mwl");
        for (int i = 0; i < METHOD_IDS.length; i++) if (METHOD_IDS[i].equals(m)) return i;
        return 0;
    }

    public static String methodName(int i) { return I18n.L(METHOD_AR[i], METHOD_EN[i]); }

    // ───────────────────────── المكان ─────────────────────────

    /** يحفظ آخر موقع للسيارة (يُستدعى من GPS). يُرجع true إذا تغيّرت المدينة. */
    public static boolean saveFix(Context c, double lat, double lon) {
        SharedPreferences p = prefs(c);
        double oLat = Double.longBitsToDouble(p.getLong("pr_lat", Double.doubleToLongBits(Double.NaN)));
        double oLon = Double.longBitsToDouble(p.getLong("pr_lon", Double.doubleToLongBits(Double.NaN)));
        if (!Double.isNaN(oLat) && Syria.km(oLat, oLon, lat, lon) < 2) return false;
        String before = place(c).name;
        p.edit().putLong("pr_lat", Double.doubleToLongBits(lat)).putLong("pr_lon", Double.doubleToLongBits(lon))
                .putLong("pr_fix_at", System.currentTimeMillis()).apply();
        String after = place(c).name;
        return before == null || !before.equals(after);
    }

    public static Place place(Context c) {
        SharedPreferences p = prefs(c);
        Place pl = new Place();
        Syria.City manual = Syria.byName(p.getString("pr_city", ""));
        if (manual != null) {
            pl.lat = manual.lat; pl.lon = manual.lon; pl.name = manual.name(); pl.city = manual; pl.auto = false;
            return pl;
        }
        pl.auto = true;
        double lat = Double.longBitsToDouble(p.getLong("pr_lat", Double.doubleToLongBits(Double.NaN)));
        double lon = Double.longBitsToDouble(p.getLong("pr_lon", Double.doubleToLongBits(Double.NaN)));
        if (Double.isNaN(lat)) {
            Location l = lastKnown(c);
            if (l != null) { lat = l.getLatitude(); lon = l.getLongitude(); }
        }
        if (Double.isNaN(lat)) {
            Syria.City d = Syria.byName("دير الزور");
            pl.lat = d.lat; pl.lon = d.lon; pl.name = d.name(); pl.city = d; pl.fallback = true;
            return pl;
        }
        Syria.City near = Syria.nearest(lat, lon, p.getBoolean("pr_capitals", false));
        if (near != null) {
            // مواقيت المدينة (مركزها) وليس الريف
            pl.lat = near.lat; pl.lon = near.lon; pl.name = near.name(); pl.city = near;
        } else {
            pl.lat = lat; pl.lon = lon; pl.name = I18n.L("موقعك الحالي", "Your location");
        }
        return pl;
    }

    static Location lastKnown(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String pr : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(pr);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Throwable ignored) { }
            }
            return best;
        } catch (Throwable e) { return null; }
    }

    // ───────────────────────── الحساب الفلكي ─────────────────────────

    /**
     * مواقيت يوم كامل بالدقائق من منتصف الليل (التوقيت المحلي للشاشة)، بعد تعديلات المستخدم.
     * الترتيب: الفجر، الشروق، الظهر، العصر، المغرب، العشاء.
     */
    public static int[] times(Context c, Calendar day) {
        SharedPreferences p = prefs(c);
        Place pl = place(c);
        return times(p, pl.lat, pl.lon, day);
    }

    public static int[] times(SharedPreferences p, double lat, double lon, Calendar day) {
        int m = methodIndex(p);
        double asrFactor = p.getInt("pr_asr", 0) == 1 ? 2 : 1;
        Calendar noon = (Calendar) day.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        noon.set(Calendar.SECOND, 0);
        noon.set(Calendar.MILLISECOND, 0);
        TimeZone tz = day.getTimeZone();
        double tzH = tz.getOffset(noon.getTimeInMillis()) / 3600000.0;
        double[] h = compute(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH),
                lat, lon, tzH, METHOD_FAJR[m], METHOD_ISHA[m], asrFactor);
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            if (Double.isNaN(h[i])) { out[i] = -1; continue; }
            int mins = (int) Math.round(h[i] * 60) + p.getInt("pr_adj_" + i, 0);
            out[i] = ((mins % 1440) + 1440) % 1440;
        }
        return out;
    }

    /** الحساب نفسه (ساعات محلية عشرية). ishaAngle سالب = دقائق بعد المغرب. */
    static double[] compute(int year, int month, int day, double lat, double lon, double tz,
                            double fajrAngle, double ishaAngle, double asrFactor) {
        double jd = julian(year, month, day) - lon / (15.0 * 24.0);
        // تقدير أولي ثم تكرارين لتحسين الدقة
        double[] t = {5, 6, 12, 13, 18, 18, 18};
        for (int it = 0; it < 2; it++) {
            for (int i = 0; i < t.length; i++) t[i] /= 24.0;
            double fajr = sunAngleTime(jd, fajrAngle, t[0], lat, true);
            double sunrise = sunAngleTime(jd, 0.833, t[1], lat, true);
            double dhuhr = midDay(jd, t[2]);
            double asr = asrTime(jd, asrFactor, t[3], lat);
            double sunset = sunAngleTime(jd, 0.833, t[4], lat, false);
            double maghrib = sunset;
            double isha = ishaAngle > 0 ? sunAngleTime(jd, ishaAngle, t[6], lat, false) : maghrib;
            t = new double[]{fajr, sunrise, dhuhr, asr, sunset, maghrib, isha};
        }
        double adj = tz - lon / 15.0;
        double[] r = new double[6];
        r[FAJR] = t[0] + adj;
        r[SUNRISE] = t[1] + adj;
        r[DHUHR] = t[2] + adj;
        r[ASR] = t[3] + adj;
        r[MAGHRIB] = t[5] + adj;
        r[ISHA] = ishaAngle > 0 ? t[6] + adj : r[MAGHRIB] + (-ishaAngle) / 60.0;
        return r;
    }

    static double julian(int year, int month, int day) {
        if (month <= 2) { year -= 1; month += 12; }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }

    /** {الميل، معادلة الزمن} للشمس. */
    static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        double eqt = q / 15.0 - fix(ra, 24);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    static double midDay(double jd, double t) {
        double eqt = sunPosition(jd + t)[1];
        return fix(12 - eqt, 24);
    }

    static double sunAngleTime(double jd, double angle, double t, double lat, boolean ccw) {
        double decl = sunPosition(jd + t)[0];
        double noon = midDay(jd, t);
        double x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        if (x < -1 || x > 1) return Double.NaN;
        double tt = darccos(x) / 15.0;
        return noon + (ccw ? -tt : tt);
    }

    static double asrTime(double jd, double factor, double t, double lat) {
        double decl = sunPosition(jd + t)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return sunAngleTime(jd, angle, t, lat, false);
    }

    // ───────────────────────── الصلاة القادمة ─────────────────────────

    /** {رقم الصلاة، وقتها بالمللي ثانية}. تُتجاوز «الشروق» لأنها ليست صلاة. */
    public static long[] next(Context c, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        for (int dayOff = 0; dayOff < 2; dayOff++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_MONTH, dayOff);
            int[] t = times(c, d);
            for (int i = 0; i < 6; i++) {
                if (i == SUNRISE || t[i] < 0) continue;
                long at = at(d, t[i]);
                if (at > now) return new long[]{i, at};
            }
        }
        return null;
    }

    /** ميلي ثانية لدقيقة معينة من يوم معين. */
    public static long at(Calendar day, int minutes) {
        Calendar x = (Calendar) day.clone();
        x.set(Calendar.HOUR_OF_DAY, minutes / 60);
        x.set(Calendar.MINUTE, minutes % 60);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    public static String hhmm(int minutes, boolean h24) {
        if (minutes < 0) return "--:--";
        int hh = minutes / 60, mm = minutes % 60;
        if (h24) return String.format(java.util.Locale.US, "%02d:%02d", hh, mm);
        int h12 = hh % 12 == 0 ? 12 : hh % 12;
        return String.format(java.util.Locale.US, "%d:%02d", h12, mm) + " " + (hh < 12 ? I18n.L("ص", "AM") : I18n.L("م", "PM"));
    }

    /** «بعد ساعة و12 دقيقة» / «in 1 h 12 min». */
    public static String remaining(long ms) {
        long m = Math.max(0, (ms + 59999) / 60000);
        long hh = m / 60, mm = m % 60;
        if ("en".equals(I18n.lang)) return "in " + (hh > 0 ? hh + " h " : "") + mm + " min";
        if (hh == 0) return "بعد " + mm + " دقيقة";
        return "بعد " + hh + " س " + (mm > 0 ? "و" + mm + " د" : "");
    }

    // ───────────────────────── التاريخ الهجري ─────────────────────────

    static final String[] HIJRI_AR = {"محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"};
    static final String[] HIJRI_EN = {"Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"};

    /** {يوم، شهر 1-12، سنة} بالتقويم الهجري الجدولي (مع تعديل المستخدم ±أيام). */
    public static int[] hijri(Calendar g, int adjustDays) {
        Calendar x = (Calendar) g.clone();
        x.add(Calendar.DAY_OF_MONTH, adjustDays);
        int y = x.get(Calendar.YEAR), m = x.get(Calendar.MONTH) + 1, d = x.get(Calendar.DAY_OF_MONTH);
        // يوم جولياني (عدد صحيح)
        int a = (14 - m) / 12;
        int yy = y + 4800 - a, mm = m + 12 * a - 3;
        int jd = d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
        int l = jd - 1948440 + 10632;
        int n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        int j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int hm = (24 * l) / 709;
        int hd = l - (709 * hm) / 24;
        int hy = 30 * n + j - 30;
        return new int[]{hd, hm, hy};
    }

    public static String hijriText(Context c, Calendar g) {
        int[] h = hijri(g, prefs(c).getInt("pr_hijri_adj", 0));
        int mi = Math.max(1, Math.min(12, h[1])) - 1;
        return h[0] + " " + I18n.L(HIJRI_AR[mi], HIJRI_EN[mi]) + " " + h[2] + I18n.L(" هـ", " AH");
    }

    // ───────────────────────── القبلة ─────────────────────────

    static final double KAABA_LAT = 21.422487, KAABA_LON = 39.826206;

    /** اتجاه القبلة بالدرجات من الشمال الجغرافي (مع عقارب الساعة). */
    public static double qibla(double lat, double lon) {
        double phi = Math.toRadians(lat), dl = Math.toRadians(KAABA_LON - lon), phiK = Math.toRadians(KAABA_LAT);
        double q = Math.toDegrees(Math.atan2(Math.sin(dl), Math.cos(phi) * Math.tan(phiK) - Math.sin(phi) * Math.cos(dl)));
        return (q + 360) % 360;
    }

    public static String compassName(double deg) {
        String[] ar = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        String[] en = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int i = (int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8;
        return I18n.L(ar[i], en[i]);
    }

    // ───────────────────────── تنبيه الأذان ─────────────────────────

    /** الصلوات التي يُنبَّه لها (بت لكل صلاة). افتراضياً كل الصلوات الخمس بدون الشروق. */
    public static int alertMask(SharedPreferences p) {
        return p.getInt("pr_alert_mask", (1 << FAJR) | (1 << DHUHR) | (1 << ASR) | (1 << MAGHRIB) | (1 << ISHA));
    }

    /** يضبط المنبّه على الأذان القادم (أو يلغيه إذا كان التنبيه مطفأ). */
    public static void schedule(Context c) {
        try {
            SharedPreferences p = prefs(c);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent it = new Intent(c, PrayerAlarm.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, 7301, it, PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(pi);
            if (!p.getBoolean("pr_alert", false)) return;
            int mask = alertMask(p);
            if (mask == 0) return;
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            for (int dayOff = 0; dayOff < 3; dayOff++) {
                Calendar d = (Calendar) cal.clone();
                d.add(Calendar.DAY_OF_MONTH, dayOff);
                int[] t = times(c, d);
                for (int i = 0; i < 6; i++) {
                    if ((mask & (1 << i)) == 0 || t[i] < 0) continue;
                    long at = at(d, t[i]);
                    if (at <= now + 5000) continue;
                    it.putExtra("prayer", i);
                    it.putExtra("at", at);
                    pi = PendingIntent.getBroadcast(c, 7301, it, PendingIntent.FLAG_UPDATE_CURRENT);
                    if (android.os.Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    else if (android.os.Build.VERSION.SDK_INT >= 19) am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                    else am.set(AlarmManager.RTC_WAKEUP, at, pi);
                    return;
                }
            }
        } catch (Throwable e) {
            Guard.log("Prayer.schedule: " + e);
        }
    }
}
