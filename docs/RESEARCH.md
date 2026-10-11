# Investigation notes: Xiaomi SensorService and Elliptic Proximity

This document records **device-specific observations**, not a universal Android API.

## Hardware/software observed

- Test phone: POCO X6 Pro (`duchamp`), MediaTek platform, HyperOS.
- Elliptic Labs proximity sensor exposed to Android as `TYPE_PROXIMITY` (8), reported handle `0x03000000`, 0 for near and about 5 for far.
- `com.whatsapp` could register the sensor and trigger unwanted routing/screen changes.

## Native investigation

The test firmware contains `/system/lib64/libsensorservice.so` and `/system_ext/lib64/libmisensorserviceimpl.so` with Xiaomi extensions. Symbols observed include `MiSensorServiceImpl::setSensorState`, `SensorControl::setSensorState`, `SensorControl::isSensorDisable`, and `SensorControl::executeCommand`.

The discovered, reversible device-specific command is:

```sh
adb shell dumpsys sensorservice disable 03000000
adb shell dumpsys sensorservice enable 03000000
```

In the test firmware, these route through a vendor extension that parses a hexadecimal handle and ultimately attempts a sensor activation change. This is **not** standard Android sensor management. Check source firmware and permission constraints before experimenting on another device; invalid handles must not be guessed.

## Reproduced behavior

- An ADB shell command was effective when the sensor had an activation record (e.g., a diagnostic app registered it).
- `android.permission.DUMP` granted to the Android APK permitted the same `dumpsys` process path without root or Shizuku on the test device.
- A single disable command was **not persistent across reboot**, and proximity behavior could return after screen lock/unlock while WhatsApp was active.
- v0.6 registers the sensor and runs a foreground watchdog; initial tests through two lock/unlock cycles were successful. Extended endurance, after-reboot automatic service startup and second-device compatibility remain unproven.
- A separate `cmd sensorservice set-uid-state` path was tested from ADB shell, but the APK's UID could not invoke it (permission checks). It was removed from the app.

## Responsible reverse engineering

We are documenting method names, commands and independently observed behavior; **no proprietary `.so` binaries are distributed**. Firmware update changes or vendor SELinux/permission differences may invalidate the findings.
