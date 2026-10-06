"""Offline Windows integration: real JVM, isolated data, no external requests."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[2]
output = root / 'outputs/desktop-parity-20261006'
session = json.loads((output / 'qa-data/local-session.json').read_text())
errors = []
external = []
report = {}
with sync_playwright() as p:
    browser = p.chromium.launch(channel='msedge', headless=True)
    page = browser.new_page(viewport={'width': 1360, 'height': 900}, bypass_csp=True)
    page.on('pageerror', lambda error: errors.append(str(error)))
    def route_request(route):
        if not route.request.url.startswith('http://127.0.0.1:'):
            external.append(route.request.url)
            route.abort()
        else:
            route.continue_()
    page.route('**/*', route_request)
    page.goto(f"http://127.0.0.1:{session['port']}/")
    page.locator('[data-nav=assistant]').click()
    page.wait_for_function('window.nexusQA && Agent.snapshot && !Agent.busy', timeout=45000)
    report['initial'] = page.evaluate('({offline:Agent.snapshot.offline,skills:Agent.snapshot.skills.length,models:models.length,mode:Agent.snapshot.mode})')
    assert report['initial']['offline'] is True
    assert report['initial']['skills'] > 0
    page.evaluate("async()=>{await agentCall('new');await agentCall('mode',{mode:'COPILOT'});Agent.items=[];renderAssistant();}")
    page.screenshot(path=str(output / 'assistant-desktop.png'))
    page.locator('[data-agent="project-new"]').click()
    page.locator('#agent-project-form [name=name]').fill('QA Desktop Project')
    page.locator('#agent-project-form button').click()
    page.wait_for_function('!Agent.busy && Agent.snapshot.projectId')
    page.locator('[data-agent="memory"]').click()
    page.locator('#agent-memory [name=synopsis]').fill('Una historia con continuidad visual')
    page.locator('#agent-memory [name=rules]').fill('Mantener el vestuario')
    page.locator('#agent-memory button').click()
    page.wait_for_function('!Agent.busy && Agent.snapshot.index.projects.some(p=>p.memory.synopsis === "Una historia con continuidad visual")')
    report['memory_saved'] = True
    page.locator('#agent-input').fill('Crea una imagen de una ciudad futurista en formato 16:9')
    page.locator('[data-agent="send"]').click()
    page.wait_for_function('!Agent.busy && Agent.items.some(i=>i.role === "assistant")', timeout=120000)
    report['turn'] = page.evaluate('({items:Agent.items,plans:Agent.snapshot.plans})')
    page.screenshot(path=str(output / 'assistant-plan.png'), full_page=True)
    assert len(report['turn']['plans']) > 0, json.dumps(report['turn'], ensure_ascii=False)
    page.locator('[data-agent-generate]').first.click()
    page.wait_for_selector('[data-agent="confirm"]')
    report['review_visible'] = True
    page.locator('[data-action=close]').click()
    assert page.evaluate('state.jobs.length') == 0
    page.locator('[data-agent-edit]').first.click()
    page.locator('#agent-edit [name=prompt]').fill('Una ciudad futurista al amanecer')
    page.locator('#agent-edit button.primary').click()
    page.wait_for_function('!Agent.busy && Agent.snapshot.plans.some(p=>p.plan.prompt === "Una ciudad futurista al amanecer")')
    report['edit_plan'] = True
    page.locator('[data-nav=catalog]').click()
    assert page.locator('#search').is_visible()
    page.locator('[data-nav=assistant]').click()
    page.locator('[data-agent-mode=ASSISTANT]').click()
    page.wait_for_function('!Agent.busy && Agent.snapshot.mode === "ASSISTANT"')
    report['mode_change'] = True
    page.evaluate("""async()=>{
      state.jobs=[{id:'qa-multiple',providerId:'atlas_cloud',modelId:'qa',name:'QA multiple',modality:'IMAGE',
        prompt:'Dos resultados distintos',status:'COMPLETED',outputs:['https://example.invalid/one.png','https://example.invalid/two.png']}];
      navigate('library');
    }""")
    page.locator('[data-output-next]').click()
    assert page.locator('.result img').get_attribute('src') == 'https://example.invalid/two.png'
    assert page.locator('[data-download]').get_attribute('data-download') == 'https://example.invalid/two.png'
    report['multiple_results'] = True
    page.evaluate("""async()=>{
      state.jobs=[];
      const m=models.find(m=>m.providerId==='atlas_cloud'&&m.id.endsWith('/text-to-image'));
      state.draft={modelId:m.id,prompt:'Una ciudad',values:{},media:[]};selection.clear();selection.add(m.id);
      await prepareComparison();
    }""")
    page.locator('[data-batch-quantity]').fill('3')
    page.locator('[data-batch-quantity]').dispatch_event('change')
    assert '3 solicitudes' in page.locator('.compare-bottom').inner_text()
    report['batch_quantity'] = True
    page.screenshot(path=str(output / 'batch-review.png'))
    page.locator('[data-action=close]').click()
    page.evaluate("navigate('assistant')")
    page.set_viewport_size({'width': 760, 'height': 900})
    page.screenshot(path=str(output / 'assistant-narrow.png'))
    report['overflow'] = page.evaluate('document.documentElement.scrollWidth > innerWidth')
    report['errors'] = errors
    report['external_requests'] = external
    report['paid_jobs'] = page.evaluate('state.jobs.length')
    (output / 'ui-test-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    assert not errors, errors
    assert all(url.startswith('https://example.invalid/') for url in external), external
    assert not report['overflow']
    real_csp = browser.new_page(viewport={'width': 1360, 'height': 900})
    real_csp.goto(f"http://127.0.0.1:{session['port']}/")
    real_csp.locator('.agent-session-info').get_by_text('Planificador local', exact=False).wait_for(timeout=30000)
    report['original_csp_smoke'] = True
    (output / 'ui-test-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    browser.close()
print(json.dumps({'passed': True, 'skills': report['initial']['skills'], 'plans': len(report['turn']['plans']), 'paid_jobs': report['paid_jobs']}))
