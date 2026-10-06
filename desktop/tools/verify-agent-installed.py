"""Read-only smoke test of the installed Nexus Studio, without provider calls."""
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
output = root / 'outputs/desktop-parity-20261006'
data = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
session = json.loads((data / 'local-session.json').read_text())
errors = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width': 1360, 'height': 900})
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.locator('[data-nav=assistant]').click()
    page.locator('.agent-session-info').get_by_text('Planificador local', exact=False).wait_for(timeout=30000)
    page.locator('[data-agent=skills]').get_by_text('34 skills').wait_for()
    page.locator('#agent-input:not([disabled])').wait_for(timeout=30000)
    report = page.evaluate('''() => ({version:initial.version,models:models.length,
        jobs:state.jobs.length,outputs:state.jobs.reduce((n,j)=>n+(j.outputs?.length||0),0),
        skills:Agent.snapshot.skills.length,offline:Agent.snapshot.offline,
        providers:initial.configured,overflow:document.documentElement.scrollWidth>innerWidth})''')
    page.screenshot(path=str(output / 'installed-assistant.png'))
    report['errors'] = errors
    assert report['jobs'] >= 85
    assert report['outputs'] >= 84
    assert not errors
    assert not report['overflow']
    (output / 'installed-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    browser.close()
print(json.dumps(report))
