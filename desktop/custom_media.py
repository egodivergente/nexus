"""Configured media providers. Kotlin owns templates/parsing; Windows owns HTTP and receipts.

An uncertain creation is never retried automatically, including after a restart.
"""
import base64
import hashlib
import http.client
import json
import math
import mimetypes
from pathlib import Path
import re
import threading
import time
from urllib.parse import urlsplit, urlencode, parse_qsl
import uuid

from native_operations import atomic_json, resolve_media


class CustomMedia:
    def __init__(self, data, codec, read_secrets, write_secrets):
        self.data = Path(data)
        self.codec = codec
        self.read_secrets = read_secrets
        self.write_secrets = write_secrets
        self.config_file = self.data / 'custom-media-providers.json'
        self.key_file = self.data / 'custom-media-credentials.dpapi'
        self.jobs = self.data / 'custom-media-jobs'
        self.jobs.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.active = set()

    def configs(self):
        return json.loads(self.config_file.read_text('utf-8')) if self.config_file.exists() else {}

    def run_codec(self, config, action='validate', **args):
        return self.codec('media_codec', {'config': config, 'action': action, **args})

    def save(self, config, key=None):
        config = self.run_codec(config)['config']
        # Keys belong in the encrypted field, never in the versioned profile JSON.
        if any(k.lower() in {'authorization', 'x-api-key', 'api-key'} for k in config['extraHeaders']):
            raise ValueError('Configura la autenticación en auth y escribe la clave en su campo protegido.')
        for m in config['models']:
            for path in (m['createPath'], m['pollPath']):
                self.check_origin(config, config['baseUrl'].rstrip('/') + '/' + path.lstrip('/') if not urlsplit(path).scheme else path)
        with self.lock:
            configs = self.configs()
            old = configs.get(config['id'])
            if old and urlsplit(old['baseUrl']).netloc != urlsplit(config['baseUrl']).netloc:
                for path in self.jobs.glob('*.json'):
                    job = json.loads(path.read_text('utf-8'))
                    if job.get('providerId') == 'usr_' + config['id'] and job.get('status') in {'SENDING','PROCESSING','UNCERTAIN'}:
                        raise ValueError('Hay encargos pendientes en el endpoint anterior. Usa otro identificador para añadir el nuevo destino.')
            if key is not None and key.strip():
                keys = self.read_secrets(self.key_file)
                keys[config['id']] = key.strip()
                self.write_secrets(self.key_file, keys)
            configs[config['id']] = config
            atomic_json(self.config_file, configs)
        return self.list()

    def delete(self, identifier):
        with self.lock:
            for path in self.jobs.glob('*.json'):
                job = json.loads(path.read_text('utf-8'))
                if job.get('providerId') == 'usr_' + identifier and job.get('status') in {'SENDING', 'PROCESSING', 'UNCERTAIN'}:
                    raise ValueError('Este proveedor tiene envíos pendientes o sin confirmar. Conserva su configuración para consultarlos.')
            configs = self.configs()
            configs.pop(identifier, None)
            atomic_json(self.config_file, configs)
            keys = self.read_secrets(self.key_file)
            keys.pop(identifier, None)
            self.write_secrets(self.key_file, keys)
        return self.list()

    def list(self):
        configs = self.configs()
        keys = self.read_secrets(self.key_file)
        return {'configs': list(configs.values()), 'configured': ['usr_' + i for i in keys],
                'models': [self.catalog(p, m) for p in configs.values() for m in p['models']]}

    @staticmethod
    def catalog(p, m):
        cap = m['capability']
        image = cap in {'IMAGE_TO_IMAGE', 'IMAGE_TO_VIDEO', 'UPSCALE_IMAGE'}
        video = cap == 'UPSCALE_VIDEO'
        maximum = max(1, m['maxImages']) if image else 0
        multi = image and maximum > 1
        image_field = 'images' if multi else 'image'
        capabilities = {'IMAGE_TO_IMAGE':['IMAGE_TO_IMAGE','IMAGE_EDITING'],
                        'UPSCALE_IMAGE':['UPSCALE'],'UPSCALE_VIDEO':['UPSCALE','VIDEO_TO_VIDEO']}.get(cap,[cap])
        labels = {'TEXT_TO_IMAGE':'Texto a imagen','IMAGE_TO_IMAGE':'Imagen a imagen','TEXT_TO_VIDEO':'Texto a vídeo',
                  'IMAGE_TO_VIDEO':'Imagen a vídeo','UPSCALE_IMAGE':'Escalar imagen','UPSCALE_VIDEO':'Escalar vídeo'}
        def options(kind):
            return next((v['options'] for v in m['params'] if v['type'] == kind), [])
        params = []
        for v in m['params']:
            kind = v['type']
            params.append({'key': v['key'], 'displayName': v['label'] or v['key'],
                           'type': 'BOOLEAN' if kind == 'boolean' else 'ENUM' if v['options'] else 'TEXT',
                           'nativeType': 'integer' if kind in {'integer', 'seed', 'duration'} else 'number' if kind == 'float' else 'boolean' if kind == 'boolean' else 'string',
                           'defaultValue': v['default'], 'options': v['options'], 'isRequired': v['required'],
                           'minValue': v['min'], 'maxValue': v['max'], 'description': v['description'], 'isAdvanced': v['advanced']})
        return {'id': 'custom/' + p['id'] + '/' + m['id'], 'providerId': 'usr_' + p['id'],
                'providerModelId': m['providerModelId'] or m['id'], 'displayName': m['name'], 'family': p['name'], 'modelFamily': p['name'], 'organization': p['name'],
                'modalities': ['VIDEO' if cap in {'TEXT_TO_VIDEO', 'IMAGE_TO_VIDEO', 'UPSCALE_VIDEO'} else 'IMAGE'],
                'description': m['description'] or labels[cap] + ' · ' + p['name'], 'parameters': params, 'maxImages': maximum,
                'capabilities': capabilities, 'inputMode': 'VIDEO_INPUT' if video else 'MULTI_IMAGE' if multi else 'SINGLE_IMAGE' if image else 'TEXT_ONLY',
                'supportsImageInput': image, 'supportsFirstFrame': cap == 'IMAGE_TO_VIDEO', 'supportsReferenceImages': multi,
                'promptRequired': not (image or video), 'isMediaRequired': image or video,
                'firstFrameField': 'image' if image and not multi else None, 'imagesArrayField': 'images' if multi else None, 'supportsVideoInput': video,
                'contractMedia': ([{'key': image_field, 'label': 'Imágenes' if multi else 'Imagen', 'mime': 'image/*', 'maximum': maximum, 'encoding': 'array' if multi else 'string', 'required': True}] if image else []) +
                                 ([{'key': 'video', 'label': 'Vídeo', 'mime': 'video/*', 'maximum': 1, 'encoding': 'string', 'required': True}] if video else []),
                'videoField': 'video' if video else None, 'priceUsd': m['priceUsd'], 'basePriceUsd': m['priceUsd'], 'priceUnit': m['priceUnit'],
                'supportedDurations': options('duration'), 'supportedResolutions': options('resolution'), 'supportedAspectRatios': options('aspect_ratio'),
                'priceUnitVerified': False, 'customMedia': True}

    def model(self, identifier, configs=None):
        for p in (configs or self.configs()).values():
            for m in p['models']:
                if identifier == 'custom/' + p['id'] + '/' + m['id']:
                    return p, m
        raise ValueError('Modelo multimedia personalizado no disponible.')

    def quote(self, identifier, values):
        p, m = self.model(identifier)
        price, unit = m['priceUsd'], m['priceUnit'].strip().lower()
        # Only explicitly understood units are multiplied; unknown units never become seconds.
        count = 1.0
        if unit in {'second', 'seconds', 's', 'segundo'}:
            count = float(values.get('duration', 0))
            if count <= 0:
                raise ValueError('La tarifa por segundo necesita una duración positiva.')
        elif unit not in {'generation', 'request', 'generación', 'solicitud'}:
            raise ValueError('Unidad de precio sin correspondencia comprobada. Usa generation o second si procede.')
        if price is None or not math.isfinite(float(price)) or price < 0 or not math.isfinite(count):
            raise ValueError('El modelo no tiene una tarifa válida declarada.')
        amount = price * count
        if not math.isfinite(amount):
            raise ValueError('La estimación supera el rango numérico admitido.')
        return {'amount': amount, 'currency': 'USD', 'status': 'estimated',
                'configFingerprint': hashlib.sha256(json.dumps(p, sort_keys=True).encode()).hexdigest(),
                'source': 'user_declared', 'priceUnitVerified': False, 'time': int(time.time() * 1000),
                'detail': 'Estimación según tu tarifa configurada; el proveedor aplica el cargo final.'}

    @staticmethod
    def check_origin(config, url):
        base, target = urlsplit(config['baseUrl']), urlsplit(url)
        def origin(u):
            return u.scheme.lower(), u.hostname, u.port or (443 if u.scheme == 'https' else 80)
        if origin(base) != origin(target) or target.username or target.password or target.fragment:
            raise ValueError('El endpoint debe pertenecer al mismo origen que la URL base.')
        if target.scheme != 'https' and not (target.scheme == 'http' and target.hostname in {'localhost', '127.0.0.1', '::1'}):
            raise ValueError('El endpoint requiere HTTPS o un servidor local.')
        return target

    def http(self, config, call, multipart=None, json_response=True):
        uri = self.check_origin(config, call['url'])
        key = self.read_secrets(self.key_file).get(config['id'], '')
        auth = config['auth']
        headers = dict(config['extraHeaders'])
        if auth['mode'] != 'NONE' and not key:
            raise ValueError('Falta la clave protegida de este proveedor.')
        path = uri.path or '/'
        query = uri.query
        if auth['mode'] == 'BEARER':
            headers['Authorization'] = 'Bearer ' + key
        elif auth['mode'] == 'HEADER':
            headers[auth['name']] = auth.get('prefix', '') + key
        elif auth['mode'] == 'QUERY':
            query = urlencode(parse_qsl(query) + [(auth['name'], auth.get('prefix', '') + key)])
        path += '?' + query if query else ''
        timeout = config['httpTimeoutSeconds'] or None
        cls = http.client.HTTPSConnection if uri.scheme == 'https' else http.client.HTTPConnection
        conn = cls(uri.hostname, uri.port, timeout=timeout)
        try:
            if multipart:
                prefix, source, suffix, content_type = multipart
                headers['Content-Type'] = content_type
                headers['Content-Length'] = str(len(prefix) + source.stat().st_size + len(suffix))
                conn.putrequest(call['method'], path)
                for k, v in headers.items():
                    conn.putheader(k, v)
                conn.endheaders()
                conn.send(prefix)
                with source.open('rb') as f:
                    while block := f.read(1024 * 1024):
                        conn.send(block)
                conn.send(suffix)
            else:
                payload = json.dumps(call['body']).encode('utf-8') if call.get('body') is not None else None
                if payload is not None:
                    headers['Content-Type'] = 'application/json'
                conn.request(call['method'], path, payload, headers)
            response = conn.getresponse()
            # No redirects: a redirect may leak credentials or repeat a charged POST.
            if not 200 <= response.status < 300:
                raise ValueError('El proveedor respondió HTTP ' + str(response.status))
            raw = response.read(20 * 1024 * 1024 + 1)
            if len(raw) > 20 * 1024 * 1024:
                raise ValueError('Respuesta del proveedor demasiado grande.')
            if not json_response:
                return {'httpStatus': response.status}
            return json.loads(raw)
        except Exception as error:
            message = str(error)
            if key:
                message = message.replace(key, '[clave protegida]')
            raise ValueError(message) from None
        finally:
            conn.close()

    def upload(self, provider, identifier, config=None):
        p = config or self.configs().get(provider.removeprefix('usr_'))
        if not p:
            raise ValueError('Proveedor no disponible.')
        path = resolve_media(self.data, identifier, self.data / 'media')
        if p['maxUploadMb'] and path.stat().st_size > p['maxUploadMb'] * 1024 * 1024:
            raise ValueError('El archivo supera el límite configurado del proveedor.')
        mime = mimetypes.guess_type(path.name)[0] or 'application/octet-stream'
        if p['uploadMode'] == 'DATA_URI':
            return 'data:' + mime + ';base64,' + base64.b64encode(path.read_bytes()).decode('ascii')
        boundary = 'nexus' + uuid.uuid4().hex
        field = p['uploadFieldName']
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', field):
            raise ValueError('Nombre de campo de subida inválido.')
        prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="media{path.suffix}"\r\nContent-Type: {mime}\r\n\r\n').encode()
        suffix = ('\r\n--' + boundary + '--\r\n').encode()
        url = p['uploadPath'] if urlsplit(p['uploadPath']).scheme else p['baseUrl'].rstrip('/') + '/' + p['uploadPath'].lstrip('/')
        response = self.http(p, {'method': 'POST', 'url': url}, (prefix, path, suffix, 'multipart/form-data; boundary=' + boundary))
        result = self.run_codec(p, 'upload_result', response=response).get('url')
        if not isinstance(result, str) or not result.startswith(('http://', 'https://', 'data:')):
            raise ValueError('El proveedor no devolvió una URL de archivo válida.')
        return result

    def job_file(self, identifier):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}', identifier):
            raise ValueError('Identificador del encargo inválido.')
        return self.jobs / (identifier + '.json')

    def public_job(self, job):
        return {k: v for k, v in job.items() if k not in {'config', 'fingerprint'}}

    @staticmethod
    def recover_sending(job):
        uncertain = job.get('stage') != 'UPLOADING'
        job.update(status='UNCERTAIN' if uncertain else 'FAILED', uncertain=uncertain,
                   error='La aplicación se cerró durante el envío; no se repetirá automáticamente.' if uncertain else 'Se interrumpió la preparación. No se envió la generación.')

    def outputs(self, job, result):
        outputs = []
        for i, url in enumerate(result.get('outputs', [])):
            if url.startswith('data:'):
                header, payload = url.split(',', 1)
                if ';base64' not in header:
                    raise ValueError('El resultado incrustado requiere base64.')
                raw = base64.b64decode(payload, validate=True)
                mime = header[5:].split(';')[0]
                if mime not in {'image/png', 'image/jpeg', 'image/webp', 'video/mp4', 'video/webm'}:
                    raise ValueError('Formato del resultado incrustado no admitido.')
                name = uuid.uuid5(uuid.NAMESPACE_URL, 'nexus-custom/' + job['id'] + '/' + str(i)).hex + (mimetypes.guess_extension(mime) or '.bin')
                media = self.data / 'media'
                media.mkdir(parents=True, exist_ok=True)
                path = media / name
                temp = path.with_suffix('.tmp')
                temp.write_bytes(raw)
                temp.replace(path)
                url = '/media/' + name
            outputs.append(url)
        result['outputs'] = outputs
        return result

    def submit(self, identifier, model, media_input, cost):
        target = self.job_file(identifier)
        fingerprint = hashlib.sha256(json.dumps([model, media_input], sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        with self.lock:
            if target.exists():
                job = json.loads(target.read_text('utf-8'))
                if job['fingerprint'] != fingerprint:
                    raise ValueError('El identificador ya pertenece a otro encargo.')
                if job['status'] == 'SENDING' and identifier not in self.active:
                    self.recover_sending(job)
                    atomic_json(target, job)
                return self.public_job(job)
            p, m = self.model(model)
            quote = self.quote(model, media_input.get('params', {}))
            if not isinstance(cost, dict) or cost.get('source') != 'user_declared' or cost.get('configFingerprint') != quote['configFingerprint'] or cost.get('amount') != quote['amount'] or cost.get('currency') != 'USD' or not 0 <= time.time() * 1000 - cost.get('time', 0) <= 300000:
                raise ValueError('Consulta y acepta una estimación vigente antes de enviar.')
            self.run_codec(p, 'input', model=m['id'], input=media_input)
            job = {'id': identifier, 'remoteId': identifier, 'providerId': 'usr_' + p['id'], 'modelId': model,
                   'config': p, 'fingerprint': fingerprint, 'status': 'SENDING', 'outputs': [],
                   'stage': 'UPLOADING', 'startedAt': time.time(), 'lastPoll': 0, 'estimated': quote}
            atomic_json(target, job)
            self.active.add(identifier)
        try:
            def uploaded(url):
                if url.startswith('/media/'):
                    return self.upload('usr_' + p['id'], url, p)
                if not url.startswith(('http://', 'https://', 'data:')):
                    raise ValueError('Referencia multimedia no admitida.')
                return url
            resolved = {**media_input, 'imageUrls': [uploaded(u) for u in media_input.get('imageUrls', [])],
                        'videoUrl': uploaded(media_input['videoUrl']) if media_input.get('videoUrl') else None}
            call = self.run_codec(p, 'create', model=m['id'], input=resolved)
            self.check_origin(p, call['url'])
            job['stage'] = 'CREATING'
            with self.lock:
                atomic_json(target, job)
            response = self.http(p, call)
            result = self.run_codec(p, 'parse_create', model=m['id'], response=response)
            remote = result.pop('remoteId', None)
            if remote:
                job['providerTaskId'] = remote
            job.update(self.outputs(job, result))
        except Exception as error:
            uncertain = job['stage'] == 'CREATING'
            job.update(status='UNCERTAIN' if uncertain else 'FAILED', uncertain=uncertain,
                       error=('Envío sin confirmar; no se repetirá automáticamente. ' if uncertain else 'No se envió la generación: ') + str(error))
        finally:
            with self.lock:
                atomic_json(target, job)
                self.active.discard(identifier)
        return self.public_job(job)

    def status(self, identifier):
        target = self.job_file(identifier)
        with self.lock:
            job = json.loads(target.read_text('utf-8'))
            p = job['config']
            if identifier in self.active:
                return self.public_job(job)
            if job['status'] == 'SENDING':
                self.recover_sending(job)
                atomic_json(target, job)
            if job['status'] != 'PROCESSING':
                return self.public_job(job)
            if time.time() - job['lastPoll'] < p['pollIntervalSeconds']:
                return self.public_job(job)
            if p['maxWaitMinutes'] and time.time() - job['startedAt'] > p['maxWaitMinutes'] * 60:
                job.update(trackingExpired=True, pollError='Límite de seguimiento alcanzado. La tarea remota puede seguir activa.')
                atomic_json(target, job)
                return self.public_job(job)
            self.active.add(identifier)
        try:
            _, m = self.model(job['modelId'], {p['id']: p})
            call = self.run_codec(p, 'poll', model=m['id'], task_id=job['providerTaskId'])
            response = self.http(p, call)
            result = self.run_codec(p, 'parse_poll', model=m['id'], response=response)
            job.update(self.outputs(job, result))
            job.pop('pollError', None)
        except Exception as error:
            job['pollError'] = str(error)
        finally:
            job['lastPoll'] = time.time()
            with self.lock:
                atomic_json(target, job)
                self.active.discard(identifier)
        return self.public_job(job)

    def command(self, op, args):
        if op == 'list': return self.list()
        if op == 'save': return self.save(args['config'], args.get('key'))
        if op == 'delete': return self.delete(args['id'])
        if op == 'quote': return self.quote(args['model'], args.get('values', {}))
        if op == 'upload': return self.upload(args['provider'], args['id'])
        if op == 'submit': return self.submit(args['id'], args['model'], args['input'], args.get('cost'))
        if op == 'status': return self.status(args['id'])
        if op == 'test':
            p = self.configs()[args['id']]
            path = p['healthPath']
            if not path:
                raise ValueError('Configura healthPath para comprobar la conexión sin crear una generación.')
            url = path if urlsplit(path).scheme else p['baseUrl'].rstrip('/') + '/' + path.lstrip('/')
            return self.http(p, {'method': 'GET', 'url': url}, json_response=False)
        if op == 'cancel': raise ValueError('Este protocolo no configura cancelación remota. No se ha cancelado el encargo.')
        raise ValueError('Operación multimedia desconocida.')
