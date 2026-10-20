# Sincronización Nexus — Pixel, S25 y AXELPC

## Resultado solicitado

Proyectos y originales antiguos y nuevos compartidos mediante Google Drive,
con copias locales, reintentos y conservación de versiones concurrentes.
Las copias manuales anteriores no deben generar entradas duplicadas visibles.

## Plan y estado del 20 de octubre de 2026

1. [x] Inspeccionar fuentes, instalación Desktop y dispositivos físicos.
2. [x] Implementar el motor común de revisiones y originales SHA-256.
3. [x] Integrar autorización y transporte Drive en Windows y Android.
4. [x] Añadir sincronización automática/manual, estado y selección de proyectos.
5. [x] Probar conflictos, reintentos, deduplicación e integridad con Drive real.
6. [x] Crear los clientes OAuth autorizados y conectar los tres dispositivos.
7. [x] Integrar las actualizaciones concurrentes de Desktop y Android.
8. [ ] Completar despliegue de APK97: S25 instalado; Pixel conserva96 y requiere ADB.
9. [x] Verificar originales reales en ambos sentidos entre los tres dispositivos.
10. [x] Publicar el código en las ramas del encargo y verificar sus SHA remotos.

## Instalaciones y continuidad

AXELPC ejecuta `2026.10.20-device-continuity.2` en el puerto 63896, con el
perfil existente y la continuidad cifrada integrada. La fuente publicada
b236cae conserva Corte y Drive con su núcleo y runtime compilados juntos.
Se verificaron 294 archivos del paquete y la conservación de todos los IDs
al activarlo. El perfil, los medios y las credenciales se mantienen.

La APK unificada 1.9.63-agent-preview/vc97, fuente 401c5ac, integra Drive
16bbcde, Studio y los turnos en segundo plano cb0871f. Está instalada en el S25
con la firma existente, UID y todos los IDs conservados. SHA-256 del APK:
`49874ff7c95649df809882e21edf0b98418f0b72be980d48bac490df867f8244`.

El Pixel conserva 1.9.62-background-preview/vc96. Su depuración inalámbrica se
desactivó durante la compilación; no hay ADB disponible para instalar97 ni para
repetir la prueba física conjunta. Ya se solicitó reconexión USB/inalámbrica.
La fuente y el APK97 están preparados; no se declara instalada esa versión en
el Pixel. La actualización anterior95 y la prueba bidireccional con originales
reales ya se habían completado en ambos móviles.

La APK contiene tanto `NexusDriveSyncWorker` como `DeviceSyncService`.
La conexión directa y Drive comparten el bloqueo del índice; una edición
recibida durante el trabajo de Drive provoca reintento, conservando la edición.

Una instalación de otra tarea sustituyó previamente la APK con Drive por una
versión que solo contenía continuidad directa. Se comprobó la ausencia de la
clase en el DEX instalado. La integración unificada corrige esa pérdida de función.
La versión 95 añade transferencia prolongada con notificación y recuperación
del trabajo periódico fallido. Ambos servicios de Drive se verificaron en
primer plano en los teléfonos después de instalarla.

## Datos y duplicados

- Las rutas locales se sustituyen por referencias portables a bytes verificados.
- Se suben originales disponibles antes de publicar sus metadatos portables.
- Un original inaccesible o un enlace caducado queda identificado como pendiente.
- Se conservan los IDs y referencias anteriores. Dos IDs con los mismos bytes
  producen una sola entrada visible por proyecto, sin borrar sus originales.
- La equivalencia jpg/jpeg con el mismo SHA-256 no crea conflictos falsos.
- Las versiones concurrentes con contenido distinto se conservan para revisión.
- Los chats de Drive son historial inerte; no reactivan autorizaciones ni planes.
- Drive transporta documentos Corte y su historial; Desktop los abre.
- Las claves de proveedores, de firma y sesiones no se incorporan al diario Drive.
  La continuidad directa de móviles conserva su configuración independiente.

Windows intenta sincronizar cada 30 segundos cuando el agente está disponible.
Android solicita trabajo tras cambios y al volver a la aplicación; su trabajo
periódico tiene un intervalo mínimo de 15 minutos, condicionado por Android,
la red seleccionada y el almacenamiento disponible. Los proyectos seleccionados,
el activo y los medios sin proyecto conservan sus originales locales.

## Validación

- APK97 unificada: compilación completa satisfactoria; 707 pruebas unitarias
  correctas (305 núcleo, 402 app), 4 omitidas y ningún fallo. La repetición
  física conjunta está pendiente del Pixel. La versión95 anterior pasó651.
  El núcleo de sincronización pasó28 pruebas enfocadas. Incluye 20 casos del motor Drive y la
  protección de una edición recibida por la conexión directa durante un ciclo.
- Motor y runtime JVM Desktop conjunto: 394 pruebas correctas, 4 omitidas.
- Continuidad cifrada Windows: 26 pruebas correctas; integridad del host: 14.
- OAuth y configuración Desktop: 11 pruebas Python correctas, incluida renovación
  de autorización después de preparar originales antiguos.
- Adaptador del agente: 6 casos correctos; Corte: 7; transporte de proyectos: 3.
- Prueba real con Drive entre dos perfiles Desktop aislados: proyecto, PNG,
  montaje Corte, deshacer y segundo ciclo idempotente, con integridad verificada.
- Prueba real de versiones concurrentes y subida/descarga con SHA-256 coincidente.
  Las carpetas de prueba aisladas se enviaron a la Papelera al terminar.
- Ambos móviles mantienen la cuenta y la misma carpeta de Drive tras actualizar;
  se ha observado el nuevo trabajador ejecutándose y nuevas subidas de originales.
  Recorrido físico Pixel → Drive → Windows y original de Windows → Drive →
  ambos móviles: PASS. Se compararon los SHA-256 reales y las identidades,
  utilizando archivos existentes ausentes de los destinos antes de sincronizar.
  En la prueba inversa los móviles utilizan sus carpetas locales de Drive,
  descartando atribuir al transporte Drive una copia recibida por el peer.
- Se verificó una sola fila por identidad del archivo de seguimiento. Los
  originales verificados no producen grupos duplicados por SHA en los móviles.
  La validación adicional encontró 84 pares de punteros HTTPS idénticos en el
  mismo proyecto; el cierre 16bbcde añade su deduplicación sin inventar bytes
  ni borrar IDs históricos. Sus 28 pruebas enfocadas pasan. La comprobación
  física posterior confirma0 grupos de URI visible duplicada en Windows .2
  y S2597, conservando los IDs. Pixel no se ha vuelto a medir tras desconectarse.
- La comparación de generaciones conserva rutas sin prueba y contenidos
  diferentes; solo normaliza referencias con SHA verificados coincidentes y
  outputs equivalentes del mismo proyecto. Las pruebas cubren orden y cantidad.
- La primera pasada observó referencias antiguas sin original accesible. No se
  afirma que todas estén recuperadas ni que toda la biblioteca esté respaldada.

La recuperación histórica ha rescatado cuatro originales (tres PNG de Runway
y un MP4 de6,08s), 15.881.875bytes vinculados a siete identidades ya existentes
mediante la caché nativa. No añadió filas ni ejecutó generaciones. Los tres PNG
se descargaron de Drive y se verificaron por SHA; en Windows cada uno conserva
sus dos IDs históricos y una sola fila visible. El MP4 también está guardado y
verificado por HTTP y descargado de Drive con SHA coincidente. Los cuatro
originales recuperados están respaldados en la carpeta compartida de Nexus.
Los tres PNG se comprobaron además en S2597: bytes SHA correctos, todos sus IDs
retenidos y una única fila visible para cada original.

El MP4 se recuperó del cuerpo HTTP guardado por Coil2.7 en el S25: coincidencia
exacta de SHA256(URL), Content-Length completo, firma MP4 y decodificación
íntegra de vídeo. Se contrastó el formato de la caché con el código oficial de
[RealDiskCache](https://raw.githubusercontent.com/coil-kt/coil/2.7.0/coil-base/src/main/java/coil/disk/RealDiskCache.kt)
y [HttpUriFetcher](https://raw.githubusercontent.com/coil-kt/coil/2.7.0/coil-base/src/main/java/coil/fetch/HttpUriFetcher.kt).
El índice no se modifica para añadir esa caché. Otras90 referencias consultadas
siguen pendientes; no equivalen a90 archivos originales únicos.

Las copias recuperadas sobreviven al reinicio de Windows .2. También se comprobó
una consulta real del agente mientras Drive preparaba medios: esa preparación
HTTP ya no retiene su bloqueo. La continuidad cifrada coordinada ha verificado
20/20 hashes de adjuntos de chats en Windows, conservando identidades e historial
como datos inertes.
La prueba S25↔Windows también verificó una clave creada y actualizada en Windows,
una skill editada en S25 y los dos archivos AGY idénticos. Las pruebas
sintéticas se retiraron; Drive y continuidad quedaron activados.

Los resultados de pruebas aisladas no equivalen a demostrar que toda la
biblioteca histórica esté respaldada. Los informes privados y copias de
recuperación permanecen fuera de Git.

## Google y política de privacidad

Clientes «Nexus Sync · AXELPC» y «Nexus Sync · Android» creados en el proyecto
autorizado, con API Drive habilitada y permiso exclusivo `drive.file`.
Los tres dispositivos están autorizados en la misma cuenta. Windows cifra
tokens y configuración con DPAPI; Android utiliza Google Identity.
No se modificaron las credenciales ni dominios del cliente web preexistente.

OAuth sigue en Prueba. La consola mantiene deshabilitado «Publicar app» y solicita completar la marca;
las URL de web y política pública siguen vacías.
En este estado los refresh tokens para Drive caducan a los siete días:
https://developers.google.com/identity/protocols/oauth2#expiration

El usuario no dispone de web y solicitó preparar la política para publicar
después. El borrador está en `docs/privacy/nexus-sync.html`; no está publicado.
