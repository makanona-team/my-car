package com.stradaos.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;

import java.io.File;

/** My Car · منبّه الأذان: عند وقت الصلاة يشغّل الصوت ويعرض إشعاراً، ثم يضبط الأذان التالي. */
public class PrayerAlarm extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            int prayer = i.getIntExtra("prayer", -1);
            long at = i.getLongExtra("at", 0);
            SharedPreferences p = c.getSharedPreferences(Prayer.PREFS, 0);
            // تأخر كبير (الشاشة كانت مطفأة ساعة مثلاً): لا يُشغَّل أذان قديم
            boolean fresh = at == 0 || Math.abs(System.currentTimeMillis() - at) < 10 * 60 * 1000L;
            if (prayer >= 0 && fresh && p.getBoolean("pr_alert", false)) {
                Intent s = new Intent(c, Athan.class);
                s.putExtra("prayer", prayer);
                c.startService(s);
            }
        } catch (Throwable e) {
            Guard.log("PrayerAlarm: " + e);
        }
        Prayer.schedule(c);
    }

    /** خدمة قصيرة تشغّل صوت الأذان (ملف يختاره المستخدم أو نغمة الإشعار) وتعرض الإشعار. */
    public static class Athan extends Service {
        static final String CH = "prayer";
        static final int NOTIF = 7302;
        private MediaPlayer mp;
        private final Handler h = new Handler();
        private AudioManager am;
        private final AudioManager.OnAudioFocusChangeListener focus = new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int f) { }
        };

        @Override public IBinder onBind(Intent i) { return null; }

        @Override
        public int onStartCommand(Intent i, int flags, int id) {
            if (i != null && "stop".equals(i.getAction())) { finish(); return START_NOT_STICKY; }
            int prayer = i != null ? i.getIntExtra("prayer", Prayer.DHUHR) : Prayer.DHUHR;
            notifyPrayer(prayer);
            play();
            return START_NOT_STICKY;
        }

        private void notifyPrayer(int prayer) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel ch = new NotificationChannel(CH, I18n.L("مواقيت الصلاة", "Prayer times"), NotificationManager.IMPORTANCE_HIGH);
                    ch.setSound(null, null);   // الصوت يشغّله التطبيق نفسه
                    nm.createNotificationChannel(ch);
                }
                Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
                String where = Prayer.place(this).name;
                b.setSmallIcon(getApplicationInfo().icon)
                    .setContentTitle(I18n.L("حان الآن موعد أذان ", "Time for ") + Prayer.name(prayer))
                    .setContentText(where + " · " + Prayer.hhmm(Prayer.times(this, java.util.Calendar.getInstance())[prayer],
                        android.text.format.DateFormat.is24HourFormat(this)))
                    .setAutoCancel(true);
                Intent open = new Intent(this, PrayerActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                b.setContentIntent(PendingIntent.getActivity(this, 7303, open, PendingIntent.FLAG_UPDATE_CURRENT));
                Intent stop = new Intent(this, Athan.class);
                stop.setAction("stop");
                PendingIntent sp = PendingIntent.getService(this, 7304, stop, PendingIntent.FLAG_UPDATE_CURRENT);
                b.addAction(0, I18n.L("إيقاف الصوت", "Stop sound"), sp);
                b.setDeleteIntent(sp);
                if (android.os.Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
                nm.notify(NOTIF, b.build());
            } catch (Throwable e) {
                Guard.log("Athan notify: " + e);
            }
        }

        private void play() {
            stopSound();
            try {
                SharedPreferences p = getSharedPreferences(Prayer.PREFS, 0);
                String path = p.getString("pr_sound", "");
                Uri u = null;
                if (path.length() > 0 && new File(path).exists()) u = Uri.fromFile(new File(path));
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (u == null) { finishLater(5000); return; }
                am = (AudioManager) getSystemService(AUDIO_SERVICE);
                am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                mp = new MediaPlayer();
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mp.setDataSource(this, u);
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    public void onCompletion(MediaPlayer m) { finish(); }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    public boolean onError(MediaPlayer m, int a, int b) { finish(); return true; }
                });
                mp.prepare();
                mp.start();
                finishLater(6 * 60 * 1000L);   // حد أقصى: 6 دقائق
            } catch (Throwable e) {
                Guard.log("Athan play: " + e);
                finish();
            }
        }

        private void finishLater(long ms) {
            h.removeCallbacksAndMessages(null);
            h.postDelayed(new Runnable() { public void run() { finish(); } }, ms);
        }

        private void stopSound() {
            try { if (mp != null) { mp.stop(); mp.release(); } } catch (Throwable ignored) { }
            mp = null;
            try { if (am != null) am.abandonAudioFocus(focus); } catch (Throwable ignored) { }
        }

        private void finish() {
            h.removeCallbacksAndMessages(null);
            stopSound();
            stopSelf();
        }

        @Override public void onDestroy() { stopSound(); super.onDestroy(); }
    }
}
