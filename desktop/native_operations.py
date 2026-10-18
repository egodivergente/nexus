"""Windows adapters for the SAME native-operation catalog used by Nexus Android.

Media is processed on the EliteBook, in a persistent serial queue. Original files
are never overwritten. Results carry lineage for the shared Kotlin AssetIndex.
"""
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import math
import mimetypes
import os
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
import urllib.parse
import urllib.request
import uuid
import zipfile

WHISPER_URL = 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin'
WHISPER_SHA = '60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe'
WHISPER_BIN = 'https://github.com/ggml-org/whisper.cpp/releases/download/v1.7.6/whisper-bin-x64.zip'
FLAGS = getattr(subprocess, 'CREATE_NO_WINDOW', 0)
EXTENSIONS = 'png|jpe?g|webp|gif|mp4|mov|webm|mp3|wav|m4a|pdf|txt|md|csv|html|srt|ogg|flac|zip|json|py|js|sh|ps1'


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    with temporary.open('w', encoding='utf-8') as stream:
        json.dump(data, stream, ensure_ascii=False)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def resolve_media(data, uri, media_dir=None):
    identifier = str(uri).removeprefix('/media/')
    if not re.fullmatch(r'[a-f0-9]{32}\.(' + EXTENSIONS + ')', identifier):
        raise ValueError('Selecciona un archivo local de la biblioteca.')
    aliases = data / 'media-aliases.json'
    mapping = json.loads(aliases.read_text('utf-8')) if aliases.exists() else {}
    directory=Path(media_dir) if media_dir is not None else data/'media'
    path = (directory / mapping.get(identifier, identifier)).resolve()
    if not path.is_relative_to(directory.resolve()) or not path.is_file():
        raise ValueError('El archivo local ya no está disponible.')
    return path


class NativeOperations:
    def __init__(self, data, host, read_state, write_state, provider_request, keys):
        self.data, self.host = Path(data), host
        self.read_state, self.write_state = read_state, write_state
        self.provider_request, self.keys = provider_request, keys
        self.media = self.data / 'media'
        self.media.mkdir(parents=True, exist_ok=True)
        self.directory = self.data / 'media-edits'
        self.directory.mkdir(parents=True, exist_ok=True)
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix='nexus-local-media')
        self.lock = threading.RLock()
        self.runtime_lock = threading.Lock()
        self.mcp = {}
        self.model_progress = {'state': 'idle'}
        # A killed process cannot truthfully claim that an in-flight encoder finished.
        # Pending/running records survive and can be retried explicitly; no duplicate auto-enqueue.
        for path in self.directory.glob('*.json'):
            job = json.loads(path.read_text('utf-8'))
            if job.get('state') in {'QUEUED', 'RUNNING'}:
                job.update(state='INTERRUPTED', error='La aplicación se cerró durante la edición. El original se conserva.', updatedAt=int(time.time()*1000))
                atomic_json(path, job)

    def ffmpeg(self):
        executable = shutil.which('ffmpeg')
        if executable:
            return executable
        try:
            import imageio_ffmpeg
            return imageio_ffmpeg.get_ffmpeg_exe()
        except ImportError as error:
            raise ValueError('Falta FFmpeg local. Ejecuta desktop/setup-media.ps1.') from error

    def process(self, arguments, timeout=1800):
        response = subprocess.run(arguments, capture_output=True, timeout=timeout, creationflags=FLAGS)
        if response.returncode:
            details = response.stderr.decode('utf-8', errors='replace')[-3000:]
            raise ValueError('El procesador local no pudo completar la operación. ' + details)
        return response.stdout

    def encode(self, arguments, timeout=1800):
        return self.process([self.ffmpeg(), '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                             '-threads', '2', '-filter_threads', '2', '-filter_complex_threads', '2',
                             *map(str, arguments)], timeout)

    def inspect(self, path):
        ffprobe = shutil.which('ffprobe')
        if ffprobe:
            info = json.loads(self.process([ffprobe, '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(path)], 30))
            duration = float(info.get('format', {}).get('duration', 0))
            streams = info.get('streams', [])
        else:
            result = subprocess.run([self.ffmpeg(), '-hide_banner', '-i', str(path)], capture_output=True, creationflags=FLAGS, timeout=30)
            text = result.stderr.decode('utf-8', errors='replace')
            match = re.search(r'Duration: (\d+):(\d+):(\d+(?:\.\d+)?)', text)
            duration = float(match[1])*3600 + float(match[2])*60 + float(match[3]) if match else 0
            streams = []
            for kind, codec, rest in re.findall(r'Stream #[^\n]*?: (Video|Audio): ([^ ,]+)([^\n]*)', text):
                stream = {'codec_type': kind.lower(), 'codec_name': codec}
                dimensions = re.search(r'\b(\d{2,5})x(\d{2,5})\b', rest)
                if dimensions:
                    stream.update(width=int(dimensions[1]), height=int(dimensions[2]))
                streams.append(stream)
        if not math.isfinite(duration) or duration <= 0:
            raise ValueError('No se pudo leer la duración de este medio.')
        video = next((s for s in streams if s.get('codec_type') == 'video'), {})
        return {'duration_ms': round(duration*1000), 'durationSec': duration, 'width': video.get('width'),
                'height': video.get('height'), 'streams': streams, 'size': path.stat().st_size,
                'has_audio': any(s.get('codec_type') == 'audio' for s in streams)}

    def assets(self, payload):
        if isinstance(payload.get('assets'), list):
            return payload['assets']
        index = self.data / 'assistant' / 'index.json'
        return json.loads(index.read_text('utf-8')).get('assets', []) if index.exists() else []

    def asset(self, identifier, assets):
        result = next((asset for asset in assets if asset['id'] == identifier and not asset.get('libraryDeleted')), None)
        if not result:
            raise ValueError('Archivo no encontrado en el índice. Usa search_assets primero.')
        if result['uri'].startswith('https://'):
            cache_path = self.data / 'remote-media-cache.json'
            with self.lock:
                cache = json.loads(cache_path.read_text('utf-8')) if cache_path.exists() else {}
                cached = cache.get(identifier)
                if cached:
                    return result, resolve_media(self.data, cached)
                extension = mimetypes.guess_extension(result.get('mimeType', '')) or Path(urllib.parse.urlsplit(result['uri']).path).suffix or ('.mp4' if result.get('type') == 'VIDEO' else '.wav')
                if not re.fullmatch(r'\.('+EXTENSIONS+')', extension):
                    raise ValueError('Formato remoto no admitido para edición local.')
                target = self.new_path(extension)
                with urllib.request.urlopen(result['uri'], timeout=120) as response, target.open('wb') as stream:
                    size = 0
                    while block := response.read(1024*1024):
                        size += len(block)
                        if size > 512*1024*1024:
                            raise ValueError('El medio remoto supera 512 MB.')
                        stream.write(block)
                cache[identifier] = '/media/'+target.name
                atomic_json(cache_path, cache)
                return result, target
        return result, resolve_media(self.data, result['uri'])

    def output(self, path, name, parent, kind=None):
        mime = mimetypes.guess_type(path.name)[0] or 'text/plain'
        kind = kind or ('IMAGE' if mime.startswith('image/') else 'VIDEO' if mime.startswith('video/') else 'AUDIO' if mime.startswith('audio/') else 'OTHER')
        result = {'id': 'asset_' + uuid.uuid4().hex, 'uri': '/media/' + path.name, 'type': kind, 'role': 'OUTPUT',
                  'source': 'IMPORTED', 'displayName': name, 'mimeType': mime, 'createdAt': int(time.time()*1000),
                  'version': parent.get('version', 1)+1, 'parentAssetId': parent['id']}
        if parent.get('projectId'):
            result['projectId'] = parent['projectId']
        if kind in {'VIDEO', 'AUDIO'}:
            info = self.inspect(path)
            result.update({key: info[key] for key in ['durationSec', 'width', 'height'] if info.get(key) is not None})
        if kind == 'IMAGE':
            from PIL import Image
            with Image.open(path) as image:
                result.update(width=image.width, height=image.height)
        return result

    def new_path(self, extension):
        return self.media / (uuid.uuid4().hex + extension)

    def queue(self, operation, args, assets):
        identifier = uuid.uuid4().hex
        job = {'edit_job_id': identifier, 'operation': operation, 'input': args, 'state': 'QUEUED',
               'createdAt': int(time.time()*1000), 'updatedAt': int(time.time()*1000), 'results': []}
        atomic_json(self.directory / (identifier+'.json'), job)
        self.executor.submit(self.run_job, job, assets)
        return {'edit_job_id': identifier, 'state': 'QUEUED', 'local': True, 'originals_preserved': True}

    def run_job(self, job, assets):
        path = self.directory / (job['edit_job_id']+'.json')
        job.update(state='RUNNING', updatedAt=int(time.time()*1000))
        atomic_json(path, job)
        try:
            job['results'] = self.edit(job['operation'], job['input'], assets)
            job['state'] = 'COMPLETED'
        except Exception as error:
            job.update(state='FAILED', error=str(error)[:3500])
        finally:
            job['updatedAt'] = int(time.time()*1000)
            atomic_json(path, job)

    def get_job(self, args):
        identifier = str(args['edit_job_id'])
        if not re.fullmatch('[a-f0-9]{32}', identifier):
            raise ValueError('Identificador de edición no válido.')
        path = self.directory / (identifier+'.json')
        deadline = time.monotonic()+max(0, min(20, int(args.get('wait_seconds', 5))))
        while True:
            if not path.exists():
                raise ValueError('Encargo local desconocido.')
            job = json.loads(path.read_text('utf-8'))
            if job['state'] not in {'QUEUED', 'RUNNING'} or time.monotonic() >= deadline:
                return {**job, '_assets': job['results']}
            time.sleep(.25)

    def edit(self, operation, args, assets):
        if operation == 'analyze_audio_asset':
            from native_library import analyze_audio
            return analyze_audio(self, args, assets)
        if operation == 'edit_video_assets':
            identifiers = args.get('asset_ids', [])
            if not 1 <= len(identifiers) <= 12:
                raise ValueError('Selecciona entre 1 y 12 vídeos.')
            selected = [self.asset(identifier, assets) for identifier in identifiers]
            parent, first = selected[0]
            action = args['action']
            if action != 'concat' and len(selected) != 1:
                raise ValueError('Esta operación admite un solo archivo.')
            info = self.inspect(first)
            target = self.new_path('.wav' if action == 'extract_audio' else '.mp4')
            if action == 'trim':
                start, end = int(args.get('start_ms', 0)), int(args['end_ms'])
                if start < 0 or end <= start or end > info['duration_ms']+100:
                    raise ValueError('El intervalo debe estar dentro de la duración real del vídeo.')
                self.encode(['-ss', start/1000, '-i', first, '-t', (end-start)/1000, '-c:v', 'libx264', '-preset', 'veryfast', '-c:a', 'aac', '-movflags', '+faststart', target])
            elif action == 'remove_audio':
                self.encode(['-i', first, '-map', '0:v:0', '-c:v', 'libx264', '-preset', 'veryfast', '-an', '-movflags', '+faststart', target])
            elif action in {'replace_audio', 'mix_audio'}:
                _, audio = self.asset(args['audio_asset_id'], assets)
                self.inspect(audio)
                command = ['-i', first, '-stream_loop', '-1', '-i', audio]
                if action == 'mix_audio' and info['has_audio']:
                    command += ['-filter_complex', '[0:a:0][1:a:0]amix=inputs=2:duration=first:normalize=0[a]', '-map', '0:v:0', '-map', '[a]']
                else:
                    command += ['-map', '0:v:0', '-map', '1:a:0']
                self.encode(command + ['-t', info['durationSec'], '-c:v', 'libx264', '-preset', 'veryfast', '-c:a', 'aac', '-movflags', '+faststart', target])
            elif action == 'extract_audio':
                if not info['has_audio']:
                    raise ValueError('Este vídeo no tiene una pista de audio.')
                self.encode(['-i', first, '-vn', '-ac', '1', '-ar', '16000', '-c:a', 'pcm_s16le', target])
            elif action == 'concat':
                width, height = info.get('width', 0), info.get('height', 0)
                if not width or not height:
                    raise ValueError('La unión requiere vídeos con dimensiones legibles.')
                width, height = width//2*2, height//2*2
                command, filters, pairs = [], [], []
                for number, (_, source) in enumerate(selected):
                    metadata = self.inspect(source)
                    command += ['-i', source]
                    filters.append(f'[{number}:v:0]scale={width}:{height}:force_original_aspect_ratio=decrease,pad={width}:{height}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,setpts=PTS-STARTPTS[v{number}]')
                    if metadata['has_audio']:
                        filters.append(f'[{number}:a:0]aresample=48000,aformat=channel_layouts=stereo,asetpts=PTS-STARTPTS[a{number}]')
                    else:
                        filters.append(f'anullsrc=r=48000:cl=stereo,atrim=duration={metadata["durationSec"]},asetpts=PTS-STARTPTS[a{number}]')
                    pairs.append(f'[v{number}][a{number}]')
                filters.append(''.join(pairs)+f'concat=n={len(selected)}:v=1:a=1[v][a]')
                self.encode(command+['-filter_complex', ';'.join(filters), '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-preset', 'veryfast', '-c:a', 'aac', '-movflags', '+faststart', target])
            else:
                raise ValueError('Operación de edición desconocida.')
            return [self.output(target, f'{parent["displayName"]} · {action}', parent)]
        parent, source = self.asset(args['asset_id'], assets)
        info = self.inspect(source)
        if operation == 'extract_video_frames':
            timestamps = args['timestamps_ms']
            maximum = max(320, min(3840, int(args.get('max_dimension', 1920))))
            if not 1 <= len(timestamps) <= 12 or any(not isinstance(value, int) or value < 0 or value >= info['duration_ms'] for value in timestamps):
                raise ValueError('Selecciona entre 1 y 12 tiempos dentro del vídeo.')
            output = []
            for timestamp in timestamps:
                target = self.new_path('.jpg')
                self.encode(['-ss', timestamp/1000, '-i', source, '-frames:v', '1', '-vf', f'scale=w=min({maximum}\\,iw):h=min({maximum}\\,ih):force_original_aspect_ratio=decrease', '-q:v', '2', target])
                output.append(self.output(target, f'{parent["displayName"]} · {timestamp/1000:.2f} s', parent, 'IMAGE'))
            return output
        if operation == 'transcribe_media_asset':
            status = self.whisper_status()
            if not status['ready']:
                raise ValueError('Descarga Whisper local desde Ajustes → Edición y Whisper.')
            start = int(args.get('start_ms', 0))
            end = int(args.get('end_ms', info['duration_ms']))
            if start < 0 or end <= start or end > info['duration_ms']+100 or end-start > 900000:
                raise ValueError('La transcripción admite un intervalo válido de hasta 15 minutos.')
            if not info['has_audio']:
                raise ValueError('Este medio no tiene audio para transcribir.')
            wav = self.directory / (uuid.uuid4().hex+'.wav')
            prefix = self.media / uuid.uuid4().hex
            try:
                self.encode(['-ss', start/1000, '-i', source, '-t', (end-start)/1000, '-vn', '-ar', '16000', '-ac', '1', '-c:a', 'pcm_s16le', wav])
                self.process([status['executable'], '-m', status['model'], '-f', str(wav), '-t', '2', '-l', 'auto', '-otxt', '-osrt', '-of', str(prefix)], 3600)
            finally:
                wav.unlink(missing_ok=True)
            result = [self.output(prefix.with_suffix(ext), parent['displayName']+' · transcripción'+ext, parent, 'OTHER') for ext in ['.txt', '.srt']]
            # Whisper starts subtitles at the extracted interval's zero; preserve the source offset in metadata.
            for asset in result:
                asset['tags'] = ['whisper-local', f'source_start_ms:{start}']
            return result
        raise ValueError('Operación local desconocida.')

    def whisper_status(self):
        model = self.data / 'whisper' / 'ggml-base.bin'
        binaries = list((self.data / 'whisper' / 'runtime').rglob('whisper-cli.exe'))
        binaries += list((self.data / 'whisper' / 'runtime').rglob('main.exe'))
        executable = next(iter(binaries), None)
        stamp = model.with_suffix('.verified.json')
        verified = False
        if model.is_file() and stamp.is_file():
            meta = json.loads(stamp.read_text('utf-8'))
            verified = meta.get('sha256') == WHISPER_SHA and meta.get('size') == model.stat().st_size and meta.get('mtime_ns') == model.stat().st_mtime_ns
        return {'ready': bool(verified and executable), 'model_downloaded': verified, 'model': str(model),
                'executable': str(executable) if executable else None, 'model_name': 'Whisper Base multilingüe',
                'threads': 2, 'local': True, 'progress': self.model_progress}

    def prepare_whisper(self):
        if not self.runtime_lock.acquire(blocking=False):
            return {'state': 'DOWNLOADING', 'progress': self.model_progress}
        def download():
            try:
                folder = self.data / 'whisper'
                folder.mkdir(parents=True, exist_ok=True)
                model = folder / 'ggml-base.bin'
                if not self.whisper_status()['model_downloaded']:
                    self.model_progress = {'state': 'downloading_model', 'bytes': 0}
                    temporary = model.with_suffix('.download')
                    with urllib.request.urlopen(WHISPER_URL, timeout=120) as response, temporary.open('wb') as stream:
                        while block := response.read(1024*1024):
                            stream.write(block)
                            self.model_progress['bytes'] += len(block)
                    digest = hashlib.file_digest(temporary.open('rb'), 'sha256').hexdigest()
                    if digest != WHISPER_SHA:
                        raise ValueError('El modelo descargado no coincide con la huella publicada.')
                    os.replace(temporary, model)
                    atomic_json(model.with_suffix('.verified.json'), {'sha256': digest, 'size': model.stat().st_size, 'mtime_ns': model.stat().st_mtime_ns})
                if not self.whisper_status()['executable']:
                    self.model_progress = {'state': 'downloading_runtime'}
                    archive = folder / 'whisper-bin-x64.zip'
                    with urllib.request.urlopen(WHISPER_BIN, timeout=120) as response, archive.open('wb') as stream:
                        shutil.copyfileobj(response, stream)
                    runtime = folder / 'runtime'
                    runtime.mkdir(exist_ok=True)
                    with zipfile.ZipFile(archive) as files:
                        for item in files.infolist():
                            target = (runtime / item.filename).resolve()
                            if not target.is_relative_to(runtime.resolve()):
                                raise ValueError('Archivo de runtime no válido.')
                        files.extractall(runtime)
                self.model_progress = {'state': 'ready'}
            except Exception as error:
                self.model_progress = {'state': 'failed', 'error': str(error)}
            finally:
                self.runtime_lock.release()
        threading.Thread(target=download, daemon=True, name='nexus-whisper-download').start()
        return {'state': 'DOWNLOADING', 'local': True, 'model_mb': 148}

    def attachment(self, operation, args):
        path = resolve_media(self.data, args['uri'])
        if operation == 'thumbnail':
            from PIL import Image, ImageOps
            with Image.open(path) as original:
                image = ImageOps.exif_transpose(original).convert('RGB')
                image.thumbnail((1280, 1280))
                output = io.BytesIO()
                image.save(output, format='JPEG', quality=85)
                return {'format': 'jpeg', 'bytes': base64.b64encode(output.getvalue()).decode()}
        if operation == 'document':
            formats = {'pdf': 'pdf', 'txt': 'txt', 'md': 'md', 'csv': 'csv', 'html': 'html'}
            format_name = formats.get(path.suffix[1:])
            if not format_name or path.stat().st_size > 4_500_000:
                raise ValueError('El documento debe ser PDF, TXT, Markdown, CSV o HTML y tener menos de 4,5 MB.')
            payload = path.read_bytes()
            if format_name == 'pdf':
                from document_reader import extract_pdf
                text = extract_pdf(path)
                if text is not None:
                    format_name, payload = 'txt', text.encode('utf-8')
            return {'format': format_name, 'name': re.sub(r'[^\w -]', ' ', args.get('name', 'Documento'))[:100], 'bytes': base64.b64encode(payload).decode()}
        if operation == 'video':
            info = self.inspect(path)
            frames = []
            for number in range(6):
                timestamp = int(number * max(0, info['duration_ms']-200)/5)
                content = self.encode(['-ss', timestamp/1000, '-i', path, '-frames:v', '1', '-vf', 'scale=w=min(1024\\,iw):h=min(1024\\,ih):force_original_aspect_ratio=decrease', '-q:v', '3', '-f', 'image2pipe', '-vcodec', 'mjpeg', 'pipe:1'], 45)
                if not content:
                    raise ValueError('No se pudieron extraer todos los fotogramas del vídeo.')
                frames.append({'timestamp_ms': timestamp, 'format': 'jpeg', 'bytes': base64.b64encode(content).decode()})
            return {'frames': frames, 'sampled': True, 'audio': False, 'duration_ms': info['duration_ms']}
        raise ValueError('Adjunto no admitido.')

    def run(self, payload):
        operation, args = payload['operation'], payload.get('input', {})
        assets = self.assets(payload)
        from native_library import OPERATIONS, run as run_library
        if operation in OPERATIONS:
            return run_library(self, operation, args, assets, payload)
        if operation.startswith('corte_'):
            return self.host.corte.agent_run(operation, payload)
        if operation in {'web_search', 'read_web_page', 'list_mcp_servers', 'connect_mcp_server', 'list_mcp_tools', 'call_mcp_tool', 'disconnect_mcp_server'}:
            from native_internet import run
            return run(self, operation, args)
        if operation == 'get_nexus_settings':
            state = self.read_state() or {}
            return {**self.host.public_settings(), 'theme': state.get('interfaceTheme', 'aurora'), 'remember_parameters': state.get('rememberParams', True)}
        if operation == 'update_nexus_settings':
            models = {'sol': 'openai.gpt-5.6-sol', 'sonnet-5.5': 'anthropic.claude-sonnet-5-5', 'opus-5.5': 'anthropic.claude-opus-5-5', 'sonnet-4.6': 'anthropic.claude-sonnet-4-6', 'opus-4.6': 'anthropic.claude-opus-4-6', 'haiku-4.5': 'anthropic.claude-haiku-4-5-20251001-v1:0'}
            updates = {target: args[source] for source, target in [('max_tokens', 'maxTokens'), ('send_images', 'sendImages'), ('excluded_families', 'excludedFamilies')] if source in args}
            if 'agent_model' in args:
                if args['agent_model'] not in models:
                    raise ValueError('Modelo de agente desconocido.')
                updates['modelId'] = models[args['agent_model']]
            self.host.save_settings(updates)
            if 'theme' in args or 'remember_parameters' in args:
                state = self.read_state() or {}
                if 'theme' in args:
                    if args['theme'] not in ['AURORA', 'OLED', 'QUARTZ', 'OCEAN', 'FOREST', 'LIGHT']:
                        raise ValueError('Tema desconocido.')
                    state['interfaceTheme'] = args['theme'].lower()
                if 'remember_parameters' in args:
                    state['rememberParams'] = bool(args['remember_parameters'])
                self.write_state(state)
            return {'updated': True, 'next_turn': True, **self.run({'operation': 'get_nexus_settings'})}
        if operation == 'atlas_get_balance':
            return self.provider_request(self.keys(), 'atlas_cloud', '/public/v1/balance', 'GET', None)
        if operation == 'get_active_generations':
            jobs = (self.read_state() or {}).get('jobs', [])
            return {'jobs': [j for j in jobs if j.get('status') not in {'COMPLETED', 'FAILED', 'CANCELLED'} and (not args.get('provider_id') or j.get('providerId') == args['provider_id'])][:max(1, min(50, int(args.get('limit', 50))))]}
        if operation in {'diagnose_flow', 'repair_flow'}:
            import flow_connection, flow_provider
            response = {'connection': flow_connection.snapshot(refresh=operation == 'repair_flow', interactive=False), 'queue': flow_provider.automation_overview(self.data)}
            response['submitted'] = False
            return response
        if operation == 'recover_generation':
            # Routed to the existing UI/provider poller, which knows every provider's actual job contract.
            return self.host.browser_callback('recoverGeneration', args)
        if operation == 'inspect_media_asset':
            asset, path = self.asset(args['asset_id'], assets)
            return {'asset_id': asset['id'], **self.inspect(path)}
        if operation == 'get_whisper_status':
            return self.whisper_status()
        if operation == 'prepare_whisper_model':
            return self.prepare_whisper()
        if operation in {'edit_video_assets', 'extract_video_frames', 'transcribe_media_asset'}:
            # Resolve before enqueue, then operate only on this immutable asset snapshot.
            for identifier in args.get('asset_ids', [args.get('asset_id')]):
                self.asset(identifier, assets)
            return self.queue(operation, args, assets)
        if operation == 'get_media_edit_job':
            return self.get_job(args)
        if operation == 'rename_asset_file':
            asset, path = self.asset(args['asset_id'], assets)
            name = str(args['file_name']).strip()
            if not name or name != Path(name).name or re.search(r'[<>:"/\\|?*\x00-\x1f]', name) or name.endswith(('.', ' ')) or Path(name).suffix.lower() != path.suffix.lower():
                raise ValueError('Introduce un nombre de archivo válido conservando su extensión.')
            target = self.media / name
            if target.exists() and target != path:
                raise ValueError('Ya existe un archivo con ese nombre.')
            aliases_path = self.data / 'media-aliases.json'
            cache_path = self.data / 'remote-media-cache.json'
            with self.lock:
                aliases = json.loads(aliases_path.read_text('utf-8')) if aliases_path.exists() else {}
                cache = json.loads(cache_path.read_text('utf-8')) if cache_path.exists() else {}
                local_uri = cache.get(asset['id'], asset['uri']) if str(asset.get('uri', '')).startswith('https://') else asset['uri']
                old_identifier = local_uri.split('/')[-1]
                if path != target:
                    path.rename(target)
                aliases[old_identifier] = name
                for key, value in list(aliases.items()):
                    if value == path.name:
                        aliases[key] = name
                try:
                    atomic_json(aliases_path, aliases)
                except Exception:
                    if target.exists() and target != path:
                        target.rename(path)
                    raise
            result = {**asset, 'displayName': name, 'uri': local_uri}
            return {'renamed': True, 'uri': local_uri, 'file_name': name, '_assets': [result]}
        if operation == 'convert_image_asset':
            from PIL import Image, ImageOps
            parent, path = self.asset(args['asset_id'], assets)
            format_name = args['format']
            if format_name not in {'png', 'jpeg', 'webp'}:
                raise ValueError('Formato de imagen desconocido.')
            target = self.new_path('.jpg' if format_name == 'jpeg' else '.'+format_name)
            with Image.open(path) as source:
                image = ImageOps.exif_transpose(source).convert('RGB' if format_name == 'jpeg' else 'RGBA')
                if args.get('aspect_ratio'):
                    width, height = [int(v) for v in args['aspect_ratio'].split(':')]
                    ratio = width/height
                    image = ImageOps.fit(image, (min(image.width, round(image.height*ratio)), min(image.height, round(image.width/ratio))))
                maximum = max(64, min(4096, int(args.get('max_dimension', 4096))))
                image.thumbnail((maximum, maximum))
                image.save(target, format=format_name.upper(), quality=max(1, min(100, int(args.get('quality', 90)))))
            result = self.output(target, parent['displayName']+' · '+format_name, parent, 'IMAGE')
            return {'asset': result, '_assets': [result], 'original_preserved': True}
        raise ValueError('Operación nativa desconocida: '+operation)
