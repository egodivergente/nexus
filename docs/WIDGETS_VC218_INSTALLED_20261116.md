# Nexus vc218 — instalada en S25 (16/11/2026)

**1.9.182-agent-preview / vc218 instalada como actualización de vc217** en el
Samsung S25 Ultra SM-S938B, paquete `com.vidadomu.nexus.agent`.
Fuente compilada: `312ce35684883b7894d4602e72060d29e0322ed4`.
Incluye el arreglo de identidad del PR #13 y su adaptación del PR #15 sobre la
rama de Cut vc217; conserva los cambios recientes del agente.

## Artefacto e instalación

- AWS CodeBuild `vidal-android-build:c963c17d-6d61-49a3-8fe7-0e17591f478f`:
  SUCCEEDED. Ensamblado AgentPreview; no se repiten suites ya verificadas.
- APK firmado e instalado, SHA-256 coincidente:
  `35946770472ce3f6bc6ece928b8b15487516f0ef95b8078d498c161c5b9cc04f`.
- Certificado instalado conservado:
  `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
- APK ARM64 con las tres bibliotecas originales idénticas a vc217 y alineación
  ZIP de 16 KiB verificada. Se comprueban la revisión de fuente y la clase
  WidgetAnswer dentro del DEX.
- Firma fuera de AWS y Git. ASUS solo controla AWS, firma y comunica con ADB;
  allí no se ejecutan Gradle, Kotlin ni compiladores.
- `adb install -r -t`: Success. Paquete, vc218, versión, UID y hash del APK
  instalado verificados; sin desinstalar ni borrar datos.

## Estado conservado

Antes de actualizar se comprobó que Nexus no tenía servicios activos. Copia
privada consistente del estado: bases de datos, preferencias, historial, índice,
tableros y proyectos Cut. La SQLite copiada pasó `integrity_check=ok`.

Los **206 archivos de estado incluidos en la copia son idénticos antes y después
de instalar**, antes del primer arranque. El inventario completo de
`files/assistant`, incluidos multimedia y caché de sincronización, no cambia.
La copia evita duplicar los grandes binarios de incoming-shares/sync; dichos
archivos permanecen en el dispositivo.

Tras arrancar no falta ningún ID anterior del índice: **12 proyectos, 2 escenas,
5 planos, 9 personajes, 3646 archivos y 894 registros de generación del índice**.
La SQLite contenía 912 generaciones; su archivo también quedó idéntico durante
la actualización. Son almacenes distintos, no un único contador.

MainActivity: Status ok, proceso activo y ningún registro nuevo de crash de Nexus
observado durante la comprobación. No se envían conversaciones de prueba,
solicitudes a modelos ni generaciones a proveedores.

## Alcance

Esta entrega verifica ensamblado, firma, instalación, estado y arranque en S25.
Las regresiones de selección ya estaban verificadas: 235 JVM superadas y
4 omitidas en el PR #13, 2 de persistencia y 5 casos físicos en Pixel; la rama
reciente pasó sus 3 regresiones de persistencia/selección.
Véase `PR13-SELECTION-IDENTITY-20261116.md` para alcance y límites de esa UI.
El Pixel no se actualizó en esta entrega. Los commits usan `[skip ci]`;
no se ejecutan workflows de GitHub Actions.
