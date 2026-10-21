# Agente compacto y destacados personales — 2026-10-21

Base: `aki/mega-mejora-20261020` @ `0ced5f1` (vc98), conservando la integración Android vc97 de Studio, segundo plano y continuidad. Rama de trabajo: `codex/agent-minimal-controls-20261021`. Entrega prevista: 1.9.65 / vc99.

Cambios pedidos por Vidal en sus anotaciones de voz y conversación:
- Proveedor solo en Ajustes → Agente → Ajustes del asistente; se mantienen configuración y credenciales existentes.
- Razonamiento dentro del compositor, icono discreto de 18dp junto al micro/envío y zona táctil de 48dp.
- Biblioteca reciente cerrada al entrar. Deslizar arriba desde el compositor o mantener el clip; deslizar abajo, cerrar o Atrás para ocultarla. Un toque normal en el clip conserva el selector de archivos. Al elegir un resultado se recoge; el borrador y sus adjuntos permanecen.
- Destacados editables por Imagen/Vídeo desde Personalizar modelos destacados. Añadir del catálogo vivo, quitar, ordenar, guardar/cancelar. Quitar de favoritos no altera el catálogo. Preferencias separadas y persistentes; lista vacía intencional distinta de los valores iniciales. Las referencias a modelos temporalmente ausentes se conservan.

Validación en curso. Las cinco pruebas JVM de `StudioFavoritesTest` pasan (0 fallos, 0 omitidas). El código de las variantes offlineQa y agentPreview compiló en el primer intento; no se completó su validación física.

El usuario ordenó detener el trabajo pesado en ASUS y reservarlo exclusivamente para Flow salvo autorización explícita por ejecución. Se detuvieron los procesos propios y se verificó ausencia de Gradle/Kotlin/builds activos; aproximadamente 2 GB de RAM disponibles. La excepción anterior de último recurso queda revocada en AGENTS.md.

Continuación en AXELPC mediante Desktop Commander, en `C:/Users/User/.codex/worktrees/nexus-agent-minimal-20261021`. S25 accesible por ADB: 1.9.64-mega1-agent-preview / vc98. Firma original verificada: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`. El usuario indicó expresamente que no había solicitado pruebas. Se canceló la compilación de QA en AXEL y se continuó únicamente con `:app:assembleAgentPreview`. No se instalaron paquetes ni se ejecutaron pruebas en el móvil. Sin generaciones pagadas ni modificación de los datos de la app principal.

GitHub: publicación desbloqueada mediante el gestor de credenciales Git de AXELPC. Rama `codex/agent-minimal-controls-20261021` publicada y SHA remoto `cb5abb9fe4a5099c3cf540d2e5bf196ff020bbf7` verificado. La integración del chat y gh CLI no tenían acceso de escritura/autenticación; Git Credential Manager sí.


Entrega final: compilacion exclusiva assembleAgentPreview completada en AXEL (BUILD SUCCESSFUL, 6m28s). Tras autorizacion expresa de Vidal, instalada sobre S25 con adb install -r -t: Success. Version confirmada por Android: 1.9.65-agent-preview / vc99. No se ejecutaron pruebas en el movil ni se borraron datos. El flag -t permite instalar esta APK marcada testOnly por android.injected.build.abi; no ejecuta pruebas. SHA256 APK: E4A877377A6328FE1DCCBD0AADC6F897CC7C1E008F883121BFE41A2D0DA71ABC
