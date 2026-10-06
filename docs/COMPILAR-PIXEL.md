# Compilación de Nexus en el Pixel 8 Pro

Comprobada el 2 de octubre de 2026 en el Pixel físico, serial `38271FDJG00264`, perfil personal 0. El puerto ADB cambia: descubrirlo y comprobar modelo, serial y perfil antes de actuar. No confundirlo con el Samsung conectado por USB.

## Entorno que funciona

- Termux nativo, paquete `com.termux`; no proot.
- Raíz: `/data/data/com.termux/files/home/nexus-build/batches-20261002`.
- Fuente identificada en `source`, copiada mediante ZIP y manifiesto SHA-256.
- Java 17.0.20 oficial de Termux, extraído en `tools/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk`.
- AAPT2 ARM64 16.0.0.4-2 de Termux con dependencias aisladas; wrapper `aapt2` en la raíz.
- SDK Android 35 y Build Tools 34.0.0 en `sdk`; Gradle Wrapper 8.9.
- Un worker, sin paralelismo, compilador Kotlin en proceso, heap de 2 GiB y metaspace de 768 MiB.
- `org.gradle.native=false` y `org.gradle.vfs.watch=false`.

El Java 21 de la distribución Google Play falló al acceder a HTTPS y el AAPT2 incluido era demasiado antiguo. Los binarios aislados evitan cambiar las herramientas de otros proyectos.

## Repetir una compilación

El procedimiento y sus versiones están conservados en `outputs/batches-20261002/build-pixel.sh`, junto con los manifiestos y la procedencia de las herramientas. No reutilizar su ZIP como si contuviera cambios posteriores: crear una instantánea del código actual y verificarla antes de compilar. Los scripts para Android deben usar saltos LF.

La carpeta de intercambio es `/sdcard/Download/nexus-batches-20261002`. Desde Termux se ejecuta:

```sh
bash /sdcard/Download/nexus-batches-20261002/build-pixel.sh
```

El script exporta `build-pixel.log`, `build-pixel.exit`, tres APK y los XML de JUnit. No iniciar otro build si ya hay uno activo. Comprobar que Termux está en primer plano antes de escribir comandos por ADB; otra tarea puede estar usando el teléfono.

## Firma e instalación

La compilación se hace en el Pixel. Los APK se recuperan y se firman en Windows con la clave debug existente, sin copiar la clave privada al teléfono, para actualizar sin desinstalar ni borrar datos. Verificar certificado y SHA-256 tras instalar en el perfil personal.

Usar Nexus Lab para pruebas locales: carece de permiso INTERNET y bloquea el envío de generaciones. Nunca pulsar el CTA de generación real durante las comprobaciones. Conservar las preferencias originales del laboratorio antes de usar fixtures y restaurarlas después.
