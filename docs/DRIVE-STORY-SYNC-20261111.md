# Guion compartido por Drive · 11 de noviembre de 2026

Esta evolución conecta el proyecto audiovisual de Windows con Nexus Android, manteniendo la carpeta, cuenta, permisos y sincronización existentes. La referencia móvil es la versión 207 (`71de517999e2`); no se sustituye su centro de proyectos ni Cut por una edición anterior.

## Datos y compatibilidad

`SyncWorkspace` sigue usando un solo `AssetIndex`. El guion se transporta en un diario independiente `story-v1-commit-UUID.json`, dentro de la misma carpeta Drive. Los lectores anteriores solo aceptan `commit-UUID.json` y no procesan este canal. La sincronización completa del guion requiere ambos clientes actualizados.

Las filas del nuevo canal representan bloques, asociaciones, elementos, preferencias, propuestas, tareas y metadatos de origen. Los campos extendidos de escenas, planos, personajes y proyectos se proyectan sin reemplazar los campos comunes editables por los clientes anteriores. Nombres, códigos y descripciones comunes conservan su mecanismo de resolución existente.

- Identificadores y vínculos n:m se conservan al reordenar y compartir un archivo entre proyectos. Las referencias necesarias de un proyecto seleccionado se descargan aunque el archivo pertenezca a otro.
- Los conflictos mantienen la versión local y las alternativas; no se confirma su resolución al reconocer un intercambio.
- Si faltan entidades relacionadas, se conserva el proyecto local y se informa de las dependencias pendientes.
- Los archivos retirados no se restauran automáticamente. No se borran originales físicos.
- La autonomía es propia de cada dispositivo. Recibir tareas no autoriza llamadas a agentes ni generaciones. Las tareas importadas sin ejecutor recuperable quedan pendientes; una tarea local que sigue ejecutándose se conserva.
- Aceptar la misma propuesta desde dos clientes usa identidades y marcas de creación estables, incluso para bloques y asociaciones creados indirectamente.

## Interfaz

Windows: **Ajustes → Dispositivos → Google Drive**, apartado de guion/storyboard. Android: **Ajustes → Google Drive**, mismo control. El estado narrativo se muestra separado del intercambio anterior. Una copia subida a Drive no demuestra que el otro dispositivo la haya recibido. La revisión de conflictos muestra texto, referencias, roles, alcance y posición.

La pausa conserva el contenido local. Los mecanismos existentes de autorización, reintento y resolución siguen siendo los puntos de entrada. No hay otro inicio de sesión ni un servicio nuevo de pago.

## Atlas en Windows

El transporte real de Nexus obtiene los siete esquemas públicos incorporados anteriormente. El HTTP 403 detectado en el sondeo genérico corresponde a la identificación predeterminada de urllib; no se ha sustituido ninguna credencial para resolverlo.

El formulario conserva sus plantillas y ahora reconoce los fotogramas intermedios de Kling, el audio de referencia de Vidu y la imagen de fondo de HeyGen. Usa los límites y tipos del esquema real. Las alternativas requeridas simples declaradas por el esquema, como prompt o audio en HeyGen, se validan antes de cotizar o enviar. Los metadatos de validación no entran en el payload del proveedor.

Las estructuras opcionales `elements` y `voice_ids` de Kling no tienen un editor especializado de Windows en esta entrega. No se presenta esta mejora como un intérprete completo de JSON Schema ni como validación de todas las condiciones de todos los proveedores.

## Comprobación

Las pruebas focalizadas cubren dos pares, reordenación, referencias compartidas, reinicio, conflictos y resolución explícita, compatibilidad con lectores anteriores, dependencias, retirados y propuestas aceptadas simultáneamente. La presentación se comprueba de forma aislada y los esquemas/formularios de Atlas mediante operaciones de lectura, sin generaciones.

La actualización Android se prepara con AWS CodeBuild existente y se firma localmente con el certificado instalado. Las credenciales, la firma, los índices reales y las evidencias privadas no forman parte de Git ni del paquete de fuentes.
