'use strict';
function flowArguments(model,values,media=[]){
 const args={model:model.providerModelId};
 if(model.promptRequired===false)return {...args,source_request_id:values.source_request_id,resolution:values.resolution};
 args.aspect=values.aspect;
 if(model.flowMode)args.mode=model.flowMode;
 if(values.duration!=null&&values.duration!=='')args.duration=Number(values.duration);
 if(values.resolution)args.resolution=values.resolution;
 for(const field of model.fields||model.contractMedia||[]){const ids=media.filter(f=>f.field===field.key).map(f=>f.id);if(ids.length)args[field.key]=field.key==='reference_images'?ids:ids[0];}
 return args;
}
// Keep the existing providers available, while opening an owned Flow model's
// selector on that account. Apply after the Studio wrappers have loaded.
document.addEventListener('DOMContentLoaded',()=>{
 const listBefore=studioModelList;
 studioModelList=function(){listBefore();
  for(const row of document.querySelectorAll('[data-studio-model]')){const m=models.find(m=>m.id===row.dataset.studioModel);if(m?.providerId==='google_flow'){const price=row.querySelector('.studio-price');if(price)price.textContent='Tu cuenta Flow';}}
 };
 const previous=studioPicker;
 studioPicker=function(){previous();if(loaded?.providerId==='google_flow'){Studio.pickerProvider='google_flow';studioModelList();}};
 const renderBefore=renderCanvas;
 renderCanvas=function(){renderBefore();scheduleFlowAvailability();};
});
let flowAvailabilityTimer,flowAvailabilityKey='',flowAvailabilityExpiry,flowAvailabilityValue=null,flowAvailabilityVerifiedKey='';
function scheduleFlowAvailability(){
 clearTimeout(flowAvailabilityTimer);
 if(view!=='canvas'||!loaded||loaded.providerId!=='google_flow'||busy||quote)return;
 if($('#cost'))$('#cost').textContent='Disponibilidad sin consultar';
 const plan={model:loaded,...structuredClone(state.draft)};
 if(E.validate(plan.model,'Disponibilidad de la cuenta',plan.values,plan.media))return;
 const key=JSON.stringify(flowArguments(plan.model,plan.values,plan.media));
 if(key===flowAvailabilityVerifiedKey&&flowAvailabilityValue&&Date.now()-flowAvailabilityValue.time<300000){
  quote=flowAvailabilityValue;if($('#cost'))$('#cost').textContent=amount(quote);updateReadiness();return;
 }
 if(key===flowAvailabilityKey)return;
 flowAvailabilityTimer=setTimeout(async()=>{
  if(view!=='canvas'||!loaded||JSON.stringify(flowArguments(loaded,state.draft.values,state.draft.media))!==key)return;
  flowAvailabilityKey=key;
  try{
   const verified=await quotePlan(plan);
   if(view!=='canvas'||!loaded||JSON.stringify(flowArguments(loaded,state.draft.values,state.draft.media))!==key)return;
   quote=verified;flowAvailabilityValue=verified;flowAvailabilityVerifiedKey=key;clearTimeout(flowAvailabilityExpiry);
   flowAvailabilityExpiry=setTimeout(()=>{if(quote===verified){quote=null;flowAvailabilityKey='';if(view==='canvas')renderCanvas();}},300001);
   renderCanvas();
  }catch(error){if(view==='canvas'&&loaded?.providerId==='google_flow'&&$('#cost'))$('#cost').textContent='Cuenta sin comprobar';}
 },450);
}
document.addEventListener('input',scheduleFlowAvailability);
document.addEventListener('change',scheduleFlowAvailability);
document.addEventListener('click',e=>{if(e.target.closest('[data-remove],[data-pick]'))setTimeout(scheduleFlowAvailability,0);});
let flowAssetPlan=null,flowAssetEpoch=0,flowAssetTimer=null;
function clearFlowAssetPlan(){flowAssetPlan=null;flowAssetEpoch++;clearTimeout(flowAssetTimer);flowAssetTimer=null;}
$('#dialog').addEventListener('close',clearFlowAssetPlan);
async function flowAssetDialog(id){
 const source=state.jobs.find(j=>j.id===id);
 if(!source||source.providerId!=='google_flow'||source.status!=='COMPLETED')throw Error('Elige un resultado completado de Flow.');
 clearFlowAssetPlan();const epoch=flowAssetEpoch;
 modal('<h2>Mejorar o exportar</h2><p data-flow-options-loading role="status"><span class="loading"></span> Consultando opciones de tu cuenta Flow…</p>');
 try{
  const details=await rpc('flowAssetOptions',{source_request_id:source.remoteId||source.id});
  if(epoch!==flowAssetEpoch||!$('#dialog').open||!$('#dialog [data-flow-options-loading]'))return;
  const allowed=details.kind==='IMAGE'?['2k','4k']:details.kind==='VIDEO'?['1080p','4k','gif']:[];
  const options=(details.options||[]).filter(o=>allowed.includes(o.resolution)&&Number.isFinite(o.credits)&&o.credits>=0).sort((a,b)=>Number(a.resolution==='gif')-Number(b.resolution==='gif'));
  if(!options.length)throw Error('Flow no confirma una mejora con coste válido para este resultado.');
  flowAssetPlan={sourceId:source.id,sourceRequestId:source.remoteId||source.id,prompt:source.prompt||'',details,options,time:Date.now()};
  modal('<h2>Mejorar o exportar</h2><p>'+escape(source.name||source.modelId)+'</p><p class="hint">Se creará una versión nueva. El original se conserva.</p><label>Resultado<select id="flow-asset-resolution" aria-label="Resolución de la nueva versión">'+options.map(o=>'<option value="'+escape(o.resolution)+'">'+escape(o.resolution==='gif'?'GIF animado · 270p':o.resolution.toUpperCase())+' · '+o.credits+' créditos Flow</option>').join('')+'</select></label><p id="flow-asset-cost" role="status"></p><div class="toolbar"><button class="primary" data-flow-process="confirm">Crear versión</button><button data-flow-requote="'+escape(id)+'">Actualizar opciones</button></div>');
  flowAssetPrice();flowAssetTimer=setTimeout(flowAssetPrice,300001);
 }catch(error){
  if(epoch!==flowAssetEpoch||!$('#dialog').open||!$('#dialog [data-flow-options-loading]'))return;
  flowAssetPlan=null;modal('<h2>No se pudieron consultar las mejoras</h2><p role="alert">'+escape(error.message)+'</p><p class="hint">No se ha solicitado ninguna mejora.</p><button data-flow-requote="'+escape(id)+'">Volver a consultar</button>');
 }
}
function flowAssetPrice(){
 const option=flowAssetPlan?.options.find(o=>o.resolution===$('#flow-asset-resolution')?.value);if(!option)return;
 const expired=Date.now()-flowAssetPlan.time>300000,button=$('[data-flow-process]');
 $('#flow-asset-cost').textContent=expired?'La consulta ha caducado. Actualiza las opciones antes de crear la versión.':option.credits+' créditos Flow · Importe indicado por tu cuenta; saldo no verificado.';
 if(button){button.disabled=expired;button.textContent=option.resolution==='gif'?'Exportar GIF':'Crear versión '+option.resolution.toUpperCase();}
}
document.addEventListener('change',e=>{if(e.target.id==='flow-asset-resolution')flowAssetPrice();});
document.addEventListener('click',async e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 try{
  if(b.dataset.flowImprove||b.dataset.flowRequote){await flowAssetDialog(b.dataset.flowImprove||b.dataset.flowRequote);return;}
  if(b.dataset.flowProcess){
   if(!flowAssetPlan||Date.now()-flowAssetPlan.time>300000)throw Error('Actualiza las opciones de Flow antes de crear la versión.');
   b.disabled=true;const approved=structuredClone(flowAssetPlan),resolution=$('#flow-asset-resolution').value,option=approved.options.find(o=>o.resolution===resolution),source=state.jobs.find(j=>j.id===approved.sourceId);
   if(!option||!source||source.status!=='COMPLETED')throw Error('Vuelve a elegir el resultado original.');
   const epoch=flowAssetEpoch,modelId='google-flow/'+(resolution==='gif'?'video_gif':approved.details.kind==='IMAGE'?'image_upscale':'video_upscale'),model=await configuration(modelId);
   if(epoch!==flowAssetEpoch||!$('#dialog').open)return;
   const plan={model,prompt:approved.prompt,media:[],values:{source_request_id:approved.sourceRequestId,resolution},cost:{amount:option.credits,currency:'flow_credits',time:approved.time,detail:option.label}};
   $('#dialog').close();const job=await dispatch(plan);job.sourceJobId=approved.sourceId;await flush();Studio.tab='activity';navigate('library');
  }
 }catch(error){b.disabled=false;toast(error.message);}
});
