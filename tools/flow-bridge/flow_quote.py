"""Read the account's current generation price without submitting a prompt."""
import json
import os
import re
import time
from pathlib import Path
from flow_contract import normalize, quote_key
from launcher import ROOT, environment

async def quote(model, aspect, **options):
    from flow_session import ensure
    await ensure()
    try:
        return await _quote(model, aspect, **options)
    except Exception as error:
        # Only this read-only settings operation is retried. A submit/result
        # reservation is never repeated by session renewal.
        if type(error).__name__ not in {'FlowAppError', 'AuthExpiredError'}:
            raise
        await ensure(force=True)
        return await _quote(model, aspect, **options)

async def _quote(model, aspect, **options):
    params=normalize({'model':model,'aspect':aspect,**options})
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
            if model in {'nano2','nano-pro'}:
                request=GenerateImageRequest(prompt='Inspection only',model=Model.from_cli(model),aspect=ImageAspect.from_cli(aspect),count=params.get('count',1),
                    ref_paths=tuple(Path(p) for p in params.get('reference_images',[])))
                await composer.apply_image_settings(page,request)
            elif model in {'veo_fast','veo_quality','veo_lite','omni_flash'}:
                request=GenerateVideoRequest(prompt='Inspection only',model=VideoModel.from_cli(model),mode=Mode(params.get('mode','t2v')),
                    aspect=VideoAspect.from_cli(aspect),count=params.get('count',1),duration=params.get('duration',8),resolution=params.get('resolution'),
                    start_image=Path(params['initial_frame']) if params.get('initial_frame') else None,
                    end_image=Path(params['end_frame']) if params.get('end_frame') else None,
                    reference_images=tuple(Path(p) for p in params.get('reference_images',[])))
                await composer.apply_video_settings(page,request)
                if params.get('voice'):
                    from flow_voices import attach
                    await attach(page,params['voice'].lower())
                    # Without uploading the caller's references, Flow switches
                    # this quote-only composer to its voice ingredient summary
                    # and hides the price pane. Confirm access, never reuse the
                    # pre-voice price as the price of this configuration.
                    cost=None
            else:raise ValueError('Unsupported model; Lite prohibited')
            if cost is None and not params.get('voice'):raise ValueError('No unambiguous account price found')
        finally:await ctx.close()
    result={**params,'credits':cost,'checked_at':time.time(),
        'source':'Flow native voice availability' if params.get('voice') else 'Flow settings',
        'native_price_exposed':cost is not None,'generation_count':params.get('count',1),'new_generation_submitted':False}
    prices=ROOT/'jobs/verified-prices.json'
    previous=json.loads(prices.read_text()) if prices.exists() else {}
    previous[quote_key(params)]=result
    temporary=prices.with_suffix('.tmp');temporary.write_text(json.dumps(previous),encoding='utf-8');temporary.replace(prices)
    return result

async def available_models():
    """Read the owned account's pickers, without typing or submitting a prompt."""
    from flow_session import ensure
    await ensure()
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.image import GenerateImageRequest, Model, Aspect as ImageAspect
    from gflow_cli.api.video import GenerateVideoRequest, VideoModel, Mode, Aspect as VideoAspect
    from gflow_cli.api.transports.migrated_composer import MigratedComposer, MENU_ITEM, OVERLAY, RADIOGROUP, _ligature
    project=json.loads((ROOT/'jobs/quote-project.json').read_text())['project_id']
    if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',project):
        raise ValueError('Invalid owned quote project')
    result={}
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            for kind in ('image','video'):
                page=await ctx.new_page();composer=MigratedComposer()
                await composer.ensure_editor(page,project)
                original_close=composer._close_pane
                async def capture(page,*,strict):
                    pane=page.locator(OVERLAY).filter(has=page.locator(RADIOGROUP)).last
                    await pane.locator('button').filter(has=_ligature(page,'arrow_drop_down')).first.click()
                    items=page.locator(MENU_ITEM)
                    await items.first.wait_for(state='visible',timeout=5000)
                    result[kind]=[{'label':(await item.inner_text()).strip(),'enabled':await item.is_enabled() and await item.get_attribute('aria-disabled')!='true'} for item in await items.all()]
                    await original_close(page,strict=strict)
                composer._close_pane=capture
                if kind=='image':
                    await composer.apply_image_settings(page,GenerateImageRequest(prompt='Inspection only',model=Model.GEM_PIX_2,aspect=ImageAspect.SQUARE,count=1))
                else:
                    await composer.apply_video_settings(page,GenerateVideoRequest(prompt='Inspection only',model=VideoModel.VEO_3_1_FAST,mode=Mode.T2V,aspect=VideoAspect.LANDSCAPE,count=1))
                await page.close()
        finally:await ctx.close()
    return result
