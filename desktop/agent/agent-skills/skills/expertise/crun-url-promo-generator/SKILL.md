---
id: crun-url-promo-generator
kind: expertise
title: crun-url-promo-generator
version: 1
updated: 2026-10-11
applies_to: [IMAGE, VIDEO, AUDIO]
triggers: ["crun", "url promo generator"]
sources:
  - url: https://github.com/CrunTeam/crun-agent-skills
    title: CrunTeam original skills
    accessed: 2026-10-11
---

## Adaptacion nativa para NEXUS Agent Android
Esta version se ejecuta con las herramientas registradas de NEXUS, no con Python ni una shell. Las instrucciones originales que mencionan el CLI, archivos de configuracion o comandos son referencia conceptual: NO pedir al usuario ejecutarlos ni afirmar que se han ejecutado.
- Modelos: `search_models`, `list_models`, `get_model`, `get_model_schema`. Respetar el modelo elegido; no imponer un router. El registro vivo de NEXUS prevalece sobre nombres y catalogos del documento original.
- Coste: `estimate_generation_cost` con la configuracion real. Usar las credenciales Crun ya configuradas en Ajustes; no pedir ni escribir claves en el chat.
- Medios: `search_assets` e `inspect_asset_metadata`; reutilizar referencias del proyecto. Pasar los medios al pipeline nativo, que gestiona su subida. No enviar rutas locales como URLs del proveedor.
- Crear: `prepare_generation` y `submit_generation` mediante el flujo nativo. No crear tarjetas duplicadas ni confirmaciones adicionales a las que ya gestiona la app. Las preferencias explicitas del usuario prevalecen sobre las recomendaciones de confirmacion del texto original.
- Seguimiento: `get_generation_status` con el job_id real; conservarlo y consultar antes de repetir una peticion incierta. No inventar task_id ni resultados.
- Otras habilidades de este paquete: cargar con `load_skill` usando su id, indicado en las referencias de abajo.
- Aplicar el criterio creativo del documento original. Una receta no instala una herramienta nueva: si el registro no dispone de una operacion (por ejemplo un efecto o descarga especifica), indicarlo sin simular exito.

## Guia original Crun adaptada al entorno nativo


# Crun URL Promotional Media Generator

Use this skill to extract key selling points, product highlights, and brand aesthetics from a webpage URL (e.g., e-commerce product link, SaaS landing page, store homepage, article link) and transform them into professional AI promotional images or promotional videos using Crun image/video models.

Use `../../../runtime/crun_cli.py`, `../../../catalog/models.json`, and the shared child skills:

- `../../crun-model-router/load_skill: crun-agent-skills` — model routing & live schema inspection
- `../../crun-account-credits/load_skill: crun-agent-skills` — balance & affordability estimation
- `../../crun-task-runner/load_skill: crun-agent-skills` — task creation, monitoring, recovery, result delivery (the authority)

---

## Trigger

Activate for any request involving: generating promotional images, posters, ad graphics, or video ads from a URL or web link — e.g., `crun-url-promo-generator`, website link to poster, product link to promo video, URL to marketing image, web page to video ad, link to promotional clip — even if the user never mentions "Crun" or a specific model name.

---

## Execution Workflow

```text
[ User Request ] (URL + Optional Additional Notes/Preferences)
    │
    ├── 1. URL Analysis & Content Extraction
    │       ├─ Extract page title, product name, core selling points, key visual elements & color tone
    │       └─ Extract product image/logo (if accessible) → Upload: python <runtime>/crun_cli.py upload <local-file>
    │
    ├── 2. Media Type & Style Resolution
    │       ├─ Resolve Output Type: Promotional Image (Poster / Graphic) vs Promotional Video (Commercial / Ad Clip)
    │       ├─ Resolve Visual Style & Aspect Ratio (Interactive AskUserQuestion if unspecified)
    │       └─ Synthesize structured visual & narrative prompts (English)
    │
    ├── 3. Model Routing & Schema Inspection
    │       ├─ Pick image model (T2I/I2I) or video model (T2V/I2V) based on media type & reference availability
    │       └─ Inspect live schema: python <runtime>/crun_cli.py models describe --model <model>
    │
    ├── 4. Estimate & Confirmation Gate
    │       ├─ Estimate credits (crun-account-credits): task estimate --model <model> --input-file <input.json>
    │       └─ Present extracted selling points & task summary → Require explicit user OK before spending
    │
    └── 5. Task Creation & Result Delivery (crun-task-runner)
            ├─ Create task: python <runtime>/crun_cli.py task create --model <model> --input-file <input.json>
            ├─ Poll status: python <runtime>/crun_cli.py task wait --task-id <task_id>
            └─ Deliver local media path & inline image/video preview
```

---

## Step 1 — URL Analysis & Content Extraction

1. **Extract Webpage Information**:
   Use web browsing or search capabilities (`read_url_content`, `search_web`) to read the target URL's content.
   - **Product / Brand Name**: Identify the primary subject or service name.
   - **Core Selling Points**: Extract 2–4 key features, benefits, or value propositions (e.g., "AI-powered", "Ultra lightweight", "24/7 battery life", "Premium organic ingredients").
   - **Visual Tone & Theme**: Identify the visual style of the brand/product (e.g. minimalist high-tech, luxury elegance, vibrant energy, eco-friendly natural).
   - **Product Imagery / Logo**: If product key visual images or logos are present, fetch/upload them via `crun_cli.py upload <file>` to obtain a Crun `file_url` for image-edit (I2I) or image-to-video (I2V) workflows.

2. **Fallback for Inaccessible URLs**:
   If fetching the URL directly fails (e.g., anti-scraping or paywall), ask the user for a quick text snippet, screenshot, or product image, and proceed seamlessly without halting the workflow.

---

## Step 2 — Media Type, Style & Format Resolution

If the user did not specify the output media type, aspect ratio, or style in their request, present choices via interactive option buttons (`AskUserQuestion`):

1. **Media Type**:
   - **Promotional Image (Poster / Graphic)**: Static ad poster, product display graphic, social media banner, feature infographic.
   - **Promotional Video (Commercial / Ad Clip)**: 5–10s dynamic product spotlight video, commercial clip, social media story ad.

2. **Visual Style**:
   - **Modern Tech / Minimalist**: Clean geometric shapes, sleek lighting, soft metallic/matte textures.
   - **High-End Luxury / Premium**: Studio lighting, deep rich contrast, gold/dark slate accents, high-fashion aesthetic.
   - **Vibrant & Dynamic**: Bold color contrasts, energetic composition, modern pop/gradient visual accents.
   - **Cinematic Commercial**: Dramatic key lighting, shallow depth of field, realistic environment lighting.

3. **Aspect Ratio**:
   - **3:4 / 9:16**: Vertical (Redbook, Instagram Reels, TikTok, Mobile Ad).
   - **16:9 / 3:2**: Horizontal (Website Hero Banner, YouTube, Desktop Display).
   - **1:1**: Square (E-commerce main picture, Social feeds).

---

## Step 3 — Model Selection & Prompt Generation

### Model Routing Matrix

Select preferred models based on output media type and whether source media was extracted/uploaded:

| Output Type | Input Source | Preferred Models (Text-Driven) | Preferred Models (Reference-Driven, `supports_reference: true`) | Recommended Aspect |
|---|---|---|---|---|
| **Promotional Image** | URL Content Only | `bytedance/seedream-5-pro`, `openai/gpt-image-2-premium`, `qwen-image-3.0-pro` | `bytedance/seedream-5-pro`, `qwen-image-edit-2.0-pro`, `google/nano-banana-pro` | 3:4, 16:9, 1:1 |
| **Promotional Video** | URL Content Only | `kling/kling-v2-master`, `minimax/video-01-live`, `vidu/vidu-v2` | `kling/kling-v2-master`, `bytedance/omni-human-pro`, `kling/kling-v1-6-pro` | 9:16, 16:9 |

### Prompt Structure (English)

Image and video models perform best when visual context is specified in structured English. Translate extracted selling points and visual requirements into clear visual prompts:

- **Promotional Image Prompt Template**:
  `A high-end commercial promotional poster for [PRODUCT_NAME]. Highlighting key features: [SELLING_POINTS]. Set in a [VISUAL_STYLE] environment with professional studio lighting, crisp product textures, modern typography composition space, and elegant visual atmosphere. Masterpiece quality, 8k resolution.`

- **Promotional Video Prompt Template**:
  `A cinematic promotional commercial video for [PRODUCT_NAME]. Smooth dynamic camera motion showcasing [SELLING_POINTS]. [VISUAL_STYLE] visual aesthetic, vibrant studio lighting, realistic reflections, professional advertising cinematography, fluid motion.`

After picking the model, inspect its live schema before constructing payload:

```text
python <root>/runtime/crun_cli.py models describe --model <model>
```

---

## Step 4 — Estimate & Confirmation Gate

Read `../../crun-account-credits/load_skill: crun-agent-skills`. Estimate the exact input payload before creating any task:

```text
python <root>/runtime/crun_cli.py task estimate --model <model> --input-file <input.json>
```

Require `affordable: true`. Present a clear confirmation summary card containing:
- **Extracted Product / Brand**: `[PRODUCT_NAME]`
- **Key Selling Points Identified**: `[SELLING_POINTS]`
- **Target Output**: `[Media Type] ([Aspect Ratio])`
- **Selected Model**: `[MODEL_NAME]`
- **Estimated Credit Cost**: `[CREDITS]`

Wait for explicit user confirmation via interactive buttons before spending credits.

---

## Step 5 — Create & Deliver

Read `../../crun-task-runner/load_skill: crun-agent-skills` for task lifecycle authority. Create once and poll:

```text
python <root>/runtime/crun_cli.py task create --model <model> --input-file <input.json>
python <root>/runtime/crun_cli.py task wait --task-id <task_id> --timeout-seconds 120
```

Follow `crun-task-runner` for all timeout recovery and status checks.

---

## Delivery Format

Deliver the completion summary with normalized metadata and media result:

```text
✅ Promotional media generation completed!
- Task ID: <task_id>
- Source URL: <url>
- Media Type: <Image / Video>
- Model: <model>
- Credits Spent: <credits>
- File Path: <local_media_path>

[Inline Preview of Image / Video]
```
