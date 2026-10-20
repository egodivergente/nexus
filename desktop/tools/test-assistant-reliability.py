"""Keyboard/rendering fixtures and real loopback restart. No inference requests."""
import json
from pathlib import Path
import subprocess
import sys
import time
import urllib.request
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
output = root.parent / 'outputs/assistant-reliability'
data = output / 'isolated-data'
data.mkdir(parents=True, exist_ok=True)
process = None


def start(port=0):
    global process
    harness = 'import local_host as host\nhost.main()'
    process = subprocess.Popen(
        [sys.executable, '-c', harness, '--headless', '--offline-qa',
         '--data-dir', str(data), '--port', str(port)], cwd=root,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(150):
        try:
            session = json.loads((data / 'local-session.json').read_text())
            if session['pid'] == process.pid:
                with urllib.request.urlopen(f"http://127.0.0.1:{session['port']}/health", timeout=1):
                    return session['port']
        except (OSError, ValueError):
            pass
        time.sleep(.1)
    raise RuntimeError('Isolated host did not start')


try:
    port = start()
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(viewport={'width':1536, 'height':864})
        errors = []
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.route('https://**/*', lambda route: route.abort())
        page.goto(f'http://127.0.0.1:{port}/')
        page.wait_for_function("()=>typeof reviewImages==='function'&&window.nexusQA")
        page.evaluate("""()=>{
          window.realAgentCall=agentCall;window.sends=0;
          Agent.snapshot={offline:false,mode:'ASSISTANT',skills:[],plans:[],proposals:[],index:{projects:[]}};
          Agent.settings={modelId:'us.anthropic.claude-sonnet-4-6'};
          agentCall=async()=>{window.sends++;Agent.busy=true;await new Promise(r=>setTimeout(r,100));Agent.busy=false;return {text:'Respuesta de prueba de interfaz.'};};
          view='assistant';render();
        }""")
        page.locator('#agent-input').fill('hola')
        page.locator('#agent-input').press('Shift+Enter')
        assert page.locator('#agent-input').input_value() == 'hola\n', repr(page.locator('#agent-input').input_value())
        assert page.evaluate('window.sends') == 0
        page.locator('#agent-input').evaluate("e=>e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',isComposing:true,bubbles:true}))")
        assert page.evaluate('window.sends') == 0
        page.locator('#agent-input').press('Enter')
        page.wait_for_function('()=>Agent.items.length===2&&!Agent.busy')
        assert page.evaluate('window.sends') == 1
        assert page.locator('#agent-input').evaluate('e=>e===document.activeElement')
        assert page.locator('[data-review=image]').count() == 0
        assert page.locator('[data-agent=attach]').get_attribute('type') == 'button'
        page.locator('#agent-input').fill('segundo mensaje')
        page.locator('[data-agent=send]').click()
        page.wait_for_function('()=>Agent.items.length===4&&!Agent.busy')
        assert page.evaluate('window.sends') == 2
        page.evaluate('window.sampleItems=structuredClone(Agent.items)')
        for width, height in ((1536,864),(1280,720),(760,700),(480,740)):
            page.set_viewport_size({'width':width,'height':height})
            for empty in (False, True):
                page.evaluate('(empty)=>{Agent.items=empty?[]:structuredClone(window.sampleItems);renderAssistant();}', empty)
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
                box = page.locator('#agent-compose').bounding_box()
                assert box['y'] >= 0 and box['y']+box['height'] <= height, box
                page.screenshot(path=str(output/f'assistant-{width}-{"empty" if empty else "chat"}.png'))

        # Keep this page and its cached session alive across an actual host restart.
        process.terminate()
        process.wait(timeout=10)
        failure = page.evaluate("async()=>{try{await rpc('init');return '';}catch(e){return e.message;}}")
        assert 'conexión' in failure and 'Failed to fetch' not in failure, failure
        start(port)
        assert page.evaluate("async()=>Boolean((await rpc('init')).qa)") is True
        # Cache the current token, restart again: first POST gets a real 403.
        process.terminate()
        process.wait(timeout=10)
        start(port)
        assert page.evaluate("async()=>Boolean((await rpc('init')).qa)") is True
        page.evaluate("""()=>{
          agentCall=async()=>{throw Error('Conexión de prueba interrumpida.');};
          Agent.draft='Conservar este mensaje';renderAssistant();
        }""")
        page.locator('#agent-input').press('Enter')
        page.get_by_role('alert').wait_for()
        assert page.locator('#agent-input').input_value() == 'Conservar este mensaje'
        assert page.locator('.agent-message.user').count() == 0
        assert not errors, errors
        browser.close()
        print('PASS: Enter/click once, Shift+Enter, IME, focus, 4 viewports, real local disconnect/restart and 403 recovery, preserved draft. No inference.')
finally:
    if process and process.poll() is None:
        process.terminate()
        process.wait(timeout=10)
