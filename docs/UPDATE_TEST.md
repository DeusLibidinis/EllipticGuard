# Actualización: prueba pendiente y diagnóstico (candidata 0.6.1-beta.1)

La base pública v0.6 es `6f598d3`. Su receptor ya escucha
`MY_PACKAGE_REPLACED`; no falta añadir la acción al manifiesto. La candidata
no declara arreglado el fallo observado: añade evidencia para distinguir una
entrega ausente, autoinicio desactivado, rechazo de inicio y fallo del listener.
No añade temporizadores, JobScheduler ni nuevos permisos. Mantiene 4 s / 30 s,
las ráfagas y el handle probado.

Android incluye `MY_PACKAGE_REPLACED` entre las excepciones para iniciar un
foreground service desde segundo plano. Es una emisión protegida enviada por
el sistema a la aplicación reemplazada. No cambiar `exported=false` ni simular
la prueba con `am broadcast`: hay que reemplazar realmente la APK.

- [Restricciones de inicio](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Contrato del intent](https://developer.android.com/reference/android/content/Intent#ACTION_MY_PACKAGE_REPLACED)

## Una prueba de reemplazo real, conservando la instalación

No reinstalar desde cero, hacer force-stop, revocar/conceder DUMP ni abrir la
interfaz después del reemplazo: esas acciones cambian lo que estamos midiendo.
Antes de empezar, el guardia debe estar activo y el autoinicio habilitado.
Conservar los ajustes HyperOS que ya funcionaron. No hace falta repetir la
investigación de SensorService.

Desde el repositorio, en CachyOS/Fish:

```fish
# Debe ser la clave EXACTA que firmó la APK actualmente instalada.
# Esta ruta es la del prototipo original: úsala solo si conservas allí la clave.
set -gx ELLIPTIC_DEBUG_KEYSTORE "$HOME/Descargas/EllipticDUMPProbe/debug.keystore"
fish app/build.fish debug
fish scripts/collect-update-evidence.fish antes
adb install -r app/build/debug/EllipticGuard-v0.6.1-beta.1-debug.apk
# Sin tocar el icono de la aplicación; esperar unos 15-30 segundos.
fish scripts/collect-update-evidence.fish despues
```

Si la clave original está en `app/debug.keystore`, omitir la variable y usar esa
copia. Un error `INSTALL_FAILED_UPDATE_INCOMPATIBLE` exige localizar la clave
correcta; **no desinstalar para completar esta prueba**. No compartir la clave.

1. Comprobar `service.txt`: `GuardService` e `isForeground=true`.
2. Revisar las entradas nuevas con versión `0.6.1-beta.1 (7)` y hora posterior a
   la instalación. `RECEPTOR ...MY_PACKAGE_REPLACED: recibido` acredita entrega;
   `autoinicio=false` explica una omisión voluntaria.
3. `solicitud enviada` no demuestra arranque. `SERVICIO: FGS confirmado` muestra
   promoción a primer plano; `origen=...MY_PACKAGE_REPLACED` la vincula al evento.
   `Listener Elliptic registrado y PERMANENTE` confirma registro.
4. Probar proximidad con el diagnóstico independiente y un audio de WhatsApp;
   bloquear/desbloquear y repetir. Anotar el resultado externo, no deducirlo de
   `exit=0`, ausencia de eventos ni una notificación.
5. Si falta el servicio, capturar de nuevo a los dos minutos sin abrir la app.
   Si faltan entradas, consultar `guard-preferences.txt` (solo debug) antes de
   atribuirlo a HyperOS; logcat puede haber rotado. Después se puede abrir la UI
   para copiar el registro, anotando esa hora.

| Evidencia | Interpretación / siguiente paso |
| --- | --- |
| No hay recepción en log persistente | Revisar estado stopped/disabled, entrega del sistema y política HyperOS; causa no confirmada |
| Recepción con autoinicio=false | Omisión intencionada; no activar por detrás del usuario |
| `ERROR RECEPTOR` | La petición fue rechazada; conservar clase y mensaje de excepción |
| Solicitud enviada, sin `FGS confirmado` | Fallo entre creación/promoción; buscar `ERROR FGS` y errores del proceso en el dispositivo |
| FGS confirmado, error DUMP o listener | Resolver el requisito exacto, no añadir reintentos de BOOT |
| FGS y listener correctos, proximidad activa | Investigar vigilancia/HAL; no es un fallo de entrega de actualización |


## Resultado observado en el POCO X6 Pro original: beta.2 (2026-10-11)

**Recuperación del servicio tras una actualización instalada con `adb install -r`: CONFIRMADA en una unidad.**
La beta.2 (`versionCode=8`) fue instalada sobre la APK anterior con **certificados SHA-256 idénticos**, sin desinstalar ni abrir la interfaz después. Los diagnósticos independientes mostraron:

- `00:44:06.890`: `RECEPTOR android.intent.action.MY_PACKAGE_REPLACED: recibido; version=0.6.1-beta.2 (8); autoinicio=true; DUMP=true`.
- `00:44:07.210`: `SERVICIO: FGS confirmado; version=0.6.1-beta.2 (8); origen=android.intent.action.MY_PACKAGE_REPLACED`.
- `00:44:07.255`: `Listener Elliptic registrado y PERMANENTE`.
- `00:44:09`, `00:44:11`, `00:44:15`, `00:44:19` y `00:45:07`: comandos de vigilancia con `exit=0` y registro de intentos.
- `dumpsys activity services` mostró `GuardService` con `isForeground=true` y `foregroundId=3305`. Permisos `DUMP` y notificaciones concedidos.

**Alcance preciso:** esta evidencia confirma la entrega del evento y la recuperación autónoma del servicio con el listener y las invocaciones `dumpsys`; **NO demuestra todavía** el efecto real sobre Elliptic tras el reemplazo, estabilidad 24–48 h, consumo energético, recuperación en todos los reemplazos o compatibilidad en otros dispositivos. Validar proximidad con una aplicación independiente y WhatsApp, incluyendo bloqueo/desbloqueo. Conservamos el PR en borrador hasta las comprobaciones pendientes.

**Precaución al usar el script en CachyOS:** `ls` puede ser alias de `eza`. Evitar `ls -dt diagnostics/*-despues` si da `invalid value ... for --time`. Usar la ruta que imprime el propio script, p. ej. `set DIR ~/Descargas/EllipticGuard-GitHub/diagnostics/20261011-004529-despues`, y consultar `$DIR/service.txt` y `$DIR/guard-logcat.txt`.


## Validación que sigue pendiente

- Autoinicio **v0.6** en frío sigue sin demostrarse: el éxito histórico fue v0.5.
  Un reinicio con la candidata valida la candidata, no retroactivamente v0.6.
- Reinicio de la candidata, primer desbloqueo sin abrir UI y captura `reinicio`;
  usar el protocolo de [autoinicio](AUTOSTART_TEST.md).
- Control negativo: desactivar vigilancia, reemplazar de nuevo la misma APK con
  la misma firma y confirmar que no se inicia. No repetir este control hasta
  haber capturado el caso principal.
- Estabilidad 24–48 h y segunda unidad: [compatibilidad](COMPATIBILITY.md).

Las capturas se quedan en `diagnostics/`, ignorado por Git. El script no obtiene
IMEI/serial, mensajes ni un logcat global. Revisar igualmente el contenido y
compartir solo los extractos necesarios.
