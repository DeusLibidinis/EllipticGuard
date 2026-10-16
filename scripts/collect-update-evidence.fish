#!/usr/bin/env fish
# Solo lectura. No abre la UI, no inicia el servicio y no modifica permisos.
set -l ROOT (cd (dirname (status filename))/..; and pwd)
set -l LABEL evidencia
if test (count $argv) -gt 1
    echo 'Uso: fish scripts/collect-update-evidence.fish [antes|despues|reinicio]'
    exit 1
end
if test (count $argv) -eq 1
    set LABEL $argv[1]
end
if not string match -qr '^[a-zA-Z0-9_-]+$' -- $LABEL
    echo 'Etiqueta invalida.'
    exit 1
end
if not command -q adb
    echo 'Falta adb en PATH.'
    exit 1
end
adb get-state > /dev/null
or exit 1
set -l OUT $ROOT/diagnostics/(date '+%Y%m%d-%H%M%S')-$LABEL
mkdir -p $OUT
or exit 1
adb shell date > $OUT/device-time.txt
adb shell getprop ro.product.model > $OUT/model.txt
adb shell getprop ro.product.device > $OUT/codename.txt
adb shell getprop ro.build.fingerprint > $OUT/firmware.txt
adb shell dumpsys activity services dev.deus.ellipticprobe > $OUT/service.txt
adb shell dumpsys package dev.deus.ellipticprobe | string match -r '.*(versionCode=|versionName=|DUMP|POST_NOTIFICATIONS|stopped=|enabled=|MY_PACKAGE_REPLACED|BOOT_COMPLETED).*' > $OUT/package-summary.txt
adb logcat -d -v threadtime -s EllipticGuard:I '*:S' > $OUT/guard-logcat.txt
# Solo funciona en la candidata debug. En release deja la negativa de run-as.
adb shell run-as dev.deus.ellipticprobe cat shared_prefs/elliptic_boot_settings.xml > $OUT/guard-preferences.txt 2>&1
printf 'Captura local: %s\n' $OUT
echo 'No certifica el estado del sensor. Revisa los archivos antes de compartir extractos.'
