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
| 1 | Tokens, tipografía, moneda, InlineNotice | ASUS: 105 tests del núcleo y 252 de la app, cero fallos; APK offline y tests compilados. Emulador: 5 regresiones de referencias y navegación, cero fallos | Completada |
| 2 | Cabecera, saldo y navegación | ASUS: 105 tests del núcleo y 254 de la app, cero fallos; APK offline y tests compilados. Emulador: hoja de saldo, regresión de navegación y seis capturas 1.3, cero fallos | Completada |
| 3 | Lienzo | ASUS: 105 núcleo + 254 app, cero fallos; APK offline. Emulador: CanvasMinimalUiTest y navegación, dos tests; cuatro capturas | Completada |
| 4 | Historial | Pendiente | Pendiente |
| 5 | Modelos | Pendiente | Pendiente |
| 6 | Biblioteca y visor | Pendiente | Pendiente |
| 7 | Asistente y proyecto | Pendiente | Pendiente |
| 8 | Ajustes y Flow | Pendiente | Pendiente |
| 9 | QA visual, accesibilidad y regresión | Pendiente | Pendiente |

Las capturas antes del S25 se realizan con la APK original vc28, a escalas 1.0 y 1.3. Se restaura la escala original al terminar cada captura. Las imágenes privadas están fuera de Git en `outputs/ui-minimal-20261009/before` del workspace original.

La compilación se realiza en una copia exclusiva `/home/vidal/.cache/nexus-ui-minimal-20261009/source` del ASUS, con Java 17, SDK 35, un worker, sin paralelismo, compilador Kotlin dentro del proceso y prioridad baja. La primera copia omitió los assets de skills y fallaron ocho tests del núcleo; se corrigió la copia, conservando intactas las fuentes del agente. La repetición completa terminó en BUILD SUCCESSFUL (19m 21s), con 105 tests del núcleo y 245 de la app, cero fallos y cero errores. Se recuperaron los dos APK y se verificaron los hashes de las 419 fuentes copiadas.

La opción `-PnexusUiQa` solo cambia el identificador del paquete offline de pruebas a `com.vidadomu.nexus.uiqa`. Mantiene el bloqueo existente de envíos y permite capturar los composables con fixtures locales sin sustituir Nexus Agent ni el paquete QA anterior. La prueba visual no envía mensajes al LLM ni pulsa Generar.

Las [24 capturas publicadas](ui-minimal/before) utilizan datos ficticios en un emulador API 35 con perfil Pixel 6a de 6,1 pulgadas, a escalas 1.0 y 1.3. Cubren Lienzo, Historial, Modelos, opciones de modelo, Biblioteca, Visor, Asistente, Proyecto, Ajustes, Flow, Comparación y Costes. Se renderiza el composable real de cada pantalla, sin la navegación y cabecera externas de MainActivity. La captura incluye las ventanas de diálogo y espera a que el emulador presente el fotograma. `UiAuditScreenshotsTest` terminó con `OK (1 test)` en 56,914 s. Las capturas privadas del S25 aportan la referencia adicional de la cabecera global; no se publican.

El inventario inicial está completo; la columna de comprobación de destinos queda pendiente hasta implementar y verificar cada fase. El Pixel físico permanece bloqueado. La evidencia de esta fase corresponde al emulador y a las capturas iniciales disponibles del S25, no a pruebas completas en un dispositivo físico.

La fase 1 define cinco tamaños tipográficos (24, 17, 16, 14 y 12 sp), sin tamaños de letra literales en pantallas. Los nombres de espaciado y formas existentes remiten a los tokens; los literales restantes de geometría se revisarán al reorganizar cada pantalla. El formateador conserva los valores guardados de las preferencias EUR_SMART/EUR_STRICT, ahora con la misma presentación de dos decimales solicitada. No cambia la consulta de divisas ni su persistencia. Los tests verifican valores pequeños, agrupación, créditos, USD de detalle, valores no finitos y la independencia del idioma del dispositivo. Las seis paletas superan 4,5:1 en los pares de texto evaluados. El componente InlineNotice reserva las excepciones para Detalles; su integración se hará con cada pantalla.

Compilación de fase 1: BUILD SUCCESSFUL en 10m 36s. Se verificaron 424 hashes de fuentes copiadas y se recuperaron los APK offline. El APK agentPreview presente en esta carpeta procede de la compilación anterior; no se presenta como un artefacto nuevo de esta fase.

La fase 2 retira AccountBalanceBar de la cabecera externa y conserva su ViewModel de consulta sin cambios. La barra común mide 56 dp, incorpora el chip de saldo y abre Saldo y gasto con los datos originales de cada proveedor, hora/último dato, actualización y BillingLedgerScreen. Los callbacks de navegación siguen siendo los mismos. La barra inferior mantiene el arreglo de restauración de Asistente/Modelos, añade indicador activo en píldora y usa etiquetas de 12 sp. «Biblio» es la etiqueta corta de Biblioteca; su descripción accesible conserva el nombre completo. La cabecera específica de Proyecto y los controles secundarios de Lienzo/Modelos se completarán en sus fases.

Compilación final de fase 2: BUILD SUCCESSFUL en 9m 18s; 427 hashes verificados. BalanceSheetUiTest y AgentNavigationTest terminaron con OK (2 tests), en 32,759 s, en el emulador. La consulta de saldo queda bloqueada en el paquete offline y la prueba comprueba que los ids de trabajos no cambian al actualizar. Los estados de hora/último dato conservan el código original de AccountReading y se presentan en la hoja; aún falta su matriz visual con saldos ficticios.

La prueba visual de fase 2 terminó con OK (1 test), en 32,369 s: seis capturas de Lienzo, Historial, Modelos, Biblioteca, Ajustes y Flow a escala 1.3. Se revisaron la cabecera única y el chip; la densidad del contenido sigue pendiente de cada fase específica. El harness aislado no incluye la barra inferior externa; sus insets y su apariencia se revisarán también en MainActivity durante el QA final.

La fase 3 coloca el modelo y el prompt antes de los medios. Los chips abren inspiración, parámetros, comparación y presets personales; la barra fija contiene el precio tocable y Generar. Los editores originales de archivos, slots y contrato pasan a Medios, incluyendo la máscara opcional. El control de restauración está directamente en cada parámetro para mantener dos pulsaciones. Las opciones avanzadas se muestran dentro de la misma hoja. El menú incorpora el catálogo completo y, solo en DEBUG, el diagnóstico y su copia sanitizada.

La estimación, CreationReadiness, los callbacks de edición/adjuntos y el envío siguen usando sus contratos originales. Los errores principales pasan a InlineNotice con diagnóstico plegado. Se retira la consulta de saldo duplicada que aún estaba dentro del composable; la cabecera continúa usando AccountBalanceViewModel, sin cambios en sus reglas o almacenamiento.

ASUS: BUILD SUCCESSFUL en 9m 1s, 428 fuentes verificadas, 105 tests del núcleo y 254 de la app sin fallos ni errores. La nueva prueba detectó que el fixture tenía identificadores distintos del borrador ya restaurado en QA; se corrigió para comparar exactamente el estado restaurado antes y después, sin borrar datos. La compilación del APK de tests terminó en 2m 9s. CanvasMinimalUiTest y AgentNavigationTest: OK (2 tests), 39,920 s. Se verifican restaurar parámetro, acceso a opciones avanzadas/medios/catálogo y conservación de adjuntos e ids de trabajos. UiAuditScreenshotsTest: OK (1 test), 28,812 s; Lienzo y Comparación a escala 1.3, más capturas de parámetros y medios. No se pulsa Generar. Las capturas de esta fase están en la carpeta privada de outputs; la matriz publicable final sigue pendiente.

## Decisiones

- Se conservan los seis temas existentes, incluidos Océano y Bosque. La matriz principal solicitada sigue cubriendo oscuro, OLED, claro y Quartz.
- Los precios con unidad conocida se presentan de forma comparable dentro de su modalidad. No se convierten unidades desconocidas, imágenes o tokens a segundos.
- La separación de la galería es un token específico de 2 dp, conforme a la petición de cuadrícula a sangre. El margen en tablet conserva su token de 20 dp.
- Los controles de detalle pueden crecer con la fuente. No se impone un alto fijo que recorte texto para aparentar compactación.
- Las capturas publicables se prepararán con fixtures locales de QA, sin prompts, claves ni fotos personales.
