'use strict';
(() => {
 const previous = renderSettings;
 let timer, refreshing, pending = false;
 const state = {drive:null,device:null};
 const writeText = (node,value) => { if (node && node.textContent !== value) node.textContent = value; };
 const fields = [['skills','Skills personales y sus archivos'],['chatAttachments','Adjuntos en el historial de chats'],['credentials','Claves API y puentes'],['settings','Ajustes y perfiles compatibles'],['agy','Sesión AGY'],['deletions','Propagar eliminaciones sin cambios locales']];
 const date = value => {
  if (!Number.isFinite(value) || value <= 0 || !Number.isFinite(new Date(value).valueOf())) return 'Aún no se ha completado un intercambio';
  return 'Último intercambio: ' + new Intl.DateTimeFormat('es', {dateStyle:'medium',timeStyle:'short'}).format(new Date(value));
 };
 function channel(id, title, description) {
  const article = document.createElement('article');article.className = 'continuity-channel';article.dataset.channel = id;
  const heading = document.createElement('h3');heading.textContent = title;
  const detail = document.createElement('p');detail.className = 'continuity-detail';detail.textContent = description;
  const status = document.createElement('p');status.className = 'continuity-status';status.dataset.channelStatus = '';status.setAttribute('role','status');status.textContent = 'Consultando…';
  const last = document.createElement('small');last.dataset.channelLast = '';
  article.append(heading,detail,status,last);
  return article;
 }
 function updateHub() {
  const hub = document.querySelector('#continuity-hub');if (!hub) return;
  for (const id of ['drive','device']) {
   const row = hub.querySelector(`[data-channel=${id}]`), value = state[id];
   const status = row.querySelector('[data-channel-status]');
   status.dataset.tone = 'neutral';
   if (!value) { writeText(status,'No se pudo consultar la conexión');status.dataset.tone = 'warning';continue; }
   let text;
   if (id === 'drive') {
    text = value.connected && value.state === 'idle' ? 'Conectado' : window.NexusDriveSync.labels[value.state] || 'Pendiente';
    if (!value.connected && value.state !== 'unavailable') text = 'Conecta Google Drive en tus dispositivos';
    if (['retry','partial','conflicts','authorization_required'].includes(value.state)) status.dataset.tone = 'warning';
    else if (value.state === 'synced') status.dataset.tone = 'success';
   } else {
    text = !value.configured ? 'Vincula Nexus Agent' : !value.enabled ? 'Vinculación pausada' : value.status;
    if (value.lastErrors?.length) status.dataset.tone = 'warning';
    else if (value.status?.startsWith('Sincronizado')) status.dataset.tone = 'success';
   }
   if (status.textContent !== text) status.textContent = text;
   writeText(row.querySelector('[data-channel-last]'),date(value.lastCompletedAt || value.lastSuccessAt));
  }
  const available = !!(state.drive?.connected && state.drive.state !== 'authorization_required') || !!(state.device?.configured && state.device?.enabled);
  const busy = pending || !!state.drive?.busy || !!state.device?.busy || state.device?.status === 'Intercambio pendiente';
  const button = hub.querySelector('[data-continuity=all]');
  button.disabled = !available || busy;
  writeText(button,busy ? 'Sincronizando…' : 'Sincronizar todo');
  writeText(hub.querySelector('[data-continuity-summary]'),available ? 'Continúa con los proyectos y archivos compartidos al abrir Nexus Agent en el otro dispositivo.' : 'Conecta la misma cuenta de Google Drive en Nexus y Nexus Agent para compartir proyectos, galería y chats.');
  window.dispatchEvent(new CustomEvent('nexus:continuity-status', {detail:{...state,busy,available}}));
 }
 function schedule() {
  clearTimeout(timer);
  if (!document.hidden && document.querySelector('#continuity-hub')) timer = setTimeout(refresh, 15000);
 }
 async function refresh() {
  if (refreshing) return refreshing;
  refreshing = (async () => {
   const replies = await Promise.allSettled([rpc('driveSync',{op:'status'}),rpc('deviceContinuity',{op:'status'})]);
   state.drive = replies[0].status === 'fulfilled' ? replies[0].value : null;
   state.device = replies[1].status === 'fulfilled' ? replies[1].value : null;
   if (state.drive) window.NexusDriveSync.paint(state.drive);
   const section = document.querySelector('#device-continuity');
   if (section && state.device) {
    writeText(section.querySelector('[data-continuity-status]'),state.device.status);
    if (!section.dataset.loaded) {
     for (const name of ['enabled',...fields.map(f=>f[0])]) section.querySelector(`[name=${name}]`).checked = !!state.device[name];
     for (const name of ['address','port','interval','timeout','maxFileMb','agyDirectory']) section.querySelector(`[name=${name}]`).value = state.device[name] ?? '';
     section.querySelector('[name=peers]').value = (state.device.peers || []).join('\n');section.dataset.loaded = '1';
    }
    section.querySelector('[data-continuity=run]').disabled = !state.device.enabled || !state.device.configured || !!state.device.busy;
   }
   updateHub();
  })().finally(() => { refreshing = null;schedule(); });
  return refreshing;
 }
 renderSettings = function () {
  previous();
  const host = document.querySelector('#settings-container');if (!host) return;
  const hub = document.createElement('section');hub.id = 'continuity-hub';hub.className = 'sheet continuity-hub';
  hub.innerHTML = '<div class="continuity-heading"><div><h2 tabindex="-1">Continúa en otro dispositivo</h2><p class="continuity-summary" data-continuity-summary></p></div><button class="primary" data-continuity="all" disabled>Sincronizar todo</button></div><div class="continuity-channels"></div><p class="hint">Las versiones en conflicto se conservan para que puedas revisarlas.</p>';
  const channels = hub.querySelector('.continuity-channels');
  const drive = channel('drive','Proyectos y galería','Google Drive mantiene tus archivos, proyectos y chats disponibles entre Windows y Android.');
  const direct = channel('device','Vinculación con Nexus Agent','El enlace directo cifrado comparte los ajustes, skills y datos que elijas entre dispositivos vinculados.');
  channels.append(drive,direct);
  const googleDetails = document.createElement('details');googleDetails.className = 'continuity-advanced';googleDetails.innerHTML = '<summary>Configurar Google Drive y revisar versiones</summary>';
  const google = document.querySelector('#nexus-drive-sync');if (google) googleDetails.append(google);drive.append(googleDetails);
  const advanced = document.createElement('details');advanced.className = 'continuity-advanced';advanced.innerHTML = '<summary>Configurar vinculación directa</summary>';
  const section = document.createElement('section');section.id = 'device-continuity';
  section.innerHTML = '<h3>Vinculación directa</h3><p data-continuity-status role="status">Consultando…</p><label><input name="enabled" type="checkbox"> Sincronización automática</label><label>Direcciones de los móviles, una por línea<textarea name="peers" rows="2" placeholder="http://100.x.x.x:8795"></textarea></label><label>Direcciones de este PC accesibles desde los móviles, una por línea<textarea name="address" rows="2" placeholder="http://100.x.x.x:8797"></textarea></label><label>Clave de vinculación compartida<input name="key" type="password" autocomplete="off" placeholder="Dejar vacío para conservarla"></label><div class="row"><label>Puerto<input name="port" type="number" min="1024" max="65535"></label><label>Intervalo (segundos)<input name="interval" type="number" min="1"></label><label>Espera de red (0 sin límite)<input name="timeout" type="number" min="0"></label><label>MB por archivo (0 sin límite)<input name="maxFileMb" type="number" min="0"></label></div><label>Carpeta de sesión AGY en Windows<input name="agyDirectory"></label><fieldset><legend>Qué compartir</legend><div data-continuity-scopes></div></fieldset><div class="row"><button data-continuity="save">Guardar vinculación</button><button data-continuity="run" disabled>Sincronizar ahora</button></div>';
  for (const [name,title] of fields) {
   const label = document.createElement('label'), input = document.createElement('input');input.type = 'checkbox';input.name = name;
   label.append(input,document.createTextNode(' ' + title));section.querySelector('[data-continuity-scopes]').append(label);
  }
  advanced.append(section);direct.append(advanced);host.prepend(hub);
  refresh();
 };
 document.addEventListener('click', async event => {
  if (event.target.closest('[data-continuity-open]')) { window.NexusContinuity.open();return; }
  const button = event.target.closest('[data-continuity]');if (!button || button.disabled) return;
  button.disabled = true;
  try {
   if (button.dataset.continuity === 'all') {
    pending = true;updateHub();
    const tasks = [];
    if (state.drive?.connected && state.drive.state !== 'authorization_required' && !state.drive.busy) tasks.push(rpc('driveSync',{op:'run'}));
    if (state.device?.configured && state.device.enabled && !state.device.busy) tasks.push(rpc('deviceContinuity',{op:'run'}));
    const results = await Promise.allSettled(tasks);
    const failures = results.filter(r=>r.status === 'rejected');
    toast(failures.length ? failures.map(r=>r.reason.message).join(' · ') : 'Sincronización solicitada. Puedes seguir trabajando.');
   } else {
    const section = button.closest('#device-continuity');
    const args = {op:button.dataset.continuity === 'save' ? 'configure' : 'run'};
    if (args.op === 'configure') {
     for (const name of ['enabled',...fields.map(f=>f[0])]) args[name] = section.querySelector(`[name=${name}]`).checked;
     for (const name of ['port','interval','timeout','maxFileMb']) args[name] = Number(section.querySelector(`[name=${name}]`).value);
     for (const name of ['address','key','agyDirectory']) args[name] = section.querySelector(`[name=${name}]`).value.trim();
     args.peers = section.querySelector('[name=peers]').value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
    }
    await rpc('deviceContinuity',args);section.querySelector('[name=key]').value = '';
    if (args.op === 'configure') { delete section.dataset.loaded;toast('Vinculación guardada'); }
   }
  } catch (error) { toast(error.message); }
  finally { pending = false;button.disabled = false;await refresh(); }
 });
 document.addEventListener('visibilitychange', () => {
  clearTimeout(timer);
  if (!document.hidden && document.querySelector('#continuity-hub')) refresh();
 });
 window.addEventListener('nexus:continuity-status', event => {
  const button = document.querySelector('#continuity-shortcut');if (!button) return;
  const busy = String(!!event.detail.busy);if (button.getAttribute('aria-busy') !== busy) button.setAttribute('aria-busy',busy);
 });
 window.NexusContinuity = {
  refresh,
  get state() { return state; },
  open() {
   if (view !== 'settings') navigate('settings');
   requestAnimationFrame(() => {
    const hub = document.querySelector('#continuity-hub');
    hub?.scrollIntoView({block:'start',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'});
    hub?.querySelector('h2')?.focus({preventScroll:true});
   });
  }
 };
})();
