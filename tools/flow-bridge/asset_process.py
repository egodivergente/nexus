"""Download/upscale an owned Flow asset through its native download menu."""
import re
from launcher import ROOT

UUID = re.compile(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\Z')
CHOICES = {'IMAGE': {'original':'1K', '2k':'2K', '4k':'4K'},
           'VIDEO': {'original':'720p', '1080p':'1080p', '4k':'4K', 'gif':'270p'}}

def source_asset(request_id):
    import json
    from bridge_tools import database, verified_file, IDENTIFIER
    if not isinstance(request_id, str) or not IDENTIFIER.fullmatch(request_id):
        raise ValueError('Invalid source request_id')
    with database() as db:
        row = db.execute('SELECT result_json FROM jobs WHERE request_id=?', (request_id,)).fetchone()
    if not row or not row['result_json']:
        raise ValueError('Source job has no completed asset')
    result = json.loads(row['result_json'])
    if result.get('status') != 'completed' or not result.get('path'):
        raise ValueError('Source job has no completed asset')
    proof = verified_file(result)
    project = result.get('flow_project_id') or result.get('project_id')
    media = result.get('flow_media_id') or result.get('media_id')
    if not all(isinstance(x, str) and UUID.fullmatch(x) for x in (project, media)):
        raise ValueError('Source job lacks authoritative Flow asset identifiers')
    # Always operate on the original Flow asset. An upscale output retains these ids.
    return {'source_request_id':request_id, 'project_id':project, 'media_id':media,
            'kind':result.get('kind') or ('VIDEO' if proof['format']=='MP4' else 'IMAGE'),
            'original_dimensions':result.get('original_dimensions') or proof['dimensions'],
            'source_sha256':proof['sha256']}

def menu_option(kind, resolution, label, disabled=False):
    expected = CHOICES.get(kind, {}).get(resolution)
    if disabled or not expected or not re.match(r'^'+re.escape(expected)+r'\s', label.strip()+' '):
        raise ValueError('Requested download option is unavailable')
    if resolution == 'gif' and not re.search(r'GIF',label,re.I):
        raise ValueError('Requested GIF option is unavailable')
    if re.search(r'puntos|points|credits|créditos',label,re.I):
        match = re.search(r'(\d+)\s*(?:puntos|points|credits|créditos)',label,re.I)
        if not match:
            raise ValueError('Download price cannot be read')
        credits = int(match[1])
    else:
        credits = 0
    return {'resolution':resolution, 'credits':credits, 'label':label.strip(),
            'price_basis':'Flow download menu; no charge indicated' if credits==0 else 'Flow download menu'}

def check_dimensions(kind, resolution, proof):
    width,height = proof['dimensions']
    if kind=='IMAGE' and resolution in {'2k','4k'}:
        # Flow keeps the original aspect. Non-square 2K/4K assets need not have
        # an exact 2048/4096 long edge; compare to their recorded original size.
        original=proof.get('original_dimensions')
        factor={'2k':2,'4k':4}[resolution]
        matches=all(abs(value-base*factor)<=1 for value,base in zip((width,height),original)) if original else max(width,height)==1024*factor
        if not matches:
            raise ValueError('Downloaded image resolution does not match the request')
    if kind=='VIDEO' and resolution in {'1080p','4k'}:
        if min(width,height) != {'1080p':1080,'4k':2160}[resolution]:
            raise ValueError('Downloaded video resolution does not match the request')
    if resolution=='gif' and proof['format']!='GIF':
        raise ValueError('Downloaded export is not GIF')
    if resolution=='gif' and (min(width,height)!=270 or not proof.get('animated')):
        raise ValueError('Downloaded GIF is not the requested animated 270p export')

async def operate(source, resolution=None, expected_credits=None, request_id=None):
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.transports.ui_automation import _is_allowed_download_host
    from gflow_cli.api.transports.migrated_composer import SUBMIT_RPCS, IMAGE_SUBMIT_RPC
    from bridge_tools import save, verified_file
    async with ProfileLease(ROOT/'state/profile_nexus'), async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            page=await ctx.new_page()
            # Postprocessing must never submit a new prompt/model, including Lite.
            async def no_generation(route):
                body=route.request.post_data or ''
                if any(rpc in body for rpc in (*SUBMIT_RPCS,IMAGE_SUBMIT_RPC)):
                    await route.abort();return
                await route.continue_()
            await page.route('**/*batchexecute*',no_generation)
            await page.goto(f"https://flow.google.com/project/{source['project_id']}/edit/{source['media_id']}",wait_until='domcontentloaded',timeout=90000)
            await page.get_by_role('button',name=re.compile('Descargar|Download',re.I)).first.click(timeout=30000)
            menu=page.locator('.cdk-overlay-pane').last
            await menu.get_by_role('menuitem').first.wait_for(timeout=15000)
            options={}
            items=menu.get_by_role('menuitem')
            for index in range(await items.count()):
                item=items.nth(index);label=await item.inner_text()
                disabled=await item.is_disabled() or await item.get_attribute('aria-disabled')=='true'
                for key in CHOICES[source['kind']]:
                    try:options[key]=menu_option(source['kind'],key,label,disabled)
                    except ValueError:pass
            if resolution is None:
                return {'status':'quoted',**source,'options':list(options.values()),'new_generation_submitted':False}
            option=options.get(resolution)
            if not option or type(expected_credits) is not int or expected_credits!=option['credits']:
                raise ValueError('Download option or approved credit amount changed')
            # Persist before a potentially credit-consuming click. Unknown outcomes
            # stay reserved; reconnecting never clicks the upscale a second time.
            save(request_id,{'status':'processing','request_id':request_id,**source,**option})
            downloads=[]
            page.on('download',lambda download:downloads.append(download))
            await items.filter(has_text=re.compile(r'^\s*'+re.escape(CHOICES[source['kind']][resolution])+r'\s')).first.click(timeout=15000)
            for _ in range(840):
                if downloads:break
                await page.wait_for_timeout(500)
            if not downloads:
                raise ValueError('Flow did not deliver the requested download')
            download=downloads[0]
            if not _is_allowed_download_host(download.url) and not download.url.startswith('blob:https://flow.google.com/'):
                raise ValueError('Unexpected download origin')
            suffix='.mp4' if source['kind']=='VIDEO' and resolution!='gif' else '.gif' if resolution=='gif' else '.png'
            path=ROOT/'results'/f'{request_id}{suffix}'
            path.parent.mkdir(parents=True,exist_ok=True)
            await download.save_as(path)
            if suffix=='.png':
                from PIL import Image
                with Image.open(path) as image:actual_suffix={'JPEG':'.jpg','PNG':'.png','WEBP':'.webp'}.get(image.format)
                if actual_suffix is None:raise ValueError('Unexpected image format')
                actual=path.with_suffix(actual_suffix)
                if actual!=path:path.replace(actual);path=actual
            save(request_id,{'status':'downloaded','path':str(path),'request_id':request_id,
                **source,**option,'new_generation_submitted':False,
                'operation':'export' if resolution in {'original','gif'} else 'upscale'})
            proof=verified_file({'status':'completed','path':str(path),'request_id':request_id,
                **source,**option,'new_generation_submitted':False,'operation':'export' if resolution in {'original','gif'} else 'upscale'})
            check_dimensions(source['kind'],resolution,proof)
            return proof
        finally:await ctx.close()
