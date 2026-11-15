package com.stradaos.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.HashMap;

/**
 * My Car · قاعدة الصفحات الجديدة (مواقيت الصلاة، الراديو، OBD2): شاشة كاملة بألوان My Car، شريط علوي فيه زر رجوع
 * وعنوان، ومساحة للمحتوى. المقاسات على لوحة افتراضية 1024×600 مثل الشاشة الرئيسية، والاتجاه من اليمين لليسار بالعربية.
 */
public abstract class Screen extends Activity {

    protected float s = 1f;
    protected SharedPreferences prefs;
    protected FrameLayout root;
    protected LinearLayout top;
    protected FrameLayout content;
    protected TextView titleTv;
    private final HashMap<String, Bitmap> icons = new HashMap<String, Bitmap>();

    protected abstract String title();
    protected abstract void build(FrameLayout content);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("launcher", 0);
        if (I18n.lang == null || prefs.getString("lang", null) != null) I18n.init(this, prefs);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        s = Math.min(dm.widthPixels / 1024f, dm.heightPixels / 600f);
        if (s <= 0) s = 1;
        root = new FrameLayout(this);
        root.setBackgroundColor(Main.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        top = hbox();
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(px(16), px(8), px(16), px(8));
        View back = circle("arrow_back", 44, 24, Main.CIRCLE);
        if (rtl()) back.setRotation(180);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        top.addView(back, new LinearLayout.LayoutParams(px(44), px(44)));
        titleTv = tv(title(), 22, Main.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        marginStart(tl, px(14));
        top.addView(titleTv, tl);
        col.addView(top, new LinearLayout.LayoutParams(-1, px(64)));

        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        build(content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }

    private void immersive() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN | 0x00001000);
        } catch (Throwable ignored) { }
    }

    // ───────────────────────── أدوات الواجهة ─────────────────────────

    /** العربية: الصفوف الأفقية تُعكس يدوياً (التطبيق لا يفعّل supportsRtl حتى لا تتأثر الشاشة الرئيسية). */
    protected boolean rtl() { return !"en".equals(I18n.lang); }

    /** صف أفقي يُعكس ترتيبه بالعربية (أول عنصر يظهر على اليمين). */
    protected LinearLayout hbox() { return new Row(this, rtl()); }

    static class Row extends LinearLayout {
        final boolean rev;
        Row(android.content.Context c, boolean rev) { super(c); this.rev = rev; }
        @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, rev && index < 0 ? 0 : index, params);
        }
    }

    protected int px(float v) { return Math.round(v * s); }

    protected TextView tv(String t, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s * Main.FONT);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return v;
    }

    protected TextView multi(String t, float size, int color) {
        TextView v = tv(t, size, color, false);
        v.setSingleLine(false);
        v.setMaxLines(20);
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    protected GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r * s);
        return g;
    }

    protected Bitmap asset(String path) {
        Bitmap b = icons.get(path);
        if (b != null) return b;
        try {
            InputStream in = getAssets().open(path);
            b = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Throwable e) {
            b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
        icons.put(path, b);
        return b;
    }

    protected ImageView icon(String name, int color) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(asset("ic/" + name + ".png"));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (color != 0xFFFFFFFF) v.setColorFilter(color);
        return v;
    }

    protected FrameLayout circle(String ic, float d, float isz, int color) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        f.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(isz), px(isz));
        lp.gravity = Gravity.CENTER;
        f.addView(icon(ic, color == Main.RED ? Color.WHITE : Main.ICON), lp);
        return f;
    }

    /** زر بشكل كبسولة: أيقونة اختيارية ونص. filled = بلون التمييز. */
    protected LinearLayout pill(String ic, String text, boolean filled, View.OnClickListener l) {
        LinearLayout b = hbox();
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(16), px(8), px(16), px(8));
        b.setBackground(round(filled ? Main.RED : Main.BTN, 22));
        int fg = filled ? Color.WHITE : Main.TEXT;
        if (ic != null) {
            ImageView iv = icon(ic, filled ? Color.WHITE : Main.ICON);
            b.addView(iv, new LinearLayout.LayoutParams(px(20), px(20)));
        }
        if (text != null) {
            TextView t = tv(text, 15, fg, true);
            if (ic != null) t.setPadding(px(8), 0, px(8), 0);
            b.addView(t);
        }
        b.setOnClickListener(l);
        return b;
    }

    protected LinearLayout cardBox() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Main.CARD, 18));
        c.setPadding(px(16), px(14), px(16), px(14));
        return c;
    }

    protected LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    /** هامش من جهة البداية (يمين بالعربية) — يعمل أيضاً على Android 4.1. */
    protected void marginStart(ViewGroup.MarginLayoutParams l, int v) {
        if (rtl()) l.rightMargin = v; else l.leftMargin = v;
    }

    protected void marginEnd(ViewGroup.MarginLayoutParams l, int v) {
        if (rtl()) l.leftMargin = v; else l.rightMargin = v;
    }

    protected LinearLayout.LayoutParams lpw(float weight) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        return l;
    }

    protected void toast(String m) {
        try { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
    }

    /** نافذة اختيار بسيطة بأسلوب My Car. */
    protected void choose(String title, String[] items, int sel, final IntCb cb) {
        TB b = new TB(this, Main.dlg());
        b.raw().setTitle(title);
        if (sel >= 0) b.setSingleChoiceItems(items, sel, new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) { d.dismiss(); cb.on(w); }
        });
        else b.setItems(items, new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) { cb.on(w); }
        });
        b.setNegativeButton(I18n.L("إغلاق", "Close"), null);
        b.show();
    }

    public interface IntCb { void on(int i); }
}
