"""Edit an owned Flow clip through the native Omni editor, keeping its original."""
import asyncio
import json
import re
from urllib.parse import parse_qs
from launcher import ROOT

EDIT_RPC = 'jIps6'
UUID=re.compile(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\Z')

def source_asset(request_id):
    from bridge_tools import database,verified_file,IDENTIFIER
    if not isinstance(request_id,str) or not IDENTIFIER.fullmatch(request_id):raise ValueError('Invalid original job')
    with database() as db:
        row=db.execute('SELECT result_json FROM jobs WHERE request_id=?',(request_id,)).fetchone()
    result=json.loads(row['result_json'] or '{}') if row else {}
    if result.get('status')!='completed' or not result.get('path'):raise ValueError('Completed original required')
    proof=verified_file(result)
    if proof['format']!='MP4':raise ValueError('Original must be a video')
    project=result.get('flow_project_id') or result.get('project_id')
    media=result.get('flow_media_id') or result.get('media_id')
    if not all(isinstance(v,str) and UUID.fullmatch(v) for v in (project,media)):raise ValueError('Authoritative Flow ids required')
    return {'source_request_id':request_id,'source_sha256':proof['sha256'],'project_id':project,'media_id':media,
        'kind':'VIDEO','original_dimensions':proof['dimensions']}

async def download(source,request_id):
    """Export only the exact accepted new clip at its native 720p resolution."""
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.transports.ui_automation import _is_allowed_download_host
    from bridge_tools import verified_file
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            page=await ctx.new_page()
            await page.goto(f"https://flow.google.com/project/{source['project_id']}/edit/{source['media_id']}",wait_until='domcontentloaded',timeout=90000)
            received=[];page.on('download',lambda value:received.append(value))
            await page.get_by_role('button',name=re.compile('Descargar|Download',re.I)).first.click(timeout=30000)
            item=page.locator('.cdk-overlay-pane').last.get_by_role('menuitem').filter(has_text=re.compile(r'^\s*720p\b'))
            label=await item.inner_text(timeout=10000)
            if re.search(r'puntos|points|credits|cr[eé]ditos',label,re.I):raise ValueError('Native original export unexpectedly shows a charge')
            await item.click(timeout=10000)
            for _ in range(60):
                if received:break
                await page.wait_for_timeout(500)
            if len(received)!=1:raise ValueError('Exact video export unavailable')
            asset=received[0]
            if not _is_allowed_download_host(asset.url) and not asset.url.startswith('blob:https://flow.google.com/'):
                raise ValueError('Unexpected export origin')
            path=ROOT/'results'/f'{request_id}.mp4';path.parent.mkdir(parents=True,exist_ok=True)
            await asset.save_as(path)
            return verified_file({'status':'completed','request_id':request_id,**source,'path':str(path)})
        finally:await ctx.close()

def submission_problem(body, source, prompt):
    """The source is a structural video slot, never an identifier in prompt text."""
    try:
        wire=json.loads(parse_qs(body).get('f.req',[''])[0])
        if len(wire)!=1 or len(wire[0])!=1:raise ValueError()
        rpc=wire[0][0]
        rows=json.loads(rpc[1])[0]
        if rpc[0]!=EDIT_RPC or len(rows)!=1:raise ValueError()
        row=rows[0];clip=row[0]
        if row[2]!='abra_edit' or clip[1]!=source['workflow_id'] or row[4][1]!=source['media_id']:raise ValueError()
        # Measured native editor: frame boundaries are at 24 fps. This adapter
        # edits the entire existing clip; it never silently trims a longer clip.
        if clip[2]!=0 or type(clip[3]) is not int or not 0<clip[3]<=240:raise ValueError()
        if clip[3]!=source['edit_frames']:raise ValueError()
        if row[3]!=source['aspect_code']:raise ValueError()
        if row[1]!=[None,None,[[[prompt]]]]:raise ValueError()
    except (TypeError,ValueError,IndexError,KeyError):
        return 'The requested Omni edit and original clip were not bound. No edit sent.'
    return None

async def open_editor(page, source):
    from gflow_cli.api.transports.batchexecute import parse_frames,_walk_lists,_is_record
    workflows=set();pending=set()
    async def inspect(response):
        if 'batchexecute' not in response.url or response.status!=200:return
        try:
            for _,payload in parse_frames(await response.text()):
                for node in _walk_lists(payload):
                    if _is_record(node) and node[1]==source['project_id'] and node[2]==source['media_id']:
                        workflows.add(node[0])
        except (ValueError,TypeError,IndexError):pass
    def collect(response):
        task=asyncio.create_task(inspect(response));pending.add(task);task.add_done_callback(pending.discard)
    page.on('response',collect)
    try:
        await page.goto(f"https://flow.google.com/project/{source['project_id']}/edit/{source['media_id']}",
            wait_until='domcontentloaded',timeout=90000)
        await page.get_by_role('button',name=re.compile('Descargar|Download',re.I)).first.wait_for(timeout=30000)
        if pending:await asyncio.gather(*pending,return_exceptions=True)
        for _ in range(20):
            if len(workflows)==1:break
            await page.wait_for_timeout(250)
        if len(workflows)!=1:raise ValueError('Flow did not bind the original media to a unique workflow')
        source={**source,'workflow_id':next(iter(workflows))}
    finally:
        page.remove_listener('response',collect)
        if pending:await asyncio.gather(*pending,return_exceptions=True)
    chip=page.locator('.model-chip').filter(has_text=re.compile(r'Omni\s+1\.1\s+Flash'))
    await chip.wait_for(timeout=15000)
    # The native player provides whole-clip duration in its accessible text.
    # Wait for loading, rather than interpreting its temporary 00:00:00 as free/empty.
    for _ in range(30):
        text=await page.locator('body').inner_text()
        match=re.search(r'(?:Duraci[oó]n total|Total duration):\s*(\d{2}):(\d{2}):(\d{2})',text,re.I)
        if match:
            minutes,seconds,centiseconds=map(int,match.groups())
            length=minutes*60+seconds+centiseconds/100
            if length>0:break
        await page.wait_for_timeout(250)
    else:raise ValueError('Flow did not confirm the source clip duration')
    if length>10:raise ValueError('This adapter edits complete clips up to 10 seconds; trim longer clips first')
    width,height=source['original_dimensions']
    if width==height:raise ValueError('Unsupported source aspect')
    source={**source,'edit_frames':round(length*24),'duration':length,'aspect_code':2 if width>height else 1}
    return source

async def operate(source, prompt=None, request_id=None):
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.transports.batchexecute import parse_frames,_walk_lists,_is_record,generation_record
    from gflow_cli.api.transports.migrated_composer import SUBMIT_RPCS,IMAGE_SUBMIT_RPC
    from bridge_tools import save,verified_file
    records={};pending=set();sent=False
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            page=await ctx.new_page()
            # Blocking applies before navigation too. Only the exact edit RPC
            # may pass after its durable reservation and native contract check.
            async def guard(route,raw):
                nonlocal sent
                body=(raw.post_data_buffer or b'').decode('utf-8','ignore')
                ids=parse_qs(__import__('urllib.parse',fromlist=['urlsplit']).urlsplit(raw.url).query).get('rpcids',[])
                if 'batchexecute' in raw.url and (any(r in body for r in (*SUBMIT_RPCS,IMAGE_SUBMIT_RPC,EDIT_RPC)) or any(r in (*SUBMIT_RPCS,IMAGE_SUBMIT_RPC,EDIT_RPC) for r in ids)):
                    if not prompt or sent or submission_problem(body,source,prompt):await route.abort();return
                    sent=True
                    save(request_id,{'status':'processing','request_id':request_id,**source,'operation':'edit_video',
                        'requested_model':'omni_flash','prompt':prompt,'new_generation_submitted':True})
                await route.continue_()
            await page.route('**/*',guard)
            source=await open_editor(page,source)
            if prompt is None:
                return {'status':'available',**source,'model':'omni_flash','native_price_exposed':False,
                    'new_generation_submitted':False}
            async def inspect(response):
                if 'batchexecute' not in response.url or response.status!=200:return
                try:
                    for rid,payload in parse_frames(await response.text()):
                        for node in _walk_lists(payload):
                            if _is_record(node) and node[1]==source['project_id'] and node[5][1]==prompt:
                                record=generation_record(rid,node)
                                if record.media_id!=source['media_id'] and node[7][0][12]=='abra_edit':
                                    records[record.media_id]=record
                                    save(request_id,{'status':'processing','request_id':request_id,**source,
                                        'operation':'edit_video','requested_model':'omni_flash','prompt':prompt,
                                        'edited_media_id':record.media_id,'new_generation_submitted':True})
                except (ValueError,TypeError,IndexError):pass
            def collect(response):
                task=asyncio.create_task(inspect(response));pending.add(task);task.add_done_callback(pending.discard)
            page.on('response',collect)
            await page.locator('[contenteditable=true]').first.fill(prompt)
            await page.locator('flow-generate-icon-button button').click(timeout=15000)
            for _ in range(840):
                if len(records)==1 and next(iter(records.values())).is_done:break
                if len(records)>1:raise ValueError('Ambiguous edited outputs')
                await page.wait_for_timeout(500)
            if pending:await asyncio.gather(*pending,return_exceptions=True)
            if not sent or len(records)!=1 or not next(iter(records.values())).is_done:
                raise ValueError('Native edit completion needs reconciliation; do not resubmit')
            record=next(iter(records.values()))
            if record.media_id==source['media_id']:raise ValueError('Original cannot be overwritten')
            # Existing downloader exports only the newly observed exact asset.
            await page.unroute('**/*',guard)
            await ctx.close()
            ctx=None
        finally:
            if pending:await asyncio.gather(*pending,return_exceptions=True)
            if ctx is not None:await ctx.close()
    # Release the profile lease before the downloader acquires it.
    result=await download({**source,'media_id':record.media_id,'kind':'VIDEO'},request_id)
    result.update(operation='edit_video',source_media_id=source['media_id'],requested_model='omni_flash',
        model_confirmed=True,new_generation_submitted=True)
    return verified_file(result)

async def recover(checkpoint):
    """Download the exact accepted output; never fill or submit an edit prompt."""
    from bridge_tools import verified_file
    source=source_asset(checkpoint['source_request_id'])
    media=checkpoint.get('edited_media_id')
    if not isinstance(media,str) or not UUID.fullmatch(media) or media==source['media_id']:
        raise ValueError('No authoritative edited asset; no new edit will be submitted')
    if source['source_sha256']!=checkpoint['source_sha256']:raise ValueError('The original changed')
    result=await download({**source,'media_id':media,'kind':'VIDEO'},checkpoint['request_id'])
    result.update(operation='edit_video',source_media_id=source['media_id'],requested_model='omni_flash',
        model_confirmed=True,new_generation_submitted=True)
    return verified_file(result)
