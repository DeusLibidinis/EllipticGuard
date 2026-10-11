# Android prototype app

Candidate **0.6.1-beta.1**, based on the v0.6 POCO X6 Pro experiment. Adds update-startup diagnostics and API-version guards while preserving the installed package, preferences and watchdog timings. Hardware validation is pending.

Run `fish build.fish` from this directory after configuring a local Android SDK. Save `debug.keystore` privately to preserve update signatures. The APK is **not included** in this repository starter.


## Identidad y firma (v0.6)

- Icono de escudo + ondas de proximidad, con monograma **DL** de Deus Libidinis.
- Icono monocromo propio en la notificacion del vigilante.
- Firma visible en la interfaz: `DEUS LIBIDINIS / SENSOR RESEARCH`.
- Easter egg: pulsa **7 veces** sobre el escudo o la linea de version para abrir el archivo oculto `DL-03000000`. No modifica la configuracion ni ejecuta comandos.
- Se mantienen package `dev.deus.ellipticprobe`, preferencias, permisos y receptor de arranque. Para actualizar una app ya instalada se **debe utilizar exactamente el mismo keystore de firma**: conservar `debug.keystore` del proyecto original.

El monograma y los creditos del autor NO sustituyen la firma criptografica de Android (`apksigner`). El nombre de proyecto no implica afiliacion con Xiaomi, MediaTek o Elliptic Labs.

See [Release preparation](../docs/RELEASE.md) for debug/release modes and private production signing; [Update test](../docs/UPDATE_TEST.md) for the pending on-device check.
