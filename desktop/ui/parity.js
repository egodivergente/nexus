'use strict';
const outputSelection=new Map();
const baseJobCard=jobCard;
jobCard=function(job){
 const outputs=job.outputs||[],index=Math.min(outputSelection.get(job.id)||0,Math.max(0,outputs.length-1));
 const html=baseJobCard({...job,outputs:outputs.length?[outputs[index]]:[]});
 if(outputs.length<2)return html;
 return html.replace('<div class="body">',`<div class="output-controls"><button data-output-prev="${escape(job.id)}" title="Resultado anterior" aria-label="Resultado anterior" ${index===0?'disabled':''}>&#8592;</button><span>${index+1} / ${outputs.length}</span><button data-output-next="${escape(job.id)}" title="Resultado siguiente" aria-label="Resultado siguiente" ${index===outputs.length-1?'disabled':''}>&#8594;</button></div><div class="body">`);
};
const canvasBeforeParity=renderCanvas;
renderCanvas=function(){canvasBeforeParity();const button=$('[data-action=chooseCompare]');if(button)button.textContent='Crear lote o comparar';};
const catalogBeforeParity=renderCatalog;
renderCatalog=function(){catalogBeforeParity();const button=$('[data-action=compare]');if(button)button.textContent='Crear lote o comparar';};
function batchQuantity(id){const value=Number(state.batchCounts?.[id]||1);return Number.isInteger(value)?Math.min(20,Math.max(1,value)):1;}
function renderBatch(){
 const totals={};let count=0;
 for(const p of comparePlans){const n=batchQuantity(p.model.id);count+=n;if(p.cost)totals[p.cost.currency]=(totals[p.cost.currency]||0)+p.cost.amount*n;}
 modal('<h2>Crear lote o comparar</h2><p class="agent-text">'+escape(state.draft.prompt)+'</p>'+comparePlans.map(p=>`<div class="compare-row"><div><h3>${escape(p.model.displayName)}</h3><small>${escape(providers[p.model.providerId])}</small>${p.error?`<p class="error">${escape(p.error)}</p>`:`<small>${escape(p.changes.join(' · '))}</small>`}</div><div class="batch-quantity"><label>Solicitudes<input type="number" min="1" max="20" step="1" data-batch-quantity="${escape(p.model.id)}" value="${batchQuantity(p.model.id)}"></label><strong>${p.cost?amount({...p.cost,amount:p.cost.amount*batchQuantity(p.model.id)}):'Coste pendiente'}</strong></div></div>`).join('')+`<div class="compare-bottom"><p>${count} solicitudes · <strong>${Object.entries(totals).map(([currency,amountValue])=>amount({currency,amount:amountValue})).join(' + ')||'Coste pendiente'}</strong></p><button class="primary" data-action="sendCompare" ${!count||count>120||comparePlans.some(p=>p.error||!p.cost)?'disabled':''}>Generar lote</button></div>`);
}
prepareComparison=async function(){
 if(!selection.size){toast('Selecciona un modelo.');return;}
 if(!state.draft.prompt.trim()){toast('Escribe primero tu idea en el lienzo.');return;}
 comparePlans=[];modal('<h2>Preparando lote</h2><span class="loading"></span>');
 for(const id of selection){
  const raw=models.find(m=>m.id===id);
  try{if(!raw.modalities.includes('IMAGE'))throw Error('Los lotes admiten modelos de imagen.');
   const model=await configuration(id),plan=E.adapt(state.draft,model);plan.cost=await quotePlan(plan);comparePlans.push(plan);
  }catch(error){comparePlans.push({model:raw,error:error.message});}
 }
 renderBatch();
};
const Batch={active:false,paused:false,stopped:false,sent:0,total:0};
function renderBatchProgress(){
 document.querySelector('.pc-batch')?.remove();if(!Batch.active)return;
 const bar=document.createElement('div');bar.className='pc-batch';bar.setAttribute('role','status');
 bar.innerHTML='<span>'+Batch.sent+' / '+Batch.total+' enviadas'+(Batch.paused?' · En pausa':'')+'</span><button data-batch-control="pause">'+(Batch.paused?'Reanudar':'Pausar pendientes')+'</button><button data-batch-control="stop">Detener pendientes</button>';
 document.body.append(bar);
}
document.addEventListener('click',e=>{
 const control=e.target.closest('[data-batch-control]')?.dataset.batchControl;if(!control)return;
 if(control==='pause')Batch.paused=!Batch.paused;
 if(control==='stop'){Batch.stopped=true;Batch.paused=false;}
 renderBatchProgress();
});
sendComparison=async function(){
 if(busy||!comparePlans.length||comparePlans.some(p=>p.error||!p.cost))return;
 const count=comparePlans.reduce((n,p)=>n+batchQuantity(p.model.id),0);if(count>120)throw Error('Hasta 120 solicitudes por lote.');
 if(comparePlans.some(p=>Date.now()-p.cost.time>300000)){toast('Vuelve a preparar el lote para actualizar el coste.');return;}
 busy=true;const id=crypto.randomUUID();$('#dialog').close();let sent=0;
 const queue=comparePlans.flatMap(plan=>Array.from({length:batchQuantity(plan.model.id)},()=>structuredClone(plan)));
 Object.assign(Batch,{active:true,paused:false,stopped:false,sent:0,total:count});renderBatchProgress();
 try{
  for(const plan of queue){
   while(Batch.paused&&!Batch.stopped)await new Promise(resolve=>setTimeout(resolve,100));
   if(Batch.stopped)break;
   await dispatch(plan,id);sent++;Batch.sent=sent;renderBatchProgress();
  }
  toast(`${sent} solicitudes enviadas.`+(Batch.stopped?' Pendientes detenidas.':''));
 }
 catch(error){toast(`${sent} solicitudes confirmadas. Lote detenido: ${error.message}`);}
 finally{busy=false;Batch.active=false;renderBatchProgress();navigate('history');}
};
document.addEventListener('change',e=>{
 const id=e.target.dataset.batchQuantity;if(!id)return;
 const count=Number(e.target.value);if(!Number.isInteger(count)||count<1||count>20){e.target.value=batchQuantity(id);toast('Entre 1 y 20 solicitudes por modelo.');return;}
 state.batchCounts={...state.batchCounts,[id]:count};persist();renderBatch();
});
document.addEventListener('click',e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 const id=b.dataset.outputPrev||b.dataset.outputNext;if(!id)return;
 const job=state.jobs.find(j=>j.id===id);if(!job)return;
 const n=outputSelection.get(id)||0;outputSelection.set(id,Math.min(job.outputs.length-1,Math.max(0,n+(b.dataset.outputNext?1:-1))));
 if($('#dialog').open&&job.batchId)modal('<h2>Comparativa</h2><div class="results">'+state.jobs.filter(j=>j.batchId===job.batchId).map(jobCard).join('')+'</div>');
 else render();
});
