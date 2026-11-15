#!/bin/bash
# My Car: يثبّت نسخة التجربة على محاكي 1024x600 ويلتقط صوراً للصفحات الجديدة، ويجمع أي أعطال (FATAL) من logcat.
PKG=com.feras.mycar.debug
ACT=$PKG/com.stradaos.launcher.Main
OUT=salida; mkdir -p $OUT
adb wait-for-device
for i in $(seq 1 100); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
sleep 15
adb shell input keyevent 82 || true
APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
adb install -r -g "$APK" | tail -1
# موقع دير الزور
adb emu geo fix 40.1408 35.3359 || true
adb shell appops set $PKG android:mock_location allow >/dev/null 2>&1 || true
adb shell am start -n $ACT --es prueba saltar >/dev/null; sleep 25
adb emu geo fix 40.1408 35.3359 || true
shot() { adb shell screencap -p /data/local/tmp/s.png && adb pull /data/local/tmp/s.png $OUT/$1.png >/dev/null; echo "shot $1"; }
go() { adb shell am start -n $ACT --es prueba "$1" >/dev/null; sleep "${2:-6}"; }
back() { adb shell input keyevent 4; sleep 3; }
shot 01_home
go mycar-salat 8; shot 02_prayer; back
go mycar-radio 20; shot 03_radio
adb shell input tap 700 300; sleep 15; shot 04_radio_play
back
go mycar-obd 6; shot 05_obd
back; back
go ajustes:11 6; shot 06_settings_syria
go ajustes:0 4
go mycar-tema:3 8; shot 07_theme_desert
go mycar-tema:2 8; shot 08_theme_drive
go "mycar-sayara:taxi:9" 6; shot 09_taxi
go "mycar-sayara:sedan:4" 6; go mycar-tema:6 8; shot 10_city_sedan
go mycar-tema:8 8; shot 11_day
go mycar-tema:0 4; go "mycar-sayara:suv:0" 4
go mycar-lang-en 8; shot 12_home_en
go mycar-salat 8; shot 13_prayer_en; back
go mycar-lang-ar 6
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|StradaOS|MyCar" | tail -80 > $OUT/logcat.txt
ls -l $OUT
