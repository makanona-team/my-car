package com.stradaos.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * My Car · كاشف لانشر المصنع: يقرأ كود لانشر المصنع وخدمات TW المنصّبة (ملفات APK مقروءة لأي تطبيق)،
 * يستخرج النصوص المتعلقة بالإنارة/الليل ورسائل النظام والخدمات، ويرفع ملفات الكود (dex + manifest)
 * لنفكّها عند المطوّر ونعرف كيف يصل أمر «الأضواء شغّالة» للانشر المصنع.
 */
final class FactoryScan {

    interface Progress { void on(String s); }

    static final class Result { String report = "", reportUrl, filesUrl, error = ""; }

    static final Pattern KEY = Pattern.compile("(?i)(ill|light|lamp|night|dim|backl|bright|headl|tw\\.|android\\.tw|twutil|carinfo|mcu|acc_|action|broadcast|day_?mode|theme|aidl|binder|service)");

    /** لانشرات المصنع (كل HOME غير برنامجنا) + كل حزم com.tw.* */
    static List<ApplicationInfo> targets(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> names = new LinkedHashSet<String>();
        try {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
                if (!c.getPackageName().equals(r.activityInfo.packageName)) names.add(r.activityInfo.packageName);
        } catch (Throwable ignored) { }
        try {
            for (ApplicationInfo a : pm.getInstalledApplications(0))
                if (a.packageName.startsWith("com.tw.") || a.packageName.startsWith("android.tw") || a.packageName.toLowerCase(Locale.US).contains("launcher") && !a.packageName.equals(c.getPackageName()))
                    names.add(a.packageName);
        } catch (Throwable ignored) { }
        List<ApplicationInfo> out = new ArrayList<ApplicationInfo>();
        for (String n : names) try { out.add(pm.getApplicationInfo(n, 0)); } catch (Throwable ignored) { }
        return out;
    }

    static Result run(Context c, Progress p) {
        Result res = new Result();
        StringBuilder rep = new StringBuilder("My Car factory scan · " + new java.util.Date() + "\n");
        rep.append("device: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append(" · ").append(android.os.Build.DISPLAY).append('\n');
        File zipF = new File(c.getCacheDir(), "factory-code.zip");
        long total = 0;
        try {
            ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(zipF));
            List<ApplicationInfo> ts = targets(c);
            rep.append("targets: ").append(ts.size()).append("\n");
            for (ApplicationInfo a : ts) {
                p.on(I18n.L("أقرأ ", "Reading ") + a.packageName);
                File apk = new File(a.sourceDir);
                rep.append("\n══ ").append(a.packageName).append(" · ").append(a.sourceDir).append(" · ").append(apk.length() / 1024).append(" KB")
                   .append((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? " · system" : "").append(" ══\n");
                ZipFile z;
                try { z = new ZipFile(apk); } catch (Throwable e) { rep.append("  cannot open: ").append(e).append('\n'); continue; }
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    boolean dex = n.matches("classes\\d*\\.dex"), man = n.equals("AndroidManifest.xml");
                    if (!dex && !man) continue;
                    byte[] data = readAll(z.getInputStream(e));
                    if (total + data.length < 60L * 1024 * 1024) {   // حد الرفع
                        zo.putNextEntry(new ZipEntry(a.packageName + "/" + n));
                        zo.write(data);
                        zo.closeEntry();
                        total += data.length;
                    }
                    Set<String> hits = new LinkedHashSet<String>();
                    for (String s : dex ? dexStrings(data) : axmlStrings(data))
                        if (s.length() >= 3 && s.length() < 160 && KEY.matcher(s).find()) hits.add(s);
                    rep.append("  [").append(n).append("] ").append(hits.size()).append(" hits\n");
                    int k = 0;
                    for (String s : hits) { rep.append("    ").append(s).append('\n'); if (++k >= (man ? 400 : 700)) { rep.append("    …\n"); break; } }
                }
                z.close();
            }
            zo.close();
        } catch (Throwable e) { res.error += "scan: " + e + "\n"; }
        // وجود عقد TW في /dev (للمعلومة فقط)
        try {
            File[] dev = new File("/dev").listFiles();
            if (dev != null) { rep.append("\n/dev tw*: "); for (File f : dev) if (f.getName().toLowerCase(Locale.US).startsWith("tw")) rep.append(f.getName()).append(f.canRead() ? "(r) " : " "); rep.append('\n'); }
        } catch (Throwable ignored) { }
        res.report = rep.toString();
        res.filesUrl = uploadFile(zipF, res, p);
        p.on(I18n.L("أرفع التقرير…", "Uploading the report…"));
        try { res.reportUrl = DiagActivity.postText(res.report + "\n\nfiles: " + res.filesUrl + "\n" + res.error); } catch (Throwable e) { res.error += "report: " + e + "\n"; }
        return res;
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toByteArray();
    }

    private static int u32(byte[] d, int o) { return (d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8 | (d[o + 2] & 0xFF) << 16 | (d[o + 3] & 0xFF) << 24; }
    private static int u16(byte[] d, int o) { return (d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8; }

    /** جدول نصوص ملف dex (string_ids). */
    static List<String> dexStrings(byte[] d) {
        List<String> out = new ArrayList<String>();
        try {
            int n = u32(d, 0x38), off = u32(d, 0x3C);
            for (int i = 0; i < n; i++) {
                int p = u32(d, off + i * 4);
                while ((d[p] & 0x80) != 0) p++;   // تخطي طول uleb128
                p++;
                int e = p;
                while (e < d.length && d[e] != 0) e++;
                out.add(new String(d, p, e - p, "UTF-8"));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** مجمع نصوص AndroidManifest الثنائي (أسماء الرسائل والخدمات والصلاحيات). */
    static List<String> axmlStrings(byte[] d) {
        List<String> out = new ArrayList<String>();
        try {
            int sp = 8;   // أول chunk بعد الترويسة
            int count = u32(d, sp + 8), flags = u32(d, sp + 16), strStart = u32(d, sp + 20);
            boolean utf8 = (flags & 0x100) != 0;
            int base = sp + strStart;
            for (int i = 0; i < count; i++) {
                int p = base + u32(d, sp + 28 + i * 4);
                if (utf8) {
                    p += (d[p] & 0x80) != 0 ? 2 : 1;
                    int len = d[p] & 0xFF; if ((len & 0x80) != 0) { len = ((len & 0x7F) << 8) | (d[p + 1] & 0xFF); p += 2; } else p += 1;
                    out.add(new String(d, p, len, "UTF-8"));
                } else {
                    int len = u16(d, p); p += 2;
                    if ((len & 0x8000) != 0) { len = ((len & 0x7FFF) << 16) | u16(d, p); p += 2; }
                    out.add(new String(d, p, len * 2, "UTF-16LE"));
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /**
     * رفع الملف على شكل أجزاء نصية (base64) على paste.rs — نفس الخدمة المجرَّبة لإرسال التقرير.
     * يعيد رابط «فهرس» فيه روابط الأجزاء بالترتيب.
     */
    static String uploadFile(File f, Result res, Progress p) {
        try {
            byte[] all = readAll(new FileInputStream(f));
            String b64 = android.util.Base64.encodeToString(all, android.util.Base64.NO_WRAP);
            int part = 700 * 1024, n = (b64.length() + part - 1) / part;
            StringBuilder idx = new StringBuilder("MYCAR-PARTS " + n + " " + all.length + "\n");
            for (int i = 0; i < n; i++) {
                p.on(I18n.L("أرفع ملفات الكود: الجزء ", "Uploading code: part ") + (i + 1) + " / " + n);
                String chunk = b64.substring(i * part, Math.min(b64.length(), (i + 1) * part));
                String u = null;
                for (int t = 0; t < 3 && (u == null || !u.startsWith("http")); t++) {
                    try { u = DiagActivity.postPaste(chunk); } catch (Throwable e) { res.error += "part " + i + ": " + e + "\n"; }
                }
                if (u == null || !u.startsWith("http")) return null;
                idx.append(u.trim()).append('\n');
            }
            return DiagActivity.postPaste(idx.toString());
        } catch (Throwable e) { res.error += "upload: " + e + "\n"; return null; }
    }

    static String multipart(String url, String field, String extra, File f) throws Exception {
        String b = "----mycar" + System.currentTimeMillis();
        HttpURLConnection c = Net.open(url, 60000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + b);
        c.setRequestProperty("User-Agent", "curl/8.0");
        c.setChunkedStreamingMode(64 * 1024);
        OutputStream os = c.getOutputStream();
        if (extra != null) {
            String[] kv = extra.split("=", 2);
            os.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + kv[0] + "\"\r\n\r\n" + kv[1] + "\r\n").getBytes("UTF-8"));
        }
        os.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + f.getName() + "\"\r\nContent-Type: application/zip\r\n\r\n").getBytes("UTF-8"));
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[65536]; int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        in.close();
        os.write(("\r\n--" + b + "--\r\n").getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        String body = is == null ? "" : new String(readAll(is), "UTF-8");
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + body.substring(0, Math.min(120, body.length())));
        return body;
    }
}
