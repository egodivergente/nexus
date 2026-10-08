'use strict';
// Bounded concurrency: submissions already in flight settle before returning.
async function nexusRunBatch(queue,options){
 const pending=queue.map(p=>structuredClone(p)),sleep=()=>new Promise(r=>setTimeout(r,40));
 let sent=0,failed=0,inFlight=0,flowInFlight=0,error=null;
 const stopped=()=>!!error||options.isStopped();
 const update=()=>options.onUpdate({sent,failed,inFlight,pending:pending.length});
 async function worker(){
  while(pending.length&&!stopped()){
   if(options.isPaused()){await sleep();continue;}
   const index=pending.findIndex(p=>p.model.providerId!=='google_flow'||flowInFlight===0);
   if(index<0){await sleep();continue;}
   const plan=pending.splice(index,1)[0],flow=plan.model.providerId==='google_flow';
   inFlight++;if(flow)flowInFlight++;update();
   try{await options.dispatch(plan);sent++;}
   catch(e){failed++;error ||= e;}
   finally{inFlight--;if(flow)flowInFlight--;update();}
  }
 }
 await Promise.all(Array.from({length:Math.min(3,pending.length)},worker));
 return {sent,failed,pending:pending.length,error};
}
if(typeof module!=='undefined'&&module.exports)module.exports={nexusRunBatch};
if(typeof document!=='undefined')(()=>{
 const previousCatalog=renderCatalog;
 renderCatalog=function(){
  previousCatalog();
  const submit=$('[data-action="compare"]');if(submit)submit.textContent=`Preparar encargo · ${selection.size} modelos`;
  document.querySelectorAll('.catalog .model').forEach(card=>{
   let add=card.querySelector('[data-compare]');
   if(!add){
    const open=card.querySelector('[data-model],[data-family]');
    const model=open?.dataset.model?models.find(m=>m.id===open.dataset.model):filteredModels().find(m=>family(m)===open?.dataset.family);
    if(!model)return;add=document.createElement('button');add.className='small';add.dataset.compare=model.id;
    add.textContent=selection.has(model.id)?'✓':'+';if(selection.has(model.id))add.classList.add('primary');card.querySelector('.card-actions')?.append(add);
   }
   add.title='Añadir al encargo';add.setAttribute('aria-label','Añadir modelo al encargo');
  });
 };
 const previousCanvas=renderCanvas;
 renderCanvas=function(){previousCanvas();let b=$('[data-action="chooseCompare"]');if(!b){b=document.createElement('button');b.dataset.action='chooseCompare';b.className='small';($('.studio-start')||$('.canvas .sheet'))?.append(b);}if(b)b.textContent='Encargar a varios modelos';};
 document.addEventListener('click',e=>{
  if(!e.target.closest('[data-action="chooseCompare"]'))return;
  e.preventDefault();e.stopImmediatePropagation();if(loaded)selection.add(loaded.id);kind=loaded?.modalities[0]||'IMAGE';navigate('catalog');
 },true);
 renderBatch=function(){
  const totals={};let count=0;
  for(const p of comparePlans){const n=batchQuantity(p.model.id);count+=n;if(p.cost)totals[p.cost.currency]=(totals[p.cost.currency]||0)+p.cost.amount*n;}
  modal('<h2>Encargo a varios modelos</h2><p class="agent-text">'+escape(state.draft.prompt)+'</p>'+comparePlans.map(p=>`<div class="compare-row"><div><h3>${escape(p.model.displayName)}</h3><small>${escape(providers[p.model.providerId])}</small>${p.error?`<p class="error">${escape(p.error)}</p>`:`<small>${escape(p.changes.join(' · '))}</small>`}</div><div class="batch-quantity"><label>Solicitudes<input type="number" min="1" max="20" step="1" data-batch-quantity="${escape(p.model.id)}" value="${batchQuantity(p.model.id)}"></label><strong>${p.cost?amount({...p.cost,amount:p.cost.amount*batchQuantity(p.model.id)}):'Coste pendiente'}</strong></div></div>`).join('')+`<div class="compare-bottom"><p>${count} solicitudes · <strong>${Object.entries(totals).map(([currency,value])=>amount({currency,amount:value})).join(' + ')||'Coste pendiente'}</strong></p><p class="hint">Hasta 3 envíos simultáneos. Flow usa una sola sesión a la vez. Pausar o detener afecta a los pendientes.</p><button class="primary" data-action="sendCompare" ${!count||count>120||comparePlans.some(p=>p.error||!p.cost)?'disabled':''}>Enviar encargo</button></div>`);
 };
 prepareComparison=async function(){
  if(!selection.size)return toast('Añade los modelos que recibirán el encargo.');
  if(!state.draft.prompt.trim())return toast('Escribe primero tu encargo en Crear.');
  comparePlans=[];modal('<h2>Preparando encargo</h2><span class="loading"></span>');
  for(const id of selection){const raw=models.find(m=>m.id===id);try{const model=await configuration(id),plan=E.adapt(state.draft,model);plan.cost=await quotePlan(plan);comparePlans.push(plan);}catch(error){comparePlans.push({model:raw||{id,displayName:id},error:error.message});}}
  renderBatch();
 };
 renderBatchProgress=function(){
  document.querySelector('.pc-batch')?.remove();if(!Batch.active)return;
  const bar=document.createElement('div');bar.className='pc-batch';bar.setAttribute('role','status');
  bar.innerHTML=`<span>${Batch.sent} / ${Batch.total} confirmadas · ${Batch.inFlight||0} enviándose${Batch.paused?' · En pausa':''}${Batch.stopped?' · Pendientes detenidos':''}</span><button data-batch-control="pause" ${Batch.stopped?'disabled':''}>${Batch.paused?'Reanudar':'Pausar pendientes'}</button><button data-batch-control="stop" ${Batch.stopped?'disabled':''}>Detener pendientes</button>`;document.body.append(bar);
 };
 sendComparison=async function(){
  if(busy||!comparePlans.length||comparePlans.some(p=>p.error||!p.cost))return;
  const count=comparePlans.reduce((n,p)=>n+batchQuantity(p.model.id),0);if(count>120)return toast('Hasta 120 solicitudes por encargo.');
  if(comparePlans.some(p=>Date.now()-p.cost.time>300000))return toast('Vuelve a preparar el encargo para actualizar el coste.');
  const queue=[];for(let round=0;round<20;round++)for(const plan of comparePlans)if(round<batchQuantity(plan.model.id))queue.push(structuredClone(plan));
  busy=true;const id=crypto.randomUUID();$('#dialog').close();Object.assign(Batch,{active:true,paused:false,stopped:false,sent:0,total:count,inFlight:0});renderBatchProgress();
  try{
   const r=await nexusRunBatch(queue,{dispatch:p=>dispatch(p,id),isPaused:()=>Batch.paused,isStopped:()=>Batch.stopped,onUpdate:p=>{Object.assign(Batch,p);renderBatchProgress();}});
   toast(`${r.sent} solicitudes confirmadas.`+(r.error?` Envío fallido: ${r.error.message}. ${r.pending} pendientes sin enviar.`:r.pending?` ${r.pending} pendientes sin enviar.`:''));
  }finally{busy=false;Batch.active=false;renderBatchProgress();navigate('history');}
 };
 if(initial)render();window.NexusBatch={version:1};
})();
