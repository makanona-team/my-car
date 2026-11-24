package com.stradaos.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * My Car · فيديو السيارة الذي يضعه المستخدم: يُنسخ لداخل البرنامج (لا يُرفع ولا يُضمَّن بالبرنامج).
 * يُستعمل للترحيب مرة عند تشغيل السيارة، وخلفية متحركة لثيم «عرض السيارة». الصوت مكتوم دائماً.
 */
final class CarVideo {

    static File file(Context c) { return new File(c.getFilesDir(), "car_video.mp4"); }

    static boolean has(Context c) { File f = file(c); return f.exists() && f.length() > 10000; }

    static boolean isVideoName(String n) {
        n = n.toLowerCase(java.util.Locale.US);
        return n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".3gp") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".mov");
    }

    /** ينسخ الفيديو ويتأكد أنه يُقرأ (مدة > 0). */
    static boolean save(Context c, CarPhoto.Opener op) {
        File tmp = new File(c.getFilesDir(), "car_video.tmp");
        try {
            InputStream in = op.open();
            FileOutputStream os = new FileOutputStream(tmp);
            byte[] b = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(b)) > 0) { os.write(b, 0, n); total += n; if (total > 400L * 1024 * 1024) throw new Exception("too big"); }
            in.close();
            os.getFD().sync();
            os.close();
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            long dur = 0;
            try { r.setDataSource(tmp.getAbsolutePath()); dur = Long.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)); }
            finally { try { r.release(); } catch (Throwable ignored) { } }
            if (dur <= 0) throw new Exception("not a video");
            File dst = file(c);
            dst.delete();
            if (!tmp.renameTo(dst)) throw new Exception("rename");
            c.getSharedPreferences("launcher", 0).edit().putBoolean("video_intro", true).apply();
            return true;
        } catch (Throwable e) {
            Guard.log("CarVideo.save: " + e);
            tmp.delete();
            return false;
        }
    }

    static void delete(Context c) { file(c).delete(); }

    /** فيديو مكتوم يملأ المكان. loop=true للثيم. */
    static VideoView view(Context c, final boolean loop, final Runnable onEnd) {
        final VideoView v = new VideoView(c);
        v.setVideoPath(file(c).getAbsolutePath());
        v.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer mp) {
            try { mp.setVolume(0f, 0f); mp.setLooping(loop); } catch (Throwable ignored) { }
            v.start();
        }});
        v.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { public void onCompletion(MediaPlayer mp) {
            if (loop) { try { v.seekTo(0); v.start(); } catch (Throwable ignored) { } }
            else if (onEnd != null) onEnd.run();
        }});
        v.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer mp, int what, int extra) {
            Guard.log("CarVideo play error " + what + "/" + extra);
            if (onEnd != null) onEnd.run();
            return true;
        }});
        return v;
    }

    /** الترحيب: مرة واحدة لكل تشغيل للشاشة (إقلاع)، فوق الواجهة، ويُتخطّى بلمسة. */
    static void intro(final Main m, final ViewGroup root) {
        try {
            SharedPreferences p = m.getSharedPreferences("launcher", 0);
            if (!has(m) || !p.getBoolean("video_intro", true)) return;
            long boot = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000L;   // دقيقة الإقلاع
            if (Math.abs(p.getLong("intro_boot", 0) - boot) <= 1) return;
            p.edit().putLong("intro_boot", boot).apply();
            final FrameLayout box = new FrameLayout(m);
            box.setBackgroundColor(0xFF000000);
            box.setClickable(true);
            final Runnable close = new Runnable() { public void run() {
                try { ((ViewGroup) box.getParent()).removeView(box); } catch (Throwable ignored) { }
            }};
            VideoView v = view(m, false, close);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            lp.gravity = Gravity.CENTER;
            box.addView(v, lp);
            TextView skip = new TextView(m);
            skip.setText(I18n.L("تخطٍّ ✕", "Skip ✕"));
            skip.setTextColor(0xCCFFFFFF);
            skip.setTextSize(16);
            float d = m.getResources().getDisplayMetrics().density;
            skip.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
            sl.gravity = Gravity.TOP | Gravity.LEFT;
            sl.setMargins((int) (12 * d), (int) (12 * d), 0, 0);
            box.addView(skip, sl);
            box.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { close.run(); } });
            root.addView(box, new FrameLayout.LayoutParams(-1, -1));
            box.postDelayed(close, 30000);   // أمان: لا يبقى أكثر من 30 ثانية
        } catch (Throwable e) { Guard.log("CarVideo.intro: " + e); }
    }
}
