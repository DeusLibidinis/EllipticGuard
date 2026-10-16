# True automatic watchdog startup test (v0.6)

The v0.6 **already contains** a manifest `BootReceiver` for `android.intent.action.BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, calling `startForegroundService(GuardService)`. The active service is `START_STICKY`, retains a `SensorManager` listener and responds to screen state broadcasts. An autonomous start was observed on the initial POCO X6 Pro with v0.5 after reboot. This does not guarantee service restart after every app update or on other firmware builds.

## Protocol

1. In the app, enable **Vigilante persistente / autoinicio** and tap **INICIAR VIGILANTE AHORA** once. Confirm the foreground notification. Ensure `DUMP` is granted.
2. In HyperOS: enable app **Autoencendido en segundo plano**, allow notifications, and select unrestricted battery mode where available.
3. Record the time and reboot **normally**. Unlock once after boot. **Do not open the app or press its buttons.**
4. Look for the persistent **Elliptic Guard activo** notification after the first unlock. Wait 2-5 minutes; test proximity with a separate diagnostic app, then repeat a WhatsApp playback after a lock/unlock cycle.
5. Only **after** these external tests, open Elliptic Guard and use **VER REGISTRO DEL VIGILANTE**.

## Interpreting logs

- `RECEPTOR android.intent.action.BOOT_COMPLETED: iniciando servicio vigilante.`: the boot broadcast was delivered.
- v0.6 `VIGILANTE INICIADO` / candidate `VIGILANTE INICIALIZANDO`: monitoring initialization began, not proof that registration succeeded.
- Candidate `SERVICIO: FGS confirmado`: foreground promotion succeeded; `origen` identifies the initiating broadcast or sticky restart.
- `Listener Elliptic registrado y PERMANENTE`: the listener was registered.
- `DISABLE #1 ... exit=0`: the command process completed. **Not proof of sensor suppression.**
- `ERROR FGS`: Android/HyperOS rejected foreground-service startup.
- No boot receiver entry: investigate permissions, explicit disablement, first-unlock restrictions, and HyperOS autostart policy. Do not guess that a missing notification necessarily means failure.
- Receiver and active service but sensor working: investigate sensor reactivation and the watchdog instead of adding more BOOT_COMPLETED retries.

For additional read-only diagnostics with USB ADB, try:

```fish
adb shell pidof dev.deus.ellipticprobe
adb shell dumpsys activity services dev.deus.ellipticprobe
adb shell dumpsys package dev.deus.ellipticprobe | grep -E 'DUMP|BOOT_COMPLETED'
```

The first unlock can matter because this v0.6 receiver is **not** declared `directBootAware`, and its preferences are in credential-encrypted app storage. A production direct-boot design would require separate handling and explicit testing.

The notification should remain while the foreground service is running; success after one reboot should be followed by longer stability/battery tests before publishing a "works after reboot" claim.

For the candidate, receipt is logged as `RECEPTOR <action>: recibido` with version, autostart and DUMP status; a separate entry confirms the start request was submitted. See [Update test](UPDATE_TEST.md). The candidate remains non-directBootAware and requires the first unlock.
