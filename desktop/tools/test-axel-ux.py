"""AXEL installed HTML against intercepted APIs. No real inference or builds."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

source=Path.home()/'bin/axel_pc.html'
out=Path(__file__).resolve().parents[2]/'outputs/ux-desktop-20261006'
out.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1360,'height':900})
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    def route(r):
        path=r.request.url.split('test.local')[-1]
        if path=='/':return r.fulfill(path=str(source),content_type='text/html')
        if path=='/models':return r.fulfill(json={'models':[{'id':'test','name':'QA','provider':'QA','enabled':True,'tools':False,'input_per_m':1,'output_per_m':2}]})
        if path=='/chat':return r.fulfill(status=503,json={'error':'Sin conexion de prueba'})
        return r.fulfill(json={})
    page.route('**/*',route)
    page.goto('https://test.local/')
    page.wait_for_function('online && models.length===1')
    page.locator('#prompt').fill('Mi borrador importante')
    page.locator('#send').click()
    page.wait_for_function('!busy && document.querySelectorAll(".message.user").length===1')
    assert page.locator('#prompt').input_value()=='Mi borrador importante'
    page.reload()
    assert page.locator('#prompt').input_value()=='Mi borrador importante'
    page.wait_for_function('online && models.length===1')
    page.evaluate("document.querySelector('#prompt').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',isComposing:true,bubbles:true}))")
    assert page.locator('.message.user').count()==0
    result=page.evaluate("""async()=>{
      const original=api;let cancelled=0;
      api=async(path,body)=>{
        if(path==='/stop'){cancelled++;return new Response('{}');}
        if(path!=='/chat')return original(path,body);
        return new Response(new ReadableStream({start(controller){
          setTimeout(()=>controller.enqueue(new TextEncoder().encode('data: {"job_id":"qa-job"}\\r\\n\\r\\n')),150);
          setTimeout(()=>{controller.enqueue(new TextEncoder().encode('data: {"done":true,"full_text":"Detenido"}\\r\\n\\r\\n'));controller.close();},350);
        }}));
      };
      const pending=send();document.querySelector('#stop').click();await pending;api=original;
      return {cancelled,text:document.querySelector('#chat').textContent};
    }""")
    assert result['cancelled']==1 and 'Detenido' in result['text']
    for width in (1360,760,480):
        page.set_viewport_size({'width':width,'height':900})
        page.screenshot(path=str(out/f'axel-{width}.png'))
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    assert not errors,errors
    print('PASS: draft recovery/reload, IME, queued cancellation once, CRLF SSE, 3 layouts; zero paid calls')
    browser.close()
