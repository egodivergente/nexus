# NEXUS — Android y Windows

Fuente unificada de **Nexus Agent 1.9.3** para Android y **Nexus Studio 1.9.3**
para Windows. La rama principal es `main`.

## Descargar

[Release 1.9.3: APK Android y paquete Windows](https://github.com/egodivergente/nexus/releases/tag/v1.9.3-agent)

- **Android:** `NEXUS-Agent-1.9.3-opciones-visuales.apk`, paquete
  `com.vidadomu.nexus.agent`, versionCode 37, con las opciones visuales nuevas,
  compilada y firmada con el certificado de la app existente.
- **Windows:** `Nexus-Studio-Agent-1.9.3-Windows.zip`, con runtime del agente.
- **SHA256SUMS.txt:** hashes para comprobar las dos descargas.
- El código completo se obtiene con **Code → Download ZIP** o clonando `main`.

La APK de opciones visuales aún no se ha instalado en el S25. Su versión coincide
con la 1.9.3 instalada, que precede a este ajuste visual. La edición Windows 1.9.3
ya está instalada en el EliteBook. Publicar esta Release no actualiza dispositivos.

## Código

| Carpeta | Contenido |
|---|---|
| `app/` | Android: interfaz, proveedores, seguimiento, biblioteca y edición local |
| `agent-core/` | Núcleo Kotlin del asistente y sus pruebas |
| `agent-skills/` | Skills de Android |
| `agent-proxy/` | Puente AKI y botón de Termux para arrancarlo |
| `desktop/` | Host Windows, interfaz, agente JVM, edición y herramientas de entrega |
| `tools/flow-bridge/` | Puente de Google Flow |
| `docs/` | Cambios, instrucciones y evidencia de validación |

## Compilar Android

Requiere JDK 17, SDK Android 35, NDK `27.2.12479018` y CMake `3.22.1`.
Con el SDK configurado mediante `ANDROID_HOME` o `local.properties`:

```bash
./gradlew :app:testAgentPreviewUnitTest :app:assembleAgentPreview --max-workers=1 --no-parallel
```

En Windows, usar `gradlew.bat`. La firma compatible de la APK entregada se aplica
fuera de Git; una compilación local no incluye esa clave de firma.
También están documentadas las rutas de [ASUS/Pixel](docs/COMPILAR-PIXEL.md).

## Compilar y abrir Windows

Requiere Python 3.12, Java 17 y Microsoft Edge. Desde la raíz:

```powershell
python -m pip install -r desktop/requirements-media.txt
./gradlew.bat -p desktop/agent test :core:test runtime --max-workers=1 --no-parallel
```

Copiar los JAR de `desktop/agent/build/runtime/` a `desktop/agent/runtime/`.
El ZIP de la Release ya incluye estos JAR. Para crear el acceso directo, ejecutar
`desktop/install-local.ps1`. Datos y configuración permanecen en
`%LOCALAPPDATA%/NexusDesktop`.

Sol usa el puente autenticado del S25: [botón de Termux](docs/NEXUS-AKI-WIDGET-20261012.md).
Whisper necesita su runtime y modelo locales; no se incluyen pesos en el ZIP.

## Estado y pruebas

- [Changelog del proyecto](CHANGELOG.md).
- [Mejoras implementadas tras la auditoría del 14 de octubre de 2026](docs/MEJORAS-AUDITORIA-20261014.md).
- [Consolidación y procedencia de la Release 1.9.3](docs/RELEASE-193-CONSOLIDADA-20261013.md).

Se conservaron todos los commits de ambas ramas, sin reescribir su historial.
Los antiguos `SOURCE-MANIFEST.json` y `REPOSITORY-MANIFEST.json` corresponden a la
recuperación inicial de 2026; no describen esta Release.
