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
n=10
for L in garage center main modes tesla drive classic; do
  go mycar-dash:$L 9; gps 35; sleep 3; shot ${n}_$L; n=$((n+1))
done
go "mycar-sayara:sedan:4" 5; go mycar-dash:garage 8; shot 17_garage_red
go mycar-dash:tesla 8; shot 18_tesla_red; go "mycar-sayara:suv:0" 5
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
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|MyCar|Dash" | tail -120 > $OUT/logcat.txt
ls -l $OUT
