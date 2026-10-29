"""Real-media acceptance for project moves and duplicate-safe imports.

All mutations stay in a disposable profile. Existing source media are read only;
provider operations, generations and external browser traffic are blocked.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit
import uuid

from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[2]
CHECKS=[]


def digest(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def check(label,passed,detail=None):
    CHECKS.append({'label':label,'passed':bool(passed),**({'detail':detail} if detail is not None else {})})
    print(('PASS ' if passed else 'FAIL ')+label,flush=True)


def native_asset(row):
    # Native snapshot JSON includes defaults; AssetIndex persists them omitted.
    # Fill the documented Kotlin defaults on both sides, retaining every field.
    return {'role':'OTHER','source':'IMPORTED','mimeType':None,'width':None,'height':None,'durationSec':None,
            'projectId':None,'sceneId':None,'shotId':None,'characterIds':[],'tags':[],'generationId':None,
            'parentAssetId':None,'libraryDeleted':False,'version':1,**row}


def native_generation(row):
    return {'nexusJobId':None,'providerJobId':None,'modelVersion':None,'negativePrompt':None,'params':{},'seed':None,
            'referenceAssetIds':[],'referenceUris':[],'estimatedCostUsd':None,'actualCostUsd':None,
            'parentGenerationId':None,'agentPlanId':None,'projectId':None,'shotId':None,'outputAssetIds':[],
            'completedAt':None,**row}


def fixture(root,sources):
    media=root/'media';media.mkdir(parents=True);(root/'assistant').mkdir()
    uris={}
    for name,source in {**sources,'imageA-copy':sources['imageA']}.items():
        path=media/(uuid.uuid4().hex+source.suffix);shutil.copyfile(source,path);uris[name]='/media/'+path.name
    archived_doc=media/(uuid.uuid4().hex+'.txt');archived_doc.write_text('Documento ya conservado en el archivo del proyecto.\n','utf-8');uris['archived-doc']='/media/'+archived_doc.name
    def asset(identifier,uri,project,**extra):
        return {'id':identifier,'uri':uri,'projectId':project,'displayName':identifier,'type':'IMAGE','role':'REFERENCE','source':'IMPORTED',
                'mimeType':'image/jpeg','width':None,'height':None,'durationSec':None,'sceneId':None,'shotId':None,'characterIds':[],
                'tags':[],'generationId':None,'parentAssetId':None,'libraryDeleted':False,'version':1,'createdAt':1,**extra}
    assets=[asset('source-a',uris['imageA'],'project-a',source='GENERATED',role='OUTPUT',sceneId='scene-a',shotId='shot-a',tags=['conservar-a'],characterIds=['character-a']),
            asset('copy-a',uris['imageA-copy'],'project-a',sceneId='scene-a',shotId='shot-a',tags=['conservar-copia']),
            asset('existing-b',uris['imageA'],'project-b',source='SHARED',sceneId='scene-b',shotId='shot-b',tags=['conservar-b']),
            asset('retired',uris['video'],'project-a',type='VIDEO',mimeType='video/mp4',libraryDeleted=True),
            asset('archived-doc',uris['archived-doc'],'project-arch',type='OTHER',mimeType='text/plain')]
    projects=[{'id':'project-'+letter,'name':'Proyecto '+letter.upper(),'createdAt':1} for letter in 'abc']
    projects.append({'id':'project-arch','name':'Proyecto archivado','createdAt':1,'archived':True})
    jobs=[{'id':'job-original','providerId':'local_media','modelId':'local/reference','name':'Original indexado','modality':'IMAGE','status':'COMPLETED','outputs':[uris['imageA']],
           'parameters':{'__project_id':'project-a'},'projectId':'project-a','startedAt':1},
          {'id':'job-legacy','providerId':'local_media','modelId':'local/reference','name':'Original histórico sin Asset','modality':'IMAGE','status':'COMPLETED','outputs':[uris['imageB']],
           'parameters':{'__project_id':'project-a'},'projectId':'project-a','startedAt':2}]
    index={'projects':projects,'assets':assets,'scenes':[{'id':'scene-'+x,'projectId':'project-'+x,'code':x.upper()} for x in 'ab'],
           'shots':[{'id':'shot-'+x,'projectId':'project-'+x,'sceneId':'scene-'+x,'code':x.upper()+'1'} for x in 'ab'],
           'characters':[{'id':'character-a','projectId':'project-a','name':'Referencia QA'}],
           'generations':[{'id':'generation-original','nexusJobId':'job-original','providerId':'local_media','modelId':'local/reference','modality':'IMAGE','prompt':'Prueba local',
                           'status':'COMPLETED','createdAt':1,'projectId':'project-a','outputAssetIds':['source-a']}]}
    state={'jobs':jobs,'draft':{'modelId':None,'prompt':'Borrador que permanece intacto','values':{},'media':[]},'favorites':[],
           'studioGallery':{'favorites':[],'collections':[],'members':{},'hidden':[],'size':240}}
    (root/'state.json').write_text(json.dumps(state),'utf-8');(root/'assistant/index.json').write_text(json.dumps(index),'utf-8')
    return state,index,uris


def acceptance(profile,sources,output):
    state,index,uris=fixture(profile,sources)
    original_media={path.name:digest(path) for path in (profile/'media').iterdir()}
    jobs_before=json.dumps(state['jobs'],sort_keys=True)
    original_ids={row['id'] for row in index['assets']}
    errors=[];external=[];prohibited=[];moves=[];imports=[]
    def current_index():return json.loads((profile/'assistant/index.json').read_text('utf-8'))
    with (output/'host.log').open('w',encoding='utf-8') as log:
        host=subprocess.Popen([sys.executable,'-u',str(ROOT/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(profile)],stdout=subprocess.PIPE,stderr=log,text=True,
                              env={**os.environ,'PYTHONDONTWRITEBYTECODE':'1'},creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        try:
            url=json.loads(host.stdout.readline())['url']
            with sync_playwright() as pw:
                browser=pw.chromium.launch(channel='msedge',headless=True);page=browser.new_page(viewport={'width':1440,'height':1000})
                page.on('pageerror',lambda error:errors.append(str(error)))
                def guard(route):
                    request=route.request
                    if urlsplit(request.url).netloc!=urlsplit(url).netloc:external.append(urlsplit(request.url).hostname);return route.abort()
                    if urlsplit(request.url).path=='/rpc':
                        message=request.post_data_json;op=message.get('op');args=message.get('args') or {}
                        if op=='libraryOperations':moves.append(args)
                        if op=='libraryImport':imports.append(args)
                        if op in {'request','generate','upload','flowSubmit','flowConnect','flowQuote','nativeOperation'} or op=='agent' and args.get('op') in {'send','confirm','apply'}:
                            prohibited.append(op);return route.fulfill(json={'id':message['id'],'ok':False,'error':'Provider operations are blocked in acceptance.'})
                    route.continue_()
                page.route('**/*',guard);page.goto(url)
                page.wait_for_function('()=>!!initial&&!!window.NexusGallery&&!!window.NexusImports',timeout=60000)
                page.evaluate("()=>{document.querySelector('#nx-startup-onboarding')?.remove();Studio.tab='results';navigate('library');}")
                def restore(project):page.evaluate('async project=>{await NexusGallery.restoreWorkspace({project});}',project)
                restore('__all__')
                page.wait_for_function('()=>NexusGallery.items.some(row=>row.assetIds?.includes("existing-b"))')
                # Compare the real native representation before and after the
                # operation. Kotlin omits default fields in persisted JSON.
                native_before=page.evaluate("async()=>{await agentCall('snapshot');return Agent.snapshot.index;}")
                existing_b=next(row for row in native_before['assets'] if row['id']=='existing-b')
                generations_before=copy.deepcopy(native_before['generations'])
                def items():return page.evaluate('NexusGallery.items')
                def file_a():return next(row for row in items() if 'existing-b' in row.get('assetIds',[]))
                def ids():return set(page.locator('.gallery-grid .gallery-piece').evaluate_all('nodes=>nodes.map(n=>n.dataset.job)'))
                initial_count=len(items())
                check('Existing exact-byte copies at different URIs appear once with both projects', initial_count==3 and set(file_a().get('projectIds',[]))=={'project-a','project-b'})
                def move(entry_id,source,target,capture=None):
                    restore(source)
                    selector=page.locator('[data-job="'+entry_id+'"] [data-gallery-select]')
                    if selector.get_attribute('aria-pressed')!='true':selector.click()
                    page.locator('[data-gallery=move-files]').click();page.wait_for_selector('#library-move-project')
                    page.locator('#library-move-project').select_option(target)
                    page.locator('#library-move-form [type=submit]').click()
                    page.wait_for_function('()=>["success","warning","error"].includes(document.querySelector("#library-move-status")?.dataset.state)',timeout=60000)
                    result=page.locator('#library-move-status').evaluate('n=>({state:n.dataset.state,text:n.textContent})')
                    if capture:page.screenshot(path=str(output/capture))
                    page.keyboard.press('Escape')
                    return result
                restore('project-a');first=file_a()['id']
                page.locator('[data-job="'+first+'"] [data-gallery-select]').click();page.locator('[data-gallery=move-files]').click()
                before_cancel=(profile/'assistant/index.json').read_bytes();page.keyboard.press('Escape')
                check('Cancelling a move leaves all project assignments untouched', (profile/'assistant/index.json').read_bytes()==before_cancel)
                same=move(file_a()['id'],'project-a','project-a')
                check('Moving to the current project reports success without creating an association', same['state']=='success' and len(current_index()['assets'])==len(index['assets']))
                result=move(file_a()['id'],'project-a','project-c','move-confirmed-real-original.png')
                after_move=current_index();rows={row['id']:row for row in after_move['assets']}
                check('A scoped move updates its source project and preserves the other membership', result['state']=='success' and set(file_a().get('projectIds',[]))=={'project-b','project-c'})
                check('An unrelated destination alias retains its exact classification', native_asset(rows['existing-b'])==native_asset(existing_b))
                check('Cross-project moves clear obsolete scenes and shots while keeping tags and lineage', all(row.get('sceneId') is None and row.get('shotId') is None for row in after_move['assets'] if row['id'] in {'source-a','copy-a'}) and rows['source-a'].get('tags')==['conservar-a'] and rows['source-a'].get('characterIds')==['character-a'])
                before_destination=copy.deepcopy(rows['existing-b'])
                merged=move(file_a()['id'],'project-c','project-b')
                merged_rows={row['id']:row for row in current_index()['assets']}
                check('Moving into an existing identical destination keeps one association', merged['state']=='success' and set(file_a().get('projectIds',[]))=={'project-b'} and sum(not row.get('libraryDeleted') and row.get('projectId')=='project-b' for row in merged_rows.values())==1)
                check('Destination deduplication preserves the existing ID, metadata and original URI', merged_rows['existing-b']==before_destination and original_ids.issubset(merged_rows))
                unassigned=move(file_a()['id'],'__all__','__unassigned__')
                check('Moving from Todas to Sin proyecto applies an explicit null assignment', unassigned['state']=='success' and not file_a().get('projectIds') and merged_rows['existing-b']['uri']==next(row['uri'] for row in current_index()['assets'] if row['id']=='existing-b'))
                back=move(file_a()['id'],'__unassigned__','project-a')
                check('An explicitly unassigned file can move back to a real project', back['state']=='success' and file_a().get('projectIds')==['project-a'])
                check('All-project and unassigned move requests preserve their different source semantics', any('sourceProjectId' not in row and row.get('projectId') is None for row in moves) and any('sourceProjectId' in row and row['sourceProjectId'] is None for row in moves))
                restore('project-a');legacy=next(row for row in items() if 'job:job-legacy:0' in row.get('identityKeys',[]))
                count_before=len(current_index()['assets']);legacy_result=move(legacy['id'],'project-a','project-c')
                legacy_rows=[row for row in current_index()['assets'] if not row.get('libraryDeleted') and row.get('uri')==uris['imageB']]
                check('Moving a legacy-only output materializes one asset without fabricating history', legacy_result['state']=='success' and len(current_index()['assets'])==count_before+1 and len(legacy_rows)==1 and legacy_rows[0].get('projectId')=='project-c')
                before_invalid=(profile/'assistant/index.json').read_bytes()
                invalid=page.evaluate('async id=>{try{await rpc("libraryOperations",{op:"move",entryIds:[id],projectId:"missing-project"});return false;}catch{return true;}}',file_a()['id'])
                check('An invalid move target is rejected before any metadata changes', invalid and before_invalid==(profile/'assistant/index.json').read_bytes())
                def import_file(name,source,project):
                    restore(project);target=profile/name;shutil.copyfile(source,target)
                    page.evaluate("()=>{window.__qaImportDone=false;window.addEventListener('nexus:library-changed',()=>{window.__qaImportDone=true;},{once:true});}")
                    with page.expect_file_chooser() as chooser:page.locator('[data-import-gallery]').click()
                    chooser.value.set_files(str(target));page.wait_for_function('()=>window.__qaImportDone',timeout=60000)
                    page.evaluate('async()=>{await NexusGallery.refresh();}')
                count_before=len(current_index()['assets']);files_before=set(path.name for path in (profile/'media').iterdir() if path.is_file())
                import_file('Mismo nombre.png',sources['imageA'],'project-c')
                check('Reimporting existing bytes under a new name creates no file or asset', len(current_index()['assets'])==count_before and set(path.name for path in (profile/'media').iterdir() if path.is_file())==files_before)
                check('Reimporting into another folder does not silently move the existing file', file_a().get('projectIds')==['project-a'])
                import_file('Mismo nombre.png',sources['imageB'],'project-b')
                different=next(row for row in items() if legacy_rows[0]['id'] in row.get('assetIds',[]))
                check('Different bytes with the same display name never collapse into one file', different['id']!=file_a()['id'] and different.get('projectIds')==['project-c'] and len(items())==initial_count)
                before_retired=copy.deepcopy(next(row for row in current_index()['assets'] if row['id']=='retired'))
                import_file('Vídeo retirado'+sources['video'].suffix,sources['video'],'project-b')
                check('Importing a retired original does not resurrect it or make another copy', next(row for row in current_index()['assets'] if row['id']=='retired')==before_retired and len(current_index()['assets'])==count_before and set(path.name for path in (profile/'media').iterdir() if path.is_file())==files_before)
                document=profile/'nuevo-documento.txt';document.write_text('Documento nuevo de aceptación; debe existir una sola copia tras importarlo dos veces.\n','utf-8')
                before_new=len(current_index()['assets'])
                import_file('Documento nuevo.txt',document,'project-b')
                after_new=current_index();created=[row for row in after_new['assets'] if row['id'] not in {asset['id'] for asset in index['assets']} and row.get('displayName')=='Documento nuevo.txt']
                check('A genuinely new original is registered once in the chosen project', len(after_new['assets'])==before_new+1 and len(created)==1 and created[0].get('projectId')=='project-b')
                new_files=set(path.name for path in (profile/'media').iterdir() if path.is_file())
                import_file('Documento renombrado.txt',document,'project-a')
                check('A second import of that original reuses its file and canonical asset ID', len(current_index()['assets'])==before_new+1 and set(path.name for path in (profile/'media').iterdir() if path.is_file())==new_files and next(row for row in current_index()['assets'] if row['id']==created[0]['id']).get('projectId')=='project-b')
                restore('project-arch');archive_context=page.evaluate('NexusGallery.importContext')
                restore('__all__')
                check('Archived import permission comes only from the explicitly chosen archived folder', archive_context.get('projectId')=='project-arch' and archive_context.get('allowArchivedProject') is True and page.evaluate('NexusGallery.importContext===undefined'))
                archived_source=profile/'referencia-archivo.txt';archived_source.write_text('Referencia nueva e independiente para un proyecto archivado.\n','utf-8')
                import_file('Referencia archivada.txt',archived_source,'project-arch')
                archived_imports=[row for row in current_index()['assets'] if row.get('displayName')=='Referencia archivada.txt']
                check('A real file import into the selected archived project preserves that destination', len(archived_imports)==1 and archived_imports[0].get('projectId')=='project-arch' and any(row.get('allowArchivedProject') is True and row.get('projectId')=='project-arch' for row in imports))
                final=current_index()
                check('No move or duplicate import removes native asset IDs or rewrites history', original_ids.issubset({row['id'] for row in final['assets']}) and list(map(native_generation,final['generations']))==list(map(native_generation,generations_before)) and json.dumps(page.evaluate('state.jobs'),sort_keys=True)==jobs_before)
                check('Existing physical originals remain at the same paths with the same bytes', all((profile/'media'/name).is_file() and digest(profile/'media'/name)==sha for name,sha in original_media.items()))
                check('No partial import files remain after duplicate handling', not list((profile/'media').glob('*.part')) and not list((profile/'media').glob('*.importing')))
                check('The active draft stays unchanged throughout library organization', page.evaluate('state.draft')==state['draft'])
                check('No paid operation or external browser request occurred', not prohibited and not external,{'prohibited':prohibited,'external':external})
                check('Real Edge move and duplicate journeys have no JavaScript errors', not errors,errors)
                browser.close()
        finally:host.terminate();host.wait(timeout=15)
    return {'browser':'Microsoft Edge','moves':moves,'imports':imports,'errors':errors,'external':external,'prohibited':prohibited}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile',type=Path,default=Path(os.environ['LOCALAPPDATA'])/'NexusDesktop')
    parser.add_argument('--output',type=Path,default=ROOT/'outputs/library-move')
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    spec=importlib.util.spec_from_file_location('source_helper',ROOT/'desktop/tools/test-library-workflow.py')
    helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper);_,sources=helper.source_media(args.profile)
    before={name:digest(path) for name,path in sources.items()}
    with tempfile.TemporaryDirectory(prefix='nexus-library-move-') as folder:integration=acceptance(Path(folder),sources,args.output)
    check('The real source originals remain byte-identical', all(digest(path)==before[name] for name,path in sources.items()))
    report={'status':'PASS' if all(row['passed'] for row in CHECKS) else 'FAIL','checks':CHECKS,'integration':integration,'mode':'Only disposable profile writes; real source originals read only.'}
    (args.output/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8')
    print(json.dumps({'status':report['status'],'checks':len(CHECKS),'output':str(args.output)}))
    return 0 if report['status']=='PASS' else 1


if __name__=='__main__':sys.exit(main())
