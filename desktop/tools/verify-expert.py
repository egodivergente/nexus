"""Isolated live LLM checks. Media generation is blocked in this test window."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
data = root.parent / 'outputs' / 'expert-agent-20261006' / 'live-check'
assistant = data / 'assistant'
assistant.mkdir(parents=True, exist_ok=True)
original = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop' / 'assistant'
for name in ('settings.json', 'credentials.dpapi'):
    shutil.copy2(original / name, assistant / name)
env = dict(os.environ)
env['PATH'] = r'C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin;' + env['PATH']
process = subprocess.Popen([sys.executable, str(root / 'local_host.py'), '--headless',
                            '--data-dir', str(data)], env=env,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                           creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
results = []
try:
    for _ in range(100):
        session_file = data / 'local-session.json'
        if session_file.exists():
            session = json.loads(session_file.read_text())
            if session['pid'] == process.pid:
                break
        if process.poll() is not None:
            raise RuntimeError('Isolated host failed')
        time.sleep(.2)
    else:
        raise TimeoutError('Isolated host did not start')
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(bypass_csp=True)
        page.goto(f"http://127.0.0.1:{session['port']}/")
        page.wait_for_function('Boolean(window.nexusQA)')
        page.locator('[data-nav=assistant]').click()
        page.wait_for_function('Agent.snapshot && !Agent.busy', timeout=45000)
        page.evaluate("""()=>{
          const originalRpc=rpc;
          rpc=async(op,args)=>{
            if(/generate|submit|batch/i.test(op))throw Error('Generation blocked in QA');
            return originalRpc(op,args);
          };
        }""")
        for prompt in [
            'hey',
            'Dame un prompt breve para FLUX 2 Pro: perfume sobre fondo negro, texto exacto NEXUS. No generes nada ni prepares un plan.',
            'Que parametros admite el modelo InventadoXYZ 999 Ultra? No generes nada.'
        ]:
            result = page.evaluate("""async(text)=>{
              if(Agent.snapshot.offline)throw Error('LLM offline');
              await agentCall('new',{});
              const r=await agentCall('send',{text,attachments:[]});
              return {text:r.text,inputTokens:r.inputTokens,outputTokens:r.outputTokens,
                elapsedMs:r.elapsedMs,toolRounds:r.toolRounds,stop:r.stop,expertVersion:r.expertVersion};
            }""", prompt)
            results.append(result)
            print(json.dumps(result, ensure_ascii=False), flush=True)
        assert results[0]['expertVersion'] == '2026-10-06.1'
        assert results[0]['toolRounds'] == 0
        assert results[0]['inputTokens'] < 9089
        browser.close()
finally:
    # Terminate only our isolated process tree, including its private JVM.
    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
