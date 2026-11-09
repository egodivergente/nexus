"""The accepted Android video selection, applied only to NEW Desktop submissions.

The full catalog and user hidden ids remain available for history and old drafts.
No provider call, migration, credential access or generation occurs in this module.
"""
import json
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VISUAL_FIELDS = frozenset(('image', 'images', 'image_url', 'image_urls', 'img_urls', 'input_image',
    'input_images', 'input_image_url', 'input_image_urls', 'reference_image_url', 'reference_image_urls',
    'reference_images', 'ref_images', 'first_frame_image', 'first_frame', 'start_image', 'last_image',
    'last_frame_image', 'end_image', 'video', 'videos', 'video_url', 'video_urls', 'input_video',
    'input_video_url', 'source_video', 'source_video_url', 'reference_video_urls', 'reference_videos',
    'promptImage', 'promptVideo', 'imageUrls', 'videoUrl', 'refers'))
CRUN_ALIASES = {'crun/kling-v3-std': 'kling/v3', 'crun/kling-v3-pro': 'kling/v3',
    'crun/kling-v3-motion-control-std': 'kling/v3-motion-control',
    'crun/kling-v3-motion-control-pro': 'kling/v3-motion-control'}


@lru_cache(maxsize=1)
def policy():
    return json.loads((ROOT / 'ui' / 'curated-video-selection.json').read_text('utf-8-sig'))


@lru_cache(maxsize=1)
def catalog():
    models = json.loads((ROOT / 'ui' / 'catalog.json').read_text('utf-8-sig'))
    additions = ROOT / 'ui' / 'catalog-current-additions.json'
    known = {(m.get('providerId'), m.get('id')) for m in models}
    if additions.is_file():
        models += [model for model in json.loads(additions.read_text('utf-8-sig'))
                   if (model.get('providerId'), model.get('id')) not in known]
    return models


def keys(model):
    provider = model.get('providerId', '')
    return {f'{provider}:{value}' for identifier in (model.get('id'), model.get('providerModelId'))
            if identifier for value in (identifier, identifier.removeprefix('crun/'))}


def in_scope(model):
    return model.get('providerId') in policy()['curatedProviders'] and 'VIDEO' in model.get('modalities', []) and 'CHAT' not in model.get('modalities', [])


def is_generator(model):
    return in_scope(model) and bool(keys(model).intersection(policy()['generators']))


def tool_category(model):
    return next((policy()['tools'][key] for key in keys(model) if key in policy()['tools']), None) if in_scope(model) else None


def is_allowed(model):
    return not in_scope(model) or is_generator(model) or tool_category(model) is not None


def _visual(value):
    if isinstance(value, str):
        return bool(value.strip())
    if isinstance(value, list):
        return any(_visual(item) for item in value)
    if isinstance(value, dict):
        if value.get('type') in ('audio', 'voice'):
            return False
        return any(_visual(value.get(key)) for key in ('url', 'uri', 'image', 'video', 'asset_id', 'id'))
    return False


def has_visual_input(model, body):
    values = body.get('input', {}) if model.get('providerId') == 'crun' else body
    if not isinstance(values, dict):
        return False
    fields = VISUAL_FIELDS | {item['key'] for item in model.get('contractMedia', [])
        if item.get('mime', '').startswith(('image/', 'video/'))}
    return any(_visual(values.get(field)) for field in fields)


def native_model(model):
    return model.get('providerModelId') or CRUN_ALIASES.get(model.get('id')) or model.get('id')


def _matches_route(model, provider, path, body):
    if model.get('providerId') != provider or native_model(model) != body.get('model'):
        return False
    if provider == 'runway' and path.rstrip('/') != '/v1/' + str(model.get('contractEndpoint', '')):
        return False
    if provider == 'atlas_cloud':
        modality = {'/api/v1/model/generateImage': 'IMAGE', '/api/v1/model/generateVideo': 'VIDEO',
                    '/api/v1/model/generateAudio': 'AUDIO', '/v1/chat/completions': 'CHAT'}.get(path.rstrip('/'))
        if modality is not None and modality not in model.get('modalities', []):
            return False
    values = body.get('input', {}) if provider == 'crun' else body
    if not isinstance(values, dict):
        return False
    # Shared Crun endpoints distinguish Pro/Std via a declared singleton, not display names.
    for parameter in model.get('parameters', []):
        options = parameter.get('options') or []
        if parameter.get('key') == 'mode' and len(options) == 1 and str(values.get('mode')) != str(options[0]):
            return False
    return True


def validate_generation(args, models=None):
    """Validate an exact NEW generate RPC before upload/submission; never use for status/history.

    catalog_model_id is the route id already recorded in the local job. Legacy clients can
    resolve a unique native endpoint+mode. Ambiguity is rejected instead of selecting a route.
    """
    provider, path, body = args.get('provider'), args.get('path', ''), args.get('body')
    if not isinstance(body, dict):
        raise ValueError('Solicitud de generación no válida.')
    if provider not in policy()['curatedProviders']:
        return None
    all_models = catalog() if models is None else models
    requested = args.get('catalog_model_id')
    candidates = [model for model in all_models if (not requested or model.get('id') == requested)
                  and _matches_route(model, provider, path, body)]
    if not candidates:
        if path.endswith('generateVideo') or provider in ('runway', 'crun'):
            raise ValueError('La ruta de generación no coincide con un contrato del catálogo actual. Reabre el modelo antes de enviar.')
        return None
    accepted = [model for model in candidates if is_allowed(model)]
    if not accepted:
        raise ValueError('Este modelo se retiró de las nuevas generaciones. Su historial y material se conservan.')
    if any(is_generator(model) for model in accepted) and not any(has_visual_input(model, body) for model in accepted):
        raise ValueError(policy()['visualInputMessage'])
    # A shared native model may represent more than one modality. Endpoint and media must agree.
    if len(accepted) > 1 and len({(m.get('id'), m.get('contractEndpoint')) for m in accepted}) > 1:
        raise ValueError('La ruta del proveedor es ambigua. Reabre el modelo y conserva su identificador exacto.')
    return accepted[0]


def validate_custom_generation(model, media_input):
    """Custom templates retain their contracts, credentials and history; prune only audited video."""
    if not is_allowed(model):
        raise ValueError('Este modelo se retiró de las nuevas generaciones. Su historial y material se conservan.')
    if is_generator(model) and not has_visual_input(model, media_input):
        raise ValueError(policy()['visualInputMessage'])


def reaches_byteplus(model, values):
    identifiers = (model.get('id', ''), model.get('providerModelId', ''), model.get('family', ''), model.get('displayName', ''))
    if not any('seedance' in str(value).lower() for value in identifiers):
        return False
    if model.get('providerId') == 'usr_byteplus-media' or any(str(value).startswith('custom/byteplus-media/') for value in identifiers[:2]):
        return True
    defaults = {p.get('key'): p.get('defaultValue') for p in model.get('parameters', [])}
    return model.get('providerId') == 'crun' and any(str(values.get(key, defaults.get(key))).lower() == 'true'
        for key in ('direct_byteplus', 'byteplus_fallback'))


def route_order(routes, manual=False):
    """Only equivalent, eligible routes with request-total quotes may be supplied by a caller.

    A manual choice is preserved. This helper does not create fallbacks or retry requests.
    """
    if manual:
        return list(routes)
    return sorted(routes, key=lambda route: (reaches_byteplus(route['model'], route.get('parameters', {})), route['total_usd']))
