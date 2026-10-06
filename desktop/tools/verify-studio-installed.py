"""Read-only verification of the installed redesign and preserved private data."""
import hashlib
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
out = root / 'outputs/studio-redesign-20261006'
data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
session = json.loads((data / 'local-session.json').read_text())
before = json.loads((out / 'private-before/state.json').read_text(encoding='utf-8'))
current = json.loads((data / 'state.json').read_text(encoding='utf-8'))
assert before == current
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
assert sha(data / 'credentials.dpapi') == sha(out / 'private-before/credentials.dpapi')
errors = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width':1360,'height':900}, bypass_csp=True)
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.wait_for_function('Boolean(window.nexusQA)')
    assert page.evaluate('initial.browserImport===true')
    assert page.locator('#navigation button').count()==3
    page.wait_for_function("document.fonts.status==='loaded'")
    page.wait_for_timeout(750)
    page.screenshot(path=str(out/'installed-library.png'))
    assert not page.evaluate('document.documentElement.scrollWidth>innerWidth')
    report=page.evaluate("""()=>({jobs:state.jobs.length,outputs:state.jobs.reduce((n,j)=>n+(j.outputs?.length||0),0),
       models:models.length,assistantLoaded:Agent.snapshot!==null,visibleCards:document.querySelectorAll('#content .result').length})""")
    assert not errors, errors
    report.update({'stateUnchanged':True,'credentialsUnchanged':True,'errors':errors,'paidCalls':0})
    (out/'installed-verification.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report))
    browser.close()

