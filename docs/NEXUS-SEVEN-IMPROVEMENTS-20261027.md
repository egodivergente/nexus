# Siete mejoras de Nexus Agent · 27/10/2026

Encargo: `Nexus_7_mejoras_actualizado-1.docx`, NEX-UX-001–007.
Estado: implementación de presentación y controles; aceptación física pendiente.
Base: `42b8d8e` de `claude/ux-auditoria-20261027`, que incorpora 1.9.97/vc133.
Rama propia: `codex/nexus-seven-improvements-20261027`, creada desde main y avanzada
hasta esa base conservando su historial. Main seguía en 1.9.29/vc63 y no era una
base representativa del trabajo Android reciente. La captura `74858.jpg` aportada por el usuario confirma Nexus 1.9.97/vc133,
Samsung SM-S938B y Android 17/API 37. Es evidencia visual del diagnóstico; el
puente S25 sigue offline y no permite contrastarlo mediante ADB ni probar cambios.
Versión de esta entrega: 1.9.98-agent-preview/vc134.

## Implementación y aceptación

| ID | Cambio concreto | Estado y límite |
| --- | --- | --- |
| NEX-UX-001 | `ChatPresentation` normaliza las respuestas guardadas y en streaming: resúmenes, consejos y decisiones usan texto normal; avisos, resultados, código, tablas y paneles interactivos conservan estructura funcional. | Implementado; pruebas de contenido y compatibilidad. No modifica el texto guardado ni intenta borrar supuestas repeticiones mediante heurísticas. |
| NEX-UX-002 | Retirado el botón y el callback de voz en directo del compositor. Se conserva el dictado y la grabación/revisión del micrófono convencional. | Implementado; prueba instrumentada añadida, pendiente de ejecución física. |
| NEX-UX-003 | Menos recuadros editoriales y menor intensidad del degradado de mensajes propios (12–6 %). Los avisos/resultados comparten el tamaño de cuerpo del diálogo. | Implementado; contraste final con fondo, temas y preferencias pendiente de medición en pantalla. |
| NEX-UX-004 | Aurora azul/cian/violeta mediante tres gradientes radiales suaves, sin capas de desenfoque ni sensores. Dibujo limitado a 12,5 Hz; no recompone mensajes por fotograma. | Implementado. Respeta `ui.background`, intensidad, `ui.motion`, animaciones del sistema, ciclo RESUMED y ahorro de batería. Con movimiento desactivado queda una composición estática; fotos personales conservan su velo de legibilidad. Temas claros mantienen su fondo claro. Consumo y fluidez no medidos en el S25. |
| NEX-UX-005 | Hover de stylus pasivo en logos, chips y botones con ayuda: elevación de 2 dp, inclinación máxima de 1,2° y escala de 1,015 con retorno amortiguado. | Implementado sin consumir eventos ni emitir vibración por hover. La política de movimiento se observa una vez por tema, no una vez por logo. Háptica de acciones usa el mecanismo existente y respeta preferencias. Sensación física y salida del área pendientes de probar con S Pen real. |
| NEX-UX-006 | Protección real del repositorio frente a reenvíos del mismo ID: mutex acotados, recibo SHA-256 de toda la petición y consulta del trabajo persistido. Una excepción de validación de respuesta ya no acredita rechazo previo al cobro. Motor `VerifiedRouteExecution` ordena totales comparables y ejecuta fallback únicamente tras reconciliar rechazo previo a aceptación. | **Parcial.** El motor se prueba con evidencia sintética y no está conectado a los adaptadores comerciales. No se activa selección automática, deduplicación nueva por equivalencia ni fallback real sin evidencia de contratos, precios totales, saldo/cuota y equivalencia exacta. |
| NEX-UX-007 | Fotogramas inicial y final en columnas cuando hay al menos 320 dp útiles y escala tipográfica ≤1,3; miniaturas cuadradas ocupan el ancho disponible. Cambiar/Biblioteca son controles de 48 dp con etiquetas accesibles. | Implementado; otras pantallas y texto grande conservan disposición vertical y scroll. No se promete que cualquier formulario quepa sin desplazamiento. El botón Generar mantiene el dock y los insets existentes. Teclado, navegación y altura final pendientes de aceptación física. |

## Enrutamiento: lo que falta para cerrar NEX-UX-006

El catálogo actual muestra grupos lógicos, pero eso no acredita intercambiabilidad
de sus hojas. No se usa la similitud del nombre ni `canonicalRouteKey` como prueba
para cambiar una API. Se conserva la implementación existente del catálogo.

Crun cotiza en créditos cuyo coste depende de la compra; Fal expone una tarifa
base de BiRefNet V2; las cotizaciones de Atlas pueden ser base o total según el
endpoint. No son importes completos universalmente comparables. Tampoco hay un
adaptador común que certifique equivalencia de todos los parámetros, referencias
y defaults entre esas APIs. La evidencia Fal/Atlas existente es consultiva.

El motor nuevo exige identidad exacta, revisión de contrato, recibo de configuración,
equivalencia de calidad explícita, total para toda la petición en la misma moneda,
saldo suficiente, cuota suficiente y vigencia. Desconocido no significa disponible
ni gratuito. Variantes Turbo o de otra identidad quedan excluidas. Cambiar el orden
de referencias invalida los recibos. No se inventan conversiones de créditos.

Antes de cada intento se debe guardar el proveedor elegido. Después de un rechazo
anterior a aceptación, se consulta al mismo proveedor. Un trabajo ya creado se
recupera; un timeout, error de servidor o resultado incierto detiene el flujo. Las
trazas son datos técnicos opcionales. Una integración comercial debe guardar esas
trazas y los IDs remotos de forma duradera antes de habilitar el motor; este documento
no afirma que esa integración esté entregada.

La captura también muestra «Nébula» con texto mal codificado. Se corrigen los
nombres españoles de versión en la configuración de compilación para que la
entrega vc134 muestre «Nébula» correctamente. No se modifica compileSdk/targetSdk
por el hecho de que el dispositivo ejecute API 37.

La protección del repositorio ya está integrada tanto en los envíos del agente como
en Lanzadera. Es por identificador de trabajo: dos peticiones explícitas con IDs
diferentes siguen siendo dos generaciones. No puede garantizar que un proveedor
no cobre dos veces internamente; evita que Nexus repita automáticamente un envío
incierto. Los trabajos antiguos sin recibo no autorizan reenviar su ID.

## Validación

Ver el resultado de ejecución al pie de este documento. No se han hecho llamadas
comerciales de generación, instalaciones ni cambios de datos en el teléfono.
La APK Agent se produce sin firma si falta la configuración privada, conforme al
contrato del proyecto. No se crea otra clave para suplantar la identidad instalada.

## Criterio de calidad solicitado

La comparación con aplicaciones de grandes empresas se convierte en criterios
verificables: coherencia entre turnos, 48 dp de interacción, legibilidad con texto
ampliado, ausencia de solapamientos con IME/barras, navegación accesible, movimiento
reducido, háptica por acción aceptada y ausencia de reenvíos con cobro incierto.
El tamaño de una empresa no es una prueba de calidad. Esta entrega no puede
certificarse como experiencia premium final sin comprobar en el S25:

1. Capturas antes/después del chat largo y de la Lanzadera con dos fotogramas,
   teclado abierto, pantalla dividida, orientación horizontal y texto ampliado.
2. TalkBack y áreas táctiles; contraste de texto normal y controles en todos los
   temas afectados. El fondo nunca debe competir con la lectura.
3. S Pen: entrada/salida de hover, pulsación, arrastre de logos y regreso elástico;
   la vibración procede del teléfono y solo de acciones reales.
4. Frame timing con pantalla a su frecuencia configurada, desplazamiento de chat
   y catálogo; comparar aurora animada/estática con brillo y condiciones iguales.
5. Consumo y temperatura en una sesión sostenida; confirmar ausencia de dibujo
   decorativo en segundo plano, ahorro de batería y animaciones desactivadas.
6. Proveedores con respuestas simuladas primero, incluyendo 402/429, error después
   de aceptar, proceso interrumpido y recuperación, antes de cualquier prueba real
   de pago solicitada específicamente.

No se añaden propuestas de expansión mientras la aceptación física y la integración
comercial del punto 006 sigan pendientes.

### Estado previo de las pruebas de navegación

La ejecución de GitHub Actions de la base (`40591794829`, SHA `42b8d8e`)
ya fallaba en tres pruebas: `ScreenNavigationTest`, `ScreenInitializationTest` y
`OutputExportSpecTest`. Esperaban cinco destinos con Flow en la barra, mientras
que la base ya lo había ocultado explícitamente y mostraba cuatro destinos.
Se actualizan esas expectativas conservando las comprobaciones de rutas, acceso
a Ajustes fuera de la barra e inicialización independiente. No se cambia la
navegación de producción como parte de este encargo.
