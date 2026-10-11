'use strict';
function createNexusTelemetry(clock=()=>performance.now(),wall=()=>Date.now()){
 const known=new Set(['init','save','request','generate','upload','download','schema','flowSubmit','flowStatus','flowConnection','flowQuote','flowRecover','flowAssetOptions','flowConnect','flowAutomation','flowPolicy','flowCancel','agent','agentEvents','agentReply','pick','openData','localPing']);
 const providers=new Set(['atlas_cloud','runway','crun','google_flow']);
 const active=new Map(),rows=[];let serial=0,enabled=true,total=0,errors=0;
 function begin(op,args={}){
  if(!enabled)return null;const id=++serial;
  const provider=providers.has(args.provider)?args.provider:op.startsWith('flow')?'google_flow':op==='schema'?'atlas_cloud':null;
  active.set(id,{op:known.has(op)?op:'other',provider,start:clock()});return id;
 }
 function finish(id,ok,code='rpc_error'){
  const item=active.get(id);if(!item)return;active.delete(id);if(!enabled)return;
  rows.push({op:item.op,provider:item.provider,ms:Math.max(0,Math.round(clock()-item.start)),ok:!!ok,code:ok?null:['timeout','rpc_error','bridge_error','ui_error'].includes(code)?code:'rpc_error',time:wall()});
  if(rows.length>200)rows.shift();total++;if(!ok)errors++;
 }
 function snapshot(){return {enabled,total,errors,inFlight:active.size,rows:rows.map(r=>({...r}))};}
 return {begin,finish,snapshot,clear:()=>{rows.length=0;total=0;errors=0;},setEnabled:v=>{enabled=!!v;},get enabled(){return enabled;}};
}
if(typeof module!=='undefined'&&module.exports)module.exports={createNexusTelemetry};
if(typeof document!=='undefined')(()=>{
 const T=createNexusTelemetry();window.NexusTelemetry=T;
 let dialog=null,pinging=false,lastPing=null,openedAt=0;
 const started=performance.now(),fmt=v=>v==null?'—':v<1000?Math.round(v)+' ms':(v/1000).toFixed(2)+' s';
 const average=rows=>rows.length?rows.reduce((n,r)=>n+r.ms,0)/rows.length:null;
 const label={init:'Inicio',save:'Guardar',generate:'Enviar generación',request:'Consulta API',upload:'Subida',download:'Descarga',schema:'Esquema',flowSubmit:'Envío Flow',flowStatus:'Estado Flow',flowConnection:'Sesión Flow',agent:'Asistente',agentEvents:'Eventos',agentReply:'Respuesta al asistente',localPing:'Sonda local',pick:'Selector de archivos',openData:'Carpeta de datos'};
 Object.assign(label,{flowQuote:'Cotización Flow',flowRecover:'Recuperación Flow',flowAssetOptions:'Opciones de mejora Flow',flowConnect:'Acceso Flow',flowAutomation:'Cola Flow',flowPolicy:'Límites Flow',flowCancel:'Cancelar pendiente Flow'});
 function mount(){
  if($('#telemetry-button')||!$('#header-actions'))return;
  const b=document.createElement('button');b.id='telemetry-button';b.type='button';b.textContent='Telemetría';b.addEventListener('click',open);$('#header-actions').append(b);
 }
 function draw(){
  mount();const s=T.snapshot(),local=s.rows.filter(r=>r.ok&&['init','save','localPing'].includes(r.op));
  const b=$('#telemetry-button');if(b){b.textContent='Telemetría'+(T.enabled&&local.length?' · '+fmt(local.at(-1).ms):'');b.title='Latencia local y estado de solicitudes';}
  if(!dialog?.open)return;
  const jobs=state.jobs||[],working=jobs.filter(j=>['SENDING','QUEUED','PROCESSING','GENERATING'].includes(j.status));
  const memory=performance.memory?.usedJSHeapSize;
  $('#telemetry-metrics').innerHTML=`<div><small>Servicio local · última sonda</small><strong>${lastPing?lastPing.ok?fmt(lastPing.ms):'Sin respuesta':'Sin medir'}</strong></div><div><small>Operaciones en vuelo</small><strong>${pending.size}</strong></div><div><small>Trabajos activos</small><strong>${working.length}</strong></div><div><small>Operaciones / errores de sesión</small><strong>${s.total} / ${s.errors}</strong></div><div><small>Memoria JavaScript aproximada</small><strong>${Number.isFinite(memory)?Math.round(memory/1048576)+' MB':'No disponible'}</strong></div><div><small>Sesión de interfaz</small><strong>${Math.floor((performance.now()-started)/60000)} min</strong></div>`;
  $('#telemetry-providers').innerHTML=Object.entries(providers).map(([id,name])=>{const rows=s.rows.filter(r=>r.provider===id),ok=rows.filter(r=>r.ok);return `<tr><td>${escape(name)}</td><td>${fmt(average(ok))}</td><td>${ok.length} / ${rows.filter(r=>!r.ok).length}</td></tr>`;}).join('');
  $('#telemetry-rows').innerHTML=s.rows.slice(-12).reverse().map(r=>`<tr><td>${new Date(r.time).toLocaleTimeString('es-ES')}</td><td>${escape(label[r.op]||'Operación')}<small>${r.provider?' · '+escape(providers[r.provider]):''}</small></td><td>${fmt(r.ms)}</td><td>${r.ok?'Correcto':r.code==='timeout'?'Tiempo agotado':'Error'}</td></tr>`).join('')||'<tr><td colspan="4">Todavía no hay mediciones.</td></tr>';
  $('#telemetry-toggle').textContent=T.enabled?'Pausar medición':'Reanudar medición';$('#telemetry-toggle').setAttribute('aria-pressed',String(T.enabled));
  $('#telemetry-ping').disabled=pinging||!T.enabled;
 }
 async function ping(){
  if(pinging||!T.enabled)return;pinging=true;const trace=T.begin('localPing'),t=performance.now();draw();
  try{
   if(location.protocol==='http:'&&location.hostname==='127.0.0.1'){
    const r=await fetch('/health',{cache:'no-store',signal:AbortSignal.timeout(5000)});if(!r.ok)throw Error('health');
    const body=await r.json();if(body.app!=='Nexus Studio')throw Error('identity');
   }else await rpc('init');
   lastPing={ok:true,ms:performance.now()-t};T.finish(trace,true);
  }catch{lastPing={ok:false,ms:performance.now()-t};T.finish(trace,false,'rpc_error');}
  finally{pinging=false;draw();}
 }
 function open(){
  if(dialog?.open)return;
  dialog=document.createElement('dialog');dialog.id='telemetry-panel';dialog.setAttribute('aria-labelledby','telemetry-title');
  dialog.innerHTML='<header><h2 id="telemetry-title">Telemetría y latencia</h2><button id="telemetry-close" aria-label="Cerrar telemetría">✕</button></header><p class="hint">Medición local de esta sesión. Latencia = duración hasta la respuesta; incluye el servicio y el proveedor, no el tiempo total de generación.</p><div id="telemetry-metrics"></div><div class="telemetry-controls"><button id="telemetry-ping">Medir ahora</button><button id="telemetry-toggle">Pausar medición</button><button id="telemetry-export">Exportar JSON</button><button id="telemetry-clear">Vaciar historial</button></div><h3>Proveedores · últimas 200 operaciones</h3><div class="telemetry-table"><table><thead><tr><th>Proveedor</th><th>Media de respuestas correctas</th><th>Correctas / errores</th></tr></thead><tbody id="telemetry-providers"></tbody></table></div><h3>Actividad reciente</h3><div class="telemetry-table"><table><thead><tr><th>Hora</th><th>Operación</th><th>Duración</th><th>Resultado</th></tr></thead><tbody id="telemetry-rows"></tbody></table></div><p class="hint">No guarda prompts, claves, URLs ni respuestas. La memoria indicada pertenece a JavaScript de esta interfaz, no a toda la aplicación. La sonda local se actualiza cada 10 s mientras este panel está abierto y visible.</p>';
  document.body.append(dialog);dialog.showModal();openedAt=performance.now();
  const close=()=>{dialog?.close();dialog?.remove();dialog=null;$('#telemetry-button')?.focus();};
  $('#telemetry-close').onclick=close;dialog.addEventListener('cancel',e=>{e.preventDefault();close();});
  $('#telemetry-ping').onclick=ping;$('#telemetry-toggle').onclick=()=>{T.setEnabled(!T.enabled);draw();};$('#telemetry-clear').onclick=()=>{T.clear();lastPing=null;draw();};
  $('#telemetry-export').onclick=()=>{const url=URL.createObjectURL(new Blob([JSON.stringify({version:1,...T.snapshot(),lastPing},null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='nexus-telemetria.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
  draw();ping();$('#telemetry-close').focus();
 }
 const style=document.createElement('link');style.rel='stylesheet';style.href='studio-telemetry.css';document.head.append(style);
 new MutationObserver(mount).observe($('#header-actions'),{childList:true});
 setInterval(()=>{if(document.hidden)return;draw();if(dialog?.open&&T.enabled&&performance.now()-openedAt>=10000){openedAt=performance.now();ping();}},1000);
 mount();
})();
