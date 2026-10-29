#!/usr/bin/env python3
"""Project and folder acceptance; live audits read only and disclose aggregate data."""
import argparse
from collections import Counter, defaultdict
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit
import uuid

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
ADB = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
CHECKS = []


def digest_bytes(value):
    return hashlib.sha256(value).hexdigest()


def check(label, passed, detail=None):
    CHECKS.append({'label':label,'passed':bool(passed), **({'detail':detail} if detail is not None else {})})
    print(('PASS ' if passed else 'FAIL ') + label, flush=True)


def index_summary(value):
    assets = [row for row in value.get('assets',[]) if isinstance(row,dict)]
    visible = [row for row in assets if not row.get('libraryDeleted')]
    projects = [row for row in value.get('projects',[]) if isinstance(row,dict)]
    known = {row['id'] for row in projects}
    by_uri = defaultdict(set)
    for row in visible:
        by_uri[row.get('uri')].add(row.get('projectId'))
    # These are metadata counts, not claims that media bytes exist on this device.
    origin = lambda row: 'GENERATED' if row.get('source')=='GENERATED' else 'SHARED' if row.get('source')=='SHARED' else 'EDITED' if row.get('parentAssetId') is not None else 'IMPORTED'
    project_keys = sorted({key for row in projects for key in row})
    asset_keys = sorted({key for row in assets for key in row})
    return {
        'schemaVersion':value.get('schemaVersion'),
        'projects':len(projects),'archivedProjects':sum(bool(row.get('archived')) for row in projects),
        'projectKeys':project_keys,'assetKeys':asset_keys,
        'folderMetadataKeys':[key for key in project_keys+asset_keys if any(part in key.lower() for part in ('folder','collection','classif'))],
        'assets':len(assets),'visibleAssets':len(visible),'deletedAssets':len(assets)-len(visible),
        'generations':len(value.get('generations',[])),
        'origins':dict(Counter(map(origin,visible))),
        'types':dict(Counter(str(row.get('type')) for row in visible)),
        'roles':dict(Counter(str(row.get('role','OTHER')) for row in visible)),
        'unassigned':sum(row.get('projectId') is None for row in visible),
        'unknownProjectReferences':sum(row.get('projectId') is not None and row.get('projectId') not in known for row in visible),
        'sceneReferences':sum(bool(row.get('sceneId')) for row in visible),
        'shotReferences':sum(bool(row.get('shotId')) for row in visible),
        'characterReferences':sum(bool(row.get('characterIds')) for row in visible),
        'taggedAssets':sum(bool(row.get('tags')) for row in visible),
        'exactUriMultipleProjects':sum(len({p for p in projects if p is not None})>1 for projects in by_uri.values()),
        'projectTokens':sorted(digest_bytes(str(row['id']).encode())[:12] for row in projects),
        'perProject':[{'token':digest_bytes(str(row['id']).encode())[:12],
                      'archived':bool(row.get('archived')),
                      'visibleAssets':sum(a.get('projectId')==row['id'] for a in visible)} for row in projects],
    }


def audit_live(profile):
    desktop_bytes = (profile/'assistant/index.json').read_bytes()
    desktop = json.loads(desktop_bytes.decode('utf-8-sig'))
    baseline = {'desktop':{**index_summary(desktop),'indexSha256':digest_bytes(desktop_bytes)},'android':[],
                'privacy':'No names, prompts, file paths, URLs or credentials are persisted; project IDs use stable digest tokens.',
                'mode':'Read only: adb devices/getprop/dumpsys and run-as cat; no launches, writes or screen actions.'}
    listed = subprocess.check_output([str(ADB),'devices','-l'],timeout=15).decode('utf-8','replace')
    for line in listed.splitlines()[1:]:
        parts = line.split()
        if len(parts)<2 or parts[1]!='device':continue
        serial = parts[0]
        command = [str(ADB),'-s',serial]
        model = subprocess.check_output(command+['shell','getprop','ro.product.model'],timeout=15).decode().strip()
        package = subprocess.check_output(command+['shell','dumpsys','package','com.vidadomu.nexus.agent'],timeout=20).decode('utf-8','replace')
        version_code = re.search(r'\bversionCode=(\d+)',package)
        version_name = re.search(r'\bversionName=(\S+)',package)
        payload = subprocess.check_output(command+['exec-out','run-as','com.vidadomu.nexus.agent','cat','files/assistant/index.json'],timeout=25,stderr=subprocess.PIPE)
        index = json.loads(payload)
        raw_status = subprocess.run(command+['exec-out','run-as','com.vidadomu.nexus.agent','cat','files/assistant/sync/status.json'],timeout=20,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        status = json.loads(raw_status.stdout) if raw_status.returncode==0 else {}
        allowed_status = {key:status[key] for key in ['state','missingMedia','conflicts','busy','enabled','connected','configured'] if key in status and isinstance(status[key],(str,bool,int,float,type(None)))}
        phone_projects = {p['id'] for p in index.get('projects',[])}
        desktop_projects = {p['id'] for p in desktop.get('projects',[])}
        baseline['android'].append({'serial':serial,'model':model,
            'versionCode':int(version_code.group(1)) if version_code else None,
            'versionName':version_name.group(1) if version_name else None,
            'indexSha256':digest_bytes(payload),'index':index_summary(index),'driveStatus':allowed_status,
            'projectIntersectionWithDesktop':len(phone_projects & desktop_projects),
            'projectsMissingFromDesktop':len(phone_projects-desktop_projects),
            'desktopProjectsMissingFromPhone':len(desktop_projects-phone_projects)})
    return baseline


def fixture(root,sources):
    media=root/'media';media.mkdir(parents=True)
    (root/'assistant').mkdir()
    uris={}
    for name,source in sources.items():
        target=media/(uuid.uuid4().hex+source.suffix)
        shutil.copyfile(source,target)
        uris[name]='/media/'+target.name
    for name in ('document','unknown'):
        target=media/(uuid.uuid4().hex+'.txt')
        target.write_text('Documento de aceptación '+name+': organización por proyecto sin mover el original.\n','utf-8')
        uris[name]='/media/'+target.name
    legacy=media/(uuid.uuid4().hex+sources['imageB'].suffix)
    shutil.copyfile(sources['imageB'],legacy);uris['legacy']='/media/'+legacy.name
    projects=[{'id':'proj-a','name':'Alba','createdAt':1},
              {'id':'proj-b','name':'Bosque','createdAt':2},
              {'id':'proj-arch','name':'Archivo','createdAt':3,'archived':True},
              {'id':'proj-empty','name':'Vacío','createdAt':4}]
    assets=[
        {'id':'shared-a','uri':uris['imageA'],'type':'IMAGE','role':'OUTPUT','source':'GENERATED','displayName':'Imagen compartida','createdAt':10,'projectId':'proj-a','sceneId':'scene-a','shotId':'shot-a','characterIds':['character-a'],'tags':['azul']},
        {'id':'shared-b','uri':uris['imageA'],'type':'IMAGE','role':'REFERENCE','source':'SHARED','displayName':'Alias compartido','createdAt':10,'projectId':'proj-b','sceneId':'scene-b','shotId':'shot-b','tags':['verde']},
        {'id':'unassigned-doc','uri':uris['document'],'type':'OTHER','role':'OTHER','displayName':'Documento sin proyecto ni origen explícito','mimeType':'text/plain','createdAt':11,'projectId':None},
        {'id':'edited','uri':uris['imageB'],'type':'IMAGE','role':'SOURCE','source':'IMPORTED','displayName':'Edición Alba','createdAt':12,'projectId':'proj-a','parentAssetId':'shared-a'},
        {'id':'archived','uri':uris['video'],'type':'VIDEO','role':'FINAL','source':'GENERATED','displayName':'Vídeo archivado','createdAt':13,'projectId':'proj-arch'},
        {'id':'unknown','uri':uris['unknown'],'type':'OTHER','role':'OTHER','source':'IMPORTED','displayName':'Referencia a proyecto ausente','mimeType':'text/plain','createdAt':14,'projectId':'proj-missing'},
        {'id':'deleted','uri':'/media/'+uuid.uuid4().hex+'.jpg','type':'IMAGE','role':'REFERENCE','source':'IMPORTED','displayName':'Archivo retirado','createdAt':15,'projectId':'proj-a','libraryDeleted':True},
    ]
    jobs=[{'id':'job-variants','providerId':'local_media','modelId':'local/reference','name':'Creación con varias afiliaciones','modality':'IMAGE','status':'COMPLETED','outputs':[uris['imageA'],uris['imageB'],uris['document']],'parameters':{'__project_id':'proj-a'},'startedAt':10,'projectId':'proj-a'},
          {'id':'job-legacy','providerId':'local_media','modelId':'local/reference','name':'Salida antigua sin Asset','modality':'IMAGE','status':'COMPLETED','outputs':[uris['legacy']],'parameters':{},'startedAt':16}]
    index={'projects':projects,'assets':assets,
           'scenes':[{'id':'scene-a','projectId':'proj-a','code':'A01'},{'id':'scene-b','projectId':'proj-b','code':'B01'}],
           'shots':[{'id':'shot-a','projectId':'proj-a','sceneId':'scene-a','code':'A01-01'},{'id':'shot-b','projectId':'proj-b','sceneId':'scene-b','code':'B01-01'}],
           'characters':[{'id':'character-a','projectId':'proj-a','name':'Personaje de prueba'}],
           'generations':[{'id':'generation-legacy','nexusJobId':'job-legacy','providerId':'local_media','modelId':'local/reference','modality':'IMAGE','prompt':'Aceptación local','status':'COMPLETED','createdAt':16,'projectId':'proj-b','outputAssetIds':[]}]}
    state={'jobs':jobs,'draft':{'modelId':None,'prompt':'Borrador que no debe cambiar al organizar','values':{},'media':[]},'favorites':[],
           'studioGallery':{'favorites':[],'collections':[{'id':'legacy-collection','name':'Colección anterior'}],'members':{'job-variants':'legacy-collection'},'hidden':[],'size':240}}
    (root/'state.json').write_text(json.dumps(state),'utf-8')
    (root/'assistant/index.json').write_text(json.dumps(index),'utf-8')
    return state,index,uris


def model_call(snapshot,state,filters=None,prefs=None):
    script="const fs=require('node:fs'),M=require(process.argv[1]),v=JSON.parse(fs.readFileSync(0,'utf8'));const entries=M.build(v.snapshot,v.jobs);process.stdout.write(JSON.stringify({entries,filtered:M.filter(entries,v.filters||{},v.prefs||{})}));"
    result=subprocess.run(['node','-e',script,str(ROOT/'desktop/ui/library-model.js')],
                          input=json.dumps({'snapshot':snapshot,'jobs':state['jobs'],'filters':filters,'prefs':prefs}),
                          text=True,encoding='utf-8',stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=30)
    return json.loads(result.stdout)


def backend_acceptance(root,state,index,uris):
    from library_snapshot import LibrarySnapshot
    before=(root/'assistant/index.json').read_bytes()
    snapshot=LibrarySnapshot(root,lambda:state).snapshot()
    rows=model_call(snapshot,state)['entries']
    owners=lambda key:[row for row in rows if key in row.get('identityKeys',[])]
    shared=owners('asset:shared-a')
    check('One physical file keeps both project memberships after deduplication', len(shared)==1 and owners('asset:shared-b')[0]['id']==shared[0]['id'] and set(shared[0].get('projectIds',[]))=={'proj-a','proj-b'})
    check('All visible indexed originals and the legacy-only output remain discoverable', len(rows)==6 and all(owners('asset:'+row['id']) for row in index['assets'] if not row.get('libraryDeleted')))
    check('An explicitly unassigned document does not inherit its old job project', not owners('asset:unassigned-doc')[0].get('projectIds'))
    check('A legacy output without an asset retains its generation project', 'proj-b' in owners('job:job-legacy:0')[0].get('projectIds',[]))
    check('Unknown project IDs remain represented without rewriting the asset', 'proj-missing' in owners('asset:unknown')[0].get('projectIds',[]))
    ids=lambda filters:{row['id'] for row in model_call(snapshot,state,filters)['filtered']}
    shared_id=shared[0]['id']
    edited_id=owners('asset:edited')[0]['id']
    check('Project Alba contains its shared and edited originals', ids({'project':'proj-a'})=={shared_id,edited_id})
    check('Project Bosque includes the same shared file and its legacy output', ids({'project':'proj-b'})=={shared_id,owners('job:job-legacy:0')[0]['id']})
    check('Archived project originals stay discoverable through their folder', ids({'project':'proj-arch'})=={owners('asset:archived')[0]['id']})
    check('An empty project filter does not leak unrelated files', ids({'project':'proj-empty'})==set())
    check('Project, role and scene filters refer to the same membership', ids({'project':'proj-a','role':'REFERENCE','sceneId':'scene-b'})==set() and ids({'project':'proj-b','role':'REFERENCE','sceneId':'scene-b'})=={shared_id})
    check('Generated and shared origins remain scoped to their own project affiliation', ids({'project':'proj-a','source':'SHARED'})==set() and ids({'project':'proj-b','source':'SHARED'})=={shared_id})
    check('Edited origin derives from the parent asset rather than provider naming', ids({'source':'EDITED'})=={edited_id})
    check('A document without explicit source uses the APK imported classification', owners('asset:unassigned-doc')[0]['id'] in ids({'source':'IMPORTED'}))
    explicit=[row for row in snapshot['entries'] if row.get('assetId')=='shared-a'][0]
    check('Scene, shot, character, role and tag metadata survive the shared snapshot', explicit.get('sceneId')=='scene-a' and explicit.get('shotId')=='shot-a' and explicit.get('characterIds')==['character-a'] and explicit.get('tags')==['azul'] and explicit.get('role')=='OUTPUT')
    check('Snapshot project folders preserve native IDs and archived metadata', any(row.get('id')=='proj-arch' and row.get('archived') for row in snapshot.get('projects',[])))
    check('A legacy local collection preference still selects all its output files', len(model_call(snapshot,state,{'collection':'legacy-collection'},state['studioGallery'])['filtered'])==3)
    check('Project and origin projections do not modify the index or historical jobs', (root/'assistant/index.json').read_bytes()==before and json.loads((root/'state.json').read_text('utf-8'))['jobs']==state['jobs'])
    return snapshot


def browser_acceptance(root,sources,output):
    state,index,uris=fixture(root,sources)
    original_jobs=json.dumps(state['jobs'],sort_keys=True)
    original_draft=state['draft']
    original_preferences=state['studioGallery']
    original_index=(root/'assistant/index.json').read_bytes()
    errors=[];external=[];prohibited=[]
    with (output/'host.log').open('w',encoding='utf-8') as log:
        host=subprocess.Popen([sys.executable,'-u',str(ROOT/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(root)],stdout=subprocess.PIPE,stderr=log,text=True,
                              env={**os.environ,'PYTHONDONTWRITEBYTECODE':'1'},creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:
            url=json.loads(host.stdout.readline())['url']
            with sync_playwright() as pw:
                browser=pw.chromium.launch(channel='msedge',headless=True)
                page=browser.new_page(viewport={'width':1440,'height':1000})
                page.on('pageerror',lambda error:errors.append(str(error)))
                def guard(route):
                    request=route.request
                    if urlsplit(request.url).netloc!=urlsplit(url).netloc:
                        external.append(urlsplit(request.url).hostname);return route.abort()
                    if urlsplit(request.url).path=='/rpc':
                        message=request.post_data_json;op=message.get('op');sub=(message.get('args') or {}).get('op')
                        if op in {'request','generate','upload','flowSubmit','flowConnect','flowQuote','nativeOperation'} or op=='agent' and sub in {'send','confirm','apply'}:
                            prohibited.append((op,sub));return route.fulfill(json={'id':message['id'],'ok':False,'error':'No generation allowed in project acceptance.'})
                    route.continue_()
                page.route('**/*',guard)
                page.goto(url)
                page.wait_for_function('()=>!!initial&&!!window.NexusGallery&&!!window.NexusWorkspaceContinuity',timeout=60000)
                page.evaluate("()=>{document.querySelector('#nx-startup-onboarding')?.remove();Studio.tab='results';navigate('library');}")
                page.evaluate('async()=>{await NexusGallery.refresh();}')
                page.wait_for_selector('#library-projects')
                if page.locator('[aria-label="Expandir navegación"]').count():page.locator('[aria-label="Expandir navegación"]').click()
                page.wait_for_function('()=>NexusGallery.items.some(row=>row.assetIds?.includes("shared-a"))')
                items=page.evaluate('NexusGallery.items')
                shared=next(row for row in items if 'shared-a' in row.get('assetIds',[]))
                ids=lambda:{row['id'] for row in page.locator('.gallery-grid .gallery-piece').evaluate_all('(nodes)=>nodes.map(n=>({id:n.dataset.job}))')}
                def restore(**filters):
                    page.evaluate('async value=>{await NexusGallery.restoreWorkspace(value);}',filters)
                check('Real library includes active, unassigned and archived project folders', all(page.locator('[data-library-project="'+key+'"]').count() for key in ('__all__','__unassigned__','proj-a','proj-b','proj-arch','proj-empty')))
                check('Project folders show all six physical originals without duplicates', len(ids())==6 and len(items)==6)
                page.wait_for_function('()=>[...document.querySelectorAll(".gallery-grid img[data-gallery-preview=thumbnail]")].filter(i=>i.complete&&i.naturalWidth>100).length===4')
                page.screenshot(path=str(output/'project-folders-all-originals.png'))
                page.locator('[data-library-project="proj-a"]').click()
                check('Selecting a project folder finds its shared file and edited original', len(ids())==2 and shared['id'] in ids())
                page.locator('[data-library-project="proj-b"]').click()
                check('The same physical file remains available from a second project folder', len(ids())==2 and shared['id'] in ids())
                page.locator('[data-library-project="__unassigned__"]').click()
                unassigned=next(row['id'] for row in items if 'unassigned-doc' in row.get('assetIds',[]))
                check('Sin proyecto includes the explicitly unassigned document', unassigned in ids())
                page.locator('[data-library-project="proj-arch"]').click()
                check('An archived project folder still opens its preserved original', len(ids())==1)
                page.locator('[data-library-project="proj-empty"]').click()
                check('An empty project remains a usable empty folder', not ids())
                restore(project='proj-b',folder='proj-b',source='SHARED',role='REFERENCE',sceneId='scene-b',shotId='shot-b')
                check('Combined native organization filters select the matching project membership', ids()=={shared['id']})
                restore(project='proj-a',folder='proj-a',source='SHARED',role='REFERENCE',sceneId='scene-b',shotId='shot-b')
                check('Cross-project metadata cannot produce a false filter match', not ids())
                restore(project='proj-a',folder='proj-a',source='GENERATED',role='OUTPUT',sceneId='scene-a',shotId='shot-a')
                check('The original generated project membership remains filterable', ids()=={shared['id']})
                workspace=page.evaluate('NexusGallery.workspace')
                check('Workspace state preserves project, origin, role, scene and shot filters', all(workspace.get(key)==value for key,value in {'project':'proj-a','source':'GENERATED','role':'OUTPUT','sceneId':'scene-a','shotId':'shot-a'}.items()))
                check('Filtering leaves the active draft, raw history and local collections intact', page.evaluate('state.draft')==original_draft and page.evaluate('state.studioGallery.collections')==original_preferences['collections'] and page.evaluate('state.studioGallery.members')==original_preferences['members'] and json.dumps(page.evaluate('state.jobs'),sort_keys=True)==original_jobs)
                check('Browsing classification never rewrites source asset metadata', (root/'assistant/index.json').read_bytes()==original_index)
                # A creation can contain separate files assigned to different
                # projects. Its output controls must keep the active scope.
                page.evaluate("uris=>{state.jobs.push({id:'qa-split-projects',providerId:'local_media',modelId:'local/reference',name:'Resultados repartidos',modality:'IMAGE',status:'COMPLETED',outputs:[uris.imageB,uris.legacy],parameters:{__project_id:'proj-b'},projectId:'proj-b',startedAt:20});refreshJobViews(['qa-split-projects']);}",uris)
                restore(project='proj-a',folder='proj-a',group='job:qa-split-projects')
                edited=next(row['id'] for row in items if 'edited' in row.get('assetIds',[]))
                scoped_grid=ids()=={edited}
                scoped_badges=page.locator('.gallery-grid [data-gallery-group]').count()==0
                page.locator('[data-job="'+edited+'"] .gallery-media').click()
                page.wait_for_function('()=>{const image=document.querySelector(".gallery-lightbox img[data-gallery-preview=original]");return image?.complete&&image.naturalWidth>100;}')
                for action in ('next-output','prev-output'):
                    button=page.locator('.gallery-lightbox [data-gallery='+action+']')
                    if button.count() and button.is_visible() and button.is_enabled():button.click()
                check('The viewer and variant badges keep the selected project when one creation spans two projects', scoped_grid and scoped_badges and page.evaluate('NexusGallery.workspace.viewer.id')==edited and ids()=={edited})
                page.keyboard.press('Escape')
                page.evaluate("()=>{state.jobs=state.jobs.filter(job=>job.id!=='qa-split-projects');refreshJobViews(['qa-split-projects']);}")
                restore(**workspace)
                # Simulate an external native project rename, preserving its stable ID.
                from native_operations import atomic_json
                index['projects'][0]['name']='Alba renombrado'
                atomic_json(root/'assistant/index.json',index)
                page.evaluate('async()=>{await NexusGallery.refresh();}')
                check('A native folder rename updates its label while retaining the selected ID', 'Alba renombrado' in page.locator('[data-library-project="proj-a"]').inner_text() and page.evaluate('NexusGallery.workspace.project')=='proj-a' and ids()=={shared['id']})
                page.evaluate("async()=>{await ensureAssistantReady();await agentCall('project',{id:'proj-b'});}")
                saved=page.evaluate('async()=>await NexusWorkspaceContinuity.capture()')
                restore(project='proj-b',folder='proj-b')
                page.evaluate('async id=>{await NexusWorkspaceContinuity.open(id);}',saved['snapshot']['id'])
                resumed=page.evaluate('NexusGallery.workspace')
                check('Explicit saved-work resume restores the complete native library filter', all(resumed.get(key)==workspace.get(key) for key in ('project','folder','source','role','sceneId','shotId')) and ids()=={shared['id']})
                restore(project='proj-a',folder='proj-a')
                check('A chosen folder supplies import context without changing Agent project', page.evaluate("NexusGallery.importContext.projectId==='proj-a'&&Agent.snapshot.projectId==='proj-b'"))
                for project,name,source in [('proj-a','Importado Alba'+sources['imageA'].suffix,sources['imageA']),('__unassigned__','Importado sin proyecto'+sources['imageB'].suffix,sources['imageB'])]:
                    restore(project=project,folder=project)
                    # A genuinely new file tests chosen-folder import; repeated
                    # original bytes are deliberately rejected by library dedup.
                    target=root/name;target.write_bytes(source.read_bytes()+b'\nNexus import scope: '+project.encode())
                    with page.expect_file_chooser() as chooser:
                        page.locator('[data-import-gallery]').click()
                    chooser.value.set_files(str(target))
                    page.wait_for_function('name=>NexusGallery.items.some(row=>row.displayName===name)',arg=name,timeout=45000)
                    current=json.loads((root/'assistant/index.json').read_text('utf-8'))
                    asset=next(a for a in current['assets'] if a.get('displayName')==name)
                    check('Library import follows '+('the selected folder' if project=='proj-a' else 'explicit Sin proyecto')+' instead of the Agent project', asset.get('projectId')==('proj-a' if project=='proj-a' else None))
                    copied=root/'media'/asset['uri'][7:]
                    check('Imported original keeps its exact bytes in '+project, digest_bytes(copied.read_bytes())==digest_bytes(target.read_bytes()))
                check('Importing originals never fabricates generation jobs', json.dumps(page.evaluate('state.jobs'),sort_keys=True)==original_jobs)
                check('Library organization and resume never trigger a generation or provider operation', not prohibited,prohibited)
                check('Project and folder workflows make no external browser requests', not external,external)
                check('Real Edge project organization has no JavaScript errors', not errors,errors)
                browser.close()
        finally:
            host.terminate();host.wait(timeout=15)
    return {'browser':'Microsoft Edge','externalRequests':external,'prohibitedOperations':prohibited,'errors':errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile',type=Path,default=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop')
    parser.add_argument('--output',type=Path,default=ROOT/'outputs/library-projects')
    parser.add_argument('--audit-live-only',action='store_true')
    parser.add_argument('--backend-only',action='store_true')
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    if args.audit_live_only:
        baseline = audit_live(args.profile)
        path=args.output/'live-metadata-audit.json'
        path.write_text(json.dumps(baseline,ensure_ascii=False,indent=2),'utf-8')
        print(json.dumps({'output':str(path),'desktop':{key:baseline['desktop'][key] for key in ('projects','visibleAssets','archivedProjects','origins')},
                          'android':[{'model':row['model'],'versionCode':row['versionCode'],'projects':row['index']['projects'],'visibleAssets':row['index']['visibleAssets'],'missingProjects':row['projectsMissingFromDesktop']} for row in baseline['android']]},ensure_ascii=False))
        return 0
    helper_spec=importlib.util.spec_from_file_location('workflow_sources',ROOT/'desktop/tools/test-library-workflow.py')
    helper=importlib.util.module_from_spec(helper_spec);helper_spec.loader.exec_module(helper)
    _,sources=helper.source_media(args.profile)
    before={name:digest_bytes(path.read_bytes()) for name,path in sources.items()}
    sys.path.insert(0,str(ROOT/'desktop'))
    with tempfile.TemporaryDirectory(prefix='nexus-library-projects-') as folder:
        root=Path(folder)
        state,index,uris=fixture(root/'projection',sources)
        backend_acceptance(root/'projection',state,index,uris)
        integration=None if args.backend_only else browser_acceptance(root/'browser',sources,args.output)
    check('All live source originals remain byte-identical', all(digest_bytes(path.read_bytes())==before[name] for name,path in sources.items()))
    report={'status':'PASS' if all(row['passed'] for row in CHECKS) else 'FAIL','checks':CHECKS,'integration':integration,
            'mode':'Real files copied into disposable QA profiles; no live metadata changes or Android writes.'}
    (args.output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8')
    print(json.dumps({'status':report['status'],'checks':len(CHECKS),'output':str(args.output)}))
    return 0 if report['status']=='PASS' else 1


if __name__=='__main__':
    sys.exit(main())
