# Comparación de rutas Fal y Atlas

## Alcance y frontera de confianza

`agent-core/.../routing/comparison` es Kotlin/JVM puro. No cambia el router, Android,
Studio, Gradle ni los catálogos. Reutiliza `registry.ModelRef`; no reutiliza
`RouteCandidate.score`, `ModelSummary.basePriceUsd` ni `CostEstimate` como prueba
suficiente: no identifican por sí solos moneda, versión, contrato, configuración
completa, cantidad y total con todos los cargos.

El módulo **evalúa evidencia suministrada**, no descarga ni autentica documentación
oficial. Un adaptador fiable debe verificarla antes de construir los objetos. No
se incluyen precios, capacidades ni equivalencias reales precargados. Los datos de
las pruebas son fixtures sintéticos, nunca una tabla de tarifas utilizable.

## Entrada

- `ProviderRoute.key`: `ModelRef`, identidad canónica **publicada** con editor,
  modelo y versión, endpoint y revisión/digest del contrato. No quitar sufijos,
  agrupar familias ni transformar aliases para fabricar una coincidencia. Dos
  rutas con la misma identidad todavía necesitan `EquivalenceEvidence`.
- `identityEvidence`: fuente, fecha y justificación del vínculo entre endpoint e
  identidad exacta. Si falta una versión verificable, se deja sin acreditar.
- `RequestedConfiguration`: modalidad canónica (`image`, `video`, `audio`, `text`, `3d`), capacidades exactas,
  parámetros efectivos y cantidad total de resultados. Incluye todos los defaults,
  calidad, seed, relación de aspecto, codec, negativos, pasos, filtros, etc. cuando
  apliquen. `complete=false` por defecto. No eliminar parámetros desconocidos.
  Vídeo exige `duration_seconds`, `resolution`, `fps`, `audio` (`true`/`false`);
  imagen exige `resolution`. Para otras modalidades manda el contrato completo.
- `references`: lista **ordenada** de roles (primer/último frame, referencia,
  máscara, audio, vídeo, etc.), modalidad e identificador opaco de contenido.
  Una referencia distinta, otro rol o reordenación invalida el alcance.
- `inputDigest`: digest/ID opaco estable de la petición de contenido completa,
  incluidos prompt y entradas; no guardar prompts, medios ni URLs firmadas aquí.
  Impide reutilizar un precio de otra petición con iguales dimensiones.
- `EndpointContract`: modalidad, capacidades, parámetros obligatorios y campos
  sin resolver. `ConfigurationReceipt` acredita validación **de la combinación
  completa**, ligada al `RouteKey` y a esa configuración. El adaptador valida
  rangos/opciones/dependencias, cantidad, formatos y roles de referencias contra
  el schema real. No basta `supportsImageInput=true`, una lista de resoluciones
  ni dos opciones válidas individualmente. Si no se sabe validar, no emitir recibo.
- `EquivalenceEvidence`: par de endpoints/revisiones + configuración exacta +
  evidencia explícita de ejecución equivalente **sin degradación de calidad**.
  La igualdad del modelo o la resolución no crea este recibo. No certificarlo por
  marketing, velocidad, un sufijo o puntuaciones inferidas. Una atestación negativa
  de calidad para el mismo par/configuración bloquea aunque sea antigua: el
  adaptador debe reconciliar la contradicción, no descartarla para anunciar ahorro.
- `AtlasCatalog`: inventario fresco de Atlas, estado de actualización y
  disponibilidad; para acreditar ausencia, debe ser completo y **sin filtrar los
  ocultos/no disponibles**. `ModelRegistry.list()` solo devuelve visibles y
  ejecutables y **no basta para afirmar ausencia**. El adaptador debe conservar
  entradas ocultas en este snapshot, no volver a mostrarlas en UI.

Todos los recibos deben invalidarse al cambiar endpoint, revisión, modelo,
configuración, referencias o cantidad. Son objetos de valor; usar colecciones
inmutables/snapshots, no modificarlas durante o después de evaluar. El módulo no
interpreta schemas propietarios ni traduce por su cuenta nombres de parámetros.
La normalización del adaptador debe estar respaldada por documentación, no por
similitud textual; `8` y `8.0` no se equiparan automáticamente.

## Precios y vigencia

Solo `PriceBasis.REQUEST_TOTAL` es comparable, con importe `BigDecimal`, moneda
ISO 4217 en mayúsculas, `includesAllMandatoryCharges=true`, fuente y fecha.
El total cubre **la misma petición y todo el lote**. Incluye suplementos de audio,
resolución, mínimos de cobro y cargos obligatorios. No representa saldo, descuento
publicitario, tarifa «desde», intervalo o estimación incompleta.

`COMPUTE_SECOND`, `VIDEO_SECOND`, `UNIT`, `BASE`, `UNKNOWN` se rechazan, incluso con
cantidad 1. No se multiplica duración de vídeo por segundos de cómputo ni se usa
`priceUnitVerified` para convertir una tarifa en total. El adaptador puede aportar
un total calculado solo cuando cuenta con todas las unidades y reglas verificadas
y las documenta en la evidencia; si falta consumo de cómputo, no hay total.
`null` no es cero. Cero explícito y acreditado sí puede compararse. No usar `Double`
para dinero ni aplicar redondeos de presentación antes de decidir si el ahorro es estrictamente
positivo; solo se permite la regla de liquidación explícita de la conversión.

`FreshnessPolicy` no fija duraciones comerciales: el integrador proporciona edad
máxima separada de cotizaciones, catálogo, evidencia de contratos/equivalencia y
conversión, además de tolerancia futura. `Clock` es obligatorio e inyectable y se
consulta una sola vez por evaluación. El límite exacto se acepta; más viejo o más
futuro se rechaza. `PriceObservation.validUntil`, si lo da el proveedor, es exclusivo: al llegar a
esa fecha ya ha vencido. No puede preceder/igualar la observación ni extender quoteMaxAge. Fuente o
justificación vacías tampoco son evidencia.

Monedas diferentes requieren una única `CurrencyConversion` directa de Fal a la
moneda de Atlas: `totalFal * rate + feeInTargetCurrency`, con tasa positiva, cargos
no negativos, todos los costes incluidos, fuente, fecha y método documentado. No
se invierte una tasa ni se encadenan conversiones implícitamente. Cualquier regla
de redondeo se suministra explícitamente con `roundingScale` (0–12) y `roundingMode`, ambos o
ninguno. Sin ambos se acredita cálculo exacto sin redondeo; con ambos se aplica al total convertido,
no a precios de igual moneda. No se impone redondeo a céntimos: hay cargos legítimos inferiores al
céntimo. Solo usar DOWN/FLOOR u otra regla que reduzca el importe si esa es la liquidación realmente
documentada, nunca para fabricar ahorro. Si la liquidación o un coste adicional no pueden expresarse
con este contrato, no afirmar ahorro. La conversión y las cotizaciones pueden tener fechas
distintas, todas dentro de su propia vigencia; no se afirma simultaneidad. Cotizaciones
múltiples para la misma petición se rechazan: el adaptador debe reconciliarlas
antes, sin escoger retrospectivamente la más barata.

## Decisiones y presentación

| `kind` | Significado |
|---|---|
| `MODEL_ABSENT_IN_ATLAS` | La identidad/versión exacta no consta en un inventario completo, vigente y sin filtrar; Fal ofrece una ruta elegible para la petición. No implica precio menor ni mejor calidad. |
| `EQUIVALENT_WITH_VERIFIED_SAVINGS` | Los pares equivalentes comprobados tienen total Fal estrictamente inferior y no quedan rutas candidatas coincidentes sin resolver. |
| `EQUIVALENT_WITHOUT_SAVINGS` | Existe al menos una ruta Atlas equivalente y comprobada cuyo total es menor o igual al de Fal. No escoger una ruta Atlas más cara para anunciar ahorro. |
| `INSUFFICIENT_EVIDENCE` | No se puede concluir: la salida enumera las carencias o incompatibilidades concretas. No equivale a rechazo comercial permanente. |

`catalogPresence` separa presencia/ausencia/desconocido del resultado económico.
Una entrada del modelo oculto cuenta como presente, pero no se compara ni se ofrece.
Una actualización fallida, proveedor no disponible o identidad sin acreditar nunca
produce ausencia. Una entrada ajena con identidad sin acreditar también bloquea ausencia y ahorro
global: podría ocultar otra ruta del modelo. El snapshot parcial conserva **hechos de pares
conocidos**, pero bloquea la decisión global de ahorro verificado. Nunca acredita cobertura completa
ni «el más barato de Atlas».

`facts` y `unverified` son listas distintas con `ReasonCode` y texto en español.
`comparisons` retiene los dos precios originales, fuente/fecha, conversión usada,
evidencia de equivalencia y ahorro firmado por cada ruta Atlas. Si otra ruta queda
sin resolver, pueden existir hechos de pares válidos dentro de una decisión global
insuficiente. La UI debe nombrar la ruta concreta, no convertir esos hechos en un
superlativo sobre todos los proveedores. `complementsAtlas` solo se activa para
modelo ausente o ahorro verificado; **no es una orden de selección**.

La calidad prevalece: no hay score de calidad, ranking por velocidad, sustitución
automática, llamadas de generación ni confirmación de gasto. Si conservar calidad
no está explícitamente acreditado, la política no declara equivalencia económica.

## Integración mínima (datos ya verificados por adaptadores)

```kotlin
import com.vidadomu.nexus.agent.core.routing.comparison.*
import java.time.Clock

fun assessRoute(
    fal: ProviderRoute,
    atlas: AtlasCatalog,
    request: RequestedConfiguration,
    equivalence: List<EquivalenceEvidence>,
    totals: List<PriceObservation>,
    freshness: FreshnessPolicy,
    fx: List<CurrencyConversion> = emptyList(),
    clock: Clock = Clock.systemUTC(),
): RouteDecision = ProviderRoutePolicy(freshness, clock).evaluate(
    fal = fal,
    atlas = atlas,
    requested = request,
    equivalences = equivalence,
    prices = totals,
    conversions = fx,
)
```

Los IDs de proveedor por defecto son `fal` y `atlas_cloud`; se pueden inyectar al
constructor si el adaptador registra Fal con otro ID exacto. La función no hace
I/O ni reemplaza la autorización habitual antes de generar. El otro agente integra
estas entradas/salidas con Android y Studio sin necesitar cambios a este router.

## Pruebas

```sh
./gradlew :agent-core:test --tests '*routing.comparison.*'
```

Pruebas JVM deterministas con `Clock.fixed`, sin Android, credenciales ni llamadas
a proveedores. El comando del repositorio puede configurar el proyecto Android
raíz, pero la política y sus tests solo usan JVM. No ejecutar en ASUSVIDAL.
