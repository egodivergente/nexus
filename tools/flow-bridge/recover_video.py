"""Recover an exact-prompt clip from an already submitted project; no submit."""
import asyncio
import json
import os
import re
from pathlib import Path
from launcher import ROOT, environment

async def run(args):
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.transports.batchexecute import parse_frames, _walk_lists, _is_record, generation_record
    from gflow_cli.api.transports.migrated_composer import MigratedComposer
    request=json.loads(Path(args.request).read_text(encoding='utf-8-sig'))
    stage='project'
    found={};pending=set()
    async def inspect(response):
        if 'batchexecute' not in response.url or response.status!=200:return
        try:
            for rid,payload in parse_frames(await response.text()):
                for node in _walk_lists(payload):
                    if _is_record(node) and node[1]==args.project and node[5][1]==request['prompt']:
                        record=generation_record(rid,node)
                        if record.is_done:found[record.media_id]=record
        except Exception:pass
    def collect(response):
        task=asyncio.create_task(inspect(response));pending.add(task);task.add_done_callback(pending.discard)
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            page=await ctx.new_page();page.on('response',collect)
            await page.goto('https://flow.google.com/project/'+args.project,wait_until='domcontentloaded',timeout=90000)
            for _ in range(60):
                if found:break
                await page.wait_for_timeout(500)
            if pending:await asyncio.gather(*pending,return_exceptions=True)
            if len(found)!=1:return {'status':'unresolved','matching_videos':len(found)}
            record=next(iter(found.values()))
            try:
                stage='listing_download'
                data=await MigratedComposer._fetch_mp4(page,record)
            except Exception:
                # The listing carries a poster handle on this cohort. Open only
                # this exact-prompt asset and use the player's actual video URL.
                await page.goto(f'https://flow.google.com/project/{args.project}/edit/{record.media_id}',wait_until='domcontentloaded',timeout=90000)
                stage='download_button'
                from gflow_cli.api.transports.ui_automation import _is_allowed_download_host
                downloads=[]
                page.on('download',lambda download:downloads.append(download))
                button=page.get_by_role('button',name=re.compile('Descargar|Download',re.I)).first
                await button.click(timeout=15000)
                await page.wait_for_timeout(1000)
                stage='download_menu'
                if not downloads:
                    menu=page.locator('.cdk-overlay-pane').last
                    (ROOT/'jobs/video-download-menu-private.txt').write_text(await menu.inner_text(),encoding='utf-8')
                    item=menu.get_by_role('menuitem',name=re.compile('720p',re.I)).first
                    await item.click(timeout=10000)
                for _ in range(60):
                    if downloads:break
                    await page.wait_for_timeout(500)
                if not downloads:raise ValueError('Video download failed')
                download=downloads[0]
                if not _is_allowed_download_host(download.url) and not download.url.startswith('blob:https://flow.google.com/'):
                    raise ValueError('Unexpected video origin')
                downloaded=ROOT/'results'/f'{record.media_id}.download.mp4'
                await download.save_as(downloaded)
                data=downloaded.read_bytes();downloaded.unlink()
                if data[4:8]!=b'ftyp':raise ValueError('Player download was not MP4')
            path=ROOT/'results'/f'{record.media_id}.mp4';path.write_bytes(data)
            return {'status':'recovered','path':str(path),'project_id':args.project,
                'media_id':record.media_id,'new_generation_submitted':False,'requested_model':request['model']}
        except Exception:
            import traceback
            (ROOT/'jobs/video-recovery-trace-private.txt').write_text(traceback.format_exc(),encoding='utf-8')
            if 'page' in locals():
                await page.screenshot(path=str(ROOT/'jobs/video-recovery-private.png'))
                (ROOT/'jobs/video-recovery-diagnostic.json').write_text(json.dumps({'stage':stage,'video_count':await page.locator('video').count(),'current_path':__import__('urllib.parse',fromlist=['urlsplit']).urlsplit(page.url).path}),encoding='utf-8')
            raise
        finally:await ctx.close()
