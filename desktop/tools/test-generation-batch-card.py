"""Browser regression for direct Flow results; no provider request is made."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time

from playwright.sync_api import sync_playwright


root = Path(__file__).resolve().parents[1]


def wait_for_session(data):
    for _ in range(150):
        path = data / "local-session.json"
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
        time.sleep(0.1)
    raise RuntimeError("El host local no inició la sesión de prueba.")


with tempfile.TemporaryDirectory() as folder:
    data = Path(folder)
    process = subprocess.Popen(
        [sys.executable, str(root / "local_host.py"), "--headless", "--offline-qa", "--data-dir", str(data)],
        cwd=root,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NO_WINDOW,
    )
    try:
        session = wait_for_session(data)
        with sync_playwright() as pw:
            browser = pw.chromium.launch(channel="msedge", headless=True)
            page = browser.new_page(viewport={"width": 1360, "height": 900}, bypass_csp=True)
            errors = []
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.route("https://**/*", lambda route: route.abort())
            page.goto(f"http://127.0.0.1:{session['port']}/")
            page.wait_for_function("Boolean(window.nexusQA) && !Agent.busy")
            page.evaluate("""()=>{
              const plan={id:'p1',ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'},
                modelName:'Nano Banana Pro',modality:'IMAGE',prompt:'Cuatro variaciones cinematográficas',
                params:{aspect:'16:9',count:'4'},effectiveParams:{aspect:'16:9',count:'4'},estimate:{basis:'Tu cuenta Flow'}};
              Agent.restored=true;Agent.busy=false;Agent.cardAnchors.clear();
              Agent.items=[{role:'user',text:'Genera cuatro variaciones'},{role:'assistant',text:'Generando en Flow'}];
              Agent.snapshot={offline:false,mode:'AGENT',skills:[],index:{projects:[]},plans:[{state:'SUBMITTED',plan}],proposals:[]};
              state.jobs=[{id:'flow-job',agentPlanId:'p1',providerId:'google_flow',modality:'IMAGE',status:'SENDING',expectedOutputCount:4,outputs:[]}];
              view='assistant';render();
            }""")
            assert page.locator('.generation-batch-card').count()==0
            assert page.locator('.agent-plan').count()==0
            assert page.locator('.agent-flow-result').count()==1
            assert page.locator('[data-agent-batch-generate]').count()==0
            assert page.locator('[data-agent=generate-ready]').count()==0
            assert page.evaluate('!document.querySelector("#dialog").open')
            assert 'Enviando a Flow' in page.locator('.agent-flow-result').inner_text()
            # New conversation turns never drag an older result below them.
            page.evaluate("Agent.items.push({role:'user',text:'Otra petición'},{role:'assistant',text:'Respuesta posterior'});renderAssistant();")
            assert page.evaluate("document.querySelector('.agent-message.assistant').nextElementSibling.classList.contains('agent-flow-result')")
            page.evaluate("state.jobs[0].status='PROCESSING';state.jobs[0].outputs=['/media/'+'a'.repeat(32)+'.png'];renderAssistant();")
            assert 'Renderizando 1/4' in page.locator('.agent-flow-result').inner_text()
            assert page.locator('.agent-flow-media img').count()==1
            page.evaluate("state.jobs[0].status='COMPLETED';state.jobs[0].outputs=['a','b','c','d'].map(c=>'/media/'+c.repeat(32)+'.png');renderAssistant();")
            assert 'Completado' in page.locator('.agent-flow-result').inner_text()
            assert page.locator('.agent-flow-media img').count()==4
            for width in [1360,760,480]:
                page.set_viewport_size({'width':width,'height':900})
                assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            parsed=page.evaluate("""()=>{
              const job={providerId:'google_flow',modality:'IMAGE',parameters:{count:'4'},expectedOutputCount:4,outputs:[]};
              parseResponse(job,{id:'flow-job',status:'in_progress',count:1,blocks:[{request_id:'native-1',count:4,status:'submitting'}]});
              return {count:job.expectedOutputCount,blocks:job.flowBlocks,status:job.status};
            }""")
            assert parsed=={'count':4,'blocks':[{'requestId':'native-1','count':4,'status':'submitting'}],'status':'PROCESSING'}
            page.evaluate("""()=>{
              const other=structuredClone(Agent.snapshot.plans[0]);other.plan.id='different-aspect';
              other.plan.effectiveParams.aspect='1:1';other.plan.params.aspect='1:1';Agent.snapshot.plans.push(other);renderAssistant();
            }""")
            assert page.locator('.agent-flow-result').count()==2
            assert page.locator('.generation-batch-card').count()==0
            assert not errors, errors
            browser.close()
            print('PASS: Flow results without generation cards or confirmations, partial results, count metadata and chronological placement.')
    finally:
        process.terminate()
        process.wait(timeout=10)
