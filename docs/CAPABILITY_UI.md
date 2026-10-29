# Lanzadera por capacidades

## Flujo real

`ModelCatalog → ModelCapabilityRegistry → ComposerState / CreateUiState →
CapabilityConstraintEngine → FixedLauncherWorkspace / renderers compactos → GenerationDispatcher →
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

El cambio conserva los ajustes actuales que el contrato de destino admite, sobre
su borrador o preferencias guardadas. Se comprueba primero la combinación completa
y sus dependencias; si no es válida se conserva un subconjunto válido, sin
aproximaciones, redondeos ni conversiones de unidades. Las equivalencias se limitan
a claves nativas y semánticas inequívocas. Los ajustes adaptados se explican en
Avanzado; un valor de respaldo no sustituye la preferencia original al volver, salvo
que el usuario lo haya editado. Las respuestas de esquemas anteriores no pueden
sobrescribir el destino o las ediciones actuales.

`ParameterTransferSource` conserva también la procedencia de las adaptaciones,
fuera de los valores transferibles. Esa metadata se hereda entre modelos mediante
la misma clave nativa o equivalencia semántica inequívoca, y sigue guardándose en
`CreationDraft` y `ModelWorkDraft`. Así, A 1080p → B 720p de respaldo → C 720p de
respaldo → A recupera los 1080p guardados, incluso si se reabre C. El respaldo no
se convierte en intención por atravesar otro modelo. Una edición explícita,
también al mismo valor de respaldo, limpia el marcador de esa clave; editar otra
clave no lo limpia. Los marcadores no introducen valores en la petición ni evitan
la validación conjunta. No se infiere herencia para mapeos ambiguos o no representados.

Los archivos se trasladan por su rol declarado, MIME y cardinalidad, aunque cambie
la clave de entrada entre proveedores. El borrador original conserva sus bindings.
Máscaras y campos ambiguos mantienen un propietario de binding con proveedor,
modelo y endpoint; una coincidencia accidental de nombres no los convierte en
otra entrada. Esta metadata es privada del borrador y no se proyecta a MediaInput
ni a los payloads del proveedor.

Las preferencias y presets nuevos se separan por identidad completa. Se conserva
lectura de los formatos anteriores; los originales no se borran. La eliminación
explícita de una referencia del borrador no borra el archivo original.

## Selección por familia y variante

ArtBox y el selector de Lanzadera agrupan la presentación por familia. Dentro de
cada familia se elige una variante del catálogo existente; no se crea un modelo
sintético ni se combinan sus contratos, capacidades o precios. `CatalogModelGroup`
y `modelGroupTitle` conservan sus claves, igual que las preferencias de producto,
las etiquetas personalizadas, la ocultación y los favoritos por ruta real.

`CatalogFamilyPresentation` resuelve la variante actual por proveedor e ID reales,
sin identificar modelos por similitud del nombre. El acceso compacto puede
continuar directamente a otra variante solo cuando una única ruta coincide con
el proveedor, el conjunto de modalidades y el conjunto completo de flujos
actuales. Sin ruta actual solo continúa si la variante tiene una única ruta.
Las ambigüedades llevan a la elección explícita. El selector completo muestra los
flujos y proveedores de cada variante aunque exista esa continuación rápida;
ninguna ruta alternativa queda escondida por la selección actual. La selección
final entrega el `NexusModel` real y mantiene la identidad de configuración con
proveedor, modelo, endpoint y modalidad.

Los flujos se obtienen de `catalogWorkflows`, usando las capacidades y la
identidad completa de ruta: alias nativo del proveedor, ID local de Nexus y
endpoint de contrato. Un alias compartido no oculta una ruta local de edición
de vídeo bajo «Desde texto». Las etiquetas usan el flujo real; si dos opciones
siguen teniendo la misma presentación se distinguen mediante el ID local,
sin sustituir la identidad ni modificar el routing por el texto mostrado.

Los filtros de búsqueda, favoritos, ocultación y disponibilidad se aplican antes
de agrupar. En ArtBox, búsqueda y favoritos mantienen la entrada por familia;
Personalizar y Herramientas conservan su jerarquía existente. El último producto
de una familia se recuerda con una preferencia acotada a la modalidad elegida en
el navegador y a la familia, sin reutilizar la primera modalidad de una ruta como
si fuera ese contexto.

La sesión de ArtBox posee una colección `rememberSaveable` de URI y MIME,
compartida entre variantes, proveedores y flujos. `CatalogMediaSelectionSaver`
guarda esas referencias; no duplica archivos ni concede nuevos permisos de URI.
La cardinalidad y el MIME del primer campo de imagen solo proyectan qué recursos
se muestran activos y cuáles conservados. Pasar a texto o a un límite menor no
recorta la colección; una sustitución deja el recurso anterior conservado y la
retirada explícita elimina únicamente esa referencia de la selección.

Al continuar hacia Lanzadera, `prepareWithMedia` recibe toda la selección y
prepara cada URI mediante la persistencia privada existente. Conserva también
el pool previo del borrador. La asignación inicial se limita al primer campo de
imagen real y a su capacidad: el excedente permanece conservado, sin adquirir
implícitamente el papel de fotograma final, máscara u otro campo. Sin campo
compatible se conserva todo. La identidad de destino, la solicitud de esquema
y la sesión de selección impiden adjuntar un resultado obsoleto, incluido
A → B → A. Una copia fallida no borra recursos previos ni los éxitos del lote;
el feedback informa el resultado parcial y no inicia una generación.

La implementación de este bloque y sus correcciones corresponden a U08/vc165–168.
Sus pruebas de código,
builds y aceptación física se registran por separado en AUDIT_PLAN/AUDIT_REPORT;
la arquitectura no acredita por sí sola instalación ni QA final.

## Presentación y extensiones

`DynamicFormRenderer` y la presentación compacta comparten los controles de entradas
y parámetros. `FixedLauncherWorkspace` organiza la principal; `PromptEditor` y
`GenerateActionBar` completan el flujo modelo → prompt → entradas → parámetros →
generar. Los controles reciben esquemas y valores; no identifican marcas.

La principal usa una composición fija con resúmenes de entradas y chips de ajustes
comunes. La gestión completa de archivos, el editor ampliado del prompt y Avanzado
se abren en hojas. En ancho suficiente se usan dos columnas. El desplazamiento de
respaldo depende del teclado y de si la altura mínima medida supera la disponible;
la escala de fuente, por sí sola, no desactiva la expansión ni fuerza scroll. El
prompt conserva su nodo y su rama de layout al entrar IME. Los propietarios de
hojas no se retiran por el teclado de otro editor.

`FixedLauncherWorkspace` mide la altura base de cada bloque y descuenta la
extensión ya aplicada para que no se convierta en un mínimo creciente. El espacio
sobrante aumenta el viewport editable del prompt y las previsualizaciones reales,
sin aumentar botones, márgenes ni tamaño de letra. En una columna, los previews
pueden recibir hasta el 60 % del sobrante, limitado por su capacidad según el ancho
real; el resto va al editor. Sin prompt, los previews pueden usar todo el sobrante
que admitan. En dos columnas se calcula el margen útil de cada una por separado.

Los campos vacíos permanecen compactos. Cuando un par de fotogramas admite
presentación horizontal y exactamente uno está poblado, ese preview ocupa todo el
ancho; la ranura vacía y otras entradas vacías se agrupan de forma compacta. Con
cero o dos fotogramas poblados se mantiene el par si cabe. La regla usa campos y
estado, sin marcas; los renderers personalizados conservan su ruta de extensión.
La capacidad de crecimiento procede del ancho efectivo y de cuántas miniaturas
comparten la fila; no se estira una ranura vacía para igualarla al preview.

Esta distribución es la implementación de layout de vc164; su aceptación física
se registra en AUDIT_PLAN/AUDIT_REPORT y no se deduce de la compilación. No cambia
cardinalidades, parámetros, archivos conservados, precios ni solicitudes.

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

Para una entrada multimedia opcional, `minItems` se comprueba cuando hay archivos
en ese campo; su ausencia no lo convierte en obligatorio. Si el contrato o una
condición activa exige el campo, el mínimo sigue aplicándose. Aportar un archivo
con mínimo dos continúa siendo inválido, igual que superar el máximo o usar un
MIME incompatible. Se mantiene el mínimo publicado y no se recortan los recursos.

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


## Cierre técnico vc169

El pool familiar y el último Inbox incorporado se guardan por separado: una importación nueva tiene prioridad, un replay no reordena ni resucita retirados. La clasificación de visibilidad usa caché acotada por snapshot completo y revisión de colocación; una copia de esquema invalida la entrada, sin cambiar payload. 288 pruebas app PASS; QA física parcial y límites en AUDIT_REPORT.md.
