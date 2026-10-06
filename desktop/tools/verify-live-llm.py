"""One user-authorized short conversation check; no media generations."""
import json
import os
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
    page.wait_for_function('Agent.snapshot && !Agent.busy', timeout=45000)
    result = page.evaluate("""async()=>{
      if(Agent.snapshot.offline)throw Error('El motor sigue offline');
      const result=await agentCall('send',{text:'hey. Solo saludame brevemente en espanol, sin recomendar modelos ni usar herramientas.',attachments:[]});
      return {text:result.text,inputTokens:result.inputTokens,outputTokens:result.outputTokens,offline:Agent.snapshot.offline};
    }""")
    print(json.dumps(result,ensure_ascii=False))
    browser.close()

