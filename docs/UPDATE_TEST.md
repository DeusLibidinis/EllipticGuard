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



## Observación de uso continuado: ~24 horas (2026-10-12)

El autor informa que la versión `0.6.1-beta.2` funcionó aparentemente bien durante aproximadamente 24 horas de uso habitual en su **POCO X6 Pro**, sin reaparición reportada del problema de proximidad. La captura de la pantalla de batería de HyperOS muestra **Elliptic Guard: 0.97 %**; el denominador de ese porcentaje no quedó verificado y **no debe describirse** como una pérdida directa de 0.97 puntos de carga.

**Nivel de evidencia:** prueba de usuario positiva, pero todavía no se ha recibido un diagnóstico final `estabilidad24h` con `dumpsys activity services` ni registro de eventos, y la captura por sí sola no valida la desactivación del sensor en ese momento. No se han medido rigurosamente los mAh, la tasa de wakelocks o la continuidad del servicio en todas las horas. No equivale a certificación general ni a compatibilidad con la segunda unidad.

Para cerrar esta etapa: capturar `fish scripts/collect-update-evidence.fish estabilidad24h` sin reiniciar manualmente el vigilante; confirmar `isForeground=true` y eficacia en diagnóstico de proximidad/WhatsApp, además de revisar la pantalla de consumo con el intervalo de medida visible. El fallo de instalación gráfica de la beta.1 en el dispositivo de la pareja queda pendiente de obtener mensaje de ADB.


## Diagnóstico adjunto de estabilidad (2026-10-12 02:12 CST)

El autor proporcionó el ZIP local `20261012-021210-estabilidad24h.zip` generado por `scripts/collect-update-evidence.fish estabilidad24h`. Se inspeccionaron `service.txt`, `package-summary.txt`, `guard-logcat.txt`, `guard-preferences.txt`, `device-time.txt`, `model.txt`, `codename.txt` y `firmware.txt`. No se incorporó al repositorio el ZIP ni la copia íntegra de preferencias/logs.

**Resultado observado en el dispositivo original:**

- Hora del dispositivo al capturar: `2026-10-12 02:12:10 CST`.
- Modelo `2311DRK48G`, codename `duchamp`, firmware `OS3.0.9.0.WNLMIXM` con Android 16.
- APK instalada: `versionName=0.6.1-beta.2`, `versionCode=8`, `targetSdk=35`.
- `GuardService` activo en `dumpsys activity services`, con `isForeground=true`, `foregroundId=3305`, notificación ongoing/no-clear y proceso vivo al capturar.
- `createTime=-12h57m46s` acredita ~12 h 58 min de existencia del registro de servicio actual, **no 24 h continuas del mismo proceso**.
- Permisos `DUMP`, `RECEIVE_BOOT_COMPLETED` y `POST_NOTIFICATIONS` concedidos; preferencia `boot_auto_enabled=true`.
- Último registro: `02:11:41 DISABLE #9670 (WATCHDOG_ON): exit=0; salida vacia; eventos=1`. También `#9622`, `#9634`, `#9646`, `#9658` registrados a intervalos de aproximadamente 48 s; los índices suben en 12 por el registro espaciado de acciones. El contador muestra **9.670 intentos en la instancia actual**, no 9.670 activaciones reales del sensor ni garantía de que todos fueran efectivos.
- Las 302 líneas retenidas en `guard-preferences.txt` se extienden de `2026-10-11 23:00:44` a `2026-10-12 02:11:41`, con eventos de pantalla y comandos `exit=0`, sin errores en el tramo conservado. El registro es circular/truncado; no permite descartar fallos fuera de ese intervalo.
- El autor reportó funcionamiento correcto en uso normal durante ~24 h, sin reaparición del fallo de proximidad. La pantalla de batería mostraba `0.97 %` para Elliptic Guard, sin confirmación de cómo HyperOS define ese porcentaje ni medición aislada de mAh.

**Conclusión:** se supera el punto de control funcional comunicado por el usuario y se acredita un vigilante activo con FGS y comandos periódicos al final de la observación. La prueba no constituye garantía de 24 h ininterrumpidas de *un único proceso*, validación experimental de cada `disable`, ni medida precisa de coste energético. La tasa de intentos aconseja analizar consumo y reducir comprobaciones solo después de disponer de comparativas con la estrategia funcional actual.

**Pendientes:** confirmar en diagnóstico externo y WhatsApp al final del ciclo, contrastar consumo con periodos equivalentes, validar ciclo de instalación y permiso DUMP en una segunda unidad POCO X6 Pro (su instalación gráfica beta.1 falló por motivo desconocido), verificar visualmente selector bilingüe y decidir firma de publicación. Mantener PR en borrador.

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
