"""Support the user-created bundled-browser profile in gflow's session readers.

gflow 0.82.1's internal login and migrated-host verifier disagree: the latter
hardcodes the system Chrome channel. Keep its Google server verification intact;
select the exact browser that created this one private profile instead.
"""
from pathlib import Path
import os

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
            await migrated._guard_image_submit(route, raw_request, reference_ids, request.model)

        await page.route(matches, guard)
        try:
            return await original_submit(composer, page, request, reference_ids=reference_ids)
        finally:
            await page.unroute(matches, guard)

    migrated._image_body_problem = image_problem
    migrated.MigratedComposer.submit_images_and_observe = guarded_submit
    async def bound_video_settings(composer,page,request):
        composer._nexus_video_model = None
        result = await original_video_settings(composer,page,request)
        composer._nexus_video_model = request.model.value if request.model else None
        return result
    migrated.MigratedComposer.apply_video_settings = bound_video_settings
    async def guarded_video_submit(composer, page, **kwargs):
        from video_guard import model_problem as video_problem
        def matches(url):
            return migrated._rpcid(str(url)) in migrated.SUBMIT_RPCS
        async def guard(route, raw_request):
            if video_problem(migrated._post_data(raw_request),getattr(composer,'_nexus_video_model',None)):
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
