(function(root){'use strict';
function createPolicy(data){
 const providers=new Set(data.curatedProviders),generators=new Set(data.generators),tools=data.tools||{};
 const keys=m=>[m.id,m.providerModelId].filter(Boolean).flatMap(id=>[m.providerId+':'+id,m.providerId+':'+id.replace(/^crun\//,'')]);
 const scoped=m=>providers.has(m.providerId)&&(m.modalities||[]).includes('VIDEO')&&!(m.modalities||[]).includes('CHAT');
 const generator=m=>scoped(m)&&keys(m).some(key=>generators.has(key));
 const tool=m=>scoped(m)?keys(m).map(key=>tools[key]).find(Boolean)||null:null;
 const allowed=m=>!scoped(m)||generator(m)||Boolean(tool(m));
 const visual=media=>(media||[]).some(file=>/^(image|video)\//.test(file.mime||file.mimeType||''));
 const validate=(m,media)=>!allowed(m)?'Este modelo se retiró de las nuevas generaciones. Su historial y material se conservan.':generator(m)&&!visual(media)?data.visualInputMessage:null;
 const byteplus=(m,values={})=>{
  if(![m.id,m.providerModelId,m.family,m.displayName].some(value=>/seedance/i.test(value||'')))return false;
  if(m.providerId==='usr_byteplus-media'||[m.id,m.providerModelId].some(id=>(id||'').startsWith('custom/byteplus-media/')))return true;
  const defaults=Object.fromEntries((m.parameters||[]).map(p=>[p.key,p.defaultValue]));
  return m.providerId==='crun'&&['direct_byteplus','byteplus_fallback'].some(key=>String(values[key]??defaults[key]).toLowerCase()==='true');
 };
 const orderRoutes=(routes,manual=false)=>manual?[...routes]:[...routes].sort((a,b)=>Number(byteplus(a.model,a.parameters))-Number(byteplus(b.model,b.parameters))||a.total_usd-b.total_usd);
 return {data,keys,scoped,generator,tool,allowed,validate,reachesBytePlus:byteplus,orderRoutes};
}
root.NexusCurrentCatalog={createPolicy};
if(typeof module!=='undefined'&&module.exports)module.exports={createPolicy};
if(typeof document==='undefined')return;
let policy=null,extra=[],failure=null;
const ready=Promise.all([
 fetch('curated-video-selection.json',{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('No se pudo cargar la selección de vídeo.');return r.json();}),
 fetch('catalog-current-additions.json',{cache:'no-store'}).then(r=>r.ok?r.json():[]),
]).then(([data,models])=>{policy=createPolicy(data);extra=models;root.NexusCurrentCatalog.policy=policy;refresh();return policy;})
 .catch(error=>{failure=error.message;return null;});
root.NexusCurrentCatalog.ready=ready;
function guard(m,media){
 if(!policy){if(['atlas_cloud','crun','runway','usr_byteplus-media'].includes(m?.providerId)&&(m.modalities||[]).includes('VIDEO'))return failure||'Cargando la selección de vídeo…';return null;}
 return policy.validate(m,media);
}
function addModels(){
 if(typeof models==='undefined'||!models.length||!extra.length)return;
 const known=new Set(models.map(m=>m.providerId+':'+m.id));
 for(const m of extra)if(!known.has(m.providerId+':'+m.id)){models.push(m);known.add(m.providerId+':'+m.id);}
}
function refresh(){addModels();if(typeof render==='function'&&typeof initial!=='undefined'&&initial)render();}
const validateBefore=E.validate;
E.validate=function(m,prompt,values,media){return guard(m,media)||validateBefore(m,prompt,values,media);};
const filteredBefore=filteredModels;
filteredModels=function(){addModels();return filteredBefore().filter(m=>policy?policy.allowed(m):!['atlas_cloud','crun','runway','usr_byteplus-media'].includes(m.providerId)||!(m.modalities||[]).includes('VIDEO'));};
if(typeof agentCallback==='function'){
 const callbackBefore=agentCallback;
 agentCallback=async function(op,args){
  if(op==='models'){await ready;addModels();return models.filter(m=>(policy?policy.allowed(m):!(m.modalities||[]).includes('VIDEO'))&&!state.hidden.includes(m.id)).map(agentSummary);}
  return callbackBefore(op,args);
 };
}
if(typeof studioModelList==='function'){
 const pickerBefore=studioModelList;
 studioModelList=function(){addModels();const all=models;models=all.filter(m=>policy?policy.allowed(m):!(m.modalities||[]).includes('VIDEO'));try{return pickerBefore();}finally{models=all;}};
}
const renderBefore=render;
render=function(){addModels();return renderBefore();};
const canvasBefore=renderCanvas;
renderCanvas=function(){canvasBefore();if(!loaded)return;const category=policy?.tool(loaded),error=guard(loaded,state.draft.media);if(!category&&!error)return;const host=document.querySelector('.canvas .selected');if(host&&!host.querySelector('[data-current-catalog]')){const line=document.createElement('small');line.dataset.currentCatalog='';line.className=error?'hint error':'hint';line.textContent=error||'Herramientas · '+category;host.append(line);}};
const rpcBefore=rpc;
rpc=function(op,args={}){
 if(op==='generate'){
  const receipt=(state.jobs||[]).find(job=>job.id===args.job_id);
  if(receipt?.modelId)args={...args,catalog_model_id:receipt.modelId};
 }
 return rpcBefore(op,args);
};
if(root.CatalogParity){
 const categoryBefore=CatalogParity.categoryOf;
 CatalogParity.categoryOf=m=>policy?.tool(m)?'tools':categoryBefore(m);
}
})(typeof window!=='undefined'?window:globalThis);
