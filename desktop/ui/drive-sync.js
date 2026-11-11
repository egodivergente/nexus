'use strict';
(() => {
 const labels = {idle:'Sin conectar',ready:'Conectado',queued:'Preparando sincronización…',syncing:'Sincronizando…',synced:'Al día',partial:'Sincronización incompleta',conflicts:'Hay versiones para revisar',waiting:'Esperando a que termine el turno',retry:'Pendiente de reintento',authorization_required:'Vuelve a conectar Google',unavailable:'No disponible en el laboratorio'};
 function narrativeSummary(state={}) {
  if(state.storyEnabled!==true)return {text:'Guion y storyboard pausados',detail:'El contenido local se conserva.',tone:'neutral'};
  const value=state.narrativeSync;
  if(!value)return {text:'Guion pendiente de sincronizar',detail:'Este intercambio todavía no confirma el guion y sus vínculos de material.',tone:'neutral'};
  if(value.supported!==true)return {text:'Guion sin conexión compatible',detail:'Esta conexión todavía no permite compartir la estructura del guion.',tone:'warning'};
  if(value.enabled===false)return {text:'Guion pendiente de sincronizar',detail:'Se incluirá en el próximo intercambio.',tone:'neutral'};
  const conflicts=Number.isInteger(value.conflicts)?Math.max(0,value.conflicts):0;
  const pending=Number.isInteger(value.pendingDependencies)?Math.max(0,value.pendingDependencies):0;
  if(conflicts||value.state==='conflicts')return {text:'Guion con versiones para revisar',detail:conflicts?`${conflicts} ${conflicts===1?'elemento conserva versiones distintas.':'elementos conservan versiones distintas.'}`:'Compara las versiones antes de elegir.',tone:'warning'};
  if(pending)return {text:'Guion con vínculos pendientes',detail:`${pending} ${pending===1?'asociación necesita':'asociaciones necesitan'} recuperar su contexto o material.`,tone:'warning'};
  if(value.state==='synced')return {text:'Guion compartido en Drive',detail:'El otro dispositivo necesita una versión compatible y completar su sincronización.',tone:'success'};
  if(value.state==='syncing')return {text:'Compartiendo guion y storyboard…',detail:'',tone:'neutral'};
  if(['error','retry'].includes(value.state))return {text:'Guion pendiente de reintento',detail:value.reason||'Tus cambios locales se conservan.',tone:'warning'};
  if(value.state==='partial')return {text:'Guion recibido parcialmente',detail:value.reason||'Hay información pendiente de recuperar.',tone:'warning'};
  return {text:'Guion pendiente de sincronizar',detail:value.reason||'',tone:'neutral'};
 }
 const editorial=value=>value?.payload&&typeof value.payload==='object'?{...value.payload,id:value.payload.id||value.id,projectId:value.payload.projectId||value.projectId}:value;
 const conflictKind=key=>String(key).startsWith('story:')?String(key).split(':')[1]:String(key).split(':')[0];
 function conflictName(value,lookup=()=>null) {const row=editorial(value);return row?.name||row?.displayName||row?.title||row?.document?.project?.name||row?.text?.replace(/\s+/g,' ').slice(0,55)||(row?.assetId&&[lookup(row.assetId),lookup(row.targetId)].filter(Boolean).join(' → '))||lookup(row?.id);}
 function conflictFields(value,lookup=()=>null) {
  const row=editorial(value);if(!row)return [];
  const names={title:'Título',name:'Nombre',description:'Acción y descripción',text:'Texto',speaker:'Personaje que habla',intent:'Intención narrativa',framing:'Encuadre',camera:'Cámara',performance:'Actuación',dialogue:'Diálogo',sound:'Sonido',note:'Nota',notes:'Notas',treatment:'Tratamiento',format:'Formato'};
  const fields=Object.entries(names).filter(([key])=>typeof row[key]==='string'&&row[key].trim()).map(([key,label])=>[label,row[key]]);
  if(typeof row.selected==='boolean')fields.push(['Material elegido',row.selected?'Sí':'No']);
  if(typeof row.enabled==='boolean')fields.push(['Indicación activa',row.enabled?'Sí':'No']);
  const narrative={idea:'Idea por explorar',story:'Incorporado',incorporated:'Incorporado'}[row.narrativeState||row.narrative_state];if(narrative)fields.push(['Lugar en la historia',narrative]);
  const production={pending:'Pendiente',prepared:'Preparado',generated:'Generado',chosen:'Elegido',pending_resume:'Pendiente de reanudación',complete:'Terminado',cancelled:'Cancelado',error:'Error'}[row.productionState||row.production_state||row.status];if(production)fields.push(['Estado de trabajo',production]);
  const role={identity:'Identidad',costume:'Vestuario',location:'Escenario',composition:'Encuadre',lighting:'Luz',sketch:'Boceto',generated:'Imagen generada',candidate:'Toma candidata',chosen:'Toma elegida',discarded:'Descartado',reference:'Referencia',audio:'Audio',output:'Resultado',final:'Material final',source:'Material de origen'}[row.role];if(role)fields.push(['Uso del material',role]);
  const scope={project:'Proyecto',scene:'Escena',shot:'Plano',character:'Personaje',element:'Elemento de continuidad',block:'Fragmento de guion'}[row.scope];if(scope)fields.push(['Alcance',scope]);
  const behavior={complement:'Complementa las referencias generales',replace:'Sustituye las referencias generales'}[row.behavior||row.mode];if(behavior)fields.push(['Referencias',behavior]);
  if(Number.isInteger(row.order))fields.push(['Posición',String(row.order+1)]);
  if(typeof row.durationSec==='number'&&Number.isFinite(row.durationSec))fields.push(['Duración orientativa',`${row.durationSec} s`]);
  for(const[key,label,fallback]of [['assetId','Archivo','Archivo aún no recibido'],['targetId','Destino','Contexto aún no recibido'],['characterId','Personaje','Personaje aún no recibido'],['sceneId','Escena','Escena aún no recibida']])if(row[key])fields.push([label,lookup(row[key])||fallback]);
  for(const[key,label,fallback]of [['characterIds','Personajes','Personaje'],['locationIds','Escenarios','Escenario']])if(Array.isArray(row[key]))fields.push([label,row[key].map(id=>lookup(id)||fallback).join(', ')||'Ninguno']);
  return fields;
 }
 if(typeof module==='object'&&module.exports){module.exports={narrativeSummary,editorial,conflictKind,conflictName,conflictFields};return;}
 const previous = renderSettings;
 const text = (node,value) => { if (node && node.textContent !== value) node.textContent = value; };
 let conflictHost, conflictRevision = '', conflictLimit = 20, lastState;
 const knownName=id=>{if(typeof Agent==='undefined'||!id)return null;const index=Agent.snapshot?.index||{};const all=[...(index.projects||[]),...(index.scenes||[]),...(index.shots||[]),...(index.characters||[]),...(index.assets||[]),...(index.story?.elements||[]),...(index.story?.blocks||[])];const row=all.find(row=>row.id===id);return row?.name||row?.displayName||row?.title||row?.text?.replace(/\s+/g,' ').slice(0,55)||row?.code||null;};
 const versionName = value=>conflictName(value,knownName);
 function paintConflicts(state, force = false) {
  const conflicts = document.querySelector('#nexus-drive-sync [data-sync-conflicts]');if (!conflicts) return;
  lastState = state;
  conflicts.querySelectorAll('[data-sync=resolve]').forEach(button => { button.disabled = !!state.busy || String(button.dataset.key).startsWith('story:')&&state.storyEnabled!==true; });
  const rows = state.conflicts || [];
  const signature = JSON.stringify(rows.map(c=>[c.key,(c.versions || []).map(v=>[v.id,versionName(v.value)])]));
  if (!force && conflicts === conflictHost && conflictRevision === signature) return;
  conflictHost = conflicts;conflictRevision = signature;
  conflicts.replaceChildren();
  if (!rows.length) return;
  const count = Math.min(conflictLimit,rows.length), summary = document.createElement('p');
  summary.dataset.syncConflictSummary = '';summary.textContent = `${count} de ${rows.length} elementos con versiones para revisar`;
  conflicts.append(summary);
  for (const conflict of rows.slice(0,count)) {
   const group = document.createElement('div'), title = document.createElement('p');group.dataset.syncConflict = '';
   const name = (conflict.versions || []).map(v=>versionName(v.value)).find(Boolean);
   const type = {assets:'Archivo',generations:'Generación',projects:'Proyecto',scenes:'Escena',shots:'Plano',characters:'Personaje',conversations:'Chat',montages:'Montaje',blocks:'Fragmento de guion',links:'Vínculo de material',elements:'Continuidad',preferences:'Indicación',proposals:'Propuesta',tasks:'Tarea',history:'Cambio',receipts:'Operación',adopted:'Referencias heredadas'}[conflictKind(conflict.key)] || 'Elemento';
   title.textContent = name ? `${type}: ${name}` : `${type} · versiones conservadas`;
   group.append(title);
   for (const version of conflict.versions || []) {
    if(String(conflict.key).startsWith('story:')) {
     const detail=document.createElement('div');detail.className='drive-narrative-version';
     const fields=conflictFields(version.value,knownName);
     for(const [label,value]of fields){const row=document.createElement('p');row.style.whiteSpace='pre-wrap';const title=document.createElement('strong');title.textContent=label+': ';row.append(title,document.createTextNode(value));detail.append(row);}
     if(!version.value){const removed=document.createElement('p');removed.textContent='Esta versión retira el elemento. El archivo físico se conserva.';detail.append(removed);}
     if(!fields.length&&version.value){const hidden=document.createElement('p');hidden.textContent='Las versiones difieren en relaciones o información interna. Sus datos se conservan.';detail.append(hidden);}
     group.append(detail);
    }
    const button = document.createElement('button');
    const when = new Date(version.at);
    button.textContent = (versionName(version.value) || (version.value ? 'Conservar esta versión' : 'Confirmar retirada')) + ' · ' + (version.device || 'Dispositivo') + (Number.isFinite(when.valueOf()) ? ' · ' + when.toLocaleString() : '');
    button.dataset.sync = 'resolve';button.dataset.key = conflict.key;button.dataset.revision = version.id;button.disabled = !!state.busy || String(conflict.key).startsWith('story:')&&state.storyEnabled!==true;
    group.append(button);
   }
   conflicts.append(group);
  }
  if (count < rows.length) {
   const more = document.createElement('button');more.dataset.sync = 'more';
   more.textContent = `Mostrar ${Math.min(20,rows.length-count)} más (${rows.length-count} pendientes)`;
   conflicts.append(more);
  }
 }
 function paint(state) {
  const section = document.querySelector('#nexus-drive-sync');
  if (!section) return;
  text(section.querySelector('[data-sync-status]'),state.connected && state.state === 'idle' ? 'Conectado' : labels[state.state] || 'Pendiente');
  text(section.querySelector('[data-sync-message]'),state.message || (state.missingMedia ? `${state.missingMedia} archivos necesitan recuperar su original.` : 'Proyectos, galería y chats compartidos con Nexus Agent.'));
  const narrative=narrativeSummary(state);
  text(section.querySelector('[data-sync-narrative-status]'),narrative.text);
  text(section.querySelector('[data-sync-narrative-detail]'),narrative.detail);
  const narrativeStatus=section.querySelector('[data-sync-narrative-status]');if(narrativeStatus)narrativeStatus.dataset.tone=narrative.tone;
  const storyToggle=section.querySelector('[data-sync=story-enabled]');if(storyToggle){storyToggle.checked=state.storyEnabled===true;storyToggle.disabled=!!state.busy;}
  section.querySelector('[data-sync=connect]').hidden = !state.configured || (state.connected && state.state !== 'authorization_required');
  section.querySelector('[data-sync=run]').hidden = !state.connected || state.state === 'authorization_required';
  section.querySelector('[data-sync=run]').disabled = !!state.busy;
  section.querySelector('[data-sync=disconnect]').hidden = !state.connected;
  section.querySelector('[data-sync=disconnect]').disabled = !!state.busy;
  section.querySelector('[data-sync=enabled]').checked = !!state.enabled;
  // Keep keyboard focus stable while an unchanged conflict is being reviewed.
  paintConflicts(state);
 }
 async function refresh() {
  const state = await rpc('driveSync', {op:'status'});
  paint(state);
  return state;
 }
 renderSettings = function () {
  previous();
  const section = document.createElement('section');
  section.id = 'nexus-drive-sync';section.className = 'sheet';
  section.innerHTML = '<h3>Conexión de Google Drive</h3><p data-sync-status role="status">Consultando…</p><p class="hint" data-sync-message></p><div class="row"><button data-sync="configure">Importar configuración de Google</button><button data-sync="connect" hidden>Conectar Google Drive</button><button data-sync="run" hidden>Sincronizar ahora</button><button data-sync="disconnect" hidden>Desconectar</button></div><label><input type="checkbox" data-sync="enabled"> Sincronizar automáticamente</label><label><input type="checkbox" data-sync="story-enabled"> Guion, storyboard y vínculos de material</label><p class="hint">Actívalo después de actualizar tus dispositivos.</p><p class="continuity-status" data-sync-narrative-status role="status"></p><p class="hint" data-sync-narrative-detail></p><div data-sync-conflicts></div>';
  document.querySelector('#settings-container')?.append(section);
  // Device continuity owns one visibility-aware scheduler for both channels.
  queueMicrotask(() => { if (!window.NexusContinuity) refresh().catch(() => {}); });
 };
 document.addEventListener('click', async event => {
  const button = event.target.closest('button[data-sync]');
  if (!button || button.disabled) return;
  if (button.dataset.sync === 'more') { conflictLimit += 20;paintConflicts(lastState,true);return; }
  button.disabled = true;
  try {
   const op = button.dataset.sync;let args = {};
   const authorizationWindow = op === 'connect' ? window.open('about:blank','_blank') : null;
   if (op === 'configure') { const files = await rpc('pick');if (files.length !== 1) return;args.id = files[0].id; }
   if (op === 'resolve') args = {key:button.dataset.key,revision:button.dataset.revision};
   let result;
   try { result = await rpc('driveSync', {op,...args}); } catch (error) { authorizationWindow?.close();throw error; }
   if (result.url) {
    if (authorizationWindow) { authorizationWindow.opener = null;authorizationWindow.location.replace(result.url); }
    else {
     const link = document.createElement('a');link.href = result.url;link.target = '_blank';link.rel = 'noopener noreferrer';link.textContent = 'Continuar en Google';button.parentElement.append(link);
    }
   }
  } catch (error) { toast(error.message); }
  finally { button.disabled = false;if (window.NexusContinuity) await window.NexusContinuity.refresh(true);else await refresh(); }
 });
 document.addEventListener('change', async event => {
  if (!event.target.matches('[data-sync=enabled],[data-sync=story-enabled]')) return;
  event.target.disabled = true;
  try { await rpc('driveSync', {op:event.target.dataset.sync==='story-enabled'?'story':'enabled',enabled:event.target.checked}); }
  catch (error) { toast(error.message); }
  finally { event.target.disabled = false; }
  if (window.NexusContinuity) await window.NexusContinuity.refresh(true);else await refresh();
 });
 window.NexusDriveSync = {refresh,paint,labels,narrativeSummary};
})();
