# Nexus Studio MCP Server

Servidor MCP (Model Context Protocol) para conectar Nexus y Atlas Cloud con clientes de IA (ChatGPT Desktop, Claude Desktop, Cursor, etc.).

## Características

- **`nexus_list_models`** (READ): Catálogo completo con filtros por modalidad (Image, Video, Audio), proveedor, capacidad (e.g. `image-to-video`), y ofertas.
- **`nexus_get_model`** (READ): Ficha técnica con schema, capabilities y pricing.
- **`nexus_estimate_cost`** (READ): Estimación de coste en USD y EUR antes de generar nada.
- **`nexus_create_image`** (WRITE): Generación/edición de imagen con validación de inputs.
- **`nexus_create_video`** (WRITE): Generación de vídeo con ruteo automático del campo de imagen (`first_frame_image`, `image`, `reference_image_url`, etc.) según el modelo.
- **`nexus_get_job`** (READ): Consulta de estado y URLs de resultado.
- **`nexus_get_balance`** (READ): Saldo actual de la cuenta.

## Instalación

```bash
cd mcp-server
pip install -r requirements.txt
```

## Configuración

Define la variable de entorno con tu API Key de Atlas Cloud:

```bash
# Windows PowerShell
$env:ATLAS_API_KEY = "tu_api_key_aqui"

# Linux / macOS
export ATLAS_API_KEY="tu_api_key_aqui"
```

## Integración con ChatGPT Desktop / Claude Desktop

Añade la configuración en tu archivo `claude_desktop_config.json` o configuración MCP:

```json
{
  "mcpServers": {
    "nexus-studio": {
      "command": "python",
      "args": ["C:/Users/User/.gemini/antigravity/scratch/nexus/mcp-server/nexus_mcp_server.py"],
      "env": {
        "ATLAS_API_KEY": "tu_api_key_aqui"
      }
    }
  }
}
```

## Ejecución directa (Prueba local)

```bash
python nexus_mcp_server.py
```
