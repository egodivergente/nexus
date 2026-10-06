# Puente de Google Flow para Codex

Conectar Codex con la cuenta personal de Flow mediante el MCP de gflow-cli. La primera validación utiliza una imagen; los vídeos quedan fuera de esa prueba inicial.

1. Instalar gflow-cli 0.82.1 y dependencias desde ruedas binarias en un entorno privado y fijar sus versiones.
2. Preparar un perfil de navegador independiente y un directorio persistente para resultados. No reutilizar ni extraer cookies del navegador habitual.
3. Verificar el protocolo MCP real, su catálogo de herramientas y el comportamiento sin autenticación.
4. Registrar el servidor en Codex conservando su configuración previa.
5. Completar el inicio de sesión de Google mediante el navegador. Contraseña, segundo factor o verificación requieren al usuario.
6. Generar una sola imagen, descargarla y comprobar formato, tamaño y SHA-256. Entregar el archivo en la conversación.
7. Consultar el resultado desde una nueva conexión y confirmar que persiste. Un envío ambiguo se reconcilia con su trabajo; no se reenvía automáticamente.

La configuración local de Codex no habilita por sí sola ChatGPT web o móvil. La incorporación del mismo puente a NEXUS Agent será una fase posterior.

## Ejecución del 2026-10-06

Pasos 1–5 completados. En el paso 6 se envió una generación real; gflow-cli falló al comparar el código del modelo. Se recuperó el JPEG de 1024 × 1024 desde el proyecto existente, con formato y SHA-256 verificados, sin repetir la generación.

Paso 7 completado: recuperación por MCP desde una nueva conexión, respuesta estructurada con archivo local y repetición del mismo request_id sin otro trabajo nativo. Se incorporó una base SQLite de identificadores persistentes y un control previo al envío que reconoce el código observado `BELUGA`. La comprobación real de ese control se hizo abortando el RPC antes de Google.

El recorrido de la última capa con una generación nueva permanece NO PROBADO; los reintentos, la persistencia y el archivo real sí están comprobados. Vídeo y NEXUS Agent quedan NO PROBADO/PENDIENTE. Publicación GitHub pendiente de identificar un destino accesible; este checkout no tiene remoto.
