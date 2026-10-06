# Integración Nexus Studio · 1.5.0

El plugin local vive en `C:\Users\User\plugins\nexus-studio`. Una copia distribuible se guarda en `outputs/nexus-plugin-20261001/Nexus-Studio-plugin.zip`. No contiene datos de usuario ni claves de proveedores.

Flujo: skill → MCP stdio → ADB autorizado → ContentProvider de Nexus → catálogo y esquema normalizados → borrador privado. El puente no tiene operación de generación. La ejecución permanece en el CTA de la app.

La app incorpora `NexusAgentProvider` (autoridad `${applicationId}.agent`), restringido a builds de depuración, UID shell o UID propio, permiso Android DUMP y perfil personal 0. El plugin identifica el teléfono por su serie física, evita confundir dos transportes del mismo S25 con dos teléfonos y rechaza otro perfil. No abre sockets ni incorpora claves Atlas al plugin.

Los borradores se guardan aparte en `files/agent_drafts`, con escritura temporal y cambio de nombre, máximo 20. El usuario confirma la sustitución del lienzo en un diálogo. Los parámetros se validan mediante `FormContract` y conservan sus claves originales. Los medios se adjuntan dentro de Nexus.

El filtro «Más nuevos» ordena las familias de modelos por la fecha de lanzamiento documentada más reciente de sus variantes. La precisión actual es mensual. «Solo con fecha» oculta los grupos sin fecha; desactivado los deja al final. Ambas preferencias persisten. No se usa la fecha de incorporación al catálogo como lanzamiento.

La conexión local de Codex no implica una conexión a ChatGPT móvil o web. Esta requiere habilitar una conexión MCP compatible en la cuenta. Documentación oficial: https://developers.openai.com/plugins/deploy/connect-chatgpt

Pruebas: `CatalogReleaseOrderTest`, `AgentDraftContractTest`, `AgentDraftStoreTest` y `scripts/test_bridge.py` del plugin. Las evidencias MCP reales y el informe están en `outputs/nexus-plugin-20261001`. Los archivos `private-*.tar` de esa carpeta son copias privadas de seguridad y nunca se deben distribuir con el plugin.
