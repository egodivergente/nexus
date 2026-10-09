# Inventario de interfaz Android

Fuente: worktree android-agent-references/NEXUS, base `4c16a1a`, versión 1.8.4-agent, vc28. Rama del rediseño: `codex/ui-minimal-20261009`.

Cada número cuenta las pulsaciones para llegar al control desde su pantalla, sin contar escribir, desplazarse, elegir un archivo en Android ni confirmar una acción. **Pendiente** no significa verificado: la columna de comprobación se completa durante cada fase con evidencia de código y de dispositivo. No se ejecutan generaciones ni operaciones de pago para verificar controles.

## Lienzo

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Elegir modelo con búsqueda/filtros | Tarjeta abre Modelos; hoja local existente | Fila de modelo → hoja | 1 | Pendiente |
| Explorar catálogo completo | Tarjeta de modelo | Hoja → Todos los modelos | 2 | Pendiente |
| Comparar varios modelos y ver lote en curso | Botón bajo modelo | Chip Comparar | 1 | Pendiente |
| Editar prompt | Campo bajo medios | Campo principal bajo modelo | 1 | Pendiente |
| Inspiración y presets creativos | Desplegable Inspiración creativa | Chip Inspirar → presets | 2 | Pendiente |
| Estilos creativos | Parámetros según schema | Hoja Parámetros | 1 | Pendiente |
| Imagen de entrada / quitar / cambiar a edición | Adaptador de medios | Fila de medios | 1 | Pendiente |
| Último fotograma / quitar | Adaptador de medios duales | Fila de medios | 1 | Pendiente |
| Añadir / quitar imágenes de referencia | Adaptador MULTI_IMAGE | Fila de miniaturas y + | 1 | Pendiente |
| Vídeo / audio de entrada y archivos dinámicos | Selectores del contrato | Fila de medios, conserva slots | 1 | Pendiente |
| Ver metadatos locales de medios | Etiquetas bajo archivo | Detalles del medio | 1 | Pendiente |
| Parámetros dinámicos y booleanos | Lista siempre visible | Chip resumen → hoja | 1 | Pendiente |
| Predeterminado / restaurar / menú por parámetro | Pulsación sobre título del parámetro | Hoja → título o menú | 2 | Pendiente |
| Relación de aspecto | Control dinámico / selector existente | Hoja Parámetros | 1 | Pendiente |
| Opciones avanzadas | Más opciones | Hoja Parámetros → sección | 2 | Pendiente |
| Avisos de compatibilidad | Ver avisos | Hoja Parámetros | 1 | Pendiente |
| Reintentar opciones | Texto y botón | Aviso con Reintentar | 1 | Pendiente |
| Diagnóstico local DEBUG / copiar JSON | Icono Terminal | Menú ··· → Diagnóstico | 2 | Pendiente |
| Mis ajustes: guardar/aplicar/borrar presets | Icono Tune → hoja | Chip Mis ajustes → hoja | 1 | Pendiente |
| Precio: detalle / reintento | Barra inferior con iconos | Coste tocable → detalle y reintento | 2 | Pendiente |
| Generar / motivo de bloqueo | Barra inferior | Barra inferior fija | 1 | Pendiente |

## Historial y costes

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Buscar por prompt/modelo | Campo fijo | Icono Buscar → campo | 1 | Pendiente |
| Todos / En curso / Completados / Fallidos / Comparativas, contadores | Chips | Chips compactos | 1 | Pendiente |
| Abrir resultados | Botón Ver | Tocar fila | 1 | Pendiente |
| Preparar de nuevo | Botón de tarjeta | Menú de fila → Preparar | 2 | Pendiente |
| Consultar estado/coste | Icono Refresh de tarjeta | Menú de fila → Actualizar | 2 | Pendiente |
| Diagnóstico: ids/proveedor/modelo/USD/error/traza | Botón Diagnóstico → diálogo | Menú de fila → Diagnóstico | 2 | Pendiente |
| Lotes y Ver comparativa | Tarjeta de lote | Grupo plegable → Ver comparativa | 2 | Pendiente |
| Recargar historial fallido | Texto y Reintentar | Aviso con Reintentar | 1 | Pendiente |
| Costes / saldo / actualizar / recibos por resultado / filtros | Pestaña Costes | Chip saldo → hoja Saldo y gasto | 1 | Pendiente |
| Abrir resultado desde recibo | Ver en recibo | Hoja saldo → recibo | 2 | Pendiente |
| Costes como acceso desde Historial | Pestaña | Entrada secundaria Costes | 1 | Pendiente |

## Modelos, familias y opciones

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Explorar familias → modelos → variantes/tareas | Catálogo jerárquico | Misma jerarquía compacta | 1–2 | Pendiente |
| Imagen/Vídeo/Audio/Texto y Más | Chips de tipo | Chips de tipo | 1–2 | Pendiente |
| Buscar modelo o función | Campo fijo | Búsqueda compacta | 1 | Pendiente |
| Favoritos: ver / añadir / quitar | Cabecera y tarjetas/menú | Icono y menú de tarjeta | 1–2 | Pendiente |
| Con referencias / todos los usos de imagen | Chip | Chip | 1 | Pendiente |
| Más nuevos / solo fecha verificada | Chips | Filtros compactos | 1–2 | Pendiente |
| Orden personal / lanzamiento / otros órdenes | Filtros → menú | Filtros → menú | 2 | Pendiente |
| Filtrar proveedor y uso | Filtros | Hoja Filtros | 1–2 | Pendiente |
| Quitar filtros activos | Texto bajo filtros | Hoja Filtros | 1–2 | Pendiente |
| Personalizar / ordenar arrastrando o pulsación larga | Menú y asa de tarjeta | Menú y asa | 2 | Pendiente |
| Ocultar variante / familia, confirmar / cancelar | Menú contextual y diálogo | Mismos menús | 1–2 | Pendiente |
| Gestionar / restaurar modelos ocultos | Menú → Modelos ocultos | Menú → Modelos ocultos | 2 | Pendiente |
| Lista/cuadrícula y tamaño compacto/amplio | Menú catálogo | Mismo menú | 2 | Pendiente |
| Actualizar catálogo/tarifas | Menú y filtros | Menú → Actualizar | 2 | Pendiente |
| Aviso de catálogo sin conexión | Línea en lista | Aviso de una frase, expansible | 0–1 | Pendiente |
| Ver precio y reintentar cotización | Tarjetas y pie de opciones | Precio alineado y detalle | 1–2 | Pendiente |
| Elegir tarea/variante/proveedor y Usar modelo | ModelOptionsScreen | Opciones compactas, misma selección | 1–2 | Pendiente |
| Detalles, consejos de uso/evitar, fuentes de lanzamiento y tarifas | Ficha de opciones | Detalles plegables | 1–2 | Pendiente |

## Biblioteca

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Cuadrícula de resultados / abrir visor | Tarjetas con texto | Cuadrícula de medios, 3 columnas | 1 | Pendiente |
| Buscar prompt/modelo | Campo fijo | Icono Buscar → campo | 1 | Pendiente |
| Todos/Imagen/Vídeo/Audio | Chips | Chips | 1 | Pendiente |
| Contador de archivos y creaciones | Subtítulo | Texto secundario bajo filtros | 0 | Pendiente |
| Abrir Alexia | Icono | Icono de cabecera | 1 | Pendiente |

## Visor

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Elegir entre varios resultados | Chips | Chips sobre medio | 1 | Pendiente |
| Imagen con zoom / vídeo / audio con tiempos | Visor | Medio a todo el ancho | 1 | Pendiente |
| Error de carga y reintento | Aviso bajo imagen | Aviso y Reintentar | 1 | Pendiente |
| Animar / Remezclar / Compartir enlace | Botones bajo medio | Fila de acciones con icono/etiqueta | 1 | Pendiente |
| Descargar/guardar copia / cancelar transferencia / progreso | Acción de exportación | Menú o fila de acciones | 1–2 | Pendiente |
| Abrir Alexia | Botón bajo medio | Acción secundaria | 1 | Pendiente |
| Copiar texto de respuesta | Botón Copiar | Botón Copiar | 1 | Pendiente |
| Metadatos: modelo/proveedor/coste/id | Tarjeta visible | Detalles plegables | 1 | Pendiente |
| Volver | Flecha | Flecha | 1 | Pendiente |

## Asistente y proyecto

| Función | Dónde está hoy | Dónde estará | Toques | Comprobación |
|---|---|---|---:|---|
| Cambiar proyecto / Sin proyecto / crear proyecto | Chip y menú | Proyecto → hoja completa | 1–2 | Pendiente |
| Descripción/sinopsis/tono/estilo/reglas/modelos preferidos | AlertDialog Memoria | Proyecto → hoja completa | 1 | Pendiente |
| Personajes / notas / borrar notas / guardar memoria | AlertDialog Memoria | Proyecto → hoja completa | 1–2 | Pendiente |
| Importar archivos de proyecto | Botón bajo proyecto | Proyecto → Importar | 2 | Pendiente |
| Ver/reusar/renombrar referencias | Carrusel desplegable | Proyecto → referencias | 1–2 | Pendiente |
| Asistente/Copiloto/Agente | Chips | Selector compacto | 1 | Pendiente |
| Explicación de modo / aviso de planificador local | Texto fijo | Ayuda de modo / aviso plegable | 1 | Pendiente |
| Nueva conversación | Icono | Menú → Nueva conversación | 2 | Pendiente |
| Conversaciones guardadas / abrir | Icono → menú | Menú → Conversaciones | 2 | Pendiente |
| Ajustes LLM: proveedor/modelo/clave/URL/temperatura/tokens/imágenes/exclusiones | Diálogo de ajustes | Menú → ajustes LLM | 2 | Pendiente |
| Skills: recargar/errores/indexar historial | Diálogo de ajustes | Ajustes LLM | 2 | Pendiente |
| Mensajes propios / respuestas / contexto de escena/skill disponible | Burbujas | Burbuja propia, respuesta limpia | 0 | Pendiente |
| Adjuntar/quitar/enviar/detener respuesta y sugerencias de modelo | Barra de entrada | Barra inferior tipo píldora | 1 | Pendiente |
| Seguir último mensaje | Botón inferior | Botón inferior cuando procede | 1 | Pendiente |
| Propuestas de organización: aplicar/descartar/resultado | Tarjeta | Tarjeta compacta | 1 | Pendiente |
| Plan: estado/modelo/coste | Tarjeta compacta y detalle | Resumen del plan | 0 | Pendiente |
| Plan: prompt editable/aplicar/deshacer/conflicto de edición | Detalles → tarjeta completa | Nivel 2 del plan | 1 | Pendiente |
| Plan: prompt original | Tarjeta detallada | Detalles → Original | 2 | Pendiente |
| Plan: justificación/alternativas/medios/parámetros editables/notas | Tarjeta detallada | Detalles plegables | 1–2 | Pendiente |
| Plan: adjuntar/cambiar referencias | Tarjeta compacta | Acción del plan | 1 | Pendiente |
| Plan: incluir/quitar del lote | Checkbox | Selector del plan | 1 | Pendiente |
| Plan: Generar individual / lote seleccionado | Detalles y barra de lote | Generar individual y barra fija de lote | 1 | Pendiente |
| Protecciones: aplicar/deshacer, asistente ocupado, envío incierto y anti doble pago | Estados habilitados y avisos | Mismas condiciones y avisos | 0 | Pendiente |
| Abrir en Lienzo / Descartar / Ver resultado | Tarjeta detallada y compacta | Acciones secundarias | 1–2 | Pendiente |
| Agente: estado/latencia/trabajos/herramientas/diagnóstico | Barra de estado | Estado compacto → detalles | 1 | Pendiente |

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
