# Nexus Studio 1.9.3: biblioteca, documentos y visor

Actualización del 12 de octubre de 2026.

- La biblioteca incluye trabajos en curso desde el envío. Muestra tiempo transcurrido, porcentaje cuando el proveedor devuelve progreso real y tiempo restante estimado a partir de al menos tres trabajos completados del mismo modelo/proveedor (mediana de los últimos veinte). Flow no presenta sus hitos como porcentaje de renderizado.
- Los PDF con texto se extraen localmente en un proceso limitado a 30 segundos. Se conserva el archivo original; si alguna página carece de texto se mantiene el envío del PDF original. No se aplica el antiguo límite local de veinte páginas. El límite existente de tamaño sigue vigente. Las imágenes del PDF no forman parte de la extracción textual y se indica al agente.
- La lectura documental se realiza una vez por turno, con resumen para los pasos posteriores, peticiones HTTP cancelables y límite total de cuatro minutos en turnos con documentos. El visor recorre todas las salidas y fotografías de la biblioteca mediante flechas, teclado y arrastre horizontal.
- La actualización no ejecuta la migración de credenciales del teléfono ni modifica la selección de backend/modelo del PC.

## Validación y entrega

Compilación runtime y pruebas JVM en ASUS: BUILD SUCCESSFUL. Prueba aislada con PDF real de 21 páginas, conservación de su hash, fallback de PDF sin texto, progreso/ETA, navegación entre salidas y sustitución de trabajo pendiente por resultado: PASS. Pruebas funcionales offline del escritorio: PASS.

Instalación Windows comprobada: 1.9.3-agent-desktop, 51 herramientas, sin errores JavaScript. Ajustes y archivos protegidos conservaron sus hashes; el health del bridge Sol respondió correctamente. No se realizaron generaciones de pago ni una lectura de PDF contra el LLM real: la validación documental fue local y con pruebas del núcleo.

Paquete: Nexus-Studio-Agent-1.9.3-Windows.zip.
SHA-256: 0fd6a641c17ea4dc0baf6782f08adf927809b50253b64af5a5044058f9c079f8.

Los informes y capturas del perfil real se conservan únicamente en outputs privados, fuera de Git.
