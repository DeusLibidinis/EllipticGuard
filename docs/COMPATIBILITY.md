# Device/firmware compatibility matrix

| Device | Sensor and handle | Vendor firmware | Status | Evidence |
| --- | --- | --- | --- | --- |
| POCO X6 Pro (`duchamp`) | `Elliptic Proximity`, `0x03000000` | HyperOS, exact build pending | **Experimental:** manual/foreground guard successful on one unit; automatic service startup after reboot observed on v0.5, v0.6 update behavior not yet verified | Original experimenter logs + independent proximity diagnostic |
| Second POCO X6 Pro | To be checked | To be checked | Not tested | None yet |
| Other Xiaomi / Redmi / POCO | Unknown | Unknown | Not tested | None |
| Other manufacturers | Unknown | Unknown | Unsupported / unknown | None |

Compatibility requires more than a matching model name: different firmware builds may expose different native `SensorService` commands, or forbid them to `DUMP`-granted apps.

For reports please provide device model/codename, Android/HyperOS build (not IMEI/serial), sensor name/vendor/type/handle, `DUMP` grant result, behavior before/after, boot behavior, and battery observations.

## Candidate 0.6.1-beta.1: evidence still required

API guards for Android 12/13 are code compatibility improvements, **not** a
new verified-device row. No handles are inferred or generalized.

For the second POCO, collect model/codename and exact Android/HyperOS build
first. Inspect the actual proximity handle and vendor implementation before
any command. Then record DUMP availability, external proximity behavior,
lock/unlock, update recovery, cold boot and battery observations separately.
A matching `Elliptic` name does not authorize using the fixed handle on a new ROM.

For a 24–48 h observation, record the firmware, version, screen-on time, charging
periods, guard failures/restarts, sensor false activations and the battery usage
shown by Android for Elliptic Guard. Compare with a similar baseline and avoid
attributing total battery drain to this process without evidence. Do not change
the proven 4 s / 30 s intervals during the first stability measurement.
