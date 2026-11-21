# NEXUS: Android y Windows

Código de **Nexus Agent** para Android (paquete `com.vidadomu.nexus.agent`) y de
**Nexus Desktop** para Windows.

## Qué rama es cuál

- `main` está en la vc63 y no comparte historia con las ramas vivas: no describe
  lo instalado.
- Lo instalado sale de ramas `aki/…`. Cada entrega instalada lleva una etiqueta
  `v<versión>-agent` en el commit del que se compiló su APK; no todas tienen
  Release con APK publicada.
- Esta rama, `aki/mejoras-auditoria-20261121`, es la vc228 (1.9.192-agent): parte
  de la vc227 (`aki/agent-polish-20261120`) y añade las mejoras de la auditoría
  del 20-11-2026 (lista en el [CHANGELOG](CHANGELOG.md)).
- El escritorio se desarrolla en sus propias ramas: la carpeta `desktop/` de una
  rama de Android puede ir por detrás.

## Código

| Carpeta | Contenido |
|---|---|
| `app/` | Android: Agente, Crear, Biblioteca, Guion, Nexus Cut, ajustes y proveedores |
| `agent-core/` | Núcleo Kotlin del agente (sin Android) y sus pruebas |
| `cut-core/` | Núcleo Kotlin de Nexus Cut y sus pruebas |
| `axel-link/` | Cliente de AXEL Link |
| `agent-skills/` | Skills de Android |
| `agent-proxy/` | Puente AKI y botón de Termux para arrancarlo |
| `desktop/` | Host Windows, interfaz, agente JVM, edición y herramientas de entrega |
| `tools/flow-bridge/` | Puente de Google Flow |
| `docs/` | Cambios, instrucciones y evidencia de validación |

## Compilar Android

Requiere JDK 17 y el SDK de Android con la plataforma 36. Whisper necesita
además el NDK `27.2.12479018` y CMake `3.22.1`; para no compilarlo, se puede
pasar `-PnexusAgentPrebuiltJniDir=<carpeta>` con `libnexus_whisper.so` ya
compilada por ABI (`arm64-v8a/`, `x86_64/`).

```bash
./gradlew :cut-core:test :agent-core:test :app:testAgentPreviewUnitTest :app:assembleAgentPreview --max-workers=1 --no-parallel
```

En Windows, `gradlew.bat`. La firma de la APK entregada se aplica fuera de Git:
una compilación propia no lleva esa clave y no se instala encima de la entregada.

## Integración continua

`.github/workflows/build-apk.yml` tiene tres trabajos:

- **Android:** APK debug y Agent, pruebas JVM de todos los módulos y las APK de
  pruebas sin red. Bloquea.
- **Pruebas de interfaz:** en emulador, con las APK del trabajo anterior.
  `AssistantPresentationUiTest` bloquea; `ChatRichContentDeviceTest`, que es
  intermitente, se ejecuta y se guarda, pero su fallo solo deja un aviso.
- **Windows:** pruebas y runtime del agente JVM de `desktop/agent`, y que todo el
  Python y el JavaScript de `desktop/` se pueda leer. Bloquea.

Un push nuevo a la misma rama cancela la ejecución anterior, y los cambios que
solo tocan documentos no lanzan nada.

## Compilar y abrir Windows

Requiere Python 3.12, Java 17 y Microsoft Edge. Desde la raíz:

```powershell
python -m pip install -r desktop/requirements-media.txt
./gradlew.bat -p desktop/agent test :core:test runtime --max-workers=1 --no-parallel
```

Copiar los JAR de `desktop/agent/build/runtime/` a `desktop/agent/runtime/`.
Para crear el acceso directo, ejecutar `desktop/install-local.ps1`. Datos y
configuración quedan en `%LOCALAPPDATA%/NexusDesktop`.

Sol usa el puente autenticado del móvil: [botón de Termux](docs/NEXUS-AKI-WIDGET-20261012.md).
Whisper necesita su runtime y modelo locales; no se incluyen pesos.

## Más

- [Changelog](CHANGELOG.md).
- [Rediseño de la pantalla del Agente, desde la vc224](docs/NEXUS-AGENT-REDESIGN-20261118.md).
- [AXEL Link](docs/AXEL_LINK_20261118.md).

Los antiguos `SOURCE-MANIFEST.json` y `REPOSITORY-MANIFEST.json` corresponden a
la recuperación inicial de 2026; no describen el código actual.
