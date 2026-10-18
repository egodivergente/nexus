'use strict';
const CustomMedia={configs:[],loading:false,loaded:false,needsIndex:false,syncing:false};
const customCall=(op,args={})=>rpc('customMedia',{op,args});
function installCustomCatalog(result){
 CustomMedia.configs=result.configs;CustomMedia.loaded=true;
 models=models.filter(m=>!m.customMedia);models.push(...result.models);
 for(const key of Object.keys(providers))if(key.startsWith('usr_'))delete providers[key];
 for(const p of result.configs)providers['usr_'+p.id]=p.name;
 initial.configured=initial.configured.filter(k=>!k.startsWith('usr_'));initial.configured.push(...result.configured);
 configurations.clear();
 if(window.nexusQA)window.nexusQA.models=models;
}
async function loadCustomCatalog(){
 if(CustomMedia.loading||!initial)return;
 CustomMedia.loading=true;
 try{installCustomCatalog(await customCall('list'));render();}catch(e){toast('No se han cargado los proveedores personalizados: '+e.message);}finally{CustomMedia.loading=false;}
}
const renderBeforeCustomMedia=render;
render=function(){renderBeforeCustomMedia();if(initial&&!CustomMedia.loaded&&!CustomMedia.loading)loadCustomCatalog();};
const settingsBeforeCustomMedia=renderSettings;
renderSettings=function(){
 settingsBeforeCustomMedia();
 // Generic providers have their own protected key fields and connection contract.
 document.querySelectorAll('[data-parity-provider^="usr_"]').forEach(b=>b.closest('.account')?.remove());
 const section=document.createElement('section');section.className='sheet';section.id='custom-media-settings';
 section.innerHTML=`<h2>Generadores personalizados</h2><p class="hint">Imagen, vídeo y escalado mediante una API JSON. La tarifa configurada se muestra como estimación.</p>${CustomMedia.configs.map(p=>`<div class="account"><div><h3>${escape(p.name)}</h3><small>${p.models.length} modelos · ${initial.configured.includes('usr_'+p.id)?'Clave guardada':'Sin clave / acceso local'}</small></div><button data-custom-edit="${escape(p.id)}">Editar</button></div>`).join('')}<button data-custom-edit="">Añadir proveedor</button>`;
 $('#settings-container')?.append(section);
};
const customTemplate={id:'mi-proveedor',name:'Mi generador',baseUrl:'https://example.com/api',auth:{mode:'BEARER'},models:[{id:'imagen',name:'Mi modelo de imagen',capability:'TEXT_TO_IMAGE',createPath:'/generate',requestTemplate:'{"model":"{{model}}","prompt":"{{prompt}}"}',mode:'SYNC',resultPaths:['images.*.url'],priceUsd:null,priceUnit:'generation'}]};
function customMediaForm(id){
 const config=CustomMedia.configs.find(p=>p.id===id)||customTemplate;
 modal(`<h2>Generador personalizado</h2><form id="custom-media-form"><p class="hint">Define las rutas y los campos que documenta tu proveedor. No incluyas claves en el JSON.</p><label>Configuración<textarea name="config" rows="20" spellcheck="false">${escape(JSON.stringify(config,null,2))}</textarea></label><label>Clave API<input type="password" name="key" autocomplete="off" placeholder="Vacío conserva la guardada"></label><p id="custom-media-diagnostic" role="status"></p><div class="toolbar"><button type="submit" class="primary">Guardar</button>${id?`<button type="button" data-custom-delete="${escape(id)}">Eliminar proveedor</button>`:''}</div></form>`);
 $('#custom-media-form').addEventListener('submit',async e=>{
  e.preventDefault();const values=Object.fromEntries(new FormData(e.target));
  try{const config=JSON.parse(values.config);if(id&&config.id!==id)throw Error('Conserva el identificador para editar este proveedor.');installCustomCatalog(await customCall('save',{config,key:values.key}));$('#dialog').close();render();toast('Proveedor guardado');}catch(error){$('#custom-media-diagnostic').textContent=error.message;}
 });
 const form=$('#custom-media-form');
 const probe=document.createElement('button');probe.type='button';probe.textContent='Guardar y probar conexión';
 probe.addEventListener('click',async()=>{
  const status=$('#custom-media-diagnostic');
  try{
   const values=Object.fromEntries(new FormData(form)),config=JSON.parse(values.config);
   if(id&&config.id!==id)throw Error('Conserva el identificador para editar este proveedor.');
   installCustomCatalog(await customCall('save',{config,key:values.key}));
   status.textContent='Comprobando el endpoint configurado…';
   const result=await customCall('test',{id:config.id});
   status.textContent='Conexión HTTP '+result.httpStatus+'. Esta comprobación no ha creado una generación.';
  }catch(error){status.textContent=error.message;}
 });
 form.querySelector('.toolbar').append(probe);
}
document.addEventListener('click',async e=>{
 const edit=e.target.closest('[data-custom-edit]');if(edit)return customMediaForm(edit.dataset.customEdit);
 const remove=e.target.closest('[data-custom-delete]');if(!remove)return;
 try{installCustomCatalog(await customCall('delete',{id:remove.dataset.customDelete}));$('#dialog').close();render();toast('Proveedor eliminado. El historial se conserva.');}catch(error){$('#custom-media-diagnostic').textContent=error.message;}
});
const quoteBeforeCustomMedia=providerQuotePlan;
providerQuotePlan=function(plan){return plan.model.customMedia?customCall('quote',{model:plan.model.id,values:plan.values||{}}):quoteBeforeCustomMedia(plan);};
const dispatchBeforeCustomMedia=dispatch;
dispatch=async function(plan,batchId=null,meta={}){
 const m=plan.model;if(!m.customMedia)return dispatchBeforeCustomMedia(plan,batchId,meta);
 if(initial.qa)throw Error('Laboratorio: envío bloqueado.');
 if(!Agent.snapshot)await ensureAssistantReady();
 const validation=E.validate(m,plan.prompt,plan.values||{},plan.media||[]);if(validation)throw Error(validation);
 if(!plan.cost||plan.cost.source!=='user_declared'||plan.cost.currency!=='USD'||Date.now()-plan.cost.time>300000)throw Error('Consulta y acepta una estimación vigente.');
 const job={projectId:Agent.snapshot?.projectId||null,...meta,id:crypto.randomUUID(),providerId:m.providerId,modelId:m.id,name:m.displayName,modality:m.modalities[0],prompt:plan.prompt,originalPrompt:plan.originalPrompt||null,parameters:structuredClone(plan.values||{}),status:'SENDING',outputs:[],startedAt:Date.now(),batchId,estimated:structuredClone(plan.cost)};
 state.jobs.unshift(job);await flush();if(typeof refreshJobViews==='function')refreshJobViews([job.id]);
 try{
  // Local references stay local in the bridge. The host streams/uploads or embeds them once.
  const media=plan.media||[],uri=f=>f.remoteUrl||f.url||('/media/'+f.id);
  const input={prompt:plan.prompt,params:plan.values||{},imageUrls:media.filter(f=>f.mime.startsWith('image/')).map(uri),videoUrl:media.find(f=>f.mime.startsWith('video/'))?uri(media.find(f=>f.mime.startsWith('video/'))):null};
  const response=await customCall('submit',{id:job.id,model:m.id,input,cost:plan.cost});
  customJobResponse(job,response);await flush();if(job.status==='FAILED')throw Error(job.error||'El proveedor no completó la tarea.');return job;
 }catch(error){if(job.status==='SENDING'){job.status='FAILED';job.uncertain=true;job.error='Envío sin confirmar. '+error.message;}await flush();throw error;}
};
function customJobResponse(job,response){
 const completedBefore=job.status==='COMPLETED',oldOutputs=JSON.stringify(job.outputs||[]);
 job.remoteId=response.remoteId||job.remoteId;job.outputs=response.outputs||[];job.progress=response.progress;
 job.status=response.status==='UNCERTAIN'?'FAILED':response.status==='SENDING'?'PROCESSING':response.status;job.uncertain=!!response.uncertain;
 job.error=response.error||null;job.pollError=response.pollError||null;job.providerTaskId=response.providerTaskId||null;
 if(job.status==='COMPLETED')job.completedAt=Date.now();
 if(job.status==='COMPLETED'&&(!completedBefore||oldOutputs!==JSON.stringify(job.outputs)))CustomMedia.needsIndex=true;
}
// Existing polling and agent tools share this route. Never send a custom ID to Atlas.
const rpcBeforeCustomMedia=rpc;
rpc=function(op,args={}){
 if(op==='request'&&String(args.provider||'').startsWith('usr_')){
  const match=String(args.path||'').match(/^\/api\/v1\/model\/prediction\/([A-Za-z0-9_-]+)$/);
  if(args.method!=='GET'||!match||!state.jobs.some(j=>j.id===match[1]&&j.providerId===args.provider))return Promise.reject(Error('Operación no definida para este proveedor personalizado.'));
  const id=match[1];
  return customCall('status',{id}).then(result=>({...result,customMedia:true,status:result.status.toLowerCase()}));
 }
 return rpcBeforeCustomMedia(op,args);
};
const parseBeforeCustomMedia=parseResponse;
parseResponse=function(job,response){if(response.customMedia)return customJobResponse(job,{...response,status:response.status.toUpperCase()});return parseBeforeCustomMedia(job,response);};
const callbackBeforeCustomMedia=agentCallback;
agentCallback=async function(op,args){
 const result=await callbackBeforeCustomMedia(op,args);
 if(op==='history')return result.map(entry=>({...entry,projectId:state.jobs.find(j=>j.id===entry.jobId)?.projectId||null}));
 return result;
};
// An agent callback must finish before another JVM command can run. Index results
// once the turn is idle; never issue a nested command while confirm/send is waiting.
setInterval(async()=>{
 if(!CustomMedia.needsIndex||CustomMedia.syncing||Agent.busy||Agent.generating)return;
 CustomMedia.syncing=true;CustomMedia.needsIndex=false;
 try{await agentCall('importHistory');if(view==='editor')renderCorteAssets();}
 catch{CustomMedia.needsIndex=true;}
 finally{CustomMedia.syncing=false;}
},1000);
