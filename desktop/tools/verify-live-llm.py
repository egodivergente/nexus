"""One user-authorized short conversation check; no media generations."""
import json
import os
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
session = json.loads((data / 'local-session.json').read_text())
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(bypass_csp=True)
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    page.locator('[data-nav=assistant]').click()
    try:
        page.wait_for_function('Agent.snapshot && !Agent.busy', timeout=45000)
    except Exception:
        print(json.dumps(page.evaluate("()=>({busy:Agent.busy,snapshot:!!Agent.snapshot,toast:document.querySelector('#toast')?.textContent})"),ensure_ascii=True))
        raise
    result = page.evaluate("""async(text)=>{
      if(Agent.snapshot.offline)throw Error('El motor sigue offline');
      const result=await agentCall('send',{text,attachments:[]});
      return {text:result.text,inputTokens:result.inputTokens,outputTokens:result.outputTokens,
        offline:Agent.snapshot.offline,expertVersion:result.expertVersion,elapsedMs:result.elapsedMs,toolRounds:result.toolRounds};
    }""", sys.argv[1] if len(sys.argv)>1 else 'hey')
    print(json.dumps(result,ensure_ascii=False))
    assert result['expertVersion'] == '2026-10-06.1'
    assert result['offline'] is False
    browser.close()

