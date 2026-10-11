#!/usr/bin/env fish
# CachyOS/Arch, sin Gradle. Las claves de produccion pertenecen al autor.
set -l ROOT (cd (dirname (status filename)); and pwd)
set -l MODE debug
if test (count $argv) -gt 1
    echo 'Uso: fish build.fish [debug|release|release-unsigned]'
    exit 1
end
if test (count $argv) -eq 1
    set MODE $argv[1]
end
if not contains -- $MODE debug release release-unsigned
    echo 'Uso: fish build.fish [debug|release|release-unsigned]'
    exit 1
end
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
    echo "ERROR: Se requiere SDK API 35+ y Build Tools 35+ en $SDK (o ANDROID_HOME)."
    exit 1
end
for x in javac keytool zip sha256sum
    if not command -q $x
        echo "ERROR: Falta $x"
        exit 1
    end
end
set -l KEYSTORE $ROOT/debug.keystore
set -l KEY_ALIAS androiddebugkey
if test "$MODE" = debug; and set -q ELLIPTIC_DEBUG_KEYSTORE
    set KEYSTORE $ELLIPTIC_DEBUG_KEYSTORE
    if not test -f "$KEYSTORE"
        echo 'ERROR: ELLIPTIC_DEBUG_KEYSTORE debe apuntar a la clave existente del prototipo.'
        exit 1
    end
end
if test "$MODE" = release
    if not set -q ELLIPTIC_RELEASE_KEYSTORE; or not set -q ELLIPTIC_RELEASE_ALIAS
        echo 'ERROR: Define ELLIPTIC_RELEASE_KEYSTORE y ELLIPTIC_RELEASE_ALIAS. No se genera una clave de produccion automaticamente.'
        exit 1
    end
    set KEYSTORE $ELLIPTIC_RELEASE_KEYSTORE
    set KEY_ALIAS $ELLIPTIC_RELEASE_ALIAS
    if not test -f "$KEYSTORE"; or test -z "$KEY_ALIAS"; or test "$KEY_ALIAS" = androiddebugkey
        echo 'ERROR: Se requiere una clave de produccion existente y alias distinto de androiddebugkey.'
        exit 1
    end
end
# No borrar fuentes durante una compilacion; rechazar restos del prototipo anterior.
for old in WhatsAppUidControl.java AutoDisable.java RetryJobService.java RetryJobs.java
    if test -f $ROOT/src/dev/deus/ellipticprobe/$old
        echo "ERROR: Fuente obsoleta $old. Usa un checkout limpio del repositorio."
        exit 1
    end
end
set -l VERSION (string match -r 'android:versionName="([^"]+)"' < $ROOT/AndroidManifest.xml)[2]
if test -z "$VERSION"
    echo 'ERROR: versionName ausente.'
    exit 1
end
set -l BUILD $ROOT/build/$MODE
set -l CLASSES $BUILD/classes
set -l DEX $BUILD/dex
set -l UNSIGNED $BUILD/base-unsigned.apk
set -l WITHDEX $BUILD/with-dex.apk
set -l ALIGNED $BUILD/aligned.apk
set -l FINAL $BUILD/EllipticGuard-v$VERSION-$MODE.apk
rm -rf -- $BUILD
mkdir -p $CLASSES $DEX
or exit 1
set -l MANIFEST $BUILD/AndroidManifest.xml
cp $ROOT/AndroidManifest.xml $MANIFEST
or exit 1
if test "$MODE" = debug
    string replace 'android:debuggable="false"' 'android:debuggable="true"' < $ROOT/AndroidManifest.xml > $MANIFEST
end

echo '[1/6] Compilando Java...'
javac -source 8 -target 8 -Xlint:-options -classpath $PLATFORM -d $CLASSES $ROOT/src/dev/deus/ellipticprobe/*.java
or exit 1

echo '[2/6] Generando DEX...'
set -l CLASSFILES (find $CLASSES -type f -name '*.class' | sort)
$BT/d8 --lib $PLATFORM --min-api 31 --output $DEX $CLASSFILES
or exit 1

echo '[3/6] Compilando recursos Deus Libidinis...'
set -l RESCOMPILED $BUILD/compiled-res
mkdir -p $RESCOMPILED
$BT/aapt2 compile --dir $ROOT/res -o $RESCOMPILED
or exit 1
set -l RESOURCE_ARGS
for f in (find $RESCOMPILED -type f -name '*.flat' | sort)
    set -a RESOURCE_ARGS -R $f
end
if test (count $RESOURCE_ARGS) -eq 0
    echo 'ERROR: No se encontraron recursos compilados.'
    exit 1
end
$BT/aapt2 link -o $UNSIGNED -I $PLATFORM --manifest $MANIFEST $RESOURCE_ARGS
or exit 1

echo '[4/6] Empaquetando DEX...'
cp $UNSIGNED $WITHDEX
or exit 1
zip -q -j $WITHDEX $DEX/classes.dex
or exit 1

echo '[5/6] Alineando APK...'
$BT/zipalign -f 4 $WITHDEX $ALIGNED
or exit 1
$BT/zipalign -c 4 $ALIGNED
or exit 1

if test "$MODE" = release-unsigned
    cp $ALIGNED $FINAL
    or exit 1
    echo '[6/6] APK SIN FIRMA para validacion de compilacion; NO instalable ni publicable como Release.'
else
    if test "$MODE" = debug
        if not test -f "$KEYSTORE"
            echo '[clave] Creando clave LOCAL de pruebas. Para actualizar v0.6 usa su keystore original.'
            keytool -genkeypair -keystore $KEYSTORE -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
            or exit 1
        end
        $BT/apksigner sign --ks $KEYSTORE --ks-key-alias $KEY_ALIAS --ks-pass pass:android --key-pass pass:android --out $FINAL $ALIGNED
        or exit 1
    else
        echo '[6/6] Firma privada: apksigner solicitara las contrasenas en la terminal.'
        $BT/apksigner sign --ks $KEYSTORE --ks-key-alias $KEY_ALIAS --out $FINAL $ALIGNED
        or exit 1
    end
    $BT/apksigner verify --verbose --print-certs $FINAL > $BUILD/signature.txt
    or exit 1
    if test "$MODE" = release; and string match -qi '*CN=Android Debug*' < $BUILD/signature.txt
        rm -f -- $FINAL
        echo 'ERROR: Firma Android Debug rechazada para Release.'
        exit 1
    end
end
# Relativo al directorio del APK para poder verificar con sha256sum -c.
set -l PREVIOUS_DIR $PWD
cd $BUILD
or exit 1
sha256sum (basename $FINAL) > (basename $FINAL).sha256
or exit 1
cd $PREVIOUS_DIR
or exit 1
printf '\nAPK: %s\n' $FINAL
if test "$MODE" != release-unsigned
    printf 'adb install -r "%s"\n' $FINAL
    echo 'Prueba de actualizacion: NO abrir la app ni iniciar el servicio manualmente.'
end
