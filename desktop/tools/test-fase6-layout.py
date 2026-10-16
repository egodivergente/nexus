"""Fase 6: la barra lateral y la cabecera no se desbordan a ningún ancho ni en ningún estado del dock."""
import json
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else None
problems = []
with tempfile.TemporaryDirectory(prefix='nexus-layout6-') as folder:
    data = Path(folder)
    (data/'media').mkdir()
    shutil.copyfile(ROOT/'desktop/ui/logos/flux.png', data/'media'/('a'*32+'.png'))
    host = subprocess.Popen([sys.executable, str(ROOT/'desktop/local_host.py'), '--headless',
        '--offline-qa', '--data-dir', str(data)], stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        for _ in range(300):
            try:
                session = json.loads((data/'local-session.json').read_text()); break
            except (OSError, ValueError): time.sleep(.1)
        else: raise RuntimeError('QA host unavailable')
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel='msedge', headless=True)
            for width in (360, 420, 600, 700, 800, 1000, 1360):
                page = browser.new_context(viewport={'width': width, 'height': 800}, bypass_csp=True).new_page()
                page.route('https://**/*', lambda r: r.abort())
                page.goto(f"http://127.0.0.1:{session['port']}/", wait_until='domcontentloaded', timeout=30000)
                page.wait_for_function("Boolean(window.NexusGallery)&&Boolean(window.nexusQA)")
                page.evaluate("""()=>{state.jobs=[{id:'j1',name:'Uno',providerId:'atlas_cloud',modelId:'fixture',prompt:'P',
                    modality:'IMAGE',status:'COMPLETED',startedAt:1700000000000,parameters:{},outputs:['/media/'+'a'.repeat(32)+'.png']}];
                    state.studioGallery={favorites:[],collections:[],members:{},hidden:[],size:240};Studio.tab='results';navigate('library');}""")
                page.wait_for_selector('.gallery-piece')
                page.wait_for_function("!document.querySelector('.nx-startup-card')", timeout=20000)
                page.add_style_tag(content='*,*::before,*::after{transition:none!important;animation:none!important}')
                for expanded in (False, True):
                    page.evaluate(f"document.body.classList.toggle('creative-dock-expanded',{str(expanded).lower()})")
                    page.wait_for_timeout(1200)
                    r = page.evaluate("""()=>{
                      const s=document.querySelector('.sidebar'),sb=s.getBoundingClientRect(),vw=innerWidth;
                      const textVisible=[...s.querySelectorAll('*')].filter(e=>{const c=getComputedStyle(e),b=e.getBoundingClientRect();
                        return b.width>0&&b.height>0&&c.fontSize!=='0px'&&[...e.childNodes].some(n=>n.nodeType==3&&n.textContent.trim())&&b.right>sb.right+1}).map(e=>e.textContent.trim().slice(0,20));
                      const rail=sb.width<=70;
                      const labelsInRail=rail?[...s.querySelectorAll('.creative-nav-label,#gallery-collections,.sidebar-bottom')].filter(e=>e.getBoundingClientRect().width>0).map(e=>e.className||e.id):[];
                      const hdr=document.querySelector('main>header').getBoundingClientRect();
                      const act=[...document.querySelectorAll('#header-actions button, #header-actions summary')].filter(b=>{const r=b.getBoundingClientRect();return r.width&&(r.right>vw+1||r.left<-1)}).map(b=>b.textContent.trim().slice(0,15));
                      return {sw:Math.round(sb.width),rail,textVisible,labelsInRail,hOverflow:document.documentElement.scrollWidth>vw+1,act,hdrBottom:Math.round(hdr.bottom)}
                    }""")
                    tag = f"w{width}-{'exp' if expanded else 'col'}"
                    if r['labelsInRail'] or r['textVisible'] or r['act'] or r['hOverflow']:
                        problems.append((tag, r))
                    if OUT:
                        OUT.mkdir(parents=True, exist_ok=True)
                        page.screenshot(path=str(OUT/f'{tag}.png'))
                page.context.close()
            browser.close()
    finally:
        host.terminate()
for p in problems: print('PROBLEMA', p)
assert not problems
print('OK layout fase6')
