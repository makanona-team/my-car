#!/bin/bash
# My Car 1.1: يثبّت نسخة التجربة على محاكي 1280x720 ويلتقط صوراً لكل تصميم وللقوائم، ويجمع الأعطال من logcat.
PKG=com.feras.mycar.debug
ACT=$PKG/com.stradaos.launcher.Main
OUT=salida; mkdir -p $OUT
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
adb shell wm size; adb shell wm density
adb shell "setprop persist.sys.timezone Asia/Damascus; cmd alarm set-timezone Asia/Damascus" || true
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r -g "$APK" | tail -1
adb shell appops set $PKG android:mock_location allow >/dev/null 2>&1 || true
gps() { adb emu geo fix 40.1408 35.3359 200 8 ${1:-0} >/dev/null || true; }
gps
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
gps 35
shot() { adb shell screencap -p /data/local/tmp/s.png && adb pull /data/local/tmp/s.png $OUT/$1.png >/dev/null; echo "shot $1"; }
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-6}"; }
back() { adb shell input keyevent 4; sleep 3; }
go unidad:kmh 6
# صور اختبار على الذاكرة
adb push .github/testdata/rear.png /sdcard/Download/car_rear.png >/dev/null
adb push .github/testdata/top.png /sdcard/Download/car_top.png >/dev/null
adb push .github/testdata/side.png /sdcard/Download/car_side.png >/dev/null
for n in rear_lit top_lit side_lit; do adb push .github/testdata/$n.png /sdcard/Download/car_$n.png >/dev/null; done
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download >/dev/null 2>&1 || true
n=10
for L in garage center main modes tesla drive classic; do
  go mycar-dash:$L 9; gps 35; sleep 3; shot ${n}_$L; n=$((n+1))
done
go mycar-dash:garage 8; shot 17_garage_art
go mycar-carmenu 5; shot 50_carmenu
back
go mycar-pick:rear 5; shot 51_source
back
go mycar-browse:rear:mem 12; shot 52_browse_mem
back
go mycar-browse:rear:usb 8; shot 53_browse_usb
back
go mycar-pic:rear:/sdcard/Download/car_rear.png 8
go mycar-pic:top:/sdcard/Download/car_top.png 8
go mycar-pic:side:/sdcard/Download/car_side.png 8
go mycar-pic:logo:/sdcard/Download/car_side.png 6
for L in garage center main modes tesla drive classic; do go mycar-dash:$L 8; shot 6_$L; done
go mycar-dash:drive 8; shot 95_drive_day
adb shell input swipe 640 560 640 560 1500; sleep 6; shot 95b_drive_toggled
adb shell input swipe 640 560 640 560 1500; sleep 6; shot 95c_drive_back
go mycar-dash:classic 8; gps 30; sleep 4; shot 90_classic_buttons
go mycar-mapsrc:3 15; shot 91_sat
go mycar-mapfull 10; shot 92_mapfull
back
go mycar-mapsrc:2 12; shot 93_dark
go mycar-mapsrc:0 3
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow >/dev/null 2>&1 || true
go mycar-dl:1.3.5 3; shot 94_dl_a; sleep 3; shot 94_dl_b; sleep 12; shot 94_dl_c
back; back
go mycar-dash:drive 8; shot 70_drive_a; sleep 1; shot 70_drive_b
go mycar-dash:garage 6
go mycar-pic:rear_lit:/sdcard/Download/car_rear_lit.png 6
go mycar-pic:top_lit:/sdcard/Download/car_top_lit.png 6
go mycar-pic:side_lit:/sdcard/Download/car_side_lit.png 6
adb shell cmd uimode night yes; sleep 12; shot 71_night_auto_garage
go mycar-dash:drive 8; shot 72_night_drive
go mycar-dash:tesla 8; shot 73_night_tesla
adb shell cmd uimode night no; sleep 8
go mycar-dash:garage 8; shot 74_day_garage_back
go mycar-carmenu 5; shot 54_carmenu_checked
back
adb push .github/testdata/car_video.mp4 /sdcard/Download/car_video.mp4 >/dev/null
go mycar-dash:show 8; shot 75_show_novideo
go mycar-pick:video 5; shot 75b_pick_video
back
go mycar-browse:video:mem 12; shot 75c_browse_video
back
go mycar-pic:video:/sdcard/Download/car_video.mp4 12; shot 76_show_video; sleep 2; shot 76b_show_video
go mycar-carmenu 5; shot 77_carmenu_video
back
go mycar-intro 3; shot 78_intro; sleep 8; shot 78b_after_intro
go mycar-dash:tesla 6
adb shell cmd uimode night yes; sleep 12; shot 79_night_tesla_side
adb shell cmd uimode night no; sleep 8
go mycar-dash:garage 6
go mycar-lit:auto 3
go mycar-picclear 5
go mycar-dash:tesla 8; shot 18_tesla_art; go "mycar-sayara:suv:0" 5
go mycar-dash:modes 6; go mycar-mode:sport 6; shot 20_modes_sport
go mycar-mode:calm 6; shot 21_modes_calm; go mycar-mode:night 4
go mycar-dash:garage 6
for W in palmyra neon desert_road none; do go mycar-wall:$W 7; shot 30_wall_$W; done
go mycar-wall:theme 5
go mycar-themes 6; shot 40_dialog_layouts; back
go mycar-walls 6; shot 41_dialog_walls; back
go ajustes:11 6; shot 42_settings_syria; back
go mycar-salat 8; shot 43_prayer; back
go mycar-obd 6; shot 44_obd; back
go mycar-lang-en 8; go mycar-dash:garage 8; shot 45_garage_en
go mycar-dash:tesla 8; shot 46_tesla_en
go mycar-lang-ar 6; go mycar-dash:garage 6
go mycar-dash:garage 6; gps 60
go mycar-tripdemo 6; go mycar-dash:garage 8; shot 80_trip_card
go mycar-triplog 5; shot 81_triplog; back
go mycar-pref:digits_ar=true 8; shot 82_digits; go mycar-pref:digits_ar=false 4
go mycar-pref:dash_lite=true 8; shot 83_lite; go mycar-pref:dash_lite=false 4
go mycar-dash:center 8; shot 84_center_road
go mycar-dash:modes 8; shot 85_modes_road
go mycar-dash:garage 6
go mycar-diag 25; shot 86_diag
adb shell input swipe 640 600 640 150 300; sleep 2; shot 87_diag_scroll
go mycar-diagsend 25; shot 87c_diag_send; back
go mycar-diagwatch 20; shot 87b_diag_watch; adb shell pidof com.feras.mycar.debug > /dev/null && echo MAIN_ALIVE >> $OUT/logcat_extra.txt || echo MAIN_DEAD >> $OUT/logcat_extra.txt; back
back
go ajustes:11 6
adb shell input swipe 760 600 760 250 500; sleep 2; shot 88_settings_trips
adb shell input swipe 760 600 760 250 500; sleep 2; shot 89_settings_screen
back
go "mycar-play:https://stream.live.vc.bbcmedia.co.uk/bbc_world_service" 4; shot 90_radio_a; sleep 14; shot 90_radio_b
back
go "mycar-play:http://icecast.radiofrance.fr/fip-midfi.mp3" 18; shot 91_radio_http
back
go "mycar-play:https://radio.garden/api/ara/content/listen/xxxx/channel.mp3" 18; shot 92_radio_bad
back
go mycar-obd 6; shot 93_obd
back
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|MyCar|Dash|Radio" | tail -120 > $OUT/logcat.txt
ls -l $OUT
