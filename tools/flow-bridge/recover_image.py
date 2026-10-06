"""Read an existing Flow project and recover only an exact-prompt image.

This command never clicks Generate or submits a generation RPC. Signed media URLs
stay in memory; the evidence contains ids and verified file facts only.
"""
import argparse
import asyncio
import hashlib
import io
import json
import os
from pathlib import Path
from urllib.parse import urlsplit, urljoin

from launcher import ROOT, environment


async def run(args):
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.transports.batchexecute import parse_frames, image_records
    from gflow_cli.api.transports.ui_automation import _is_allowed_download_host
    from gflow_cli.exceptions import WireFormatError
    from PIL import Image

    profile = ROOT / "state" / "profile_nexus"
    request = json.loads(Path(args.request).read_text(encoding="utf-8-sig"))
    found = {}
    diagnostics = []
    pending = set()

    async def inspect_response(response):
        if "batchexecute" not in response.url or response.status != 200:
            return
        try:
            frames = parse_frames(await response.text())
            for rpcid, payload in frames:
                try:
                    records = image_records(rpcid, payload)
                except WireFormatError:
                    diagnostics.append({"rpcid": rpcid, "image_records": 0})
                    continue
                diagnostics.append({"rpcid": rpcid, "image_records": len(records)})
                for record in records:
                    if record.project_id == args.project and record.prompt == request["prompt"]:
                        found[record.media_id] = record
        except Exception as exc:
            diagnostics.append({"reader_error": type(exc).__name__})

    def schedule(response):
        task = asyncio.create_task(inspect_response(response))
        pending.add(task)
        task.add_done_callback(pending.discard)

    async with ProfileLease(profile), async_playwright() as pw:
        ctx = await pw.chromium.launch_persistent_context(str(profile), headless=True,
            args=["--password-store=basic"])
        try:
            page = await ctx.new_page()
            page.on("response", schedule)
            await page.goto(f"https://flow.google.com/project/{args.project}",
                wait_until="domcontentloaded", timeout=90000)
            for _ in range(60):
                if found:
                    break
                await page.wait_for_timeout(500)
            if pending:
                await asyncio.gather(*pending, return_exceptions=True)
            if len(found) != 1:
                return {"status": "unresolved", "matching_images": len(found),
                    "rpc_diagnostics": diagnostics,
                    "grid_media_ids": await page.locator("img[data-media-id]").evaluate_all(
                        "els => els.map(el => el.getAttribute('data-media-id'))")}
            record = next(iter(found.values()))
            if not _is_allowed_download_host(record.image_url):
                raise ValueError("Refusing an unexpected media origin")
            media_url = record.image_url
            # The persisted listing URL is an /asb/ thumbnail handle. Request
            # its original-size rendition and require the recorded dimensions.
            parts = urlsplit(media_url)
            if parts.hostname == "lh3.googleusercontent.com" and parts.path.startswith("/asb/"):
                media_url = parts._replace(path=parts.path.split("=", 1)[0] + "=s0").geturl()
            for _ in range(4):
                if not _is_allowed_download_host(media_url):
                    raise ValueError("Refusing an unexpected media redirect origin")
                response = await ctx.request.get(media_url, max_redirects=0)
                if response.status not in (301, 302, 303, 307, 308):
                    break
                location = response.headers.get("location")
                if not location:
                    raise ValueError("Media redirect has no destination")
                media_url = urljoin(media_url, location)
            if response.status != 200:
                raise ValueError(f"Image GET returned HTTP {response.status}")
            body = await response.body()
            with Image.open(io.BytesIO(body)) as im:
                dimensions, image_format = im.size, im.format
                im.verify()
            if dimensions != tuple(record.dimensions):
                raise ValueError(f"Downloaded dimensions {dimensions} disagree with Google's record {record.dimensions}; origin {urlsplit(media_url).hostname}")
            extension = {"JPEG": ".jpg", "PNG": ".png", "WEBP": ".webp"}.get(image_format)
            if not extension:
                raise ValueError("Unexpected image format")
            out = ROOT / "results" / f"{record.media_id}{extension}"
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(body)
            return {"status": "recovered", "project_id": record.project_id,
                "media_id": record.media_id, "workflow_id": record.workflow_id,
                "path": str(out), "bytes": len(body), "format": image_format,
                "dimensions": dimensions, "sha256": hashlib.sha256(body).hexdigest(),
                "media_origin": urlsplit(media_url).hostname,
                "model_confirmed": False, "new_generation_submitted": False,
                "rpc_diagnostics": diagnostics}
        finally:
            await ctx.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--request", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    result = asyncio.run(run(args))
    data = json.dumps(result, ensure_ascii=False, indent=2)
    Path(args.output).write_text(data + "\n", encoding="utf-8")
    print(data)


if __name__ == "__main__":
    main()
