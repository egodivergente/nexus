# Agente compacto y destacados personales — 2026-10-21

Base: `aki/mega-mejora-20261020` @ `0ced5f1` (vc98), conservando la integración Android vc97 de Studio, segundo plano y continuidad. Rama de trabajo: `codex/agent-minimal-controls-20261021`. Entrega prevista: 1.9.65 / vc99.

Cambios pedidos por Vidal en sus anotaciones de voz y conversación:
- Proveedor solo en Ajustes → Agente → Ajustes del asistente; se mantienen configuración y credenciales existentes.
- Razonamiento dentro del compositor, icono discreto de 18dp junto al micro/envío y zona táctil de 48dp.
- Biblioteca reciente cerrada al entrar. Deslizar arriba desde el compositor o mantener el clip; deslizar abajo, cerrar o Atrás para ocultarla. Un toque normal en el clip conserva el selector de archivos. Al elegir un resultado se recoge; el borrador y sus adjuntos permanecen.
- Destacados editables por Imagen/Vídeo desde Personalizar modelos destacados. Añadir del catálogo vivo, quitar, ordenar, guardar/cancelar. Quitar de favoritos no altera el catálogo. Preferencias separadas y persistentes; lista vacía intencional distinta de los valores iniciales. Las referencias a modelos temporalmente ausentes se conservan.

Validación en curso. El S25 no es accesible actualmente por ADB y su conector directo devuelve 404. AXELPC está desconectado. Pixel responde a ADB, pero SSH a Termux rechaza la autenticación; se utiliza ASUS como último recurso para compilar, con un worker y memoria limitada. Sin generaciones pagadas ni modificación de datos de la app instalada.
