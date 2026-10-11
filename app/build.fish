#!/usr/bin/env fish
# Compilacion manual en CachyOS/Arch, sin Gradle, usando el Android SDK del proyecto Camera2Probe.
set -l ROOT (cd (dirname (status filename)); and pwd)
set -l SDK /opt/android-sdk
if set -q ANDROID_HOME
    set SDK $ANDROID_HOME
end

set -l PLATFORM ''
for candidate in $SDK/platforms/android-37.0/android.jar $SDK/platforms/android-37/android.jar $SDK/platforms/android-36/android.jar $SDK/platforms/android-35/android.jar
    if test -f $candidate
        set PLATFORM $candidate
        break
    end
end

set -l BT ''
for candidate in $SDK/build-tools/37.0.0 $SDK/build-tools/36.0.0 $SDK/build-tools/35.0.0
    if test -x $candidate/aapt2; and test -x $candidate/d8; and test -x $candidate/apksigner; and test -x $candidate/zipalign
        set BT $candidate
        break
    end
end

if test -z "$PLATFORM"; or test -z "$BT"
    echo 'ERROR: No encuentro Android SDK completo.'
    echo "Busque en: $SDK"
    echo 'Se requiere android.jar (API 35+) y build-tools (35+).'
    exit 1
end

for x in javac keytool zip
    if not command -q $x
        echo "ERROR: Falta $x"
        exit 1
    end
end

# v0.6 elimina de forma explicita las clases antiguas de UID y JobScheduler.
# Necesario porque unzip -o NO borra archivos obsoletos de v0.3/v0.4.
for old in WhatsAppUidControl.java AutoDisable.java RetryJobService.java RetryJobs.java
    if test -f $ROOT/src/dev/deus/ellipticprobe/$old
        rm -f $ROOT/src/dev/deus/ellipticprobe/$old
    end
end

set -l BUILD $ROOT/build
set -l CLASSES $BUILD/classes
set -l DEX $BUILD/dex
set -l UNSIGNED $BUILD/base-unsigned.apk
set -l WITHDEX $BUILD/with-dex.apk
set -l ALIGNED $BUILD/aligned.apk
set -l FINAL $BUILD/EllipticGuard-v0.6.apk
set -l KEYSTORE $ROOT/debug.keystore

rm -rf $BUILD
mkdir -p $CLASSES $DEX

echo '[1/6] Compilando Java...'
javac -source 8 -target 8 -Xlint:-options \
    -classpath $PLATFORM \
    -d $CLASSES \
    $ROOT/src/dev/deus/ellipticprobe/*.java
or exit 1

echo '[2/6] Generando DEX...'
set -l CLASSFILES (find $CLASSES -type f -name '*.class')
$BT/d8 --lib $PLATFORM --min-api 31 --output $DEX $CLASSFILES
or exit 1

echo '[3/6] Compilando recursos e incluyendo el icono Deus Libidinis...'
set -l RESCOMPILED $BUILD/compiled-res
mkdir -p $RESCOMPILED
$BT/aapt2 compile --dir $ROOT/res -o $RESCOMPILED
or exit 1
set -l RESOURCE_ARGS
for f in (find $RESCOMPILED -type f -name '*.flat')
    set -a RESOURCE_ARGS -R $f
end
if test (count $RESOURCE_ARGS) -eq 0
    echo 'ERROR: No se encontraron recursos compilados'
    exit 1
end
$BT/aapt2 link -o $UNSIGNED -I $PLATFORM --manifest $ROOT/AndroidManifest.xml $RESOURCE_ARGS
or exit 1

echo '[4/6] Empaquetando DEX...'
cp $UNSIGNED $WITHDEX
zip -q -j $WITHDEX $DEX/classes.dex
or exit 1

echo '[5/6] Alineando APK...'
$BT/zipalign -f 4 $WITHDEX $ALIGNED
or exit 1

if not test -f $KEYSTORE
    echo '[clave] Generando firma local de pruebas...'
    keytool -genkeypair \
        -keystore $KEYSTORE \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -dname 'CN=Android Debug,O=Android,C=US' \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000
    or exit 1
end

echo '[6/6] Firmando y verificando...'
$BT/apksigner sign \
    --ks $KEYSTORE \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out $FINAL $ALIGNED
or exit 1
$BT/apksigner verify --verbose $FINAL
or exit 1

echo
printf 'APK construida: %s\n' $FINAL
echo 'Instala y concede DUMP desde la PC:'
printf 'adb install -r "%s"\n' $FINAL
echo 'adb shell pm grant dev.deus.ellipticprobe android.permission.DUMP'
echo 'adb shell am start -n dev.deus.ellipticprobe/.MainActivity'
