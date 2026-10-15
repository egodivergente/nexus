# Verificación · 2026-10-15

- Python 3.12 en Windows: 14 pruebas unitarias pasan. Cubren navegación ramificada,
  recuperación de rutas, límites, persistencia ante error, controles sensibles,
  diálogos externos, keyguard, escape HTML, SSH y descargas GitHub con digest.
- GUI Tkinter: ventana construida, bucle de eventos ejecutado y cierre comprobado.
  No se ha hecho una revisión visual completa de todos los controles de la ventana.
- GitHub real: acceso al repositorio privado `egodivergente/nexus` con la sesión
  existente de GitHub CLI. Release `v1.9.4-agent`, asset `643882626`, descargado
  `Nexus-Agent-1.9.4-preview.apk`, package detectado `com.vidadomu.nexus.agent`.
  SHA-256 `3a8552ff74ea6677618c75e9a80af43df2cc8d70c086a101998d33aa688dc353`,
  verificado frente al digest del asset de GitHub. No se instaló esa descarga.
- Inventario real del código: rama `main`, SHA
  `7d2bc72df7b7f80198c2b853990967c784b685c0`, 28 archivos candidatos inspeccionados,
  19 referencias de navegación encontradas. Esto no demuestra su cobertura en una APK.
- Pixel 8 Pro: ADB autorizado a través de ASUSVIDAL, serial observado
  `192.168.1.146:43849`. Se lanzó la app de pruebas ya instalada
  `com.vidadomu.nexus.uiqa` y se recuperaron XML y PNG reales. El sistema mantuvo
  `NotificationShade`/`element:lockscreen` delante de la app; la captura era negra.
  El explorador registró `blocked` y no ejecutó pulsaciones de exploración.
  Recorrido real entre varias pantallas: **NO PROBADO**. No se borraron datos,
  desinstalaron apps ni cambiaron ajustes persistentes del dispositivo.

Los artefactos de estas comprobaciones permanecen en `sessions/`, excluido de Git.
No hay validación de cobertura total ni de funcionamiento con cualquier APK.
