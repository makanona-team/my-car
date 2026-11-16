package com.stradaos.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** My Car · صفحة OBD2: اختيار الوصلة والاتصال، العدادات الحية، وأكواد الأعطال مع الشرح. */
public class ObdActivity extends Screen implements Obd.Listener {

    private Obd obd;
    private TextView devTv, stTv, infoTv;
    private LinearLayout connBtn;
    private TextView connTxt;
    private final Tile[] tiles = new Tile[8];
    private final Handler h = new Handler();

    @Override protected String title() { return I18n.L("OBD2 · حالة السيارة", "OBD2 · Car status"); }

    /** مربع قيمة: اسم، رقم كبير، وحدة، وشريط اختياري. */
    class Tile {
        LinearLayout box;
        TextView name, val, unit;
        View bar, barBg;
        float max;

        Tile(String n, String u, float max, String ic) {
            this.max = max;
            box = new LinearLayout(ObdActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(round(Main.CARD, 16));
            box.setPadding(px(14), px(10), px(14), px(10));
            LinearLayout hd = hbox();
            hd.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout ib = new FrameLayout(ObdActivity.this);
            ib.setBackground(round(0x33FF3B30, 14));
            iconV = icon(ic, 0xFFFF6B5A);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(px(20), px(20), Gravity.CENTER);
            ib.addView(iconV, il);
            hd.addView(ib, lp(px(30), px(30)));
            name = tv(n, 13, Main.MUTED, false);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
            marginStart(nl, px(8));
            hd.addView(name, nl);
            box.addView(hd);
            LinearLayout vr = new LinearLayout(ObdActivity.this);
            vr.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            if (android.os.Build.VERSION.SDK_INT >= 17) vr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            val = tv("--", 38, Main.TEXT, true);
            unit = tv(u, 14, Main.MUTED, false);
            unit.setPadding(px(6), 0, 0, px(5));
            vr.addView(val);
            vr.addView(unit);
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1, 0, 1);
            box.addView(vr, vl);
            FrameLayout fb = new FrameLayout(ObdActivity.this);
            barBg = new View(ObdActivity.this);
            barBg.setBackground(round(Main.INNER, 4));
            fb.addView(barBg, new FrameLayout.LayoutParams(-1, -1));
            bar = new View(ObdActivity.this);
            bar.setBackground(round(Main.RED, 4));
            fb.addView(bar, new FrameLayout.LayoutParams(0, -1));
            box.addView(fb, lp(-1, px(8)));
            if (max <= 0) fb.setVisibility(View.INVISIBLE);
        }

        ImageView iconV;

        void set(Double v, String text, int color) {
            val.setText(v == null ? "--" : text);
            val.setTextColor(v == null ? Main.MUTED : color);
            if (bar != null) bar.setBackground(round(v == null ? Main.INNER : (color == Main.TEXT ? Main.RED : color), 4));
            if (iconV != null) iconV.setColorFilter(v == null ? Main.MUTED : (color == Main.TEXT ? 0xFFFF6B5A : color));
            if (bar != null) {
                float f = v == null ? 0 : (float) Math.max(0, Math.min(1, v / max));
                int w = barBg.getWidth();
                FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) bar.getLayoutParams();
                l.width = (int) (w * f);
                l.gravity = rtl() ? Gravity.RIGHT : Gravity.LEFT;
                bar.setLayoutParams(l);
                bar.setBackground(round(color == Main.TEXT ? Main.RED : color, 4));
            }
        }
    }

    @Override
    protected void build(FrameLayout content) {
        obd = Obd.get(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), 0, px(16), px(12));
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ── شريط الحالة ──
        LinearLayout st = hbox();
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setBackground(round(Main.CARD, 16));
        st.setPadding(px(14), px(8), px(10), px(8));
        st.addView(icon("obd", Main.RED), lp(px(32), px(32)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(px(12), 0, px(12), 0);
        devTv = tv("", 16, Main.TEXT, true);
        stTv = tv("", 12, Main.MUTED, false);
        tx.addView(devTv);
        tx.addView(stTv);
        st.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        st.addView(pill("bluetooth", I18n.L("الوصلة", "Adapter"), false, new View.OnClickListener() { public void onClick(View v) { chooseAdapter(); } }));
        st.addView(space(8));
        connBtn = pill("play_arrow", I18n.L("اتصال", "Connect"), true, new View.OnClickListener() { public void onClick(View v) { toggleConn(); } });
        connTxt = (TextView) connBtn.getTag();
        st.addView(connBtn);
        st.addView(space(8));
        st.addView(pill("car_repair", I18n.L("الأعطال", "Codes"), false, new View.OnClickListener() { public void onClick(View v) { showCodes(); } }));
        st.addView(space(8));
        st.addView(pill("settings", null, false, new View.OnClickListener() { public void onClick(View v) { settings(); } }));
        col.addView(st, lp(-1, px(64)));

        // ── العدادات ──
        tiles[0] = new Tile(I18n.L("دورات المحرك", "Engine RPM"), "rpm", 7000, "speed");
        tiles[1] = new Tile(I18n.L("السرعة", "Speed"), "km/h", 200, "directions_car");
        tiles[2] = new Tile(I18n.L("حرارة المحرك (الماء)", "Coolant temp"), "°C", 130, "thermostat");
        tiles[3] = new Tile(I18n.L("جهد البطارية", "Battery voltage"), "V", 0, "battery_charging_full");
        tiles[4] = new Tile(I18n.L("حمل المحرك", "Engine load"), "%", 100, "electric_bolt");
        tiles[5] = new Tile(I18n.L("فتحة البوابة", "Throttle"), "%", 100, "tune");
        tiles[6] = new Tile(I18n.L("حرارة الهواء الداخل", "Intake air temp"), "°C", 0, "air");
        tiles[7] = new Tile(I18n.L("تصحيح الوقود قصير/طويل", "Fuel trim short/long"), "%", 0, "local_gas_station");
        for (int r = 0; r < 2; r++) {
            LinearLayout row = hbox();
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, 0, 1);
            rl.topMargin = px(10);
            col.addView(row, rl);
            for (int c = 0; c < 4; c++) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -1, 1);
                if (c > 0) marginStart(tl, px(10));
                row.addView(tiles[r * 4 + c].box, tl);
            }
        }
        infoTv = tv("", 11, Main.MUTED, false);
        infoTv.setPadding(0, px(6), 0, 0);
        col.addView(infoTv, lp(-1, -2));
    }

    private View space(int w) { View v = new View(this); v.setLayoutParams(lp(px(w), 1)); return v; }

    @Override
    protected void onResume() {
        super.onResume();
        obd.add(this);
        onState(obd.state(), obd.stateMsg());
        onValues(new HashMap<String, Double>(obd.values));
        // أول مرة: إن كانت الوصلة محفوظة يتصل وحده
        if (obd.state() == Obd.OFF && obd.hasDevice() && prefs.getBoolean("obd_auto", true)) obd.connect();
        if (!obd.hasDevice()) h.postDelayed(new Runnable() { public void run() { chooseAdapter(); } }, 400);
    }

    @Override
    protected void onPause() {
        super.onPause();
        obd.remove(this);
        h.removeCallbacksAndMessages(null);
    }

    // ───────────────────────── الحالة والقيم ─────────────────────────

    public void onState(int st, String msg) {
        String name = prefs.getString("obd_name", "");
        String type = prefs.getString("obd_type", "");
        devTv.setText(name.length() > 0 ? name + "  ·  " + typeName(type) : I18n.L("لم تُختر وصلة بعد", "No adapter chosen yet"));
        String s;
        int col = Main.MUTED;
        switch (st) {
            case Obd.CONNECTING: s = msg; break;
            case Obd.READY:
                if (obd.carAnswers) { s = I18n.L("الوصلة ✓ · السيارة ✓", "Adapter ✓ · Car ✓") + (obd.protocol.length() > 0 ? " · " + obd.protocol : ""); col = 0xFF2ECC71; }
                else { s = I18n.L("الوصلة ✓ · السيارة لا ترد ✗ — شغّل المحرك", "Adapter ✓ · Car not answering ✗ — start the engine"); col = 0xFFFFB020; }
                break;
            case Obd.FAILED: s = msg + I18n.L(" · إعادة المحاولة تلقائياً…", " · retrying…"); col = 0xFFFF6B5A; break;
            default: s = I18n.L("غير متصل", "Not connected");
        }
        stTv.setText(s);
        stTv.setTextColor(col);
        boolean on = st != Obd.OFF;
        connTxt.setText(on ? I18n.L("قطع", "Disconnect") : I18n.L("اتصال", "Connect"));
        infoTv.setText(obd.adapterInfo.length() > 0 ? I18n.L("الوصلة: ", "Adapter: ") + obd.adapterInfo : I18n.L("شغّل إشعال السيارة (أو المحرك) قبل الاتصال. القيم غير المدعومة في سيارتك تظهر «--».", "Turn the ignition (or engine) on before connecting. Values your car doesn't support show «--»."));
    }

    public void onValues(HashMap<String, Double> v) {
        Double rpm = v.get(Obd.RPM), sp = v.get(Obd.SPEED), t = v.get(Obd.COOLANT), vo = v.get(Obd.VOLT), ld = v.get(Obd.LOAD),
                th = v.get(Obd.THROTTLE), iat = v.get(Obd.IAT), oil = v.get(Obd.OIL), st = v.get(Obd.STFT), lt = v.get(Obd.LTFT), fuel = v.get(Obd.FUEL);
        tiles[0].set(rpm, rpm == null ? "" : String.valueOf(Math.round(rpm)), rpm != null && rpm > 5000 ? 0xFFFF6B5A : Main.TEXT);
        tiles[1].set(sp, sp == null ? "" : String.valueOf(Math.round(sp)), Main.TEXT);
        int hot = ObdAlerts.hotLimit(prefs);
        tiles[2].set(t, t == null ? "" : String.valueOf(Math.round(t)), t == null ? Main.TEXT : t >= hot ? 0xFFFF4D4D : t >= hot - 8 ? 0xFFFFB020 : t < 60 ? 0xFF5DADE2 : 0xFF2ECC71);
        boolean running = rpm != null && rpm > 500;
        int vc = Main.TEXT;
        if (vo != null) vc = running ? (vo < 12.7 || vo > 15.2 ? 0xFFFF4D4D : 0xFF2ECC71) : (vo < 11.9 ? 0xFFFF4D4D : vo < 12.3 ? 0xFFFFB020 : 0xFF2ECC71);
        tiles[3].set(vo, vo == null ? "" : String.format(Locale.US, "%.1f", vo), vc);
        tiles[4].set(ld, ld == null ? "" : String.valueOf(Math.round(ld)), Main.TEXT);
        tiles[5].set(th, th == null ? "" : String.valueOf(Math.round(th)), Main.TEXT);
        if (oil != null) {
            tiles[6].name.setText(I18n.L("حرارة الزيت", "Oil temp"));
            tiles[6].set(oil, String.valueOf(Math.round(oil)), oil > 130 ? 0xFFFF4D4D : Main.TEXT);
        } else {
            tiles[6].set(iat, iat == null ? "" : String.valueOf(Math.round(iat)), Main.TEXT);
        }
        if (fuel != null) {
            tiles[7].name.setText(I18n.L("مستوى الوقود", "Fuel level"));
            tiles[7].set(fuel, String.valueOf(Math.round(fuel)), fuel < 12 ? 0xFFFFB020 : Main.TEXT);
        } else {
            String txt = (st == null ? "--" : String.format(Locale.US, "%+.0f", st)) + " / " + (lt == null ? "--" : String.format(Locale.US, "%+.0f", lt));
            tiles[7].set(st != null || lt != null ? 0.0 : null, txt, Main.TEXT);
        }
    }

    private static String typeName(String t) {
        if ("ble".equals(t)) return "Bluetooth BLE";
        if ("wifi".equals(t)) return "Wi-Fi";
        if ("spp".equals(t)) return I18n.L("بلوتوث", "Bluetooth");
        return "";
    }

    private void toggleConn() {
        if (obd.state() != Obd.OFF) { obd.disconnect(); return; }
        if (!obd.hasDevice()) { chooseAdapter(); return; }
        obd.connect();
    }

    // ───────────────────────── اختيار الوصلة ─────────────────────────

    private void chooseAdapter() {
        final List<String[]> opts = new ArrayList<String[]>();   // type, addr/host, name
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad != null && ad.isEnabled()) {
                Set<BluetoothDevice> bonded = ad.getBondedDevices();
                List<BluetoothDevice> l = new ArrayList<BluetoothDevice>(bonded);
                Collections.sort(l, new Comparator<BluetoothDevice>() {
                    public int compare(BluetoothDevice a, BluetoothDevice b) { return (looksObd(b) ? 1 : 0) - (looksObd(a) ? 1 : 0); }
                });
                for (BluetoothDevice d : l) {
                    String n = d.getName() != null ? d.getName() : d.getAddress();
                    String type = "spp";
                    if (android.os.Build.VERSION.SDK_INT >= 18) try { if (d.getType() == BluetoothDevice.DEVICE_TYPE_LE) type = "ble"; } catch (Throwable ignored) { }
                    opts.add(new String[]{type, d.getAddress(), n});
                }
            }
        } catch (Throwable ignored) { }
        final List<String> labels = new ArrayList<String>();
        for (String[] o : opts) labels.add((looksObdName(o[2]) ? "★ " : "") + o[2] + "   ·   " + typeName(o[0]));
        final int scanIdx = labels.size();
        labels.add(I18n.L("🔍 بحث عن وصلات بلوتوث BLE قريبة…", "🔍 Scan for nearby BLE adapters…"));
        final int wifiIdx = labels.size();
        labels.add(I18n.L("📶 وصلة واي فاي (مثل V-LINK) · ", "📶 Wi-Fi adapter (e.g. V-LINK) · ") + prefs.getString("obd_host", "192.168.0.10:35000"));
        final int helpIdx = labels.size();
        labels.add(I18n.L("❓ وصلتي لا تظهر في القائمة", "❓ My adapter is not listed"));
        choose(I18n.L("اختر وصلة OBD2", "Choose the OBD2 adapter"), labels.toArray(new String[0]), -1, new IntCb() { public void on(int w) {
            if (w < scanIdx) save(opts.get(w)[0], opts.get(w)[1], opts.get(w)[2]);
            else if (w == scanIdx) scanBle();
            else if (w == wifiIdx) askWifi();
            else if (w == helpIdx) help();
        }});
    }

    static boolean looksObd(BluetoothDevice d) { return looksObdName(d.getName()); }

    static boolean looksObdName(String n) {
        if (n == null) return false;
        String s = n.toLowerCase(Locale.US);
        return s.contains("obd") || s.contains("elm") || s.contains("vgate") || s.contains("vlink") || s.contains("v-link") || s.contains("icar")
                || s.contains("vlinker") || s.contains("konnwei") || s.contains("veepeak") || s.contains("carista") || s.contains("ios-vlink") || s.contains("android-vlink");
    }

    private void save(String type, String addr, String name) {
        obd.disconnect();
        prefs.edit().putString("obd_type", type).putString("obd_addr", addr).putString("obd_name", name).apply();
        onState(obd.state(), "");
        obd.connect();
    }

    private void scanBle() {
        if (android.os.Build.VERSION.SDK_INT < 18) { toast("BLE: Android 4.3+"); return; }
        final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null || !ad.isEnabled()) { toast(I18n.L("شغّل البلوتوث أولاً", "Turn Bluetooth on first")); return; }
        final List<BluetoothDevice> found = new ArrayList<BluetoothDevice>();
        final BluetoothAdapter.LeScanCallback cb = new BluetoothAdapter.LeScanCallback() {
            public void onLeScan(final BluetoothDevice d, int rssi, byte[] rec) {
                runOnUiThread(new Runnable() { public void run() {
                    for (BluetoothDevice x : found) if (x.getAddress().equals(d.getAddress())) return;
                    found.add(d);
                }});
            }
        };
        toast(I18n.L("جارِ البحث 8 ثوانٍ… (السيارة في وضع التشغيل)", "Scanning for 8 seconds… (ignition on)"));
        try { ad.startLeScan(cb); } catch (Throwable e) { toast(String.valueOf(e.getMessage())); return; }
        h.postDelayed(new Runnable() { public void run() {
            try { ad.stopLeScan(cb); } catch (Throwable ignored) { }
            if (found.isEmpty()) { toast(I18n.L("لم يُعثر على أي جهاز BLE", "No BLE devices found")); return; }
            Collections.sort(found, new Comparator<BluetoothDevice>() {
                public int compare(BluetoothDevice a, BluetoothDevice b) { return (looksObd(b) ? 1 : 0) - (looksObd(a) ? 1 : 0); }
            });
            String[] names = new String[found.size()];
            for (int i = 0; i < found.size(); i++) {
                String n = found.get(i).getName();
                names[i] = (looksObdName(n) ? "★ " : "") + (n != null ? n : I18n.L("بدون اسم", "No name")) + "   ·   " + found.get(i).getAddress();
            }
            choose(I18n.L("أجهزة BLE القريبة", "Nearby BLE devices"), names, -1, new IntCb() { public void on(int w) {
                BluetoothDevice d = found.get(w);
                save("ble", d.getAddress(), d.getName() != null ? d.getName() : d.getAddress());
            }});
        }}, 8000);
    }

    private void askWifi() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setText(prefs.getString("obd_host", "192.168.0.10:35000"));
        et.setTextColor(Main.TEXT);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(px(20), px(10), px(20), 0);
        wrap.addView(et);
        new TB(this, Main.dlg()).raw()
            .setTitle(I18n.L("عنوان وصلة الواي فاي (IP:منفذ)", "Wi-Fi adapter address (IP:port)"))
            .setView(wrap)
            .setPositiveButton(I18n.L("حفظ واتصال", "Save & connect"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String hp = et.getText().toString().trim();
                if (hp.length() == 0) hp = "192.168.0.10:35000";
                prefs.edit().putString("obd_host", hp).apply();
                save("wifi", "", "Wi-Fi " + hp);
            }})
            .setNegativeButton(I18n.L("إلغاء", "Cancel"), null)
            .show();
    }

    private void help() {
        new TB(this, Main.dlg()).raw()
            .setTitle(I18n.L("ربط وصلة OBD2", "Connecting an OBD2 adapter"))
            .setMessage(I18n.L(
                "• ركّب الوصلة في منفذ OBD تحت المقود وشغّل الإشعال.\n"
                + "• وصلة بلوتوث عادي: اقرنها أولاً من إعدادات البلوتوث في الشاشة (الرمز غالباً 1234 أو 0000)، ثم ارجع واخترها هنا.\n"
                + "• وصلة BLE (مثل Vgate iCar Pro BLE): لا تحتاج اقتران، اختر «بحث عن وصلات BLE».\n"
                + "• وصلة واي فاي: اتصل بشبكتها من إعدادات الواي فاي (مثل V-LINK)، ثم اختر «وصلة واي فاي».\n"
                + "• إذا كانت الوصلة مقترنة مع هاتف، افصلها عنه أولاً: الوصلة تقبل جهازاً واحداً.",
                "• Plug the adapter into the OBD port under the steering wheel and turn the ignition on.\n"
                + "• Classic Bluetooth adapter: pair it first in the screen's Bluetooth settings (PIN usually 1234 or 0000), then pick it here.\n"
                + "• BLE adapter (e.g. Vgate iCar Pro BLE): no pairing needed, use «Scan for BLE adapters».\n"
                + "• Wi-Fi adapter: join its network in Wi-Fi settings (e.g. V-LINK), then pick «Wi-Fi adapter».\n"
                + "• If the adapter is connected to a phone, disconnect it first: it accepts one device at a time."))
            .setPositiveButton(I18n.L("إعدادات البلوتوث", "Bluetooth settings"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (Throwable ignored) { }
            }})
            .setNegativeButton(I18n.L("إغلاق", "Close"), null)
            .show();
    }

    // ───────────────────────── الأعطال ─────────────────────────

    private void showCodes() {
        if (obd.state() != Obd.READY) { toast(I18n.L("اتصل بالوصلة أولاً", "Connect to the adapter first")); return; }
        toast(I18n.L("جارِ قراءة أكواد الأعطال…", "Reading trouble codes…"));
        new Thread(new Runnable() { public void run() {
            List<String> stored = null, pending = null;
            String err = null;
            try { stored = obd.readCodes(false); } catch (Throwable e) { err = e.getMessage(); }
            try { pending = obd.readCodes(true); } catch (Throwable ignored) { }
            final List<String> fs = stored, fp = pending;
            final String fe = err;
            runOnUiThread(new Runnable() { public void run() { codesDialog(fs, fp, fe); } });
        }}).start();
    }

    private void codesDialog(List<String> stored, List<String> pending, String err) {
        StringBuilder b = new StringBuilder();
        if (err != null && stored == null) b.append(I18n.L("تعذّرت القراءة: ", "Could not read: ")).append(err);
        else {
            if (stored == null || stored.isEmpty()) b.append(I18n.L("✓ لا توجد أكواد أعطال مخزنة.", "✓ No stored trouble codes."));
            else {
                b.append(I18n.L("أكواد مخزنة (لمبة المحرك):\n", "Stored codes (check engine):\n"));
                for (String c : stored) b.append("• ").append(c).append("  —  ").append(DtcInfo.describe(c)).append('\n');
            }
            if (pending != null && !pending.isEmpty()) {
                b.append(I18n.L("\nأكواد معلّقة (ظهرت مرة ولم تتأكد بعد):\n", "\nPending codes (seen once, not confirmed yet):\n"));
                for (String c : pending) b.append("• ").append(c).append("  —  ").append(DtcInfo.describe(c)).append('\n');
            }
            b.append(I18n.L("\nالشرح عام؛ تأكد من التشخيص عند فني مختص.", "\nDescriptions are general; confirm with a mechanic."));
        }
        boolean any = stored != null && !stored.isEmpty() || pending != null && !pending.isEmpty();
        TB t = new TB(this, Main.dlg());
        t.raw().wide().setTitle(I18n.L("أكواد الأعطال", "Trouble codes")).setMessage(b.toString().trim());
        if (any) t.setNeutralButton(I18n.L("مسح الأكواد", "Clear codes"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { confirmClear(); } });
        t.setPositiveButton(I18n.L("قراءة مجدداً", "Read again"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { showCodes(); } });
        t.setNegativeButton(I18n.L("إغلاق", "Close"), null);
        t.show();
    }

    private void confirmClear() {
        new TB(this, Main.dlg()).raw()
            .setTitle(I18n.L("مسح أكواد الأعطال؟", "Clear trouble codes?"))
            .setMessage(I18n.L("يُطفئ لمبة فحص المحرك ويمسح بيانات الفحص الذاتي. افعل ذلك والمحرك متوقف والإشعال يعمل، وبعد إصلاح العطل. إذا بقي العطل سيعود الكود.",
                "Turns the check-engine light off and resets readiness data. Do it with the engine off and ignition on, after the fault is fixed. If the fault remains, the code will come back."))
            .setPositiveButton(I18n.L("مسح", "Clear"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                new Thread(new Runnable() { public void run() {
                    boolean ok = false;
                    try { ok = obd.clearCodes(); } catch (Throwable ignored) { }
                    final boolean fok = ok;
                    runOnUiThread(new Runnable() { public void run() {
                        toast(fok ? I18n.L("تم مسح الأكواد ✓", "Codes cleared ✓") : I18n.L("لم تقبل السيارة المسح (أطفئ المحرك وأبقِ الإشعال)", "The car refused (engine off, ignition on)"));
                    }});
                }}).start();
            }})
            .setNegativeButton(I18n.L("إلغاء", "Cancel"), null)
            .show();
    }

    // ───────────────────────── الإعدادات ─────────────────────────

    private void settings() {
        final boolean auto = prefs.getBoolean("obd_auto", true), alerts = prefs.getBoolean("obd_alerts", true);
        String[] items = {
            (auto ? "✓ " : "✗ ") + I18n.L("الاتصال تلقائياً عند تشغيل الشاشة", "Connect automatically when the screen starts"),
            (alerts ? "✓ " : "✗ ") + I18n.L("تنبيهات الحرارة والبطارية", "Temperature and battery alerts"),
            I18n.L("حد تنبيه الحرارة: ", "Temperature alert at: ") + ObdAlerts.hotLimit(prefs) + "°C",
            I18n.L("نسيان الوصلة المحفوظة", "Forget the saved adapter"),
            I18n.L("سجل الاتصال (صوّره وأرسله للمطوّر)", "Connection log (photo it for the developer)"),
            I18n.L("إعادة البحث عن البروتوكول", "Search the protocol again"),
        };
        choose(I18n.L("إعدادات OBD2", "OBD2 settings"), items, -1, new IntCb() { public void on(int w) {
            if (w == 0) prefs.edit().putBoolean("obd_auto", !auto).apply();
            else if (w == 1) prefs.edit().putBoolean("obd_alerts", !alerts).apply();
            else if (w == 2) {
                final int[] vals = {98, 100, 102, 105, 108, 110, 115};
                String[] n = new String[vals.length];
                int sel = 3;
                for (int i = 0; i < vals.length; i++) { n[i] = vals[i] + "°C"; if (vals[i] == ObdAlerts.hotLimit(prefs)) sel = i; }
                choose(I18n.L("التنبيه عند حرارة", "Alert at temperature"), n, sel, new IntCb() { public void on(int k) {
                    prefs.edit().putInt("obd_hot", vals[k]).apply();
                }});
            } else if (w == 3) {
                obd.disconnect();
                prefs.edit().remove("obd_type").remove("obd_addr").remove("obd_name").remove("obd_proto").apply();
                onState(obd.state(), "");
            } else if (w == 4) {
                StringBuilder b = new StringBuilder();
                synchronized (Obd.commLog) { for (String l : Obd.commLog) b.append(l).append('\n'); }
                if (b.length() == 0) b.append(I18n.L("لا يوجد سجل بعد. اضغط «اتصال» أولاً.", "No log yet. Tap Connect first."));
                TextView t = multi(b.toString(), 12, Main.TEXT);
                t.setMaxLines(1000);
                t.setTextIsSelectable(true);
                if (android.os.Build.VERSION.SDK_INT >= 17) t.setTextDirection(View.TEXT_DIRECTION_LTR);
                android.widget.ScrollView sv = new android.widget.ScrollView(ObdActivity.this);
                sv.setPadding(px(16), px(10), px(16), px(10));
                sv.addView(t);
                new TB(ObdActivity.this, Main.dlg()).raw().wide().setTitle(I18n.L("سجل اتصال OBD", "OBD connection log")).setView(sv)
                    .setNegativeButton(I18n.L("إغلاق", "Close"), null).show();
            } else {
                prefs.edit().remove("obd_proto").apply();
                obd.disconnect();
                h.postDelayed(new Runnable() { public void run() { obd.connect(); } }, 800);
            }
        }});
    }
}
