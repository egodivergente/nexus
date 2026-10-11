"""Inspect actual multimodal submit contracts while aborting every final POST."""
import argparse
import asyncio
import json
import os
import sys
from pathlib import Path
from urllib.parse import parse_qs
from launcher import ROOT, environment

async def run(args):
    sys.stdout.reconfigure(encoding='utf-8')
    os.environ.update(environment())
    from runtime_compat import install
    install()
    from playwright.async_api import async_playwright
    from gflow_cli.profile_lease import ProfileLease
    from gflow_cli.api.video import GenerateVideoRequest, VideoModel, Mode, Aspect
    from gflow_cli.api.image import GenerateImageRequest, Model, Aspect as ImageAspect
    from gflow_cli.api.transports.migrated_composer import MigratedComposer
    from PIL import Image
    fixtures=ROOT/'inputs'
    fixtures.mkdir(exist_ok=True)
    start,end=fixtures/'nexus-contract-start.png',fixtures/'nexus-contract-end.png'
    Image.new('RGB',(640,360),(160,80,20)).save(start)
    Image.new('RGB',(640,360),(20,80,160)).save(end)
    cases=json.loads(Path(args.cases).read_text(encoding='utf-8'))
    results=[]
    async with ProfileLease(ROOT/'state/profile_nexus'),async_playwright() as pw:
        ctx=await pw.chromium.launch_persistent_context(str(ROOT/'state/profile_nexus'),headless=True,args=['--password-store=basic'])
        try:
            for case in cases:
                result={'case':case,'new_generation_submitted':False}
                page=await ctx.new_page()
                try:
                    composer=MigratedComposer()
                    await composer.ensure_editor(page,args.project)
                    model,mode=case['model'],case.get('mode','t2v')
                    start_id=end_id=None;ref_ids=()
                    image=model in {'nano-pro','nano2'}
                    if image:
                        request=GenerateImageRequest(prompt='Nexus aborted contract inspection.',model=Model.from_cli(model),aspect=ImageAspect.SQUARE,
                            ref_paths=(start,) if case.get('references') else ())
                        await composer.apply_image_settings(page,request)
                        if case.get('references'):ref_ids=await composer.attach_references(page,args.project,(start,))
                    else:
                        request=GenerateVideoRequest(prompt='A coloured cube rotates slowly.',model=VideoModel.from_cli(model),mode=Mode(mode),
                            aspect=Aspect.from_cli(case.get('aspect','16:9')),duration=case.get('duration',8),resolution=case.get('resolution'),
                            start_image=start if mode=='i2v' else None,end_image=end if case.get('end') else None,
                            reference_images=(start,) if mode=='r2v' else ())
                        await composer.apply_video_settings(page,request)
                        if mode=='i2v':start_id=await composer.attach_start_frame(page,args.project,start)
                        if case.get('end'):end_id=await composer.attach_end_frame(page,args.project,end)
                        if mode=='r2v':ref_ids=await composer.attach_references(page,args.project,(start,))
                    captured=[]
                    async def abort_post(route,raw):
                        # Uploads have finished. At the final click EVERY POST is
                        # aborted, including an unknown or renamed generation RPC.
                        if raw.method!='POST':
                            await route.continue_();return
                        try:
                            value=parse_qs((raw.post_data_buffer or b'').decode('utf-8','ignore')).get('f.req',[''])[0] if 'batchexecute' in raw.url else ''
                            if value:
                                wire=json.loads(value)
                                for group in wire:
                                    for entry in group:
                                        if entry[0] in {'YhhmEf','eb1hJf','MZZa6b','nprQif','ogiZ0b'}:
                                            payload=json.loads(entry[1])
                                            # Keep only generation rows, never session context,
                                            # CAPTCHA tokens, cookies or signed URLs.
                                            safe_payload=[None,payload[1]] if entry[0]=='ogiZ0b' else [payload[0]]
                                            safe_wire=[[[entry[0],json.dumps(safe_payload),None,'generic']]]
                                            body='f.req='+json.dumps(safe_wire)
                                            captured.append(body)
                                            from probe_video import redact
                                            result.update(rpc=entry[0],wire=redact(payload[1] if entry[0]=='ogiZ0b' else payload[0]),
                                                start_bound=bool(start_id and start_id in body),end_bound=bool(end_id and end_id in body),
                                                references_bound=all(i in body for i in ref_ids),reference_count=len(ref_ids))
                                            # Private diagnostic only; never written to source or GitHub.
                                            result['private_body']=body
                                            result['private_ids']={'start_id':start_id,'end_id':end_id,'reference_ids':ref_ids}
                        finally:
                            await route.abort()
                            if captured:result['aborted_before_google']=True
                    await page.route('**/*',abort_post)
                    await composer.send_prompt(page,request.prompt,append=bool(ref_ids))
                    await (await composer._pre_submit_gate(page)).click()
                    for _ in range(30):
                        if captured:break
                        await page.wait_for_timeout(250)
                    if not captured:raise RuntimeError('No aborted generation request captured')
                except Exception as exc:
                    result['error']=type(exc).__name__+': '+str(exc)[:500]
                finally:await page.close()
                results.append(result)
                Path(args.output).write_text(json.dumps(results,indent=2),encoding='utf-8')
                print(json.dumps({k:v for k,v in result.items() if k not in {'private_body','private_ids','wire'}}),flush=True)
        finally:await ctx.close()
    return results

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for name in ('cases','project','output'):parser.add_argument('--'+name,required=True)
    asyncio.run(run(parser.parse_args()))
