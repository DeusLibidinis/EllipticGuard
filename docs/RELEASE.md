# Preparar la primera beta instalable — Deus Libidinis

**Candidata:** `0.6.1-beta.1`, `versionCode=7`, paquete `dev.deus.ellipticprobe`.
No es una Release publicada ni un arreglo validado en hardware. El punto de
partida probado sigue siendo el descrito para v0.5/v0.6.

## Modos de compilación (Fish)

| Comando desde el repositorio | Firma / salida |
| --- | --- |
| `fish app/build.fish debug` | Clave local del prototipo; APK depurable en `app/build/debug/` |
| `fish app/build.fish release-unsigned` | Sin firma, no instalable; `debuggable=false` en `app/build/release-unsigned/` |
| `fish app/build.fish release` | Clave privada indicada por el autor; `debuggable=false` en `app/build/release/` |

El manifiesto fuente tiene `debuggable=false`. El modo debug genera una copia
depurable dentro de `build/debug/`; no modifica el manifiesto fuente. Los tres
modos usan la misma versión y generan un checksum SHA-256 junto al APK. Los
modos firmados verifican la firma y guardan `signature.txt`. No se promete
reproducibilidad bit a bit: las herramientas y marcas de tiempo ZIP aún deben
fijarse si se quiere ofrecer esa garantía.

## Firma de producción: solo en el equipo del autor

Si ya existe una clave de producción, reutilizarla. Para crear **la primera**,
una única vez, elegir contraseñas fuertes en los prompts de `keytool`:

```fish
mkdir -p "$HOME/.local/share/elliptic-guard-signing"
chmod 700 "$HOME/.local/share/elliptic-guard-signing"
# No ejecutar si ya existe esta clave.
keytool -genkeypair -keystore "$HOME/.local/share/elliptic-guard-signing/release.jks" \
    -alias ellipticguard -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Deus Libidinis, O=Elliptic Guard'
chmod 600 "$HOME/.local/share/elliptic-guard-signing/release.jks"
```

Respaldar de forma privada la clave y sus contraseñas antes de distribuir.
No subirlas al repositorio, adjuntarlas al chat ni utilizar `androiddebugkey`.
El script no crea una clave de producción automáticamente.

```fish
set -gx ELLIPTIC_RELEASE_KEYSTORE "$HOME/.local/share/elliptic-guard-signing/release.jks"
set -gx ELLIPTIC_RELEASE_ALIAS ellipticguard
fish app/build.fish release
# apksigner solicita las contraseñas, sin incluirlas en el historial.
cd app/build/release
sha256sum -c EllipticGuard-v0.6.1-beta.1-release.apk.sha256
```

Conservar la huella SHA-256 del certificado de `signature.txt` y publicarla
junto al checksum del archivo. Son valores distintos: el checksum identifica
el APK exacto, la huella identifica el firmante esperado para futuras versiones.
Repetir futuras compilaciones con esa misma clave.

## Migración desde el prototipo

Una firma de producción nueva no actualiza directamente una instalación debug
firmada con otra clave. Antes de desinstalar, completar la prueba de reemplazo
con la clave debug original y guardar los resultados. La desinstalación pierde
preferencias y DUMP; no usarla como supuesto arreglo del autoinicio.

Para una instalación inicial de la beta con nueva firma, documentar por
separado la retirada del prototipo y la configuración inicial:

```fish
adb install app/build/release/EllipticGuard-v0.6.1-beta.1-release.apk
adb shell pm grant dev.deus.ellipticprobe android.permission.DUMP
```

Después abrir la aplicación y configurar notificaciones/autoinicio/batería.
Esta instalación inicial **no acredita** recuperación tras reemplazar una APK.
Antes de distribuir, comprobar también un reemplazo firmado con la clave de
producción (puede reinstalarse la misma APK con `adb install -r`).

## Condiciones para publicar

- Compilación completa, alineación, firma y checksum correctos; `debuggable=false`
  comprobado en el manifiesto empaquetado mediante `aapt2 dump xmltree`.
- [Actualización real](UPDATE_TEST.md) con autoinicio activo y control negativo
  desactivado; confirmar servicio y efecto externo después de bloquear/desbloquear.
- Reinicio de la **candidata**, primer desbloqueo sin abrir UI y vigilancia efectiva.
- Registro de firmware exacto y resultados de estabilidad/batería 24–48 h. Si la
  beta se publica antes, describir expresamente esa validación como pendiente.
- Clave de producción respaldada, huella revisada y conservada para actualizar.
- Revisar el diff/archivos: sin keystores, capturas privadas ni `.so` propietarios.

Subir únicamente APK firmado, su `.sha256` y huella pública del certificado.
Marcar la Release como **prerelease**. No añadir una etiqueta de Release a
`main` hasta que el código candidato esté incorporado y se haya revisado la
validación. No hay automatización de firma ni secretos GitHub añadidos.

## Borrador de notas de Release

> Elliptic Guard, por Deus Libidinis — candidata beta experimental.
>
> Añade trazabilidad del reinicio tras actualizar, diferenciando recepción del
> evento, solicitud de inicio, promoción a primer plano y registro del listener.
> Conserva el vigilante y el control selectivo de v0.6; mantiene paquete,
> preferencias e identidad DL. El código adapta las llamadas de servicio y
> receptor a Android 12/13, sin afirmar compatibilidad de esos dispositivos.
>
> Requiere permiso DUMP concedido inicialmente por ADB. Compatibilidad de sensor
> y comando limitada a la evidencia documentada; el segundo POCO sigue pendiente.
> La desactivación es global y puede afectar el apagado de pantalla en llamadas.
> Consumo de batería y recuperación automática: indicar aquí los resultados
> reales obtenidos antes de publicar; no convertir pendientes en éxitos.
