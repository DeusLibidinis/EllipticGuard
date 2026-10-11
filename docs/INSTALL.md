# Installation and practical safety

## Experimental requirements

1. Confirm that Android lists **Elliptic Proximity** with **handle `0x03000000`**. Device name alone is insufficient.
2. Build the prototype from `app/` using `fish build.fish`; install with `adb install -r`.
3. Grant `android.permission.DUMP` once via `adb shell pm grant dev.deus.ellipticprobe android.permission.DUMP`.
4. Allow notifications; enable HyperOS **Auto-start / Background autostart** for Elliptic Guard; set battery usage to **Unrestricted** where available.
5. Open the app and select **INICIAR VIGILANTE AHORA**. Check for its persistent notification. Verify with an independent proximity diagnostic app.

**Manual recovery:** In the app select **DETENER VIGILANTE Y REACTIVAR Elliptic**. If using an authorized ADB shell, try `adb shell dumpsys sensorservice enable 03000000` and validate the result. Restarting the device may clear temporary native sensor state, but do not rely on this as a guaranteed fix for every firmware build.

## Known risks

- Global loss of automatic proximity screen-off behavior during voice calls.
- Additional battery consumption from the foreground listener and watchdog.
- ROM/firmware-specific command behavior; the command can return zero without having the intended hardware effect.
- A service running after a reboot is different from a one-shot BOOT_COMPLETED command. Validate the persistent notification and service logs.
- Granting `DUMP` exposes diagnostic access to the app. Install only trusted builds and revoke permission/uninstall if no longer needed.

Never include a debug signing key, proprietary ROM binaries, full user logs or credentials in compatibility reports.
