"""Read account-specific video controls without uploading or generating media."""
import argparse
import asyncio
import json
import os
from pathlib import Path
import re
from launcher import ROOT, environment

async def inspect():
    from flow_session import ensure
    await ensure()
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.video import GenerateVideoRequest, VideoModel, Mode, Aspect
    from gflow_cli.api.transports.migrated_composer import MigratedComposer, OVERLAY, RADIOGROUP, RADIO
    project=json.loads((ROOT/'jobs/quote-project.json').read_text())['project_id']
    if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',project):raise ValueError('Invalid owned project')
    result={}
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            for model in ('omni_flash','veo_fast','veo_quality','veo_lite'):
                for mode in ('t2v','i2v','r2v'):
                    if mode=='r2v' and model=='veo_quality':continue
                    page=await ctx.new_page();composer=MigratedComposer()
                    await composer.ensure_editor(page,project)
                    original=composer._close_pane
                    async def capture(page,*,strict):
                        pane=page.locator(OVERLAY).filter(has=page.locator(RADIOGROUP)).last
                        if await pane.count():
                            groups=[]
                            for group in await pane.locator(RADIOGROUP).all():
                                groups.append([{'text':(await radio.inner_text()).strip(),'enabled':await radio.is_enabled() and await radio.get_attribute('aria-disabled')!='true'} for radio in await group.locator(RADIO).all()])
                            result[model+':'+mode]={'groups':groups,'text':await pane.inner_text()}
                        await original(page,strict=strict)
                    composer._close_pane=capture
                    options={'start_image':Path('not-uploaded.png')} if mode=='i2v' else {'reference_images':(Path('not-uploaded.png'),)} if mode=='r2v' else {}
                    request=GenerateVideoRequest(prompt='Settings inspection only',model=VideoModel.from_cli(model),mode=Mode(mode),aspect=Aspect.LANDSCAPE,count=1,**options)
                    await composer.apply_video_settings(page,request)
                    await page.close()
                    print(model+':'+mode,flush=True)
        finally:await ctx.close()
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True)
    args=parser.parse_args()
    Path(args.output).write_text(json.dumps(asyncio.run(inspect()),ensure_ascii=False),encoding='utf-8')
