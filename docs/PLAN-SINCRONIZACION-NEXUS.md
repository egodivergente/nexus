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
8. [x] Instalar la APK unificada 1.9.61-sync-preview/vc95 en Pixel 8 Pro y S25.
9. [x] Verificar originales reales en ambos sentidos entre los tres dispositivos.
10. [x] Publicar el código en las ramas del encargo y verificar sus SHA remotos.

## Instalaciones y continuidad

AXELPC ejecuta `2026.10.20-device-continuity.1` en el puerto 63896, con el
perfil existente y la continuidad cifrada integrada. La siguiente revisión
conjunta se valida antes de sustituir esta instalación. No se reemplazó el
perfil ni sus credenciales.

La rama Android 042adcb integra el cierre de sincronización 16bbcde y Studio
3e72943. La versión instalada 95 se actualizó mediante
actualización, sin desinstalar ni borrar datos. Se verificaron el certificado,
el SHA-256 de la APK instalada y la conservación de todas las identidades
anteriores de proyectos, escenas, planos, personajes, medios y generaciones.

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

- Versión Android unificada: compilación completa satisfactoria; 651 pruebas
  correctas, 4 omitidas y ningún fallo. Incluye 20 casos del motor Drive y la
  protección de una edición recibida por la conexión directa durante un ciclo.
- Motor JVM Desktop: 19 casos de sincronización correctos.
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
  física de esta corrección corresponde a la siguiente instalación unificada.
- La comparación de generaciones conserva rutas sin prueba y contenidos
  diferentes; solo normaliza referencias con SHA verificados coincidentes y
  outputs equivalentes del mismo proyecto. Las pruebas cubren orden y cantidad.
- La primera pasada observó referencias antiguas sin original accesible. No se
  afirma que todas estén recuperadas ni que toda la biblioteca esté respaldada.

La recuperación histórica ha rescatado tres PNG de Runway (12.952.850 bytes),
vinculados a seis identidades ya existentes mediante la caché nativa de medios.
Su SHA-256 y los bytes servidos por la instalación se verificaron; el índice
conserva todas sus identidades y no se añadieron filas. La publicación de
estos tres originales queda pendiente del siguiente ciclo del host conjunto. No ha creado
generaciones ni ha adivinado correspondencias cuando cambió el número de
outputs. Otras 91 referencias consultadas siguen pendientes; son enlaces, no
un recuento de originales únicos.

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
