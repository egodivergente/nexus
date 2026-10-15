# Verificación · 2026-10-15

- Python 3.12 en Windows: 27 pruebas unitarias pasan. Cubren navegación ramificada,
  recuperación de rutas, límites, persistencia ante error, controles sensibles,
  diálogos externos, keyguard, escape HTML, SSH, descargas GitHub con digest,
  persistencia de preferencias, detección de móviles, selección con cambio de puerto,
  CLI para agentes con JSON limpio, inferencia del package, recorrido por anchura,
  listado de acciones pendientes, detección de capturas modificadas, estabilización
  después del splash, prioridades de navegación y JSON Unicode en consola Windows.
- Prueba real sobre NEXUS Agent ya instalado en el S25 (`com.vidadomu.nexus.agent`,
  interfaz `v1.9.5-agent-preview`, build 39), ADB local `192.168.1.134:5555`:
  una pasada de 3 minutos obtuvo 9 estados, 9 acciones, 94 acciones pendientes y
  14 controles omitidos, finalizó con `limit_reached` y devolvió JSON válido.
  Se abrió su lámina y se verificaron capturas reales del chat, diálogos, proyecto,
  ajustes y saldo. No se reinstaló la app. Los artefactos privados están excluidos de Git.
  Esta prueba corrigió una salida Unicode incompatible con la consola y una captura
  prematura del splash, además de priorizar pestañas y navegación frente al chat.
  La pasada posterior, con las prioridades corregidas, obtuvo Agente, Lienzo, Modelos
  y Biblioteca en 4 capturas y 3 pulsaciones, todas propias de la app, con SHA-256
  verificado y sin avisos de imagen uniforme. Finalizó por el límite de 4 estados;
  se revisó visualmente su lámina y el JSON de stdout era válido. Cobertura parcial.
- Modo agente real en el Pixel: creó `agent.json` y una lámina JPG desde XML/PNG
  del dispositivo. Marcó `blocked`, captura uniforme y SHA-256 válido; se abrió la
  lámina y se comprobó que muestra la captura negra con aviso de revisión.
  No se presenta esa captura como interfaz de la app ni se valida un recorrido real.
- Detección automática real desde Windows: S25 `192.168.1.134:5555` y Pixel por mDNS.
  La GUI seleccionó el S25 y guardó las preferencias en una carpeta temporal de prueba.
  Esta comprobación solo enumeró dispositivos; no lanzó apps ni exploró sus pantallas.
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
