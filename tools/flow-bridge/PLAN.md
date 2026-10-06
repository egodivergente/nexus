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
