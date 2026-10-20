# Traspaso: Flow, ASUS y APK de pruebas (2026-10-20)

Rama: `claude/asus-pc-health-audit-i023i6`. Código de la APK instalada: `4ba9755`.
Repositorio: `egodivergente/nexus`. Trabaja sobre esta rama; no hagas force-push ni reescribas historial.

## Qué está hecho y probado

- **Puente de Flow en el ASUS** (`tools/flow-linux-probe/`): listar proyectos, leer medios, descargar archivos,
  descarga por tamaño 1K/2K/4K (probada en real: 1376×768, 2752×1536 y 5504×3072), vigilante de sesión
  (`flow_keepalive.py`, timer cada 5 min) y navegador en servicio propio (`nexus-flow-browser.service`).
  Pruebas unitarias: 51 (`python -m unittest test_flow_import test_recovery test_flow_keepalive`).
- **Importar de Flow en la app Android** (Biblioteca › ⋮ › «Importar de Flow», `FlowImportSheet.kt`).
  Compila; 377 pruebas de la app pasaron en la última ejecución local antes del último guardado de modo.
- **Lote desde archivo en el Lienzo** (`PromptBatch.kt`, `PromptBatchSheet.kt`): CSV/TSV, XLSX y TXT.
- **Reintento automático de encargos de Flow** (`FlowRetryPolicy.kt`): solo errores anteriores al clic en Generar.
- **Agente de Nexus**: Flow (cuenta Ultra) se usa con iniciativa para bocetos; Atlas/Crun/Runway no.
- **Generación real de prueba en el Pixel** con la cuenta Ultra: una imagen de 1376×768 (verificada).

## Qué falta

1. **Verificación funcional de la APK en el Pixel**: ya está instalada `1.9.52-agent-preview` / vc86
   sobre vc85, con autorización del usuario. `:app:assembleAgentPreview` (ARM64) terminó en AXELPC;
   el ASUS solo firmó con su clave existente, sin copiarla. Certificado SHA-256 comprobado contra la APK
   instalada: `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`.
   APK SHA-256: `fa8fcc55008ed8405f577763236ffa9da33262f24f7f121e3e9a841af213fc0f`.
   Actualización mediante `adb install -r -t --user 0`: `Success`, mismo UID 10374 y fecha original de
   instalación (2026-10-06). `MainActivity` quedó en primer plano y el proceso seguía activo.
   Falta probar los flujos de importación y fotogramas en la app; no se inició ninguna generación.
   GitHub Actions sigue sin asignar runner ni ejecutar pasos.
2. **Fotogramas inicio/fin de Vídeo en Flow**: programados en la app y el puente (modo Vídeo › Fotogramas,
   controles «Inicio», «Fin» e «Intercambiar»). Las 51 pruebas del puente pasan. La API autenticada
   `/api/flow/status` respondió `connected` y `generation_ready`; no se envió ninguna generación. La app tuvo
   377 pruebas en verde antes de la última validación añadida; repetir `:app:testDebugUnitTest` falló por OOM
   del host Windows, así que queda pendiente repetirla con recursos disponibles.
3. **Vídeo a 4K**: sin probar. Cuesta más puntos; no ejecutar sin confirmación del usuario.
4. **Prueba de la importación completa en el teléfono** (la app con el botón nuevo), pendiente de la APK.

## Reglas de este proyecto (leer antes de tocar nada)

- Secretos y firmas **fuera de Git**. La clave `~/.android/debug.keystore` del ASUS tiene la firma que ya usa el
  Pixel; no la copies al repositorio ni a un chat. El secreto de GitHub `NEXUS_DEBUG_KEYSTORE_B64` lo añade
  el usuario a mano en la web de GitHub.
- No instalar, borrar datos ni gastar créditos de pago sin confirmación. Los créditos de Flow son del usuario
  y puede usarlos, pero las generaciones de vídeo caras, no.
- El ASUS es el último recurso para compilar (solo con permiso explícito del usuario, `--max-workers=1 --no-parallel`).
- El estado del navegador y de la sesión de Google vive en el ASUS (`~/.local/share/nexus-flow-probe/`), no en Git.
  No reiniciar `nexus-flow-browser` sin necesidad: reiniciar Chromium cierra la sesión de Google.

## Cómo verificar lo que ya funciona

```bash
cd tools/flow-linux-probe && python -m unittest test_flow_import test_recovery test_flow_keepalive
cd /ruta/al/repo && ./gradlew :agent-core:test          # 226 pruebas, 4 omitidas
cd /ruta/al/repo && ./gradlew :app:testDebugUnitTest    # 369 pruebas
```

Si tienes acceso a GitHub Actions con minutos, el workflow `build-apk.yml` ya compila `assembleAgentPreview`
y, si existe el secreto, sube `NEXUS-agent-preview-device-signed`.
