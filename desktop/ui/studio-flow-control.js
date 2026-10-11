'use strict';
(()=>{
 let panel=null,snapshot=null,busy=false,checked=false;
 const oldParse=parseResponse;
 parseResponse=function(job,response){
  oldParse(job,response);if(job.providerId!=='google_flow')return;
  const r=response.data||response;job.requestedModel=r.requested_model||job.requestedModel;
  job.modelConfirmed=r.model_confirmed===true;job.flowQueuePhase=r.queue_phase;job.flowTimings=r.timings||job.flowTimings;
  if(r.status==='queued')job.status='QUEUED';
  if(r.status==='not_sent'){job.status='NOT_SENT';job.uncertain=false;}
 };
 const labels={queued:'En cola',running:'En ejecución',completed:'Completado',uncertain:'Por comprobar',not_sent:'No enviado'};
 const ms=v=>Number.isFinite(v)?v<1000?Math.round(v)+' ms':(v/1000).toFixed(1)+' s':'—';
 const oldStatus=typeof readableJobStatus==='function'?readableJobStatus:null;
 if(oldStatus)readableJobStatus=function(job){return job.status==='NOT_SENT'?'No enviado':oldStatus(job);};
 function mount(){
  if($('#flow-control-button')||!$('#header-actions')||!initial?.configured?.includes('google_flow'))return;
  const b=document.createElement('button');b.id='flow-control-button';b.textContent='Control Flow';b.onclick=open;$('#header-actions').append(b);
 }
 async function read(){
  if(busy||initial?.qa||!initial?.configured?.includes('google_flow'))return;
  busy=true;try{snapshot=await rpc('flowAutomation');draw();return snapshot;}catch(e){if(panel?.open)$('#flow-control-status').textContent=e.message;}finally{busy=false;}
 }
 function draw(){
  if(!panel?.open||!snapshot)return;const p=snapshot.policy,health=typeof FlowConnection!=='undefined'?FlowConnection.value:null;
  $('#flow-control-status').textContent=`${p.paused?'Cola pausada':'Cola disponible'} · ${snapshot.pending} pendientes o inciertos · ${snapshot.reserved_credits} créditos reservados`;
  $('#flow-control-health').textContent=`Sesión: ${health?.status==='connected'?'conectada':health?.status||'sin comprobar'} · comprobador: editor Flow · ${health?.checked_at?'comprobada '+new Date(health.checked_at*1000).toLocaleTimeString('es-ES'):'sin fecha'} · saldo sin consultar`;
  $('#flow-control-pause').textContent=p.paused?'Reanudar cola':'Pausar cola';
  $('#flow-control-rows').innerHTML=snapshot.requests.map(r=>{const j=state.jobs.find(j=>(j.remoteId||j.id)===r.id),t=r.metrics;return `<tr><td>${escape(j?.name||r.id)}<small>${r.mode==='recover'?'Consulta de recuperación':'Encargo'}</small></td><td>${labels[r.phase]||'Por comprobar'}</td><td>${ms(t.queue_ms)}</td><td>${ms(t.quote_ms)}</td><td>${ms(t.execution_ms??t.recovery_ms)}</td><td>${ms(t.materialize_ms)}</td><td>${ms(t.total_ms)}</td><td>${r.phase==='queued'&&r.mode==='submit'?`<button data-flow-cancel="${escape(r.id)}">Cancelar</button>`:''}</td></tr>`;}).join('')||'<tr><td colspan="8">Todavía no hay solicitudes en la cola.</td></tr>';
 }
 async function reconcile(){
  if(checked||initial?.qa||!initial?.configured?.includes('google_flow'))return;checked=true;const changed=[];
  for(const job of state.jobs.filter(j=>j.providerId==='google_flow'&&(j.status!=='COMPLETED'||typeof j.modelConfirmed!=='boolean')).slice(0,120)){
   try{parseResponse(job,await rpc('flowStatus',{request_id:job.remoteId||job.id}));changed.push(job.id);}catch{/* May have failed before reservation. */}
  }
  if(changed.length){await flush();refreshJobViews(changed);}
 }
 async function open(){
  if(panel?.open)return;panel=document.createElement('dialog');panel.id='flow-control';panel.setAttribute('aria-labelledby','flow-control-title');
  panel.innerHTML='<header><h2 id="flow-control-title">Control Flow</h2><button id="flow-control-close" aria-label="Cerrar Control Flow">✕</button></header><p id="flow-control-status" role="status">Consultando cola…</p><p id="flow-control-health" class="hint"></p><form id="flow-control-policy"><label>Resultados por solicitud<input name="max_results" type="number" min="1" max="120" required></label><label>Resultados pendientes en total<input name="max_total_results" type="number" min="1" max="120" required></label><label>Solicitudes pendientes<input name="max_pending" type="number" min="1" max="120" required></label><label>Presupuesto de créditos pendientes<input name="max_credits" type="number" min="0" max="100000" step="any" required></label><label class="flow-control-check"><input name="allow_unknown_cost" type="checkbox">Autorizar operaciones cuyo importe Flow no expone</label><button type="submit">Guardar límites</button></form><p class="hint">El presupuesto incluye encargos activos e inciertos. Un importe desconocido no se considera gratis. Tras un reinicio no se envían automáticamente solicitudes pendientes.</p><div class="toolbar"><button id="flow-control-pause">Pausar cola</button><button id="flow-control-refresh">Actualizar</button><button id="flow-control-export">Exportar telemetría</button></div><div class="flow-control-table"><table><thead><tr><th>Solicitud</th><th>Estado</th><th>Espera</th><th>Cotización inicial</th><th>Ejecución / recuperación</th><th>Copia local</th><th>Total</th><th></th></tr></thead><tbody id="flow-control-rows"></tbody></table></div><p class="hint">Ejecución incluye comprobar precio, trabajo Flow y descarga del puente. Copia local mide validación e incorporación a NEXUS. No son tiempos de red pura. Solo se cancelan solicitudes no enviadas.</p>';
  document.body.append(panel);panel.showModal();
  const close=()=>{panel?.close();panel?.remove();panel=null;$('#flow-control-button')?.focus();};$('#flow-control-close').onclick=close;panel.addEventListener('cancel',e=>{e.preventDefault();close();});
  $('#flow-control-refresh').onclick=read;
  $('#flow-control-pause').onclick=async()=>{try{await rpc('flowPolicy',{paused:!snapshot.policy.paused});await read();}catch(e){toast(e.message);}};
  $('#flow-control-policy').onsubmit=async e=>{e.preventDefault();const f=e.target;try{await rpc('flowPolicy',{max_results:Number(f.elements.max_results.value),max_total_results:Number(f.elements.max_total_results.value),max_pending:Number(f.elements.max_pending.value),max_credits:Number(f.elements.max_credits.value),allow_unknown_cost:f.elements.allow_unknown_cost.checked});await read();toast('Límites Flow guardados.');}catch(e){toast(e.message);}};
  panel.addEventListener('click',async e=>{const b=e.target.closest('[data-flow-cancel]');if(!b)return;try{const r=await rpc('flowCancel',{request_id:b.dataset.flowCancel}),job=state.jobs.find(j=>(j.remoteId||j.id)===r.id);if(job){parseResponse(job,r);await flush();}await read();}catch(e){toast(e.message);}});
  $('#flow-control-export').onclick=()=>{if(!snapshot)return;const url=URL.createObjectURL(new Blob([JSON.stringify({version:1,...snapshot},null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='nexus-flow-telemetria.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
  await read();if(!panel?.open||!snapshot)return;const f=$('#flow-control-policy');for(const k of ['max_results','max_total_results','max_pending','max_credits'])f.elements[k].value=snapshot.policy[k];f.elements.allow_unknown_cost.checked=snapshot.policy.allow_unknown_cost;$('#flow-control-close').focus();
 }
 const style=document.createElement('link');style.rel='stylesheet';style.href='studio-flow-control.css';document.head.append(style);
 new MutationObserver(mount).observe($('#header-actions'),{childList:true});setInterval(()=>{mount();if(panel?.open&&!document.hidden)read();},3000);
 window.NexusFlowControl={open,read,snapshot:()=>snapshot};mount();reconcile();
})();
