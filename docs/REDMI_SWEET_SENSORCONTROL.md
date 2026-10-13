# Redmi Note 10 Pro (`sweet`): MIUI 14 SensorControl reverse engineering

**Status: static analysis only. No command that changes sensor state has been run on the Redmi.** This is not confirmation of device compatibility with Elliptic Guard.

## Device information supplied by the experimenter

| Field | Reported value |
|---|---|
| Model/codename | `M2101K6G` / `sweet` |
| SoC | Qualcomm (`ro.hardware=qcom`) |
| OS | Android 13 / API 33, MIUI `V14.0.8.0.TKFMIXM` |
| Proximity sensor | `Xiaomi Proximity`, vendor `Xiaomi`, `android.sensor.proximity(8)`, handle `0x0000002e` |
| Native components | `/system/lib64/libsensorservice.so` and `/system_ext/lib64/libmisensorserviceimpl.so` |

Native ELF files were supplied for local disassembly only and are **not stored in Git**:

- `libmisensorserviceimpl.so`: 238,560 bytes, ARM64, sha256 `99b2e57be47e2bdc6684407728afbf0eb8152c0d0ac897116d51cf0dc72e18f5`
- `libsensorservice.so`: 567,872 bytes, ARM64, sha256 `84f2ff9753450ddd3e79567754e14c6192bc29414b20103bdeb640413a9d219f`

## Findings from actual ARM64 disassembly

In this firmware, `SensorService::dump()` (`libsensorservice.so`, address `0x63708`) checks the subcommand `sensorsControl` and routes the argument vector to `MiSensorServiceStub::executeCommand()`, which delegates to `MiSensorServiceImpl::executeCommand()` in the vendor library.

The implementation at `0x26d50` reads a **decimal UID** from the third argument (via `atoi`); a guard checks its remainder modulo 100,000 against 10,001. It recognizes the following **second argument** strings: `stop`, `resume`, `update`.

| Native operation | Observed call path | Scope / caveat |
| --- | --- | --- |
| `stop` | `addControlApp(uid)` then `stopSensorsByUid(uid)` | Stops existing active sensor connections for the target **application UID**; not a sensor-handle operation |
| `resume` | `resumeSensorsByUid(uid)` then `removeControlApp(uid)` | Restores the target UID's connections and clears its control status |
| `update` | Parses fourth argument as comma-separated decimal sensor **types**, stores in `SortedVector<int>`, then calls `updateControlTypes(uid, types)` | Configuration for controlled types by UID. Literal `all` causes an empty list; interpretation in live runtime is not tested |

Important nuance: `stopSensorsByUid` calls `SensorDevice::setUidControlStateForConnection(connection, true)` for **all currently connected sensor connections belonging to the UID**, not only the proximity type. `appIsControlled(uid,type)` consults the control list and optional type map on subsequent checks, suggesting type-selective restrictions *for an application*, but the exact behavior on newly registered listeners has not been reproduced. Do not claim `update` alone globally disables proximity or that `stop` preserves other sensors.

The likely ADB syntax inferred from argument indexing is `dumpsys sensorservice sensorsControl <stop|resume|update> <decimal-uid> [sensor-type-list]`. This syntax has been **inferred from the binary only, not executed or functionally verified on the Redmi**.

AOSP's `cmd sensorservice set-uid-state` is a distinct service interface and must not be conflated with this vendor-specific extension.

## Conclusion and safe next phase

The POCO X6 Pro (`duchamp`, HyperOS 3) uses a global, handle-based sensor control path demonstrated with `dumpsys sensorservice disable 03000000`. This Redmi (`sweet`, MIUI 14) exposes a **UID-oriented** controller and the proximity handle is `0000002e`, not `03000000`. It is **not appropriate to install the current fixed-handle Elliptic Guard build to control the Redmi**.

Next phase, if a test is warranted:
1. Identify a disposable sensor-diagnostic app installed on the Redmi, including its actual UID (`adb shell cmd package list packages -U`); avoid system UID targets.
2. Record baseline proximity and accelerometer behavior in that test app.
3. Test only with a documented reversal and one known, non-system UID. `stop` may disable all sensors for that app, and `resume` is the intended vendor-side reversal. Do not test on telephony/system UIDs.
4. Confirm changes using independent sensor diagnostics and evaluate whether `update` provides genuine type-level selectivity; success cannot be inferred from a silent command or exit=0.
5. Keep `main` and current `duchamp` implementation unchanged.

**No proprietary blobs are distributed.** This report contains names, addresses, checksums, and independently derived control flow only.
