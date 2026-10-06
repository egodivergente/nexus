# NEXUS BRIDGE — Agent & MCP Integration Specification

## 1. Executive Summary

**Nexus Bridge** is an agentic gateway designed to allow external AI reasoning engines (such as Claude Desktop, ChatGPT, Codex, and custom autonomous agents) to communicate with the **Nexus Native Android Hub**.

Through Nexus Bridge, an agent does not interact with individual AI cloud APIs directly and does not manage API keys. Instead, the agent interacts with Nexus via standard **Model Context Protocol (MCP)** tool calls. Nexus orchestrates provider selection, parameter normalization, Keystore authentication, and native output rendering.

---

## 2. Architectural Design

```
+------------------------+
| External AI Agent      |  (ChatGPT / Claude / Codex / MCP Client)
+-----------+------------+
            |
            | Model Context Protocol (MCP JSON-RPC over TLS / ADB Forward)
            v
+------------------------+
|      Nexus Bridge      |  Local / Paired Gateway Service
+-----------+------------+
            |
            | Intent Broker (Provider Agnostic)
            v
+------------------------+
|       Nexus Core       |  (NexusProvider, Registry, Room Database, Media Engine)
+-----------+------------+
            |
    +-------+-------+
    |               |
    v               v
[Atlas Cloud]   [Future Providers] (OpenAI, Google, Replicate, fal)
```

---

## 3. Tool Contracts & Schemas

All bridge tools are serialized in strict JSON Schema conforming to MCP specification:

### 3.1. `nexus_list_providers`
Lists all active and configured providers on the device.
- **Input:** `{}`
- **Output:**
```json
{
  "providers": [
    {
      "id": "atlas_cloud",
      "displayName": "Atlas Cloud",
      "isConfigured": true,
      "supportedModalities": ["IMAGE", "VIDEO", "AUDIO", "CHAT"]
    }
  ]
}
```

### 3.2. `nexus_list_models`
Queries available models, optionally filtered by modality.
- **Input:**
```json
{
  "modality": "IMAGE" // Optional: "IMAGE" | "VIDEO" | "AUDIO" | "CHAT"
}
```
- **Output:**
```json
{
  "models": [
    {
      "id": "bytedance/seedream-v3.0",
      "displayName": "Seedream 3.0",
      "providerId": "atlas_cloud",
      "capabilities": ["TEXT_TO_IMAGE", "IMAGE_TO_IMAGE"],
      "parameters": [
        { "name": "aspect_ratio", "type": "ENUM", "options": ["1:1", "16:9", "9:16"] }
      ]
    }
  ]
}
```

### 3.3. `nexus_generate_image`
Submits an image generation job.
- **Input:**
```json
{
  "modelId": "bytedance/seedream-v3.0",
  "prompt": "Cyberpunk high-speed hovercraft over rain-slicked highway",
  "negativePrompt": "low quality, blur",
  "aspectRatio": "16:9",
  "seed": 42
}
```
- **Output:**
```json
{
  "jobId": "job_a1b2c3d4",
  "status": "PROCESSING",
  "pollIntervalSeconds": 3
}
```

### 3.4. `nexus_generate_video`
Submits a video generation task.
- **Input:**
```json
{
  "modelId": "bytedance/seedance-v1.0",
  "prompt": "Cinematic drone reveal of an alpine fortress at sunset",
  "durationSeconds": 5,
  "resolution": "1080p",
  "imageUrl": "https://..." // Optional for Image-to-Video
}
```
- **Output:**
```json
{
  "jobId": "job_v9x8y7z6",
  "status": "PROCESSING",
  "pollIntervalSeconds": 5
}
```

### 3.5. `nexus_get_generation`
Polls status and retrieves output metadata for a submitted job.
- **Input:**
```json
{
  "jobId": "job_a1b2c3d4"
}
```
- **Output:**
```json
{
  "jobId": "job_a1b2c3d4",
  "status": "COMPLETED",
  "progress": 1.0,
  "outputs": [
    {
      "type": "IMAGE",
      "url": "https://storage.atlascloud.ai/outputs/sample.png",
      "localUri": "content://media/external/images/media/4092"
    }
  ]
}
```

### 3.6. `nexus_cancel_generation`
Cancels an active generation job.
- **Input:** `{ "jobId": "job_a1b2c3d4" }`
- **Output:** `{ "cancelled": true }`

---

## 4. Security Architecture

1. **Zero Key Exposure:** External agents never receive provider API keys (e.g. Atlas Cloud Bearer tokens). Nexus securely manages keys in Android Keystore.
2. **Local-Only Communication:** By default, Bridge communication happens locally (Unix domain socket, local loopback, or ADB port forwarding `adb forward tcp:9292 tcp:9292`). No public ports are opened.
3. **Pairing & Cryptographic Tokens:** When an agent attempts first connection, Nexus requires user confirmation on the phone display and issues an ephemeral HMAC pairing token.
4. **Scoped Permissions:** External agents can be restricted (e.g., read-only access to catalog, or maximum generation quota per session).
5. **Full Audit Trail:** All bridge tool invocations are logged into the SQLite database for user transparency.
