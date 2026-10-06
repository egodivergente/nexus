# Kling v3.0 Std Motion Control — Atlas Cloud API

> Kling 3.0 Standard Motion Control transfers motion from reference videos to animate still images. Upload a character image and a motion clip (dance, action, gesture), and the model extracts the movement to generate smooth, realistic video.

This is the machine-readable API reference for **Kling v3.0 Std Motion Control** on Atlas Cloud,
a unified API platform for 400+ AI models across text, image, video, audio and 3D.

- **Model ID**: `kwaivgi/kling-v3.0-std/motion-control`
- **Built by**: KWAIVGI
- **Modality**: Video
- **Model page**: https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/motion-control
- **API key**: https://www.atlascloud.ai/console/api-keys
- **Docs**: https://www.atlascloud.ai/docs

## Pricing on Atlas Cloud

- $0.107 per second of generated video
- Pay-as-you-go. No minimum spend, no subscription required.

> **These are the authoritative Atlas Cloud rates for this model.** Any price that
> appears in the vendor description further down refers to a different platform or
> a different model variant and does not apply here.

## Use this model from an AI agent

Atlas Cloud ships three first-party integration surfaces. All three authenticate
with the same API key via the `ATLASCLOUD_API_KEY` environment variable.

### MCP server

The official MCP server (`atlascloud-mcp`) exposes this model to any
MCP-compatible host — Claude Code, OpenAI Codex, Cursor, Gemini CLI, Goose,
Claude Desktop. One-line install:

```bash
# Claude Code
claude mcp add atlascloud -- npx -y atlascloud-mcp

# OpenAI Codex CLI
codex mcp add atlascloud -- npx -y atlascloud-mcp

# Gemini CLI
gemini mcp add atlascloud -- npx -y atlascloud-mcp

export ATLASCLOUD_API_KEY="your-api-key"
```

Then ask in plain English; the agent calls `atlas_generate_video` with `model: "kwaivgi/kling-v3.0-std/motion-control"`.
The server fetches each model's schema and validates parameters before submitting,
so invalid requests fail fast without spending credits.

MCP docs: https://www.atlascloud.ai/docs/mcp-server

### Agent Skills

`atlas-cloud-skills` is a portable skill package (API reference, code templates in
Python / Node.js / cURL, model IDs with pricing) for Claude Code, Cursor, Codex and
12+ other agents:

```bash
npx skills add AtlasCloudAI/atlas-cloud-skills
export ATLASCLOUD_API_KEY="your-api-key"
```

Skills docs: https://www.atlascloud.ai/docs/skills

### CLI

The `atlas` binary runs Atlas Cloud from a terminal or CI script. Async media jobs
are polled and downloaded automatically (use `--no-download` when a script only
needs the output URLs):

```bash
# Install (Homebrew, npm, or shell installer)
brew install AtlasCloudAI/tap/atlascloud
# npm install -g atlascloud-cli
# curl -fsSL https://raw.githubusercontent.com/AtlasCloudAI/cli/main/install.sh | sh

atlas auth login
atlas generate video kwaivgi/kling-v3.0-std/motion-control -p "Your prompt here"
```

CLI docs: https://www.atlascloud.ai/docs/cli

## HTTP API reference

- **Submit endpoint (POST)**: `https://api.atlascloud.ai/api/v1/model/generateVideo` — start an async generation; returns a `prediction_id`
- **Poll endpoint (GET)**: `https://api.atlascloud.ai/api/v1/model/prediction/{prediction_id}` — poll this until the prediction finishes
- **Model ID**: `kwaivgi/kling-v3.0-std/motion-control`


## API Information

This model can be used via our HTTP API or more conveniently via our client libraries.
See the input and output schema below, as well as the usage examples.


### Input Schema

The API accepts the following input parameters:

- **`model`** (`string`, _required_):
  model name
  - Default: `"kwaivgi/kling-v3.0-std/motion-control"`

- **`image`** (`string`, _required_):
  Supported image formats:.jpg /.jpeg /.png The size of the image file should not exceed 10MB, the width and height of the image should be no less than 300px, and the aspect ratio of the image should be between 1:2.5 and 2.5:1

- **`video`** (`string`, _required_):
  Supported video formats:.mp4/.mov The size of the video file should not exceed 10MB, the width and height of the video should be no less than 300px, and the aspect ratio of the video should be between 1:2.5 and 2.5:1

- **`character_orientation`** (`string`, _required_):
  Generate the orientation of the characters in the video, which can be selected to match the image or the video.
  - Options: "image", "video"

- **`prompt`** (`string`, _optional_):
  The positive prompt for the generation.

- **`keep_original_sound`** (`boolean`, _optional_):
  Whether to retain the original video sound
  - Default: `true`



**Required Parameters Example**:

```json
{
  "model": "kwaivgi/kling-v3.0-std/motion-control",
  "image": "",
  "video": "",
  "character_orientation": "image"
}
```


**Full Example**:

```json
{
  "model": "kwaivgi/kling-v3.0-std/motion-control",
  "image": "",
  "video": "",
  "character_orientation": "image",
  "prompt": "",
  "keep_original_sound": true
}
```


### Output Schema

The API returns the following output format:


- **`created_at`** (`string`, _optional_):
  ISO timestamp of when the request was created (e.g., “2023-04-01T12:34:56.789Z”).

- **`id`** (`string`, _optional_):
  Unique identifier for the prediction, the ID of the prediction to get.

- **`model`** (`string`, _optional_):
  Model ID used for the prediction.

- **`outputs`** (`array[string]`, _optional_):
  Array of URLs to the generated content (empty when status is not completed).

- **`status`** (`string`, _optional_):
  Status of the task: created, processing, completed, or failed.

- **`urls`** (`object`, _optional_):
  Object containing related API endpoints.



**Example Response**:

```json
{
  "created_at": "",
  "id": "",
  "model": "",
  "outputs": [
    ""
  ],
  "status": "",
  "urls": {}
}
```


## Usage Examples

### cURL

```bash
# Step 1: Start generation (async)
curl -X POST "https://api.atlascloud.ai/api/v1/model/generateVideo" \
  -H "Authorization: Bearer $ATLASCLOUD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
  "model": "kwaivgi/kling-v3.0-std/motion-control",
  "image": "",
  "video": "",
  "character_orientation": "image",
  "prompt": "",
  "keep_original_sound": true
}'

# Response will contain: {"code": 200, "data": {"id": "prediction_id", "status": "processing"}}

# Step 2: Poll for result (replace {prediction_id} with the id returned above)
curl -X GET "https://api.atlascloud.ai/api/v1/model/prediction/{prediction_id}" \
  -H "Authorization: Bearer $ATLASCLOUD_API_KEY"

# Keep polling until status is "completed", "succeeded" or "failed"
# When completed, outputs will contain the generated content URL(s)
```

## Additional Resources

### Documentation

- [Model Playground](https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/motion-control)

## About this model

_Vendor-supplied description. Any pricing or endpoint mentioned below refers to_
_other platforms — use the Atlas Cloud values above._

#### Kling V3.0 Standard Motion Control

Transfer motion from any video onto your character with Kling V3.0 Motion Control. Upload a reference image of your subject and a motion video — the model generates your character performing those exact movements. Perfect for dance videos, action sequences, and character animation.

#### Why It Looks Great

* **Precise motion transfer:** Accurately maps movements from reference video to your character.
* **Character preservation:** Maintains your subject's identity and appearance.
* **Audio retention:** Option to keep the original video's sound.
* **Extended duration:** Supports videos up to 30 seconds.
* **Orientation control:** Specify character facing direction for better results.
* **Prompt Enhancer:** Refine scene descriptions for enhanced output.

#### Parameters

| Parameter | Required | Description |
| :--- | :--- | :--- |
| image | Yes | Reference image of your character/person. |
| video | Yes | Motion reference video to transfer movements from. |
| character_orientation | Yes | Direction character is facing (front, side, back). |
| prompt | No | Additional scene description or style guidance. |
| negative_prompt | No | Elements to avoid in the generated video. |
| keep_original_sound | No | Retain audio from the original motion video. |

#### How to Use

1. **Upload character image** — a clear image of your subject.
2. **Upload motion video** — the video with movements to transfer.
3. **Select character orientation** — Generate the orientation of the characters in the video, which can be selected to match the image or the video.
4. **Write prompt** (optional) — add scene details or style guidance.
5. **Add negative prompt** (optional) — specify what to avoid.
6. **Toggle audio** — check to keep original video sound.
7. **Run** — click the button to generate.
8. **Download** — preview and save your video.

#### Pricing

Billed per second of generated video.

| Cost per second |
| --- |
| $0.126 |

_(Description truncated. Full text on the model page.)_

---

Atlas Cloud — one API for 400+ AI models. Model page: https://www.atlascloud.ai/models/kwaivgi/kling-v3.0-std/motion-control · Docs: https://www.atlascloud.ai/docs

