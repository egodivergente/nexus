'use strict';
const PC={search:'',provider:'',kind:'',status:'',limit:36,selected:new Map()};
const cardBeforePC=jobCard;
jobCard=function(job){
 const index=outputSelection.get(job.id)||0,output=job.outputs?.[index],url=typeof output==='string'?output:output?.url;
 const key=job.id+':'+index;
 return cardBeforePC(job).replace('<article class="result">','<article class="result" data-job="'+escape(job.id)+'">')
 .replace('<div class="body">','<div class="body">'+(url&&isMediaUrl(url)?'<button class="small pc-select" data-pc-compare="'+escape(job.id)+'" aria-pressed="'+PC.selected.has(key)+'">'+(PC.selected.has(key)?'Seleccionado':'Comparar resultado')+'</button>':''));
};
function pcJobs(){
 const query=PC.search.trim().toLocaleLowerCase();
 return state.jobs.filter(j=>(view!=='library'||j.outputs?.length||!['COMPLETED','FAILED','CANCELLED'].includes(j.status)||j.uncertain||j.parameters?.__tracking_paused==='true')&&(!PC.provider||j.providerId===PC.provider)&&(!PC.kind||j.modality===PC.kind)&&(!PC.status||j.status===PC.status)&&(!query||[j.prompt,j.name,j.modelId].join(' ').toLocaleLowerCase().includes(query)));
}
function pcOptions(values,current){return values.map(([id,label])=>'<option value="'+id+'" '+(id===current?'selected':'')+'>'+label+'</option>').join('');}
function renderPC(){
 const jobs=pcJobs();
 $('#content').innerHTML='<div class="pc-toolbar"><input id="pc-search" type="search" aria-label="Buscar resultados" placeholder="Buscar por prompt o modelo" value="'+escape(PC.search)+'"><select id="pc-provider" aria-label="Proveedor">'+pcOptions([['','Todos los proveedores'],...Object.entries(providers)],PC.provider)+'</select><select id="pc-kind" aria-label="Formato">'+pcOptions([['','Todos los formatos'],['IMAGE','Imagen'],['VIDEO','Video'],['AUDIO','Audio'],['CHAT','Texto']],PC.kind)+'</select><select id="pc-status" aria-label="Estado">'+pcOptions([['','Todos los estados'],['COMPLETED','Completado'],['PROCESSING','Procesando'],['QUEUED','En cola'],['FAILED','Revisar envio']],PC.status)+'</select></div><div class="summary"><span class="pc-results-count">'+jobs.length+' resultados</span><div class="toolbar"><button data-pc="open-compare" '+(PC.selected.size<2?'disabled':'')+'>Comparar ('+PC.selected.size+'/4)</button><button data-pc="clear" '+(!PC.selected.size?'disabled':'')+'>Limpiar seleccion</button>'+(view==='history'?'<button data-action="refreshJobs">Actualizar estados</button>':'')+'</div></div><div class="results">'+jobs.slice(0,PC.limit).map(jobCard).join('')+'</div>'+(jobs.length>PC.limit?'<button data-pc="more">Mostrar mas</button>':'')+(!jobs.length?'<div class="empty"><h2>Sin resultados</h2></div>':'');
}
renderLibrary=renderPC;renderHistory=renderPC;
function refreshJobViews(ids){
 if(!['library','history'].includes(view))return;
 const list=pcJobs(),visible=list.slice(0,PC.limit),grid=$('#content .results');if(!grid)return;
 const wanted=new Set(visible.map(j=>j.id));
 grid.querySelectorAll('[data-job]').forEach(n=>{if(!wanted.has(n.dataset.job))n.remove();});
 for(const [i,job] of visible.entries()){
  let node=[...grid.children].find(n=>n.dataset.job===job.id);
  if(!node||ids.includes(job.id)){const template=document.createElement('template');template.innerHTML=jobCard(job);const next=template.content.firstElementChild;if(node)node.replaceWith(next);node=next;}
  if(grid.children[i]!==node)grid.insertBefore(node,grid.children[i]||null);
 }
 $('.pc-results-count').textContent=list.length+' resultados';
}
const pcSchedule=new Map();
poll=async function(force=false){
 if(polling||initial.qa||(!force&&document.hidden))return;polling=true;const changed=[];
 try{
  const jobs=state.jobs.filter(j=>j.remoteId&&['QUEUED','PROCESSING','GENERATING'].includes(j.status))
   .filter(j=>force||(pcSchedule.get(j.id)?.next||0)<=Date.now()).sort((a,b)=>(pcSchedule.get(a.id)?.last||0)-(pcSchedule.get(b.id)?.last||0)).slice(0,8);
  for(const job of jobs){
   const before=JSON.stringify(job),schedule=pcSchedule.get(job.id)||{errors:0};
   try{const path=job.providerId==='runway'?'/v1/tasks/'+encodeURIComponent(job.remoteId):job.providerId==='crun'?'/api/v1/client/job/TaskInfo?task_id='+encodeURIComponent(job.remoteId):'/api/v1/model/prediction/'+encodeURIComponent(job.remoteId);
    const updated={...job};parseResponse(updated,job.providerId==='google_flow'?await rpc('flowStatus',{request_id:job.remoteId}):await rpc('request',{provider:job.providerId,path,method:'GET'}));Object.assign(job,updated);delete job.pollError;schedule.errors=0;
   }catch{job.pollError=true;schedule.errors++;}
   schedule.last=Date.now();schedule.next=schedule.last+Math.min(120000,15000*2**schedule.errors);pcSchedule.set(job.id,schedule);
   if(before!==JSON.stringify(job))changed.push(job.id);
  }
  if(changed.length){persist();refreshJobViews(changed);}
 }finally{polling=false;}
};
let pcSearchTimer;
document.addEventListener('input',e=>{
 if(e.target.id!=='pc-search')return;PC.search=e.target.value;PC.limit=36;clearTimeout(pcSearchTimer);
 pcSearchTimer=setTimeout(()=>{if(!['library','history'].includes(view))return;renderPC();$('#pc-search').focus();},180);
});
document.addEventListener('change',e=>{
 const key={'pc-provider':'provider','pc-kind':'kind','pc-status':'status'}[e.target.id];
 if(key){PC[key]=e.target.value;PC.limit=36;renderPC();}
});
document.addEventListener('click',e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 if(b.dataset.pcCompare){
  const job=state.jobs.find(j=>j.id===b.dataset.pcCompare),index=outputSelection.get(job.id)||0,key=job.id+':'+index;
  if(PC.selected.has(key))PC.selected.delete(key);else if(PC.selected.size<4)PC.selected.set(key,{...job,outputs:[job.outputs[index]]});else return toast('Hasta 4 resultados.');
  renderPC();
 }
 if(b.dataset.pc==='more'){PC.limit+=36;renderPC();}
 if(b.dataset.pc==='clear'){PC.selected.clear();renderPC();}
 if(b.dataset.pc==='open-compare'){
  modal('<h2>Comparar resultados</h2><div class="pc-comparison">'+[...PC.selected.values()].map(j=>'<section class="pc-comparison-item">'+baseJobCard(j)+'</section>').join('')+'</div>');
  $('#dialog').classList.add('pc-dialog');
 }
});
$('#dialog').addEventListener('close',()=>$('#dialog').classList.remove('pc-dialog'));

