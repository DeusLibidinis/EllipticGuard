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
