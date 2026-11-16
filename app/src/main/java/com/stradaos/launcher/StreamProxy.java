package com.stradaos.launcher;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Locale;

/**
 * My Car · وسيط البث: يفتح رابط المحطة بأنفسنا (بشهادات TLS الحديثة المرفقة، وتتبّع التحويلات وقوائم pls/m3u)
 * ثم يقدّمه لمشغّل أندرويد على 127.0.0.1. مشغّل أندرويد 8.1 الأصلي يفشل مع كثير من روابط https الحديثة.
 */
final class StreamProxy {

    interface Progress { void on(long bytes); }

    static final class Opened {
        HttpURLConnection conn;
        InputStream in;
        String type = "audio/mpeg";
        String finalUrl;
        boolean hls;
    }

    private ServerSocket server;
    private volatile boolean closed;
    volatile long bytes;

    /** يفتح الرابط (حتى 6 تحويلات وقوائم تشغيل). يرمي استثناءً برسالة مفهومة عند الفشل. */
    static Opened open(String url, int timeoutMs) throws Exception {
        String u = url;
        for (int hop = 0; hop < 6; hop++) {
            String low = u.toLowerCase(Locale.US);
            if (low.contains(".m3u8")) { Opened o = new Opened(); o.hls = true; o.finalUrl = u; return o; }
            HttpURLConnection c = Net.open(u, timeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Icy-MetaData", "0");
            c.setRequestProperty("Accept", "*/*");
            int code;
            try { code = c.getResponseCode(); }
            catch (javax.net.ssl.SSLException e) { throw new Exception(I18n.L("خطأ أمان (TLS) مع المحطة", "TLS error with the station") + ": " + e.getMessage()); }
            catch (java.net.SocketTimeoutException e) { throw new Exception(I18n.L("المحطة لا ترد (انتهت المهلة)", "Station timed out")); }
            catch (java.net.UnknownHostException e) { throw new Exception(I18n.L("لا يوجد إنترنت أو اسم المحطة غير معروف", "No internet or unknown host")); }
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("HTTP " + code);
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code >= 400) { c.disconnect(); throw new Exception(I18n.L("المحطة رفضت الطلب", "Station refused") + " (HTTP " + code + ")"); }
            String ct = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.US);
            low = u.toLowerCase(Locale.US);
            if (ct.contains("mpegurl") && !ct.contains("audio/mpegurl") || ct.contains("vnd.apple")) { c.disconnect(); Opened o = new Opened(); o.hls = true; o.finalUrl = u; return o; }
            if (ct.contains("scpls") || ct.contains("mpegurl") || ct.contains("x-mpegurl") || low.endsWith(".pls") || low.endsWith(".m3u")) {
                String next = firstUrl(c.getInputStream());
                c.disconnect();
                if (next == null) throw new Exception(I18n.L("قائمة تشغيل فارغة", "Empty playlist"));
                if (next.toLowerCase(Locale.US).contains(".m3u8")) { Opened o = new Opened(); o.hls = true; o.finalUrl = next; return o; }
                u = next;
                continue;
            }
            Opened o = new Opened();
            o.conn = c;
            o.in = c.getInputStream();
            o.finalUrl = u;
            if (ct.startsWith("audio/") || ct.startsWith("application/ogg")) o.type = ct.split(";")[0].trim();
            else if (ct.contains("aac")) o.type = "audio/aac";
            return o;
        }
        throw new Exception(I18n.L("تحويلات كثيرة", "Too many redirects"));
    }

    private static String firstUrl(InputStream in) throws Exception {
        byte[] buf = new byte[16384];
        int n = 0, r;
        while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
        for (String line : new String(buf, 0, n, "UTF-8").split("[\\r\\n]+")) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (t.toLowerCase(Locale.US).startsWith("file") && eq > 0) t = t.substring(eq + 1).trim();
            if (t.startsWith("http://") || t.startsWith("https://")) return t;
        }
        return null;
    }

    /** يبدأ خادماً محلياً يقدّم البث المفتوح لمشغّل واحد؛ يعيد الرابط المحلي. */
    String serve(final Opened o, final Progress p) throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        new Thread(new Runnable() { public void run() {
            Socket s = null;
            try {
                s = server.accept();
                InputStream req = s.getInputStream();
                byte[] tmp = new byte[2048];
                req.read(tmp);   // نتجاهل طلب المشغّل: البث مفتوح أصلاً
                OutputStream out = s.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + o.type + "\r\nConnection: close\r\nCache-Control: no-cache\r\n\r\n").getBytes("US-ASCII"));
                byte[] buf = new byte[16384];
                int r;
                long last = 0;
                while (!closed && (r = o.in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    bytes += r;
                    if (p != null && bytes - last > 32768) { last = bytes; p.on(bytes); }
                }
            } catch (Throwable ignored) {
            } finally {
                try { if (s != null) s.close(); } catch (Throwable ignored) { }
                close();
            }
        }}, "radio-proxy").start();
        return "http://127.0.0.1:" + server.getLocalPort() + "/stream";
    }

    void close() {
        closed = true;
        try { if (server != null) server.close(); } catch (Throwable ignored) { }
    }

    static void closeQuietly(Opened o) {
        if (o == null) return;
        try { if (o.in != null) o.in.close(); } catch (Throwable ignored) { }
        try { if (o.conn != null) o.conn.disconnect(); } catch (Throwable ignored) { }
    }
}
