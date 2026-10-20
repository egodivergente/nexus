# Traspaso: Flow, ASUS y APK de pruebas (2026-10-20)

Rama: `claude/asus-pc-health-audit-i023i6` (último commit publicado: `ce28fb0`, más el workflow de firma `fe420e1`).
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

1. **APK de pruebas firmada con la clave del dispositivo**, para instalarla encima de `com.vidadomu.nexus.agent`
   en el Pixel 8 Pro sin perder datos. Bloqueado por dos cosas:
   - La compilación en GitHub Actions no arranca: la cuenta tiene el límite de gasto o el pago fallido.
   - Compilar en la nube falla al descargar whisper.cpp v1.7.6 desde GitHub (la sandbox no tiene acceso).
   El código de whisper sí existe en el ASUS: `~/audit-nexus-6874e34/app/.cxx/Release/4q712105/arm64-v8a/_deps/whisper-src`.
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
