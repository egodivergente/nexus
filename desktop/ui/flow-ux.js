'use strict';
const FlowRecovery={busy:new Set()};
const descriptionBeforeFlowUX=description;
description=function(model){return model.providerId==='google_flow'&&model.shortDescription?model.shortDescription:descriptionBeforeFlowUX(model);};
const logoBeforeFlowUX=logo;
logo=function(model){return model.providerId==='google_flow'?'<img class="logo" src="logos/google.png" alt="Google">':logoBeforeFlowUX(model);};
const promptLimitsBeforeFlowUX=studioPromptLimits;
studioPromptLimits=function(){promptLimitsBeforeFlowUX();const node=$('#prompt-limits'),prompt=$('#prompt');if(node&&prompt&&loaded?.textLimits?.prompt?.maximum==null)node.textContent=Array.from(prompt.value.trim()).length+' caracteres';};
function readableJobStatus(job){
 if(FlowRecovery.busy.has(job.id)||job.providerId==='google_flow'&&job.flowRecoveryRevision&&job.status==='PROCESSING')return 'Recuperando resultado';
 if(job.uncertain)return 'Envío por comprobar';
 if(job.pollError&&['PROCESSING','QUEUED','GENERATING'].includes(job.status))return 'Estado sin actualizar';
 return {COMPLETED:'Completado',PROCESSING:'Procesando',FAILED:'No completado',QUEUED:'En cola',GENERATING:'Generando',SENDING:'Enviando',CANCELLED:'Cancelado'}[job.status]||job.status;
}
function closeResultDetails(){const id=Studio.detail;Studio.detail=null;$('.studio-details')?.remove();if(id)document.querySelector('[data-studio-detail="'+CSS.escape(id)+'"]')?.focus();}
const detailsBeforeFlowUX=studioDetails;
studioDetails=function(id){
 const old=$('.studio-details'),userOpened=!old||Studio.detail!==id,focused=old?.contains(document.activeElement);
 detailsBeforeFlowUX(id);const panel=$('.studio-details'),job=state.jobs.find(j=>j.id===id);if(!panel||!job)return;
 panel.querySelector('p.hint').textContent=(providers[job.providerId]||job.providerId)+' · '+readableJobStatus(job);
 const info=[],started=Number(job.startedAt),cost=job.estimated;
 if(Number.isFinite(started)&&started>0)info.push('<span>Creado '+escape(new Date(started).toLocaleString('es-ES'))+'</span>');
 if(cost&&Number.isFinite(cost.amount))info.push('<span>Estimación '+escape(amount(cost))+' · cargo no verificado</span>');
 panel.querySelector('h3').insertAdjacentHTML('beforebegin','<div class="ux-job-info">'+info.join('')+'</div>');
 if(job.uncertain||job.pollError)panel.querySelector('h3').insertAdjacentHTML('beforebegin','<p class="ux-notice" role="status">'+(job.uncertain?'No se confirma que el envío terminara. Comprueba el trabajo existente antes de repetir.':'No se ha podido consultar el estado actual. El último estado conocido se conserva.')+'</p>');
 if(job.providerId==='google_flow'){
  const busy=FlowRecovery.busy.has(id)||job.status==='PROCESSING'&&job.flowRecoveryRevision;
  panel.querySelector('.studio-detail-actions').insertAdjacentHTML('beforeend','<button data-flow-recover="'+escape(id)+'" '+(busy?'disabled':'')+'>'+(busy?'Recuperando…':job.status==='COMPLETED'?'Comprobar resultado':'Recuperar resultado')+'</button>');
  panel.querySelector('.studio-detail-actions').insertAdjacentHTML('afterend','<p class="hint ux-recovery-help">La recuperación consulta el mismo trabajo de Flow. No vuelve a enviar el prompt.</p>');
 }
 if(job.outputs?.length>1){const selected=Math.min(outputSelection.get(id)||0,job.outputs.length-1);panel.querySelector('.studio-detail-actions').insertAdjacentHTML('beforebegin','<div class="output-controls"><button data-output-prev="'+escape(id)+'" aria-label="Resultado anterior" '+(!selected?'disabled':'')+'>&#8592;</button><span>Resultado '+(selected+1)+' de '+job.outputs.length+'</span><button data-output-next="'+escape(id)+'" aria-label="Resultado siguiente" '+(selected===job.outputs.length-1?'disabled':'')+'>&#8594;</button></div>');}
 if(userOpened)panel.querySelector('.studio-detail-close').focus();else if(focused)panel.querySelector('[data-flow-recover]:not([disabled]),.studio-detail-close')?.focus();
};
const refreshBeforeFlowUX=refreshJobViews;
refreshJobViews=function(ids){refreshBeforeFlowUX(ids);if(Studio.detail&&ids.includes(Studio.detail)&&['library','history'].includes(view))studioDetails(Studio.detail);};
const cardBeforeFlowUX=jobCard;
jobCard=function(job){const template=document.createElement('template');template.innerHTML=cardBeforeFlowUX(job);const status=template.content.querySelector('.studio-detail-button small');if(status){status.textContent=readableJobStatus(job);status.classList.toggle('ux-attention',!!(job.uncertain||job.pollError));}return template.innerHTML;};
async function recoverFlowJob(id){
 const job=state.jobs.find(j=>j.id===id);if(!job||job.providerId!=='google_flow')throw Error('Elige un trabajo de tu cuenta Flow.');if(FlowRecovery.busy.has(id))return;
 FlowRecovery.busy.add(id);if(Studio.detail===id)studioDetails(id);
 try{const response=await rpc('flowRecover',{request_id:job.remoteId||job.id});parseResponse(job,response);delete job.pollError;await flush();pcSchedule.delete(id);refreshJobViews([id]);toast(job.status==='COMPLETED'?'Resultado comprobado y disponible.':job.uncertain?'El trabajo sigue pendiente de comprobar. No se ha reenviado.':'Recuperación iniciada. Puedes seguirla en Actividad.');}
 finally{FlowRecovery.busy.delete(id);if(Studio.detail===id)studioDetails(id);}
}
const renderPCBeforeFlowUX=renderPC;
renderPC=function(){
 renderPCBeforeFlowUX();const filtering=Boolean(PC.search||PC.provider||PC.kind||PC.status),jobs=pcJobs();
 if(filtering)$('.pc-toolbar')?.insertAdjacentHTML('beforeend','<button class="small" data-ux="clear-filters">Quitar filtros</button>');
 const count=$('.pc-results-count');if(count)count.textContent=jobs.length+' resultados'+(jobs.length>PC.limit?' · '+Math.min(PC.limit,jobs.length)+' visibles':'');
 const empty=$('#content .empty');if(empty)empty.innerHTML=filtering?'<h2>No hay coincidencias</h2><p>Prueba otra búsqueda o quita los filtros.</p><button data-ux="clear-filters">Quitar filtros</button>':state.jobs.length&&Studio.tab==='results'?'<h2>Tus solicitudes están en Actividad</h2><p>Aquí aparecerán sus resultados cuando estén disponibles.</p><button data-studio-tab="activity">Ver actividad</button>':'<h2>Tu biblioteca está vacía</h2><p>Elige un modelo para crear tu primer resultado.</p><button data-nav="canvas">Crear</button>';
};
renderLibrary=renderPC;renderHistory=function(){Studio.tab='activity';renderPC();};
Studio.pickerProvider='';Studio.pickerFamily='';Studio.pickerLimit=50;
const pickerBeforeFlowUX=studioPicker;
studioPicker=function(){Studio.pickerProvider='';Studio.pickerFamily='';Studio.pickerLimit=50;pickerBeforeFlowUX();$('.studio-picker-toolbar').insertAdjacentHTML('beforeend','<select id="ux-model-provider" aria-label="Proveedor de modelos">'+pcOptions([['','Todos los proveedores'],...Object.entries(providers)],'')+'</select><select id="ux-model-family" aria-label="Familia de modelos"></select>');studioModelList();};
function pickerModels(){return models.filter(m=>!forbiddenModel(m)&&m.modalities.includes(Studio.pickerKind)&&!state.hidden.includes(m.id)&&(!Studio.pickerProvider||m.providerId===Studio.pickerProvider)&&(!Studio.reference||m.supportsImageInput||m.contractMedia?.some(f=>f.mime?.startsWith('image/'))));}
studioModelList=function(){
 const providerSelector=$('#ux-model-provider');if(providerSelector&&providerSelector.value!==Studio.pickerProvider)providerSelector.value=Studio.pickerProvider;
 const candidates=pickerModels(),family=m=>m.modelFamily||m.family||'Otros',families=[...new Set(candidates.map(family))].sort((a,b)=>a.localeCompare(b,'es'));
 if(Studio.pickerFamily&&!families.includes(Studio.pickerFamily))Studio.pickerFamily='';const selector=$('#ux-model-family');if(selector)selector.innerHTML=pcOptions([['','Todas las familias'],...families.map(f=>[escape(f),escape(f)])],escape(Studio.pickerFamily));
 const q=Studio.pickerQuery.trim().toLocaleLowerCase(),list=candidates.filter(m=>(!Studio.pickerFamily||family(m)===Studio.pickerFamily)&&(!q||[m.displayName,m.id,providers[m.providerId],family(m)].join(' ').toLocaleLowerCase().includes(q))).sort((a,b)=>Number(state.favorites.includes(b.id))-Number(state.favorites.includes(a.id))||Number(state.recent.includes(b.id))-Number(state.recent.includes(a.id)));
 $('#studio-model-list').innerHTML='<p class="hint ux-picker-count" role="status">'+list.length+(list.length===1?' modelo':' modelos')+(Studio.reference?' compatibles con tu imagen':'')+'</p>'+list.slice(0,Studio.pickerLimit).map(m=>'<button class="studio-model-row" data-studio-model="'+escape(m.id)+'">'+logo(m)+'<span><strong>'+escape(m.displayName)+'</strong><small>'+escape(providers[m.providerId])+' · '+escape(description(m))+'</small></span><span class="studio-price">'+(m.basePriceUsd!=null?'Base '+money(m.basePriceUsd):'Consultar coste')+'</span></button>').join('')+(list.length>Studio.pickerLimit?'<button data-ux="more-models">Mostrar más modelos</button>':'')+(!list.length?'<p>No hay modelos con estos filtros.</p><button data-ux="reset-models">Quitar filtros</button>':'');
};
document.addEventListener('change',e=>{const field={'ux-model-provider':'pickerProvider','ux-model-family':'pickerFamily'}[e.target.id];if(field){Studio[field]=e.target.value;Studio.pickerLimit=50;studioModelList();}if(e.target.id==='studio-model-kind'){Studio.pickerLimit=50;studioModelList();}});
document.addEventListener('input',e=>{if(e.target.id==='studio-model-search'){Studio.pickerLimit=50;studioModelList();}});
document.addEventListener('click',async e=>{
 const button=e.target.closest('button');if(!button||button.disabled)return;
 try{
  if(button.dataset.flowRecover)await recoverFlowJob(button.dataset.flowRecover);
  if(button.dataset.ux==='clear-filters'){PC.search='';PC.provider='';PC.kind='';PC.status='';PC.limit=36;renderPC();}
  if(button.dataset.ux==='more-models'){Studio.pickerLimit+=50;studioModelList();}
  if(button.dataset.ux==='reset-models'){Studio.pickerQuery='';Studio.pickerProvider='';Studio.pickerFamily='';Studio.pickerLimit=50;$('#studio-model-search').value='';studioModelList();}
 }catch(error){toast(error.message);}
});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('#dialog').open&&Studio.detail){e.preventDefault();closeResultDetails();}});
document.addEventListener('click',e=>{if(e.target.closest('[data-studio="close-detail"]'))closeResultDetails();},true);
