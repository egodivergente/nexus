"""Browser regression for compact assistant image batches; no provider request is made."""
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
            page.evaluate(
                """()=>{
                  const prompt='Una editorial de moda nocturna, luz de neón azul y magenta, retrato vertical muy detallado con composición cinematográfica y textura de película analógica.';
                  const plan=(id,aspect='9:16')=>({
                    id, ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'}, modelName:'Nano Banana Pro',
                    modality:'IMAGE', prompt, params:{aspect,count:'1'}, effectiveParams:{aspect,count:'1'},
                    estimate:{basis:'Incluido'}, warnings:[]
                  });
                  document.body.classList.add('nx-desktop');
                  Agent.busy=false;Agent.cardAnchors.clear();Agent.expandedBatches.clear();
                  Agent.items=[];
                  Agent.snapshot={offline:false,mode:'ASSISTANT',skills:[],index:{projects:[]},
                    plans:['p1','p2','p3','p4'].map(id=>({state:'PREPARED',fingerprint:'fp-'+id,plan:plan(id)})),proposals:[]};
                  state.jobs=[];view='assistant';render();
                }"""
            )
            assert page.locator(".generation-batch-card").count() == 1
            assert page.locator(".agent-plan").count() == 0
            assert page.locator(".agent-empty").count() == 0
            assert page.locator(".agent-messages").evaluate("node=>getComputedStyle(node).display") != "flex"
            assert page.locator("[data-agent-batch-generate]").count() == 1
            assert page.locator("[data-agent-batch-generate]").inner_text() == "Generar 4"
            card_text = page.locator(".generation-batch-card").inner_text()
            for chip in ("Nano Banana Pro", "Google Flow", "9:16", "Incluido", "4 imágenes", "Listo para generar"):
                assert chip in card_text
            assert "Procesando" not in page.locator(".agent-messages").inner_text()
            assert page.locator("[data-agent-prompt-toggle]").inner_text() == "Ver más"
            page.locator("[data-agent-prompt-toggle]").click()
            assert page.locator(".generation-batch-prompt").evaluate("node=>node.classList.contains('is-expanded')")
            assert page.locator("[data-agent-prompt-toggle]").inner_text() == "Ver menos"
            layout = page.evaluate(
                """()=>{
                  const other=structuredClone(Agent.snapshot.plans[0]);
                  other.plan.id='different-prompt';other.plan.prompt='Una escena totalmente distinta.';
                  Agent.snapshot.plans.push(other);renderAssistant();
                  return [...document.querySelectorAll('.generation-batch-card')].map(card=>{
                    const box=card.getBoundingClientRect();return {top:box.top,bottom:box.bottom};
                  });
                }"""
            )
            assert page.locator(".generation-batch-card").count() == 2
            assert page.locator(".agent-plan").count() == 0
            assert layout[1]["top"] >= layout[0]["bottom"]
            page.evaluate("""()=>{Agent.snapshot.plans=Agent.snapshot.plans.filter(entry=>entry.plan.id!=='different-prompt');Agent.cardAnchors.clear();Agent.items=[{role:'user',text:'Crea cuatro variaciones'}];renderAssistant();}""")
            confirmation = page.evaluate(
                """async()=>{
                  const calls=[],oldCall=agentCall,oldModel=agentModel,oldQuote=quotePlan,oldModal=modal;
                  try {
                    agentCall=async(op,args)=>{
                      calls.push({op,args});
                      const entry=Agent.snapshot.plans.find(item=>item.plan.id===args.id);
                      if(op==='edit'){entry.plan={...entry.plan,prompt:args.prompt,params:{...entry.plan.params,...args.params},effectiveParams:{...entry.plan.effectiveParams,...args.params}};entry.fingerprint='fresh-fingerprint';}
                      if(op==='discard')entry.state='DISCARDED';
                      return {};
                    };
                    agentModel=async()=>({fields:[]});
                    quotePlan=async()=>({currency:'flow_credits',amount:0,time:Date.now()});
                    modal=html=>window.batchConfirmation=html;
                    await prepareBatchConfirmation(findGenerationBatch(document.querySelector('[data-agent-batch-generate]').dataset.agentBatchGenerate));
                    return {calls,approved:Agent.approved,html:window.batchConfirmation};
                  } finally {agentCall=oldCall;agentModel=oldModel;quotePlan=oldQuote;modal=oldModal;}
                }"""
            )
            assert [call["op"] for call in confirmation["calls"]] == ["edit", "discard", "discard", "discard"]
            assert confirmation["approved"]["id"] == "p1"
            assert confirmation["approved"]["fingerprint"] == "fresh-fingerprint"
            assert "Generar 4" in confirmation["html"]
            page.evaluate("""()=>{Agent.items.push({role:'assistant',text:'Primera respuesta'},{role:'user',text:'Otra petición'},{role:'assistant',text:'Segunda respuesta'});renderAssistant();}""")
            order = page.locator(".agent-messages > article").all_text_contents()
            assert "Crea cuatro variaciones" in order[0]
            assert "Generación de imágenes" in order[1]
            assert "Segunda respuesta" in order[-1]

            page.evaluate(
                """()=>{
                  state.jobs=[{id:'flow-job',agentPlanId:'p1',providerId:'google_flow',modelId:'google-flow/nano-pro',
                    modality:'IMAGE',status:'SENDING',expectedOutputCount:4,outputs:[]}];
                  renderAssistant();
                }"""
            )
            assert "Enviando a Flow" in page.locator(".generation-batch-card").inner_text()

            page.evaluate(
                """()=>{
                  state.jobs[0]={...state.jobs[0],status:'PROCESSING',outputs:['/media/'+ 'a'.repeat(32)+'.png']};
                  renderAssistant();
                }"""
            )
            assert "Renderizando 1/4" in page.locator(".generation-batch-card").inner_text()
            assert page.locator(".generation-batch-grid img").count() == 1

            page.evaluate(
                """()=>{
                  state.jobs[0]={...state.jobs[0],status:'COMPLETED',outputs:['a','b','c','d'].map(c=>'/media/'+c.repeat(32)+'.png')};
                  renderAssistant();
                }"""
            )
            assert "Completado" in page.locator(".generation-batch-card").inner_text()
            assert page.locator(".generation-batch-grid img").count() == 4
            assert page.locator(".generation-batch-grid").evaluate("node=>getComputedStyle(node).gridTemplateColumns.split(' ').length") == 4
            assert page.locator(".generation-batch-output").first.evaluate("node=>getComputedStyle(node).aspectRatio") == "9 / 16"
            page.set_viewport_size({"width": 760, "height": 900})
            assert page.locator(".generation-batch-grid").evaluate("node=>getComputedStyle(node).gridTemplateColumns.split(' ').length") == 2
            page.set_viewport_size({"width": 480, "height": 900})
            assert page.locator(".generation-batch-grid").evaluate("node=>getComputedStyle(node).gridTemplateColumns.split(' ').length") == 2

            parsed = page.evaluate(
                """()=>{
                  const job={providerId:'google_flow',modality:'IMAGE',parameters:{count:'4'},expectedOutputCount:4,outputs:[]};
                  parseResponse(job,{id:'flow-job',status:'in_progress',count:1,blocks:[{request_id:'native-1',count:4,status:'submitting'}]});
                  return {count:job.expectedOutputCount,blocks:job.flowBlocks,status:job.status};
                }"""
            )
            assert parsed == {"count": 4, "blocks": [{"requestId": "native-1", "count": 4, "status": "submitting"}], "status": "PROCESSING"}

            page.evaluate(
                """()=>{
                  const other=structuredClone(Agent.snapshot.plans[0]);
                  other.plan.id='different-aspect';other.plan.effectiveParams.aspect='1:1';other.plan.params.aspect='1:1';
                  Agent.snapshot.plans.push(other);state.jobs=[];renderAssistant();
                }"""
            )
            assert page.locator(".generation-batch-card").count() == 2
            assert page.locator(".agent-plan").count() == 0
            assert not errors, errors
            browser.close()
            print("PASS: compact Flow batch cards group plans, render partial outputs, preserve count metadata, and use a 4/2 responsive grid.")
    finally:
        process.terminate()
        process.wait(timeout=10)
