package com.stradaos.launcher;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * My Car · راديو الإنترنت العربي. المحطات من دليل Radio-Browser.info المفتوح (روابطها تتحدّث باستمرار):
 * المفضلة، سوريا، القرآن الكريم، الأكثر استماعاً بالعربية، الدول العربية، والبحث بالاسم.
 */
public class RadioActivity extends Screen {

    static final String[] HOSTS = {"https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info", "https://de2.api.radio-browser.info", "https://fi1.api.radio-browser.info"};
    static int hostIdx = 0;

    static final String[][] COUNTRIES = {
        {"SY", "سوريا", "Syria"}, {"LB", "لبنان", "Lebanon"}, {"IQ", "العراق", "Iraq"}, {"JO", "الأردن", "Jordan"},
        {"PS", "فلسطين", "Palestine"}, {"SA", "السعودية", "Saudi Arabia"}, {"EG", "مصر", "Egypt"}, {"AE", "الإمارات", "UAE"},
        {"KW", "الكويت", "Kuwait"}, {"QA", "قطر", "Qatar"}, {"BH", "البحرين", "Bahrain"}, {"OM", "عُمان", "Oman"},
        {"YE", "اليمن", "Yemen"}, {"LY", "ليبيا", "Libya"}, {"TN", "تونس", "Tunisia"}, {"DZ", "الجزائر", "Algeria"},
        {"MA", "المغرب", "Morocco"}, {"SD", "السودان", "Sudan"}, {"TR", "تركيا", "Türkiye"},
    };

    /** الأقسام: id، اسم عربي، اسم إنجليزي، أيقونة. */
    static final String[][] TABS = {
        {"fav", "المفضلة", "Favourites", "star"},
        {"sy", "سوريا", "Syria", "radio_tower"},
        {"quran", "القرآن الكريم", "Holy Quran", "mosque"},
        {"top", "الأكثر استماعاً", "Most popular", "favorite"},
        {"countries", "الدول العربية", "Arab countries", "public"},
        {"search", "بحث", "Search", "search"},
    };

    static class Station {
        String uuid, name, url, country, codec;
        int bitrate;

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("stationuuid", uuid); o.put("name", name); o.put("url_resolved", url);
                o.put("countrycode", country); o.put("codec", codec); o.put("bitrate", bitrate);
            } catch (Throwable ignored) { }
            return o;
        }

        static Station from(JSONObject o) {
            Station s = new Station();
            s.uuid = o.optString("stationuuid", "");
            s.name = o.optString("name", "").trim();
            s.url = o.optString("url_resolved", "");
            if (s.url.length() == 0) s.url = o.optString("url", "");
            s.country = o.optString("countrycode", "");
            s.codec = o.optString("codec", "");
            s.bitrate = o.optInt("bitrate", 0);
            return s;
        }

        String info() {
            StringBuilder b = new StringBuilder();
            String cn = countryName(country);
            if (cn != null) b.append(cn);
            if (codec != null && codec.length() > 0 && !"UNKNOWN".equalsIgnoreCase(codec)) b.append(b.length() > 0 ? " · " : "").append(codec);
            if (bitrate > 0) b.append(b.length() > 0 ? " · " : "").append(bitrate).append(" kbps");
            return b.toString();
        }
    }

    static String countryName(String code) {
        if (code == null) return null;
        for (String[] c : COUNTRIES) if (c[0].equalsIgnoreCase(code)) return I18n.L(c[1], c[2]);
        return code.length() > 0 ? code : null;
    }

    private final Handler h = new Handler();
    private final List<Station> items = new ArrayList<Station>();
    private final List<Station> favs = new ArrayList<Station>();
    private final Set<String> favIds = new HashSet<String>();
    private String tab = "sy";
    private String country = null;
    private LinearLayout tabsCol;
    private ListView listView;
    private Adapter adapter;
    private TextView statusTv, nowTv, nowSubTv;
    private LinearLayout searchRow;
    private EditText searchEt;
    private View stopBtn;
    private int loadSeq;

    @Override protected String title() { return I18n.L("راديو الإنترنت", "Internet radio"); }

    @Override
    protected void build(FrameLayout content) {
        Net.init(this);
        loadFavs();
        tab = favs.isEmpty() ? "sy" : prefs.getString("radio_tab", "fav");

        LinearLayout row = new LinearLayout(this);
        row.setPadding(px(16), 0, px(16), px(12));
        content.addView(row, new FrameLayout.LayoutParams(-1, -1));

        // ── الأقسام ──
        ScrollView tsv = new ScrollView(this);
        tabsCol = new LinearLayout(this);
        tabsCol.setOrientation(LinearLayout.VERTICAL);
        tsv.addView(tabsCol);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(px(200), -1);
        marginEnd(tlp, px(12));
        row.addView(tsv, tlp);

        // ── القائمة + شريط التشغيل ──
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        row.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.setBackground(round(Main.CARD, 16));
        now.setPadding(px(14), px(8), px(14), px(8));
        now.addView(icon("radio", Main.RED), lp(px(30), px(30)));
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        nt.setPadding(px(12), 0, px(12), 0);
        nowTv = tv("", 17, Main.TEXT, true);
        nowSubTv = tv("", 12, Main.MUTED, false);
        nt.addView(nowTv);
        nt.addView(nowSubTv);
        now.addView(nt, new LinearLayout.LayoutParams(0, -2, 1));
        stopBtn = pill("stop", I18n.L("إيقاف", "Stop"), true, new View.OnClickListener() { public void onClick(View v) { RadioService.stop(RadioActivity.this); } });
        now.addView(stopBtn);
        main.addView(now, lp(-1, px(64)));

        searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, px(8), 0, 0);
        searchEt = new EditText(this);
        searchEt.setSingleLine(true);
        searchEt.setHint(I18n.L("اسم المحطة… (مثلاً: شام، المدينة، قرآن)", "Station name… (e.g. Sham, Quran)"));
        searchEt.setTextColor(Main.TEXT);
        searchEt.setHintTextColor(Main.MUTED);
        searchEt.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 16 * s);
        searchEt.setBackground(round(Main.INNER, 14));
        searchEt.setPadding(px(14), px(8), px(14), px(8));
        searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEt.setInputType(InputType.TYPE_CLASS_TEXT);
        searchEt.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int a, KeyEvent e) { doSearch(); return true; }
        });
        searchRow.addView(searchEt, new LinearLayout.LayoutParams(0, px(46), 1));
        View gap = new View(this);
        searchRow.addView(gap, lp(px(8), 1));
        searchRow.addView(pill("search", I18n.L("بحث", "Search"), true, new View.OnClickListener() { public void onClick(View v) { doSearch(); } }), lp(-2, px(46)));
        main.addView(searchRow, lp(-1, -2));

        statusTv = tv("", 14, Main.MUTED, false);
        statusTv.setGravity(Gravity.CENTER);
        statusTv.setPadding(0, px(8), 0, px(4));
        main.addView(statusTv, lp(-1, -2));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(px(6));
        listView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        listView.setCacheColorHint(Color.TRANSPARENT);
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { onItem(pos); }
        });
        main.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        buildTabs();
        showTab(tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RadioService.listener = new Runnable() { public void run() { updateNow(); adapter.notifyDataSetChanged(); } };
        updateNow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        RadioService.listener = null;
    }

    private void buildTabs() {
        tabsCol.removeAllViews();
        for (final String[] t : TABS) {
            boolean sel = t[0].equals(tab);
            LinearLayout b = new LinearLayout(this);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding(px(14), 0, px(14), 0);
            b.setBackground(round(sel ? Main.RED : Main.CARD, 14));
            b.addView(icon(t[3], sel ? Color.WHITE : Main.ICON), lp(px(22), px(22)));
            TextView tx = tv(I18n.L(t[1], t[2]), 16, sel ? Color.WHITE : Main.TEXT, sel);
            tx.setPadding(px(10), 0, px(10), 0);
            b.addView(tx);
            b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { country = null; showTab(t[0]); } });
            LinearLayout.LayoutParams l = lp(-1, px(54));
            l.bottomMargin = px(8);
            tabsCol.addView(b, l);
        }
    }

    private void showTab(String id) {
        tab = id;
        if (!"search".equals(id) && !"countries".equals(id)) prefs.edit().putString("radio_tab", id).apply();
        buildTabs();
        searchRow.setVisibility("search".equals(id) ? View.VISIBLE : View.GONE);
        items.clear();
        adapter.notifyDataSetChanged();
        if ("fav".equals(id)) {
            items.addAll(favs);
            adapter.notifyDataSetChanged();
            statusTv.setText(favs.isEmpty() ? I18n.L("لا توجد محطات مفضلة بعد. اضغط ☆ بجانب أي محطة لإضافتها هنا.", "No favourites yet. Tap ☆ next to a station to add it.") : "");
            statusTv.setVisibility(favs.isEmpty() ? View.VISIBLE : View.GONE);
        } else if ("sy".equals(id)) {
            load("sy", "/json/stations/bycountrycodeexact/SY?hidebroken=true&order=clickcount&reverse=true&limit=300");
        } else if ("quran".equals(id)) {
            load("quran", "/json/stations/search?tag=quran&hidebroken=true&order=clickcount&reverse=true&limit=150");
        } else if ("top".equals(id)) {
            load("top", "/json/stations/bylanguage/arabic?hidebroken=true&order=clickcount&reverse=true&limit=300");
        } else if ("countries".equals(id)) {
            if (country == null) showCountries();
            else load("c_" + country, "/json/stations/bycountrycodeexact/" + country + "?hidebroken=true&order=clickcount&reverse=true&limit=300");
        } else if ("search".equals(id)) {
            statusTv.setVisibility(View.VISIBLE);
            statusTv.setText(I18n.L("اكتب اسم المحطة واضغط بحث", "Type a station name and tap Search"));
            searchEt.requestFocus();
        }
    }

    private void showCountries() {
        statusTv.setVisibility(View.GONE);
        String[] names = new String[COUNTRIES.length];
        for (int i = 0; i < COUNTRIES.length; i++) names[i] = I18n.L(COUNTRIES[i][1], COUNTRIES[i][2]);
        choose(I18n.L("اختر الدولة", "Choose a country"), names, -1, new IntCb() { public void on(int w) {
            country = COUNTRIES[w][0];
            titleTv.setText(title() + " · " + I18n.L(COUNTRIES[w][1], COUNTRIES[w][2]));
            showTab("countries");
        }});
    }

    private void doSearch() {
        String q = searchEt.getText().toString().trim();
        if (q.length() < 2) { toast(I18n.L("اكتب حرفين على الأقل", "Type at least 2 letters")); return; }
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(searchEt.getWindowToken(), 0);
        } catch (Throwable ignored) { }
        try {
            load(null, "/json/stations/search?name=" + URLEncoder.encode(q, "UTF-8") + "&hidebroken=true&order=clickcount&reverse=true&limit=150");
        } catch (Throwable ignored) { }
    }

    /** يحمّل قائمة من Radio-Browser (مع نسخة محفوظة تظهر فوراً وتُستخدم بدون إنترنت). */
    private void load(final String cacheKey, final String path) {
        final int seq = ++loadSeq;
        statusTv.setVisibility(View.VISIBLE);
        statusTv.setText(I18n.L("جارِ تحميل المحطات…", "Loading stations…"));
        if (cacheKey != null) {
            List<Station> cached = parse(readCache(cacheKey));
            if (!cached.isEmpty()) { items.clear(); items.addAll(cached); adapter.notifyDataSetChanged(); }
        }
        new Thread(new Runnable() { public void run() {
            String body = null, err = null;
            for (int k = 0; k < HOSTS.length && body == null; k++) {
                int idx = (hostIdx + k) % HOSTS.length;
                try {
                    body = new String(Net.get(HOSTS[idx] + path, 15000), "UTF-8");
                    hostIdx = idx;
                } catch (Throwable e) { err = String.valueOf(e); }
            }
            final String fb = body, fe = err;
            runOnUiThread(new Runnable() { public void run() {
                if (seq != loadSeq || isFinishing()) return;
                if (fb == null) {
                    if (items.isEmpty()) statusTv.setText(I18n.L("تعذّر الاتصال بدليل المحطات. تأكد من الإنترنت.", "Could not reach the station directory. Check the internet."));
                    else statusTv.setText(I18n.L("بدون إنترنت · نسخة محفوظة", "Offline · saved list"));
                    Guard.log("Radio list: " + fe);
                    return;
                }
                List<Station> l = parse(fb);
                if (cacheKey != null && !l.isEmpty()) writeCache(cacheKey, fb);
                items.clear();
                items.addAll(l);
                adapter.notifyDataSetChanged();
                listView.setSelection(0);
                if (l.isEmpty()) statusTv.setText(I18n.L("لا توجد نتائج", "No results"));
                else statusTv.setVisibility(View.GONE);
            }});
        }}).start();
    }

    private static List<Station> parse(String json) {
        List<Station> out = new ArrayList<Station>();
        if (json == null) return out;
        try {
            JSONArray a = new JSONArray(json);
            Set<String> seen = new HashSet<String>();
            for (int i = 0; i < a.length(); i++) {
                Station s = Station.from(a.getJSONObject(i));
                if (s.name.length() == 0 || s.url.length() == 0) continue;
                String key = s.name.toLowerCase(java.util.Locale.US) + "|" + s.country;
                if (!seen.add(key)) continue;   // محطات مكررة بنفس الاسم
                out.add(s);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private String readCache(String key) {
        try {
            File f = new File(getCacheDir(), "radio_" + key + ".json");
            if (!f.exists()) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            in.close();
            return new String(b, 0, n, "UTF-8");
        } catch (Throwable e) { return null; }
    }

    private void writeCache(String key, String body) {
        try {
            FileOutputStream os = new FileOutputStream(new File(getCacheDir(), "radio_" + key + ".json"));
            os.write(body.getBytes("UTF-8"));
            os.close();
        } catch (Throwable ignored) { }
    }

    // ── المفضلة ──

    private void loadFavs() {
        favs.clear();
        favIds.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString("radio_favs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                Station s = Station.from(a.getJSONObject(i));
                favs.add(s);
                favIds.add(s.uuid);
            }
        } catch (Throwable ignored) { }
    }

    private void saveFavs() {
        JSONArray a = new JSONArray();
        for (Station s : favs) a.put(s.json());
        prefs.edit().putString("radio_favs", a.toString()).apply();
    }

    private void toggleFav(Station s) {
        if (favIds.contains(s.uuid)) {
            for (int i = favs.size() - 1; i >= 0; i--) if (favs.get(i).uuid.equals(s.uuid)) favs.remove(i);
            favIds.remove(s.uuid);
            toast(I18n.L("أُزيلت من المفضلة", "Removed from favourites"));
        } else {
            favs.add(s);
            favIds.add(s.uuid);
            toast(I18n.L("أُضيفت إلى المفضلة ★", "Added to favourites ★"));
        }
        saveFavs();
        if ("fav".equals(tab)) showTab("fav");
        else adapter.notifyDataSetChanged();
    }

    // ── التشغيل ──

    private void onItem(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        final Station st = items.get(pos);
        if (st.uuid.equals(RadioService.curUuid) && RadioService.state != RadioService.IDLE && RadioService.state != RadioService.ERROR) {
            RadioService.stop(this);
            return;
        }
        RadioService.play(this, st.name, st.url, st.uuid, st.info());
        prefs.edit().putString("radio_last", st.json().toString()).apply();
        // Radio-Browser يطلب تسجيل «نقرة» عند التشغيل (يساعد ترتيب المحطات للجميع)
        new Thread(new Runnable() { public void run() {
            try { Net.get(HOSTS[hostIdx] + "/json/url/" + st.uuid, 8000); } catch (Throwable ignored) { }
        }}).start();
        updateNow();
    }

    private void updateNow() {
        int st = RadioService.state;
        if (st == RadioService.IDLE || RadioService.curName == null) {
            Station last = null;
            try { last = Station.from(new JSONObject(prefs.getString("radio_last", "{}"))); } catch (Throwable ignored) { }
            nowTv.setText(last != null && last.name.length() > 0 ? last.name : I18n.L("اختر محطة", "Pick a station"));
            nowSubTv.setText(last != null && last.name.length() > 0 ? I18n.L("آخر محطة · اضغطها في القائمة للتشغيل", "Last station · tap it in the list to play") : I18n.L("اضغط على أي محطة لتشغيلها", "Tap any station to play it"));
            stopBtn.setVisibility(View.GONE);
            return;
        }
        nowTv.setText(RadioService.curName);
        String sub = st == RadioService.CONNECTING ? I18n.L("جارِ الاتصال…", "Connecting…")
                : st == RadioService.PLAYING ? I18n.L("يعمل الآن", "Playing") + (RadioService.curInfo != null && RadioService.curInfo.length() > 0 ? " · " + RadioService.curInfo : "")
                : I18n.L("تعذّر تشغيل هذه المحطة، جرّب غيرها", "This station could not be played, try another");
        nowSubTv.setText(sub);
        stopBtn.setVisibility(st == RadioService.ERROR ? View.GONE : View.VISIBLE);
    }

    class Adapter extends BaseAdapter {
        public int getCount() { return items.size(); }
        public Object getItem(int p) { return items.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int pos, View cv, ViewGroup parent) {
            final Station st = items.get(pos);
            LinearLayout r = new LinearLayout(RadioActivity.this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(px(60));
            r.setPadding(px(14), px(6), px(6), px(6));
            boolean cur = st.uuid.equals(RadioService.curUuid) && RadioService.state != RadioService.IDLE;
            r.setBackground(round(cur ? Main.RED : Main.CARD, 14));
            int fg = cur ? Color.WHITE : Main.TEXT;
            ImageView ic = icon(cur && RadioService.state == RadioService.PLAYING ? "pause" : "play_arrow", cur ? Color.WHITE : Main.ICON);
            r.addView(ic, lp(px(26), px(26)));
            LinearLayout t = new LinearLayout(RadioActivity.this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(px(12), 0, px(12), 0);
            t.addView(tv(st.name, 17, fg, true));
            t.addView(tv(st.info(), 12, cur ? Color.WHITE : Main.MUTED, false));
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            final boolean fav = favIds.contains(st.uuid);
            TextView star = tv(fav ? "★" : "☆", 26, fav ? (cur ? Color.WHITE : 0xFFFFC107) : (cur ? Color.WHITE : Main.MUTED), false);
            star.setGravity(Gravity.CENTER);
            star.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleFav(st); } });
            r.addView(star, lp(px(54), px(54)));
            return r;
        }
    }
}
