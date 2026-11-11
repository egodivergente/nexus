"""Read public Atlas schemas and render the real forms in isolated, credential-free Edge.

No model inference, estimate, upload, provider-auth request, agent or Flow operation is
allowed. The real schema RPC/transport and frontend run against public live schemas;
the init response uses laboratory mode only to disable poll/send/background services.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import secrets
import sys
import tempfile
import threading
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_host as local


def run(evidence_path=None):
    from playwright.sync_api import sync_playwright

    additions = json.loads((local.UI / 'catalog-current-additions.json').read_text(encoding='utf-8-sig'))
    allowed_urls = {model['schemaUrl'] for model in additions}
    report = {'schemas': [], 'forms': [], 'provider_calls': 0, 'llm_calls': 0,
              'generation_calls': 0, 'credentials_loaded': False}
    original_fetch, original_dispatch = local.public_json, local.dispatch
    schema_calls, denied = Counter(), Counter()
    errors = []

    def schema_only(url, *args, **kwargs):
        if url not in allowed_urls:
            raise AssertionError('Read-only acceptance blocked an unexpected upstream URL.')
        assert not args and not kwargs.get('headers') and kwargs.get('body') is None
        schema_calls[url] += 1
        return original_fetch(url, *args, **kwargs)

    def read_only_dispatch(keys, operation, args):
        assert not keys, 'Acceptance must not load provider credentials.'
        if operation == 'init':
            with patch.object(local, 'OFFLINE_QA', True):
                return original_dispatch(keys, operation, args)
        if operation in ('schema', 'save'):
            return original_dispatch(keys, operation, args)
        denied[operation] += 1
        raise ValueError('Verificación de formularios: operación fuera del alcance de solo lectura.')

    def no_credentials(path):
        assert Path(path) == local.KEY_FILE and not Path(path).exists(), 'Credential path must be empty and isolated.'
        return {}

    with tempfile.TemporaryDirectory(prefix='nexus-atlas-readonly-') as directory, \
            patch.object(local, 'public_json', schema_only), \
            patch.object(local, 'dispatch', read_only_dispatch), \
            patch.object(local, 'windows_keys', no_credentials):
        local.DATA = Path(directory)
        local.MEDIA = local.DATA / 'media'
        local.MEDIA.mkdir()
        local.KEY_FILE = local.DATA / 'credentials.dpapi'
        local.OFFLINE_QA = False
        local.SCHEMA_CACHE.clear()
        for model in additions:
            value = local.dispatch({}, 'schema', {'url': model['schemaUrl'], 'refresh': True})
            schema = value['components']['schemas']['Input']
            assert isinstance(schema.get('properties'), dict)
            report['schemas'].append({'id': model['id'], 'input_fields': list(schema['properties']),
                                      'required': schema.get('required', [])})
        server = local.LoopbackHTTPServer(('127.0.0.1', 0), local.Handler)
        server.token = secrets.token_urlsafe(32)
        server.keys = {}
        threading.Thread(target=server.serve_forever, daemon=True).start()
        origin = f'http://127.0.0.1:{server.server_port}'
        try:
            with sync_playwright() as playwright:
                browser = playwright.chromium.launch(channel='msedge', headless=True)
                page = browser.new_page(viewport={'width': 1440, 'height': 1000})
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.route('**/*', lambda route: route.continue_() if route.request.url.startswith(origin + '/') else route.abort())
                page.goto(origin, wait_until='domcontentloaded')
                page.wait_for_function('()=>Boolean(window.nexusQA)&&models.some(m=>m.id==="kwaivgi/kling-v4.0/image-to-video")', timeout=30000)
                for model in additions:
                    result = page.evaluate('''async id=>{
                      await openModel(id);
                      if(!loaded||loaded.id!==id)throw Error('Model form failed to open: '+id);
                      return {id:loaded.id,unsupported:loaded.unsupported,
                        fields:loaded.fields.map(f=>({key:f.key,mime:f.mime,maximum:f.maximum,encoding:f.encoding})),
                        parameters:loaded.parameters.map(p=>p.key)};
                    }''', model['id'])
                    assert not result['unsupported'], result
                    for field in result['fields']:
                        assert page.locator('[data-pick="' + field['key'] + '"]').count() == 1
                    if model['id'] == 'heygen/avatar-5/digital-twin':
                        guard = page.evaluate('''async()=>{
                          const error=E.validate(loaded,state.draft.prompt,state.draft.values,state.draft.media);
                          await getQuote();
                          return {error,generate_disabled:document.querySelector('#generate').disabled};
                        }''')
                        assert 'prompt' in guard['error'] and 'audio' in guard['error'], guard
                        assert guard['generate_disabled'], guard
                        assert denied['request'] == 0
                        report['empty_heygen_blocked_before_quote'] = True
                    report['forms'].append(result)
                for model_id, field in [('kwaivgi/kling-v4.0/image-to-video', 'middle_images'),
                                        ('vidu/q4-preview/reference-to-video', 'reference_audios'),
                                        ('heygen/avatar-5/digital-twin', 'background_image')]:
                    await_expression = 'async id=>{await openModel(id);}'
                    page.evaluate(await_expression, model_id)
                    button = page.locator('[data-pick="' + field + '"]')
                    button.scroll_into_view_if_needed()
                    assert button.is_visible(), f'Missing live form attachment control: {field}'
                    if evidence_path:
                        destination = Path(evidence_path).with_name('atlas-form-' + field + '.png')
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        page.screenshot(path=str(destination), full_page=False)
                browser.close()
            assert not errors, errors
            report['page_errors'] = 0
            report['live_schema_fetches'] = sum(schema_calls.values())
            report['blocked_background_reads'] = dict(denied)
            assert not any(denied[op] for op in ('generate', 'request', 'upload', 'flowSubmit', 'flowConnect'))
            return report
        finally:
            server.shutdown()
            server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', help='Optional JSON report and public-model form screenshots, outside the repository.')
    args = parser.parse_args()
    result = run(args.evidence)
    if args.evidence:
        Path(args.evidence).parent.mkdir(parents=True, exist_ok=True)
        Path(args.evidence).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
