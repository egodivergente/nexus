# Auditoría premium de Nexus Agent — 29/10/2026

## U08 — familias y variantes, CÓDIGO RESUELTO / QA FINAL PARCIAL vc169

- Implementado y publicado: familia única, selector compartido, rutas exactas, pool íntegro, Inbox con prioridad y conservación; audio/vídeo opcionales, identidad de flujo y procedencia de ajustes corregidos.
- AXELPC vc169/1.9.133, fuente712ae1c86def5594ae20aaf5137b934de612ac81: 288 app ejecutadas PASS +4 núcleo UP-TO-DATE. Umbral de ocultamiento400ms intacto, PASS tras cache de snapshot/revisión. Firma/nativas/16KiB PASS, cero dependencias.
- Instalada como actualización en ambos: hash/versión/foreground y borradores inmediatos iguales. Pixel169 ciclo2.5→Mini→Fast→2.5 recupera1080p, prompt y dos imágenes exactos; opcionales ausentes no bloquean.
- ArtBox169: Pro2 preparados→Lite2 conservados y reapertura conservados PASS. Traspaso/retorno final pendiente, interrumpido por cambio de pantalla. Su prueba166 es histórica.
- S25169: arranque/Biblioteca comprobados; QA restante interrumpida por Samsung Notes. Borrador147chars/1imagen igual durante install; después texto vacío sin causa atribuible demostrada. No se restauró sobre el estado actual.
- PENDIENTE aceptación: resto QA S25, traspaso ArtBox final, retirar dos referencias QA Pixel/restaurar su borrador inicial sin interferir, fuente grande/IME selector169, TalkBack/háptica física/120Hz. No se declara cierre global10/10. Motivos, ventanas de log y resultados exactos en AUDIT_REPORT.md.
- Sin pagos, cambios de firma, borrado de originales/datos ni refactors ajenos. Registros históricos inferiores conservados.

## Corrección posterior: aprovechar el marco disponible — U07

- **U07 — IMPLEMENTADO, QA PIXEL PASS / S25 VISUAL PENDIENTE por Keyguard,
  vc164 / 1.9.128-agent-preview.** La captura posterior a vc161
  mostraba un gran vacío entre Avanzado y Generar. Se amplían el viewport editable
  y los previews reales con la altura útil, manteniendo botones y márgenes. El
  mínimo medido decide si cabe, también con fuente 1,5×; no hay un corte artificial
  por escala de fuente. Scroll de respaldo por IME o desbordamiento real.
- Un único fotograma poblado ocupa todo el ancho cuando el par horizontal cabe;
  las ranuras vacías permanecen compactas. Cero o dos poblados mantienen el par.
  Los previews reciben hasta el 60 % del sobrante en una columna, con capacidad
  limitada por su ancho real; el resto queda para el editor. Ver CAPABILITY_UI.
- Fuente compilada, publicada y verificada en remoto:
  `d2616d2cde9f44cf81e48c47e8dbb28a53345dc1`. Build en AXELPC
  PASS en 8 min 54 s: **205 pruebas app ejecutadas**, 0 fallos/errores/omitidas;
  **4 pruebas núcleo UP-TO-DATE**, resultado válido reutilizado sin cambios del
  núcleo. No se presentan como 209 ejecuciones nuevas.
- APK: `C:/Users/User/Documents/Codex/nexus-premium-20261029/Nexus-Agent-1.9.128-v164.apk`.
  SHA-256 `99dfb4ed53e9ac188674172decc8c3d1f4e6d8d8bfb24a8da7cca81e5fd69f39`.
  Certificado existente `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`,
  alineación 16 KiB y cinco ARM64 idénticas a vc155: PASS.
- Instalación Success/hash/versión vc164 y huellas de borrador iguales en ambos.
  Pixel: retrato con foto → retirada → vacío, fuente 1,5×, ancho/IME y modal de
  Duración PASS. Con foto, prompt 553 px y hueco Avanzado→Coste 54 px; sin fotos,
  prompt 1261 px y hueco 115 px; fuente 1,5× con foto, hueco 48 px. Borrador original
  recuperado exactamente y archivo original sin borrar.
- **S25 VISUAL BLOQUEADO:** Keyguard showing/inputRestricted true y foco
  Bouncer/NotificationShade; `am start` Status ok no acredita Nexus foreground.
  Pendiente humano desbloqueo y QA visual propia vc164. Su borrador de 4908
  caracteres, 3 activos y 2 conservados sí conserva todas las huellas al instalar.
  La última captura S25 vc162 no se hereda como prueba vc164. Sin PIN/bypass.
- Navegación propia Pixel Agente → ArtBox → Biblioteca → Lanzadera completada;
  revisión independiente de retrato/fuente 1,5× sin otro hallazgo visual de impacto.
  Identidad final/hash/borrador original exactos en ambos; solo Pixel foreground.
  Logs definitivos posteriores: Pixel 14 726 líneas y S25 101 957, 0 Fatal/ANR de
  Nexus detectados en las ventanas disponibles; intervalos en el suplemento.
  La revisión de logs no sustituye la QA visual bloqueada del S25.
- Riesgo acotado a layout: conservar estado, capacidades, parámetros, archivos,
  proveedor, firma y comportamiento de generación. Comparar geometría en el mismo
  escenario; no agrandar controles ni modificar contratos para llenar la pantalla.

| Iteración | Motivo y evidencia propia |
|---|---|
| vc162 | Checkpoint instalado en ambos, hash/huellas del borrador comprobados. Amplió la altura útil, pero la revisión posterior detectó un umbral de fuente que impedía aprovecharla a 1,5× cuando sí cabía. Sus métricas/capturas son históricas, no prueba de vc164. |
| vc163 | Solo COMPILADA, NO INSTALADA. Retiró ese umbral; la revisión visual con una foto descubrió preview a media anchura y fotograma final vacío estirado. No se cerró como entrega. |
| vc164 | INSTALADA en ambos con hash/huellas iguales. Layout y navegación Pixel PASS; S25 visual PENDIENTE humano por Keyguard, sin otra tentativa. Da ancho completo al único fotograma poblado, limita preview por ancho y mantiene vacíos compactos. Colector final sin Fatal/ANR de Nexus en ambas ventanas; no acredita visual S25. |

## Procedencia y criterios

AXELPC; rama propia `codex/nexus-premium-20261029`, base reconciliada `dead4032d43f2b47ccc58471b4466d2adf2f313e` (vc155). Leídos AGENTS.md, CONTINUAR.md y MIGRATION-STATUS.json antes de modificar. La autorización permite compilar, actualizar en sitio y publicar en esta rama; se conservan datos, firma y trabajos ajenos.

Pixel 8 Pro `38271FDJG00264` y S25 Ultra SM-S938B `R5CXC3J630Y`, con serial ADB explícito. Referencias de conservación: **Pixel vc156 / 1.9.120** y **S25 vc155 / 1.9.119**. Evidencias privadas fuera de Git: `C:/Users/User/Documents/Codex/nexus-premium-20261029`.

Se autorizó ampliar Conversaciones y Lanzadera con un motor por proveedor + modelo + endpoint + modalidad. Se reutilizan contratos, catálogo, precios y adaptadores; sin dependencias nuevas. Ambigüedad: conservar semántica o marcar desconocida con explicación. Riesgo irreversible: no ejecutar y registrar. Máximo dos intentos iguales; diagnosticar antes de reintentar. Tests y capturas no sustituyen aceptación física ni medición.

## Plan y estado — cierre histórico comprobado vc161

Checkpoint histórico `2abd8d732fa686aff954bcf95516a67cd76feb8d` publicado y verificado. **vc161 / 1.9.125-agent-preview**, 209 pruebas PASS (205 app + 4 núcleo, sin omitidas), APK/hash/firma/alineación/nativas verificados e instalación Success con hash idéntico en ambos. Las filas siguientes conservan el cierre de vc161; la corrección posterior y su aceptación propia están en U07. IMPLEMENTADO describe el bloque ejecutado; las evidencias de vc161 no se atribuyen a vc164.

| ID | Impacto / riesgo | Acción y criterio | Estado / evidencia |
|---|---|---|---|
| U01 | Alto / bajo | Compositor compacto sin fila vacía; crecimiento por contenido y controles accesibles. | IMPLEMENTADO. Pixel medido: unos 116 → 60 dp; drawer/IME comprobados en vc159. |
| U02 | Alto / medio | Drawer con preview, fecha, búsqueda, fijado, menú y cierre reversible; proyecto propietario. | IMPLEMENTADO. Pixel: fijar/desfijar, renombrado con CANCELAR, cierre/Deshacer, búsqueda/limpiar e IME PASS. S25: apertura por gesto comprobada. Sin borrado irreversible. |
| U03 | Alto / medio | Lanzadera con prompt independiente, entradas por ranura, importación compacta y favoritos. | IMPLEMENTADO. Pixel vc159: NexusLibrary importación/miniatura/preview/retirada PASS. Layout final y foco comprobados en U05/F161. |
| U05 | Alto / medio | Principal fija adaptativa, controles habituales compactos y detalle en hojas/Avanzado; fallback de scroll cuando sea necesario. | IMPLEMENTADO y compilado. Pixel final: retrato, foto/fuente 1,5× y 800 × 533 + IME/foco PASS; hoja Duración sobre IME PASS. S25: formulario observado con entradas, chips, Avanzado y Generar sin scroll. Sin certificación de todas las ventanas/modelos. |
| C01 | Alto / medio | Registro con identidad, procedencia, fecha y soporte confirmado/no soportado/desconocido. | IMPLEMENTADO y probado. Contratos/adaptadores reales; sin inferir por marca ni inventar verificación remota. |
| C02 | Alto / medio | Restricciones compartidas UI/preflight/envío y configuración efectiva por modelo. | IMPLEMENTADO y probado. Condiciones, opciones, cardinalidades, ocultos y pasos exactos; desconocido bloquea con explicación. No es JSON Schema completo. |
| C03 | Alto / medio | Conservar prompt, preferencias y recursos activos/conservados. | IMPLEMENTADO. Huellas antes/después de instalar final iguales en ambos: prompt/negativo/parámetros/IDs/URI/conteos. Pixel: 72 caracteres, 1 activo; S25: 4908 caracteres, 3 activos y 2 conservados. Todos los IDs/filas originales de trabajos y outputs permanecen; límites del resto en informe. |
| C04 | Alto / medio | Diez escenarios de capacidades, incluidos dependencias, resolución por duración, desconocido, cambio incompatible y multimodelo. | IMPLEMENTADO; pruebas PASS dentro de las 209 finales. Fixtures reales y sintéticas identificadas; sin llamadas de pago. |
| C05 | Alto / medio | Transferir ajustes compatibles por contrato, conservando origen y fallback destino sin conversiones inventadas ni truncado. | IMPLEMENTADO y pruebas PASS, incluidas siete regresiones de persistencia/restore/ediciones async. Anotaciones en CreationDraft/configuración/Undo; fallback no se convierte en intención nueva. Pixel: cambio con foto y retorno por selector comprobados exactos; Snackbar «Volver» expiró, sin PASS físico de esa acción. |
| C06 | Alto / medio | Handoff por rol/MIME/capacidad y owner reversible para entradas no portables. | IMPLEMENTADO y pruebas PASS. IDs/URI/excedentes preservados; final separado de inicial; máscaras/ambigüedad protegidas. Comparison y ConstraintEngine/preflight rechazan owner ajeno; metadata excluida del payload. |
| P01 | Alta / bajo | Reconciliar fuente, firma, versión y datos. | IMPLEMENTADO hasta vc161. Misma APK instalada en ambos, certificado conservado e integridad SQLite ok. Originales de trabajos/outputs sin ausencias ni filas cambiadas; asistente 0 ausentes, 9/11 hashes cambiados y preferencias seguras distintas. |
| P02 | Alta / medio | Recorrer arranque, cuatro secciones, ajustes, estados y teclado. | IMPLEMENTADO con cobertura acotada: recorridos Pixel y cuatro pestañas finales S25 mediante imágenes/foco. Cambio/retorno por selector y logs finales cerrados en P09/F161. No se certifican todos los submenús/combinaciones; límites P04. |
| P03 | Alta / bajo | Tokens y escala común de color, radios, espacios, iconos y controles. | IMPLEMENTADO, revisado y compilado en componentes tocados. Cobertura física detallada en informe. |
| P04 | Alta / medio | Texto grande, contraste, tacto, semántica y adaptación compacto/ancho. | PENDIENTE aceptación física completa. Pixel final a 1,5× y wide/IME PASS; objetivos compartidos de 48 dp y contraste probado. S25: fuente 1,5× global, TalkBack y plegable sin comprobar. |
| P05 | Alta / bajo | Háptica central por acción/preferencias y deduplicación. | IMPLEMENTADO; regresiones PASS. Sensación/intensidad y S Pen físicos pendientes de aceptación, sin atribuirlos a tests. |
| P06 | Alta / medio | Insets, foco, teclado, cancelación, Atrás y deshacer. | IMPLEMENTADO. Núcleo: 4 PASS, locks liberados en finally; foco final estable en wide/IME y hoja de parámetros comprobada. Sin borrar originales. |
| P07 | Media / medio | Jank de causa medida, ciclo de vida y movimiento reducido. | IMPLEMENTADO. Runtime fuera de main, warmup cancelable y caché para 863 modelos. Muestra vc157: 633 frames / 6 janky (0,95 %), p95 10 ms. Arranque final puntual Pixel 1340 ms / S25 1506 ms; sin certificación de 60/120 Hz. |
| P08 | Alta / bajo | Compilar bloques, revisar diff y ejecutar regresiones en AXELPC. | IMPLEMENTADO. Final: 209 PASS = 205 app + 4 núcleo, sin fallos/errores/omitidas; alineación 16 KiB, firma conservada y 5 bibliotecas ARM64 idénticas a vc155. |
| P09 | Alta / bajo | Segunda auditoría independiente y última pasada tras corregir hallazgos. | IMPLEMENTADO. Últimas pasadas del alcance tocado sin otro bloqueo; 0 Fatal/ANR detectados en ventanas finales de ambos. Ownership, persistencia/carreras y foco corregidos. Sin certificación global del producto. |
| P10 | Alta / bajo | APK/hash/firma, instalación/versión/arranque en ambos, commit/push e informe. | IMPLEMENTADO artefacto e instalación vc161, fuente 2abd8d7 publicada/verificada. Informe y plan cerrados, publicados en la misma rama; SHA de fuente y APK en AUDIT_REPORT. |
| U04 / F161 | Alto / bajo | Validar principal/hojas/IME y flujo de cambio de modelo con recursos en ambos. | IMPLEMENTADO. Pixel: Kling 4 I2V → MiniMax H3 Ref preserva prompt de 72 caracteres e ID/URI, 1 activo / 0 conservados; regreso por selector a Kling 4 exacto en todas las huellas. Referencia QA retirada: 0/0 y textos/parámetros originales exactos. Snackbar «Volver» expiró, no probado físicamente; Undo cubierto por tests. Logs y configuración final comprobados. |
| D01 | Alto / irreversible | Pagos, Generar/LLM, reenvío histórico, borrado de originales/datos y desinstalación. | DESCARTADO para esta QA por prohibición/riesgo. Tres trabajos nuevos S25 externos, no atribuidos ni revertidos por la auditoría. |
| D02 | Medio / alto | Cambiar firma, secretos en Git, dependencias nuevas, refactor ajeno y motores/pesos nativos. | DESCARTADO por continuidad/alcance. El motor de capacidades sí fue autorizado; nativas reutilizadas con integridad verificada. |

## Evidencia y límites del checkpoint vc161

La comparación privada final confirma IDs/filas originales de trabajos/outputs, integridad SQLite y ningún archivo original del asistente ausente. Sus 9/11 hashes cambiados y los hashes distintos de preferencias seguras impiden afirmar contenido o secretos idénticos. Sin IDs exportados de proyectos/recursos, los conteos no prueban conservación individual. El borrador sí tiene todas sus huellas iguales durante la instalación final de ambos. Véase [AUDIT_REPORT.md](AUDIT_REPORT.md).

UIAutomator sigue dando null root en S25; se descartaron XML reutilizados de otra app y se usan imágenes/foco. Revisión final de logcat: Pixel 12 788 líneas y S25 235 950, con 0 Fatal/ANR detectados en las ventanas disponibles desde actualizar; intervalos en el informe. No se revirtió el trabajo concurrente detectado ni se borró el archivo original de la referencia QA.

Configuraciones temporales restauradas: Android fuente 1,0×, timeout 300000 ms, rotación automática 1/orientación de usuario 0; Pixel tamaño físico 1344 × 2992/densidad 480 y S25 1440 × 3120/override 560. Apariencia conservada: Pixel LIGHT; S25 AURORA y fuente de app ≈0,95.

TalkBack, sensación háptica, S Pen, plegable, todas las combinaciones visuales y cadencia sostenida de 60/120 Hz requieren aceptación física/medición específica. La ventana simulada del Pixel no acredita un plegable. El Snackbar «Volver» expirado queda NO COMPROBADO físicamente, con retorno por selector y Undo automatizado acreditados por separado. No hay nota 10 ni certificación global; las últimas pasadas del alcance tocado están cerradas sin otro bloqueo. No hay operaciones irreversibles pendientes de decisión humana.
