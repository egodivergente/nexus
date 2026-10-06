# Proxy del asistente de Nexus (Bedrock Converse)

```
Nexus (APK) --HTTPS + token de dispositivo--> nexus_proxy.py --boto3/IAM--> Amazon Bedrock (Converse)
```

La APK nunca ve credenciales de AWS. Solo guarda (cifrados en el Keystore) la URL del proxy y un
token de dispositivo que se puede revocar quitando su hash de la configuración.

## Puesta en marcha

1. `pip install boto3` (Python 3.10+).
2. Credenciales de AWS por la vía estándar de boto3 (perfil `aws_profile`, variables de entorno o rol).
   Permiso mínimo: `bedrock:InvokeModel` sobre el modelo / inference profile elegido.
3. `python nexus_proxy.py --new-token` → copia el **hash** a `device_token_sha256` y guarda el **token** para el móvil.
4. `copy config.example.json config.local.json` y rellena `region`, `allowed_models`
   (el model ID o inference profile de Claude que vayas a usar), `prices_per_mtok` y `daily_budget_usd`.
   `config.local.json` y `usage/` están en `.gitignore`.
5. `python nexus_proxy.py`

## Conectar el móvil

- **Depuración por USB:** `adb reverse tcp:8787 tcp:8787` y en Nexus › Asistente › ⚙ pon
  `http://127.0.0.1:8787`, el model ID y el token. La app solo permite texto plano a 127.0.0.1/localhost.
- **Uso normal:** publícalo por HTTPS (p. ej. `tailscale serve --https=443 http://127.0.0.1:8787`)
  y usa esa URL `https://…`. No abras el puerto 8787 a Internet.

## Controles

| Control | Configuración | Respuesta |
|---|---|---|
| Token de dispositivo | `device_token_sha256` (solo hashes) | 401 |
| Lista blanca de modelos | `allowed_models` | 422 |
| Tope de salida | `max_tokens_cap` | se recorta |
| Peticiones por minuto | `requests_per_minute` | 429 |
| Presupuesto diario (UTC) | `daily_budget_usd` + `prices_per_mtok`, y `daily_token_budget` | 402, sin llamar a Bedrock |
| Tamaño de petición | `max_request_bytes` | 413 |

El gasto del día se guarda en `usage/AAAA-MM-DD.json`. Antes de cada llamada se **reserva el peor caso**
(entrada estimada por tamaño + `maxTokens` de salida) bajo un cerrojo, así que varias peticiones a la vez
no pueden pasarse del presupuesto. Todo modelo permitido necesita precio en `prices_per_mtok`
(o `allow_unpriced_models: true`, y entonces solo lo frena el tope de tokens).

También se limitan imágenes por petición (`max_images_per_request`, `max_image_bytes`) y el número de
herramientas (`max_tools`). El proxy sirve HTTP plano: para usarlo fuera de `adb reverse` pon TLS delante
(Tailscale serve, Caddy…).

Los registros solo llevan modelo, tokens, coste, latencia y nombres de herramientas. Nunca prompts,
imágenes, tokens ni credenciales.

Este proxy solo paga tokens del LLM. **No puede generar imágenes ni vídeos**: eso lo hace la app
y solo tras pulsar GENERAR en la tarjeta de cada plan.

## Pruebas

`python -m unittest test_nexus_proxy -v` (cliente Bedrock falso; no hace llamadas a AWS).
