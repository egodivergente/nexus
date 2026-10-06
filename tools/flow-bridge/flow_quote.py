"""Read the account's current generation price without submitting a prompt."""
import json
import os
import re
import time
from launcher import ROOT, environment

async def quote(model, aspect):
    os.environ.update(environment())
    from runtime_compat import install
    install()
    config=ROOT/'jobs/quote-project.json'
    if not config.exists():
        from gflow_cli.api.client import FlowApiClient
        async with FlowApiClient(profile_dir=ROOT/'state/profile_nexus') as client:
            created=await client.create_project(title='Nexus Flow price inspection')
        config.parent.mkdir(parents=True,exist_ok=True)
        config.write_text(json.dumps({'project_id':created.project_id}),encoding='utf-8')
    project=json.loads(config.read_text())['project_id']
    if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',project):
        raise ValueError('Invalid owned quote project')
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.image import GenerateImageRequest, Model, Aspect as ImageAspect
    from gflow_cli.api.video import GenerateVideoRequest, VideoModel, Mode, Aspect as VideoAspect
    from gflow_cli.api.transports.migrated_composer import MigratedComposer, OVERLAY, RADIOGROUP
    cost=None
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            page=await ctx.new_page();composer=MigratedComposer()
            await composer.ensure_editor(page,project)
            close=composer._close_pane
            async def read_price(page,*,strict):
                nonlocal cost
                pane=page.locator(OVERLAY).filter(has=page.locator(RADIOGROUP)).last
                text=await pane.inner_text()
                matches=re.findall(r'(?:La generación consumirá|Generation will cost|This generation will use)\s+(\d+)\s+(?:puntos|credits)',text,re.I)
                if len(matches)==1:cost=int(matches[0])
                await close(page,strict=strict)
            composer._close_pane=read_price
            if model=='nano2':
                request=GenerateImageRequest(prompt='Inspection only',model=Model.NARWHAL,aspect=ImageAspect.from_cli(aspect),count=1)
                await composer.apply_image_settings(page,request)
            elif model=='veo_fast':
                request=GenerateVideoRequest(prompt='Inspection only',model=VideoModel.VEO_3_1_FAST,mode=Mode.T2V,aspect=VideoAspect.from_cli(aspect),count=1)
                await composer.apply_video_settings(page,request)
            else:raise ValueError('Unsupported model; Lite prohibited')
            if cost is None:raise ValueError('No unambiguous account price found')
        finally:await ctx.close()
    result={'model':model,'aspect':aspect,'credits':cost,'checked_at':time.time(),
        'source':'Flow settings','generation_count':1,'new_generation_submitted':False}
    prices=ROOT/'jobs/verified-prices.json'
    previous=json.loads(prices.read_text()) if prices.exists() else {}
    previous[model]=result
    temporary=prices.with_suffix('.tmp');temporary.write_text(json.dumps(previous),encoding='utf-8');temporary.replace(prices)
    return result
