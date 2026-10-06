'use strict';
const Agent = {owner:crypto.randomUUID(),snapshot:null,settings:{},items:[],attachments:[],busy:false,callbacks:new Set(),draft:''};
const agentIcons = {
 chat:'<path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8z"/>',
 plus:'<path d="M12 5v14M5 12h14"/>',send:'<path d="m22 2-7 20-4-9-9-4 20-7ZM22 2 11 13"/>',
 clip:'<path d="m21.4 11.6-9.2 9.2a6 6 0 0 1-8.5-8.5L14.3 1.7a4 4 0 0 1 5.7 5.7L9.4 18a2 2 0 0 1-2.8-2.8l9.2-9.2"/>',
 book:'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/>'
};
const aiIcon=name=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${agentIcons[name]||icons.settings}</svg>`;
const planLabels={PREPARED:'Preparado',AWAITING_CONFIRMATION:'Pendiente de confirmar',CONFIRMED:'Confirmado',SUBMITTED:'Enviado',DISCARDED:'Descartado',UNCERTAIN:'Revisar en historial'};
function agentEditControl(p,value){
 const name='param:'+p.key,v=value??p.defaultValue??'',label=escape(p.displayName||E.label(p.key));
 if(p.options?.length||p.type==='BOOLEAN')return `<label>${label}<select name="${escape(name)}">${(p.options?.length?p.options:['true','false']).map(o=>`<option value="${escape(o)}" ${String(v)===String(o)?'selected':''}>${escape(o==='true'?'Sí':o==='false'?'No':o)}</option>`).join('')}</select></label>`;
 const numeric=['integer','number'].includes(p.nativeType)||['INTEGER','FLOAT','SEED','SLIDER'].includes(p.type);
 return `<label>${label}<input name="${escape(name)}" type="${numeric?'number':'text'}" value="${escape(v)}" ${p.minValue!=null?`min="${p.minValue}"`:''} ${p.maxValue!=null?`max="${p.maxValue}"`:''} ${numeric?'step="any"':''}></label>`;
}
const aiButton=(action,title,name,disabled=false)=>`<button class="agent-icon" data-agent="${action}" title="${title}" aria-label="${title}" ${disabled?'disabled':''}>${aiIcon(name)}</button>`;

async function agentCall(op,args={}) {
 if(Agent.busy)throw Error('Espera a que termine el asistente.');
 Agent.busy=true;if(view==='assistant')renderAssistant();
 let processing=false;
 const pump=async()=>{
  if(processing)return;processing=true;
  try {for(const request of await rpc('agentEvents',{owner:Agent.owner})) {
   if(Agent.callbacks.has(request.id))continue;Agent.callbacks.add(request.id);
   try{const value=await agentCallback(request.op,request.args);await rpc('agentReply',{owner:Agent.owner,id:request.id,ok:true,value});}
   catch(error){await rpc('agentReply',{owner:Agent.owner,id:request.id,ok:false,error:error.message});}
   finally{Agent.callbacks.delete(request.id);}
  }}catch(error){console.error('Agent bridge:',error.message);}finally{processing=false;}
 };
 const interval=setInterval(pump,180);
 try{
  const response=await rpc('agent',{op,args,owner:Agent.owner});Agent.settings=response.settings;
  Agent.snapshot=response.result.snapshot||response.result;
  return response.result;
 }finally{clearInterval(interval);Agent.busy=false;if(view==='assistant')renderAssistant();}
}
async function agentModel(ref){
 const raw=models.find(m=>m.id===ref.modelId&&m.providerId===ref.providerId);
 if(!raw)throw Error('El modelo no esta en el catalogo actual.');
 const schema=raw.providerId==='atlas_cloud'&&raw.schemaUrl?await rpc('schema',{url:raw.schemaUrl}):null;
 return E.normalize(raw,schema);
}
function agentSummary(m){
 const summary={ref:{providerId:m.providerId,modelId:m.id},name:m.displayName,
  organization:m.organization,family:m.modelFamily||m.family,modalities:m.modalities,capabilities:m.capabilities||[],
  inputMode:m.inputMode,purpose:description(m),releaseMonth:m.releaseMonth,isFlagship:!!m.isFlagship,
  supportsImageInput:!!m.supportsImageInput,supportsFirstFrame:!!m.supportsFirstFrame,supportsLastFrame:!!m.supportsLastFrame,
  supportsReferenceImages:!!m.supportsReferenceImages,supportsVideoInput:!!m.supportsVideoInput,supportsAudio:!!m.supportsAudio,
  maxImages:m.maxImages||0,mediaRequired:!!m.isMediaRequired,catalogDurations:m.supportedDurations||[],
  catalogResolutions:m.supportedResolutions||[],basePriceUsd:m.basePriceUsd,priceUnit:m.priceUnit,priceUnitVerified:!!m.priceUnitVerified};
 return Object.fromEntries(Object.entries(summary).filter(([,value])=>value!=null));
}
function agentSchema(m){
 const fields=m.fields||[],images=fields.filter(f=>f.mime==='image/*'),videos=fields.filter(f=>f.mime==='video/*');
 const first=images.find(f=>f.key===m.firstFrameField)||images.find(f=>/^(first_frame|first_frame_image|start_image|image|image_url|input_image|input_image_url)$/.test(f.key));
 const last=images.find(f=>f.key===m.lastFrameField)||images.find(f=>/^(last_frame|last_frame_image|end_image|last_image)$/.test(f.key));
 const array=images.find(f=>f.encoding==='array'||f.encoding==='atlas-references');
 const unsupported=[...(m.unsupported||[]),...fields.filter(f=>f.required&&!images.includes(f)&&!videos.includes(f)).map(f=>f.key)];
 const media={inputMode:m.inputMode,firstFrameField:first?.key,lastFrameField:last?.key,inputImageField:first?.key,
  imagesArrayField:array?.key,videoField:videos[0]?.key,maxImages:Math.max(0,...images.map(f=>f.maximum||1)),
  maxVideos:Math.max(0,...videos.map(f=>f.maximum||1)),mediaRequired:fields.some(f=>f.required)};
 return {ref:{providerId:m.providerId,modelId:m.id},parameters:m.parameters.filter(p=>!fields.some(f=>f.key===p.key)).map(p=>{
  const value={key:p.key,displayName:p.displayName,uiType:p.type,nativeType:p.nativeType,options:p.options||[],
   min:p.minValue,max:p.maxValue,step:p.step,default:p.defaultValue==null?undefined:String(p.defaultValue),required:!!p.isRequired,advanced:!!p.isAdvanced,description:p.description};
  return Object.fromEntries(Object.entries(value).filter(([,v])=>v!=null));
 }),media:Object.fromEntries(Object.entries(media).filter(([,v])=>v!=null)),promptRequired:m.promptRequired!==false,unsupportedRequired:unsupported,source:m.schemaUrl||'Nexus desktop contract'};
}
function agentMedia(plan,m){
 return (plan.media||[]).map(ref=>{
  let file=Agent.attachments.find(f=>f.url===ref.uri)||Agent.items.flatMap(i=>i.attachments||[]).find(f=>f.url===ref.uri);
  if(!file&&/^\/media\/[a-f0-9]{32}\.[a-z0-9]+$/.test(ref.uri))file={id:ref.uri.split('/').pop(),url:ref.uri,name:ref.uri.split('/').pop(),mime:ref.mimeType||'image/jpeg'};
  if(!file&&/^https:\/\//.test(ref.uri)&&Agent.snapshot?.index.assets.some(a=>a.uri===ref.uri))file={id:ref.assetId||ref.uri,url:ref.uri,remoteUrl:ref.uri,name:'Referencia guardada',mime:ref.mimeType||'image/jpeg'};
  if(!file)throw Error('Vuelve a adjuntar el archivo del plan antes de generar.');
  const field=ref.fieldKey||m.fields.find(f=>f.mime===(ref.mimeType||'image/').split('/')[0]+'/*')?.key;
  return {...file,field};
 });
}
async function agentCallback(op,args){
 switch(op){
  case'models':return models.filter(m=>!state.hidden.includes(m.id)).map(agentSummary);
  case'schema':return agentSchema(await agentModel(args));
  case'quote':{
   const model=await agentModel(args.ref);
   const q=await quotePlan({model,prompt:'',values:args.params,media:[]});
   return {amountUsd:q.currency==='USD'?q.amount:null,confidence:q.currency==='USD'?'NEXUS_QUOTE':'UNKNOWN',
    basis:amount(q),notes:q.currency==='USD'?[]:['Precio expresado en creditos del proveedor.']};
  }
  case'history':return state.jobs.slice(0,Number(args)||30).map(j=>({jobId:j.id,providerId:j.providerId,modelId:j.modelId,
   modality:j.modality||'IMAGE',prompt:j.prompt||'',status:j.status,createdAt:j.startedAt||0,
   outputs:(j.outputs||[]).map(x=>typeof x==='string'?x:x.url).filter(Boolean)}));
  case'status':{
   await poll();const j=state.jobs.find(j=>j.id===args);if(!j)throw Error('Trabajo no encontrado.');
   return {jobId:j.id,status:j.status,outputs:j.outputs||[],error:j.error||null};
  }
  case'cancel':{
   const j=state.jobs.find(j=>j.id===args);if(!j||!j.remoteId)throw Error('Trabajo no encontrado.');
   if(j.providerId!=='runway')throw Error('Este proveedor no ofrece cancelacion en Nexus Desktop.');
   await rpc('request',{provider:'runway',path:'/v1/tasks/'+encodeURIComponent(j.remoteId),method:'DELETE'});
   j.status='CANCELLED';await flush();return true;
  }
  case'submit':{
   const model=await agentModel(args.ref),media=agentMedia(args,model),values={...args.params};
   if(args.negativePrompt)values.negative_prompt=args.negativePrompt;
   const plan={model,prompt:args.prompt,values,media};
   const key=args.id,approved=Agent.approved;
   if(!approved||approved.id!==key)throw Error('Falta la confirmacion del plan.');
   plan.cost=approved.cost;Agent.approved=null;
   const job=await dispatch(plan);job.agentPlanId=args.id;job.projectId=args.projectId;await flush();
   return {jobId:job.id,providerJobId:job.remoteId||null};
  }
  case'thumbnail':{
   const file=Agent.items.flatMap(i=>i.attachments||[]).find(f=>f.url===args.uri)||Agent.attachments.find(f=>f.url===args.uri)||(/^\/media\/[a-f0-9]{32}\.[a-z0-9]+$/.test(args.uri)?{url:args.uri,mime:args.mimeType}:null);
   if(!file||!file.mime.startsWith('image/'))throw Error('Imagen no disponible.');
   const img=new Image();img.src=file.url;await img.decode();
   const canvas=document.createElement('canvas'),scale=Math.min(1,1280/Math.max(img.naturalWidth,img.naturalHeight));
   canvas.width=Math.max(1,Math.round(img.naturalWidth*scale));canvas.height=Math.max(1,Math.round(img.naturalHeight*scale));
   canvas.getContext('2d').drawImage(img,0,0,canvas.width,canvas.height);
   return {format:'jpeg',bytes:canvas.toDataURL('image/jpeg',.82).split(',')[1]};
  }
  default:throw Error('Operacion desconocida.');
 }
}
function renderAssistant(){
 const s=Agent.snapshot,project=s?.index.projects.find(p=>p.id===s.projectId),disabled=Agent.busy?'disabled':'';
 $('#content').classList.add('agent-view');
 $('#content').innerHTML=`<div class="agent-toolbar"><div class="tabs" role="group" aria-label="Modo del asistente">${[['ASSISTANT','Asistente'],['COPILOT','Copiloto'],['AGENT','Agente']].map(([id,name])=>`<button data-agent-mode="${id}" class="${s?.mode===id?'active':''}" ${disabled}>${name}</button>`).join('')}</div><div class="agent-project"><select id="agent-project" aria-label="Proyecto activo" ${disabled}><option value="">Sin proyecto</option>${(s?.index.projects||[]).filter(p=>!p.archived).map(p=>`<option value="${escape(p.id)}" ${p.id===s.projectId?'selected':''}>${escape(p.name)}</option>`).join('')}</select>${aiButton('project-new','Nuevo proyecto','plus',Agent.busy)}${aiButton('memory','Memoria del proyecto','book',Agent.busy||!project)}</div></div>
 <div class="agent-session-info"><span>${s?(s.offline?'Planificador local · sin LLM':escape(Agent.settings.modelId)):'Conectando con el asistente'}</span><button class="small" data-agent="skills" ${disabled}>${s?.skills.length||0} skills</button></div>
 <div class="agent-messages" role="log" aria-live="polite">${Agent.items.length?Agent.items.map(i=>`<article class="agent-message ${i.role}"><div class="agent-author">${i.role==='user'?'Tú':'Nexus'}</div><div class="agent-text">${i.role==='assistant'&&i.html?i.html:escape(i.text)}</div>${(i.attachments||[]).map(f=>`<span class="agent-file">${escape(f.name)}</span>`).join('')}${i.usage?`<small class="hint">${i.usage}</small>`:''}</article>`).join(''):'<div class="agent-empty"><h2>¿Qué quieres crear?</h2></div>'}
 ${(s?.plans||[]).filter(p=>p.state!=='DISCARDED').map(p=>`<article class="agent-plan"><div class="summary"><h3>${escape(p.plan.modelName)}</h3><span>${escape(planLabels[p.state]||p.state)}</span></div><p class="agent-text">${escape(p.plan.prompt)}</p><div class="agent-plan-meta">${Object.entries(p.plan.effectiveParams).map(([k,v])=>`<span>${escape(E.label(k))}: ${escape(v)}</span>`).join('')}</div><p>${escape(p.plan.estimate?.basis||'Coste pendiente')}</p>${p.plan.warnings.map(w=>`<p class="hint">${escape(w)}</p>`).join('')}<div class="toolbar"><button class="primary" data-agent-generate="${p.plan.id}" ${Agent.busy||['SUBMITTED','UNCERTAIN'].includes(p.state)?'disabled':''}>Revisar y generar</button><button data-agent-canvas="${p.plan.id}" ${disabled}>Abrir en lienzo</button><button data-agent-edit="${p.plan.id}" ${Agent.busy||['SUBMITTED','UNCERTAIN'].includes(p.state)?'disabled':''}>Editar</button><button data-agent-discard="${p.plan.id}" ${disabled}>Descartar</button></div></article>`).join('')}
 ${(s?.proposals||[]).map(p=>`<article class="agent-plan"><p>${escape(p.description)}</p><button data-agent-apply="${p.id}" ${disabled}>Aplicar</button> <button data-agent-reject="${p.id}" ${disabled}>Descartar</button></article>`).join('')}
 ${Agent.busy?'<div class="agent-thinking"><span class="loading"></span> Procesando…</div>':''}</div>
 <form id="agent-compose"><div class="agent-attachments">${Agent.attachments.map(f=>`<button type="button" data-agent-remove="${escape(f.id)}" title="Quitar adjunto">${escape(f.name)} ×</button>`).join('')}</div><textarea id="agent-input" aria-label="Mensaje al asistente" placeholder="Escribe tu idea…" ${disabled}>${escape(Agent.draft)}</textarea><div class="agent-compose-actions">${aiButton('attach','Adjuntar imagen o vídeo','clip',Agent.busy)}${aiButton('send','Enviar mensaje','send',Agent.busy)}</div></form>`;
 const messages=$('.agent-messages');messages.scrollTop=messages.scrollHeight;
}
const originalRender=render;
render=function(){
 $('#content').classList.remove('agent-view');
 if(view==='assistant'){
  renderShell();
  $('#title').textContent='Asistente';$('#eyebrow').textContent='NEXUS · AGENTE';
  $('#header-actions').innerHTML=aiButton('new','Nueva conversación','plus',Agent.busy)+aiButton('settings','Ajustes del asistente','settings',Agent.busy);
  $('#content').classList.remove('canvas-view');renderAssistant();
 }else originalRender();
 $('#navigation').insertAdjacentHTML('afterbegin',`<button data-nav="assistant" class="${view==='assistant'?'active':''}">${aiIcon('chat')}Asistente</button>`);
 if(view==='assistant')$('#navigation').querySelectorAll('[data-nav]:not([data-nav="assistant"])').forEach(b=>b.classList.remove('active'));
};
async function sendAgent(){
 const text=Agent.draft.trim();if(!text||Agent.busy)return;
 if(!Agent.snapshot||Agent.snapshot.offline){
  try{await agentCall('snapshot');}catch(error){toast(error.message);return;}
  if(Agent.snapshot.offline){toast('El asistente necesita una conexion LLM. Configurala en sus ajustes.');agentSettings();return;}
 }
 const attachments=Agent.attachments.slice();Agent.items.push({role:'user',text,attachments});Agent.draft='';Agent.attachments=[];
 try{const result=await agentCall('send',{text,attachments:attachments.map(f=>({id:f.id,uri:f.url,mimeType:f.mime,name:f.name}))});
  Agent.items.push({role:'assistant',text:result.text,html:result.html,usage:result.inputTokens||result.outputTokens?`${result.inputTokens} entrada · ${result.outputTokens} salida`:null});
 }catch(error){Agent.items.push({role:'assistant',text:error.message});Agent.draft=text;Agent.attachments=attachments;}
 renderAssistant();
}
function agentSettings(){
 const s=Agent.settings;
 modal(`<h2>Ajustes del asistente</h2><form id="agent-settings"><div class="fields"><label>Conexión<select name="backend"><option value="direct">Bedrock directo</option><option value="proxy">Proxy Bedrock</option></select></label><label>Modelo<input name="modelId" value="${escape(s.modelId||'')}" required></label><label>Región<input name="region" value="${escape(s.region||'us-east-1')}" required></label><label>Máximo de tokens<input name="maxTokens" type="number" min="256" max="32000" value="${s.maxTokens||4096}"></label><label>URL del proxy<input name="endpoint" value="${escape(s.endpoint||'')}"></label><label>Familias excluidas<input name="excludedFamilies" value="${escape(s.excludedFamilies||'')}"></label>${[['accessKey','AWS Access Key'],['secretKey','AWS Secret Key'],['sessionToken','AWS Session Token'],['token','Token del proxy']].map(([id,label])=>`<label>${label}<input name="${id}" type="password" autocomplete="new-password" placeholder="${s.configured?.includes(id)?'Configurado':'Sin configurar'}"></label>`).join('')}</div><label class="agent-check"><input name="sendImages" type="checkbox" ${s.sendImages?'checked':''}> Enviar imágenes al asistente</label><button class="primary" type="submit">Guardar</button></form>`);
 $('#agent-settings [name=backend]').value=s.backend||'direct';
}
document.addEventListener('input',e=>{if(e.target.id==='agent-input')Agent.draft=e.target.value;});
document.addEventListener('change',async e=>{if(e.target.id==='agent-project')try{await agentCall('project',{id:e.target.value});}catch(error){toast(error.message);}});
document.addEventListener('submit',async e=>{
 const formId=e.target.getAttribute('id')||'';
 if(!formId.startsWith('agent-'))return;e.preventDefault();
 try{
  if(formId==='agent-compose')return await sendAgent();
  const fields=Object.fromEntries(new FormData(e.target));
  if(formId==='agent-settings'){fields.maxTokens=Number(fields.maxTokens);fields.sendImages=e.target.elements.sendImages.checked;await agentCall('settings',fields);}
  if(formId==='agent-project-form')await agentCall('createProject',fields);
  if(formId==='agent-memory')await agentCall('memory',fields);
  if(formId==='agent-edit'){const params=Object.fromEntries(Object.entries(fields).filter(([k])=>k.startsWith('param:')).map(([k,v])=>[k.slice(6),v||null]));await agentCall('edit',{id:fields.id,prompt:fields.prompt,params});}
  $('#dialog').close();
 }catch(error){toast(error.message);}
});
document.addEventListener('click',async e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 try{
  if(b.dataset.nav==='assistant'&&!Agent.busy){const first=!Agent.snapshot;await agentCall('snapshot');if(first)await agentCall('importHistory');}
  if(b.dataset.agentMode)await agentCall('mode',{mode:b.dataset.agentMode});
  if(b.dataset.agentRemove){Agent.attachments=Agent.attachments.filter(f=>f.id!==b.dataset.agentRemove);renderAssistant();}
  for(const action of ['apply','reject','discard'])if(b.dataset['agent'+action[0].toUpperCase()+action.slice(1)])await agentCall(action,{id:b.dataset['agent'+action[0].toUpperCase()+action.slice(1)]});
  if(b.dataset.agentCanvas){const p=Agent.snapshot.plans.find(p=>p.plan.id===b.dataset.agentCanvas).plan;
   const m=await agentModel(p.ref);state.draft={modelId:p.ref.modelId,prompt:p.prompt,values:p.params,media:agentMedia(p,m)};await openModel(p.ref.modelId,false);persist();}
  if(b.dataset.agentEdit){const p=Agent.snapshot.plans.find(p=>p.plan.id===b.dataset.agentEdit).plan,m=await agentModel(p.ref);
   modal(`<h2>Editar plan</h2><form id="agent-edit"><input type="hidden" name="id" value="${p.id}"><label>Prompt<textarea name="prompt">${escape(p.prompt)}</textarea></label><div class="fields">${m.parameters.map(param=>agentEditControl(param,p.effectiveParams[param.key])).join('')}</div><button class="primary">Guardar cambios</button></form>`);}
  if(b.dataset.agentGenerate){
   const entry=Agent.snapshot.plans.find(p=>p.plan.id===b.dataset.agentGenerate),p=entry.plan,m=await agentModel(p.ref);
   const cost=await quotePlan({model:m,prompt:p.prompt,values:p.params,media:agentMedia(p,m)});
   Agent.approved={id:p.id,fingerprint:entry.fingerprint,cost};
   modal(`<h2>Confirmar generación</h2><h3>${escape(p.modelName)}</h3><p class="agent-text">${escape(p.prompt)}</p><p>Coste estimado: <strong>${amount(cost)}</strong></p><button class="primary" data-agent="confirm">Generar</button>`);
  }
  switch(b.dataset.agent){
   case'send':e.preventDefault();await sendAgent();break;
   case'attach':e.preventDefault();Agent.attachments.push(...(await rpc('pick')).slice(0,Math.max(0,6-Agent.attachments.length)));renderAssistant();break;
   case'new':await agentCall('new');Agent.items=[];Agent.attachments=[];renderAssistant();break;
   case'settings':agentSettings();break;
   case'project-new':modal('<h2>Nuevo proyecto</h2><form id="agent-project-form"><label>Nombre<input name="name" maxlength="80" required></label><button class="primary">Crear proyecto</button></form>');break;
   case'memory':{const p=Agent.snapshot.index.projects.find(p=>p.id===Agent.snapshot.projectId),m=p.memory;
    modal(`<h2>Memoria · ${escape(p.name)}</h2><form id="agent-memory"><input type="hidden" name="id" value="${p.id}">${[['description','Descripción',p.description],['synopsis','Sinopsis',m.synopsis],['tone','Tono',m.tone],['visualStyle','Estilo visual',m.visualStyle],['rules','Reglas',m.rules.join('\n')],['preferredModels','Modelos preferidos',m.preferredModels.join('\n')]].map(([key,label,value])=>`<label>${label}<textarea name="${key}">${escape(value||'')}</textarea></label>`).join('')}<button class="primary">Guardar memoria</button></form>`);break;}
   case'skills':modal('<h2>Skills</h2><button data-agent="reload">Actualizar</button>'+Agent.snapshot.skills.map(s=>`<details><summary>${escape(s.title)}</summary><div class="agent-text">${escape(s.body)}</div></details>`).join(''));break;
   case'reload':await agentCall('reload');$('#dialog').close();break;
   case'confirm':{const approved=Agent.approved;if(!approved)throw Error('Revisa de nuevo el plan.');b.disabled=true;$('#dialog').close();await agentCall('confirm',{id:approved.id,fingerprint:approved.fingerprint});await flush();break;}
  }
 }catch(error){toast(error.message);}
});
view='library';
