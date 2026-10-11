# Device/firmware compatibility matrix

| Device | Sensor and handle | Vendor firmware | Status | Evidence |
| --- | --- | --- | --- | --- |
| POCO X6 Pro (`duchamp`) | `Elliptic Proximity`, `0x03000000` | HyperOS, exact build pending | **Experimental:** manual/foreground guard successful on one unit; automatic service startup after reboot observed on v0.5, v0.6 update behavior not yet verified | Original experimenter logs + independent proximity diagnostic |
| Second POCO X6 Pro | To be checked | To be checked | Not tested | None yet |
| Other Xiaomi / Redmi / POCO | Unknown | Unknown | Not tested | None |
| Other manufacturers | Unknown | Unknown | Unsupported / unknown | None |

Compatibility requires more than a matching model name: different firmware builds may expose different native `SensorService` commands, or forbid them to `DUMP`-granted apps.

For reports please provide device model/codename, Android/HyperOS build (not IMEI/serial), sensor name/vendor/type/handle, `DUMP` grant result, behavior before/after, boot behavior, and battery observations.
