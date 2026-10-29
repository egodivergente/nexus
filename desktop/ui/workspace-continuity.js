'use strict';
(() => {
 let rows = [], limit = 20, lastBackup = null, loading = null;
 const call = args => rpc('workspaceContinuity',args);
 const date = value => new Intl.DateTimeFormat('es',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value));
 function header() {
  const actions = document.querySelector('#header-actions');if (!actions || !['canvas','assistant','editor'].includes(view)) return;
  if (!actions.querySelector('#workspace-save')) actions.insertAdjacentHTML('beforeend','<button id="workspace-save" type="button" data-workspace-save title="Guardar este punto de trabajo para continuar después">Guardar trabajo</button>');
 }
 function payload() {
  const draft = state.draft || {}, projectId = typeof Agent !== 'undefined' ? Agent.snapshot?.projectId || null : null;
  const project = typeof Agent !== 'undefined' ? Agent.snapshot?.index?.projects?.find(p=>p.id===projectId) : null;
  const selected = loaded || models.find(m=>m.id===draft.modelId);
  const editor = typeof Corte !== 'undefined' ? {projectId:Corte.projectId || null,playheadUs:Corte.playhead || 0,zoom:Corte.zoom || 0} : {};
  const library = window.NexusGallery?.workspace ? structuredClone(window.NexusGallery.workspace) : {};
  return {op:'capture',projectId,title:project?.name || 'Trabajo sin proyecto',shared:true,
   view:['canvas','assistant','editor','library'].includes(view) ? view : 'canvas',editor,library,
   canvas:{modelId:draft.modelId || '',providerId:selected?.providerId || '',prompt:draft.prompt || '',values:structuredClone(draft.values || {}),
    references:(draft.media || []).map(media=>({assetId:media.assetId || null,url:media.url,name:media.name || 'Referencia',mime:media.mime || '',field:media.field || ''}))}};
 }
 async function capture() {
  if (busy || typeof Agent !== 'undefined' && Agent.busy || typeof Corte !== 'undefined' && Corte.busy) throw Error('Espera a que termine la operación actual para guardar el punto de trabajo.');
  await flush();
  const result = await call(payload());
  toast(result.snapshot.missingReferences ? 'Trabajo guardado. Algunas referencias necesitan su original.' : 'Trabajo guardado. Puedes retomarlo desde Dispositivos.');
  await refresh();
  window.dispatchEvent(new CustomEvent('nexus:workspace-saved',{detail:result.snapshot}));
  return result;
 }
 function paint() {
  const list = document.querySelector('[data-workspace-list]');if (!list) return;
  list.replaceChildren();
  if (!rows.length) {
   const empty = document.createElement('p');empty.className = 'hint';empty.textContent = 'Guarda un punto de trabajo con su proyecto, modelo, parámetros y referencias. Al retomarlo revisarás el borrador antes de generar.';list.append(empty);return;
  }
  for (const row of rows.slice(0,limit)) {
   const article = document.createElement('article');article.className = 'workspace-resume-row';article.setAttribute('role','listitem');
   const info = document.createElement('div'), title = document.createElement('strong'), detail = document.createElement('p');
   title.textContent = row.title;detail.className = 'workspace-resume-detail';
   detail.textContent = `${row.deviceName || 'Dispositivo'} · ${date(row.createdAt)}${row.referenceCount ? ' · '+row.referenceCount+' referencias' : ''}${row.missingReferences ? ' · '+row.missingReferences+' pendientes' : ''}`;
   info.append(title,detail);
   const button = document.createElement('button');button.type = 'button';button.dataset.workspaceOpen = row.id;button.textContent = 'Retomar';
   const actions=document.createElement('div');actions.className='workspace-resume-actions';actions.append(button);
   if(row.modelId){const phone=document.createElement('button');phone.type='button';phone.dataset.workspacePhone=row.id;phone.textContent='Continuar en móvil';actions.append(phone);}
   article.append(info,actions);list.append(article);
  }
  if (rows.length > limit) {
   const more = document.createElement('button');more.type = 'button';more.dataset.workspaceMore = '';more.textContent = `Mostrar ${Math.min(20,rows.length-limit)} más`;list.append(more);
  }
  const undo = document.querySelector('[data-workspace-undo]');if (undo) undo.hidden = !lastBackup;
 }
 async function refresh() {
  if (loading) return loading;
  loading = call({op:'list'}).then(result=>{rows=result.snapshots || [];paint();return result;}).finally(()=>{loading=null;});
  return loading;
 }
 async function apply(result) {
  if (busy || typeof Agent !== 'undefined' && Agent.busy || typeof Corte !== 'undefined' && Corte.busy) throw Error('Termina la operación actual antes de retomar otro trabajo.');
  const modelId = result.draft?.modelId;
  const model = modelId ? models.find(m=>m.id===modelId && (!result.snapshot?.providerId || m.providerId===result.snapshot.providerId)) : null;
  if (modelId && !model) throw Error('El modelo guardado no está disponible en este catálogo. El trabajo se conserva.');
  if (result.projectId && !result.projectAvailable) throw Error('Este proyecto todavía no se ha sincronizado. El trabajo se conserva para retomarlo después.');
  if (Object.hasOwn(result,'projectId') && typeof agentCall === 'function') await agentCall('project',{id:result.projectId || ''});
  if (result.draft) {state.draft=structuredClone(result.draft);state.draft.media=(state.draft.media || []).map(media=>({...media,remoteUrl:null}));quote=null;}
  if (modelId) await openModel(modelId,false);
  else {loaded=null;quote=null;persist();}
  const target = result.view || 'canvas';
  if (target === 'editor' && result.editor?.projectId && typeof corteOpen === 'function') {
   await corteOpen(result.editor.projectId);
   if (typeof Corte !== 'undefined') {Corte.playhead=result.editor.playheadUs || 0;Corte.zoom=result.editor.zoom || Corte.zoom;}
   navigate('editor');
  } else if (target === 'library') {
   navigate('library');
   if (window.NexusGallery?.restoreWorkspace) {await window.NexusGallery.refresh?.();await window.NexusGallery.restoreWorkspace(result.library || {});}
  } else navigate(target === 'assistant' ? 'assistant' : 'canvas');
  await flush();
  if (result.preservedLocalDraft) lastBackup=result.preservedLocalDraft;
  toast(result.missingReferences?.length ? 'Trabajo retomado. Faltan referencias; añádelas antes de generar.' : 'Trabajo retomado. Revisa el borrador antes de generar.');
  return result;
 }
 async function open(id) {
  await flush();
  const context={projectId:typeof Agent!=='undefined'?Agent.snapshot?.projectId || null:null,view:['canvas','assistant','editor','library'].includes(view)?view:'canvas'};
  return apply(await call({op:'open',id,context}));
 }
 async function phones(id){
  const result=await call({op:'devices'});
  const devices=result.devices || [];
  modal('<h2>Continuar en móvil</h2><p>El puente de Nexus Agent prepara el modelo, prompt y parámetros sin generar. El proyecto y las referencias permanecen en el trabajo guardado; el móvil actual todavía no los aplica con este puente.</p><div class="workspace-resume-actions">'+devices.map(device=>'<button type="button" data-workspace-handoff="'+escape(id)+'" data-workspace-serial="'+escape(device.serial)+'">Preparar en '+escape(device.name)+'</button>').join('')+'</div>'+(!devices.length?'<p>No hay un S25 Ultra o Pixel 8 Pro accesible por ADB en este momento.</p>':''));
 }
 async function handoff(id,serial,launch){
  const result=await call({op:'handoff',id,serial,launch});
  if(launch){toast('Borrador abierto en '+result.name+'. Revisa proyecto, referencias y coste antes de generar.');return;}
  modal('<h2>Borrador preparado en '+escape(result.name)+'</h2><p>El móvil conserva una versión nueva del modelo, prompt y parámetros. No se ha enviado ninguna generación.</p><p>Al abrirlo cambiarás el borrador que muestra el Lienzo del móvil. El proyecto y las referencias requieren revisión allí.</p><button type="button" data-workspace-launch="'+escape(id)+'" data-workspace-serial="'+escape(serial)+'">Abrir borrador en el móvil</button>');
 }
 const previousSettings=renderSettings;
 renderSettings=function() {
  previousSettings();
  const hub=document.querySelector('#continuity-hub');if (!hub) return;
  const section=document.createElement('section');section.className='workspace-resume';
  section.innerHTML='<div class="workspace-resume-heading"><div><h3>Retoma tu trabajo</h3><p class="hint">Versiones guardadas por dispositivo. Recibir un trabajo nunca sustituye tu borrador.</p></div><button type="button" data-workspace-save>Guardar trabajo actual</button></div><div data-workspace-list class="workspace-resume-list" role="list"><p class="hint">Consultando trabajos guardados…</p></div><div class="workspace-resume-actions"><button type="button" data-workspace-refresh>Actualizar trabajos</button><button type="button" data-workspace-undo hidden>Volver al borrador anterior</button></div>';
  hub.append(section);refresh().catch(error=>{const node=section.querySelector('[data-workspace-list]');if(node)node.textContent=error.message;});
 };
 const previousRender=render;render=function(){previousRender();header();};
 document.addEventListener('click',async event=>{
  const button=event.target.closest('[data-workspace-save],[data-workspace-open],[data-workspace-refresh],[data-workspace-more],[data-workspace-undo],[data-workspace-phone],[data-workspace-handoff],[data-workspace-launch]');
  if (!button || button.disabled) return;
  if (button.hasAttribute('data-workspace-more')) {limit+=20;paint();return;}
  button.disabled=true;
  try {
   if (button.hasAttribute('data-workspace-save')) await capture();
   else if (button.dataset.workspaceOpen) await open(button.dataset.workspaceOpen);
   else if (button.dataset.workspacePhone) await phones(button.dataset.workspacePhone);
   else if (button.dataset.workspaceHandoff) await handoff(button.dataset.workspaceHandoff,button.dataset.workspaceSerial,false);
   else if (button.dataset.workspaceLaunch) await handoff(button.dataset.workspaceLaunch,button.dataset.workspaceSerial,true);
   else if (button.hasAttribute('data-workspace-undo')) {await flush();await apply(await call({op:'restoreBackup',id:lastBackup}));lastBackup=null;}
   else await refresh();
  } catch(error) {toast(error.message);} finally {button.disabled=false;}
 });
 window.NexusWorkspaceContinuity={capture,open,refresh,apply};
})();
