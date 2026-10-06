"""Render the archived pre-change UI offline for a reproducible visual baseline."""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from threading import Thread
import os
import zipfile
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[2]
archive=root/'entregas'/'Nexus-Studio-Privado-Windows-20261002.zip'
out=root/'outputs'/'desktop-audit-20261002'/'before'
ui=out/'archived-ui'
ui.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(archive) as zipped:
    for name in zipped.namelist():
        prefix='Nexus-Studio/ui/'
        if not name.startswith(prefix) or name.endswith('/'):continue
        target=ui/name[len(prefix):]
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(zipped.read(name))

class Quiet(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(ui),**kwargs)
    def log_message(self,*args):pass
server=ThreadingHTTPServer(('127.0.0.1',0),Quiet)
Thread(target=server.serve_forever,daemon=True).start()
mock="""(() => { const listeners=[]; window.chrome=window.chrome||{};window.chrome.webview={
 addEventListener:(name,callback)=>listeners.push(callback),
 postMessage:message=>setTimeout(()=>{const ok=['init','save'].includes(message.op);
 const value=message.op==='init'?{qa:true,configured:[],state:null}:true;
 listeners.forEach(callback=>callback({data:{id:message.id,ok,value,error:'Sin conexión en esta captura'}}));},0)
 };})();"""
with sync_playwright() as playwright:
    browser=playwright.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1440,'height':900},device_scale_factor=1)
    page.add_init_script(mock)
    page.goto(f'http://127.0.0.1:{server.server_port}/',wait_until='domcontentloaded')
    page.get_by_role('button',name='Imagen',exact=True).wait_for(timeout=20000)
    page.screenshot(path=str(out/'modelos-imagen.png'))
    page.get_by_role('button',name='Vídeo',exact=True).click();page.screenshot(path=str(out/'modelos-video.png'))
    for view in ('canvas','history','library','settings'):
        page.locator(f'#navigation [data-nav="{view}"]').click()
        page.screenshot(path=str(out/f'{dict(canvas="lienzo",history="historial",library="biblioteca",settings="ajustes")[view]}.png'))
    browser.close()
server.shutdown()
print(out)
