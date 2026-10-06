"""Support the user-created bundled-browser profile in gflow's session readers.

gflow 0.82.1's internal login and migrated-host verifier disagree: the latter
hardcodes the system Chrome channel. Keep its Google server verification intact;
select the exact browser that created this one private profile instead.
"""
from pathlib import Path
import os

ROOT = Path.home() / ".codex" / "integrations" / "google-flow"
PROFILE = ROOT / "state" / "profile_nexus"

def owned_internal(profile):
    candidate = Path(profile).resolve()
    marker = candidate / ".gflow_browser_strategy"
    return (candidate == PROFILE.resolve() and marker.exists()
            and marker.read_text(encoding="utf-8").strip() == "internal")

def install():
    from playwright.async_api import BrowserType
    from gflow_cli.auth import cookies
    from gflow_cli.profile_lease import ProfileLease

    original_launch = BrowserType.launch_persistent_context
    original_read = cookies._get_chrome_cookies_playwright

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
