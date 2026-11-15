package com.stradaos.launcher;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * My Car · «ثيمات جديدة»: قائمة ثيمات في مستودع المطوّر على GitHub (themes/index.json) تُثبَّت دون تحديث التطبيق،
 * و«لصق رابط ثيم» لأي ملف JSON بنفس الشكل.
 *
 * شكل الثيم: {"id":"x","ar":"اسم","en":"Name","layout":"garage","accent":"#FF8F1F","mode":"night","wall":"asset:palmyra" أو رابط صورة}
 */
public class RemoteThemes {

    public static final String INDEX = "https://raw.githubusercontent.com/" + Updates.OWNER + "/" + Updates.REPO + "/main/themes/index.json";

    public static void browse(final Main m) { load(m, INDEX); }

    public static void paste(final Main m) {
        final EditText et = new EditText(m);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setHint("https://…/theme.json");
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0x88FFFFFF);
        FrameLayout wrap = new FrameLayout(m);
        int pd = (int) (18 * m.getResources().getDisplayMetrics().density);
        wrap.setPadding(pd, pd / 2, pd, 0);
        wrap.addView(et);
        new TB(m, Main.dlg()).raw().setTitle(I18n.L("رابط الثيم", "Theme link")).setView(wrap)
            .setPositiveButton(I18n.L("تثبيت", "Install"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String u = et.getText().toString().trim();
                if (u.startsWith("http")) load(m, u); else m.toastPublic(I18n.L("الرابط غير صحيح", "Invalid link"));
            }})
            .setNegativeButton(I18n.L("إلغاء", "Cancel"), null).show();
    }

    static void load(final Main m, final String url) {
        m.toastPublic(I18n.L("جارِ تحميل الثيمات…", "Loading themes…"));
        new Thread(new Runnable() { public void run() {
            String body = null, err = null;
            try { Net.init(m); body = new String(Net.get(url, 15000), "UTF-8"); } catch (Throwable e) { err = e.getMessage(); }
            final List<JSONObject> themes = new ArrayList<JSONObject>();
            try {
                if (body != null) {
                    String b = body.trim();
                    if (b.startsWith("[")) { JSONArray a = new JSONArray(b); for (int i = 0; i < a.length(); i++) themes.add(a.getJSONObject(i)); }
                    else {
                        JSONObject o = new JSONObject(b);
                        JSONArray a = o.optJSONArray("themes");
                        if (a != null) for (int i = 0; i < a.length(); i++) themes.add(a.getJSONObject(i));
                        else themes.add(o);
                    }
                }
            } catch (Throwable e) { err = I18n.L("ملف الثيم غير صالح", "Invalid theme file"); }
            final String fe = err;
            m.runOnUiThread(new Runnable() { public void run() {
                if (themes.isEmpty()) { m.toastPublic(I18n.L("تعذّر تحميل الثيمات", "Could not load themes") + (fe != null ? ": " + fe : "")); return; }
                if (themes.size() == 1) { install(m, themes.get(0)); return; }
                String[] n = new String[themes.size()];
                for (int i = 0; i < n.length; i++) {
                    JSONObject t = themes.get(i);
                    n[i] = I18n.L(t.optString("ar", t.optString("id")), t.optString("en", t.optString("id")));
                }
                new TB(m, Main.dlg()).raw().setTitle(I18n.L("ثيمات جديدة", "New themes"))
                    .setItems(n, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { install(m, themes.get(w)); } })
                    .setNegativeButton(I18n.L("إغلاق", "Close"), null).show();
            }});
        }}).start();
    }

    static void install(final Main m, final JSONObject t) {
        new Thread(new Runnable() { public void run() {
            String err = null;
            SharedPreferences.Editor e = m.getSharedPreferences("launcher", 0).edit();
            try {
                String layout = t.optString("layout", "garage");
                if (Dash.layoutIndex(layout) < 0) layout = "garage";
                e.putString("dash_layout", layout);
                String acc = t.optString("accent", "");
                if (acc.length() > 0) e.putInt("dash_accent", Color.parseColor(acc));
                String mode = t.optString("mode", "");
                if (mode.length() > 0) e.putString("dash_mode", mode);
                String wall = t.optString("wall", "theme");
                if (wall.startsWith("asset:")) e.putString("dash_wall", wall.substring(6));
                else if (wall.startsWith("http")) {
                    byte[] img = Net.get(wall, 30000);
                    File dir = new File(m.getFilesDir(), "themes");
                    dir.mkdirs();
                    File f = new File(dir, t.optString("id", "theme").replaceAll("[^A-Za-z0-9_-]", "_") + ".jpg");
                    FileOutputStream os = new FileOutputStream(f);
                    os.write(img);
                    os.close();
                    e.putString("dash_wall", "file:" + f.getAbsolutePath());
                } else e.putString("dash_wall", wall);
                e.apply();
            } catch (Throwable x) { err = x.getMessage(); }
            final String fe = err;
            m.runOnUiThread(new Runnable() { public void run() {
                if (fe != null) m.toastPublic(I18n.L("تعذّر تثبيت الثيم: ", "Could not install the theme: ") + fe);
                else { m.toastPublic(I18n.L("تم تثبيت الثيم ✓", "Theme installed ✓")); m.rebuildPublic(); }
            }});
        }}).start();
    }
}
