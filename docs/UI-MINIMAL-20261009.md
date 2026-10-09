# Rediseño minimalista de Android

## Procedencia y alcance

Base comprobada: `4c16a1a`, Nexus Agent 1.8.4-agent vc28. Worktree `C:\Users\User\.codex\worktrees\android-agent-references\NEXUS`. Rama propia `codex/ui-minimal-20261009`. La tarea anterior de referencias está publicada y su worktree estaba limpio al empezar.

Se usa el remoto existente `https://github.com/vidaltb94-collab/nexus.git`. GitHub redirige ese remoto al repositorio renombrado; la fuente de este trabajo sigue siendo el worktree indicado, no otra copia ni otra rama.

Este encargo modifica presentación y navegación de interfaz. No autoriza cambios en proveedores, cobro, generación, colas, almacenamiento, herramientas del agente ni ejecución de operaciones de pago. El único cambio permitido en core es el formato visible de moneda. Los datos reales, claves, APK y copias de seguridad no se incluyen en Git.

El mapa de funciones y su verificación está en [INVENTARIO-UI.md](INVENTARIO-UI.md).

## Fases y evidencia

| Fase | Contenido | Compilación y pruebas | Estado |
|---|---|---|---|
| 0 | Inventario y capturas antes | ASUS: 105 tests del núcleo y 245 de la app, cero fallos; ambos APK compilados. Emulador: prueba visual, 24 capturas, cero fallos | Completada |
| 1 | Tokens, tipografía, moneda, InlineNotice | Pendiente | Pendiente |
| 2 | Cabecera, saldo y navegación | Pendiente | Pendiente |
| 3 | Lienzo | Pendiente | Pendiente |
| 4 | Historial | Pendiente | Pendiente |
| 5 | Modelos | Pendiente | Pendiente |
| 6 | Biblioteca y visor | Pendiente | Pendiente |
| 7 | Asistente y proyecto | Pendiente | Pendiente |
| 8 | Ajustes y Flow | Pendiente | Pendiente |
| 9 | QA visual, accesibilidad y regresión | Pendiente | Pendiente |

Las capturas antes del S25 se realizan con la APK original vc28, a escalas 1.0 y 1.3. Se restaura la escala original al terminar cada captura. Las imágenes privadas están fuera de Git en `outputs/ui-minimal-20261009/before` del workspace original.

La compilación se realiza en una copia exclusiva `/home/vidal/.cache/nexus-ui-minimal-20261009/source` del ASUS, con Java 17, SDK 35, un worker, sin paralelismo, compilador Kotlin dentro del proceso y prioridad baja. La primera copia omitió los assets de skills y fallaron ocho tests del núcleo; se corrigió la copia, conservando intactas las fuentes del agente. La repetición completa terminó en BUILD SUCCESSFUL (19m 21s), con 105 tests del núcleo y 245 de la app, cero fallos y cero errores. Se recuperaron los dos APK y se verificaron los hashes de las 419 fuentes copiadas.

La opción `-PnexusUiQa` solo cambia el identificador del paquete offline de pruebas a `com.vidadomu.nexus.uiqa`. Mantiene el bloqueo existente de envíos y permite capturar los composables con fixtures locales sin sustituir Nexus Agent ni el paquete QA anterior. La prueba visual no envía mensajes al LLM ni pulsa Generar.

Las [24 capturas publicadas](ui-minimal/before) utilizan datos ficticios en un emulador API 35 con perfil Pixel 6a de 6,1 pulgadas, a escalas 1.0 y 1.3. Cubren Lienzo, Historial, Modelos, opciones de modelo, Biblioteca, Visor, Asistente, Proyecto, Ajustes, Flow, Comparación y Costes. Se renderiza el composable real de cada pantalla, sin la navegación y cabecera externas de MainActivity. La captura incluye las ventanas de diálogo. `UiAuditScreenshotsTest` terminó con `OK (1 test)` en 39,756 s. Las capturas privadas del S25 aportan la referencia adicional de la cabecera global; no se publican.

El inventario inicial está completo; la columna de comprobación de destinos queda pendiente hasta implementar y verificar cada fase. El Pixel físico permanece bloqueado. La evidencia de esta fase corresponde al emulador y a las capturas iniciales disponibles del S25, no a pruebas completas en un dispositivo físico.

## Decisiones

- Se conservan los seis temas existentes, incluidos Océano y Bosque. La matriz principal solicitada sigue cubriendo oscuro, OLED, claro y Quartz.
- Los precios con unidad conocida se presentan de forma comparable dentro de su modalidad. No se convierten unidades desconocidas, imágenes o tokens a segundos.
- La separación de la galería es un token específico de 2 dp, conforme a la petición de cuadrícula a sangre. El margen en tablet conserva su token de 20 dp.
- Los controles de detalle pueden crecer con la fuente. No se impone un alto fijo que recorte texto para aparentar compactación.
- Las capturas publicables se prepararán con fixtures locales de QA, sin prompts, claves ni fotos personales.
