# Puente de Google Flow para Codex

Conectar Codex con la cuenta personal de Flow mediante el MCP de gflow-cli. La primera validación utiliza una imagen; los vídeos quedan fuera de esa prueba inicial.

1. Instalar gflow-cli 0.82.1 y dependencias desde ruedas binarias en un entorno privado y fijar sus versiones.
2. Preparar un perfil de navegador independiente y un directorio persistente para resultados. No reutilizar ni extraer cookies del navegador habitual.
3. Verificar el protocolo MCP real, su catálogo de herramientas y el comportamiento sin autenticación.
4. Registrar el servidor en Codex conservando su configuración previa.
5. Completar el inicio de sesión de Google mediante el navegador. Contraseña, segundo factor o verificación requieren al usuario.
6. Generar una sola imagen, descargarla y comprobar formato, tamaño y SHA-256. Entregar el archivo en la conversación.
7. Consultar el resultado desde una nueva conexión y confirmar que persiste. Un envío ambiguo se reconcilia con su trabajo; no se reenvía automáticamente.

La configuración local de Codex no habilita por sí sola ChatGPT web o móvil. La incorporación al Asistente de NEXUS usa sus funciones comunes de modelos, esquema, cotización, envío y estado.

## Ejecución del 2026-10-06

Pasos 1–5 completados. En el paso 6 se envió una generación real; gflow-cli falló al comparar el código del modelo. Se recuperó el JPEG de 1024 × 1024 desde el proyecto existente, con formato y SHA-256 verificados, sin repetir la generación.

Paso 7 completado: recuperación por MCP desde una nueva conexión, respuesta estructurada con archivo local y repetición del mismo request_id sin otro trabajo nativo. Se incorporó una base SQLite de identificadores persistentes y un control previo al envío que reconoce el código observado `BELUGA`. La comprobación real de ese control se hizo abortando el RPC antes de Google.

El recorrido de la última capa se ha comprobado con una generación nueva: petición MCP, control de modelo, trabajo nativo completado y JPEG de 1024 × 1024 recibido sin recuperación adicional. Dos generaciones reales enviadas en esa fase. La continuación validó un vídeo real de Veo Fast, recuperado sin repetir el envío, y una imagen real enviada desde el gateway del Asistente de NEXUS: cuatro generaciones nativas en total. Se integraron cotizaciones leídas de la cuenta (0 y 10 puntos), trabajos persistentes y resultados locales en su biblioteca. Android queda NO PROBADO. El destino GitHub identificado es vidaltb94-collab/nexus, rama codex/google-flow-account.

Ampliación solicitada: lectura de opciones de cada activo existente, escalado de imágenes 2K/4K, vídeo 1080p/4K y exportación GIF. Las operaciones usan reservas independientes, verifican el precio antes del clic y conservan el original. La biblioteca y el catálogo del Agente comparten el adaptador; no se compila otro APK o EXE. Las validaciones reales utilizan los activos de prueba existentes y las opciones cuyo menú no indica cargo. No se ejecuta el escalado de vídeo 4K de 50 puntos solo para comprobar el código; su prueba de pago queda explícitamente pendiente.

Continuación del Agente: se corrigieron búsqueda de origen, identificadores de fuente, proveedores obtenidos del registro, preparación sin prompt según esquema y cotizaciones en créditos. El motor Kotlin se compiló en ASUS mediante el selector, con fuentes aisladas y límites de memoria. La conversación real preparó un escalado 2K en dos rondas; la confirmación de ese mismo plan devolvió el JPEG de 2048 × 2048 a la biblioteca, sin modificar el original ni enviar una generación nueva. Las evidencias de cuenta y resultados permanecen privados. El saldo/deducción efectiva no se obtienen y no deben prometerse.
