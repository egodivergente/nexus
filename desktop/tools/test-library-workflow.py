"""Integration acceptance for original protection, complete library and work handoffs.

Fixtures copy real existing media into disposable profiles. The live profile and
phones are never written, and provider/generation execution is not permitted.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
from unittest.mock import Mock, patch
from urllib.parse import urlsplit
import uuid

from playwright.sync_api import sync_playwright


ROOT = Path(__file__).resolve().parents[2]
CHECKS = []


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def check(name, passed, detail=None):
    CHECKS.append({'name': name, 'passed': bool(passed), **({'detail': detail} if detail is not None else {})})
    print(('PASS ' if passed else 'FAIL ') + name, flush=True)


def rejected(operation):
    try:
        operation()
    except (ValueError, OSError):
        return True
    return False


def source_media(profile):
    state = json.loads((profile / 'state.json').read_text('utf-8-sig'))
    images, video, seen = [], None, set()
    for job in state.get('jobs', []):
        for output in job.get('outputs', []):
            uri = output if isinstance(output, str) else output.get('url', '')
            if not isinstance(uri, str) or not uri.startswith('/media/'):
                continue
            path = profile / 'media' / uri[7:]
            if not path.is_file() or path.stat().st_size < 32000:
                continue
            if job.get('modality') == 'IMAGE' and len(images) < 2:
                sha = digest(path)
                if sha not in seen:
                    images.append(path);seen.add(sha)
            if job.get('modality') == 'VIDEO' and video is None:
                video = path
        if len(images) == 2 and video is not None:
            break
    if len(images) != 2 or video is None:
        raise RuntimeError('Se necesitan dos imágenes distintas y un vídeo reales ya existentes.')
    return state, {'imageA': images[0], 'imageB': images[1], 'video': video}


def fixture(root, sources):
    media = root / 'media';media.mkdir(parents=True)
    (root / 'assistant').mkdir()
    paths, uris = {}, {}
    for key, path in sources.items():
        target = media / (uuid.uuid4().hex + path.suffix.lower())
        shutil.copyfile(path, target)
        paths[key], uris[key] = target, '/media/' + target.name
    index_only = media / (uuid.uuid4().hex + sources['imageB'].suffix.lower())
    shutil.copyfile(sources['imageB'], index_only)
    assets = [
        {'id':'asset-variant-a','uri':uris['imageA'],'type':'IMAGE','role':'OUTPUT','source':'GENERATED','displayName':'Variante A','createdAt':1,'projectId':'project-workflow','generationId':'generation-variants'},
        {'id':'asset-variant-b','uri':uris['imageB'],'type':'IMAGE','role':'OUTPUT','source':'GENERATED','displayName':'Variante B','createdAt':1,'projectId':'project-workflow','generationId':'generation-variants'},
        {'id':'asset-video','uri':uris['video'],'type':'VIDEO','role':'OUTPUT','source':'GENERATED','displayName':'Vídeo real','createdAt':2,'projectId':'project-workflow','generationId':'generation-video'},
        {'id':'asset-index-only','uri':'/media/'+index_only.name,'type':'IMAGE','role':'REFERENCE','source':'IMPORTED','displayName':'Original solo en el índice','createdAt':3,'projectId':'project-workflow'},
        {'id':'asset-deleted-alias','uri':uris['imageA'],'type':'IMAGE','role':'OUTPUT','source':'GENERATED','displayName':'Alias retirado','createdAt':1,'libraryDeleted':True},
        {'id':'asset-deleted-remote','uri':'https://fixture.invalid/deleted-original.jpeg','type':'IMAGE','role':'OUTPUT','source':'GENERATED','displayName':'Original retirado','createdAt':1,'libraryDeleted':True},
    ]
    jobs = [
        {'id':'job-variants','providerId':'local_media','modelId':'local/reference','name':'Dos variantes reales','modality':'IMAGE','status':'COMPLETED','outputs':[uris['imageA'],uris['imageB']],'prompt':'Prueba local','parameters':{},'startedAt':1,'projectId':'project-workflow'},
        {'id':'job-video','providerId':'local_media','modelId':'local/reference','name':'Vídeo real','modality':'VIDEO','status':'COMPLETED','outputs':[uris['video']],'prompt':'Prueba local','parameters':{},'startedAt':2,'projectId':'project-workflow'},
        {'id':'job-remote-cache','providerId':'local_media','modelId':'local/reference','name':'Original desde caché temporal','modality':'IMAGE','status':'COMPLETED','outputs':['https://fixture.invalid/original.jpeg?Expires=1'],'prompt':'Prueba local','parameters':{},'startedAt':3,'projectId':'project-workflow'},
        {'id':'job-unresolved','providerId':'local_media','modelId':'local/reference','name':'Referencia pendiente','modality':'IMAGE','status':'COMPLETED','outputs':['https://fixture.invalid/unavailable.png'],'prompt':'Prueba local','parameters':{},'startedAt':4,'projectId':'project-workflow'},
        {'id':'job-deleted-remote','providerId':'local_media','modelId':'local/reference','name':'Original retirado','modality':'IMAGE','status':'COMPLETED','outputs':['https://fixture.invalid/deleted-original.jpeg'],'prompt':'Prueba local','parameters':{},'startedAt':4,'projectId':'project-workflow'},
    ]
    index = {'projects':[{'id':'project-workflow','name':'Proyecto de aceptación','createdAt':1},{'id':'project-local','name':'Proyecto local anterior','createdAt':1}], 'assets':assets,
             'generations':[{'id':'generation-variants','nexusJobId':'job-variants','providerId':'local_media','modelId':'local/reference','modality':'IMAGE','prompt':'Prueba local','status':'COMPLETED','createdAt':1,'outputAssetIds':['asset-variant-a','asset-variant-b']},
                            {'id':'generation-video','nexusJobId':'job-video','providerId':'local_media','modelId':'local/reference','modality':'VIDEO','prompt':'Prueba local','status':'COMPLETED','createdAt':2,'outputAssetIds':['asset-video']} ]}
    state = {'jobs':jobs,'hidden':['local/reference'],'favorites':[], 'draft':{'modelId':None,'prompt':'Borrador local que debe conservarse','values':{},'media':[]}}
    (root / 'state.json').write_text(json.dumps(state), 'utf-8')
    (root / 'assistant/index.json').write_text(json.dumps(index), 'utf-8')
    return state, index, paths, uris


def archive_acceptance(root, state, paths, uris):
    import original_archive as archive_module
    from gallery_sources import GallerySources
    register = Mock(return_value=False)
    archive = archive_module.OriginalArchive(root, lambda: state, register_assets=register, start_worker=False)
    cache_key = archive.previews.key('job-remote-cache', 0, state['jobs'][2]['outputs'][0])
    ephemeral = archive.previews.originals / (cache_key + paths['imageA'].suffix)
    shutil.copyfile(paths['imageA'], ephemeral)
    before_jobs = copy.deepcopy(state['jobs'])
    with patch.object(archive_module.urllib.request, 'urlopen', side_effect=AssertionError('Archive attempted external traffic')):
        a = archive.protect('job-variants', 0, remote=False)
        b = archive.protect('job-variants', 1, remote=False)
        protected = archive.protect('job-remote-cache', 0, remote=False)
        check('Pending offline original never retries a provider generation', rejected(lambda: archive.protect('job-unresolved', 0, remote=False)))
    check('Two distinct variants keep their original verified bytes', a['sha256'] == digest(paths['imageA']) and b['sha256'] == digest(paths['imageB']) and a['sha256'] != b['sha256'])
    durable = root / 'media' / Path(protected['uri']).name
    check('A temporary downloaded original is promoted to durable media', durable.is_file() and durable != ephemeral and digest(durable) == digest(paths['imageA']))
    check('Archive does not rewrite historical output URLs or jobs', state['jobs'] == before_jobs)
    archive._register()
    check('Failed metadata registration does not claim cloud backup', archive.public()['registered'] == 0 and archive.public()['pendingCloud'] == archive.public()['archived'])
    register.return_value = True
    archive._register()
    check('A truthy acknowledgement without canonical index IDs is not a registration', archive.public()['registered'] == 0)

    def canonical_registration(rows):
        # AssetIndex.addAsset deduplicates by URI and can return an existing ID.
        # Preserve that canonical metadata, including a pre-existing tombstone.
        from native_operations import atomic_json
        current = json.loads((root/'assistant/index.json').read_text('utf-8'))
        mapping = {}
        for requested in rows:
            existing = next((asset for asset in current['assets'] if asset.get('uri') == requested['uri']),None)
            if existing is None:
                existing = copy.deepcopy(requested);current['assets'].append(existing)
            mapping[requested['id']] = existing['id']
        atomic_json(root/'assistant/index.json',current)
        return mapping

    register.side_effect = canonical_registration
    archive._register()
    variants = {(row['jobId'],row['index']):row for row in archive.records.values()}
    check('Original registration retains canonical IDs returned by URI deduplication', variants[('job-variants',0)]['assetId'] == 'asset-variant-a' and variants[('job-variants',1)]['assetId'] == 'asset-variant-b')
    check('Local registration still leaves cloud verification pending', archive.public()['registered'] == archive.public()['archived'] and archive.public()['pendingCloud'] == archive.public()['archived'])
    protected_again = archive.protect('job-variants',0,remote=False)
    check('Protecting the same verified original again preserves its canonical registration', protected_again['assetId'] == 'asset-variant-a' and protected_again['registered'] is True)
    hidden_copy = root/'media'/(uuid.uuid4().hex+paths['imageA'].suffix)
    shutil.copyfile(paths['imageA'],hidden_copy)
    current_index = json.loads((root/'assistant/index.json').read_text('utf-8'))
    current_index['assets'].append({'id':'asset-hidden-canonical','uri':'/media/'+hidden_copy.name,'type':'IMAGE','role':'OUTPUT','source':'GENERATED','displayName':'Original retirado','createdAt':1,'libraryDeleted':True})
    from native_operations import atomic_json
    atomic_json(root/'assistant/index.json',current_index)
    hidden_job = {**state['jobs'][0],'id':'job-hidden-canonical','outputs':['/media/'+hidden_copy.name]}
    state['jobs'].append(hidden_job)
    archive.protect(hidden_job['id'],remote=False);archive._register()
    stored_hidden = next(asset for asset in json.loads((root/'assistant/index.json').read_text('utf-8'))['assets'] if asset['id']=='asset-hidden-canonical')
    hidden_key = archive.previews.key(hidden_job['id'],0,hidden_job['outputs'][0])
    check('Protecting bytes behind an existing tombstone does not resurrect its library metadata', stored_hidden['libraryDeleted'] is True and archive.records[hidden_key]['assetId'] == 'asset-hidden-canonical')
    state['jobs'][:] = [job for job in state['jobs'] if job['id'] != hidden_job['id']]
    ephemeral.unlink()
    restarted = archive_module.OriginalArchive(root, lambda: state, start_worker=False)
    check('Protected original survives derived-cache deletion and archive restart', digest(durable) == protected['sha256'] and restarted.public()['archived'] == archive.public()['archived'])
    resolved = GallerySources(root, lambda: state).resolve('job-remote-cache', 0)[2]
    check('Durable original remains discoverable without temporary cache or JVM registration', resolved is not None and digest(resolved) == protected['sha256'])
    manifest_hash = digest(archive.path)
    old_records, old_count = copy.deepcopy(archive.records), archive.public()['archived']
    atomic_job = {**state['jobs'][0], 'id':'job-atomic', 'outputs':[uris['imageA']]}
    state['jobs'].append(atomic_job)
    replace = archive_module.os.replace

    def refuse_manifest(source, target):
        if Path(target) == archive.path:
            raise OSError('Simulated metadata commit failure')
        return replace(source, target)

    with patch.object(archive_module.os, 'replace', side_effect=refuse_manifest):
        failed = rejected(lambda: archive.protect('job-atomic', remote=False))
    check('Atomic metadata failure preserves the committed manifest', failed and digest(archive.path) == manifest_hash)
    check('Failed metadata commit never publishes an uncommitted protected record', archive.records == old_records and archive.public()['archived'] == old_count)
    check('Atomic failures leave no partial media or manifest files', not list((root / 'media').glob('.original-*.part')) and not list(root.glob('originals-protection.json.*.tmp')))
    mismatch = {**state['jobs'][0], 'id':'job-wrong-sha', 'outputs':[uris['imageA']], 'verifiedSha256':'0'*64}
    state['jobs'].append(mismatch)
    check('A verified-hash mismatch is rejected without modifying its original', rejected(lambda: archive.protect('job-wrong-sha', remote=False)) and digest(paths['imageA']) == a['sha256'])
    state['jobs'][:] = [job for job in state['jobs'] if job['id'] not in {'job-atomic','job-wrong-sha'}]
    check('Archive failures never mark a cloud copy as verified', all(row.get('cloudVerifiedAt') is None for row in archive.records.values()))
    confirmed = {row['assetId']:row['sha256'] for row in archive.records.values() if row.get('registered')}
    during = {**state['jobs'][1], 'id':'job-created-during-cloud-sync'}
    state['jobs'].append(during)
    late = archive.protect(during['id'], remote=False)
    archive._register()
    archive.cloud_verified(confirmed)
    late_key = archive.previews.key(during['id'],0,during['outputs'][0])
    check('Cloud confirmation excludes originals registered after that sync snapshot', archive.records[late_key].get('cloudVerifiedAt') is None and archive.public()['pendingCloud'] == 1)
    check('Confirmed cloud originals reference canonical IDs present in the shared index', all(any(asset['id']==row['assetId'] and asset.get('uri')==row['uri'] for asset in json.loads((root/'assistant/index.json').read_text('utf-8'))['assets']) for row in archive.records.values() if row.get('cloudVerifiedAt')))
    state['jobs'][:] = [job for job in state['jobs'] if job['id'] != during['id']]
    binary_state = {'jobs':[{'id':'job-binary-original','modality':'IMAGE','status':'COMPLETED','outputs':['https://fixture.invalid/full-original.jpeg']}]}
    binary = archive_module.OriginalArchive(root/'binary-response',lambda:binary_state,start_worker=False)

    def binary_download(url, target):
        shutil.copyfile(paths['imageA'],target)
        return target,'application/octet-stream',digest(paths['imageA'])

    with patch.object(binary,'_download',side_effect=binary_download):
        binary_record = binary.protect('job-binary-original',remote=True)
    protected_binary = binary.media / Path(binary_record['uri']).name
    check('A binary HTTP original keeps its real media suffix, MIME and exact permanent bytes', binary_record['mimeType'] == 'image/jpeg' and protected_binary.suffix in {'.jpeg','.jpg'} and digest(protected_binary) == digest(paths['imageA']) and binary.public()['pendingCloud'] == 1)


def library_acceptance(root, state):
    from library_snapshot import LibrarySnapshot
    before = copy.deepcopy(state)
    snapshot = LibrarySnapshot(root, lambda: state).snapshot()
    entries, hidden = snapshot['entries'], snapshot.get('hiddenIdentityKeys', [])
    keys = {key for row in entries for key in row.get('identityKeys', [])}
    check('An indexed original without a historical job is included', any(row.get('assetId') == 'asset-index-only' for row in entries))
    check('Every distinct generation output remains in the normalized projection', {'job:job-variants:0','job:job-variants:1'}.issubset(keys))
    check('Hiding a model does not hide its existing originals', any(row.get('assetId') == 'asset-variant-a' for row in entries) and any(row.get('assetId') == 'asset-variant-b' for row in entries))
    check('A retired duplicate alias does not hide the visible original', 'job:job-variants:0' not in hidden and 'asset:asset-variant-a' not in hidden)
    check('A retired remote original does not reappear through a historical job alias', 'job:job-deleted-remote:0' in hidden and not any(row['id'] == 'job:job-deleted-remote:0' for row in entries))
    check('Building the complete library never fabricates historical generation jobs', state == before)
    check('Unavailable original references remain represented honestly', any(row.get('sourceJobId') == 'job-unresolved' and not row.get('localUri') for row in entries))


def workspace_acceptance(root, state, paths, uris):
    from workspace_continuity import WorkspaceContinuity
    writer = Mock()
    owner = WorkspaceContinuity(root, lambda: state, writer, project_exists=lambda value: value == 'project-workflow')
    prior = copy.deepcopy(state)
    captured = owner.capture({'title':'Trabajo con referencia real','projectId':'project-workflow','view':'canvas','shared':True,
                              'canvas':{'modelId':'local/reference','providerId':'local_media','prompt':'Continuar esta escena','values':{'seed':123},
                                        'references':[{'assetId':'asset-variant-a','url':uris['imageA'],'name':'Referencia real','mime':'image/jpeg','field':'images'}]},
                              'editor':{'projectId':'project-workflow','playheadUs':2000000,'zoom':80}, 'library':{'kind':'IMAGE','query':'referencia'}})
    check('Capturing a resumable workspace never applies or submits it', state == prior and not writer.called and captured['paidExecutionEnabled'] is False)
    snapshot = owner.export()[0]
    reference = snapshot['canvas']['references'][0]
    check('Shared reference is captured with exact original SHA-256', reference['sha256'] == digest(paths['imageA']) and digest(owner.blob(reference['sha256'])) == reference['sha256'])
    owner.capture({'title':'Solo local','canvas':{'prompt':'Borrador privado'},'shared':False})
    check('Only explicitly shared workspaces are exported', len(owner.export()) == 1)
    invalid = {'canvas':{'values':{'api_key':'must-not-persist'},'prompt':'Prueba'}}
    check('Credentials are rejected from resumable draft parameters', rejected(lambda: owner.capture(invalid)))
    receiver_root = root / 'receiver';receiver_root.mkdir()
    receiver_state = {'draft':{'modelId':'local/different','prompt':'Borrador local diferente','values':{},'media':[]}, 'jobs':[]}
    receiver_before = copy.deepcopy(receiver_state)
    receiver_writer = Mock()
    receiver = WorkspaceContinuity(receiver_root, lambda: receiver_state, receiver_writer, project_exists=lambda value: False)
    receiver.receive(snapshot, lambda sha, target: shutil.copyfile(owner.blob(sha), target))
    check('Receiving a work point preserves the active local draft', receiver_state == receiver_before and not receiver_writer.called)
    opened = receiver.open(snapshot['id'])
    backup = receiver._read(receiver.root / 'local-backups' / (opened['preservedLocalDraft'] + '.json'), {})
    check('Explicit resume preserves a conflicting local draft as a recoverable copy', backup.get('draft') == receiver_before['draft'] and receiver_state == receiver_before)
    check('Resume returns the exact prompt, controls and local reference without execution', opened['draft']['prompt'] == 'Continuar esta escena' and opened['draft']['values'] == {'seed':123} and len(opened['draft']['media']) == 1 and opened['requiresReview'] and opened['paidExecutionEnabled'] is False)
    check('Unavailable project context is reported truthfully', opened['projectId'] == 'project-workflow' and opened['projectAvailable'] is False)
    restored = receiver.command({'op':'restoreBackup','id':opened['preservedLocalDraft']})
    check('The preserved local draft can be restored explicitly without submission', restored['draft'] == receiver_before['draft'] and restored['requiresReview'] and restored['paidExecutionEnabled'] is False)
    altered = copy.deepcopy(snapshot);altered['canvas']['prompt'] = 'Changed immutable content'
    check('A received immutable work point cannot be overwritten under the same ID', rejected(lambda: receiver.receive(altered)))
    bad_blob = copy.deepcopy(snapshot);bad_blob['id'] = str(uuid.uuid4());bad_blob['canvas']['references'][0]['sha256'] = 'f'*64
    check('A transferred reference with incorrect bytes is rejected', rejected(lambda: receiver.receive(bad_blob, lambda sha, target: target.write_bytes(b'incorrect reference'))))
    check('An invalid reference is not exposed as a usable local blob', receiver.blob('f'*64) is None)
    exported = receiver.export()
    check('Local draft backups are never exported to peers', all(row['id'] != opened['preservedLocalDraft'] for row in exported) and receiver_before['draft']['prompt'] not in json.dumps(exported))
    peer = str(uuid.uuid4())
    receiver.observe_peer(peer, ['gallery','chats'])
    check('A device without workspace support is not advertised as resume compatible', receiver.peers[peer]['workspaceCompatible'] is False)


def browser_acceptance(root, sources, output):
    from original_archive import OriginalArchive
    state, index, paths, uris = fixture(root, sources)
    archive = OriginalArchive(root, lambda: state, start_worker=True)
    key = archive.previews.key('job-remote-cache',0,state['jobs'][2]['outputs'][0])
    ephemeral = archive.previews.originals / (key + paths['imageA'].suffix)
    shutil.copyfile(paths['imageA'], ephemeral)
    archive.schedule(remote=False)
    deadline = time.monotonic() + 15
    while archive.work.unfinished_tasks and time.monotonic() < deadline:
        time.sleep(.05)
    archive.close()
    check('Offline archive worker completes local protection and preserves pending originals', archive.work.unfinished_tasks == 0 and archive.public()['archived'] >= 4 and archive.public()['failed'] >= 1)
    ephemeral.unlink(missing_ok=True)
    expected_jobs = sorted(job['id'] for job in state['jobs'])
    errors, external, prohibited, rpc_log = [], [], [], []
    phase = {'name':'bootstrap'}
    with (output / 'host.log').open('w', encoding='utf-8') as log:
        host = subprocess.Popen([sys.executable,'-u',str(ROOT/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(root)],
                                stdout=subprocess.PIPE,stderr=log,text=True,env={**os.environ,'PYTHONDONTWRITEBYTECODE':'1'},
                                creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:
            url = json.loads(host.stdout.readline())['url']
            with sync_playwright() as pw:
                browser = pw.chromium.launch(channel='msedge',headless=True)
                page = browser.new_page(viewport={'width':1440,'height':1000})
                page.on('pageerror',lambda error: errors.append(str(error)))

                def guard(route):
                    request = route.request
                    if urlsplit(request.url).netloc != urlsplit(url).netloc:
                        external.append({'host':urlsplit(request.url).hostname,'path':urlsplit(request.url).path,'type':request.resource_type,'phase':phase['name']});return route.abort()
                    if urlsplit(request.url).path == '/rpc':
                        message = request.post_data_json
                        op, sub = message.get('op'), (message.get('args') or {}).get('op')
                        rpc_log.append((op,sub))
                        if op in {'request','generate','upload','flowSubmit','flowConnect','flowQuote','nativeOperation'} or op == 'agent' and sub in {'send','confirm','apply'}:
                            prohibited.append((op,sub));return route.fulfill(json={'id':message['id'],'ok':False,'error':'No se permiten ejecuciones externas en la aceptación.'})
                    route.continue_()

                page.route('**/*',guard)
                page.goto(url)
                page.wait_for_function('()=>!!initial&&!!window.NexusGallery&&!!window.NexusWorkspaceContinuity',timeout=60000)
                page.evaluate("()=>{document.querySelector('#nx-startup-onboarding')?.remove();Studio.tab='results';navigate('library');}")
                phase['name'] = 'library'
                page.evaluate('async()=>{await window.NexusGallery.refresh();}')
                page.wait_for_function('()=>window.NexusGallery.items.some(row=>row.assetIds?.includes("asset-index-only"))')
                items = page.evaluate('window.NexusGallery.items')
                owners = lambda key: [row for row in items if key in row.get('identityKeys',[])]
                check('Real Edge includes the indexed original without a historical job', len(owners('asset:asset-index-only')) == 1)
                va, vb = owners('job:job-variants:0'), owners('job:job-variants:1')
                check('Real Edge shows every distinct output variant once', len(va) == 1 and len(vb) == 1 and va[0]['id'] != vb[0]['id'])
                check('Historical and indexed aliases deduplicate to one visible file', len(owners('asset:asset-variant-a')) == 1 and owners('asset:asset-variant-a')[0]['id'] == va[0]['id'])
                check('A deleted remote original stays out of the real library grid', not owners('job:job-deleted-remote:0'))
                check('Normalized file count matches the visible grid', page.locator('.gallery-grid .gallery-piece').count() == len(items))
                page.screenshot(path=str(output/'complete-library-real-media.png'))
                a_card = page.locator('[data-job="'+va[0]['id']+'"]')
                a_card.locator('[data-gallery-group]').click()
                page.wait_for_timeout(120)
                check('Generation grouping exposes all sibling variants', page.locator('.gallery-grid .gallery-piece').count() >= 2 and page.locator('[data-gallery=group-all]').is_visible())
                page.locator('[data-gallery=group-all]').click()
                page.locator('[data-job="'+va[0]['id']+'"] .gallery-media').click()
                page.wait_for_function('()=>{const i=document.querySelector(".gallery-lightbox img[data-gallery-preview=original]");return i?.complete&&i.naturalWidth>10;}')
                first = page.locator('.gallery-lightbox img[data-gallery-preview=original]').get_attribute('src')
                page.locator('.gallery-lightbox [data-gallery=next-output]').click()
                page.wait_for_function('(before)=>{const i=document.querySelector(".gallery-lightbox img[data-gallery-preview=original]");return i?.complete&&i.naturalWidth>10&&i.getAttribute("src")!==before;}',arg=first)
                check('Viewer navigation opens the next real original variant', page.locator('.gallery-lightbox img[data-gallery-preview=original]').get_attribute('src') != first)
                with page.expect_download() as downloaded:
                    page.locator('.gallery-lightbox [data-gallery=download-original]').click()
                check('A normalized variant download returns its full original bytes', digest(downloaded.value.path()) in {digest(paths['imageA']),digest(paths['imageB'])})
                page.keyboard.press('Escape')
                page.locator('#pc-search').fill('Original solo en el índice')
                page.wait_for_function('()=>document.querySelectorAll(".gallery-grid .gallery-piece").length===1')
                check('Indexed originals are searchable without fabricating a history entry', page.locator('.gallery-grid .gallery-piece').count() == 1)
                page.locator('.gallery-piece [data-gallery-select]').click()
                page.locator('#pc-search').focus()
                page.evaluate("()=>{window.__qaSearch=document.querySelector('#pc-search');window.__qaSearch.setSelectionRange(4,9);}")
                page.evaluate('async()=>{await window.NexusGallery.refresh();await window.NexusGallery.refresh();}')
                check('Repeated unchanged library refresh preserves search focus and selection', page.evaluate("document.activeElement===window.__qaSearch&&window.__qaSearch.selectionStart===4&&window.__qaSearch.selectionEnd===9") and page.locator('.gallery-piece [data-gallery-select][aria-pressed=true]').count() == 1)
                index['assets'][3]['displayName'] = 'Original solo en el índice actualizado'
                from native_operations import atomic_json
                atomic_json(root/'assistant/index.json', index)
                page.evaluate('async()=>{await window.NexusGallery.refresh();}')
                refresh_state = page.locator('#pc-search').evaluate('(n)=>({query:n.value,focused:document.activeElement===n,start:n.selectionStart,end:n.selectionEnd,selected:document.querySelectorAll(".gallery-piece [data-gallery-select][aria-pressed=true]").length})')
                check('A changed library revision preserves the active query and selected file', refresh_state == {'query':'Original solo en el índice','focused':True,'start':4,'end':9,'selected':1}, refresh_state)
                page.locator('.gallery-selection [data-gallery=clear]').click()
                page.locator('#pc-search').fill('')
                page.wait_for_function('()=>document.querySelectorAll(".gallery-grid .gallery-piece").length>1')
                late_variant = page.evaluate("""uri=>{
                 const job=state.jobs.find(j=>j.id==='job-variants');job.outputs.push(uri);refreshJobViews(['job-variants']);
                 const visible=NexusGallery.items.some(entry=>entry.identityKeys.includes('job:job-variants:2'));
                 job.outputs.pop();refreshJobViews(['job-variants']);return visible;
                }""", '/media/'+next(a['uri'][7:] for a in index['assets'] if a['id']=='asset-index-only'))
                check('A newly completed raw output is visible before the backend snapshot refreshes', late_variant)
                protection = page.evaluate("async()=>rpc('originalArchive',{op:'status'})")
                check('The protected originals status keeps unconfirmed cloud copies pending', protection['archived'] >= 4 and protection['pendingCloud'] >= protection['archived'])

                phase['name'] = 'canvas-capture'
                page.evaluate("""async uri=>{
                 await ensureAssistantReady();await agentCall('project',{id:'project-workflow'});
                 loaded=E.normalize(models.find(m=>m.id==='runway/gen4_image/image'));
                 state.draft={modelId:loaded.id,prompt:'Escena guardada para retomar',values:{...E.defaults(loaded),seed:123},
                  media:[{id:'qa-real-reference',assetId:'asset-variant-a',url:uri,name:'Referencia real',mime:'image/jpeg',field:loaded.fields[0].key,remoteUrl:null}]};
                 navigate('canvas');
                }""", uris['imageA'])
                page.evaluate("()=>window.addEventListener('nexus:workspace-saved',e=>window.__qaWorkspace=e.detail,{once:true})")
                page.locator('#workspace-save').click()
                page.wait_for_function('()=>!!window.__qaWorkspace?.id')
                workspace_id = page.evaluate('window.__qaWorkspace.id')
                check('The UI captures the real model and its reference binding', page.evaluate("window.__qaWorkspace.modelId==='runway/gen4_image/image'&&window.__qaWorkspace.referenceCount===1"))
                page.locator('#prompt').fill('Otro borrador local que debe volver intacto')
                page.locator('[data-remove=qa-real-reference]').click()
                page.evaluate("async()=>{await agentCall('project',{id:'project-local'});}")
                page.evaluate("navigate('settings')")
                page.wait_for_selector('[data-workspace-open="'+workspace_id+'"]')
                check('A previous-draft restore action stays hidden before any conflicting resume', not page.locator('[data-workspace-undo]').is_visible())
                page.wait_for_function('()=>document.querySelector("[data-originals-count=archived]")?.textContent!=="—"')
                check('Originals UI separates local verification from unconfirmed Drive backup', page.locator('[data-originals-count=cloudVerified]').inner_text() == '0' and int(page.locator('[data-originals-count=pendingCloud]').inner_text()) >= 4)
                check('Originals UI reports unavailable originals instead of claiming completion', 'pendientes' in page.locator('[data-originals-status]').inner_text().lower())
                page.screenshot(path=str(output/'explicit-resume-point.png'))
                phase['name'] = 'explicit-resume'
                page.locator('[data-workspace-open="'+workspace_id+'"]').click()
                page.wait_for_function('()=>document.querySelector("#prompt")?.value==="Escena guardada para retomar"')
                backups = [json.loads(p.read_text('utf-8')) for p in (root/'workspace-continuity/local-backups').glob('*.json')]
                check('Explicit UI resume preserves a conflicting local draft', any(row.get('draft',{}).get('prompt') == 'Otro borrador local que debe volver intacto' for row in backups))
                check('Explicit UI resume restores its saved project context', page.evaluate('Agent.snapshot.projectId') == 'project-workflow')
                restored_media = page.evaluate('state.draft.media')
                check('Explicit UI resume restores the real model parameter and reference field', page.evaluate("loaded.id==='runway/gen4_image/image'&&Number(state.draft.values.seed)===123&&state.draft.media.length===1&&loaded.fields.some(f=>f.key===state.draft.media[0].field)") and digest(root/'media'/restored_media[0]['url'][7:]) == digest(paths['imageA']))
                page.evaluate("navigate('settings')")
                page.wait_for_selector('[data-workspace-undo]:not([hidden])')
                page.locator('[data-workspace-undo]').click()
                phase['name'] = 'restore-local-draft'
                page.wait_for_function('()=>document.querySelector("#prompt")?.value==="Otro borrador local que debe volver intacto"')
                check('The previous local draft can be restored through the UI', page.locator('#prompt').input_value() == 'Otro borrador local que debe volver intacto')
                check('Undo also restores the previous project and reference choices', page.evaluate("Agent.snapshot.projectId==='project-local'&&state.draft.media.length===0"))
                check('Library and resume workflows never fabricate jobs', page.evaluate('state.jobs.map(j=>j.id).sort()') == expected_jobs)
                check('No generation, paid tool or provider operation was attempted', not prohibited,prohibited)
                check('No external browser request occurred', not external,external)
                check('Real Edge workflows have no JavaScript errors', not errors,errors)
                browser.close()
        finally:
            host.terminate();host.wait(timeout=15)
    return {'errors':errors,'externalHosts':external,'prohibitedOperations':prohibited,'rpc':rpc_log}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop')
    parser.add_argument('--output', type=Path, default=ROOT / 'outputs/library-workflow')
    parser.add_argument('--backend-only', action='store_true')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    live_state, sources = source_media(args.profile)
    before = {name:digest(path) for name,path in sources.items()}
    sys.path.insert(0, str(ROOT / 'desktop'))
    with tempfile.TemporaryDirectory(prefix='nexus-library-workflow-') as folder:
        root = Path(folder)
        state, index, paths, uris = fixture(root, sources)
        archive_acceptance(root, state, paths, uris)
        library_acceptance(root, state)
        workspace_acceptance(root, state, paths, uris)
        integration = None if args.backend_only else browser_acceptance(root/'browser',sources,args.output)
    check('Live source originals remain byte-identical', all(digest(path) == before[name] for name,path in sources.items()))
    report = {'status':'PASS' if all(row['passed'] for row in CHECKS) else 'FAIL','checks':CHECKS,
              'liveBaseline':{'jobs':len(live_state.get('jobs',[])), 'sourceBytes':{name:path.stat().st_size for name,path in sources.items()}},
              'sourceHashes':before,'mode':'disposable profiles; live source media read only',
              'browserIntegration':integration}
    (args.output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), 'utf-8')
    print(json.dumps({'status':report['status'],'checks':len(CHECKS),'output':str(args.output)}, ensure_ascii=False))
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
