"""Browser regression for compact, native Google Flow image batches.

The test only drives the local offline host. It never calls Flow or another
provider: the in-page bridge is replaced while checking that the four ready
plans are consolidated into one native ``count=4`` request before confirmation.
"""
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


def grid_columns(page):
    return page.evaluate(
        "getComputedStyle(document.querySelector('.generation-batch-grid')).gridTemplateColumns"
    ).split()


with tempfile.TemporaryDirectory() as folder:
    data = Path(folder)
    process = subprocess.Popen(
        [
            sys.executable,
            str(root / "local_host.py"),
            "--headless",
            "--offline-qa",
            "--data-dir",
            str(data),
        ],
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
                  const prompt='Una joven caminando por Gran Vía al amanecer, fotografía editorial cinematográfica con luz suave, detalles urbanos y composición vertical.';
                  const plan=id=>({
                    id,
                    ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'},
                    modelName:'Nano Banana Pro',
                    modality:'IMAGE',
                    prompt,
                    params:{aspect:'9:16',count:'1'},
                    effectiveParams:{aspect:'9:16',count:'1'},
                    estimate:{basis:'Incluido'},
                    warnings:[]
                  });
                  Agent.restored=true;
                  Agent.busy=false;
                  Agent.generating=false;
                  Agent.cardAnchors.clear();
                  Agent.expandedBatches.clear();
                  Agent.items=[
                    {role:'user',text:'Genera cuatro variaciones'},
                    {role:'assistant',text:'Preparé el lote'}
                  ];
                  state.agentConversation=null;
                  state.jobs=[];
                  Agent.snapshot={
                    offline:false,
                    mode:'AGENT',
                    skills:[],
                    index:{projects:[],assets:[]},
                    plans:['p1','p2','p3','p4'].map(id=>({state:'PREPARED',fingerprint:'fp-'+id,plan:plan(id)})),
                    proposals:[]
                  };
                  view='assistant';
                  render();
                }"""
            )

            card = page.locator(".generation-batch-card")
            assert card.count() == 1
            assert page.locator(".agent-flow-result").count() == 0
            assert page.locator(".agent-plan").count() == 0
            assert page.locator("[data-agent-batch-generate]").count() == 1
            assert page.locator("[data-agent-batch-generate]").inner_text() == "Generar 4"
            assert page.locator("[data-agent=generate-ready]").count() == 0
            assert page.evaluate("!document.querySelector('#dialog').open")
            assert "Listo para generar" in card.inner_text()
            assert page.locator(".generation-batch-chips span").all_inner_texts() == [
                "Nano Banana Pro",
                "Google Flow",
                "9:16",
                "Incluido",
                "4 imágenes",
            ]
            assert page.locator("[data-agent-batch-edit]").inner_text() == "Editar prompt"
            assert page.locator("[data-agent-batch-regenerate]").inner_text() == "Regenerar 4"
            assert page.locator("[data-agent-batch-canvas]").inner_text() == "Abrir en lienzo"
            assert page.locator("[data-agent-prompt-toggle]").inner_text() == "Ver más"
            page.locator("[data-agent-prompt-toggle]").click()
            assert "is-expanded" in page.locator(".generation-batch-prompt").get_attribute("class")

            # The card belongs after the assistant response from its originating turn,
            # not below later conversation turns added afterwards.
            page.evaluate(
                """()=>{
                  Agent.items.push(
                    {role:'user',text:'Esta pregunta llegó después'},
                    {role:'assistant',text:'Esta respuesta llegó después'}
                  );
                  renderAssistant();
                }"""
            )
            assert page.evaluate(
                """()=>{
                  const children=[...document.querySelector('.agent-messages').children];
                  const index=text=>children.findIndex(node=>node.textContent.includes(text));
                  const cardIndex=children.findIndex(node=>node.matches('.generation-batch-card'));
                  return index('Preparé el lote') < cardIndex && cardIndex < index('Esta pregunta llegó después');
                }"""
            )

            # A click path must coalesce four equivalent Flow plans before the one
            # lifecycle confirmation. This remains an offline test by replacing the
            # bridge and the quote/model helpers for the duration of the call.
            consolidation = page.evaluate(
                """async()=>{
                  const calls=[];
                  const original={agentCall,agentModel,quotePlan,flush};
                  try{
                    window.agentCall=async(op,args={})=>{
                      calls.push({op,args:structuredClone(args)});
                      const entry=Agent.snapshot.plans.find(candidate=>candidate.plan.id===args.id);
                      if(op==='edit'){
                        entry.plan.prompt=args.prompt;
                        entry.plan.params=structuredClone(args.params);
                        entry.plan.effectiveParams=structuredClone(args.params);
                        entry.fingerprint='merged-flow-batch';
                      }else if(op==='discard'){
                        entry.state='DISCARDED';
                      }else if(op==='confirm'){
                        entry.state='SUBMITTED';
                      }
                      return {ok:true};
                    };
                    window.agentModel=async()=>({providerId:'google_flow',id:'google-flow/nano-pro',fields:[]});
                    window.quotePlan=async()=>({amount:null,currency:'flow_account',detail:'Incluido'});
                    window.flush=async()=>{};
                    const batch=findGenerationBatch('group:p1|p2|p3|p4');
                    await generateGenerationBatch(batch);
                    const leader=Agent.snapshot.plans.find(entry=>entry.plan.id==='p1');
                    return {
                      calls,
                      leaderCount:leader.plan.params.count,
                      leaderEffectiveCount:leader.plan.effectiveParams.count,
                      discarded:Agent.snapshot.plans.filter(entry=>entry.state==='DISCARDED').map(entry=>entry.plan.id),
                      submitted:leader.state,
                      renderedBatches:document.querySelectorAll('.generation-batch-card').length
                    };
                  }finally{
                    window.agentCall=original.agentCall;
                    window.agentModel=original.agentModel;
                    window.quotePlan=original.quotePlan;
                    window.flush=original.flush;
                  }
                }"""
            )
            assert [call["op"] for call in consolidation["calls"]] == [
                "edit",
                "discard",
                "discard",
                "discard",
                "confirm",
            ]
            assert consolidation["calls"][0]["args"]["params"]["count"] == "4"
            assert consolidation["leaderCount"] == "4"
            assert consolidation["leaderEffectiveCount"] == "4"
            assert consolidation["discarded"] == ["p2", "p3", "p4"]
            assert consolidation["submitted"] == "SUBMITTED"
            assert consolidation["renderedBatches"] == 1

            # State labels stay tied to actual lifecycle work and existing media is
            # surfaced directly in the compact grid.
            page.evaluate(
                """()=>{
                  state.jobs=[{
                    id:'flow-job',agentPlanId:'p1',providerId:'google_flow',modelId:'google-flow/nano-pro',
                    modality:'IMAGE',status:'SENDING',expectedOutputCount:4,outputs:[]
                  }];
                  renderAssistant();
                }"""
            )
            assert "Enviando a Flow" in card.inner_text()

            page.evaluate(
                """()=>{
                  state.jobs[0].status='PROCESSING';
                  state.jobs[0].outputs=['/media/'+'a'.repeat(32)+'.png'];
                  renderAssistant();
                }"""
            )
            assert "Renderizando 1/4" in card.inner_text()
            assert page.locator(".generation-batch-grid img").count() == 1

            page.evaluate(
                """()=>{
                  state.jobs[0].status='COMPLETED';
                  state.jobs[0].outputs=['a','b','c','d'].map(value=>'/media/'+value.repeat(32)+'.png');
                  renderAssistant();
                }"""
            )
            assert "Completado" in card.inner_text()
            assert page.locator(".generation-batch-grid img").count() == 4
            assert page.evaluate(
                "getComputedStyle(document.querySelector('.generation-batch-output')).aspectRatio"
            ).replace(" ", "") == "9/16"
            thumbnail = page.locator(".generation-batch-output").first.bounding_box()
            assert thumbnail and abs(thumbnail["height"] / thumbnail["width"] - 16 / 9) < 0.02
            assert len(grid_columns(page)) == 4
            for width in [760, 480]:
                page.set_viewport_size({"width": width, "height": 900})
                assert len(grid_columns(page)) == 2
                assert page.evaluate("document.documentElement.scrollWidth<=innerWidth")

            # Orders created by an earlier version may retain one job per image.
            # They must still collapse into one batch after those jobs finish.
            page.set_viewport_size({"width": 1360, "height": 900})
            page.evaluate(
                """()=>{
                  const prompt='Una joven caminando por Gran Vía al amanecer, fotografía editorial cinematográfica con luz suave, detalles urbanos y composición vertical.';
                  const plan=id=>({
                    id,
                    ref:{providerId:'google_flow',modelId:'google-flow/nano-pro'},
                    modelName:'Nano Banana Pro', modality:'IMAGE', prompt,
                    params:{aspect:'9:16',count:'1'}, effectiveParams:{aspect:'9:16',count:'1'},
                    estimate:{basis:'Incluido'}, warnings:[]
                  });
                  Agent.snapshot.plans=['legacy-1','legacy-2','legacy-3','legacy-4'].map(id=>({state:'SUBMITTED',plan:plan(id)}));
                  state.agentConversation=null;
                  Agent.cardAnchors.clear();
                  for(const entry of Agent.snapshot.plans)Agent.cardAnchors.set('plan:'+entry.plan.id,Agent.items[1]);
                  state.jobs=Agent.snapshot.plans.map((entry,index)=>({
                    id:'legacy-job-'+index, agentPlanId:entry.plan.id, providerId:'google_flow',
                    modelId:'google-flow/nano-pro', modality:'IMAGE', status:'COMPLETED',
                    expectedOutputCount:1, outputs:['/media/'+String(index + 1).repeat(32)+'.png']
                  }));
                  renderAssistant();
                }"""
            )
            assert page.locator(".generation-batch-card").count() == 1
            assert page.locator(".generation-batch-grid img").count() == 4
            assert "Completado" in card.inner_text()

            # A differing aspect ratio is deliberately a separate batch.
            page.evaluate(
                """()=>{
                  const other=structuredClone(Agent.snapshot.plans[0]);
                  other.plan.id='different-aspect';
                  other.plan.params.aspect='1:1';
                  other.plan.effectiveParams.aspect='1:1';
                  other.state='PREPARED';
                  Agent.snapshot.plans.push(other);
                  renderAssistant();
                }"""
            )
            assert page.locator(".generation-batch-card").count() == 2
            assert page.locator(".agent-flow-result").count() == 0
            assert not errors, errors
            browser.close()
            print(
                "PASS: Flow IMAGE batches render as one compact card, coalesce four plans "
                "into count=4 before one confirmation, and preserve responsive grid states."
            )
    finally:
        process.terminate()
        process.wait(timeout=10)
