/* Explicit shot origins travel with the job, independently of the active screen. */
(() => {
 'use strict';
 const copy = value => value ? structuredClone(value) : null;
 const originalDispatch = dispatch;
 dispatch = async function(plan, batchId=null, meta={}) {
  const context=copy(plan.storyContext||null);
  const references=(plan.media||[]).map(file=>file.url||file.uri||file.remoteUrl).filter(Boolean);
  return originalDispatch(plan,batchId,{...meta,storyContext:context,
   referenceUris:references,...(context?{projectId:context.projectId}:{} )});
 };
 const originalAdapt=E.adapt;
 E.adapt=function(source,target,options){
  const plan=originalAdapt(source,target,options);
  plan.storyContext=copy(source.storyContext||null);
  return plan;
 };
 function fillReferences(){
  if(!loaded||!state.draft.storyReferencePending)return;
  const context=state.draft.storyContext;
  const assets=(context?.referenceAssetIds||[]).map(id=>Agent.snapshot?.index.assets.find(a=>a.id===id)).filter(Boolean);
  // A single input group has an unambiguous assignment. First/last-frame and
  // mixed-media schemas require an explicit assignment by the user or agent.
  if(loaded.fields.length===1){
   const field=loaded.fields[0],family=field.mime.split('/')[0]+'/';
   const files=assets.filter(asset=>(asset.mimeType||'').startsWith(family));
   if(files.length>(field.maximum||1)){
    toast('El modelo admite menos referencias. Elige cuáles utilizar en Referencias del plano.');
   }else state.draft.media=files.map(asset=>({id:asset.uri.split('/').pop(),url:asset.uri,
    ...(/^https:\/\//.test(asset.uri)?{remoteUrl:asset.uri}:{}),
    name:asset.displayName||'Referencia del plano',mime:asset.mimeType,field:field.key,assetId:asset.id}));
  }
  state.draft.storyReferencePending=false;
  persist();
 }
 const beforeOpen=openModel;
 openModel=async function(...args){await beforeOpen(...args);fillReferences();if(view==='canvas')renderCanvas();};
 const beforeCanvas=renderCanvas;
 renderCanvas=function(){
  beforeCanvas();
  const context=state.draft?.storyContext;
  if(!context||view!=='canvas')return;
  const banner=document.createElement('div');banner.className='story-generation-origin';
  const text=document.createElement('span');
  const shot=Agent.snapshot?.index.shots.find(s=>s.id===context.shotId);
  text.textContent=`Origen: ${shot?.title||shot?.code||'plano preparado'} · revisión ${context.revision}`;
  const clear=document.createElement('button');clear.className='small';clear.textContent='Quitar vínculo';
  clear.onclick=()=>{delete state.draft.storyContext;delete state.draft.storyReferencePending;persist();renderCanvas();};
  const references=document.createElement('button');references.className='small';
  references.textContent=`Referencias del plano (${context.referenceAssetIds?.length||0})`;
  references.onclick=()=>{try{chooseReferences();}catch(error){toast(error.message);}};
  banner.append(text,references,clear);document.getElementById('content').prepend(banner);
 };
 function chooseReferences(){
  if(!loaded)throw Error('Elige primero un modelo en ArtBox.');
  const assets=(state.draft.storyContext?.referenceAssetIds||[]).map(id=>Agent.snapshot?.index.assets.find(a=>a.id===id)).filter(Boolean);
  const supported=asset=>(loaded.fields||[]).filter(field=>(asset.mimeType||'').startsWith(field.mime.split('/')[0]+'/'));
  const rows=assets.map(asset=>`<label class="story-origin-reference"><img width="48" height="48" style="object-fit:cover;border-radius:8px" src="/library-preview/${encodeURIComponent(asset.id)}" alt=""><span>${escape(asset.displayName||asset.id)}</span><select data-story-origin-asset="${escape(asset.id)}"><option value="">Sin utilizar</option>${supported(asset).map(field=>`<option value="${escape(field.key)}" ${state.draft.media.some(file=>file.url===asset.uri&&file.field===field.key)?'selected':''}>${escape(field.label||E.label(field.key))}</option>`).join('')}</select></label>`).join('');
  modal(`<h2>Referencias del plano</h2><p class="hint">Asigna cada referencia a una entrada compatible del modelo.</p>${rows||'<p>No hay referencias efectivas en este plano.</p>'}<p id="story-origin-error" role="alert"></p><button class="primary" id="story-origin-apply">Aplicar referencias</button>`);
  document.getElementById('story-origin-apply').onclick=()=>{
   try{
    const assetUris=new Set(assets.map(asset=>asset.uri));
    const files=state.draft.media.filter(file=>!assetUris.has(file.url));
    for(const select of document.querySelectorAll('[data-story-origin-asset]')){
     if(!select.value)continue;
     const asset=assets.find(item=>item.id===select.dataset.storyOriginAsset);
     files.push({id:asset.uri.split('/').pop(),url:asset.uri,...(/^https:\/\//.test(asset.uri)?{remoteUrl:asset.uri}:{}),name:asset.displayName||'Referencia',mime:asset.mimeType,field:select.value,assetId:asset.id});
    }
    for(const field of loaded.fields)if(files.filter(file=>file.field===field.key).length>(field.maximum||1))throw Error(`Demasiadas referencias para ${field.label||E.label(field.key)}.`);
    state.draft.media=files;quote=null;persist();document.getElementById('dialog').close();renderCanvas();
   }catch(error){document.getElementById('story-origin-error').textContent=error.message;}
  };
 }
 async function prepare(context){
  if(!context?.projectId||!context.shotId||typeof context.context!=='string')throw Error('Falta el contexto verificable del plano.');
  state.draft.storyContext=copy(context);
  state.draft.storyReferencePending=true;
  const shot=Agent.snapshot?.index.shots.find(s=>s.id===context.shotId);
  state.draft.prompt=shot?.description||shot?.action||context.context;
  state.draft.media=[];
  quote=null;fillReferences();persist();
 }
 let importing=false,lastImported='',lastError='';
 setInterval(async()=>{
  if(importing||Agent.busy||!Agent.snapshot)return;
  const jobs=state.jobs.filter(job=>job.storyContext&&job.status==='COMPLETED'&&job.outputs?.length);
  const fingerprint=JSON.stringify(jobs.map(job=>[job.id,job.outputs]));
  if(!jobs.length||fingerprint===lastImported||fingerprint===lastError)return;
  importing=true;
  try{await agentCall('importHistory');lastImported=fingerprint;window.dispatchEvent(new CustomEvent('nexus:library-changed'));}
  catch(error){lastError=fingerprint;toast('Resultado conservado. Abre el agente para reindexarlo: '+error.message);}
  finally{importing=false;}
 },5000);
 window.NexusStoryGeneration={prepare,fillReferences};
})();
