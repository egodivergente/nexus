'use strict';
(()=>{
 const U={quoting:false,recovered:false,sourceKey:null,edits:new Map()},q=()=>state.lastOrderRun;
 function stash(p){U.edits.set(p.model.id,structuredClone({prompt:p.prompt,values:p.values,media:p.media,unmapped:p.unmapped,quantity:p.quantity}));}
 function validation(p){if(p.setupError)return p.setupError;if(p.unmapped?.length)return 'Asigna o retira las referencias sin entrada compatible.';return !Number.isInteger(p.quantity)||p.quantity<1||p.quantity>20?'Entre 1 y 20 solicitudes por modelo.':E.validate(p.model,p.prompt,p.values,p.media);}
 function field(p,param,i){
  const key=escape(param.key),value=p.values[param.key]??param.defaultValue??'',id=`order-${i}-${key}`,attr=`id="${id}" data-order-param="${key}"`;
  const options=param.options?.length?param.options:param.nativeType==='boolean'||param.type==='BOOLEAN'?['true','false']:null;
  return `<label for="${id}">${escape(param.displayName)}${param.isRequired?' · obligatorio':''}${options?`<select ${attr}><option value="">Automático</option>${options.map(v=>`<option value="${escape(v)}" ${String(value)===String(v)?'selected':''}>${v==='true'?'Sí':v==='false'?'No':escape(v)}</option>`).join('')}</select>`:`<input ${attr} value="${escape(value)}" type="${['integer','number'].includes(param.nativeType)?'number':'text'}" ${param.nativeType==='number'?'step="any"':''} ${param.minValue!=null?`min="${param.minValue}"`:''} ${param.maxValue!=null?`max="${param.maxValue}"`:''}>`}</label>`;
 }
 function fresh(p){return p.cost&&Date.now()-p.cost.time<=300000;}
 function summary(){
  const totals={};let count=0;
  for(const p of comparePlans){if(Number.isInteger(p.quantity)&&p.quantity>0&&p.quantity<=20){count+=p.quantity;if(fresh(p))totals[p.cost.currency]=(totals[p.cost.currency]||0)+p.cost.amount*p.quantity;}}
  const total=Object.entries(totals).map(([currency,value])=>amount({currency,amount:value})).join(' + ');
  return {count,total:total?(total+(comparePlans.some(p=>!fresh(p)||validation(p))?' · parcial':'')):'Coste pendiente',ready:count>0&&count<=120&&!U.quoting&&!comparePlans.some(p=>validation(p)||!fresh(p))};
 }
 function footer(){
  if(!$('#order-summary'))return;const s=summary();
  $('#order-summary').innerHTML=`<strong>${s.count} solicitudes · ${s.total}</strong><span>${U.quoting?'Consultando costes…':s.ready?'Listo para enviar':'Revisa las fichas y actualiza los costes.'}</span>`;
  $('#order-send').disabled=!s.ready;$('#order-quote').disabled=U.quoting;
  document.querySelectorAll('[data-order-index]').forEach(card=>{const p=comparePlans[Number(card.dataset.orderIndex)],error=validation(p)||p.quoteError,notice=card.querySelector('.order-validation');notice.textContent=error||(!fresh(p)?'Coste pendiente de actualizar.':'Ajustes válidos');notice.classList.toggle('is-ready',!error&&fresh(p));card.classList.toggle('is-invalid',!!error);card.querySelector('.order-price').textContent=fresh(p)?amount({...p.cost,amount:p.cost.amount*p.quantity}):'Coste pendiente';});
 }
 renderBatch=function(){
  modal(`<div class="order-heading"><div><small>CREAR · ENCARGO</small><h2>Encargo a varios modelos</h2><p class="hint">Un prompt y ajustes propios por modelo.</p></div></div><div class="order-grid">${comparePlans.map((p,i)=>`<section class="order-card" data-order-index="${i}"><header><div><h3>${escape(p.model.displayName)}</h3><small>${escape(providers[p.model.providerId]||p.model.providerId)}</small></div><strong class="order-price"></strong></header>${p.setupError?`<p class="order-validation">${escape(p.setupError)}</p>`:`<label>Prompt<textarea data-order-prompt rows="4">${escape(p.prompt)}</textarea></label><div class="order-parameters">${(p.model.parameters||[]).map(param=>field(p,param,i)).join('')}<label>Solicitudes<input data-order-quantity type="number" min="1" max="20" value="${p.quantity}"></label></div>${p.changes?.length?`<details><summary>Ajustes adaptados</summary><p class="hint">${escape(p.changes.join(' · '))}</p></details>`:''}<div class="order-references">${(p.model.fields||[]).map(f=>`<div><span>${escape(f.label)}${f.required?' · obligatorio':''}</span><button data-order-pick="${escape(f.key)}">Añadir referencia</button>${p.media.map((m,index)=>m.field===f.key?`<div class="order-file">${escape(m.name||'Referencia')}<button data-order-remove="${index}" aria-label="Retirar referencia">✕</button></div>`:'').join('')}</div>`).join('')}${(p.unmapped||[]).map((m,index)=>`<div class="order-file">${escape(m.name||'Referencia sin asignar')}<select data-order-map="${index}" aria-label="Entrada para referencia"><option value="">Entrada sin asignar</option>${p.model.fields.filter(f=>m.mime?.startsWith(f.mime.split('/')[0]+'/')).map(f=>`<option value="${escape(f.key)}">${escape(f.label)}</option>`).join('')}</select><button data-order-unmap-remove="${index}" aria-label="Retirar referencia sin asignar">✕</button></div>`).join('')}</div><p class="order-validation" role="status"></p>`}</section>`).join('')}</div><footer class="order-footer"><div id="order-summary" aria-live="polite"></div><div><button id="order-quote" data-order-action="quote">Actualizar costes</button><button class="primary" id="order-send" data-action="sendCompare">Enviar encargo</button></div><small>Hasta 3 envíos simultáneos. Flow se ejecuta en una cola compartida. El coste es una estimación.</small></footer>`);
  $('#dialog').classList.add('order-dialog');footer();
 };
 $('#dialog').addEventListener('close',()=>$('#dialog').classList.remove('order-dialog','order-run-dialog'));
 async function prices(){
  if(U.quoting)return;U.quoting=true;footer();
  try{for(const p of comparePlans){p.cost=null;p.quoteError=null;if(validation(p))continue;const revision=p.revision;try{const cost=await quotePlan(structuredClone(p));if(revision===p.revision)p.cost=cost;}catch(error){if(revision===p.revision)p.quoteError=error.message;}}}
  finally{U.quoting=false;footer();for(const [i,p] of comparePlans.entries())if(p.quoteError)document.querySelector(`[data-order-index="${i}"] .order-validation`).textContent=p.quoteError;}
 }
 prepareComparison=async function(){
  if(!selection.size)return toast('Añade los modelos que recibirán el encargo.');
  comparePlans=[];modal('<h2>Preparando encargo</h2><span class="loading"></span>');
  const source=structuredClone(state.draft);
  const sourceKey=JSON.stringify(source);if(sourceKey!==U.sourceKey){U.edits.clear();U.sourceKey=sourceKey;}
  for(const id of selection){const raw=models.find(m=>m.id===id);try{
   const model=await configuration(id);let adapted;
   try{adapted=E.adapt({...source,media:[]},model);}catch{adapted={model,prompt:source.prompt,values:E.defaults(model),media:[],changes:[]};for(const param of model.parameters)if(source.values[param.key]!=null)adapted.values[param.key]=source.values[param.key];}
   const p={...adapted,media:[],unmapped:[],quantity:batchQuantity(id),revision:0,cost:null};
   for(const file of source.media){const fields=model.fields.filter(f=>file.mime?.startsWith(f.mime.split('/')[0]+'/')),target=fields.find(f=>f.key===file.field)||(fields.length===1?fields[0]:null);if(target)p.media.push({...file,field:target.key});else p.unmapped.push({...file});}
   if(U.edits.has(id))Object.assign(p,structuredClone(U.edits.get(id)));
   comparePlans.push(p);
  }catch(error){comparePlans.push({model:raw||{id,displayName:id},setupError:error.message,quantity:batchQuantity(id),revision:0,cost:null});}}
  renderBatch();await prices();
 };
 function changed(p){p.revision++;p.cost=null;p.quoteError=null;stash(p);footer();}
 document.addEventListener('input',e=>{
  const card=e.target.closest('[data-order-index]');if(!card)return;const p=comparePlans[Number(card.dataset.orderIndex)],t=e.target;
  if(t.hasAttribute('data-order-prompt')){p.prompt=t.value;changed(p);}
  if(t.dataset.orderParam!==undefined){p.values[t.dataset.orderParam]=t.value;changed(p);}
  if(t.hasAttribute('data-order-quantity')){p.quantity=Number(t.value);if(Number.isInteger(p.quantity)&&p.quantity>=1&&p.quantity<=20){state.batchCounts={...state.batchCounts,[p.model.id]:p.quantity};persist();}stash(p);footer();}
 });
 document.addEventListener('change',e=>{
  const card=e.target.closest('[data-order-index]');if(!card||e.target.dataset.orderMap===undefined)return;const p=comparePlans[Number(card.dataset.orderIndex)],i=Number(e.target.dataset.orderMap),key=e.target.value;if(!key)return;
  const file=p.unmapped.splice(i,1)[0];p.media.push({...file,field:key});changed(p);renderBatch();
 });
 async function metadata(file){
  if(!/^(audio|video)\//.test(file.mime))return file;
  file.duration=await new Promise(resolve=>{const el=document.createElement(file.mime.startsWith('video/')?'video':'audio');const done=value=>{clearTimeout(timer);el.removeAttribute('src');el.load();resolve(value);},timer=setTimeout(()=>done(null),6000);el.preload='metadata';el.onloadedmetadata=()=>done(Number.isFinite(el.duration)?el.duration:null);el.onerror=()=>done(null);el.src=file.url;});return file;
 }
 document.addEventListener('click',async e=>{
  const b=e.target.closest('button');if(!b||b.disabled)return;
  if(b.dataset.orderAction==='quote')return prices();
  if(b.dataset.orderAction==='run')return showRun();
  if(b.dataset.orderJob){$('#dialog').close();Studio.tab='results';navigate('library');studioDetails(b.dataset.orderJob);return;}
  const card=b.closest('[data-order-index]');if(!card)return;const p=comparePlans[Number(card.dataset.orderIndex)];
  try{
   if(b.dataset.orderRemove!==undefined){p.media.splice(Number(b.dataset.orderRemove),1);changed(p);renderBatch();}
   if(b.dataset.orderUnmapRemove!==undefined){p.unmapped.splice(Number(b.dataset.orderUnmapRemove),1);changed(p);renderBatch();}
   if(b.dataset.orderPick){const f=p.model.fields.find(f=>f.key===b.dataset.orderPick),files=await rpc('pick');if(files.some(file=>!file.mime.startsWith(f.mime.split('/')[0]+'/')))throw Error('Revisa el formato de '+f.label+'.');if(p.media.filter(m=>m.field===f.key).length+files.length>f.maximum)throw Error('Máximo '+f.maximum+' referencias.');for(const file of files)p.media.push({...await metadata(file),field:f.key});changed(p);renderBatch();}
  }catch(error){toast(error.message);}
 });
 function jobFor(task){return state.jobs.find(j=>j.id===task.jobId||j.orderRequestId===task.id);}
 function taskLabel(task){const job=jobFor(task);if(job){if(job.uncertain)return 'Envío no confirmado';return ({SENDING:'Enviándose',QUEUED:'Enviado · En cola',PROCESSING:'Enviado · Procesando',GENERATING:'Enviado · Generando',COMPLETED:'Completado',FAILED:'Fallido'})[job.status]||job.status;}return ({PENDING:'Pendiente',SENDING:'Enviándose',SUBMITTED:'Enviado',FAILED:'Envío no confirmado',NOT_SENT:'Sin enviar'})[task.status]||task.status;}
 let runSignature='';
 function runRows(){
  if(!$('#order-run-rows')||!q())return;const signature=JSON.stringify(q().requests.map(t=>[t.id,taskLabel(t),t.error,t.jobId]));if(signature===runSignature)return;runSignature=signature;
  $('#order-run-rows').innerHTML=q().requests.map(t=>`<tr><td>${escape(t.name)}<small>Solicitud ${t.number}</small></td><td><span class="order-task-state">${escape(taskLabel(t))}</span>${t.error?`<small>${escape(t.error)}</small>`:''}</td><td>${jobFor(t)?.outputs?.length?`<button data-order-job="${escape(jobFor(t).id)}">Abrir resultado</button>`:''}</td></tr>`).join('');
 }
 function showRun(){if(!q())return;runSignature='';modal(`<h2>Solicitudes del encargo</h2><p class="hint">Enviadas, pendientes y resultados de cada modelo. Detener no cancela lo que ya recibió el proveedor.</p><div class="order-run-table"><table><thead><tr><th>Modelo</th><th>Estado</th><th>Resultado</th></tr></thead><tbody id="order-run-rows"></tbody></table></div>`);$('#dialog').classList.add('order-run-dialog');runRows();}
 renderBatchProgress=function(){
  document.querySelector('.pc-batch')?.remove();if(!Batch.active&&!q())return;
  const tasks=q()?.requests||[],confirmed=tasks.filter(t=>t.status==='SUBMITTED').length,waiting=tasks.filter(t=>t.status==='PENDING').length;
  if(Batch.active)$('#order-latest')?.remove();
  else if(q()&&$('#header-actions')){let button=$('#order-latest');if(!button){button=document.createElement('button');button.id='order-latest';button.dataset.orderAction='run';$('#header-actions').append(button);}button.textContent='Último encargo · '+tasks.length;}
  if(!Batch.active){runRows();return;}
  const bar=document.createElement('div');bar.className='pc-batch';bar.innerHTML=`<span>${Batch.active?Batch.sent:confirmed} / ${tasks.length||Batch.total} enviadas${Batch.active?' · '+(Batch.inFlight||0)+' enviándose · '+waiting+' pendientes':''}${Batch.paused&&Batch.active?' · En pausa':''}</span><button data-order-action="run">Ver solicitudes</button>${Batch.active?`<button data-batch-control="pause" ${Batch.stopped?'disabled':''}>${Batch.paused?'Reanudar':'Pausar pendientes'}</button><button data-batch-control="stop" ${Batch.stopped?'disabled':''}>Detener pendientes</button>`:''}`;document.body.append(bar);runRows();
 };
 sendComparison=async function(){
  if(busy||!summary().ready)return toast('Revisa los ajustes y actualiza los costes.');
  const queue=[],run={id:crypto.randomUUID(),startedAt:Date.now(),requests:[]};
  for(let round=0;round<20;round++)for(const p of comparePlans)if(round<p.quantity){const id=crypto.randomUUID();queue.push({...structuredClone(p),orderRequestId:id});run.requests.push({id,modelId:p.model.id,name:p.model.displayName,number:round+1,status:'PENDING',jobId:null});}
  busy=true;state.lastOrderRun=run;$('#dialog').close();Object.assign(Batch,{active:true,paused:false,stopped:false,sent:0,total:queue.length,inFlight:0});renderBatchProgress();
  try{
   await flush();
   const r=await nexusRunBatch(queue,{dispatch:p=>dispatch(p,run.id,{orderRequestId:p.orderRequestId}),isPaused:()=>Batch.paused,isStopped:()=>Batch.stopped,onUpdate:p=>{Object.assign(Batch,p);renderBatchProgress();},onItem:(p,status,result)=>{const task=run.requests.find(t=>t.id===p.orderRequestId);task.status=status;if(status==='SUBMITTED')task.jobId=result?.id||null;if(status==='FAILED')task.error='Comprueba el historial antes de repetir el envío.';persist();runRows();}});
   for(const task of run.requests)if(task.status==='PENDING')task.status='NOT_SENT';
   toast(`${r.sent} solicitudes enviadas.`+(r.error?' Hay un envío sin confirmar; revisa el historial.':r.pending?' '+r.pending+' pendientes sin enviar.':''));
  }catch(error){for(const task of run.requests)if(task.status==='PENDING')task.status='NOT_SENT';toast('No se pudo continuar el encargo: '+error.message);}
  finally{busy=false;Batch.active=false;renderBatchProgress();persist();navigate('history');}
 };
 const oldRender=render;render=function(){oldRender();if(initial&&!U.recovered){U.recovered=true;if(q()){for(const task of q().requests){if(task.status==='PENDING')task.status='NOT_SENT';if(task.status==='SENDING'&&!jobFor(task))task.status='FAILED';}persist();}}renderBatchProgress();};
 setInterval(()=>{if($('#order-run-rows'))runRows();if($('#order-summary'))footer();},1000);
 const style=document.createElement('link');style.rel='stylesheet';style.href='studio-order-editor.css';document.head.append(style);
 if(initial)render();window.NexusOrderEditor={version:1};
})();
