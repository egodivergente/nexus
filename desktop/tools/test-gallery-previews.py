"""Verify gallery thumbnails with copies of real, existing local library media.

Reads the specified profile without changing it. Only selected source bytes enter
a disposable offline profile; provider calls and external browser requests fail.
Reports contain file hashes/counts, never provider URLs or profile credentials.
"""
import argparse
from collections import Counter
from email.message import Message
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import urllib.error
from urllib.parse import urlsplit
import uuid
from unittest.mock import Mock, patch

from PIL import Image
from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read(path, default):
    return json.loads(path.read_text('utf-8-sig')) if path.is_file() else default


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--desktop', type=Path, default=ROOT / 'desktop')
    parser.add_argument('--profile', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop')
    parser.add_argument('--output', type=Path, default=ROOT / 'outputs/gallery-previews')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    state = read(args.profile / 'state.json', {})
    before_state_hash = sha(args.profile / 'state.json')
    jobs = state.get('jobs', [])
    library = [j for j in jobs if (j.get('outputs') or j.get('status') not in {'COMPLETED', 'FAILED', 'CANCELLED'} or j.get('uncertain') or j.get('parameters', {}).get('__tracking_paused') == 'true') and j['id'] not in state.get('studioGallery', {}).get('hidden', [])]
    outputs = [o if isinstance(o, str) else o.get('url', '') for j in jobs for o in j.get('outputs', [])]
    summary = {'jobs': len(jobs), 'library': len(library), 'libraryModalities': dict(Counter(j.get('modality') for j in library)),
               'outputs': len(outputs), 'localOutputs': sum(u.startswith('/media/') for u in outputs),
               'remoteOutputs': sum(u.startswith('https://') for u in outputs)}
    sources = {}
    for modality in ('IMAGE', 'VIDEO'):
        for job in jobs:
            if job.get('modality') != modality:
                continue
            for output in job.get('outputs', []):
                uri = output if isinstance(output, str) else output.get('url', '')
                if not uri.startswith('/media/'):
                    continue
                candidate = args.profile / 'media' / uri[7:]
                if candidate.is_file() and candidate.stat().st_size >= (32000 if modality == 'IMAGE' else 1000000):
                    sources[modality] = candidate
                    break
            if modality in sources:
                break
    if set(sources) != {'IMAGE', 'VIDEO'}:
        raise RuntimeError('Se necesita una imagen real y un vídeo real ya existentes en la biblioteca local.')
    original_hashes = {kind: sha(path) for kind, path in sources.items()}
    checks, errors, remote, denied, requests, responses = [], [], [], [], [], {}

    def check(name, passed, detail=None):
        checks.append({'name': name, 'passed': bool(passed), **({'detail': detail} if detail is not None else {})})
        print(('PASS ' if passed else 'FAIL ') + name, flush=True)

    with tempfile.TemporaryDirectory(prefix='nexus-real-gallery-') as folder:
        profile = Path(folder)
        (profile / 'media').mkdir()
        (profile / 'assistant').mkdir()
        local = {}
        for kind, path in sources.items():
            copy = profile / 'media' / (uuid.uuid4().hex + path.suffix.lower())
            shutil.copyfile(path, copy)
            local[kind] = '/media/' + copy.name
        missing = '/media/' + 'e' * 32 + '.png'
        sample_jobs = []

        def job(identifier, kind, outputs):
            value = {'id': identifier, 'name': identifier, 'modality': kind, 'providerId': 'local_media',
                     'modelId': 'local/reference', 'prompt': 'QA con una copia del original local',
                     'status': 'COMPLETED', 'startedAt': 1, 'outputs': outputs, 'parameters': {}}
            sample_jobs.append(value)
            return value

        job('real-image', 'IMAGE', [local['IMAGE']])
        job('real-video', 'VIDEO', [local['VIDEO']])
        job('cached-image', 'IMAGE', ['https://expired.invalid/image.png?Expires=1'])
        job('cached-video', 'VIDEO', ['https://expired.invalid/video.mp4?Expires=1'])
        job('alternative-image', 'IMAGE', ['https://expired.invalid/first.png?Expires=1', local['IMAGE']])
        job('missing-image', 'IMAGE', [missing])
        assets, generations, cache = [], [], {}
        for kind in ('IMAGE', 'VIDEO'):
            identifier = 'cached-' + kind.lower()
            primary, duplicate = identifier + '-asset', identifier + '-copy'
            absent_source = '/media/' + uuid.uuid4().hex + ('.jpeg' if kind == 'IMAGE' else '.mp4')
            assets.extend([{'id': primary, 'uri': absent_source, 'type': kind, 'role': 'OUTPUT', 'source': 'GENERATED', 'displayName': identifier, 'createdAt': 1},
                           {'id': duplicate, 'uri': local[kind], 'type': kind, 'role': 'OUTPUT', 'source': 'GENERATED', 'displayName': identifier, 'createdAt': 1}])
            cache[primary] = local[kind]
            generations.append({'id': identifier + '-generation', 'nexusJobId': identifier,
                                'providerId': 'local_media', 'modelId': 'local/reference', 'modality': kind,
                                'prompt': 'QA local', 'status': 'COMPLETED', 'createdAt': 1,
                                'outputAssetIds': [primary, duplicate]})
        test_state = {'jobs': sample_jobs, 'favorites': [], 'hidden': [], 'draft': {'modelId': '', 'prompt': '', 'values': {}, 'media': []}}
        (profile / 'state.json').write_text(json.dumps(test_state), 'utf-8')
        (profile / 'assistant/index.json').write_text(json.dumps({'schemaVersion': 1, 'assets': assets, 'generations': generations}), 'utf-8')
        (profile / 'remote-media-cache.json').write_text(json.dumps(cache), 'utf-8')
        # Exercise resource and recovery boundaries independently of the browser.
        # Network responses are simulated; local frames come from the real video.
        sys.path.insert(0, str(args.desktop))
        import gallery_previews as preview_module
        import imageio_ffmpeg
        recovery = Mock(return_value=[])
        previews = preview_module.GalleryPreviews(profile, lambda: test_state,
                                                  imageio_ffmpeg.get_ffmpeg_exe,
                                                  recover_outputs=recovery, allow_remote=False)

        def unavailable(operation):
            try:
                operation()
            except preview_module.PreviewUnavailable:
                return True
            return False

        with patch.object(preview_module.urllib.request, 'urlopen', side_effect=AssertionError('Offline preview attempted network')) as network:
            check('Offline missing original never downloads or recovers a provider job', unavailable(lambda: previews.thumbnail('alternative-image', 0)) and network.call_count == 0 and recovery.call_count == 0)
            check('Repeated offline failure leaves original metadata unchanged', unavailable(lambda: previews.thumbnail('alternative-image', 0)) and read(profile / 'state.json', {}) == test_state)
        first_frame = previews.thumbnail('real-video')
        cached_hash, cached_time = sha(first_frame), first_frame.stat().st_mtime_ns
        with patch.object(preview_module.subprocess, 'run', side_effect=AssertionError('Cached video decoded again')):
            again = previews.thumbnail('real-video')
        check('Cached video thumbnail avoids another decoder process', again == first_frame and sha(again) == cached_hash and again.stat().st_mtime_ns == cached_time)
        check('Missing local image fails without changing its job', unavailable(lambda: previews.thumbnail('missing-image')))
        restored = profile / 'media' / missing[7:]
        shutil.copyfile(sources['IMAGE'], restored)
        check('A restored local original recovers immediately after a cached failure', previews.thumbnail('missing-image').is_file())
        restored.unlink()

        class DownloadResponse(io.BytesIO):
            def __init__(self, data, mime, declared=None):
                super().__init__(data)
                self.headers = Message()
                self.headers['Content-Type'] = mime
                if declared is not None:
                    self.headers['Content-Length'] = str(declared)

        with patch.object(preview_module.urllib.request, 'urlopen', return_value=DownloadResponse(b'<html>not media</html>', 'text/html')):
            check('A non-media response is rejected without caching it', unavailable(lambda: previews._download('https://fixture.invalid/output', 'bad-mime', 'IMAGE')))
        with patch.object(preview_module.urllib.request, 'urlopen', return_value=DownloadResponse(b'', 'video/mp4', preview_module.MAX_REMOTE_BYTES + 1)):
            check('An oversized declared original is rejected before reading its body', unavailable(lambda: previews._download('https://fixture.invalid/output', 'too-large', 'VIDEO')))
        with patch.object(preview_module, 'MAX_REMOTE_BYTES', 1024), patch.object(preview_module.urllib.request, 'urlopen', return_value=DownloadResponse(b'x' * 2048, 'video/mp4')):
            check('An unbounded stream stops when the byte limit is crossed', unavailable(lambda: previews._download('https://fixture.invalid/output', 'stream-too-large', 'VIDEO')))
        with patch.object(preview_module.time, 'monotonic', side_effect=[100.0, 121.0]), patch.object(preview_module.urllib.request, 'urlopen', return_value=DownloadResponse(b'late body', 'image/png')):
            check('A slow download stops after its total time budget', unavailable(lambda: previews._download('https://fixture.invalid/output', 'too-slow', 'IMAGE')))
        check('Failed downloads leave no partial or mislabeled original files', not any(previews.originals.iterdir()))
        with patch.object(preview_module.urllib.request, 'urlopen', side_effect=AssertionError('Unsafe URL reached transport')) as network:
            check('Local file URLs cannot be passed to the download transport', unavailable(lambda: previews._download('file:///C:/Windows/win.ini', 'unsafe', 'IMAGE')) and network.call_count == 0)
        job('malformed-cache', 'IMAGE', ['https://fixture.invalid/malformed.bin'])
        downloaded = preview_module.GalleryPreviews(profile, lambda: test_state,
                                                    imageio_ffmpeg.get_ffmpeg_exe,
                                                    recover_outputs=recovery, allow_remote=True)
        with patch.object(preview_module.urllib.request, 'urlopen', return_value=DownloadResponse(b'not a decodable image', 'application/octet-stream')):
            check('A malformed binary media body fails without generation recovery', unavailable(lambda: downloaded.thumbnail('malformed-cache')) and recovery.call_count == 0)
        check('A failed decoded download is evicted from the owned cache', not any(downloaded.originals.iterdir()))
        corrupt_local = profile / 'media' / (uuid.uuid4().hex + '.png')
        corrupt_local.write_bytes(b'local invalid image bytes must remain intact')
        corrupt_hash = sha(corrupt_local)
        job('malformed-local', 'IMAGE', ['/media/' + corrupt_local.name])
        check('A local decode failure preserves that original and the good real image', unavailable(lambda: downloaded.thumbnail('malformed-local')) and corrupt_local.is_file() and sha(corrupt_local) == corrupt_hash and sha(profile / 'media' / local['IMAGE'][7:]) == original_hashes['IMAGE'])
        corrupt_local.unlink()
        sample_jobs[:] = [row for row in sample_jobs if row['id'] not in {'malformed-cache', 'malformed-local'}]
        with (args.output / 'host.log').open('w', encoding='utf-8') as log:
            host = subprocess.Popen([sys.executable, '-u', str(args.desktop / 'local_host.py'), '--headless', '--offline-qa', '--data-dir', str(profile)],
                                    stdout=subprocess.PIPE, stderr=log, text=True,
                                    env={**os.environ, 'PYTHONDONTWRITEBYTECODE': '1'},
                                    creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            try:
                url = json.loads(host.stdout.readline())['url']

                def status(path, headers=None):
                    try:
                        with urllib.request.urlopen(urllib.request.Request(url + path, headers=headers or {}), timeout=15) as answer:
                            return answer.status
                    except urllib.error.HTTPError as error:
                        return error.code

                check('A cross-site thumbnail request is denied', status('gallery-preview/real-image?index=0', {'Sec-Fetch-Site': 'cross-site'}) == 403)
                rejected = ['gallery-preview/not-a-registered-job?index=0', 'gallery-preview/%2e%2e%2fcredentials.dpapi?index=0', 'gallery-preview/real-image?index=-1', 'gallery-preview/real-image?index=10000', 'gallery-preview/real-image?index=bad']
                check('Unregistered jobs, traversal paths and invalid output indices are rejected', all(status(path) == 404 for path in rejected))
                with sync_playwright() as pw:
                    browser = pw.chromium.launch(channel='msedge', headless=True)
                    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    pending, peak = set(), [0]

                    def thumbnail(request):
                        p = urlsplit(request.url)
                        return p.path.startswith(('/gallery-preview/', '/library-preview/')) and 'original=1' not in p.query

                    def requested(request):
                        p = urlsplit(request.url)
                        requests.append(p.path + ('?' + p.query if p.query else ''))
                        if thumbnail(request):
                            pending.add(request.url)
                            peak[0] = max(peak[0], len(pending))

                    def finished(request):
                        pending.discard(request.url)

                    def response_received(response):
                        if urlsplit(response.url).path.startswith(('/gallery-preview/', '/library-preview/')):
                            responses[urlsplit(response.url).path + '?' + urlsplit(response.url).query] = response.status

                    def guard(route):
                        p = urlsplit(route.request.url)
                        if p.netloc != urlsplit(url).netloc:
                            remote.append(p.hostname)
                            return route.abort()
                        if p.path == '/rpc':
                            message = route.request.post_data_json
                            op, sub = message.get('op'), (message.get('args') or {}).get('op')
                            if op in {'request', 'generate', 'upload', 'flowSubmit', 'flowConnect', 'flowQuote', 'nativeOperation'} or (op == 'agent' and sub in {'send', 'confirm', 'apply'}):
                                denied.append(op)
                                return route.fulfill(json={'id': message['id'], 'ok': False, 'error': 'Bloqueado por la prueba sin conexión.'})
                        route.continue_()

                    page.route('**/*', guard)
                    page.on('request', requested)
                    page.on('requestfinished', finished)
                    page.on('requestfailed', finished)
                    page.on('response', response_received)
                    page.goto(url)
                    page.wait_for_function('()=>!!initial&&!!window.NexusGallery')
                    page.evaluate("()=>{document.querySelector('#nx-startup-onboarding')?.remove();Studio.tab='results';navigate('library');}")
                    page.evaluate('async()=>{await NexusGallery.refresh();}')

                    def canonical(identifier, index=None):
                        ordinal = (1 if identifier == 'alternative-image' else 0) if index is None else index
                        value = page.evaluate("({id,index})=>NexusGallery.items.find(row=>row.identityKeys.includes('job:'+id+':'+index))?.id", {'id':identifier,'index':ordinal})
                        if not value:
                            raise AssertionError('Missing normalized source identity: ' + identifier)
                        return value

                    def card(identifier, index=None):
                        return page.locator('[data-job="' + canonical(identifier,index) + '"]')

                    page.mouse.move(0, 0)
                    good = ['real-image', 'real-video', 'cached-image', 'cached-video', 'alternative-image']
                    page.wait_for_function("ids=>ids.every(id=>{const index=id==='alternative-image'?1:0,row=NexusGallery.items.find(e=>e.identityKeys.includes('job:'+id+':'+index)),n=row&&document.querySelector('[data-job=\"'+row.id+'\"] .gallery-media');const i=n?.querySelector('.gallery-thumbnail');return n?.dataset.previewState==='ready'&&i?.complete&&i.naturalWidth>10&&Number(getComputedStyle(i).opacity)>=.99;})", arg=good, timeout=60000)
                    for identifier in good:
                        image = card(identifier).locator('.gallery-thumbnail')
                        check(identifier + ': real thumbnail visible before hover', image.is_visible() and image.evaluate('(n)=>n.complete&&n.naturalWidth>10&&getComputedStyle(n).opacity===\'1\''))
                    check('Identical media aliases deduplicate without losing provenance', len({canonical(identifier) for identifier in good}) == 2)
                    card('missing-image').locator('.gallery-media[data-preview-state=failed]').wait_for()
                    check('Missing image has an honest fallback and keeps its image node', card('missing-image').locator('.gallery-thumbnail').count() == 1 and card('missing-image').locator('.media-unavailable').count() == 0)
                    check('All video originals stay unloaded before hover', page.locator('.gallery-hover-video[src]').count() == 0)
                    check('No original request before user interaction', not any('original=1' in request or request.split('?')[0].endswith('.mp4') for request in requests))
                    check('Thumbnail queue never exceeds two requests', peak[0] <= 2, peak[0])
                    page.screenshot(path=str(args.output / 'real-gallery-before-hover.png'))

                    for identifier in ('real-image', 'real-video', 'cached-image', 'cached-video'):
                        request_url = url + 'gallery-preview/' + identifier + '?index=0'
                        with urllib.request.urlopen(request_url, timeout=45) as answer:
                            data = answer.read()
                        image = Image.open(io.BytesIO(data))
                        check(identifier + ': HTTP JPEG contains real image detail', image.format == 'JPEG' and min(image.size) > 10 and image.entropy() > 1, {'dimensions': image.size, 'bytes': len(data), 'entropy': round(image.entropy(), 3)})
                    for identifier, kind in [('cached-image', 'IMAGE'), ('cached-video', 'VIDEO')]:
                        original_url = url + 'gallery-preview/' + identifier + '?index=0&original=1'
                        request = urllib.request.Request(original_url, headers={'Range': 'bytes=0-1023'})
                        with urllib.request.urlopen(request, timeout=30) as answer:
                            body = answer.read()
                            check(identifier + ': original is local and supports HTTP Range', answer.status == 206 and len(body) == 1024 and answer.headers.get('Content-Range', '').startswith('bytes 0-1023/'))
                        with urllib.request.urlopen(original_url, timeout=30) as answer:
                            recovered_hash = hashlib.sha256(answer.read()).hexdigest()
                        check(identifier + ': recovered original preserves exact source bytes', recovered_hash == original_hashes[kind])

                    card('real-video').locator('.gallery-media').hover()
                    page.wait_for_function("id=>{const v=document.querySelector('[data-job=\"'+id+'\"] video');return v?.videoWidth>10&&!v.paused&&v.readyState>=2;}", arg=canonical('real-video'), timeout=30000)
                    check('Hover plays a real video with decoded frames', page.locator('.gallery-piece.is-previewing').count() == 1)
                    page.mouse.move(0, 0)
                    page.wait_for_function("()=>document.querySelectorAll('.gallery-hover-video[src]').length===0")
                    check('Leaving the video releases its original source', page.locator('.gallery-hover-video[src]').count() == 0)
                    card('real-video').locator('.gallery-media').click()
                    page.wait_for_function("()=>{const v=document.querySelector('.gallery-lightbox video[data-gallery-preview=original]');return v?.videoWidth>10&&v.readyState>=1;}")
                    page.evaluate("window.__qaVideoViewer=document.querySelector('.gallery-lightbox video')")
                    check('Video viewer decodes the real original', page.locator('.gallery-lightbox video').count() == 1)
                    page.screenshot(path=str(args.output / 'real-gallery-video-viewer.png'))
                    page.keyboard.press('Escape')
                    check('Closing the video viewer releases its decoder source', page.evaluate("!window.__qaVideoViewer.isConnected&&!window.__qaVideoViewer.getAttribute('src')&&window.__qaVideoViewer.paused"))
                    card('alternative-image').locator('.gallery-media').click()
                    page.wait_for_function("()=>{const n=document.querySelector('.gallery-lightbox img[data-gallery-preview=original]');return n?.complete&&n.naturalWidth>10;}")
                    source_url = page.locator('.gallery-lightbox [data-gallery-preview=original]').get_attribute('src')
                    with urllib.request.urlopen(url + source_url.lstrip('/'), timeout=15) as answer:
                        alternative_hash = hashlib.sha256(answer.read()).hexdigest()
                    check('Viewer opens the full valid alternative output', alternative_hash == original_hashes['IMAGE'])
                    page.screenshot(path=str(args.output / 'real-gallery-alternative-viewer.png'))
                    page.keyboard.press('Escape')
                    card('cached-image').locator('.gallery-media').click()
                    page.wait_for_function("()=>{const i=document.querySelector('.gallery-lightbox img[data-gallery-preview=original]');return i?.complete&&i.naturalWidth>10;}")
                    with page.expect_download(timeout=15000) as download_event:
                        page.locator('.gallery-lightbox [data-gallery=download-original]').click()
                    download = download_event.value
                    check('Recovered image download contains the full original bytes', sha(Path(download.path())) == original_hashes['IMAGE'] and Path(download.path()).stat().st_size == sources['IMAGE'].stat().st_size)
                    with urllib.request.urlopen(url + 'gallery-preview/cached-image?index=0&original=1&download=1', timeout=15) as answer:
                        check('Recovered download is served as an attachment', answer.headers.get('Content-Disposition', '').startswith('attachment'))
                    page.keyboard.press('Escape')
                    card('missing-image').locator('.gallery-media').click()
                    page.wait_for_selector('.gallery-original-error')
                    check('Unavailable original offers a retry without deleting the job', page.locator('.gallery-original-error [data-gallery=retry-preview]').is_visible() and page.evaluate("state.jobs.some(j=>j.id==='missing-image')"))
                    check('Unavailable original cannot trigger a misleading download', page.locator('.gallery-lightbox [data-gallery=download-original]').is_disabled())
                    page.keyboard.press('Escape')
                    check('No JavaScript errors', not errors, errors)
                    check('No remote browser request', not remote, sorted(set(remote)))
                    check('No provider or paid operation', not denied, denied)
                    browser.close()
            finally:
                host.terminate()
                host.wait(timeout=15)
    check('Original image and video remain byte-identical', all(sha(path) == original_hashes[kind] for kind, path in sources.items()))
    # A different live window may legitimately update its state during the test;
    # this script never writes the live profile, even if that hash changes.
    report = {'status': 'PASS' if all(c['passed'] for c in checks) else 'FAIL', 'checks': checks,
              'liveProfile': summary, 'sourceMedia': {kind: {'file': path.name, 'bytes': path.stat().st_size, 'sha256': original_hashes[kind]} for kind, path in sources.items()},
              'profileStateHashBefore': before_state_hash, 'profileStateHashAfter': sha(args.profile / 'state.json'),
              'javascriptErrors': errors, 'remoteHosts': sorted(set(remote)), 'deniedOperations': denied,
              'thumbnailResponses': responses, 'profileMode': 'read-only source; disposable offline copies'}
    (args.output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), 'utf-8')
    print(json.dumps({'status': report['status'], 'checks': len(checks), 'liveProfile': summary, 'output': str(args.output)}, ensure_ascii=False))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
