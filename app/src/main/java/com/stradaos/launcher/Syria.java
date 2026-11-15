package com.stradaos.launcher;

/**
 * My Car · مدن سوريا (مراكز المحافظات والمدن الرئيسية) بإحداثيات مركز المدينة.
 * تُستخدم لمواقيت الصلاة واسم المدينة: يُؤخذ موقع السيارة من GPS ويُعتمد أقرب مركز مدينة (وليس الريف).
 */
public class Syria {

    public static class City {
        public final String ar, en;
        public final double lat, lon;
        /** true = مركز محافظة. */
        public final boolean capital;

        City(String ar, String en, double lat, double lon, boolean capital) {
            this.ar = ar; this.en = en; this.lat = lat; this.lon = lon; this.capital = capital;
        }

        public String name() { return I18n.L(ar, en); }
    }

    /** أبعد مسافة (كم) عن أقرب مدينة سورية تُعتبر داخل سوريا؛ أبعد من ذلك يُستخدم موقع GPS نفسه. */
    public static final double MAX_KM = 60;

    public static final City[] CITIES = {
        // مراكز المحافظات الأربع عشرة
        new City("دمشق", "Damascus", 33.5138, 36.2765, true),
        new City("حلب", "Aleppo", 36.2021, 37.1343, true),
        new City("حمص", "Homs", 34.7324, 36.7137, true),
        new City("حماة", "Hama", 35.1318, 36.7578, true),
        new City("اللاذقية", "Latakia", 35.5317, 35.7915, true),
        new City("طرطوس", "Tartus", 34.8890, 35.8866, true),
        new City("إدلب", "Idlib", 35.9306, 36.6339, true),
        new City("دير الزور", "Deir ez-Zor", 35.3359, 40.1408, true),
        new City("الرقة", "Raqqa", 35.9528, 39.0079, true),
        new City("الحسكة", "Al-Hasakah", 36.5024, 40.7477, true),
        new City("درعا", "Daraa", 32.6189, 36.1021, true),
        new City("السويداء", "As-Suwayda", 32.7090, 36.5695, true),
        new City("القنيطرة", "Quneitra", 33.1256, 35.8241, true),
        new City("ريف دمشق · دوما", "Douma", 33.5711, 36.4019, false),
        // دير الزور
        new City("الميادين", "Al-Mayadin", 35.0205, 40.4528, false),
        new City("البوكمال", "Abu Kamal", 34.4519, 40.9186, false),
        new City("البصيرة", "Al-Busayrah", 35.1567, 40.4300, false),
        new City("هجين", "Hajin", 34.6953, 40.8303, false),
        new City("الصور", "Al-Sur", 35.5458, 40.6339, false),
        // الرقة
        new City("الطبقة", "Al-Tabqa", 35.8371, 38.5481, false),
        new City("تل أبيض", "Tell Abyad", 36.6975, 38.9567, false),
        new City("معدان", "Ma'dan", 35.7456, 39.6556, false),
        new City("سلوك", "Suluk", 36.6031, 39.1414, false),
        // الحسكة
        new City("القامشلي", "Qamishli", 37.0522, 41.2272, false),
        new City("المالكية", "Al-Malikiyah", 37.1766, 42.1386, false),
        new City("عامودا", "Amuda", 37.1042, 40.9300, false),
        new City("رأس العين", "Ras al-Ayn", 36.8503, 40.0706, false),
        new City("الشدادي", "Al-Shaddadi", 36.0566, 40.7302, false),
        new City("الدرباسية", "Al-Darbasiyah", 37.0711, 40.6511, false),
        // حلب
        new City("منبج", "Manbij", 36.5281, 37.9549, false),
        new City("الباب", "Al-Bab", 36.3705, 37.5157, false),
        new City("عفرين", "Afrin", 36.5119, 36.8692, false),
        new City("جرابلس", "Jarablus", 36.8175, 38.0115, false),
        new City("عين العرب", "Kobani", 36.8906, 38.3542, false),
        new City("السفيرة", "As-Safira", 36.0703, 37.3711, false),
        new City("مسكنة", "Maskanah", 35.9644, 38.0403, false),
        new City("أعزاز", "Azaz", 36.5862, 37.0463, false),
        // حمص
        new City("تدمر", "Palmyra", 34.5560, 38.2690, false),
        new City("السخنة", "Al-Sukhnah", 34.8869, 38.8733, false),
        new City("القريتين", "Al-Qaryatayn", 34.2306, 37.2394, false),
        new City("الرستن", "Al-Rastan", 34.9267, 36.7325, false),
        new City("تلكلخ", "Talkalakh", 34.6683, 36.2597, false),
        new City("القصير", "Al-Qusayr", 34.5089, 36.5797, false),
        // حماة
        new City("سلمية", "Salamiyah", 35.0113, 37.0532, false),
        new City("مصياف", "Masyaf", 35.0653, 36.3421, false),
        new City("محردة", "Mhardeh", 35.2497, 36.5733, false),
        new City("السقيلبية", "Al-Suqaylabiyah", 35.3697, 36.3836, false),
        // الساحل
        new City("جبلة", "Jableh", 35.3610, 35.9256, false),
        new City("بانياس", "Baniyas", 35.1824, 35.9487, false),
        new City("صافيتا", "Safita", 34.8203, 36.1172, false),
        // إدلب
        new City("معرة النعمان", "Maarat al-Numan", 35.6481, 36.6728, false),
        new City("أريحا", "Ariha", 35.8136, 36.6097, false),
        new City("جسر الشغور", "Jisr al-Shughur", 35.8128, 36.3197, false),
        new City("سراقب", "Saraqib", 35.8640, 36.8070, false),
        new City("خان شيخون", "Khan Shaykhun", 35.4419, 36.6503, false),
        // ريف دمشق
        new City("النبك", "Al-Nabk", 34.0236, 36.7286, false),
        new City("يبرود", "Yabroud", 33.9672, 36.6572, false),
        new City("الزبداني", "Zabadani", 33.7247, 36.1003, false),
        new City("قطنا", "Qatana", 33.4386, 36.0789, false),
        new City("الكسوة", "Al-Kiswah", 33.3586, 36.2414, false),
        // الجنوب
        new City("إزرع", "Izra", 32.8567, 36.2483, false),
        new City("نوى", "Nawa", 32.8889, 36.0431, false),
        new City("بصرى الشام", "Bosra", 32.5167, 36.4833, false),
        new City("شهبا", "Shahba", 32.8550, 36.6286, false),
    };

    /** المسافة بالكيلومتر بين نقطتين (هافرساين). */
    public static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * أقرب مدينة سورية للموقع، أو null إذا كان الموقع خارج سوريا (أبعد من MAX_KM عن كل المدن).
     * capitalsOnly: مراكز المحافظات فقط.
     */
    public static City nearest(double lat, double lon, boolean capitalsOnly) {
        City best = null;
        double bd = Double.MAX_VALUE;
        for (City c : CITIES) {
            if (capitalsOnly && !c.capital) continue;
            double d = km(lat, lon, c.lat, c.lon);
            if (d < bd) { bd = d; best = c; }
        }
        // «خارج سوريا» يُحسب على كل المدن، حتى مع مراكز المحافظات فقط
        double any = Double.MAX_VALUE;
        for (City c : CITIES) any = Math.min(any, km(lat, lon, c.lat, c.lon));
        return any <= MAX_KM ? best : null;
    }

    public static City byName(String ar) {
        if (ar == null) return null;
        for (City c : CITIES) if (c.ar.equals(ar)) return c;
        return null;
    }
}
