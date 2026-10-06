"""Support the user-created bundled-browser profile in gflow's session readers.

gflow 0.82.1's internal login and migrated-host verifier disagree: the latter
hardcodes the system Chrome channel. Keep its Google server verification intact;
select the exact browser that created this one private profile instead.
"""
from pathlib import Path
import os
import re
from dataclasses import replace

ROOT = Path.home() / ".codex" / "integrations" / "google-flow"
PROFILE = ROOT / "state" / "profile_nexus"
_installed = False

def owned_internal(profile):
    candidate = Path(profile).resolve()
    marker = candidate / ".gflow_browser_strategy"
    return (candidate == PROFILE.resolve() and marker.exists()
            and marker.read_text(encoding="utf-8").strip() == "internal")

def install():
    global _installed
    if _installed:
        return
    from playwright.async_api import BrowserType
    from gflow_cli.auth import cookies
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.transports import migrated_composer as migrated
    from gflow_cli.api.transports import batchexecute
    from image_guard import model_problem

    original_launch = BrowserType.launch_persistent_context
    original_read = cookies._get_chrome_cookies_playwright
    original_problem = migrated._image_body_problem
    original_submit = migrated.MigratedComposer.submit_images_and_observe
    original_video_submit = migrated.MigratedComposer.submit_and_observe
    original_video_settings = migrated.MigratedComposer.apply_video_settings
    original_send_prompt = migrated.MigratedComposer.send_prompt
    original_select = migrated.MigratedComposer._select
    original_record = batchexecute._is_record
    def migrated_record(node):
        if original_record(node):
            return True
        # Measured 2026-10-06: the migrated video record has null instead of CAE.
        # Accept only the same IDs, terminal-state and typed video-model structure.
        try:
            return (len(node) >= 8 and node[3] is None
                and all(isinstance(node[i], str) and batchexecute._UUID_RE.fullmatch(node[i]) for i in (0,1,2))
                and isinstance(node[5][1], str) and node[5][8][0] in (2,3,6)
                and isinstance(node[7][0][12], str) and node[7][0][12].startswith(('veo_3_1_','abra_')))
        except (TypeError, IndexError, KeyError):
            return False
    batchexecute._is_record = migrated_record

    async def launch(browser, user_data_dir, **kwargs):
        if owned_internal(user_data_dir) and kwargs.get("channel") == "chrome":
            kwargs.pop("channel")
        return await original_launch(browser, user_data_dir, **kwargs)

    async def read_owned(profile_dir):
        if not owned_internal(profile_dir):
            return await original_read(profile_dir)
        from gflow_cli.auth.strategies import async_playwright
        async with ProfileLease(profile_dir), async_playwright() as pw:
            ctx = await pw.chromium.launch_persistent_context(
                user_data_dir=str(profile_dir), headless=True,
                args=["--password-store=basic"],
            )
            try:
                jar = await ctx.cookies()
            finally:
                await ctx.close()
        # The same origin filtering and Google SSO test as upstream. No values
        # are logged, exported, or sent to any third-party service.
        return cookies.ChromeCookieSnapshot(
            httpx_cookies=cookies._name_value_cookies(jar, flow_only=True),
            google_session=cookies._has_google_session_cookie(jar),
        )

    BrowserType.launch_persistent_context = launch
    cookies._get_chrome_cookies_playwright = read_owned

    def image_problem(body, reference_ids, model=None):
        if model is not None:
            problem = model_problem(body, model.value)
            if problem:
                return problem
        return original_problem(body, reference_ids, None)

    async def guarded_submit(composer, page, request, *, reference_ids=()):
        # Upstream registers its blocking route only for reference images. T2I
        # must also refuse a mismatched/unknown model BEFORE Google receives it.
        def matches(url):
            return migrated._rpcid(str(url)) == migrated.IMAGE_SUBMIT_RPC

        async def guard(route, raw_request):
            if model_problem(migrated._post_data(raw_request), request.model.value, count=request.count):
                await route.abort()
            else:
                await migrated._guard_image_submit(route, raw_request, reference_ids, request.model)

        await page.route(matches, guard)
        try:
            return await original_submit(composer, page, request, reference_ids=reference_ids)
        finally:
            await page.unroute(matches, guard)

    migrated._image_body_problem = image_problem
    migrated.MigratedComposer.submit_images_and_observe = guarded_submit
    async def select_duration(composer,page,pane,*,axis,lig=None,text=None):
        if axis=='duration' and text and re.fullmatch(r'\d+\s*s',text):
            seconds=re.sub(r'\s','',text)[:-1]
            choices=pane.locator(migrated.RADIO).filter(has_text=re.compile(r'^\s*'+seconds+r'\s*s\s*$'))
            if await choices.count()==1:text=(await choices.first.inner_text()).strip()
        result=await original_select(composer,page,pane,axis=axis,lig=lig,text=text)
        pending=getattr(composer,'_nexus_pending_request',None)
        if axis=='mode' and lig=='videocam' and pending and pending.mode.value=='t2v':
            await original_select(composer,page,pane,axis='submode',lig=migrated.FRAMES_LIGATURE)
        return result
    migrated.MigratedComposer._select = select_duration
    async def pin_duration(composer,page,pane):
        await composer._select(page,pane,axis='duration',text='8s')
    migrated.MigratedComposer._pin_r2v_duration = pin_duration
    async def bound_video_settings(composer,page,request):
        request=replace(request,duration=request.duration or 8,
            resolution=request.resolution or '720p' if request.model and request.model.value=='omni_flash' else request.resolution)
        composer._nexus_pending_request=request
        composer._nexus_video_model = None
        composer._nexus_video_request = None
        close=composer._close_pane
        # This account exposes Omni references at 4/6/8/10 s. The upstream
        # historical 8 s restriction remains intact for Veo. The submit guard
        # still requires every uploaded reference in the actual RPC.
        custom=request.mode.value=='r2v' and request.model and request.model.value=='omni_flash' and request.duration not in (None,8)
        if custom:
            async def set_omni_duration(page,*,strict):
                if strict:
                    pane=page.locator(migrated.OVERLAY).filter(has=page.locator(migrated.RADIOGROUP)).last
                    await composer._select(page,pane,axis='duration',text=f'{request.duration}s')
                await close(page,strict=strict)
            composer._close_pane=set_omni_duration
        try:
            result = await original_video_settings(composer,page,replace(request,duration=None) if custom else request)
        finally:composer._close_pane=close
        composer._nexus_video_model = request.model.value if request.model else None
        composer._nexus_video_request = request
        return result
    migrated.MigratedComposer.apply_video_settings = bound_video_settings
    async def prompt_with_voice(composer,page,prompt,*,append=False):
        bound=getattr(composer,'_nexus_video_request',None)
        voice=bound.reference_audio if bound else None
        if voice:
            if bound.model.value!='omni_flash' or bound.mode.value!='r2v':raise ValueError('Unsupported voice modality')
            from flow_voices import attach
            await attach(page,voice)
        return await original_send_prompt(composer,page,prompt,append=append)
    migrated.MigratedComposer.send_prompt = prompt_with_voice
    from video_guard import interpolation_problem
    migrated._interpolation_body_problem = interpolation_problem
    async def guarded_video_submit(composer, page, **kwargs):
        from video_guard import model_problem as video_problem
        def matches(url):
            return migrated._rpcid(str(url)) in migrated.SUBMIT_RPCS
        async def guard(route, raw_request):
            bound=getattr(composer,'_nexus_video_request',None)
            if video_problem(migrated._post_data(raw_request),getattr(composer,'_nexus_video_model',None),
                mode=bound.mode.value if bound else None,duration=bound.duration if bound else None,
                resolution=bound.resolution if bound else None,aspect=bound.aspect.value if bound else None,start_id=kwargs.get('expect_media_id'),
                end_id=kwargs.get('expect_end_media_id'),reference_ids=kwargs.get('expect_reference_ids',()),
                voice=bound.reference_audio if bound else None,count=bound.count if bound else 1):
                await route.abort()
            else:
                await route.continue_()
        await page.route(matches, guard)
        try:
            return await original_video_submit(composer, page, **kwargs)
        finally:
            await page.unroute(matches, guard)
    migrated.MigratedComposer.submit_and_observe = guarded_video_submit
    _installed = True
