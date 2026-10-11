"""Read video settings and abort a submit to inspect its contract without spending."""
import argparse
import asyncio
import json
import os
import re
from pathlib import Path
from urllib.parse import parse_qs
from launcher import ROOT, environment

def redact(value):
    if isinstance(value, list):
        return [redact(v) for v in value]
    if isinstance(value, str):
        if value.startswith(("[", "{")):
            try:
                return redact(json.loads(value))
            except ValueError:
                pass
        if re.fullmatch(r"(?:veo_[a-z0-9_]+|omni_[a-z0-9_]+|YhhmEf|eb1hJf|MZZa6b|nprQif)", value):
            return value
        return f"[string:{len(value)}]"
    return value

async def run(args):
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.video import GenerateVideoRequest, VideoModel, Mode, Aspect
    from gflow_cli.api.transports.migrated_composer import MigratedComposer, _rpcid, SUBMIT_RPCS
    evidence = {}
    async with ProfileLease(ROOT / "state/profile_nexus"), async_playwright() as pw:
        ctx = await pw.chromium.launch_persistent_context(str(ROOT / "state/profile_nexus"),
            headless=True, args=["--password-store=basic"])
        try:
            page = await ctx.new_page()
            composer = MigratedComposer()
            original_close = composer._close_pane
            async def close_with_cost(page, *, strict):
                text = await page.locator("body").inner_text()
                evidence["cost_labels"] = [line.strip()[:120] for line in text.splitlines()
                    if re.search(r"(?:\d+\s*(?:credits|créditos)|(?:credits|créditos)\s*[:·]?\s*\d+)", line, re.I)]
                await original_close(page, strict=strict)
            composer._close_pane = close_with_cost
            await composer.ensure_editor(page, args.project)
            request = GenerateVideoRequest(prompt="An amber glass cube slowly rotates on an ivory pedestal.",
                model=VideoModel.from_cli(args.model), mode=Mode.T2V, aspect=Aspect.LANDSCAPE, count=1)
            await composer.apply_video_settings(page, request)
            async def intercept(route, raw):
                if _rpcid(raw.url) in SUBMIT_RPCS:
                    try:
                        body = parse_qs(raw.post_data or "").get("f.req", [""])[0]
                        wire=json.loads(body)
                        payload=json.loads(wire[0][0][1])
                        model_slot=payload[0][0][1]
                        if not isinstance(model_slot,str) or not re.fullmatch(r'[a-zA-Z0-9_]{1,50}',model_slot):
                            raise ValueError('Unexpected model field')
                        evidence.update(submit=redact(wire),model_slot=model_slot,aborted_before_google=True)
                    finally:
                        await route.abort()
                else:
                    await route.continue_()
            await page.route("**/*batchexecute*", intercept)
            await composer.send_prompt(page, request.prompt)
            await (await composer._pre_submit_gate(page)).click()
            for _ in range(20):
                if evidence.get("submit"):
                    break
                await page.wait_for_timeout(500)
            if not evidence.get("submit"):
                raise RuntimeError("No aborted video request observed")
            Path(args.output).write_text(json.dumps(evidence, indent=2), encoding="utf-8")
            print(json.dumps(evidence, indent=2))
        finally:
            await ctx.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--model", choices=['veo_fast','veo_quality','veo_lite','omni_flash'], default='veo_fast')
    asyncio.run(run(parser.parse_args()))
