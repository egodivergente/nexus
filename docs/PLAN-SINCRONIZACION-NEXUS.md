# Sincronización Nexus — Pixel, S25 y AXELPC

## Resultado solicitado

Sincronización bidireccional de proyectos y archivos originales mediante una
carpeta privada de Google Drive. Copias locales utilizables sin conexión,
reintentos persistentes y conservación de versiones en conflictos.

## Plan de ejecución

1. [x] Comprobar fuentes, instalación Desktop y dispositivos físicos.
   Desktop: base 05c1898. Pixel: 1.9.51/vc85. S25: 1.9.57/vc91.
   Android se integra sobre la fuente publicada a4088da, sin sustituir trabajos
   de otras tareas ni instalar una versión anterior a la existente.
2. [x] Motor común: registros inmutables, identificación de archivos por SHA-256,
   resolución explícita de conflictos y recuperación tras interrupciones.
3. [x] Transporte Drive y autorización nativa en Android y Windows.
4. [x] Integración de proyectos y medios; cola de sincronización automática,
   estado visible, sincronización manual y selección de proyectos descargados.
5. [ ] Pruebas con varios dispositivos simulados, archivos reales, conflictos,
   reintentos, pérdida de conexión e integridad. Compilación Android y Desktop.
6. [ ] Alta de Google OAuth, vinculación y prueba física cuando exista acceso.
7. [ ] Publicación de commits en GitHub y entrega de resultados verificables.

## Contrato

- Las rutas locales de un dispositivo no se copian como rutas válidas de otro.
- Se guardan originales, no solo enlaces de proveedores que puedan caducar.
- Los cambios concurrentes conservan versiones; no hay sobrescritura silenciosa.
- Ninguna credencial de generación, sesión local o clave de firma se sincroniza.
- Un fallo no vacía la biblioteca ni elimina originales. Desconectar conserva
  las copias locales. La sincronización no dispara nuevas generaciones.
- Windows conserva originales descargados. Android descarga los proyectos
  seleccionados y conserva metadatos del resto; el trabajo periódico respeta
  la conectividad seleccionada y las restricciones del sistema.
- La primera vinculación requiere autorización de Google y registro OAuth.
  Se distingue implementación probada con dobles de la operación real en Drive.

## Dependencias externas

Clientes «Nexus Sync · AXELPC» y «Nexus Sync · Android» creados el 2026-10-20
en el proyecto autorizado. API Drive habilitada. Autorización real Windows
completada; tokens cifrados con DPAPI en el perfil privado. No se guardan
contraseñas en código, informes ni historial Git.

La consola mantiene el proyecto OAuth en estado Prueba y muestra Publicar app
deshabilitado por configuración de marca incompleta. Este proyecto también
tiene un cliente web anterior: el alta de Nexus no modifica sus dominios,
permisos ni credenciales. Mientras continúe en Prueba, el refresh token de
Drive caduca a los siete días, según la documentación oficial de Google:
https://developers.google.com/identity/protocols/oauth2#expiration

## Evidencia de desarrollo

- 242 pruebas JVM Android pasadas, incluidas 13 de sincronización, antes de
  integrar las dos actualizaciones Android publicadas durante esta sesión.
- Compilación Kotlin Android inicial satisfactoria. La APK final debe incluir
  también 5150759 (UX 1.9.58) y 0aafaea (consumo BytePlus); no instalar la
  compilación inicial sobre esos cambios más recientes.
- Ocho pruebas Python de OAuth, separación de secretos, desconexión e
  importación privada pasadas; seis pruebas del adaptador Desktop pasadas.
- Prueba real con Drive: intercambio de metadatos entre dos diarios, versiones
  concurrentes conservadas, subida/descarga de bytes con SHA-256 coincidente.
  Se utilizó una subcarpeta aislada, enviada a la Papelera tras la prueba.
- Prueba real entre dos perfiles Desktop aislados: proyecto, PNG válido, montaje
  Corte, historial de deshacer y segundo ciclo idempotente: PASS. Siete casos
  del editor Corte y tres de transporte de proyectos también pasados.
- El usuario no dispone de web y solicita preparar la política de privacidad.
  Borrador en `docs/privacy/nexus-sync.html`, sin publicación ni dominio ficticio.
- La prueba real de transporte no demuestra todavía el recorrido físico completo
  Pixel → AXELPC → S25, ni recuperación de todos los medios antiguos.
