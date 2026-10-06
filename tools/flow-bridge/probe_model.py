"""Inspect the image submit shape, aborting its network request before Google.

Uses an existing dedicated test project. Persists only a redacted structural tree.
"""
import asyncio
import argparse
import json
import os
import re
from pathlib import Path
from urllib.parse import parse_qs
from launcher import ROOT, environment

def safe_tree(value):
    if isinstance(value, list):
        return [safe_tree(v) for v in value]
    if isinstance(value, dict):
        return {k: safe_tree(v) for k, v in value.items()}
    if isinstance(value, str):
        if value.startswith(("[", "{")):
            try:
                return safe_tree(json.loads(value))
            except ValueError:
                pass
        if re.fullmatch(r"(?:NARWHAL|GEM_PIX_2|IMAGEN_3_5|HARBOR_SEAL|IMAGE_[A-Z_]+|Nano Banana[^\n]{0,25}|ogiZ0b)", value, re.I):
            return value
        return f"[string:{len(value)}]"
    return value

async def main(args):
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.image import GenerateImageRequest, Model, Aspect
    from gflow_cli.api.transports.migrated_composer import MigratedComposer, _rpcid, _post_data, _image_body_problem

    profile = ROOT / "state/profile_nexus"
    evidence = {}
    async with ProfileLease(profile), async_playwright() as pw:
        ctx = await pw.chromium.launch_persistent_context(str(profile), headless=True,
            args=["--password-store=basic"])
        try:
            page = await ctx.new_page()
            async def intercept(route, request):
                if _rpcid(request.url) == "ogiZ0b":
                    try:
                        raw = parse_qs(request.post_data or "").get("f.req", [""])[0]
                        rpc = json.loads(raw)
                        payload = json.loads(rpc[0][0][1])
                        model_slot = payload[1][0][5]
                        if not isinstance(model_slot, str) or not re.fullmatch(r"[a-zA-Z_0-9]{1,40}", model_slot):
                            model_slot = "unexpected"
                        evidence.update(submit=safe_tree(rpc), model_slot=model_slot,
                            patched_model_guard=_image_body_problem(_post_data(request), (), Model.NARWHAL),
                            aborted_before_google=True)
                    finally:
                        await route.abort()
                else:
                    await route.continue_()
            await page.route("**/*batchexecute*", intercept)
            composer = MigratedComposer()
            await composer.ensure_editor(page, args.project)
            request = GenerateImageRequest(prompt="Flow bridge inspection. Do not generate.",
                model=Model.NARWHAL, aspect=Aspect.SQUARE, count=1)
            await composer.apply_image_settings(page, request)
            await composer.send_prompt(page, request.prompt)
            submit = await composer._pre_submit_gate(page)
            await submit.click()
            for _ in range(20):
                if evidence:
                    break
                await page.wait_for_timeout(500)
            if not evidence:
                raise RuntimeError("No aborted request was observed")
            Path(args.output).write_text(json.dumps(evidence, indent=2), encoding="utf-8")
            print(json.dumps(evidence, indent=2))
        finally:
            await ctx.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--output", required=True)
    asyncio.run(main(parser.parse_args()))
