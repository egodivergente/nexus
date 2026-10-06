#!/usr/bin/env python3
"""
Nexus MCP Server — AI Studio Bridge for ChatGPT Apps SDK & MCP Protocol.
Provides typed access to Atlas Cloud's generative media ecosystem:
- Image generation & editing
- Video generation (Text-to-Video, Image-to-Video, Reference-to-Video)
- Audio & Music generation
- Model catalog inspection, schemas, and dynamic cost estimation.
"""

import os
import sys
import json
import logging
from pathlib import Path
from typing import Optional, List, Dict, Any

import httpx
from mcp.server.fastmcp import FastMCP

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("nexus-mcp")

# Initialize FastMCP application
mcp = FastMCP("Nexus Creative Studio", dependencies=["httpx", "pydantic", "python-dotenv"])

BASE_URL = os.environ.get("ATLAS_API_BASE_URL", "https://api.atlascloud.ai")
LOCAL_MODELS_PATH = Path(__file__).resolve().parent.parent / "models_atlas.json"

def get_api_key() -> str:
    key = os.environ.get("ATLAS_API_KEY", "").strip()
    return key

def get_headers() -> Dict[str, str]:
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "User-Agent": "Nexus-MCP-Bridge/2.0"
    }
    key = get_api_key()
    if key:
        headers["Authorization"] = f"Bearer {key}"
    return headers

def load_fallback_catalog() -> List[Dict[str, Any]]:
    """Loads models from local models_atlas.json dump if available."""
    if LOCAL_MODELS_PATH.exists():
        try:
            with open(LOCAL_MODELS_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                return data.get("data", [])
        except Exception as e:
            logger.warning(f"Failed to load local models dump: {e}")
    return []

def derive_input_image_field(model_id: str, categories: List[str] = None) -> Optional[str]:
    """Derives exact API field name for image input based on model ID and categories."""
    id_lower = model_id.lower()
    cat_upper = [c.upper() for c in (categories or [])]

    if "reference-to-video" in id_lower or "REFERENCE-TO-VIDEO" in cat_upper:
        return "reference_image_url"
    if "/edit" in id_lower:
        return "image"
    if "seedance" in id_lower or "seedream" in id_lower:
        return "first_frame_image" if "image-to-video" in id_lower else "image"
    if "minimax" in id_lower and "image-to-video" in id_lower:
        return "first_frame_image"
    if "wan-3.0" in id_lower or "wan-prime" in id_lower:
        return "first_frame_image" if "image-to-video" in id_lower else "image"
    if "kling" in id_lower:
        return "image"
    if "gemini-omni" in id_lower:
        return "image"
    if "image-to-video" in id_lower:
        return "image"
    return None

# =========================================================================
# MCP Tools (Read Operations)
# =========================================================================

@mcp.tool()
def nexus_list_models(
    modality: Optional[str] = None,
    provider: Optional[str] = None,
    capability: Optional[str] = None,
    has_discount: bool = False
) -> List[Dict[str, Any]]:
    """List available AI generative models on Nexus with optional filtering.

    Args:
        modality: Filter by modality ('Image', 'Video', 'Audio', 'Text')
        provider: Filter by organization/provider (e.g. 'OpenAI', 'Google', 'MiniMax', 'ByteDance', 'Qwen')
        capability: Filter by capability (e.g. 'image-to-video', 'text-to-image', 'reference-to-video')
        has_discount: If True, only returns models with active price discounts
    """
    models = []
    # Try fetching live models
    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.get(f"{BASE_URL}/api/v1/models", headers=get_headers())
            if resp.status_code == 200:
                models = resp.json().get("data", [])
    except Exception as e:
        logger.warning(f"Could not fetch live models, falling back to local catalog: {e}")

    if not models:
        models = load_fallback_catalog()

    results = []
    for m in models:
        model_id = m.get("model") or m.get("id") or ""
        m_type = m.get("type", "")
        org = m.get("organization", "")
        categories = m.get("categories") or []
        tags = m.get("tags") or []
        price_obj = m.get("price") or {}
        discount = price_obj.get("discount")
        actual_price = price_obj.get("actual") or {}
        base_price = actual_price.get("base_price") or actual_price.get("input_price")

        # Apply filters
        if modality and m_type.lower() != modality.lower():
            continue
        if provider and provider.lower() not in org.lower():
            continue
        if has_discount and (not discount or discount in ("0", "100")):
            continue
        if capability:
            cap_lower = capability.lower()
            matches_cap = (
                cap_lower in model_id.lower() or
                any(cap_lower in c.lower() for c in categories) or
                any(cap_lower in t.lower() for t in tags)
            )
            if not matches_cap:
                continue

        input_field = derive_input_image_field(model_id, categories)
        results.append({
            "id": model_id,
            "displayName": m.get("displayName") or m.get("name"),
            "organization": org,
            "type": m_type,
            "categories": categories,
            "tags": tags,
            "description": m.get("profile") or m.get("description", ""),
            "basePriceUsd": float(base_price) if base_price else None,
            "priceUnit": actual_price.get("unit"),
            "discount": discount,
            "inputImageField": input_field,
            "supportsImageInput": input_field is not None or any("IMAGE-TO-" in c for c in categories)
        })

    return results

@mcp.tool()
def nexus_get_model(model_id: str) -> Dict[str, Any]:
    """Get complete details, capabilities, and schema information for a specific AI model.

    Args:
        model_id: The unique model identifier (e.g. 'minimax/h3-max/image-to-video')
    """
    models = load_fallback_catalog()
    model = next((m for m in models if (m.get("model") == model_id or m.get("id") == model_id)), None)

    if not model:
        # Try fetching live
        try:
            with httpx.Client(timeout=10.0) as client:
                resp = client.get(f"{BASE_URL}/api/v1/models", headers=get_headers())
                if resp.status_code == 200:
                    for m in resp.json().get("data", []):
                        if m.get("model") == model_id or m.get("id") == model_id:
                            model = m
                            break
        except Exception:
            pass

    if not model:
        return {"error": f"Model '{model_id}' not found."}

    categories = model.get("categories") or []
    input_field = derive_input_image_field(model_id, categories)
    price_obj = model.get("price") or {}
    actual_price = price_obj.get("actual") or {}

    return {
        "id": model.get("model") or model.get("id"),
        "displayName": model.get("displayName") or model.get("name"),
        "organization": model.get("organization"),
        "type": model.get("type"),
        "description": model.get("profile") or model.get("description", ""),
        "categories": categories,
        "tags": model.get("tags") or [],
        "schemaUrl": model.get("schema"),
        "exampleUrl": model.get("example"),
        "inputImageField": input_field,
        "supportsImageInput": input_field is not None,
        "pricing": {
            "basePriceUsd": actual_price.get("base_price"),
            "unit": actual_price.get("unit"),
            "discount": price_obj.get("discount")
        }
    }

@mcp.tool()
def nexus_estimate_cost(
    model_id: str,
    duration_seconds: Optional[int] = 5,
    count: int = 1
) -> Dict[str, Any]:
    """Calculate the estimated generation cost before executing any job.

    Args:
        model_id: Model identifier (e.g. 'bytedance/seedream-v4' or 'minimax/h3-max/image-to-video')
        duration_seconds: Duration in seconds for video models (default: 5)
        count: Number of outputs to generate (default: 1)
    """
    model_info = nexus_get_model(model_id)
    if "error" in model_info:
        return model_info

    pricing = model_info.get("pricing", {})
    base_price = float(pricing.get("basePriceUsd") or 0.0)
    unit = (pricing.get("unit") or "").lower()
    discount_str = pricing.get("discount")

    if "second" in unit or "seg" in unit:
        total_usd = base_price * (duration_seconds or 5) * count
    else:
        total_usd = base_price * count

    # EUR conversion rate ~ 0.92
    total_eur = total_usd * 0.92

    return {
        "modelId": model_id,
        "basePriceUsd": base_price,
        "unit": unit,
        "discountPercent": discount_str,
        "estimatedTotalUsd": round(total_usd, 5),
        "estimatedTotalEur": round(total_eur, 5),
        "formattedEstimate": f"${total_usd:.4f} USD (~€{total_eur:.4f} EUR)"
    }

@mcp.tool()
def nexus_get_balance() -> Dict[str, Any]:
    """Check current Atlas Cloud account balance (requires ATLAS_API_KEY)."""
    key = get_api_key()
    if not key:
        return {"error": "ATLAS_API_KEY environment variable not configured."}

    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.get(f"{BASE_URL}/public/v1/balance", headers=get_headers())
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return {
                    "availableUsd": float(data.get("available", 0.0)),
                    "cashUsd": float(data.get("cash", 0.0)),
                    "bonusUsd": float(data.get("bonus", 0.0)),
                    "frozenUsd": float(data.get("frozen", 0.0))
                }
            return {"error": f"API error {resp.status_code}: {resp.text}"}
    except Exception as e:
        return {"error": str(e)}

# =========================================================================
# MCP Tools (Execution / Write Operations - Require validation)
# =========================================================================

@mcp.tool()
def nexus_create_image(
    model_id: str,
    prompt: str,
    aspect_ratio: str = "1:1",
    seed: int = -1,
    reference_image_url: Optional[str] = None
) -> Dict[str, Any]:
    """Submit an image generation or image editing job.
    IMPORTANT: This consumes account credits. Always verify cost with nexus_estimate_cost first.

    Args:
        model_id: Image model ID (e.g. 'bytedance/seedream-v4.7/text-to-image' or 'openai/gpt-image-2.5-sunburst/text-to-image')
        prompt: Detailed creative prompt
        aspect_ratio: Aspect ratio ('1:1', '16:9', '9:16', '4:3', '3:4')
        seed: Random seed (-1 for random)
        reference_image_url: Optional URL to reference image for img2img or editing
    """
    key = get_api_key()
    if not key:
        return {"error": "ATLAS_API_KEY environment variable not configured."}

    if not prompt.strip() and not reference_image_url:
        return {"error": "A prompt or reference_image_url is required."}

    payload: Dict[str, Any] = {
        "model": model_id,
        "prompt": prompt.strip(),
        "aspect_ratio": aspect_ratio
    }
    if seed != -1:
        payload["seed"] = seed

    if reference_image_url:
        payload["image"] = reference_image_url

    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(
                f"{BASE_URL}/api/v1/model/generateImage",
                json=payload,
                headers=get_headers()
            )
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return {
                    "status": "submitted",
                    "jobId": data.get("id"),
                    "predictionStatus": data.get("status", "processing"),
                    "outputs": data.get("outputs", []),
                    "modelId": model_id
                }
            return {"error": f"API error {resp.status_code}: {resp.text}"}
    except Exception as e:
        return {"error": str(e)}

@mcp.tool()
def nexus_create_video(
    model_id: str,
    prompt: str,
    duration: int = 5,
    aspect_ratio: str = "16:9",
    resolution: str = "720p",
    first_frame_url: Optional[str] = None,
    last_frame_url: Optional[str] = None,
    reference_image_url: Optional[str] = None
) -> Dict[str, Any]:
    """Submit a video generation job (Text-to-Video, Image-to-Video, or Reference-to-Video).
    Correctly routes image input to the model's exact expected API field (first_frame_image, image, etc.).
    IMPORTANT: This consumes account credits. Always verify cost with nexus_estimate_cost first.

    Args:
        model_id: Video model ID (e.g. 'minimax/h3-max/image-to-video', 'alibaba/wan-3.0-prime/image-to-video', 'google/gemini-omni-1.1-flash/image-to-video')
        prompt: Video action and camera motion prompt
        duration: Duration in seconds (usually 5 or 10)
        aspect_ratio: Aspect ratio ('16:9', '9:16', '1:1')
        resolution: Output quality ('720p', '1080p')
        first_frame_url: URL of starting frame for Image-to-Video
        last_frame_url: URL of optional ending frame for interpolation
        reference_image_url: URL of subject reference image for Reference-to-Video
    """
    key = get_api_key()
    if not key:
        return {"error": "ATLAS_API_KEY environment variable not configured."}

    # Validate image input requirement
    input_field = derive_input_image_field(model_id)
    if "image-to-video" in model_id.lower() and not first_frame_url:
        return {"error": f"Model '{model_id}' requires an input image (first_frame_url)."}

    payload: Dict[str, Any] = {
        "model": model_id,
        "prompt": prompt.strip(),
        "duration": duration,
        "aspect_ratio": aspect_ratio,
        "resolution": resolution
    }

    # Route image input to the model's exact expected API field name
    if first_frame_url:
        target_field = input_field or "first_frame_image"
        payload[target_field] = first_frame_url

    if last_frame_url:
        payload["last_frame_image"] = last_frame_url

    if reference_image_url:
        payload["reference_image_url"] = reference_image_url

    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(
                f"{BASE_URL}/api/v1/model/generateVideo",
                json=payload,
                headers=get_headers()
            )
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return {
                    "status": "submitted",
                    "jobId": data.get("id"),
                    "predictionStatus": data.get("status", "processing"),
                    "outputs": data.get("outputs", []),
                    "modelId": model_id
                }
            return {"error": f"API error {resp.status_code}: {resp.text}"}
    except Exception as e:
        return {"error": str(e)}

@mcp.tool()
def nexus_get_job(job_id: str) -> Dict[str, Any]:
    """Poll prediction status and check if generation is completed.

    Args:
        job_id: The prediction ID returned by nexus_create_image or nexus_create_video
    """
    key = get_api_key()
    if not key:
        return {"error": "ATLAS_API_KEY environment variable not configured."}

    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.get(
                f"{BASE_URL}/api/v1/model/prediction/{job_id}",
                headers=get_headers()
            )
            if resp.status_code == 200:
                data = resp.json().get("data", {})
                return {
                    "jobId": data.get("id", job_id),
                    "status": data.get("status"),
                    "outputs": data.get("outputs", []),
                    "error": data.get("error"),
                    "priceUsd": data.get("price")
                }
            return {"error": f"API error {resp.status_code}: {resp.text}"}
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    mcp.run()
