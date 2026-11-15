package com.stradaos.launcher;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * My Car · الاتصال بوصلة OBD2 (ELM327، مثل Vgate) عبر بلوتوث عادي (SPP) أو بلوتوث BLE أو واي فاي، وقراءة القيم الحية
 * وأكواد الأعطال. يعمل في خيط خاص به ويبقى متصلاً حتى بعد الخروج من الصفحة (لتنبيهات الحرارة والبطارية).
 */
public class Obd {

    // ── القيم المقروءة: id، الأمر، الاسم ──
    public static final String RPM = "rpm", SPEED = "speed", COOLANT = "coolant", LOAD = "load", IAT = "iat", THROTTLE = "throttle",
            VOLT = "volt", FUEL = "fuel", STFT = "stft", LTFT = "ltft", MAP = "map", OIL = "oil", MAF = "maf", ECU_V = "ecuv";

    public interface Listener {
        void onState(int state, String msg);
        void onValues(HashMap<String, Double> v);
    }

    public static final int OFF = 0, CONNECTING = 1, READY = 2, FAILED = 3;
    static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static Obd inst;
    public static synchronized Obd get(Context c) {
        if (inst == null) inst = new Obd(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    public final HashMap<String, Double> values = new HashMap<String, Double>();
    private volatile int state = OFF;
    private volatile String stateMsg = "";
    private volatile boolean want;
    private Thread worker;
    private Link link;
    private final boolean[] supported = new boolean[0x61];
    private boolean can;
    public volatile String adapterInfo = "", protocol = "";
    private final Object cmdLock = new Object();

    private Obd(Context c) { ctx = c; }

    public int state() { return state; }
    public String stateMsg() { return stateMsg; }

    public void add(Listener l) { synchronized (listeners) { if (!listeners.contains(l)) listeners.add(l); } }
    public void remove(Listener l) { synchronized (listeners) { listeners.remove(l); } }

    SharedPreferences prefs() { return ctx.getSharedPreferences("launcher", 0); }

    private void setState(final int st, final String msg) {
        state = st;
        stateMsg = msg == null ? "" : msg;
        ui.post(new Runnable() { public void run() {
            List<Listener> ls;
            synchronized (listeners) { ls = new ArrayList<Listener>(listeners); }
            for (Listener l : ls) try { l.onState(st, stateMsg); } catch (Throwable ignored) { }
        }});
    }

    private void pushValues() {
        final HashMap<String, Double> copy;
        synchronized (values) { copy = new HashMap<String, Double>(values); }
        ui.post(new Runnable() { public void run() {
            List<Listener> ls;
            synchronized (listeners) { ls = new ArrayList<Listener>(listeners); }
            for (Listener l : ls) try { l.onValues(copy); } catch (Throwable ignored) { }
            ObdAlerts.check(ctx, copy);
        }});
    }

    public boolean hasDevice() {
        SharedPreferences p = prefs();
        String t = p.getString("obd_type", "");
        return t.length() > 0 && (!"wifi".equals(t) || p.getString("obd_host", "").length() > 0) && (!"spp".equals(t) && !"ble".equals(t) || p.getString("obd_addr", "").length() > 0);
    }

    /** يتصل بالوصلة المحفوظة ويبقى يحاول إعادة الاتصال إذا انقطع (حتى disconnect()). */
    public synchronized void connect() {
        if (!hasDevice()) { setState(FAILED, I18n.L("اختر وصلة OBD أولاً", "Choose an OBD adapter first")); return; }
        want = true;
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(new Runnable() { public void run() { loop(); } }, "MyCar-OBD");
        worker.start();
    }

    public synchronized void disconnect() {
        want = false;
        closeLink();
        if (worker != null) worker.interrupt();
        setState(OFF, "");
    }

    private void closeLink() {
        Link l = link;
        link = null;
        if (l != null) try { l.close(); } catch (Throwable ignored) { }
    }

    private void loop() {
        int fails = 0;
        while (want) {
            try {
                setState(CONNECTING, I18n.L("جارِ الاتصال بالوصلة…", "Connecting to the adapter…"));
                link = open();
                setState(CONNECTING, I18n.L("تهيئة ELM327…", "Initialising ELM327…"));
                init();
                fails = 0;
                prefs().edit().putBoolean("obd_ok_once", true).apply();
                setState(READY, adapterInfo);
                poll();
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                closeLink();
                if (!want) break;
                fails++;
                String m = e.getMessage() != null ? e.getMessage() : e.toString();
                Guard.log("OBD: " + m);
                setState(FAILED, m);
                try { Thread.sleep(fails < 3 ? 4000 : 15000); } catch (InterruptedException ie) { break; }
            }
        }
        closeLink();
        if (!want) setState(OFF, "");
    }

    // ───────────────────────── فتح الاتصال ─────────────────────────

    private Link open() throws Exception {
        SharedPreferences p = prefs();
        String type = p.getString("obd_type", "");
        if ("wifi".equals(type)) {
            String hp = p.getString("obd_host", "192.168.0.10:35000");
            String host = hp;
            int port = 35000;
            int c = hp.lastIndexOf(':');
            if (c > 0) { host = hp.substring(0, c); try { port = Integer.parseInt(hp.substring(c + 1).trim()); } catch (Throwable ignored) { } }
            return new TcpLink(host.trim(), port);
        }
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) throw new Exception(I18n.L("هذه الشاشة لا تدعم البلوتوث", "This screen has no Bluetooth"));
        if (!ad.isEnabled()) throw new Exception(I18n.L("البلوتوث مطفأ", "Bluetooth is off"));
        BluetoothDevice d = ad.getRemoteDevice(p.getString("obd_addr", ""));
        if ("ble".equals(type)) {
            if (android.os.Build.VERSION.SDK_INT < 18) throw new Exception("BLE: Android 4.3+");
            return new BleLink(ctx, d);
        }
        try { ad.cancelDiscovery(); } catch (Throwable ignored) { }
        try {
            return new SppLink(d);
        } catch (Exception e) {
            // وصلات «ثنائية» (عادي + BLE): إذا فشل العادي نجرّب BLE
            if (android.os.Build.VERSION.SDK_INT >= 18) {
                int t = BluetoothDevice.DEVICE_TYPE_CLASSIC;
                try { t = d.getType(); } catch (Throwable ignored) { }
                if (t == BluetoothDevice.DEVICE_TYPE_DUAL || t == BluetoothDevice.DEVICE_TYPE_LE) return new BleLink(ctx, d);
            }
            throw e;
        }
    }

    // ───────────────────────── أوامر ELM327 ─────────────────────────

    /** يرسل أمراً وينتظر «>» ويعيد الرد (بدون الصدى والمسافات الزائدة). */
    String cmd(String c, long timeoutMs) throws Exception {
        synchronized (cmdLock) {
            Link l = link;
            if (l == null) throw new Exception(I18n.L("انقطع الاتصال", "Disconnected"));
            l.drain();
            l.write(c + "\r");
            String r = l.readUntilPrompt(timeoutMs);
            r = r.replace("\r", "\n").replace(">", "");
            // حذف الصدى إن وُجد
            StringBuilder b = new StringBuilder();
            for (String line : r.split("\n")) {
                String t = line.trim();
                if (t.length() == 0 || t.equalsIgnoreCase(c) || t.startsWith("SEARCHING")) continue;
                if (b.length() > 0) b.append('\n');
                b.append(t);
            }
            return b.toString();
        }
    }

    private void init() throws Exception {
        String z = cmd("ATZ", 4000);
        Thread.sleep(400);
        cmd("ATE0", 2000);
        cmd("ATL0", 2000);
        cmd("ATS0", 2000);
        cmd("ATH0", 2000);
        try { cmd("ATAT1", 2000); } catch (Throwable ignored) { }
        String ver = "";
        try { ver = cmd("ATI", 2000); } catch (Throwable ignored) { }
        adapterInfo = (ver.length() > 0 ? ver : z).replace("\n", " ").trim();
        cmd("ATSP0", 2000);
        // أول طلب: البحث عن بروتوكول السيارة (قد يأخذ حتى 10 ثوانٍ)
        String r = cmd("0100", 12000);
        if (r.contains("UNABLE") || r.contains("NO DATA") || r.contains("ERROR") || r.contains("STOPPED")) {
            String again = cmd("0100", 12000);
            if (!again.replace(" ", "").contains("4100"))
                throw new Exception(I18n.L("الوصلة تعمل لكن السيارة لا تستجيب. شغّل الإشعال (أو المحرك) وحاول ثانية", "Adapter OK but the car does not answer. Turn the ignition (or engine) on and retry"));
            r = again;
        }
        for (int i = 0; i < supported.length; i++) supported[i] = false;
        readSupported(r, 0x00);
        try { if (supported[0x20]) readSupported(cmd("0120", 4000), 0x20); } catch (Throwable ignored) { }
        try { if (supported[0x40]) readSupported(cmd("0140", 4000), 0x40); } catch (Throwable ignored) { }
        try {
            String dp = cmd("ATDPN", 2000).replace("A", "").trim();
            protocol = cmd("ATDP", 2000).replace("AUTO,", "").trim();
            can = dp.length() > 0 && "6789".indexOf(dp.charAt(dp.length() - 1)) >= 0;
        } catch (Throwable ignored) { }
    }

    private void readSupported(String r, int base) {
        byte[] d = data(r, base == 0 ? "4100" : base == 0x20 ? "4120" : "4140");
        if (d == null || d.length < 4) return;
        for (int i = 0; i < 32; i++) {
            boolean on = ((d[i / 8] >> (7 - i % 8)) & 1) == 1;
            int pid = base + i + 1;
            if (pid < supported.length) supported[pid] = on;
        }
    }

    /** يستخرج البايتات بعد بادئة مثل «410C» من الرد (يتجاهل المسافات والأسطر الأخرى). */
    static byte[] data(String resp, String prefix) {
        if (resp == null) return null;
        for (String line : resp.split("\n")) {
            String h = line.replace(" ", "").toUpperCase(Locale.US);
            int c = h.indexOf(':');
            if (c >= 0 && c <= 2) h = h.substring(c + 1);   // أسطر CAN المتعددة «0:»
            int i = h.indexOf(prefix);
            if (i < 0) continue;
            String hex = h.substring(i + prefix.length());
            int n = hex.length() / 2;
            byte[] out = new byte[n];
            try {
                for (int k = 0; k < n; k++) out[k] = (byte) Integer.parseInt(hex.substring(k * 2, k * 2 + 2), 16);
            } catch (NumberFormatException e) { continue; }
            return out;
        }
        return null;
    }

    private static int u(byte b) { return b & 0xFF; }

    /** قراءة PID واحد؛ null إذا لم يُدعم أو لم يرد. */
    private Double pid(int pid) throws Exception {
        if (pid < supported.length && !supported[pid]) return null;
        String code = String.format(Locale.US, "%02X", pid);
        String r = cmd("01" + code, 2500);
        byte[] d = data(r, "41" + code);
        if (d == null || d.length == 0) return null;
        int a = u(d[0]), b = d.length > 1 ? u(d[1]) : 0;
        switch (pid) {
            case 0x0C: return d.length > 1 ? (a * 256 + b) / 4.0 : null;
            case 0x0D: return (double) a;
            case 0x05: case 0x0F: case 0x5C: return a - 40.0;
            case 0x04: case 0x11: case 0x2F: return a * 100.0 / 255.0;
            case 0x06: case 0x07: return (a - 128) * 100.0 / 128.0;
            case 0x0B: return (double) a;
            case 0x10: return d.length > 1 ? (a * 256 + b) / 100.0 : null;
            case 0x42: return d.length > 1 ? (a * 256 + b) / 1000.0 : null;
            default: return null;
        }
    }

    private void put(String k, Double v) {
        synchronized (values) { if (v == null) values.remove(k); else values.put(k, v); }
    }

    private void poll() throws Exception {
        int round = 0;
        while (want && link != null) {
            // سريعة: كل دورة
            put(RPM, pid(0x0C));
            put(SPEED, pid(0x0D));
            put(LOAD, pid(0x04));
            put(THROTTLE, pid(0x11));
            pushValues();
            // أبطأ: كل 4 دورات
            if (round % 4 == 0) {
                put(COOLANT, pid(0x05));
                put(IAT, pid(0x0F));
                put(MAP, pid(0x0B));
                put(MAF, pid(0x10));
                put(STFT, pid(0x06));
                put(LTFT, pid(0x07));
                try {
                    String v = cmd("ATRV", 2000).replace("V", "").replace("v", "").trim();
                    put(VOLT, Double.parseDouble(v));
                } catch (Throwable e) { put(VOLT, pid(0x42)); }
                pushValues();
            }
            if (round % 15 == 0) {
                put(FUEL, pid(0x2F));
                put(OIL, pid(0x5C));
                pushValues();
            }
            round++;
            Thread.sleep(150);
        }
    }

    // ───────────────────────── أكواد الأعطال ─────────────────────────

    /** يقرأ الأكواد المخزنة (03) والمعلّقة (07). يعمل فقط والاتصال جاهز. */
    public List<String> readCodes(boolean pending) throws Exception {
        if (state != READY) throw new Exception(I18n.L("غير متصل", "Not connected"));
        String r = cmd(pending ? "07" : "03", 6000);
        return parseDtc(r, pending ? "47" : "43", can);
    }

    public boolean clearCodes() throws Exception {
        if (state != READY) throw new Exception(I18n.L("غير متصل", "Not connected"));
        String r = cmd("04", 6000);
        return r.replace(" ", "").contains("44");
    }

    static List<String> parseDtc(String resp, String mode, boolean can) {
        List<String> out = new ArrayList<String>();
        if (resp == null) return out;
        StringBuilder hex = new StringBuilder();
        List<String> lines = new ArrayList<String>();
        for (String line : resp.split("\n")) {
            String h = line.replace(" ", "").toUpperCase(Locale.US);
            int c = h.indexOf(':');
            if (c >= 0 && c <= 2) h = h.substring(c + 1);
            else if (h.length() <= 3 && h.matches("[0-9A-F]+")) continue;   // سطر الطول في رسائل CAN الطويلة
            if (!h.matches("[0-9A-F]+")) continue;
            lines.add(h);
            hex.append(h);
        }
        if (can) {
            String all = hex.toString();
            int i = all.indexOf(mode);
            if (i < 0) return out;
            all = all.substring(i + 2);
            if (all.length() < 2) return out;
            int n;
            try { n = Integer.parseInt(all.substring(0, 2), 16); } catch (Throwable e) { n = 0; }
            all = all.substring(2);
            for (int k = 0; k < n && (k + 1) * 4 <= all.length(); k++) addCode(out, all.substring(k * 4, k * 4 + 4));
        } else {
            for (String h : lines) {
                int i = h.indexOf(mode);
                if (i < 0) continue;
                String d = h.substring(i + 2);
                for (int k = 0; k + 4 <= d.length() && k < 12; k += 4) addCode(out, d.substring(k, k + 4));
            }
        }
        return out;
    }

    private static void addCode(List<String> out, String four) {
        if ("0000".equals(four)) return;
        int a = Integer.parseInt(four.substring(0, 1), 16);
        char sys = "PCBU".charAt(a >> 2);
        String code = sys + Integer.toString(a & 3) + four.substring(1);
        if (!out.contains(code)) out.add(code);
    }

    // ───────────────────────── أنواع الاتصال ─────────────────────────

    abstract static class Link {
        abstract void write(String s) throws Exception;
        /** يقرأ حتى «>» أو انتهاء المهلة. */
        abstract String readUntilPrompt(long timeoutMs) throws Exception;
        abstract void drain();
        abstract void close();
    }

    /** بلوتوث عادي (SPP) وواي فاي: تيار بايتات. */
    abstract static class StreamLink extends Link {
        InputStream in;
        OutputStream out;

        void write(String s) throws Exception { out.write(s.getBytes("US-ASCII")); out.flush(); }

        void drain() {
            try { while (in.available() > 0) in.read(); } catch (Throwable ignored) { }
        }

        String readUntilPrompt(long timeoutMs) throws Exception {
            StringBuilder b = new StringBuilder();
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                if (in.available() > 0) {
                    int c = in.read();
                    if (c < 0) throw new Exception(I18n.L("انقطع الاتصال", "Disconnected"));
                    if (c == '>') return b.toString();
                    if (c != 0) b.append((char) c);
                } else {
                    Thread.sleep(8);
                }
            }
            if (b.length() == 0) throw new Exception(I18n.L("الوصلة لا ترد", "Adapter not responding"));
            return b.toString();
        }
    }

    static class SppLink extends StreamLink {
        BluetoothSocket sock;

        SppLink(BluetoothDevice d) throws Exception {
            Exception last = null;
            for (int attempt = 0; attempt < 3 && sock == null; attempt++) {
                BluetoothSocket s = null;
                try {
                    if (attempt == 0) s = d.createRfcommSocketToServiceRecord(SPP);
                    else if (attempt == 1) s = d.createInsecureRfcommSocketToServiceRecord(SPP);
                    else s = (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", int.class).invoke(d, 1);
                    s.connect();
                    sock = s;
                } catch (Exception e) {
                    last = e;
                    try { if (s != null) s.close(); } catch (Throwable ignored) { }
                }
            }
            if (sock == null) throw new Exception(I18n.L("تعذّر الاتصال بالوصلة عبر البلوتوث. تأكد أنها مقترنة وأن السيارة في وضع التشغيل", "Could not connect over Bluetooth. Make sure it is paired and the ignition is on")
                    + (last != null ? " (" + last.getMessage() + ")" : ""));
            in = sock.getInputStream();
            out = sock.getOutputStream();
        }

        void close() { try { sock.close(); } catch (Throwable ignored) { } }
    }

    static class TcpLink extends StreamLink {
        Socket sock;

        TcpLink(String host, int port) throws Exception {
            sock = new Socket();
            try {
                sock.connect(new InetSocketAddress(host, port), 6000);
            } catch (Exception e) {
                throw new Exception(I18n.L("تعذّر الاتصال بالوصلة عبر الواي فاي ", "Could not connect over Wi-Fi ") + host + ":" + port
                        + I18n.L(". اتصل أولاً بشبكة الوصلة (مثل V-LINK) من إعدادات الواي فاي", ". First join the adapter's Wi-Fi network (e.g. V-LINK)"));
            }
            sock.setTcpNoDelay(true);
            in = sock.getInputStream();
            out = sock.getOutputStream();
        }

        void close() { try { sock.close(); } catch (Throwable ignored) { } }
    }

    /** بلوتوث BLE (مثل Vgate iCar Pro BLE): يبحث عن خاصية الكتابة وخاصية الإشعارات تلقائياً. */
    @TargetApi(18)
    static class BleLink extends Link {
        static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        /** خدمات معروفة لوصلات ELM327 BLE (تُفضَّل إن وُجدت). */
        static final String[] KNOWN = {"e7810a71", "0000fff0", "0000ffe0", "000018f0", "49535343"};

        BluetoothGatt gatt;
        BluetoothGattCharacteristic wr, nt;
        final StringBuilder buf = new StringBuilder();
        final Object lock = new Object();
        volatile int phase;   // 0 اتصال، 1 خدمات، 2 جاهز، -1 فشل
        volatile boolean writeDone = true;

        BleLink(Context c, BluetoothDevice d) throws Exception {
            BluetoothGattCallback cb = new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt g, int status, int ns) {
                    if (ns == BluetoothProfile.STATE_CONNECTED) { phase = 1; g.discoverServices(); }
                    else if (ns == BluetoothProfile.STATE_DISCONNECTED) { phase = -1; synchronized (lock) { lock.notifyAll(); } }
                }
                @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                    pick(g);
                    if (nt == null || wr == null) { phase = -1; synchronized (lock) { lock.notifyAll(); } return; }
                    g.setCharacteristicNotification(nt, true);
                    BluetoothGattDescriptor de = nt.getDescriptor(CCCD);
                    if (de != null) {
                        boolean ind = (nt.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0;
                        de.setValue(ind ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        g.writeDescriptor(de);
                    } else { phase = 2; synchronized (lock) { lock.notifyAll(); } }
                }
                @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor de, int status) {
                    phase = 2; synchronized (lock) { lock.notifyAll(); }
                }
                @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
                    byte[] v = ch.getValue();
                    if (v == null) return;
                    synchronized (lock) {
                        for (byte x : v) if (x != 0) buf.append((char) (x & 0xFF));
                        lock.notifyAll();
                    }
                }
                @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
                    synchronized (lock) { writeDone = true; lock.notifyAll(); }
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 23) gatt = d.connectGatt(c, false, cb, BluetoothDevice.TRANSPORT_LE);
            else gatt = d.connectGatt(c, false, cb);
            long end = System.currentTimeMillis() + 15000;
            synchronized (lock) {
                while (phase >= 0 && phase < 2 && System.currentTimeMillis() < end) lock.wait(500);
            }
            if (phase != 2) {
                close();
                throw new Exception(I18n.L("تعذّر الاتصال بوصلة BLE (لم توجد خدمة ELM327)", "Could not connect to the BLE adapter (no ELM327 service)"));
            }
        }

        void pick(BluetoothGatt g) {
            List<BluetoothGattService> ss = g.getServices();
            // المعروفة أولاً، ثم أي خدمة غير قياسية فيها كتابة وإشعار
            for (int pass = 0; pass < 2 && (wr == null || nt == null); pass++) {
                for (BluetoothGattService s : ss) {
                    String id = s.getUuid().toString().toLowerCase(Locale.US);
                    boolean known = false;
                    for (String k : KNOWN) if (id.startsWith(k)) known = true;
                    if (pass == 0 && !known) continue;
                    if (id.startsWith("00001800") || id.startsWith("00001801") || id.startsWith("0000180a") || id.startsWith("0000180f")) continue;
                    BluetoothGattCharacteristic w = null, n = null;
                    for (BluetoothGattCharacteristic ch : s.getCharacteristics()) {
                        int p = ch.getProperties();
                        if (n == null && (p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) n = ch;
                        if (w == null && (p & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) w = ch;
                    }
                    if (w != null && n != null) { wr = w; nt = n; return; }
                }
            }
        }

        void write(String s) throws Exception {
            byte[] all = s.getBytes("US-ASCII");
            boolean noResp = (wr.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
            for (int off = 0; off < all.length; off += 20) {
                int n = Math.min(20, all.length - off);
                byte[] part = new byte[n];
                System.arraycopy(all, off, part, 0, n);
                synchronized (lock) {
                    long end = System.currentTimeMillis() + 2000;
                    while (!writeDone && System.currentTimeMillis() < end) lock.wait(100);
                    writeDone = false;
                    wr.setValue(part);
                    wr.setWriteType(noResp ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    if (!gatt.writeCharacteristic(wr)) { writeDone = true; throw new Exception(I18n.L("تعذّرت الكتابة للوصلة", "Write to adapter failed")); }
                    if (noResp) writeDone = true;
                }
            }
        }

        void drain() { synchronized (lock) { buf.setLength(0); } }

        String readUntilPrompt(long timeoutMs) throws Exception {
            long end = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) {
                while (true) {
                    if (phase < 0) throw new Exception(I18n.L("انقطع الاتصال", "Disconnected"));
                    int i = buf.indexOf(">");
                    if (i >= 0) {
                        String r = buf.substring(0, i);
                        buf.delete(0, i + 1);
                        return r;
                    }
                    long left = end - System.currentTimeMillis();
                    if (left <= 0) break;
                    lock.wait(Math.min(left, 200));
                }
                if (buf.length() == 0) throw new Exception(I18n.L("الوصلة لا ترد", "Adapter not responding"));
                String r = buf.toString();
                buf.setLength(0);
                return r;
            }
        }

        void close() {
            try { gatt.disconnect(); } catch (Throwable ignored) { }
            try { gatt.close(); } catch (Throwable ignored) { }
        }
    }
}
