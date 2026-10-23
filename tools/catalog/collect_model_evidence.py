"""Collect public, exact-endpoint contracts for Nexus. GET only; no API keys or paid requests.

Raw responses stay in a private cache outside the repository. The app receives normalized
technical facts and provenance, never an inferred benchmark or a copied marketing article.
"""
import argparse
import concurrent.futures
import datetime
import hashlib
import json
from pathlib import Path
import urllib.request
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]


def facts(schema):
    definitions = schema.get('components', {}).get('schemas', {})
    definition = definitions.get('Input', schema)
    properties = definition.get('properties', {})
    result = []
    for key, value in properties.items():
        if not isinstance(value, dict):
            continue
        if '$ref' in value:
            value = definitions.get(value['$ref'].split('/')[-1], value)
        alternatives = value.get('anyOf', value.get('oneOf', []))
        for option in alternatives:
            if isinstance(option, dict) and option.get('type') != 'null':
                expanded = definitions.get(option.get('$ref', '').split('/')[-1], option)
                value = {**value, **expanded}
                break
        record = {'key': key, 'type': value.get('type', ''), 'required': key in definition.get('required', [])}
        for field in ('enum', 'minimum', 'maximum', 'minLength', 'maxLength', 'minItems', 'maxItems', 'default'):
            if field in value and value[field] is not None:
                record[field] = value[field]
        if value.get('items', {}).get('type'):
            record['itemType'] = value['items']['type']
        result.append(record)
    return result


def collect(cache, workers):
    models = json.loads((ROOT/'app/src/main/assets/atlas_catalog_snapshot.json').read_text('utf-8'))['data']
    urls = sorted({m.get('schema') for m in models if m.get('schema')})
    cache.mkdir(parents=True, exist_ok=True)

    def fetch(url):
        # URLs are taken verbatim from the real catalog; never construct guessed endpoints.
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or parsed.hostname != 'static.atlascloud.ai':
            return url, None, 'untrusted_schema_url'
        path = cache/(hashlib.sha256(url.encode()).hexdigest()+'.json')
        try:
            if path.exists():
                raw = path.read_bytes()
            else:
                request = urllib.request.Request(url, headers={'User-Agent': 'Nexus-contract-review/1.0'})
                with urllib.request.urlopen(request, timeout=20) as response:
                    raw = response.read()
                json.loads(raw)
                path.write_bytes(raw)
            return url, {'fields': facts(json.loads(raw)), 'sha256': hashlib.sha256(raw).hexdigest()}, None
        except Exception as error:
            return url, None, type(error).__name__

    contracts = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        for index, (url, data, error) in enumerate(pool.map(fetch, urls), 1):
            contracts[url] = (data, error)
            if index % 50 == 0:
                print(f'Contracts {index}/{len(urls)}', flush=True)
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    records = []
    for model in models:
        data, error = contracts.get(model.get('schema'), (None, 'no_schema_url'))
        records.append({'provider': 'atlas_cloud', 'model': model['model'], 'checkedAt': now,
            'source': model.get('schema', ''), 'documentation': model.get('readme', ''),
            'status': 'official_contract' if data else 'catalog_metadata',
            'fields': data['fields'] if data else [], 'sourceSha256': data['sha256'] if data else '',
            'error': error, 'catalogProfile': model.get('profile', ''), 'metadata': {k: model[k] for k in ('contextLength','maxCompletionTokens','avgTotalTPS','avgLatency','price','supported_features','supported_protocols','input_modalities','output_modalities','architectureType') if k in model}})
    crun = json.loads((ROOT/'app/src/main/assets/crun_models_snapshot.json').read_text('utf-8'))
    for model in crun:
        raw = {'properties': model.get('properties', {}), 'required': model.get('required', [])}
        records.append({'provider': 'crun', 'model': model['providerModelId'], 'checkedAt': now,
            'source': 'Crun OpenAPI / '+model.get('sourcePath', ''), 'documentation': '',
            'status': 'bundled_contract', 'fields': facts(raw),
            'sourceSha256': hashlib.sha256(json.dumps(raw, sort_keys=True).encode()).hexdigest(),
            'catalogProfile': model.get('summary', '')})
    destination = ROOT/'app/src/main/assets/model_endpoint_evidence.json'
    destination.write_text(json.dumps({'formatVersion': 1, 'checkedAt': now, 'records': records}, ensure_ascii=False, separators=(',', ':')), 'utf-8')
    report = {'records': len(records), 'verifiedAtlas': sum(r['status']=='official_contract' for r in records),
        'missingAtlas': [r['model'] for r in records if r['provider']=='atlas_cloud' and r['status']!='official_contract']}
    (cache/'coverage.json').write_text(json.dumps(report, indent=2), 'utf-8')
    print(json.dumps(report), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--workers', type=int, default=4)
    arguments = parser.parse_args()
    collect(arguments.cache, arguments.workers)
