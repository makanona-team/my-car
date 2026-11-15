package com.stradaos.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;

/**
 * My Car · مشغّل راديو الإنترنت في الخلفية: يشغّل البث، يأخذ تركيز الصوت (فيتوقف الراديو العادي أو الموسيقى)، يعرض
 * إشعاراً فيه «إيقاف»، ويعيد المحاولة تلقائياً إذا انقطع البث. يُرسل اسم المحطة لبطاقة الموسيقى في الشاشة الرئيسية.
 */
public class RadioService extends Service {

    public static final int IDLE = 0, CONNECTING = 1, PLAYING = 2, ERROR = 3;
    static volatile int state = IDLE;
    static volatile String curName, curUuid, curUrl, curInfo;
    static volatile String lastError;
    /** يُستدعى (على خيط الواجهة) عند تغيّر الحالة. */
    static Runnable listener;

    static final String CH = "radio";
    static final int NOTIF = 7401;

    private MediaPlayer mp;
    private AudioManager am;
    private final Handler h = new Handler();
    private int retries;
    private boolean ducked;

    public static void play(Context c, String name, String url, String uuid, String info) {
        Intent i = new Intent(c, RadioService.class);
        i.setAction("play");
        i.putExtra("name", name);
        i.putExtra("url", url);
        i.putExtra("uuid", uuid);
        i.putExtra("info", info);
        c.startService(i);
    }

    public static void stop(Context c) {
        Intent i = new Intent(c, RadioService.class);
        i.setAction("stop");
        try { c.startService(i); } catch (Throwable ignored) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String a = i != null ? i.getAction() : null;
        if ("play".equals(a)) {
            curName = i.getStringExtra("name");
            curUrl = i.getStringExtra("url");
            curUuid = i.getStringExtra("uuid");
            curInfo = i.getStringExtra("info");
            retries = 0;
            start();
        } else if ("stop".equals(a)) {
            stopAll();
        }
        return START_NOT_STICKY;
    }

    private void set(int st) {
        state = st;
        h.post(new Runnable() { public void run() { if (listener != null) try { listener.run(); } catch (Throwable ignored) { } } });
        broadcastMeta(st == PLAYING || st == CONNECTING);
        foreground();
    }

    private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int f) {
            try {
                if (f == AudioManager.AUDIOFOCUS_LOSS) stopAll();
                else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { if (mp != null) { mp.setVolume(0.25f, 0.25f); ducked = true; } }
                else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { if (mp != null && mp.isPlaying()) mp.pause(); }
                else if (f == AudioManager.AUDIOFOCUS_GAIN) {
                    if (mp != null) { if (ducked) mp.setVolume(1f, 1f); ducked = false; if (!mp.isPlaying() && state == PLAYING) mp.start(); }
                }
            } catch (Throwable ignored) { }
        }
    };

    private void start() {
        release();
        if (curUrl == null || curUrl.length() == 0) { lastError = I18n.L("لا يوجد رابط للبث", "No stream URL"); set(ERROR); return; }
        set(CONNECTING);
        try {
            am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(this, Uri.parse(curUrl));
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer m) {
                    try { m.start(); retries = 0; set(PLAYING); } catch (Throwable e) { fail(String.valueOf(e)); }
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer m, int what, int extra) { fail("MediaPlayer " + what + "/" + extra); return true; }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) { fail(I18n.L("انقطع البث", "Stream ended")); }
            });
            mp.prepareAsync();
            // بث لا يبدأ خلال 25 ثانية = خطأ
            h.postDelayed(timeout, 25000);
        } catch (Throwable e) {
            fail(String.valueOf(e));
        }
    }

    private final Runnable timeout = new Runnable() {
        public void run() { if (state == CONNECTING) fail(I18n.L("المحطة لا تستجيب", "Station not responding")); }
    };

    private void fail(String why) {
        h.removeCallbacks(timeout);
        lastError = why;
        Guard.log("Radio: " + curName + " · " + why);
        release();
        if (retries < 2) {
            retries++;
            set(CONNECTING);
            h.postDelayed(new Runnable() { public void run() { start(); } }, 2500);
        } else {
            set(ERROR);
        }
    }

    private void release() {
        h.removeCallbacks(timeout);
        try { if (mp != null) { mp.reset(); mp.release(); } } catch (Throwable ignored) { }
        mp = null;
    }

    private void stopAll() {
        h.removeCallbacksAndMessages(null);
        release();
        try { if (am != null) am.abandonAudioFocus(focus); } catch (Throwable ignored) { }
        state = IDLE;
        broadcastMeta(false);
        try { stopForeground(true); } catch (Throwable ignored) { }
        if (listener != null) h.post(new Runnable() { public void run() { if (listener != null) try { listener.run(); } catch (Throwable ignored) { } } });
        stopSelf();
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        release();
        try { if (am != null) am.abandonAudioFocus(focus); } catch (Throwable ignored) { }
        if (state != IDLE) { state = IDLE; broadcastMeta(false); }
        super.onDestroy();
    }

    /** بطاقة الموسيقى في الشاشة الرئيسية تقرأ هذا الإعلان (مثل مشغّلات الموسيقى المعروفة). */
    private void broadcastMeta(boolean playing) {
        try {
            Intent i = new Intent("com.android.music.metachanged");
            i.putExtra("track", curName != null ? curName : "");
            i.putExtra("artist", I18n.L("راديو إنترنت", "Internet radio") + (curInfo != null && curInfo.length() > 0 ? " · " + curInfo : ""));
            i.putExtra("album", "My Car");
            i.putExtra("playing", playing);
            sendBroadcast(i);
        } catch (Throwable ignored) { }
    }

    private void foreground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, I18n.L("راديو الإنترنت", "Internet radio"), NotificationManager.IMPORTANCE_LOW);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
            String st = state == CONNECTING ? I18n.L("جارِ الاتصال…", "Connecting…") : state == PLAYING ? I18n.L("يعمل الآن", "Playing")
                    : state == ERROR ? I18n.L("تعذّر التشغيل", "Could not play") : "";
            b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(curName != null ? curName : I18n.L("راديو الإنترنت", "Internet radio"))
                .setContentText(st)
                .setOngoing(state == PLAYING || state == CONNECTING);
            Intent open = new Intent(this, RadioActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setContentIntent(PendingIntent.getActivity(this, 7402, open, PendingIntent.FLAG_UPDATE_CURRENT));
            Intent stop = new Intent(this, RadioService.class);
            stop.setAction("stop");
            b.addAction(0, I18n.L("إيقاف", "Stop"), PendingIntent.getService(this, 7403, stop, PendingIntent.FLAG_UPDATE_CURRENT));
            Notification n = b.build();
            if (state == PLAYING || state == CONNECTING) startForeground(NOTIF, n);
            else { stopForeground(false); nm.notify(NOTIF, n); }
        } catch (Throwable e) {
            Guard.log("Radio notif: " + e);
        }
    }
}
