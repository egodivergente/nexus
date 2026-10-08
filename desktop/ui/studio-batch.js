'use strict';
// Bounded concurrency: submissions already in flight settle before returning.
async function nexusRunBatch(queue,options){
 const pending=queue.map(p=>structuredClone(p)),sleep=()=>new Promise(r=>setTimeout(r,40));
 let sent=0,failed=0,inFlight=0,flowInFlight=0,error=null;
 const stopped=()=>options.isStopped();
 const update=()=>options.onUpdate({sent,failed,inFlight,pending:pending.length});
 async function worker(){
  while(pending.length&&!stopped()){
   if(options.isPaused()){await sleep();continue;}
   const index=pending.findIndex(p=>p.model.providerId!=='google_flow'||flowInFlight===0);
   if(index<0){await sleep();continue;}
   const plan=pending.splice(index,1)[0],flow=plan.model.providerId==='google_flow';
   inFlight++;if(flow)flowInFlight++;update();
   try{options.onItem?.(plan,'SENDING');const result=await options.dispatch(plan);sent++;options.onItem?.(plan,'SUBMITTED',result);}
   catch(e){failed++;error ||= e;options.onItem?.(plan,'FAILED',e);}
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
 if(initial)render();window.NexusBatch={version:1};
 const editor=document.createElement('script');editor.src='studio-order-editor.js';document.head.append(editor);
})();
