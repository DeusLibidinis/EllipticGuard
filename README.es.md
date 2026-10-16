# Elliptic Guard

Proyecto experimental para desactivar selectivamente el sensor virtual de proximidad **Elliptic Proximity** en un **POCO X6 Pro**, sin root ni Shizuku. Utiliza una interfaz de Xiaomi no documentada en la ayuda estándar de Android y un servicio vigilante para contrarrestar reactivaciones.

**Estado:** prototipo `v0.6`. La desactivación manual y la vigilancia en primer plano funcionaron en las pruebas iniciales de **una unidad** de POCO X6 Pro. **Se observó el autoinicio del vigilante tras reiniciar con v0.5 en la unidad original; falta confirmar el comportamiento después de actualizar a v0.6.** Que `dumpsys` devuelva `exit=0` no demuestra por sí solo que el sensor esté desactivado.

> [!WARNING]
> **Atención:** al desactivar globalmente proximidad, la pantalla puede dejar de apagarse al acercar el teléfono a la oreja durante llamadas normales. El vigilante mantiene un servicio con notificación y puede aumentar el consumo de batería.

[English](README.md) | [Instalación](docs/INSTALL.md) | [Prueba de autoinicio](docs/AUTOSTART_TEST.md) | [Compatibilidad](docs/COMPATIBILITY.md) | [Investigación](docs/RESEARCH.md)

## Función

La APK identifica el sensor Elliptic, registra un `SensorManager` listener y ejecuta `dumpsys sensorservice disable 03000000` con permiso `android.permission.DUMP` otorgado por ADB. Con la vigilancia activa, repite la operación al encender/desbloquear la pantalla, al recibir eventos del sensor y periódicamente. No incorpora el control por UID de WhatsApp ni requiere `MANAGE_SENSORS`.

## Instalar desde el código

Requiere SDK Android API 35+, Build Tools 35+, Fish, JDK y ZIP:

```fish
cd app
fish build.fish
adb install -r build/EllipticGuard-v0.6.apk
adb shell pm grant dev.deus.ellipticprobe android.permission.DUMP
```

Mantén el `debug.keystore` local para poder actualizar la instalación; **no lo subas al repositorio**. El paquete sigue siendo `dev.deus.ellipticprobe` para conservar compatibilidad con el prototipo instalado.

**Compatibilidad distinta del firmware probado: desconocida.** No ejecutes comandos con handles de sensores elegidos al azar. La investigación de otros dispositivos debe empezar identificando sus sensores y verificando el comportamiento real.

## Colaborar

Utiliza la plantilla de compatibilidad. No publiques información personal, volcados de sistema sin revisar, claves ni bibliotecas propietarias del fabricante. Licencia: Apache 2.0.


## Desarrollo asistido por IA

Elliptic Guard se desarrolla con asistencia de inteligencia artificial para la implementación de código, el análisis técnico y la documentación.

La dirección del proyecto, las pruebas en dispositivos físicos, la validación y las decisiones de publicación permanecen bajo supervisión humana. Las propuestas generadas con IA no se consideran verificadas hasta que se comprueban mediante pruebas.

El uso de herramientas de IA no modifica la licencia Apache 2.0 del proyecto.

### Identidad Deus Libidinis

La aplicacion incorpora un escudo DL, icono de notificacion propio y firma de autoria visible.
Un easter egg inocuo se descubre tocando siete veces el escudo o el numero de version.
El codigo no incluye binarios propietarios de los fabricantes.
