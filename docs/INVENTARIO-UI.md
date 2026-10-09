# Inventario de interfaz Android

Fuente: worktree android-agent-references/NEXUS, base `4c16a1a`, versión 1.8.4-agent, vc28. Rama del rediseño: `codex/ui-minimal-20261009`.

Cada número cuenta las pulsaciones para llegar al control desde su pantalla, sin contar escribir, desplazarse, elegir un archivo en Android ni confirmar una acción. **Pendiente** no significa verificado: la columna de comprobación se completa durante cada fase con evidencia de código y de dispositivo. No se ejecutan generaciones ni operaciones de pago para verificar controles.

## Lienzo

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Elegir modelo con búsqueda/filtros | Tarjeta abre Modelos; hoja local existente | Fila de modelo → hoja | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Explorar catálogo completo | Tarjeta de modelo | Menú ··· → Todos los modelos | 2 | Código y captura 1.3; regresiones de formulario en verde |
| Comparar varios modelos y ver lote en curso | Botón bajo modelo | Chip Comparar | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Editar prompt | Campo bajo medios | Campo principal bajo modelo | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Inspiración y presets creativos | Desplegable Inspiración creativa | Chip Inspirar → presets | 2 | Código y captura 1.3; regresiones de formulario en verde |
| Estilos creativos | Presets y parámetros según schema | Inspirar → estilos; parámetros en su hoja | 1–2 | Código y captura; valores de prompt conservados |
| Imagen de entrada / quitar / cambiar a edición | Adaptador de medios | Medios → selector; quitar en miniatura | 1–2 | Código; editor original y callbacks conservados |
| Último fotograma / quitar | Adaptador de medios duales | Medios → selector; quitar en miniatura | 1–2 | Código; editor original y callbacks conservados |
| Añadir / quitar imágenes de referencia | Adaptador MULTI_IMAGE | Miniaturas para quitar; Añadir → editor | 1–2 | CanvasMinimalUiTest: tres referencias conservadas |
| Vídeo / audio de entrada y archivos dinámicos | Selectores del contrato | Medios → editor del contrato con sus slots | 2 | Código; máscara opcional visible en captura |
| Ver metadatos locales de medios | Etiquetas bajo archivo | Medios → ficha con metadatos locales | 1 | Código; mismo inspector local |
| Parámetros dinámicos y booleanos | Lista siempre visible | Chip resumen → hoja | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Predeterminado / restaurar / menú por parámetro | Pulsación sobre título del parámetro | Hoja → restaurar directo o menú | 2 | CanvasMinimalUiTest: restaura 42 desde 99 |
| Relación de aspecto | Control dinámico / selector existente | Hoja Parámetros | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Opciones avanzadas | Más opciones | Hoja Parámetros, sección visible | 1 | CanvasMinimalUiTest: opción avanzada existe |
| Avisos de compatibilidad | Ver avisos | Hoja Parámetros | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Reintentar opciones | Texto y botón | Aviso con Reintentar | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Diagnóstico local DEBUG / copiar JSON | Icono Terminal | Menú ··· → Diagnóstico o Copiar diagnóstico | 2 | Código; mismo JSON sanitizado sin prompt ni rutas |
| Mis ajustes: guardar/aplicar/borrar presets | Icono Tune → hoja | Chip Mis ajustes → hoja | 1 | Código y captura 1.3; regresiones de formulario en verde |
| Precio: detalle / reintento | Barra inferior con iconos | Coste tocable → detalle y reintento | 2 | Código y captura 1.3; regresiones de formulario en verde |
| Generar / motivo de bloqueo | Barra inferior | Barra inferior fija | 1 | Captura 1.3; CreationReadiness y envío sin cambios |

## Historial y costes

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Buscar por prompt/modelo | Campo fijo | Icono Buscar → campo | 1 | Código y capturas 1.3; consulta y filtros originales conservados |
| Todos / En curso / Completados / Fallidos / Comparativas, contadores | Chips | Chips compactos | 1 | Código y capturas 1.3; consulta y filtros originales conservados |
| Abrir resultados | Botón Ver | Tocar fila completada; tarea pendiente abre diagnóstico | 1 | Código; conserva disponibilidad original de resultados |
| Preparar de nuevo | Botón de tarjeta | Menú de fila → Preparar | 2 | HistoryMinimalUiTest: devuelve la misma tarea sin crear trabajos |
| Consultar estado/coste | Icono Refresh de tarjeta | Menú de fila → Actualizar | 2 | Código y capturas 1.3; consulta y filtros originales conservados |
| Diagnóstico: ids/proveedor/modelo/USD/error/traza | Botón Diagnóstico → diálogo | Menú de fila → Diagnóstico | 2 | HistoryMinimalUiTest: diálogo e ID de tarea visibles |
| Lotes y Ver comparativa | Tarjeta de lote | Grupo plegable → Ver comparativa | 2 | Código y capturas 1.3; consulta y filtros originales conservados |
| Recargar historial fallido | Texto y Reintentar | Aviso con Reintentar | 1 | Código y capturas 1.3; consulta y filtros originales conservados |
| Costes / saldo / actualizar / recibos por resultado / filtros | Pestaña Costes | Chip saldo → hoja Saldo y gasto | 1 | Código y capturas 1.3; consulta y filtros originales conservados |
| Abrir resultado desde recibo | Ver en recibo | Hoja saldo → recibo | 2 | Código y capturas 1.3; consulta y filtros originales conservados |
| Costes como acceso desde Historial | Pestaña | Chip Saldo y gasto de la cabecera | 1 | BalanceSheetUiTest; ledger accesible desde la hoja |

## Modelos, familias y opciones

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Explorar familias → modelos → variantes/tareas | Catálogo jerárquico | Misma jerarquía compacta | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Imagen/Vídeo/Audio/Texto y Más | Chips de tipo | Chips de tipo | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Buscar modelo o función | Campo fijo | Búsqueda compacta | 1 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Favoritos: ver / añadir / quitar | Cabecera y tarjetas/menú | Chip Favoritos y estrella en opciones | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Con referencias / todos los usos de imagen | Chip | Chip | 1 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Más nuevos / solo fecha verificada | Chips | Filtros compactos | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Orden personal / lanzamiento / otros órdenes | Filtros → menú | Hoja Filtros → elección directa | 2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Filtrar proveedor y uso | Filtros | Hoja Filtros | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Quitar filtros activos | Texto bajo filtros | Hoja Filtros | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Personalizar / ordenar arrastrando o pulsación larga | Menú y asa de tarjeta | Menú y asa | 2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Ocultar variante / familia, confirmar / cancelar | Menú contextual y diálogo | Mismos menús | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Gestionar / restaurar modelos ocultos | Menú → Modelos ocultos | Menú → Modelos ocultos | 2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Lista/cuadrícula y tamaño compacto/amplio | Menú catálogo | Mismo menú | 2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Actualizar catálogo/tarifas | Menú y filtros | Menú → Actualizar | 2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Aviso de catálogo sin conexión | Línea en lista | Aviso de una frase que ajusta las líneas | 0–1 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Ver precio y reintentar cotización | Tarjetas y pie de opciones | Precio alineado y detalle | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Elegir tarea/variante/proveedor y Usar modelo | ModelOptionsScreen | Opciones compactas, misma selección | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |
| Detalles, consejos de uso/evitar, fuentes de lanzamiento y tarifas | Ficha de opciones | Detalles plegables | 1–2 | Ubicación verificada en código; búsqueda, filtros, ocultos y selección probados en emulador |

## Biblioteca

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Cuadrícula de resultados / abrir visor | Tarjetas con texto | Cuadrícula de medios, 3 columnas | 1 | Código y LibraryMinimalUiTest / MultiOutputTest |
| Buscar prompt/modelo | Campo fijo | Icono Buscar → campo | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Todos/Imagen/Vídeo/Audio | Chips | Chips | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Contador de archivos y creaciones | Subtítulo | Texto secundario junto a búsqueda | 0 | Revisado en código; pruebas de Biblioteca/Visor |
| Abrir Alexia | Icono | Icono de cabecera | 1 | Revisado en código; pruebas de Biblioteca/Visor |

## Visor

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Elegir entre varios resultados | Chips | Chips sobre medio | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Imagen con zoom / vídeo / audio con tiempos | Visor | Medio a todo el ancho | 1 | MultiOutputTest + MediaPreviewUiTest; sonido no probado |
| Error de carga y reintento | Aviso bajo imagen | Aviso y Reintentar | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Animar / Remezclar / Compartir enlace | Botones bajo medio | Fila de acciones con icono/etiqueta | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Descargar/guardar copia / cancelar transferencia / progreso | Acción de exportación | Botón Descargar y progreso con Cancelar | 1–2 | Revisado en código; pruebas de Biblioteca/Visor |
| Abrir Alexia | Botón bajo medio | Acción secundaria | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Copiar texto de respuesta | Botón Copiar | Botón Copiar | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Metadatos: modelo/proveedor/coste/id | Tarjeta visible | Detalles plegables | 1 | Revisado en código; pruebas de Biblioteca/Visor |
| Volver | Flecha | Flecha | 1 | Revisado en código; pruebas de Biblioteca/Visor |

## Asistente y proyecto

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Cambiar proyecto / Sin proyecto / crear proyecto | Chip y menú | Proyecto → hoja completa | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Descripción/sinopsis/tono/estilo/reglas/modelos preferidos | AlertDialog Memoria | Proyecto → hoja completa | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Personajes / notas / borrar notas / guardar memoria | AlertDialog Memoria | Proyecto → hoja completa | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Importar archivos de proyecto | Botón bajo proyecto | Proyecto → Importar | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Ver/reusar/renombrar referencias | Carrusel desplegable | Proyecto → referencias | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Asistente/Copiloto/Agente | Chips | Selector compacto | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Explicación de modo / aviso de planificador local | Texto fijo | Ayuda de modo / aviso plegable | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Nueva conversación | Icono | Proyecto → Nueva conversación | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Conversaciones guardadas / abrir | Icono → menú | Proyecto → conversaciones | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Ajustes LLM: proveedor/modelo/clave/URL/temperatura/tokens/imágenes/exclusiones | Diálogo de ajustes | Icono Ajustes del asistente → hoja | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Skills: recargar/errores/indexar historial | Diálogo de ajustes | Ajustes LLM | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Mensajes propios / respuestas / contexto de escena/skill disponible | Burbujas | Burbuja propia, respuesta limpia | 0 | Destino y callback revisados; nueve regresiones del Asistente |
| Adjuntar/quitar/enviar/detener respuesta y sugerencias de modelo | Barra de entrada | Barra inferior tipo píldora | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Seguir último mensaje | Botón inferior | Botón inferior cuando procede | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Propuestas de organización: aplicar/descartar/resultado | Tarjeta | Tarjeta compacta | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: estado/modelo/coste | Tarjeta compacta y detalle | Resumen del plan | 0 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: prompt editable/aplicar/deshacer/conflicto de edición | Detalles → tarjeta completa | Nivel 2 del plan | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: prompt original | Tarjeta detallada | Detalles → Original | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: justificación/alternativas/medios/parámetros editables/notas | Tarjeta detallada | Detalles plegables | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: adjuntar/cambiar referencias | Tarjeta compacta | Acción del plan | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: incluir/quitar del lote | Checkbox | Selector del plan | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Plan: Generar individual / lote seleccionado | Detalles y barra de lote | Generar individual y barra fija de lote | 1 | Destino y callback revisados; nueve regresiones del Asistente |
| Protecciones: aplicar/deshacer, asistente ocupado, envío incierto y anti doble pago | Estados habilitados y avisos | Mismas condiciones y avisos | 0 | Destino y callback revisados; nueve regresiones del Asistente |
| Abrir en Lienzo / Descartar / Ver resultado | Tarjeta detallada y compacta | Acciones secundarias | 1–2 | Destino y callback revisados; nueve regresiones del Asistente |
| Agente: estado/latencia/trabajos/herramientas/diagnóstico | Barra de estado | Ajustes del asistente → estado y métricas | 1 | Destino y callback revisados; nueve regresiones del Asistente |

## Ajustes y Flow

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Apariencia: seis temas / restaurar predeterminado | Sección y pulsación | Sección Apariencia y menú | 1–2 | Pendiente |
| Moneda / restaurar / tasa BCE / fecha / ejemplo / actualizar | Sección Moneda | Sección Moneda | 1–2 | Pendiente |
| Atlas: saldo efectivo/bonos/congelado / actualizar | Tarjeta cuenta | Sección Cuenta y hoja saldo | 1 | Pendiente |
| Atlas: modificar clave / probar conexión / feedback | Sección proveedor | Sección Proveedores | 1–2 | Pendiente |
| Crun/Runway: clave enmascarada / modificar / saldo / prueba | Tarjetas proveedor | Sección Proveedores | 1–2 | Pendiente |
| Modo Mock | Switch | Sección Pruebas | 1 | Pendiente |
| Diagnóstico sistema/dispositivo | Desplegable inline | Subpantalla Diagnóstico | 1 | Pendiente |
| Flow: vincular ASUS / cancelar / abrir portal autenticado | FlowRecoverySettings | Sección Google Flow | 1–2 | Pendiente |
| Crear en Flow | Botón Ajustes | Sección Flow → Crear | 1 | Pendiente |
| Flow: prompt / validación vacío / contador y límite | FlowCreateScreen | Campo principal | 1 | Pendiente |
| Flow: Imagen/Vídeo según capacidades / disponibilidad | Chips | Chips | 1 | Pendiente |
| Flow: estado de conexión / detalle / actualizar | Tarjeta y cabecera | Aviso y Actualizar | 1–2 | Pendiente |
| Flow: avisos de ajustes/coste del proyecto | Tarjeta inferior | Detalles plegables | 1 | Pendiente |
| Flow: Generar / enviando / bloqueo / volver / ver resultado | Pie fijo y navegación | Pie fijo, mismas condiciones | 1 | Pendiente |
| Referencias Flow del agente y Lienzo / identificación y bloqueo de errores | Medios del plan / formulario MULTI_IMAGE | Mismos medios reubicados | 1–2 | Pendiente |

## Global

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Ver saldo Atlas/Crun y estados sin configurar/no disponible | Cabecera global doble | Chip saldo → hoja | 1 | Código + BalanceSheetUiTest; formatos USD/créditos en BalancePresentationTest |
| Hora consulta / último dato desactualizado | Subtítulo por proveedor | Hoja saldo | 1 | Ubicación verificada en código; matriz con saldos ficticios pendiente |
| Actualizar saldos | Icono de cabecera | Hoja saldo → Actualizar | 2 | BalanceSheetUiTest, sin cambios de ids de trabajos |
| Abrir Ajustes | Cabecera global o asistente | Icono de barra superior | 1 | BalanceSheetUiTest; cabecera específica de Asistente pendiente de fase 7 |
| Cinco destinos y conservar borradores/conversación | Barra inferior | Barra inferior con indicador en píldora | 1 | Cinco destinos en código; regresión Asistente→Ajustes→Modelos→Asistente verificada en emulador |

## Decisiones que preservan funciones

- El código tiene **seis** temas, no cuatro: se mantienen Océano y Bosque, además de los cuatro solicitados para la matriz principal de QA.
- `FlowCreateScreen` es la creación con el proyecto activo, sin controles nuevos de proveedor. Las referencias existentes se alcanzan desde Lienzo y Asistente; no se inventan funciones ni se cambian contratos.
- Las tarifas solo se convierten a una unidad temporal común cuando la unidad publicada permite esa conversión. Imagen/audio/tokens y unidades desconocidas conservan su unidad explícita. Nunca se interpreta una tarifa desconocida como segundos.
- Las condiciones que habilitan Generar, las huellas del plan, la selección del lote y el bloqueo de envíos inciertos permanecen intactos.
- Las capturas con datos reales se guardan en `outputs/ui-minimal-20261009/before`, fuera de Git. Las capturas publicables se realizarán con datos de QA sin claves ni medios personales.
