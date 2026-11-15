package com.stradaos.launcher;

import java.util.HashMap;

/** My Car · شرح أكواد الأعطال الشائعة (OBD2 القياسية) بالعربية والإنجليزية. */
public class DtcInfo {

    private static final HashMap<String, String[]> M = new HashMap<String, String[]>();

    private static void a(String code, String ar, String en) { M.put(code, new String[]{ar, en}); }

    static {
        a("P0100", "حساس تدفق الهواء MAF: خلل في الدائرة", "MAF sensor circuit malfunction");
        a("P0101", "حساس تدفق الهواء MAF: قراءة خارج المدى", "MAF sensor range/performance");
        a("P0102", "حساس تدفق الهواء MAF: إشارة منخفضة", "MAF sensor circuit low");
        a("P0103", "حساس تدفق الهواء MAF: إشارة مرتفعة", "MAF sensor circuit high");
        a("P0105", "حساس ضغط المنفولد MAP: خلل", "MAP sensor circuit malfunction");
        a("P0106", "حساس ضغط المنفولد MAP: قراءة خارج المدى", "MAP sensor range/performance");
        a("P0110", "حساس حرارة الهواء الداخل: خلل", "Intake air temperature sensor circuit");
        a("P0113", "حساس حرارة الهواء الداخل: إشارة مرتفعة", "Intake air temperature sensor high");
        a("P0115", "حساس حرارة ماء المحرك: خلل", "Engine coolant temperature sensor circuit");
        a("P0116", "حساس حرارة ماء المحرك: قراءة خارج المدى", "Coolant temperature sensor range/performance");
        a("P0117", "حساس حرارة ماء المحرك: إشارة منخفضة", "Coolant temperature sensor low");
        a("P0118", "حساس حرارة ماء المحرك: إشارة مرتفعة", "Coolant temperature sensor high");
        a("P0120", "حساس وضع البوابة (الثروتل): خلل", "Throttle position sensor circuit");
        a("P0121", "حساس وضع البوابة: قراءة خارج المدى", "Throttle position sensor range/performance");
        a("P0122", "حساس وضع البوابة: إشارة منخفضة", "Throttle position sensor low");
        a("P0123", "حساس وضع البوابة: إشارة مرتفعة", "Throttle position sensor high");
        a("P0125", "المحرك لا يصل لحرارة التشغيل بالوقت المطلوب", "Insufficient coolant temperature for closed loop");
        a("P0128", "حرارة المحرك منخفضة: غالباً ترموستات عالق مفتوح", "Coolant below thermostat temperature (thermostat stuck open)");
        a("P0130", "حساس الأكسجين الأول (قبل الدبة): خلل", "O2 sensor circuit (bank 1 sensor 1)");
        a("P0131", "حساس الأكسجين الأول: جهد منخفض", "O2 sensor low voltage (B1S1)");
        a("P0132", "حساس الأكسجين الأول: جهد مرتفع", "O2 sensor high voltage (B1S1)");
        a("P0133", "حساس الأكسجين الأول: استجابة بطيئة", "O2 sensor slow response (B1S1)");
        a("P0134", "حساس الأكسجين الأول: لا توجد إشارة", "O2 sensor no activity (B1S1)");
        a("P0135", "سخّان حساس الأكسجين الأول: خلل", "O2 sensor heater circuit (B1S1)");
        a("P0136", "حساس الأكسجين الثاني (بعد الدبة): خلل", "O2 sensor circuit (B1S2)");
        a("P0141", "سخّان حساس الأكسجين الثاني: خلل", "O2 sensor heater circuit (B1S2)");
        a("P0171", "خليط فقير (هواء زائد) · الجهة 1: تسريب هواء، بخاخات، طرمبة بنزين أو حساس MAF", "System too lean (bank 1)");
        a("P0172", "خليط غني (وقود زائد) · الجهة 1", "System too rich (bank 1)");
        a("P0174", "خليط فقير (هواء زائد) · الجهة 2", "System too lean (bank 2)");
        a("P0175", "خليط غني (وقود زائد) · الجهة 2", "System too rich (bank 2)");
        a("P0190", "حساس ضغط سكة الوقود: خلل", "Fuel rail pressure sensor circuit");
        a("P0191", "حساس ضغط سكة الوقود: قراءة خارج المدى", "Fuel rail pressure sensor range/performance");
        a("P0201", "دائرة البخاخ 1: خلل", "Injector circuit cylinder 1");
        a("P0202", "دائرة البخاخ 2: خلل", "Injector circuit cylinder 2");
        a("P0203", "دائرة البخاخ 3: خلل", "Injector circuit cylinder 3");
        a("P0204", "دائرة البخاخ 4: خلل", "Injector circuit cylinder 4");
        a("P0217", "ارتفاع حرارة المحرك", "Engine over-temperature condition");
        a("P0230", "دائرة طرمبة الوقود: خلل", "Fuel pump primary circuit");
        a("P0234", "ضغط التيربو زائد (Overboost)", "Turbo overboost condition");
        a("P0299", "ضغط التيربو ناقص (Underboost)", "Turbo underboost");
        a("P0300", "احتراق غير منتظم (تقطيع) في عدة سلندرات: بواجي، كويلات، بخاخات أو تسريب هواء", "Random/multiple cylinder misfire");
        a("P0301", "تقطيع (Misfire) في السلندر 1", "Cylinder 1 misfire");
        a("P0302", "تقطيع (Misfire) في السلندر 2", "Cylinder 2 misfire");
        a("P0303", "تقطيع (Misfire) في السلندر 3", "Cylinder 3 misfire");
        a("P0304", "تقطيع (Misfire) في السلندر 4", "Cylinder 4 misfire");
        a("P0305", "تقطيع (Misfire) في السلندر 5", "Cylinder 5 misfire");
        a("P0306", "تقطيع (Misfire) في السلندر 6", "Cylinder 6 misfire");
        a("P0325", "حساس الطرق (Knock): خلل", "Knock sensor circuit");
        a("P0335", "حساس الكرنك (Crankshaft): خلل", "Crankshaft position sensor circuit");
        a("P0340", "حساس الكامة (Camshaft): خلل", "Camshaft position sensor circuit");
        a("P0380", "شمعات التسخين (ديزل): خلل في الدائرة", "Glow plug circuit");
        a("P0400", "نظام إعادة تدوير العادم EGR: خلل في التدفق", "EGR flow malfunction");
        a("P0401", "نظام EGR: تدفق غير كافٍ (انسداد)", "EGR flow insufficient");
        a("P0402", "نظام EGR: تدفق زائد", "EGR flow excessive");
        a("P0420", "كفاءة الدبة (الكتلايزر) منخفضة · الجهة 1", "Catalyst efficiency below threshold (bank 1)");
        a("P0430", "كفاءة الدبة (الكتلايزر) منخفضة · الجهة 2", "Catalyst efficiency below threshold (bank 2)");
        a("P0440", "نظام أبخرة الوقود EVAP: خلل", "EVAP system malfunction");
        a("P0441", "نظام أبخرة الوقود EVAP: تدفق غير صحيح", "EVAP incorrect purge flow");
        a("P0442", "تسريب صغير في نظام EVAP (غالباً غطاء خزان الوقود)", "EVAP small leak (often the fuel cap)");
        a("P0455", "تسريب كبير في نظام EVAP (غطاء الخزان مفتوح أو تالف)", "EVAP large leak (fuel cap loose/damaged)");
        a("P0456", "تسريب صغير جداً في نظام EVAP", "EVAP very small leak");
        a("P0480", "مروحة التبريد 1: خلل في دائرة التحكم", "Cooling fan 1 control circuit");
        a("P0500", "حساس سرعة السيارة: خلل", "Vehicle speed sensor malfunction");
        a("P0505", "نظام التحكم بالتباطؤ (السلانسيه): خلل", "Idle air control system");
        a("P0506", "دورات التباطؤ أقل من المطلوب", "Idle RPM lower than expected");
        a("P0507", "دورات التباطؤ أعلى من المطلوب", "Idle RPM higher than expected");
        a("P0520", "حساس ضغط زيت المحرك: خلل", "Engine oil pressure sensor circuit");
        a("P0560", "جهد النظام (البطارية): خلل", "System voltage malfunction");
        a("P0562", "جهد النظام منخفض: افحص البطارية والدينامو", "System voltage low");
        a("P0563", "جهد النظام مرتفع: منظم الدينامو", "System voltage high");
        a("P0600", "خلل في اتصال كمبيوتر المحرك", "ECM serial communication link");
        a("P0601", "خطأ في ذاكرة كمبيوتر المحرك", "ECM memory checksum error");
        a("P0700", "عطل في ناقل الحركة (اقرأ كمبيوتر الجير)", "Transmission control system malfunction");
        a("P0705", "حساس وضع ذراع الجير: خلل", "Transmission range sensor circuit");
        a("P0715", "حساس سرعة دخول الجير: خلل", "Input/turbine speed sensor circuit");
        a("P0720", "حساس سرعة خروج الجير: خلل", "Output speed sensor circuit");
        a("P0730", "نسبة تبديل غير صحيحة في الجير", "Incorrect gear ratio");
        a("P0741", "قفل محول العزم (التوربين) لا يعمل جيداً", "Torque converter clutch performance");
        a("P0750", "سلونويد تبديل A: خلل", "Shift solenoid A malfunction");
        a("P0755", "سلونويد تبديل B: خلل", "Shift solenoid B malfunction");
        a("P2002", "فلتر جزيئات الديزل DPF: كفاءة منخفضة", "Diesel particulate filter efficiency below threshold");
        a("P2263", "أداء نظام التيربو: خلل", "Turbo boost system performance");
        a("P2279", "تسريب هواء في نظام السحب", "Intake air system leak");
        a("U0100", "فقدان الاتصال مع كمبيوتر المحرك", "Lost communication with ECM/PCM");
        a("U0101", "فقدان الاتصال مع كمبيوتر الجير", "Lost communication with TCM");
        a("U0121", "فقدان الاتصال مع وحدة ABS", "Lost communication with ABS module");
        a("U0140", "فقدان الاتصال مع وحدة الكهرباء (BCM)", "Lost communication with body control module");
        a("U0155", "فقدان الاتصال مع لوحة العدادات", "Lost communication with instrument cluster");
    }

    public static String describe(String code) {
        String[] d = M.get(code);
        if (d != null) return I18n.L(d[0], d[1]);
        if (code == null || code.length() < 5) return "";
        char sys = code.charAt(0);
        boolean maker = code.charAt(1) == '1' || code.charAt(1) == '3';
        String extra = maker ? I18n.L(" · كود خاص بالشركة المصنّعة", " · manufacturer-specific code") : "";
        if (sys == 'C') return I18n.L("الشاسيه: الفرامل ABS أو التعليق أو التوجيه", "Chassis: ABS brakes, suspension or steering") + extra;
        if (sys == 'B') return I18n.L("الهيكل: الوسائد الهوائية أو الكهرباء الداخلية أو المكيف", "Body: airbags, interior electrics or A/C") + extra;
        if (sys == 'U') return I18n.L("شبكة الاتصال بين وحدات السيارة", "Network communication between modules") + extra;
        char g = code.charAt(2);
        String area;
        switch (g) {
            case '0': case '1': case '2': area = I18n.L("المحرك: الوقود والهواء والبخاخات", "Engine: fuel, air and injectors"); break;
            case '3': area = I18n.L("المحرك: نظام الإشعال أو تقطيع", "Engine: ignition system or misfire"); break;
            case '4': area = I18n.L("أنظمة الانبعاثات (EGR، الدبة، EVAP)", "Emission controls (EGR, catalyst, EVAP)"); break;
            case '5': area = I18n.L("السرعة والتباطؤ والمدخلات", "Speed, idle control and inputs"); break;
            case '6': area = I18n.L("كمبيوتر المحرك ودوائره", "Engine computer and outputs"); break;
            case '7': case '8': case '9': area = I18n.L("ناقل الحركة (الجير)", "Transmission"); break;
            default: area = I18n.L("المحرك ونقل الحركة", "Powertrain");
        }
        return area + extra;
    }
}
