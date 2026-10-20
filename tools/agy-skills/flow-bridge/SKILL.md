---
name: flow-bridge
description: Genera imágenes y vídeos en Google Flow a través del puente NEXUS (AXELPC, respaldo ASUS) con flow.sh. Úsala cuando el usuario pida generar, variar o descargar imágenes/vídeos de Flow, o subir referencias a Flow.
---

# Flow bridge (NEXUS)

Script: `flow.sh` en esta misma carpeta. Elige AXELPC (`100.72.111.97:8899`) y, si falla, ASUS (`100.112.57.2:8899`).
Lee el token de `~/.config/nexus-flow/token` y los hosts de `~/.config/nexus-flow/hosts`. Nunca imprimas, copies ni muestres el token.

## Cuándo usarla
- El usuario pide imágenes o vídeos «en Flow», variantes de un personaje con referencias, o descargar resultados de Flow.
- Para imágenes rápidas que no exigen Flow, ver la skill `nano-banana`.

## Comandos
```
flow.sh status                                   # comprobar antes de un lote
flow.sh upload ref.png                           # -> {"reference_id": "..."}
flow.sh submit-image --prompt "..." --n 4 --aspect 9:16 --ref ID [--ref ID2]
flow.sh submit-video --prompt "..." --n 1 --aspect 16:9 [--ref ID]
flow.sh job JOB_ID                               # state: submitting|submitted|generating|completed|failed|uncertain
flow.sh wait JOB_ID --timeout 1800               # sondeo cada >=10 s
flow.sh download JOB_ID --all                    # -> ~/storage/downloads/NEXUS_Flow/AAAA-MM-DD/
```
`--n` es el número de ejecuciones (1-10). `--aspect` se añade al prompt porque el puente no tiene campo de formato.

## Reglas
1. **Confirma el lote con el usuario antes de enviar** (prompts, n, referencias, formato), salvo que el `/goal` vigente ya lo autorice explícitamente.
2. **Ritmo prudente: máximo 4 trabajos simultáneos** en estado no terminal. Espera (`wait`) antes de lanzar más. Si varios subagentes trabajan a la vez, el total sigue siendo 4.
3. **Nunca borres nada** (ni en Flow, ni en el puente, ni descargas previas) y **no toques credenciales, cookies, perfiles de navegador ni la cuenta de Flow**. No llames a `/unlock`, `/lock` ni a rutas de administración.
4. Guarda siempre los resultados en `~/storage/downloads/NEXUS_Flow/<fecha>/` (lo hace `download` por defecto).
5. Si `status` falla en ambos hosts, o un trabajo queda `uncertain`/`failed`, para y avisa: no reintentes en bucle ni reenvíes el mismo lote sin confirmar (podría duplicar generaciones).
6. Informa con datos reales: job_id, estado, rutas descargadas. No inventes resultados.
