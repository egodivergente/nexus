# Lanzadera por capacidades

## Flujo real

`ModelCatalog → ModelCapabilityRegistry → ComposerState / CreateUiState →
CapabilityConstraintEngine → DynamicFormRenderer → GenerationDispatcher →
NativeGenerationRequest / adaptadores existentes`.

El catálogo sigue siendo el repositorio existente. El registro adapta `NexusModel`,
los contratos de cada proveedor y el `Input` ya analizado por `NativeModelSchema`.
No hay un segundo catálogo, ni formularios por marcas, ni otra implementación de
los proveedores o del cálculo económico.

La identidad incluye proveedor, ID de la variante en Nexus, endpoint efectivo
(`contractEndpoint` o `atlasEndpoint`) y modalidad. Las variantes conservan sus
propios campos, límites y preferencias. Nunca se intersectan todos los modelos
para formar un formulario con el mínimo común.

## Datos y procedencia

- `CONFIRMED`: contrato de esquema analizado o declaración explícita del adaptador.
- `UNSUPPORTED`: capacidad ausente en un contrato representado o modalidad incompatible.
- `UNKNOWN`: sin contrato suficiente o con reglas que aún no se pueden representar.
- La procedencia distingue `PARSED_SCHEMA`, `DECLARED_ADAPTER` y `UNVERIFIED`.
  La fecha es la observación existente del contrato; si no consta, permanece nula.
  Cargar datos locales no equivale a verificar nuevamente la documentación remota.
- Los máximos desconocidos permanecen nulos. El límite de lote del selector Android
  es una protección local de importación, nunca una capacidad atribuida al proveedor.
- Enumeraciones, rangos, requisitos, cardinalidades, longitud/unidad del prompt y
  dependencias se derivan de ese destino. Un tamaño sugerido localmente no se
  presenta como un enum publicado.

## Estado y conservación

`CreateUiState` conserva el estado de pantalla y persistencia. `ComposerState`
proyecta el trabajo hacia el motor puro. `attachedMedia` contiene solo entradas
activas; `retainedMedia` mantiene los recursos incompatibles o no disponibles.
Ambos grupos se guardan en el borrador y se muestran diferenciados. Los recursos
conservados no se suben ni forman parte de la solicitud.

Cambiar de modelo conserva el prompt actual íntegro y combina las referencias
guardadas sin duplicarlas. Al regresar se recuperan los parámetros válidos y las
asignaciones de recursos de esa configuración. No se restaura silenciosamente un
prompt antiguo. El cambio es reversible. Aplicar presets, restaurar parámetros y
cambiar modalidad reconcilia las dos particiones sin borrar archivos.

Las preferencias y presets nuevos se separan por identidad completa. Se conserva
lectura de los formatos anteriores; los originales no se borran. La eliminación
explícita de una referencia del borrador no borra el archivo original.

## Presentación y extensiones

`DynamicFormRenderer` organiza entradas y parámetros. `PromptEditor` y
`GenerateActionBar` completan el flujo modelo → prompt → entradas → parámetros →
generar. Los controles reciben esquemas y valores; no identifican marcas.

`DynamicControlRegistry.Provide` registra renderers por clave nativa o tipo
semántico mediante un `CompositionLocal` acotado. Incluye imagen, inicio/final,
referencias, vídeo, audio, duración, resolución, relación de aspecto, FPS, audio
generado, semilla y avanzados. Los renderers nativos existentes son el fallback.

El coste usa el motor existente y los parámetros efectivos de la configuración.
`CostEstimator` presenta la estimación recibida; no inventa una tarifa ni interpreta
un coste desconocido como cero. Las confirmaciones económicas existentes siguen
aplicándose.

## Validación y límites explícitos

La pantalla y la última comprobación anterior a subir archivos utilizan
`CapabilityConstraintEngine`. El envío utiliza su proyección efectiva: los valores
ocultos se conservan en el borrador y quedan fuera de la petición. Los adaptadores
existentes siguen realizando sus comprobaciones finales.

Se representan condiciones declarativas `if/then/else` con valores constantes,
enumeraciones y cardinalidad; requisitos, límites, longitudes y extensiones
`x-depends-on`/`x-hidden`. No se afirma soporte completo de JSON Schema. Los
operadores no representados y las entradas estructuradas obligatorias sin editor
producen un estado desconocido y una explicación antes de enviar.

Las reglas particulares de adaptadores que ya existían se mantienen en su ruta
actual; no se han añadido formularios por familias en el compositor.

Las pruebas usan snapshots de contratos versionados y fixtures sintéticas del
motor claramente identificadas. Una fixture sintética comprueba el mecanismo;
no acredita una capacidad comercial de ningún modelo real. No realizan pagos ni
generaciones.
