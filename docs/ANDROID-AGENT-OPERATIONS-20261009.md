# Herramientas operativas del agente Android — 2026-10-09

## Alcance

Se añaden nueve herramientas nativas a las 27 existentes. En modo Agente, los cambios solicitados se ejecutan directamente. Copiloto conserva propuestas aplicables una sola vez y Asistente solo recibe las consultas. El límite existente de 40 rondas por mensaje se conserva: el problema era la falta de herramientas operativas, no ese límite.

| Herramienta | Operación real |
| --- | --- |
| get_active_generations | Historial local pendiente, proveedor, edad, error y posibilidad de consultar el job remoto |
| recover_generation | Consultar un job remoto existente y reactivar seguimiento; no reenviar |
| diagnose_flow | Estado autenticado del puente y cola local |
| repair_flow | Reactivar seguimiento de trabajos Flow con identificador remoto real |
| get_nexus_settings | Modelo, tokens, imágenes, familias excluidas, tema y parámetros recordados; sin claves |
| update_nexus_settings | Cambiar esos ajustes reales; modelo aplicado en el siguiente mensaje sin perder la conversación |
| rename_asset_file | Renombrar copias privadas de medios de Nexus o MediaStore modificable |
| convert_image_asset | Exportar PNG/JPEG/WebP, reducir tamaño o recortar al centro conservando el original |
| atlas_get_balance | Saldo oficial Atlas; respuesta de permisos si el endpoint no lo autoriza |

## Recuperación y límites

La consulta de estado del backend del agente ahora vuelve al proveedor para trabajos activos con ID remoto. Las consultas de un mismo job se serializan con las del trabajador para evitar sobrescribir respuestas concurrentes. Se consulta el proveedor de origen; nunca se sustituye por el proveedor activo de Ajustes.

Los marcadores de cola Flow no se interpretan como IDs remotos. Estas herramientas no envían trabajo nuevo, no duplican una petición incierta ni simulan cancelaciones. Flow mantiene el formato/modelo del proyecto activo: el puente actual no ofrece cambio de configuración, reparación de sesión Google ni reinicio remoto del navegador.

El renombrado privado conserva un enlace duro en la ruta antigua o, si Android lo rechaza, una copia sincronizada y verificada por tamaño. Así los adjuntos, planes y chats persistidos siguen abriendo el mismo contenido. La copia requiere espacio libre suficiente; si falla, se restaura el nombre original. El nombre físico nuevo y la URI principal se verifican por la operación; MediaStore conserva su URI. No se admite renombrar URLs remotas, claves, bases de datos ni rutas arbitrarias. Android puede exigir permisos adicionales para MediaStore ajeno.

El índice distingue cambios de metadatos de un cambio de URI verificado. La reubicación conserva ID, fecha, etiquetas y relaciones; rechaza una URI antigua o duplicada. La escritura se sincroniza y reemplaza el JSON antes de publicar el nuevo estado en memoria; un fallo de persistencia conserva el índice anterior.

La conversión solo procesa imágenes locales indexadas: JPEG respeta orientación EXIF; la salida se guarda en un archivo temporal, se sincroniza y se verifica antes de indexarla. No amplía imágenes ni sustituye originales. Un máximo de 4096 por lado y decodificación acotada limitan memoria. No se implementa transcodificación de vídeo.

## Atlas MCP y transporte Android

Fuentes oficiales revisadas:

- https://www.atlascloud.ai/docs/mcp-server
- https://github.com/AtlasCloudAI/mcp-server/blob/main/src/index.ts
- https://github.com/AtlasCloudAI/mcp-server/blob/main/src/tools/account.ts
- https://github.com/AtlasCloudAI/mcp-server/blob/main/src/services/api-client.ts

El servidor oficial usa Node y transporte stdio. Esta implementación Android consulta directamente sus contratos HTTP oficiales mediante adaptadores nativos; no es un servidor MCP instalado o conectado en la APK. El catálogo, schemas, cotización y seguimiento usan los adaptadores Atlas de Nexus existentes.

La consulta de prueba a /public/v1/balance devolvió HTTP 403 desde Windows y ASUS con la credencial disponible en el almacén Windows. Esto no verifica el saldo ni identifica por sí solo el motivo exacto del rechazo. La herramienta informa del bloqueo y no calcula un saldo ficticio. No se enviaron generaciones de pago para probarla.

## Validación e instalación

Resultado final: compilación AgentPreview correcta con Java 17/SDK 35, un trabajador, sin paralelismo Gradle y prioridad baja. ASUS se comprobó primero; su presión de RAM/swap impidió completar el empaquetado, por lo que se utilizó Windows con recursos limitados.

- 105 pruebas del núcleo y 241 pruebas de la variante AgentPreview: PASS, sin fallos, errores ni omitidas.
- Cuatro pruebas nativas en el S25 físico: PASS. Cubren renombrado físico e índice, conservación de la URI antigua y los bytes originales, conversión/EXIF, recuperación por proveedor de origen sin reenviar y cambios de ajustes sin reescribir credenciales.
- La primera prueba de renombrado detectó el rechazo de link() en Android; la segunda detectó que updateAsset protegía la URI. Se corrigieron con copia compatible y reubicación explícita del índice antes de la prueba final.
- Instalación real en el S25 de com.vidadomu.nexus.agent, código 27, 1.8.3-agent-preview: PASS, mediante actualización -r con certificado SHA256 8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127 coincidente.
- APK instalada recuperada del teléfono y SHA256 comprobado: 9a35738e53fbe942ec5167236595d53796db562847ad1040144f829dc76944d3.
- Base de datos, preferencias e índice/chats privados: bytes conservados al actualizar; 106 trabajos y 95 resultados. Tras abrir la app se comprobaron todos los campos de generaciones/resultados y los IDs del historial sin cambios, integrity_check=ok y SHA256 idéntico de 171 archivos privados de imagen/vídeo/audio.
- Prueba de lectura sobre la Agent real: 36 herramientas enlazadas, seis elementos del chat actual y dos conversaciones archivadas compatibles.
- Flow desde el S25: HTTP 200, connected y generation_ready=true. El saldo oficial Atlas se pudo consultar con la credencial del móvil; el HTTP 403 anterior se obtuvo con la credencial del almacén Windows.
- Comprobación posterior al arranque: las seis credenciales conservan sus valores cifrados. Los cambios automáticos de preferencias corresponden al tipo de cambio y su fecha, identificadores de actividad y metadatos de caché del catálogo. Modo AGENT conservado.
- Modelo existente conservado: Opus 4.6. Sonnet 4.6 está disponible en los ajustes operativos.
- El componente temporal de comprobación se retiró. No se ejecutaron generaciones de pago ni llamadas nuevas al LLM para estas pruebas.

La versión inicial instalada era 1.8.2-agent-preview (código 26), mientras que las fuentes disponibles eran 1.8.1. El usuario confirmó que las fuentes de 1.8.2 no estarán disponibles durante una semana y posteriormente pidió expresamente instalar la APK mejorada en el S25. Se instaló 1.8.3-agent-preview (código 27) desde las fuentes disponibles, con firma coincidente y actualización conservando datos. No se afirma haber reproducido todos los cambios de código de la 1.8.2. Se recupera la compatibilidad de ChatItem.ModeSwitch a partir de su esquema real persistido (id, text, resolved), conservando su historial sin ejecutar cambios de modo al abrirlo.

Antes de actualizar se respaldaron APK y datos privados consistentes (185339392 bytes, 205 entradas). La base de datos dio integrity_check=ok: 106 trabajos, 85 COMPLETED y 21 FAILED; 95 resultados. Los archivos privados, credenciales y respaldos permanecen fuera de Git. La APK original conserva SHA256 7f9e726d3b635d02cfeece8c2b1d1dfe5f067c1f0da1e4047c962a8250d6659e.
