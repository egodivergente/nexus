---
name: nano-banana
description: Genera o edita imágenes con la generación de imágenes integrada de Antigravity (Nano Banana) cuando el usuario la pida expresamente o quiera una imagen rápida sin pasar por Google Flow.
---

# Nano Banana (generación integrada de Antigravity)

## Cuándo usarla
- El usuario dice «Nano Banana», «genera una imagen aquí» o quiere una edición rápida de una imagen local.
- Si pide expresamente Google Flow, lotes grandes o vídeo, usa la skill `flow-bridge`.

## Cómo
- Usa **la herramienta de generación de imágenes integrada de Antigravity** (la que agy exponga para Nano Banana). Esta skill no fija su nombre exacto: si dudas, consulta la lista de herramientas disponibles de agy en lugar de inventar un comando.
- Si el usuario aporta imágenes de referencia, pásalas a esa herramienta como entrada.
- Guarda cada resultado en `~/storage/downloads/NEXUS_Flow/<AAAA-MM-DD>/` con nombre `nanobanana_<HHMMSS>_<n>.png` (crea la carpeta con `mkdir -p`). Nunca sobrescribas un archivo existente.

## Reglas
- Confirma con el usuario prompt y número de imágenes antes de generar en lote, salvo que el `/goal` lo autorice.
- No borres archivos ni toques credenciales.
- Indica la ruta real de cada archivo guardado; si la herramienta no está disponible, dilo en vez de simularla.
