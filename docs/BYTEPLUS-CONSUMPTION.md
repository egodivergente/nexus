# Consumo BytePlus en Nexus Agent

Pantalla Compose integrada en Ajustes, con los componentes, navegación y tema existentes. El cerebro seleccionado no se cambia.

## Fuentes separadas

- **Nexus / este dispositivo:** el proveedor registra cada petición BytePlus antes de enviarla y liquida la reserva con el `usage` recibido, también en streaming. El registro comienza con esta versión; no reconstruye historial anterior. Cada reserva tiene un identificador y una segunda liquidación no duplica tokens. Las respuestas sin métricas y las tarifas ausentes permanecen sin dato.
- **Coste estimado:** usa las tarifas de entrada/salida del perfil BytePlus o, si faltan, la tarifa oficial sincronizada de ese modelo con menos de 24 horas. No utiliza las tarifas globales de otro cerebro ni descuenta cuotas gratuitas. Los tramos con precios diferentes y las unidades no reconocidas no se convierten en una tarifa plana. No representa cargos definitivos.
- **Consumo oficial:** `arkcli usage stats --start FECHA --end FECHA --by model`. El ámbito es la cuenta/proyecto del perfil administrativo, que puede contener otras aplicaciones. Retraso habitual de 5–30 minutos.
- **Facturación:** `arkcli billing list --start AAAA-MM --project= --interval day`, añadiendo `--day` en la vista diaria. Suma decimal de `PayableAmount` solo si el resultado está completo y la moneda es única. Si la cuenta no tiene la función split bill (caso real comprobado), se usa `ListBillDetail` mediante el SDK oficial 3.0.62 y las credenciales STS temporales de la misma sesión Ark CLI. Se consultan datos agrupados por cuenta con paginación completa, sin activar funciones de cuenta ni crear claves. `pretax_amount`, `tax` y `posttax_amount` se mantienen separados. Liquidación T+1; las cifras actuales pueden estar incompletas.
- **Cuotas:** `arkcli usage balance --type free-quota --modality LLM --page-size 20 --page-number N`. Lee hasta diez páginas y rechaza resultados incompletos. Muestra paquetes sin sumar su duplicado en `free_usage`; respeta remanentes negativos y unidades no informadas.
- **Saldo monetario:** `arkcli api billing.query_balance_acct --params {}` permite consultar `QueryBalanceAcct`; se muestra `Result.AvailableBalance` junto con su moneda. Es independiente de las cuotas gratuitas.

Las fuentes oficiales nunca se incorporan al libro local ni se suman a su estimación.

## Puente y sincronización

Se amplía el puente privado ASUS existente mediante `byteplus_admin.py` y una ruta autenticada `GET /api/byteplus/usage?period=day|month`. Conserva los controles Host, Origin y Bearer del portal. Android reutiliza su vinculación privada Flow; ninguna credencial administrativa BytePlus entra en la APK. El SDK financiero adicional se instala solo en el entorno del puente (`requirements-byteplus.txt`), sin otro servicio.

Ark CLI oficial `@byteplus/ark-cli` 1.0.40 verificado en ASUS. Requiere sesión administrativa propia (`arkcli auth login`); una clave de inferencia no equivale a acceso de facturación. No se enumeran ni crean claves. La consulta de consumo es de cuenta, sin atribuirla íntegramente a Nexus.

Caché persistente atómica por periodo y por fuente. Si falla una fuente, conserva su última lectura y el periodo original con estado desactualizado. Actualización manual con `refresh=1`; caché de servidor de 15 minutos para las consultas periódicas. WorkManager programa intervalos configurables de 15/30/60 minutos con conectividad; Android puede retrasarlos. Los errores y las respuestas vacías no se convierten en ceros.

## Límites y alertas

Controles opcionales por turno/día/mes en USD y llamadas diarias. Se reservan importes conservadores antes de enviar; las reservas inciertas sobreviven al reinicio. Si falta tarifa o no puede leerse/guardarse el registro, un límite monetario activo bloquea la llamada. Los rechazos conocidos no cuentan como gasto; una desconexión incierta conserva su reserva.

Los límites cubren exclusivamente las nuevas llamadas BytePlus que pasan por este proveedor Nexus en este dispositivo. No limitan otras apps, otros dispositivos ni el gasto total de la cuenta; tampoco cancelan peticiones ya enviadas. Una estimación no es un límite de facturación del proveedor.

Alertas diarias por tokens o coste estimado, visibles en pantalla y mediante notificación si Nexus tiene ese permiso. No se solicita ni concede el permiso silenciosamente.

La verificación del S25 distingue los presupuestos guardados de su activación efectiva. No se activa ningún límite ni se cambia el cerebro seleccionado. Las tarifas sincronizadas se derivan de `pricing models` (unidad K tokens a millón); no alteran el perfil del cerebro. Los datos financieros de la cuenta quedan en el dispositivo y en la validación privada, fuera de Git.

## Compilación y firma

Base reconciliada con el S25 real: `a4088da`, 1.9.57-agent-preview / vc91. La referencia anterior 1.9.54 / vc88 corresponde a `49fe173`. Esta actualización es 1.9.58-agent-preview / vc92, paquete `com.vidadomu.nexus.agent`.

La variante Agent usa el fichero privado `~/.gradle/nexus-agent-signing.properties` o `-PnexusAgentSigningFile=RUTA`, con `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Si falta, produce una salida sin firmar en lugar de sustituir la identidad instalada. No incluir ese fichero ni el almacén en Git.

Certificado instalado verificado SHA-256: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.

Comandos de validación sin inferencia pagada:

```text
gradlew.bat :agent-core:test :app:testAgentPreviewUnitTest --tests com.vidadomu.nexus.assistant.BytePlusPricingTest :app:assembleAgentPreview --no-daemon --max-workers=1 --no-parallel --console=plain -Pkotlin.compiler.execution.strategy=in-process
python -m unittest discover -s tools/flow-linux-probe -p test_byteplus_admin.py -v
python -m unittest discover -s tools/flow-linux-probe -p test_recovery.py -q
```

Referencias oficiales: [consumo](https://github.com/byteplus-sdk/ark-cli/blob/main/skills/arkcli-usage/references/arkcli-usage-stats.md), [cuotas](https://github.com/byteplus-sdk/ark-cli/blob/main/skills/arkcli-usage/references/arkcli-usage-balance.md), [facturación](https://github.com/byteplus-sdk/ark-cli/blob/main/skills/arkcli-billing/references/arkcli-billing-list.md).

## Lectura administrativa real validada

Se validaron consultas administrativas reales con SSO: consumo por modelo, cuotas, saldo monetario, tarifa y factura con impuestos. La cuenta de validación no admitía `billing list` por carecer de split bill; el SDK oficial resolvió la lectura sin activar esa función. Los importes y los identificadores de cuenta no se publican con el código.

Pruebas: 239 casos JVM, cero fallos y cuatro casos reales omitidos; cuatro pruebas de tarifas en Android, diez pruebas administrativas nuevas y 28 del puente. Se prueban liquidación idempotente, persistencia, concurrencia, reservas inciertas, ausencia de tarifa/uso, límites antes de enviar, streaming, caché desactualizada, respuestas incompatibles, paginación, unidades de precio y separación de impuestos/saldo/cuotas. No se enviaron inferencias de pago.

La variante Agent Preview omite el optimizador JVM de Kotlin (`-Xno-optimize`) para evitar su análisis patológico de boxing en funciones Compose grandes. No cambia los controles de gasto; Android conserva la optimización de ejecución de la aplicación.


## Entrega y revisión en dispositivo

Instalada la APK definitiva 1.9.58-agent-preview/vc92 en el S25 SM-S938B mediante actualización, con certificado idéntico al instalado. SHA-256 de la APK: `9e4233a44f8b040a93cc9ab3ddfdbd9560f208c043b4b273cad84e387f2270f1`; se comparó también con el APK realmente instalado.

Se revisó la navegación desde Ajustes, las vistas de día/mes, los importes y fuentes oficiales, las cuotas, las tarifas y los controles de sincronización, alertas y límites. Se probó la actualización manual real; las cinco fuentes quedaron disponibles y persistidas. La caché sobrevivió a una nueva actualización/reinicio del proceso. La cabecera de esta página oculta el saldo general de Atlas para no confundirlo con BytePlus. No se observaron excepciones fatales en el proceso durante la revisión.

Se conservaron las preferencias del asistente y los perfiles de cerebros (comparación SHA-256 antes/después); Antigravity permaneció seleccionado. El presupuesto general existente estaba desactivado: sus cifras guardadas no constituían límites efectivos. No se activaron límites ni alertas en el dispositivo del usuario. El cálculo de consumo, el bloqueo previo, los errores, la deduplicación y la persistencia se validaron con proveedores simulados; no se provocó una inferencia facturable ni una notificación de umbral real en el S25. La programación periódica se comprobó en la configuración y en la sincronización inicial, sin esperar cada intervalo Android.

Compilación final en AXELPC satisfactoria. Un estado incremental truncado de `mergeJavaRes` se apartó en la validación privada y se regeneró, sin limpiar el proyecto ni tocar otros trabajos. Informes, capturas, datos financieros y copia de seguridad del dispositivo permanecen fuera de Git.
