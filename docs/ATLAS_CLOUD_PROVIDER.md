# Atlas Cloud Provider Specification & Integration Guide

## 1. Overview

**Atlas Cloud** (`api.atlascloud.ai`) is the primary AI provider integrated into **Nexus**. It aggregates over 400 generative AI models across video, image, audio, and language modalities.

---

## 2. API Endpoints

All requests use HTTPS and require standard HTTP Bearer authentication:
`Authorization: Bearer <ATLAS_API_KEY>`

| Endpoint | Method | Purpose | Response Behavior |
| :--- | :--- | :--- | :--- |
| `/v1/models` | `GET` | List available models & verify authentication | Synchronous (OpenAI-compatible) |
| `/api/v1/model/generateImage` | `POST` | Text-to-Image and Image-to-Image generation | Asynchronous (Returns prediction ID) |
| `/api/v1/model/generateVideo` | `POST` | Text-to-Video and Image-to-Video generation | Asynchronous (Returns prediction ID) |
| `/api/v1/model/prediction/{id}` | `GET` | Poll task status and fetch final output URLs | Synchronous polling |
| `/api/v1/model/uploadMedia` | `POST` | Upload local media files for img2img / img2video | Multipart upload (Returns remote URL) |

---

## 3. Workflow & Polling Lifecycle

1. **Submission (`POST /generateImage` or `/generateVideo`):**
   - Payload specifies `model`, `prompt`, dynamic parameters, and optional media URLs.
   - API responds immediately with `{ "code": 200, "data": { "id": "pred_xxx", "status": "processing" } }`.

2. **Asynchronous Polling (`GET /prediction/{id}`):**
   - Nexus polls every 3 to 6 seconds using exponential backoff to preserve battery life.
   - States:
     - `processing` -> Nexus status: `PROCESSING`
     - `completed` / `succeeded` -> Nexus status: `COMPLETED` (outputs extracted from `data.outputs`)
     - `failed` -> Nexus status: `FAILED` (error extracted from `data.error`)

---

## 4. Curated Models in Nexus

1. **Image Generation:**
   - `bytedance/seedream-v3.0` (Aesthetic photorealism, custom aspect ratios)
   - `black-forest-labs/flux-1-schnell` (Ultra-fast distilled 4-step generation)
   - `black-forest-labs/flux-1-dev` (Pro-level prompt adherence & guidance scale control)

2. **Video Generation:**
   - `bytedance/seedance-v1.0` (Smooth cinematic motion, 5s & 10s durations)
   - `kuaishou/kling-v1.6` (Physical simulation & camera controls)
   - `google/veo3` (1080p high definition with audio toggle)

3. **Audio & Chat:**
   - `f5-tts/f5-tts-v1` (High-fidelity natural voice synthesis)
   - `deepseek/deepseek-v3` (Frontier 671B reasoning model for prompt engineering)
