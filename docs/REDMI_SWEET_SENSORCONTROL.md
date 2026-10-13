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


## First live UID-control experiment: independently observed

Test application: `io.kodular.borekbandell.Proximity_sensor_test`, user-installed app UID **10288**. Baseline showed a registered, active `Xiaomi Proximity` connection (`Y1.d | uid 10288`, handle `0x0000002e`). The CIT proximity diagnostic separately responded 5.0 (far) and 0.0 (near).

Sequence reported by experimenter:

1. `adb shell dumpsys sensorservice sensorsControl stop 10288` produced no textual output; proximity readings in the third-party diagnostic stopped updating.
2. Follow-up `dumpsys sensorservice` showed UID **10288** added among `controlled apps`. The existing connection remained listed as `status: active`, which does not prove that sensor events were still being delivered.
3. `adb shell dumpsys sensorservice sensorsControl resume 10288` was run, followed by a **second** `stop 10288`.
4. When the experimenter left the diagnostic test screen and returned, proximity readings worked again **without another resume**. No `controlled apps` / active-connection dump was captured after reopening, so persistence of the control list across the recreation is **not established**.

Native `stopSensorsByUid` iterates current active sensor connections matching the UID and calls `SensorDevice::setUidControlStateForConnection(connection, true)`; `resumeSensorsByUid` calls it with `false`. The current experiment is consistent with a connection-scoped stop that can be bypassed by reopening the diagnostic screen / recreating subscriptions. More evidence is needed before explaining the exact cause.

**Verified at this stage:** one temporary, effective UID-specific suspension of proximity events in an app that was registered to proximity; `controlled apps` dump change.

**Not verified:** persistent suspension across listener reconnection, sensor-type-8-only isolation, other sensors unaffected for the same UID, WhatsApp behavior, or Android APK ability to invoke this vendor command without shell privileges.

**Immediate cleanup after the second stop:** run `dumpsys sensorservice sensorsControl resume 10288` and verify 10288 is no longer a controlled app before future experiments.

Potential next test (only after cleanup): use a disposable app that monitors *both* proximity and accelerometer to test `sensorsControl update 10288 8` followed by `stop`, with explicit `resume` and `update 10288 all` reversal. Treat `update` type-list semantics as unverified, do not assume it limits the initial `stop` to type 8.

## Follow-up: `update 10288 8` + `stop 10288` did not interrupt proximity

Experimenter reports that proximity remained operational after running `dumpsys sensorservice sensorsControl update 10288 8` followed by `... stop 10288`. **This attempt is a negative functional result.** No contemporaneous `controlled apps` dump was supplied, so the cause is not established.

Closer inspection of the ARM64 implementation at `0x26ef4–0x26f40` revealed an important guard: the `stop` handler calls `SortedVectorImpl::indexOf(uid)` on the controlled-UID list and **skips both `addControlApp` and `stopSensorsByUid` if the UID is already present** (`tbz x24, #63, 0x270c4`). Thus repeated `stop` requests are not idempotent "resuspend" operations for newly created sensor connections.

The `update` handler at `0x26fb0–0x2708c` only parses decimal sensor-type values and invokes `updateControlTypes(uid,types)`. It does not call `stopSensorsByUid` or force refresh of existing connections. Consequently, a stale controlled UID left after the preceding trial, combined with a new active subscription, is a plausible explanation for the follow-up observation. This remains **hypothesis**, not confirmed without a before/after dump.

A reproducible next trial needs **explicit cleanup first**, `resume 10288` and `update 10288 all`, plus verification that UID 10288 is absent from `controlled apps` before separately testing update and stop. Regardless of result, restore with the same commands and verify working proximity. Type-selective control across multiple sensors is still **unproven**.

## Clean-state retest: `update 10288 8` then `stop 10288` successfully stopped proximity

New user-observed sequence on Redmi `sweet`, with terminal screenshot:

1. `dumpsys sensorservice sensorsControl resume 10288`, then `... update 10288 all` to clear the previous experiment.
2. The `controlled apps` list was searched for `10288`: **absent**.
3. `dumpsys sensorservice sensorsControl update 10288 8`.
4. A second search showed `10288` **still absent**: `update` by itself did not register the UID as controlled.
5. `dumpsys sensorservice sensorsControl stop 10288`.
6. The experimenter confirms the test app **stopped receiving proximity changes**.
7. `... resume 10288` and `... update 10288 all` were executed to restore state.

This is a **positive functional stop following explicit UID cleanup**, supporting (not proving) the preceding hypothesis that already-controlled-UID state was preventing a repeat `stop` operation. No `controlled apps` dump was captured *after* this successful `stop`; it is not correct to claim that the post-stop UID state was directly measured in this retest. No sensor readings after cleanup were supplied.

**Not yet demonstrated:** whether `update 10288 8` affects type-level selection, whether a second sensor belonging to the *same app UID* stays active, whether a recreated sensor listener stays blocked, or whether the commands can be issued from a non-shell Android app. Since `stop 10288` alone had succeeded in the initial clean experiment, the value added by `update 10288 8` remains **unproven**.

Next safe trial should isolate application re-registration (with explicit before/after controlled-UID snapshots), then type-level isolation in a test app that exposes at least two sensor types; record and restore a pristine UID state after each trial. Neither current POCO code nor the default GitHub branch has been changed.

## Confirmed restoration of an effective stop with a `resume` → `stop` cycle

On Redmi Note 10 Pro (`sweet`, MIUI 14), the experimenter independently confirmed this sequence with the third-party proximity diagnostic (`io.kodular.borekbandell.Proximity_sensor_test`, UID `10288`):

1. An initial `sensorsControl stop 10288` interrupted proximity sensor events.
2. Leaving and re-entering the diagnostic screen restored proximity events **without** an explicit `resume`.
3. A post-reentry `dumpsys sensorservice` capture showed **UID 10288 remained among `controlled apps`**, with a `Xiaomi Proximity 0x0000002e | status: active` connection associated with UID 10288. A still-registered connection is not in itself proof of event delivery; the operator also observed functioning proximity readings.
4. Running `dumpsys sensorservice sensorsControl resume 10288` followed immediately by `dumpsys sensorservice sensorsControl stop 10288`, **without exiting the diagnostic screen**, successfully suspended proximity events again (user-reported successful cycle).
5. The user had previously run `resume 10288` and `update 10288 all` after other trials; a fresh explicit cleanup and final readback after the current cycle were not reported.

This demonstrates a **recoverable, but nonpersistent, UID-scoped suspension**. It supports the observation that `stop` ignores already-controlled UID entries and is therefore unable to resuspend a fresh connection without first removing the UID from the controlled set.

**Still unverified and important for implementation:** `update <uid> 8` truly limits restrictions to proximity when the *same UID* has other active sensors; `resume` does not cause unwanted bursts of sensor events; an ordinary APK with `android.permission.DUMP` can execute these native commands; or a low-overhead lifecycle trigger can safely replace continuous `resume` → `stop` loops. Do not enable such a loop on system UIDs or ship the feature in the existing POCO build on the strength of this test alone.

Cleanup for the test UID: `dumpsys sensorservice sensorsControl resume 10288` then `dumpsys sensorservice sensorsControl update 10288 all`; verify proximity works and UID is absent from `controlled apps`.


## Same-UID dual-sensor selectivity: first positive result

A custom, independent Sensor Dual Lab v0.1 APK (`dev.deus.sensorlab`) was installed on Redmi Note 10 Pro `sweet`; its ADB-verified UID was **10307**. The APK registers both Android `TYPE_PROXIMITY=8` and `TYPE_ACCELEROMETER=1` listeners simultaneously under that same UID and displays live event counters. It does not itself issue privileged commands.

The experimenter ran:

```sh
adb shell dumpsys sensorservice sensorsControl update 10307 8
adb shell dumpsys sensorservice sensorsControl stop 10307
```

**Observed outcome (user reported):** proximity stopped responding; accelerometer continued to receive events. This is the first positive *same-UID, two-type* selectivity observation on this firmware, unlike prior proximity-only diagnostic app experiments.

**Scope of evidence:** the command pair yields selective proximity interruption while retaining accelerometer events in an already-running dual-listener app. It does **not yet prove** that `update` *caused* the selectivity (a matched `stop`-without-`update` control trial is still needed), that the selective block persists when the proximity listener is recreated, or that an ordinary APK can issue Xiaomi's native controls with DUMP alone. No automatic watchdog or non-test-UID control is approved.

The app has two controls useful for discriminating the next hypothesis:
- **Recrear SOLO proximidad** unregisters/re-registers only the proximity listener without stopping the accelerometer;
- **Recrear AMBOS listeners** re-registers both.

Next experiment: reset test UID with `resume 10307` and `update 10307 all`; open app and validate both counters; configure `update 10307 8` + `stop 10307`, confirm selective effect, and press **Recrear SOLO proximidad** while still marked as controlled. Check whether proximity resumes and whether UID 10307 remains in `controlled apps`; capture events and restore settings. A separate A/B run with `stop 10307` **without** prior `update` is needed to isolate the source of selectivity, with cleanup between trials.
