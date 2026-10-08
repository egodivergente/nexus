"""Approved gallery interactions against isolated local state. No provider calls."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'outputs/studio-gallery-20261008'
OUT.mkdir(parents=True, exist_ok=True)
errors, blocked = [], []
with tempfile.TemporaryDirectory(prefix='nexus-gallery-') as folder:
    data = Path(folder)
    (data/'media').mkdir()
    shutil.copyfile(ROOT/'desktop/ui/logos/flux.png',data/'media'/('a'*32+'.png'))
    shutil.copyfile(ROOT/'desktop/ui/logos/google.png',data/'media'/('b'*32+'.png'))
    host = subprocess.Popen([sys.executable, str(ROOT/'desktop/local_host.py'), '--headless',
        '--offline-qa', '--data-dir', str(data)], stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            if host.poll() is not None: raise RuntimeError('QA host exited')
            if (data/'local-startup-error.txt').exists():
                raise RuntimeError((data/'local-startup-error.txt').read_text())
            try:
                session = json.loads((data/'local-session.json').read_text())
                break
            except (OSError, ValueError): time.sleep(.1)
        else: raise RuntimeError('QA host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.set_default_timeout(8000)
            page.route('https://**/*', lambda r: r.abort())
            def guard(route):
                m = route.request.post_data_json
                if m['op'] in ['request','agent','generate','flowSubmit','upload','flowConnect','flowConnection']:
                    blocked.append(m['op'])
                    route.fulfill(json={'id':m['id'],'ok':False,'error':'QA external operation blocked'})
                else: route.continue_()
            page.route('**/rpc', guard)
            page.goto(f"http://127.0.0.1:{session['port']}/",wait_until='domcontentloaded',timeout=30000)
            page.wait_for_function("Boolean(window.NexusGallery)&&Boolean(window.nexusQA)&&typeof renderBalances==='function'")
            page.evaluate("""()=>{
              state.jobs=Array.from({length:80},(_,i)=>({id:'gallery-'+i,name:'Creación '+i,
                providerId:i%2?'runway':'atlas_cloud',modelId:'fixture',prompt:'Prompt conservado '+i,
                modality:'IMAGE',status:'COMPLETED',startedAt:1700000000000+i,
                parameters:{seed:1234,aspect:'4:3'},outputs:['/media/'+'a'.repeat(32)+'.png','/media/'+'b'.repeat(32)+'.png']}));
              state.studioGallery={favorites:[],collections:[],members:{},hidden:[],size:240};
              state.draft={modelId:null,prompt:'Mi borrador',values:{},media:[]};
              Studio.tab='results';navigate('library');
            }""")
            assert page.locator('.gallery-piece').count() <= 72
            # Two outputs of the same job stay distinct in the viewer.
            page.locator('.gallery-media').first.click()
            assert page.locator('.gallery-lightbox').evaluate('(d)=>d.open')
            assert 'Prompt conservado' in page.locator('.gallery-prompt').inner_text()
            page.locator('[data-gallery=next-output]').click()
            assert page.locator('.gallery-viewer-top span').inner_text() == '2 / 2'
            page.keyboard.press('ArrowRight')
            assert page.locator('.gallery-viewer-top span').inner_text() == '1 / 2'
            page.keyboard.press('f')
            assert page.evaluate('state.studioGallery.favorites.length') == 1
            page.keyboard.press('Escape')
            assert page.locator('.gallery-lightbox').count() == 0
            page.locator('.gallery-piece').first.hover()
            page.locator('.gallery-select').first.click()
            page.locator('.gallery-piece').nth(1).hover()
            page.locator('.gallery-select').nth(1).click()
            assert '2 seleccionados' in page.locator('.gallery-selection').inner_text()
            page.locator('[data-gallery=compare]').click()
            assert page.locator('.pc-comparison-item').count() == 2
            page.locator('[data-gallery=compare-slider]').click()
            page.locator('#gallery-compare-slider').fill('70')
            assert page.locator('.gallery-image-comparison').evaluate("e=>e.style.getPropertyValue('--compare-split')") == '70%'
            page.locator('[data-action=close]').click()
            page.locator('[data-gallery=new-collection]').click()
            page.locator('#gallery-new-collection input').fill('Corto de prueba')
            page.locator('#gallery-new-collection .primary').click()
            assert page.evaluate('Object.keys(state.studioGallery.members).length') == 2
            page.locator('[data-gallery=clear]').click()
            page.keyboard.press('Control+k')
            assert page.locator('#gallery-command-search').evaluate('(el)=>el===document.activeElement')
            page.locator('[data-gallery-command=canvas]').click()
            assert page.locator('#prompt').input_value() == 'Mi borrador'
            page.evaluate("Studio.tab='results';navigate('library')")
            page.locator('#pc-search').fill('conservado 79')
            page.wait_for_timeout(350)
            assert page.locator('.gallery-piece').count() == 1
            page.locator('.gallery-filters summary').click()
            page.locator('[data-gallery=save-view]').click()
            page.locator('[data-gallery=reset]').click()
            page.keyboard.press('Control+k')
            page.locator('[data-gallery=saved-view]').click()
            assert page.locator('.gallery-piece').count() == 1
            page.locator('[data-gallery=reset]').click()
            for width,height in [(1360,900),(760,900),(480,850),(1280,600)]:
                page.set_viewport_size({'width':width,'height':height})
                page.wait_for_timeout(100)
                assert not page.evaluate('document.documentElement.scrollWidth>innerWidth'), width
                page.screenshot(path=str(OUT/f'gallery-{width}.png'))
                page.locator('.gallery-media').first.click()
                assert page.locator('.gallery-lightbox').bounding_box()['width'] <= width+1
                page.screenshot(path=str(OUT/f'viewer-{width}.png'))
                page.keyboard.press('Escape')
            page.locator('.gallery-media').first.click()
            page.locator('[data-gallery=to-video]').click()
            assert page.locator('#studio-model-kind').input_value() == 'VIDEO'
            assert page.evaluate('Studio.reference.mime') == 'image/png'
            page.locator('[data-action=close]').click()
            page.evaluate("Studio.tab='results';navigate('library')")
            page.locator('.gallery-piece').first.hover()
            page.locator('.gallery-select').first.click()
            page.locator('[data-gallery=hide]').click()
            page.locator('[data-gallery=confirm-hide]').click()
            assert page.evaluate('state.studioGallery.hidden.length') == 1
            page.keyboard.press('Control+k')
            page.locator('[data-gallery=restore-hidden]').click()
            assert page.evaluate('state.studioGallery.hidden.length') == 0
            # Optional local clip is copied to the isolated directory, never uploaded.
            video_checked=False
            if os.environ.get('NEXUS_QA_VIDEO'):
                clip=Path(os.environ['NEXUS_QA_VIDEO'])
                assert clip.suffix.lower() in ['.mp4','.webm']
                shutil.copyfile(clip,data/'media'/('c'*32+clip.suffix.lower()))
                page.evaluate("""ext=>{state.jobs.unshift({id:'video-fixture',name:'Vídeo local',providerId:'runway',
                  modelId:'fixture',prompt:'Vídeo de prueba',modality:'VIDEO',status:'COMPLETED',
                  startedAt:1900000000000,parameters:{duration:1},outputs:['/media/'+'c'.repeat(32)+ext]});navigate('library');}""",clip.suffix.lower())
                page.locator('[data-job=video-fixture]').hover()
                page.wait_for_function("document.querySelector('[data-job=video-fixture] video').readyState>=2&&!document.querySelector('[data-job=video-fixture] video').paused")
                assert page.locator('[data-job=video-fixture] video').evaluate('v=>v.muted&&!v.controls')
                page.locator('[data-job=video-fixture] .gallery-media').click()
                assert not page.locator('.gallery-lightbox video').evaluate('v=>v.controls')
                page.locator('.gallery-lightbox video').evaluate('v=>v.loop=true')
                page.locator('[data-gallery=play]').click()
                page.wait_for_function("document.querySelector('.gallery-lightbox video').readyState>=2&&!document.querySelector('.gallery-lightbox video').paused")
                page.keyboard.press('Space')
                assert page.locator('.gallery-lightbox video').evaluate('v=>v.paused')
                page.keyboard.press('Escape')
                video_checked=True
            page.evaluate('flush()')
            page.reload(wait_until='domcontentloaded',timeout=30000)
            page.wait_for_function('Boolean(window.NexusGallery)&&state.studioGallery?.collections.length===1')
            assert page.evaluate('state.studioGallery.favorites.length') == 1
            assert page.evaluate('state.draft.prompt') == 'Mi borrador'
            assert not errors, errors
            assert not blocked, blocked
            report={'status':'PASS','errors':errors,'blockedOperations':blocked,'paidCalls':0,
                'viewerAndOutputs':True,'favoritesPersisted':True,'collectionsPersisted':True,
                'contextSelection':True,'comparisonSlider':True,'commands':True,'draftPreserved':True,
                'savedView':True,'reversibleHide':True,'videoPreviewAndControls':video_checked,'prepareVideo':True,
                'responsiveWidths':[1360,760,480,1280]}
            (OUT/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print(json.dumps(report))
            browser.close()
    finally:
        host.terminate()
        host.wait(timeout=10)
