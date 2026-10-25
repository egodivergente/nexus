'use strict';
const Agent = {owner:(()=>{try{let id=sessionStorage.getItem('nexus_agent_owner');if(!id){id=crypto.randomUUID();sessionStorage.setItem('nexus_agent_owner',id);}return id;}catch{return crypto.randomUUID();}})(),snapshot:null,settings:{},items:[],attachments:[],busy:false,generating:false,callbacks:new Set(),draft:'',error:'',cardAnchors:new Map(),expandedBatches:new Set()};
// Keep the editor and unchanged messages alive across job/status updates.
const agentMarkup=new WeakMap();
function agentNodeKey(node){return node.dataset.agentMessage||node.dataset.agentCard||node.id||node.classList?.[0]||node.className;}
function agentPatchChildren(parent,incoming){
 const existing=new Map([...parent.children].map(node=>[agentNodeKey(node),node]));
 let cursor=parent.firstElementChild;
 for(const fresh of [...incoming.children]){
  const key=agentNodeKey(fresh),old=existing.get(key),markup=fresh.outerHTML;
  let node=fresh.agentRetained||fresh;
  if(old&&agentMarkup.get(old)===markup)node=old;
  else if(old&&!fresh.agentRetained){
   // Preserve playback and decoded images when a result's status/count changes.
   const media=new Map([...old.querySelectorAll('img,video,audio')].map(m=>[m.tagName+':'+m.getAttribute('src'),m]));
   for(const m of fresh.querySelectorAll('img,video,audio')){
    const retained=media.get(m.tagName+':'+m.getAttribute('src'));
    if(retained){media.delete(m.tagName+':'+m.getAttribute('src'));m.replaceWith(retained);}
   }
  }
  agentMarkup.set(node,markup);existing.delete(key);
  if(node!==cursor)parent.insertBefore(node,cursor);
  if(old&&old!==node)old.remove();
  cursor=node.nextElementSibling;
 }
 for(const node of existing.values())node.remove();
}
const AgentViewport={
 node:null,following:true,top:0,anchor:null,frame:0,force:false,height:0,extent:0,userUntil:0,
 layoutChanged(){return this.node&&(this.node.clientHeight!==this.height||this.node.scrollHeight!==this.extent);},
 remember(){
  const n=this.node;if(!n?.isConnected)return;
  this.top=n.scrollTop;
  const edge=n.getBoundingClientRect().top;
  const visible=[...n.children].find(c=>c.getBoundingClientRect().bottom>edge+1);
  this.anchor=visible?{key:agentNodeKey(visible),offset:visible.getBoundingClientRect().top-edge}:null;
 },
 capture(n){
  if(!n)return;
  if(this.layoutChanged()){this.schedule();return;}
  if(!this.force&&Math.abs(n.scrollTop-this.top)>1)this.following=n.scrollHeight-n.scrollTop-n.clientHeight<72;
  this.remember();
 },
 latest(){this.following=true;this.force=true;this.userUntil=0;this.schedule();},
 schedule(){if(!this.frame)this.frame=requestAnimationFrame(()=>{this.frame=0;this.apply();});},
 apply(){
  const n=this.node;if(!n?.isConnected||view!=='assistant')return;
  if(this.following)n.scrollTop=n.scrollHeight;
  else{
   const anchor=this.anchor&&[...n.children].find(c=>agentNodeKey(c)===this.anchor.key);
   n.scrollTop=anchor?n.scrollTop+anchor.getBoundingClientRect().top-n.getBoundingClientRect().top-this.anchor.offset:this.top;
  }
  this.top=n.scrollTop;this.height=n.clientHeight;this.extent=n.scrollHeight;this.force=false;
  const compose=$('#agent-compose');if(compose)$('#content').style.setProperty('--agent-compose-height',compose.offsetHeight+'px');
  const jump=$('[data-agent-latest]');if(jump)jump.hidden=this.following||n.scrollHeight-n.scrollTop-n.clientHeight<72;
 },
 mount(n){
  if(this.node!==n){
   this.resize?.disconnect();this.mutations?.disconnect();this.node=n;this.following=true;this.anchor=null;
   n.addEventListener('scroll',()=>{
    if(Math.abs(n.scrollTop-this.top)<1)return;
    if(performance.now()>this.userUntil||this.layoutChanged()){this.schedule();return;}
    this.following=n.scrollHeight-n.scrollTop-n.clientHeight<72;this.remember();this.schedule();
   },{passive:true});
   n.addEventListener('wheel',e=>{this.userUntil=performance.now()+600;if(e.deltaY<0){this.following=false;this.remember();}},{passive:true});
   n.addEventListener('touchmove',()=>{this.userUntil=performance.now()+1000;},{passive:true});
   n.addEventListener('pointerdown',e=>{if(e.clientX>=n.getBoundingClientRect().right-20)this.userUntil=performance.now()+1000;},{passive:true});
   n.addEventListener('keydown',e=>{if(['ArrowUp','ArrowDown','PageUp','PageDown','Home','End',' '].includes(e.key))this.userUntil=performance.now()+600;});
   this.resize=new ResizeObserver(()=>this.schedule());
   this.mutations=new MutationObserver(()=>this.schedule());
   this.mutations.observe(n,{childList:true,subtree:true,characterData:true});
   n.addEventListener('load',()=>this.schedule(),true);
   n.addEventListener('loadedmetadata',()=>this.schedule(),true);
  }
  this.resize.disconnect();this.resize.observe(n);
  for(const child of n.children)this.resize.observe(child);
  this.resize.observe($('#agent-compose'));this.schedule();
 }
};
function agentSizeInput(){
 const input=$('#agent-input');if(!input)return;
 input.style.height='auto';input.style.height=Math.max(44,Math.min(160,input.scrollHeight))+'px';AgentViewport.schedule();
}
const agentIcons = {
 chat:'<path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8z"/>',
 plus:'<path d="M12 5v14M5 12h14"/>',send:'<path d="m22 2-7 20-4-9-9-4 20-7ZM22 2 11 13"/>',
 clip:'<path d="m21.4 11.6-9.2 9.2a6 6 0 0 1-8.5-8.5L14.3 1.7a4 4 0 0 1 5.7 5.7L9.4 18a2 2 0 0 1-2.8-2.8l9.2-9.2"/>',
 book:'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/>'
};
const aiIcon=name=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${agentIcons[name]||icons.settings}</svg>`;
const planLabels={PREPARED:'Preparado',AWAITING_CONFIRMATION:'Pendiente de confirmar',CONFIRMED:'Confirmado',SUBMITTED:'Enviado',DISCARDED:'Descartado',UNCERTAIN:'Revisar en historial'};
function promptOriginalHtml(p){return p.originalPrompt?`<details class=prompt-original><summary>Prompt resumido · Ver original</summary><p class=agent-text>${escape(p.originalPrompt)}</p></details>`:'';}
function agentEditControl(p,value){
 const name='param:'+p.key,v=value??p.defaultValue??'',label=escape(p.displayName||E.label(p.key));
 if(p.options?.length||p.type==='BOOLEAN')return `<label>${label}<select name="${escape(name)}">${(p.options?.length?p.options:['true','false']).map(o=>`<option value="${escape(o)}" ${String(v)===String(o)?'selected':''}>${escape(o==='true'?'Sí':o==='false'?'No':o)}</option>`).join('')}</select></label>`;
 const numeric=['integer','number'].includes(p.nativeType)||['INTEGER','FLOAT','SEED','SLIDER'].includes(p.type);
 return `<label>${label}<input name="${escape(name)}" type="${numeric?'number':'text'}" value="${escape(v)}" ${p.minValue!=null?`min="${p.minValue}"`:''} ${p.maxValue!=null?`max="${p.maxValue}"`:''} ${numeric?'step="any"':''}></label>`;
}
const aiButton=(action,title,name,disabled=false)=>`<button type="button" class="agent-icon" data-agent="${action}" title="${title}" aria-label="${title}" ${disabled?'disabled':''}>${aiIcon(name)}</button>`;
const planCost=plan=>Number.isFinite(plan.estimate?.amountUsd)?money(plan.estimate.amountUsd):plan.estimate?.basis||'Coste pendiente';

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
 }),textLimits:m.textLimits||{},media:Object.fromEntries(Object.entries(media).filter(([,v])=>v!=null)),promptRequired:m.promptRequired!==false,unsupportedRequired:unsupported,source:m.schemaUrl||'Nexus desktop contract'};
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
 if(op==='progress'){const node=document.querySelector('.agent-thinking');if(node)node.textContent=String(args);return true;}
 switch(op){
  case'models':return models.filter(m=>!state.hidden.includes(m.id)).map(agentSummary);
  case'schema':return agentSchema(await agentModel(args));
  case'quote':{
   const model=await agentModel(args.ref);
   if(model.providerId==='google_flow')return {amountUsd:null,confidence:'UNKNOWN',basis:'Tu cuenta Flow',notes:[]};
   const q=await quotePlan({model,prompt:'',values:args.params,media:[]});
   return {amountUsd:q.currency==='USD'?q.amount:null,confidence:q.currency==='USD'||model.providerId==='google_flow'&&q.currency==='flow_credits'&&Number.isFinite(q.amount)&&q.amount>=0&&!q.upperBound?'NEXUS_QUOTE':'UNKNOWN',
    basis:amount(q),notes:q.currency==='USD'?[]:[q.detail||'Precio expresado en créditos del proveedor.']};
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
   if(model.providerId==='google_flow'){
    const count=Number(values.count||1);if(!Number.isInteger(count)||count<1||count>20)throw Error('Puedes pedir hasta 20 resultados por encargo.');
    plan.cost={amount:null,currency:'flow_account',time:Date.now(),detail:'Tu cuenta Flow'};
   }else{
    if(!approved||approved.id!==key)throw Error('Falta la confirmacion del plan.');
    plan.cost=approved.cost;Agent.approved=null;
   }
    const job=await dispatch({...plan,originalPrompt:args.originalPrompt},null,{agentPlanId:args.id,agentBatchId:args.id,projectId:args.projectId});await flush();
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
function agentModelLabel(){return (Agent.settings.modelId||'Modelo conectado').replace(/^us\.|^eu\.|^global\./,'').replace(/^anthropic\./,'').replace(/^openai\.gpt-5\.6-sol$/,'Sol').replace(/claude-sonnet-5-5/,'Claude Sonnet 5.5').replace(/claude-opus-5-5/,'Claude Opus 5.5').replace(/claude-sonnet-4-6/,'Claude Sonnet 4.6').replace(/claude-opus-4-6/,'Claude Opus 4.6');}
function planOutputCount(plan){const count=Number(plan?.effectiveParams?.count??plan?.params?.count);return Number.isInteger(count)&&count>0?count:1;}
function planAspect(plan){return plan?.effectiveParams?.aspect||plan?.params?.aspect||plan?.effectiveParams?.aspect_ratio||plan?.params?.aspect_ratio||'Original';}
function isImagePlan(entry){const plan=entry?.plan;return plan?.modality==='IMAGE'&&Boolean(plan.ref?.providerId&&plan.ref?.modelId);}
function jobsForPlan(id){return state.jobs.filter(job=>job.agentPlanId===id);}
function batchIdentity(plan){return [plan.ref.providerId,plan.ref.modelId,String(plan.prompt||'').trim(),planAspect(plan),plan.negativePrompt||'',JSON.stringify(Object.entries(plan.effectiveParams||plan.params||{}).sort(([a],[b])=>a.localeCompare(b))),JSON.stringify(plan.media||[])].join('\u241f');}
function makeGenerationBatch(id,entries){
 const plan=entries[0].plan,jobs=entries.flatMap(entry=>jobsForPlan(entry.plan.id));
 const requested=entries.reduce((total,entry)=>total+planOutputCount(entry.plan),0);
 const reported=jobs.reduce((total,job)=>total+(Number.isInteger(job.expectedOutputCount)&&job.expectedOutputCount>0?job.expectedOutputCount:0),0);
 return {id,entries,plan,jobs,providerId:plan.ref.providerId,modelId:plan.ref.modelId,aspect:planAspect(plan),count:Math.max(requested,reported),cost:entries.map(entry=>planCost(entry.plan)).find(Boolean)||'Coste pendiente'};
}
function generationBatches(entries){
 const groups=[],claimed=new Set();
 for(const entry of entries){
  const plan=entry.plan;if(claimed.has(plan.id)||!isImagePlan(entry))continue;
  const count=planOutputCount(plan);
  if(count>1||plan.ref.providerId==='google_flow'){groups.push(makeGenerationBatch('native:'+plan.id,[entry]));claimed.add(plan.id);continue;}
  const key=batchIdentity(plan);
  // Completed orders must never absorb a new request with the same prompt.
  const anchor=Agent.cardAnchors.get('plan:'+plan.id);
  const siblings=entries.filter(candidate=>!claimed.has(candidate.plan.id)&&isImagePlan(candidate)&&planOutputCount(candidate.plan)===1&&batchIdentity(candidate.plan)===key&&candidate.state===entry.state&&!jobsForPlan(candidate.plan.id).length&&!jobsForPlan(plan.id).length&&Agent.cardAnchors.get('plan:'+candidate.plan.id)===anchor);
  if(siblings.length>1){
   const id='group:'+siblings.map(candidate=>candidate.plan.id).sort().join('|');
   groups.push(makeGenerationBatch(id,siblings));siblings.forEach(candidate=>claimed.add(candidate.plan.id));
  }else{
   groups.push(makeGenerationBatch('image:'+plan.id,[entry]));claimed.add(plan.id);
  }
 }
 const byPlanId=new Map();for(const batch of groups)for(const entry of batch.entries)byPlanId.set(entry.plan.id,batch);
 return {groups,byPlanId};
}
function batchOutputs(batch){
 const seen=new Set(),outputs=[];
 for(const job of batch.jobs)for(const value of job.outputs||[]){const url=typeof value==='string'?value:value?.url;if(isMediaUrl(url)&&!seen.has(url)){seen.add(url);outputs.push(url);}}
 return outputs;
}
function batchStatus(batch){
 const outputs=batchOutputs(batch),jobs=batch.jobs;
 if(jobs.some(job=>job.uncertain))return 'Envío por comprobar';
 if(jobs.some(job=>job.reconciling||job.flowRecoveryRevision&&job.status!=='COMPLETED'))return 'Recuperando resultado';
 if(jobs.some(job=>job.status==='FAILED'))return 'Revisar envío';
 if(!jobs.length){return batch.entries.some(entry=>entry.state==='UNCERTAIN')?'Envío por comprobar':batch.entries.some(entry=>entry.state==='SUBMITTED')?(batch.providerId==='google_flow'?'Enviando a Flow':'Enviando'):'Listo para generar';}
 if(jobs.every(job=>job.status==='COMPLETED'))return 'Completado';
 if(outputs.length)return `Renderizando ${Math.min(outputs.length,batch.count)}/${batch.count}`;
 return batch.providerId==='google_flow'?'Enviando a Flow':'Enviando';
}
function batchCanGenerate(batch){return !batch.jobs.length&&batch.entries.every(entry=>Agent.snapshot?.plans.some(p=>p.plan.id===entry.plan.id)&&!['SUBMITTED','UNCERTAIN','DISCARDED'].includes(entry.state));}
function legacyPlanCard(entry,disabled){const p=entry.plan,live=Agent.snapshot?.plans.some(e=>e.plan.id===p.id);return `<article class="agent-plan" data-agent-card="plan:${escape(p.id)}"><div class="summary"><h3>${escape(p.modelName)}</h3><span>${escape(planLabels[entry.state]||entry.state)}</span></div><p class="agent-text">${escape(p.prompt)}</p>${promptOriginalHtml(p)}<div class="agent-plan-meta">${Object.entries(p.effectiveParams||{}).map(([k,v])=>`<span>${escape(E.label(k))}: ${escape(v)}</span>`).join('')}</div><p>${escape(planCost(p))}</p>${(p.warnings||[]).map(w=>`<p class="hint">${escape(w)}</p>`).join('')}<div class="toolbar"><button class="primary" data-agent-generate="${p.id}" ${Agent.busy||!live||['SUBMITTED','UNCERTAIN'].includes(entry.state)?'disabled':''}>Generar</button><button data-agent-canvas="${p.id}" ${disabled}>Abrir en lienzo</button><button data-agent-edit="${p.id}" ${Agent.busy||!live||['SUBMITTED','UNCERTAIN'].includes(entry.state)?'disabled':''}>Editar</button><button data-agent-discard="${p.id}" ${disabled}>Descartar</button></div></article>`;}
function generationBatchCard(batch,disabled){
 if(batch.providerId==='google_flow'){
  const outputs=batchOutputs(batch),status=batchStatus(batch);
  return `<div class="agent-flow-result" data-agent-card="batch:${escape(batch.id)}" data-agent-batch-members="${escape(batch.entries.map(entry=>entry.plan.id).join('|'))}"><p class="agent-flow-status">${escape(status)}${batch.count>1?' · '+outputs.length+' / '+batch.count:''}</p><div class="agent-flow-media">${outputs.map((url,i)=>batch.plan.modality==='VIDEO'?`<div><video src="${escape(url)}" controls playsinline preload="metadata" aria-label="Resultado ${i+1}"></video><a href="${escape(url)}" download>Descargar vídeo</a></div>`:`<a href="${escape(url)}" download aria-label="Descargar resultado ${i+1}"><img src="${escape(url)}" alt="Resultado ${i+1}" loading="lazy" decoding="async"></a>`).join('')}</div></div>`;
 }
 const outputs=batchOutputs(batch),status=batchStatus(batch),expanded=Agent.expandedBatches.has(batch.id),canGenerate=batchCanGenerate(batch);
 const prompt=String(batch.plan.prompt||''),canExpand=prompt.length>96,actionLabel=`Generar ${batch.count}`;
 const primary=canGenerate?`<button class="primary" data-agent-batch-generate="${escape(batch.id)}" ${Agent.busy||Agent.generating?'disabled':''}>${Agent.generating?'Enviando…':actionLabel}</button>`:'';
 const images=outputs.length?`<div class="generation-batch-grid" aria-label="${outputs.length} resultados del lote">${outputs.map((url,index)=>`<a class="generation-batch-output" href="${escape(url)}" download aria-label="Abrir resultado ${index+1} de ${batch.count}"><img src="${escape(url)}" alt="Resultado ${index+1} de ${batch.plan.modelName}" loading="lazy" decoding="async"></a>`).join('')}</div>`:'';
 return `<article class="generation-batch-card" data-agent-card="batch:${escape(batch.id)}" data-agent-batch-members="${escape(batch.entries.map(entry=>entry.plan.id).join('|'))}"><div class="generation-batch-header"><span class="generation-batch-label">Generación de imágenes</span><span class="generation-batch-status">${escape(status)}</span></div><div class="generation-batch-chips"><span>${escape(batch.plan.modelName)}</span><span>${escape(providers[batch.providerId]||batch.providerId)}</span><span>${escape(batch.aspect)}</span><span>${escape(batch.cost)}</span><span>${batch.count} imágenes</span></div>${images}<div class="generation-batch-prompt ${expanded?'is-expanded':''}"><span>${escape(prompt)}</span>${canExpand?`<button class="small" data-agent-prompt-toggle="${escape(batch.id)}">${expanded?'Ver menos':'Ver más'}</button>`:''}</div>${promptOriginalHtml(batch.plan)}${batch.plan.warnings?.map(w=>`<p class="hint generation-batch-warning">${escape(w)}</p>`).join('')||''}<div class="generation-batch-actions">${primary}<button class="small" data-agent-batch-edit="${escape(batch.id)}" ${Agent.busy||!canGenerate?'disabled':''}>Editar prompt</button><button class="small" data-agent-batch-regenerate="${escape(batch.id)}" ${disabled}>Regenerar ${batch.count}</button><button class="small" data-agent-batch-canvas="${escape(batch.id)}" ${disabled}>Abrir en lienzo</button></div></article>`;
}
function renderGenerationPlans(snapshot,disabled){
 const current=snapshot?.plans||[],saved=[...new Map([...(state.agentConversation?.flowPlans||[]),...(state.agentConversation?.resultPlans||[])].map(e=>[e.plan.id,e])).values()].filter(entry=>!current.some(e=>e.plan.id===entry.plan.id));
 const entries=[...saved,...current].filter(entry=>entry.state!=='DISCARDED'),{byPlanId}=generationBatches(entries),rendered=new Set();
 return entries.map(entry=>{const batch=byPlanId.get(entry.plan.id);if(!batch)return entry.plan.ref.providerId==='google_flow'?generationBatchCard(makeGenerationBatch('flow:'+entry.plan.id,[entry]),disabled):legacyPlanCard(entry,disabled);if(rendered.has(batch.id))return '';rendered.add(batch.id);return generationBatchCard(batch,disabled);}).join('');
}
function findGenerationBatch(id){return generationBatches((Agent.snapshot?.plans||[]).filter(entry=>entry.state!=='DISCARDED')).groups.find(batch=>batch.id===id);}
function agentPendingEntries(){return (Agent.snapshot?.plans||[]).filter(e=>e.plan.ref.providerId!=='google_flow'&&!['SUBMITTED','UNCERTAIN','DISCARDED'].includes(e.state)&&!jobsForPlan(e.plan.id).length);}
async function openBatchCanvas(batch,regenerate=false){const p=batch.plan,m=await agentModel(p.ref),supportsCount='count' in (p.params||{})||'count' in (p.effectiveParams||{}),values={...p.params,...(supportsCount?{count:String(batch.count)}:{})};state.draft={modelId:p.ref.modelId,prompt:p.prompt,values,media:agentMedia(p,m)};persist();await openModel(p.ref.modelId,false);if(regenerate)toast(`Lote de ${batch.count} listo para regenerar.`);}
async function consolidateBatch(batch,prompt=batch.plan.prompt){
 // Editing a group must not invent unsupported count parameters or discard jobs.
 for(const entry of batch.entries)if(prompt!==entry.plan.prompt)await agentCall('edit',{id:entry.plan.id,prompt,params:entry.plan.params});
 return findGenerationBatch(batch.id)||batch;
}
async function generateAgentEntries(entries){
 if(Agent.generating||Agent.busy)return;
 Agent.generating=true;if(view==='assistant')renderAssistant();
 const failures=[];
 try{
  for(const selected of entries){
   const entry=Agent.snapshot?.plans.find(e=>e.plan.id===selected.plan.id);
   if(!entry||['SUBMITTED','UNCERTAIN','DISCARDED'].includes(entry.state)||jobsForPlan(entry.plan.id).length)continue;
   try{
    const p=entry.plan,m=await agentModel(p.ref);
    const cost=await quotePlan({model:m,prompt:p.prompt,values:p.params,media:agentMedia(p,m)});
    // The inline Generate click authorizes these exact plans once. No second dialog.
    Agent.approved={id:p.id,fingerprint:entry.fingerprint,cost};
    await agentCall('confirm',{id:p.id,fingerprint:entry.fingerprint});
   }catch(error){failures.push(`${entry.plan.modelName}: ${error.message}`);}
   finally{Agent.approved=null;}
  }
  await flush();
 }finally{Agent.generating=false;if(view==='assistant')renderAssistant();}
 if(failures.length)toast(failures.join(' · '));
}
function renderAssistant(){
 if(!Agent.restored){Agent.restored=true;const saved=state.agentConversation;Agent.draft=saved?.draft||Agent.draft;if(!Agent.attachments.length&&Array.isArray(saved?.draftAttachments))Agent.attachments=saved.draftAttachments;if(saved?.items?.length){Agent.items=saved.items;for(const [key,index] of Object.entries(saved.anchors||{}))if(Agent.items[index])Agent.cardAnchors.set(key,Agent.items[index]);}}
 const content=$('#content'),oldMessages=$('.agent-messages'),oldCompose=$('#agent-compose');
 AgentViewport.capture(oldMessages);
 const s=Agent.snapshot,project=s?.index.projects.find(p=>p.id===s.projectId),busyUi=Agent.busy&&!Agent.initPromise,disabled=busyUi||Agent.generating?'disabled':'';
 const ready=agentPendingEntries(),readyCount=ready.reduce((sum,e)=>sum+planOutputCount(e.plan),0),readyModels=[...new Set(ready.map(e=>e.plan.modelName))];
 for(const entry of s?.plans||[]){
  const id=entry.plan.id,key='plan:'+id;
  if(!Agent.cardAnchors.has(key)){
   const prior=[...Agent.cardAnchors].find(([k])=>k.startsWith('batch:')&&(k.endsWith(':'+id)||k.slice(k.indexOf('group:')+6).split('|').includes(id)));
   Agent.cardAnchors.set(key,prior?.[1]||Agent.items.at(-1)||null);
  }
 }
 const generationMarkup=renderGenerationPlans(s,disabled),proposalMarkup=(s?.proposals||[]).map(p=>`<article class="agent-plan" data-agent-card="proposal:${escape(p.id)}"><p>${escape(p.description)}</p><button data-agent-apply="${p.id}" ${disabled}>Aplicar</button> <button data-agent-reject="${p.id}" ${disabled}>Descartar</button></article>`).join('');
 const messageMarkup=Agent.items.length?Agent.items.map(i=>(i.id||=crypto.randomUUID(),window.NexusChatContinuity?window.NexusChatContinuity.message(i):`<article data-agent-message="${i.id}" class="agent-message ${i.role}"><div class="agent-author">${i.role==='user'?'Tú':'Nexus'}</div><div class="agent-text">${i.role==='assistant'&&i.html?i.html:escape(i.text)}</div>${(i.attachments||[]).map(f=>`<span class="agent-file">${escape(f.name)}</span>`).join('')}${i.failed?`<div class="cc-failed" role="status"><span>Envío no confirmado. Tu mensaje se conserva.</span><button type="button" class="cc-retry" data-cc-retry="${escape(i.id)}">Reintentar</button></div>`:''}${i.usage?`<details class="cc-usage"><summary>Detalles</summary><small>${escape(i.usage)}</small></details>`:''}</article>`)).join(''):(!generationMarkup&&!proposalMarkup?'<div class="agent-empty"><h2>¿Qué quieres crear?</h2></div>':'');
 $('#content').classList.add('agent-view');
 const template=document.createElement('template');
 template.innerHTML=`<div class="agent-toolbar"><div class="tabs" role="group" aria-label="Modo del asistente">${[['ASSISTANT','Asistente'],['COPILOT','Copiloto'],['AGENT','Agente']].map(([id,name])=>`<button data-agent-mode="${id}" class="${s?.mode===id?'active':''}" ${disabled}>${name}</button>`).join('')}</div><div class="agent-project"><select id="agent-project" aria-label="Proyecto activo" ${disabled}><option value="">Sin proyecto</option>${(s?.index.projects||[]).filter(p=>!p.archived).map(p=>`<option value="${escape(p.id)}" ${p.id===s.projectId?'selected':''}>${escape(p.name)}</option>`).join('')}</select><button type="button" class="nx-toolbar-btn" data-agent="project-new" title="Nuevo proyecto" aria-label="Nuevo proyecto" ${busyUi?'disabled':''}>+ Proyecto</button><button type="button" class="nx-toolbar-btn" data-parity="project" title="Memoria y ajustes del proyecto" aria-label="Proyecto" ${busyUi?'disabled':''}>Proyecto</button><button type="button" class="nx-toolbar-btn" data-project-references ${disabled}>Referencias</button><button type="button" class="nx-toolbar-btn" data-parity="conversations" title="Historial de conversaciones" aria-controls="cc-panel" aria-expanded="${!!window.NexusChatContinuity?.isOpen()}" ${disabled}>Conversaciones</button><button type="button" class="nx-toolbar-btn" data-parity="media-jobs" title="Ediciones locales" ${disabled}>Ediciones</button><button type="button" class="nx-toolbar-btn" data-agent="new" title="Nueva conversación" aria-label="Nueva conversación" ${busyUi?'disabled':''}>Nueva charla</button></div></div>
 ${Agent.error?`<div class="agent-error inline-notice" role="alert"><span>No se pudo completar el mensaje. Tu texto y los adjuntos se conservan.</span><button type="button" data-agent="reconnect" ${disabled}>Comprobar conexión</button><details><summary>Detalles</summary><pre>${escape(Agent.error)}</pre></details></div>`:''}
 <div class="agent-messages" role="log" aria-label="Conversación" aria-live="polite" aria-relevant="additions text" tabindex="0">${messageMarkup}
 ${generationMarkup}
 ${proposalMarkup}
 ${busyUi?'<div class="agent-thinking"><span class="loading"></span> Preparando…</div>':''}</div>
 <div class="agent-scroll-tools"><button type="button" data-agent-latest hidden aria-label="Ir al último mensaje">↓ Últimos mensajes</button></div>
 ${ready.length?`<div class="agent-ready-actions"><span>${escape(readyModels.length===1?readyModels[0]:readyModels.length+' modelos')} · ${readyCount} resultado${readyCount===1?'':'s'} preparado${readyCount===1?'':'s'}</span><button type="button" class="primary" data-agent="generate-ready" ${disabled}>${Agent.generating?'Enviando…':'Generar'+(readyCount>1?' '+readyCount:'')}</button></div>`:''}
 <form id="agent-compose"><div class="agent-attachments">${Agent.attachments.map(f=>`<button type="button" data-agent-remove="${escape(f.id)}" title="Quitar adjunto">${escape(f.name)} ×</button>`).join('')}</div><textarea id="agent-input" rows="2" aria-label="Mensaje al asistente" placeholder="Escribe tu idea…">${escape(Agent.draft)}</textarea><div class="agent-compose-actions"><div class="agent-compose-left">${aiButton('attach','Adjuntar imagen, vídeo o documento','clip',busyUi)}<div class="agent-session-info"><button type="button" class="small nx-meta-chip" data-agent="settings" title="Modelo y conexión del asistente" ${disabled}>${s?(s.offline?'Sin modelo conectado':escape(agentModelLabel())):'Conectando con el asistente'}</button><button type="button" class="small nx-meta-chip" data-agent="skills" title="Skills activas" ${disabled}>Skills · ${s?.skills.length||0}</button><span class="nx-meta-badge">${s?.tools?.length||0} herramientas</span></div></div>${aiButton('send','Enviar mensaje (Enter)','send',busyUi)}</div></form>${window.NexusChatContinuity?.panelPlaceholder()||''}`;
 const messages=template.content.querySelector('.agent-messages');
 const messageNodes=[...messages.querySelectorAll('.agent-message')];
 // Anchor each card to the message present when it first appeared. Updates to
 // its status/results do not move it beneath later turns.
 const cards=[...messages.querySelectorAll('[data-agent-card]')];
 for(const card of cards){
  const key=card.dataset.agentCard;
  if(!Agent.cardAnchors.has(key)||!Agent.cardAnchors.get(key)&&Agent.items.length){
   const memberAnchors=(card.dataset.agentBatchMembers||'').split('|').flatMap(id=>[...Agent.cardAnchors.entries()].filter(([anchorKey])=>anchorKey==='plan:'+id||anchorKey.startsWith('batch:')&&anchorKey.includes(id)).map(([,anchor])=>anchor)).filter(Boolean);
   const earliest=memberAnchors.sort((a,b)=>Agent.items.indexOf(a)-Agent.items.indexOf(b))[0];
   Agent.cardAnchors.set(key,earliest||Agent.items.at(-1)||null);
  }
 }
 for(const card of cards.reverse()){
  const anchor=Agent.cardAnchors.get(card.dataset.agentCard),index=Agent.items.indexOf(anchor);
  // Keep the result and its single action below the response for this turn.
  const response=anchor?.role==='user'&&Agent.items[index+1]?.role==='assistant'?Agent.items[index+1]:anchor;
  const responseIndex=Agent.items.indexOf(response);
  if(responseIndex>=0&&messageNodes[responseIndex])messageNodes[responseIndex].after(card);else messages.prepend(card);
 }
 if(oldMessages){agentPatchChildren(oldMessages,messages);messages.agentRetained=oldMessages;}
 else for(const child of messages.children)agentMarkup.set(child,child.outerHTML);
 if(oldCompose){
  const fresh=template.content.querySelector('#agent-compose');
  for(const selector of ['.agent-attachments','.agent-compose-actions']){
   const old=oldCompose.querySelector(selector),next=fresh.querySelector(selector),markup=next.outerHTML;
   if(agentMarkup.get(old)!==markup){old.replaceWith(next);agentMarkup.set(next,markup);}
  }
  const input=oldCompose.querySelector('#agent-input');
  if(input.value!==Agent.draft)input.value=Agent.draft;
  input.disabled=false;fresh.agentRetained=oldCompose;
 }
 agentPatchChildren(content,template.content);
 AgentViewport.mount($('.agent-messages'));agentSizeInput();window.NexusChatContinuity?.afterRender();
 const currentResults=(s?.plans||[]).filter(e=>jobsForPlan(e.plan.id).length);
 const previousResults=[...new Map([...(state.agentConversation?.flowPlans||[]),...(state.agentConversation?.resultPlans||[])].map(e=>[e.plan.id,e])).values()].filter(e=>!currentResults.some(c=>c.plan.id===e.plan.id));
 // Keep the archive id so later archives overwrite the same conversation instead of duplicating it.
 state.agentConversation={...(state.agentConversation?.id?{id:state.agentConversation.id}:{}),draft:Agent.draft,draftAttachments:Agent.attachments,items:Agent.items,resultPlans:[...previousResults,...currentResults],anchors:Object.fromEntries([...Agent.cardAnchors].map(([key,item])=>[key,Agent.items.indexOf(item)]))};persist();
}
const originalRender=render;
render=function(){
 $('#content').classList.remove('agent-view');
 if(view!=='assistant')window.NexusChatContinuity?.leaveView();
 if(view==='assistant'){
  renderShell();
  $('#title').textContent='Asistente';$('#eyebrow').textContent='NEXUS · AGENTE';
  $('#header-actions').innerHTML='';
  $('#content').classList.remove('canvas-view');renderAssistant();
 }else originalRender();
 $('#navigation').insertAdjacentHTML('afterbegin',`<button data-nav="assistant" class="${view==='assistant'?'active':''}">${aiIcon('chat')}Asistente</button>`);
 if(view==='assistant')$('#navigation').querySelectorAll('[data-nav]:not([data-nav="assistant"])').forEach(b=>b.classList.remove('active'));
};
async function ensureAssistantReady(){
 if(Agent.snapshot)return Agent.snapshot;
 if(Agent.initPromise)return Agent.initPromise;
 Agent.initPromise=(async()=>{try{await agentCall('snapshot');await agentCall('importHistory');return Agent.snapshot;}finally{Agent.initPromise=null;}})();
 return Agent.initPromise;
}
async function sendAgent(requestText){
 if(window.NexusChatContinuity?.isSwitching())return;
 const text=Agent.draft.trim();if(!text||Agent.generating)return;
 if(Agent.initPromise){try{await Agent.initPromise;}catch{}}
 if(Agent.busy)return;
 window.NexusModelSuggestions?.close();
 Agent.error='';
 if(!Agent.snapshot||Agent.snapshot.offline){
  try{await ensureAssistantReady();}catch(error){Agent.error=error.message;if(view==='assistant')renderAssistant();return;}
  if(Agent.snapshot.offline){toast('El asistente necesita una conexion LLM. Configurala en sus ajustes.');agentSettings();return;}
 }
 const attachments=Agent.attachments.slice(),sent={role:'user',text,attachments};Agent.items.push(sent);Agent.draft='';Agent.attachments=[];AgentViewport.latest();
 $('#agent-input')?.focus({preventScroll:true});
 try{const result=await agentCall('send',{text:requestText||text,attachments:attachments.map(f=>({id:f.id,uri:f.url,mimeType:f.mime,name:f.name}))});
  Agent.items.push({role:'assistant',text:result.text,html:result.html,usage:result.inputTokens||result.outputTokens?`${result.inputTokens} entrada · ${result.outputTokens} salida`:null});
 }catch(error){sent.failed=true;Agent.error=error.message;if(!Agent.draft){Agent.draft=text;Agent.attachments=attachments;}}
 if(view==='assistant')renderAssistant();
}
function agentSettings(){
 const s=Agent.settings;
 modal(`<h2>Ajustes del asistente</h2><form id="agent-settings"><div class="fields"><label>Conexión<select name="backend"><option value="direct">Bedrock directo</option><option value="proxy">Proxy Bedrock</option></select></label><label>Modelo<input name="modelId" value="${escape(s.modelId||'')}" required></label><label>Región<input name="region" value="${escape(s.region||'us-east-1')}" required></label><label>Máximo de tokens<input name="maxTokens" type="number" min="256" max="32000" value="${s.maxTokens||4096}"></label><label>URL del proxy<input name="endpoint" value="${escape(s.endpoint||'')}"></label><label>Familias excluidas<input name="excludedFamilies" value="${escape(s.excludedFamilies||'')}"></label>${[['accessKey','AWS Access Key'],['secretKey','AWS Secret Key'],['sessionToken','AWS Session Token'],['token','Token del proxy']].map(([id,label])=>`<label>${label}<input name="${id}" type="password" autocomplete="new-password" placeholder="${s.configured?.includes(id)?'Configurado':'Sin configurar'}"></label>`).join('')}</div><label class="agent-check"><input name="sendImages" type="checkbox" ${s.sendImages?'checked':''}> Enviar imágenes al asistente</label><button class="primary" type="submit">Guardar</button></form>`);
 $('#agent-settings [name=backend]').value=s.backend||'direct';
}
document.addEventListener('input',e=>{if(e.target.id==='agent-input'){Agent.draft=e.target.value;agentSizeInput();}});
document.addEventListener('keydown',e=>{
 if(e.target.id!=='agent-input'||e.key!=='Enter'||e.shiftKey||e.altKey||e.metaKey||e.isComposing)return;
 e.preventDefault();if(!e.repeat)sendAgent();
});
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
   if(formId==='agent-batch-edit'){const batch=findGenerationBatch(fields.batchId);if(!batch)throw Error('El lote ya no está disponible.');await consolidateBatch(batch,fields.prompt);}
  $('#dialog').close();
 }catch(error){toast(error.message);}
});
document.addEventListener('click',async e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 if(b.hasAttribute('data-agent-latest')){AgentViewport.latest();return;}
 if(Agent.generating&&(b.dataset.agent||Object.keys(b.dataset).some(key=>key.startsWith('agent'))))return;
 try{
  if(b.dataset.nav==='assistant'&&!Agent.busy)await ensureAssistantReady();
  if(b.dataset.agentMode)await agentCall('mode',{mode:b.dataset.agentMode});
  if(b.dataset.agentRemove){Agent.attachments=Agent.attachments.filter(f=>f.id!==b.dataset.agentRemove);renderAssistant();}
   if(b.dataset.agentPromptToggle){if(Agent.expandedBatches.has(b.dataset.agentPromptToggle))Agent.expandedBatches.delete(b.dataset.agentPromptToggle);else Agent.expandedBatches.add(b.dataset.agentPromptToggle);renderAssistant();return;}
  for(const action of ['apply','reject','discard'])if(b.dataset['agent'+action[0].toUpperCase()+action.slice(1)])await agentCall(action,{id:b.dataset['agent'+action[0].toUpperCase()+action.slice(1)]});
   if(b.dataset.agentBatchCanvas||b.dataset.agentBatchRegenerate){const batch=findGenerationBatch(b.dataset.agentBatchCanvas||b.dataset.agentBatchRegenerate);if(!batch)throw Error('El lote ya no está disponible.');await openBatchCanvas(batch,Boolean(b.dataset.agentBatchRegenerate));return;}
   if(b.dataset.agentBatchEdit){const batch=findGenerationBatch(b.dataset.agentBatchEdit);if(!batch)throw Error('El lote ya no está disponible.');modal(`<h2>Editar prompt</h2><form id="agent-batch-edit"><input type="hidden" name="batchId" value="${escape(batch.id)}"><label>Prompt<textarea name="prompt">${escape(batch.plan.prompt)}</textarea></label><button class="primary">Guardar</button></form>`);return;}
   if(b.dataset.agentBatchGenerate){const batch=findGenerationBatch(b.dataset.agentBatchGenerate);if(!batch)throw Error('El lote ya no está disponible.');await generateAgentEntries(batch.entries);return;}
  if(b.dataset.agentDiscard){if(Agent.snapshot.plans.some(p=>p.plan.id===b.dataset.agentDiscard))await agentCall('discard',{id:b.dataset.agentDiscard});else{for(const entry of state.agentConversation?.resultPlans||[])if(entry.plan.id===b.dataset.agentDiscard)entry.state='DISCARDED';persist();renderAssistant();}return;}
  if(b.dataset.agentCanvas){const p=(Agent.snapshot.plans.find(p=>p.plan.id===b.dataset.agentCanvas)||state.agentConversation?.resultPlans?.find(p=>p.plan.id===b.dataset.agentCanvas))?.plan;if(!p)throw Error('No se encontró el encargo archivado.');
   const m=await agentModel(p.ref);state.draft={modelId:p.ref.modelId,prompt:p.prompt,originalPrompt:p.originalPrompt,values:p.params,media:agentMedia(p,m)};await openModel(p.ref.modelId,false);persist();}
  if(b.dataset.agentEdit){const p=Agent.snapshot.plans.find(p=>p.plan.id===b.dataset.agentEdit).plan,m=await agentModel(p.ref);
   modal(`<h2>Editar plan</h2><form id="agent-edit"><input type="hidden" name="id" value="${p.id}"><label>Prompt<textarea name="prompt">${escape(p.prompt)}</textarea></label><div class="fields">${m.parameters.map(param=>agentEditControl(param,p.effectiveParams[param.key])).join('')}</div><button class="primary">Guardar cambios</button></form>`);}
  if(b.dataset.agentGenerate){
   const entry=Agent.snapshot.plans.find(p=>p.plan.id===b.dataset.agentGenerate);
   if(!entry)throw Error('El encargo ya no está disponible.');
   await generateAgentEntries([entry]);return;
  }
  switch(b.dataset.agent){
   case'reconnect':try{await rpc('init');await agentCall('snapshot');Agent.error='';toast('Conexión local comprobada. No se ha reenviado el mensaje.');}catch(error){Agent.error=error.message;}if(view==='assistant')renderAssistant();break;
   case'send':e.preventDefault();await sendAgent();break;
   case'generate-ready':await generateAgentEntries(agentPendingEntries());break;
   case'attach':e.preventDefault();Agent.attachments.push(...(await rpc('pick')).slice(0,Math.max(0,6-Agent.attachments.length)));renderAssistant();$('#agent-input')?.focus({preventScroll:true});break;
   case'new':if(window.NexusChatContinuity?.isSwitching())throw Error('Espera a que se abra la conversación.');if(Agent.initPromise){try{await Agent.initPromise;}catch{}}if(window.NexusChatContinuity)await window.NexusChatContinuity.archiveCurrent();else if(Agent.items.length)await rpc('archiveChat',{...state.agentConversation,items:Agent.items,draft:Agent.draft});await agentCall('new');Agent.items=[];Agent.draft='';Agent.attachments=[];Agent.cardAnchors.clear();Agent.expandedBatches.clear();state.agentConversation=null;persist();AgentViewport.latest();renderAssistant();$('#agent-input')?.focus({preventScroll:true});break;
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
