'use strict';
(() => {
 const labels = {idle:'Sin conectar',ready:'Conectado',queued:'Preparando sincronización…',syncing:'Sincronizando…',synced:'Al día',partial:'Faltan originales por recuperar',conflicts:'Hay versiones para revisar',waiting:'Esperando a que termine el turno',retry:'Pendiente de reintento',authorization_required:'Vuelve a conectar Google',unavailable:'No disponible en el laboratorio'};
 const previous = renderSettings;
 const text = (node,value) => { if (node && node.textContent !== value) node.textContent = value; };
 let conflictHost, conflictRevision = '', conflictLimit = 20, lastState;
 const versionName = value => value?.name || value?.displayName || value?.title || value?.document?.project?.name;
 function paintConflicts(state, force = false) {
  const conflicts = document.querySelector('#nexus-drive-sync [data-sync-conflicts]');if (!conflicts) return;
  lastState = state;
  conflicts.querySelectorAll('[data-sync=resolve]').forEach(button => { button.disabled = !!state.busy; });
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
   const type = {assets:'Archivo',generations:'Generación',projects:'Proyecto',scenes:'Escena',shots:'Plano',characters:'Personaje',conversations:'Chat',montages:'Montaje'}[String(conflict.key).split(':')[0]] || 'Elemento';
   title.textContent = name ? `${type}: ${name}` : `${type} · versiones conservadas`;
   group.append(title);
   for (const version of conflict.versions || []) {
    const button = document.createElement('button');
    const when = new Date(version.at);
    button.textContent = (versionName(version.value) || (version.value ? 'Conservar esta versión' : 'Confirmar retirada')) + ' · ' + (version.device || 'Dispositivo') + (Number.isFinite(when.valueOf()) ? ' · ' + when.toLocaleString() : '');
    button.dataset.sync = 'resolve';button.dataset.key = conflict.key;button.dataset.revision = version.id;button.disabled = !!state.busy;
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
  section.innerHTML = '<h3>Conexión de Google Drive</h3><p data-sync-status role="status">Consultando…</p><p class="hint" data-sync-message></p><div class="row"><button data-sync="configure">Importar configuración de Google</button><button data-sync="connect" hidden>Conectar Google Drive</button><button data-sync="run" hidden>Sincronizar ahora</button><button data-sync="disconnect" hidden>Desconectar</button></div><label><input type="checkbox" data-sync="enabled"> Sincronizar automáticamente</label><div data-sync-conflicts></div>';
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
  finally { button.disabled = false;if (window.NexusContinuity) await window.NexusContinuity.refresh();else await refresh(); }
 });
 document.addEventListener('change', async event => {
  if (!event.target.matches('[data-sync=enabled]')) return;
  event.target.disabled = true;
  try { await rpc('driveSync', {op:'enabled',enabled:event.target.checked}); }
  catch (error) { toast(error.message); }
  finally { event.target.disabled = false; }
  if (window.NexusContinuity) await window.NexusContinuity.refresh();else await refresh();
 });
 window.NexusDriveSync = {refresh,paint,labels};
})();
