# Elliptic Guard

Experimental **rootless** proximity-sensor guard for a tested **POCO X6 Pro** firmware. Uses a Xiaomi-specific `dumpsys sensorservice disable 03000000` command after temporarily registering the Elliptic proximity sensor, with a foreground watchdog to counter later reactivations.

**Status:** `v0.6` experimental prototype. Manual and foreground watchdog operation have worked on one POCO X6 Pro in initial tests. **Automatic watchdog startup after reboot was observed on the original POCO X6 Pro with v0.5; startup after upgrading to v0.6 is not yet confirmed.** Do not interpret an exit code of `0` as proof of actual sensor suppression. No claim of support for all POCO X6 Pro firmware builds or any other device is made.

> [!WARNING]
> This is **not** an official Xiaomi, MediaTek, Elliptic Labs or WhatsApp project. Disabling proximity globally can prevent the screen from turning off when you hold the phone to your ear during normal calls. The watchdog runs a persistent foreground service and can use more power. Install only if you understand these trade-offs.

[Español](README.es.md) | [Installation & safety](docs/INSTALL.md) | [Autostart test](docs/AUTOSTART_TEST.md) | [Compatibility](docs/COMPATIBILITY.md) | [Research notes](docs/RESEARCH.md)

## What it does

- Locates an Android proximity sensor supplied by Elliptic Labs.
- Registers a continuous `SensorManager` listener in a foreground service.
- Executes the **vendor-specific** `dumpsys sensorservice disable 03000000` command via the Android `DUMP` development permission.
- Reapplies the command after display wake/unlock, when sensor events are received, and periodically while running.
- Logs execution and allows explicit stop / re-enable from the app.
- Does **not** require root, Shizuku, internet access, Accessibility access, or `MANAGE_SENSORS` for the verified control path.

## Requirements

- Tested target: POCO X6 Pro (`duchamp`), with an `Elliptic Proximity` sensor **handle `0x03000000`** and the relevant Xiaomi implementation.
- Android SDK API 35+ and Build Tools 35+ on Linux, plus `fish`, Java JDK and `zip` to build the prototype.
- A one-time ADB grant of `android.permission.DUMP`. This may not be allowed on other ROMs.
- Autostart permission / unrestricted battery policy in HyperOS if testing background functionality.

## Build and install

The current prototype builds with `app/build.fish` and a local Android SDK. **Preserve your own local `app/debug.keystore` across updates** so update signatures remain consistent; never upload it to GitHub.

```fish
cd app
fish build.fish
adb install -r build/EllipticGuard-v0.6.apk
adb shell pm grant dev.deus.ellipticprobe android.permission.DUMP
```

The application package remains `dev.deus.ellipticprobe` to support in-place upgrades from the research prototype.

## Contributing

Please open a [device compatibility report](.github/ISSUE_TEMPLATE/device-compatibility.yml) with non-sensitive firmware/sensor metadata. **Do not** publish personal messages, complete system logs containing user data, unique identifiers, proprietary `.so` files or signing keys.

The hardcoded handle and vendor command are intentionally **not** generalized yet. A sensor named "Elliptic" alone is not sufficient evidence that another device supports the same control command.

## License

Apache License 2.0, see [LICENSE](LICENSE). No proprietary Android vendor libraries are included.


### Project identity

The UI and launcher icon carry a **DL** signature (Deus Libidinis). A harmless easter egg
can be unlocked by tapping the shield or version label seven times. Android APK signing
remains a separate, cryptographic operation; use the same local keystore when upgrading.
