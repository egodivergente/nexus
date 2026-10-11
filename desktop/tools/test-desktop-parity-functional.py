"""Functional browser/native roundtrip in an isolated profile; optional real Sol read."""
from pathlib import Path
import argparse, json, os, shutil, subprocess, sys, time, uuid
from playwright.sync_api import sync_playwright
parser=argparse.ArgumentParser();parser.add_argument('--sol',action='store_true');args=parser.parse_args()
root=Path(__file__).resolve().parents[2]
out=root/'outputs/desktop-agent-parity/functional-qa';out.mkdir(parents=True,exist_ok=True)
data=out/(('sol-profile-' if args.sol else 'data-')+uuid.uuid4().hex[:8]);(data/'assistant').mkdir(parents=True,exist_ok=True);(data/'media').mkdir(exist_ok=True)
settings={'backend':'direct','mode':'AGENT','modelId':'us.anthropic.claude-sonnet-4-6'}
if args.sol:
    stage=root/'outputs/desktop-agent-parity/migration/stage/assistant'
    settings=json.loads((stage/'settings.json').read_text('utf-8'))
    shutil.copy2(stage/'credentials.dpapi',data/'assistant/credentials.dpapi')
(data/'assistant/settings.json').write_text(json.dumps(settings),encoding='utf-8')
(data/'state.json').write_text(json.dumps({'jobs':[],'draft':{'prompt':'','values':{},'media':[]},'interfaceTheme':'aurora'}),encoding='utf-8')
sys.path.insert(0,str(root/'desktop'))
from native_operations import NativeOperations
native=NativeOperations(data,None,lambda:{},lambda s:None,None,lambda:{})
video=native.new_path('.mp4');text=native.new_path('.srt')
native.encode(['-f','lavfi','-i','color=c=blue:s=320x240:r=24:d=2','-c:v','libx264',video])
text.write_text('1\n00:00:00,000 --> 00:00:01,000\nTexto QA Studio\n',encoding='utf-8');native.executor.shutdown()
process=subprocess.Popen([sys.executable,str(root/'desktop/local_host.py'),'--headless','--offline-qa','--data-dir',str(data)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
report={}
try:
    url=json.loads(process.stdout.readline())['url']
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='msedge',headless=True)
        page=browser.new_page(viewport={'width':1366,'height':768});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto(url);page.wait_for_function('()=>Boolean(window.NexusAgentParity)&&Boolean(initial)',timeout=45000)
        page.wait_for_function('()=>!Agent.busy',timeout=30000)
        page.evaluate("async()=>{await agentCall('snapshot');await agentCall('createProject',{name:'Studio · Prueba local'});}")
        items=[{'id':uuid.uuid4().hex,'uri':'/media/'+path.name,'type':kind,'role':'REFERENCE','source':'IMPORTED','displayName':name,'mimeType':mime,'projectId':page.evaluate('Agent.snapshot.projectId'),'createdAt':int(time.time()*1000)} for path,kind,name,mime in [(video,'VIDEO','Vídeo QA','video/mp4'),(text,'OTHER','Texto QA','text/plain')]]
        page.evaluate("async items=>{await agentCall('importAssets',{assets:items});navigate('assistant');}",items)
        page.evaluate('id=>NexusAgentParity.editor(id)',items[0]['id']);page.wait_for_selector('#parity-editor');page.locator('[name=action]').select_option('trim');page.locator('[name=end]').fill('1');page.locator('#parity-editor button.primary').click()
        page.wait_for_function("()=>state.jobs.some(j=>j.localEdit)",timeout=40000)
        assert page.evaluate("Agent.snapshot.index.assets.some(a=>a.parentAssetId)")
        report['browser_edit_roundtrip']=True
        page.locator('#dialog .close').click();page.evaluate('NexusAgentParity.project()');page.wait_for_selector('[data-parity=project-library]');page.locator('[data-parity=project-library]').click();page.wait_for_selector('#parity-project-files')
        page.locator('#parity-project-files [name=assetIds]').first.check()
        with page.expect_download() as download:page.locator('#parity-project-files [value=export]').click()
        assert download.value.suggested_filename.endswith('.zip');report['project_zip']=True
        page.locator('#parity-project-files [data-parity-asset]').nth(1).click();page.wait_for_selector('#parity-document');page.wait_for_function("()=>document.querySelector('#parity-document').textContent.includes('Texto QA Studio')");report['document_viewer']=True
        page.locator('#dialog .close').click()
        page.evaluate("async()=>{await rpc('archiveChat',{items:[{role:'user',text:'Conversación archivada QA'},{role:'assistant',text:'Memoria conservada.'}]});navigate('assistant');}")
        page.locator('[data-parity=conversations]').click();page.wait_for_selector('[data-parity-chat]');page.locator('[data-parity-chat]').first.click();page.wait_for_function("()=>Agent.items.some(i=>i.text==='Memoria conservada.')");report['conversation_restore']=True
        page.wait_for_function("()=>!Agent.busy&&!document.querySelector('#dialog').open")
        page.evaluate("()=>{const original=state.jobs.find(j=>j.localEdit);state.jobs.push({...original,id:'qa-second-result'});Studio.tab='results';navigate('library');}")
        page.locator('[data-gallery-select]').first.click();page.locator('[data-gallery-select]').nth(1).click();page.locator('[data-gallery=compare]').click()
        assert page.locator('.pc-comparison-item').count()==2;report['result_comparison']=True
        page.locator('#dialog .close').click()
        page.evaluate("()=>{const m=models.find(m=>m.modalities.includes('VIDEO'));state.agentConversation={...state.agentConversation,resultPlans:[{state:'PREPARED',plan:{id:'archived-qa-plan',ref:{providerId:m.providerId,modelId:m.id},modelName:m.displayName,modality:'VIDEO',prompt:'Archivado QA',params:{},effectiveParams:{},media:[],warnings:[],createdAt:1}}]};navigate('assistant');}")
        assert page.locator('[data-agent-generate="archived-qa-plan"]').is_disabled()
        page.locator('[data-agent-canvas="archived-qa-plan"]').click()
        page.wait_for_function("()=>view==='canvas'&&!configuring")
        assert page.evaluate("state.draft.prompt==='Archivado QA'");report['archived_plan_reuse']=True
        page.evaluate("navigate('assistant')");page.locator('[data-agent-discard="archived-qa-plan"]').click()
        page.wait_for_function("()=>!document.querySelector('[data-agent-card=\"plan:archived-qa-plan\"]')")
        report['archived_plan_discard']=True
        page.evaluate("uri=>{state.jobs.push({id:'qa-text-history',modality:'CHAT',providerId:'local_media',modelId:'whisper-local',prompt:'Texto QA',status:'COMPLETED',outputs:[uri],startedAt:1});navigate('history');studioDetails('qa-text-history');}",'/media/'+text.name)
        page.wait_for_function("()=>document.querySelector('#gallery-document')?.textContent.includes('Texto QA Studio')")
        page.locator('[data-gallery=close-viewer]').click();report['text_history_viewer']=True
        for theme in ['aurora','oled','quartz','ocean','forest','light']:
            page.evaluate("theme=>{state.interfaceTheme=theme;NexusThemes.apply();navigate('assistant');}",theme);page.wait_for_timeout(150);page.screenshot(path=str(out/(theme+'.png')))
        if args.sol:
            result=page.evaluate("async()=>await agentCall('send',{text:'Usa únicamente get_nexus_settings una vez y dime brevemente cuál es el modelo configurado. No ejecutes generaciones, ediciones ni otras herramientas.',attachments:[]})")
            assert 'get_nexus_settings' in result['tools'],result
            assert not page.evaluate('Agent.snapshot.offline')
            report['real_sol_tool_turn']={'model':settings['modelId'],'tools':result['tools'],'stop':result['stop']}
            from PIL import Image, ImageDraw
            from reportlab.pdfgen import canvas
            picture=data/'media'/('a'*32+'.png');im=Image.new('RGB',(400,250),'red');ImageDraw.Draw(im).text((25,25),'NEXUS 42',fill='white');im.save(picture)
            pdf=data/'media'/('b'*32+'.pdf');document=canvas.Canvas(str(pdf));document.drawString(70,700,'Codigo de verificacion: LUNA 73');document.save()
            attachments=[{'id':uuid.uuid4().hex,'uri':'/media/'+path.name,'mimeType':mime,'name':name} for path,mime,name in [(picture,'image/png','Prueba imagen'),(pdf,'application/pdf','Prueba PDF'),(video,'video/mp4','Prueba video')]]
            vision=page.evaluate("async attachments=>await agentCall('send',{text:'Lee los tres adjuntos sin usar herramientas. Dime el color del fondo de la imagen, el codigo del PDF y el color del video.',attachments})",attachments)
            answer=vision['text'].lower()
            assert all(term in answer for term in ['rojo','luna','73','azul']),vision['text']
            report['real_sol_image_pdf_video']={'recognized':['red image','LUNA 73 PDF','blue sampled video'],'stop':vision['stop']}
        assert not errors,errors
        report.update(errors=errors,tools=page.evaluate('Agent.snapshot.tools'),paidGenerations=0)
        (out/('sol-report.json' if args.sol else 'report.json')).write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
        browser.close()
finally:
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill()
