'use strict';
// Shared dispatch also exposes these catalog operations to Nexus Agent.
function flowArguments(model,values){
 return {model:model.providerModelId,...(model.providerModelId==='nano2'||model.providerModelId==='veo_fast'?
  {aspect:values.aspect}:{source_request_id:values.source_request_id,resolution:values.resolution})};
}
let flowAssetPlan=null;
async function flowAssetDialog(id){
 const source=state.jobs.find(j=>j.id===id);
 if(!source||source.providerId!=='google_flow'||source.status!=='COMPLETED')throw Error('Elige un resultado completado de Flow.');
 modal('<h2>Mejorar o exportar</h2><p><span class="loading"></span> Consultando opciones de tu cuenta Flow…</p>');
 const details=await rpc('flowAssetOptions',{source_request_id:source.remoteId||source.id});
 const options=details.options.filter(o=>o.resolution!=='original').sort((a,b)=>Number(a.resolution==='gif')-Number(b.resolution==='gif'));
 if(!options.length)throw Error('Flow no ofrece mejoras para este resultado.');
 flowAssetPlan={source,details,options};
 modal('<h2>Mejorar o exportar</h2><p>El original se conserva en tu biblioteca.</p><label>Resultado<select id="flow-asset-resolution">'+
  options.map(o=>'<option value="'+escape(o.resolution)+'">'+escape(o.resolution==='gif'?'GIF animado · 270p':o.resolution.toUpperCase())+' · '+o.credits+' créditos Flow</option>').join('')+
  '</select></label><p id="flow-asset-cost"></p><button class="primary" data-flow-process="confirm">Crear resultado</button>');
 flowAssetPrice();
}
function flowAssetPrice(){
 const option=flowAssetPlan?.options.find(o=>o.resolution===$('#flow-asset-resolution')?.value);
 if(option)$('#flow-asset-cost').textContent=option.credits+' créditos Flow · Importe indicado por tu cuenta; saldo no verificado.';
}
document.addEventListener('change',e=>{if(e.target.id==='flow-asset-resolution')flowAssetPrice();});
document.addEventListener('click',async e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 try{
  if(b.dataset.flowImprove){await flowAssetDialog(b.dataset.flowImprove);return;}
  if(b.dataset.flowProcess){
   if(!flowAssetPlan)throw Error('Vuelve a consultar las opciones.');
   const {source,details,options}=flowAssetPlan,resolution=$('#flow-asset-resolution').value,option=options.find(o=>o.resolution===resolution);
   const modelId='google-flow/'+(resolution==='gif'?'video_gif':details.kind==='IMAGE'?'image_upscale':'video_upscale');
   const model=await configuration(modelId);
   const plan={model,prompt:source.prompt||'',media:[],values:{source_request_id:source.remoteId||source.id,resolution},
    cost:{amount:option.credits,currency:'flow_credits',time:Date.now(),detail:option.label}};
   b.disabled=true;$('#dialog').close();flowAssetPlan=null;
   const job=await dispatch(plan);job.sourceJobId=source.id;await flush();Studio.tab='activity';navigate('library');
  }
 }catch(error){b.disabled=false;toast(error.message);}
});
