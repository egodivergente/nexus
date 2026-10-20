# Skills de agy para NEXUS Flow

- `flow-bridge/` — Google Flow vía el puente NEXUS (`recovery.py`, `/api/flow`, puerto 8899).
- `nano-banana/` — generación de imágenes integrada de Antigravity.

Rutas del puente usadas por `flow.sh` (base `http://HOST:8899/api/flow`, `Authorization: Bearer <token>`):
`GET /status`, `POST /jobs`, `POST /references`, `GET /jobs/{id}`, `GET /jobs/{id}/downloads/{i}`.

## Instalación en Termux (Debian/proot del S25)

1. **Copiar las skills** a la carpeta de skills de agy. La ruta exacta no está confirmada; verifica con agy (p. ej. pregúntale «¿dónde cargas las skills de usuario?» o revisa su ayuda) y usa una de las candidatas:
   ```sh
   apt install -y curl jq
   mkdir -p ~/.gemini/antigravity-cli/skills      # candidata 1
   # mkdir -p ~/.gemini/skills                    # candidata 2 (estilo Gemini CLI)
   cp -r flow-bridge nano-banana ~/.gemini/antigravity-cli/skills/
   chmod +x ~/.gemini/antigravity-cli/skills/flow-bridge/*.sh
   termux-setup-storage   # en Termux (fuera de proot) si ~/storage no existe
   ```
2. **Token y hosts con permisos 600** (pega el recovery-token sin que quede en el historial):
   ```sh
   mkdir -p ~/.config/nexus-flow && chmod 700 ~/.config/nexus-flow
   (umask 077; read -rs T; printf '%s' "$T" > ~/.config/nexus-flow/token; unset T)
   printf '%s\n' http://100.72.111.97:8899/api/flow http://100.112.57.2:8899/api/flow > ~/.config/nexus-flow/hosts
   chmod 600 ~/.config/nexus-flow/token ~/.config/nexus-flow/hosts
   ```
   (El middleware del puente solo acepta como Host la IP Tailscale o el nombre MagicDNS.)
3. **Probar**:
   ```sh
   bash ~/.gemini/antigravity-cli/skills/flow-bridge/test_flow_sh.sh   # sin red
   ~/.gemini/antigravity-cli/skills/flow-bridge/flow.sh status
   ```
4. **Usar con /goal** en agy:
   ```
   /goal genera 12 variantes del personaje con estas 3 referencias (ref1.png ref2.png ref3.png) y descárgalas
   ```
   Esperado: `upload` de las 3 referencias → 3 trabajos `submit-image --n 4` con las 3 `--ref` (≤4 simultáneos) → `wait` → `download --all` en `~/storage/downloads/NEXUS_Flow/<fecha>/`. El `/goal` cuenta como autorización del lote; si no la incluye, agy debe confirmar antes de enviar.

## Subagentes en paralelo (/agents)

```
/agents
- subagente A: sube ref1-3 con flow-bridge y lanza 2 trabajos de 4 imágenes (plano medio, 9:16)
- subagente B: lanza 2 trabajos de vídeo 16:9 con las mismas referencias
- subagente C: espera los job_id de A y B y descarga todo en NEXUS_Flow/<fecha>
```
Límite global: 4 trabajos de Flow activos entre todos los subagentes. Ninguno borra ni toca credenciales.
