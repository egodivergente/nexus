'use strict';
// Local studio navigation and presentation. Provider dispatch stays in the existing flow.
(()=>{
 const galleryProviders={...providers,local_media:'Archivos locales'};
 const G={collection:'',favorites:false,sort:'recent',limit:36,selected:new Map(),viewer:null,observer:null,flowStatus:null,flowSince:0,previewObserver:null,previewQueue:[],previewActive:0,activeVideo:null};
 const prefs=()=>state.studioGallery||(state.studioGallery={favorites:[],collections:[],members:{},hidden:[],size:240});
 const isExpiringUrl=u=>typeof u==='string'&&(/X-Tos-Expires=|Expires=/i.test(u));
 const outputUrls=j=>(j.outputs||[]).map(o=>typeof o==='string'?o:o?.url);
 const outputIndex=(j,requested)=>{
  const urls=outputUrls(j),selected=requested??(outputSelection.has(j.id)?outputSelection.get(j.id):null);
  if(selected!==null)return Math.max(0,Math.min(Number(selected)||0,Math.max(0,urls.length-1)));
  if(!urls[0]){const first=urls.findIndex(url=>typeof url==='string'&&url);if(first>=0)return first;}
  if(urls.length>1&&isExpiringUrl(urls[0])){const better=urls.findIndex(url=>typeof url==='string'&&(url.startsWith('/media/')||!isExpiringUrl(url)));if(better>=0)return better;}
  return 0;
 };
 const media=(j,index=outputIndex(j))=>outputUrls(j)[index];
 const previewUrl=(j,index=outputIndex(j),original=false)=>'/gallery-preview/'+encodeURIComponent(j.id)+'?index='+index+(original?'&original=1':'');
 const previewable=j=>['IMAGE','VIDEO'].includes(j.modality)&&!active(j)&&(j.status==='COMPLETED'||j.outputs?.length);
 const name=j=>j.name||models.find(m=>m.id===j.modelId)?.displayName||j.modelId;
 const entries=()=>pcJobs().filter(j=>!prefs().hidden.includes(j.id)&&(!G.favorites||prefs().favorites.includes(j.id))&&(!G.collection||prefs().members[j.id]===G.collection)).sort((a,b)=>G.sort==='name'?name(a).localeCompare(name(b),'es'):(Number(b.startedAt)||0)-(Number(a.startedAt)||0));
 const button=(action,label,extra='')=>`<button type="button" data-gallery="${action}" ${extra}>${label}</button>`;
 const status=j=>typeof readableJobStatus==='function'?readableJobStatus(j):j.status;
 const active=j=>!['COMPLETED','FAILED','CANCELLED'].includes(j.status);
 function progress(j){
  const measured=j.providerId!=='google_flow'&&typeof j.progress==='number'&&Number.isFinite(j.progress)&&j.progress>=0&&j.progress<=1?j.progress:null;
  const elapsed=Math.max(0,(Date.now()-Number(j.startedAt||Date.now()))/1000);
  const samples=state.jobs.filter(x=>x.status==='COMPLETED'&&x.providerId===j.providerId&&x.modelId===j.modelId&&Number(x.completedAt)>Number(x.startedAt)).sort((a,b)=>b.startedAt-a.startedAt).slice(0,20).map(x=>(x.completedAt-x.startedAt)/1000).sort((a,b)=>a-b);
  const typical=samples.length>=3?samples[Math.floor(samples.length/2)]:null, remaining=typical===null?null:Math.max(0,Math.round(typical-elapsed));
  const duration=n=>Math.floor(n/60)+' min '+Math.floor(n%60)+' s';
  return `<strong>${escape(status(j))}</strong><progress max="1" ${measured===null?'':`value="${measured}"`} aria-label="Progreso de generación"></progress><span>${measured===null?'En curso':Math.round(measured*100)+'% completado · '+Math.round(100-measured*100)+'% pendiente'}</span><small>${duration(elapsed)} transcurridos</small><small>${remaining===null?'Tiempo restante no disponible':remaining?'Restante ≈ '+duration(remaining)+' (estimado)':'Superando el tiempo habitual'}</small>`;
 }
 const pendingMedia=j=>`<div class="gallery-progress" data-job-progress="${escape(j.id)}">${progress(j)}</div>`;
 const viewerEntries=()=>entries().filter(j=>j.status==='COMPLETED'&&(state.jobs.find(x=>x.id===G.viewer?.id)?.modality!=='IMAGE'||j.modality==='IMAGE')).flatMap(j=>(j.outputs||[]).map((o,index)=>({id:j.id,index})).filter(x=>isMediaUrl(media(j,x.index))));
 function card(j){
  const u=media(j),valid=isMediaUrl(u),favorite=prefs().favorites.includes(j.id),selected=G.selected.has(j.id),duration=j.parameters?.duration;
  const content=active(j)?pendingMedia(j):j.modality==='CHAT'?`<p>${escape(j.prompt||'Documento')}</p>`:previewable(j)?`<img class="gallery-thumbnail" data-gallery-preview="thumbnail" data-gallery-src="${escape(previewUrl(j))}" alt="${escape(name(j))}" decoding="async"><span class="gallery-preview-state" data-gallery-preview-state role="status"><span class="loading" aria-hidden="true"></span><small>Preparando vista previa…</small></span>${j.modality==='VIDEO'?`<video class="gallery-hover-video" data-gallery-preview="video" data-gallery-original="${escape(previewUrl(j,outputIndex(j),true))}" muted playsinline loop preload="none" aria-hidden="true"></video>`:''}`:valid&&j.modality==='AUDIO'?`<audio src="${escape(u)}" controls preload="none"></audio>`:`<div class="gallery-placeholder"><span>${escape(status(j))}</span></div>`;
  return `<article class="gallery-piece result ${selected?'is-selected':''}" data-job="${escape(j.id)}"><div class="gallery-media" ${j.modality!=='AUDIO'?'tabindex="0" role="button"':''} aria-label="Abrir ${escape(name(j))}">${content}</div><div class="gallery-badges"><span>${j.modality==='VIDEO'?'▷'+(duration?' '+escape(duration)+' s':''):j.modality==='AUDIO'?'Audio':j.modality==='CHAT'?'Texto':''}</span>${j.status!=='COMPLETED'||j.uncertain||j.pollError?`<span class="gallery-status ${j.status==='FAILED'?'warning':''}">${escape(status(j))}</span>`:''}</div><button class="gallery-select" data-gallery-select="${escape(j.id)}" aria-label="Seleccionar ${escape(name(j))}" aria-pressed="${selected}">${selected?'✓':''}</button><div class="gallery-overlay"><div><strong>${escape(name(j))}</strong><small>${escape(galleryProviders[j.providerId]||j.providerId)}${j.parameters?.aspect?' · '+escape(j.parameters.aspect):''}</small></div><button data-gallery-favorite="${escape(j.id)}" aria-label="${favorite?'Quitar de favoritos':'Favorito'}" aria-pressed="${favorite}">${favorite?'★':'☆'}</button></div><button class="gallery-open sr-only" data-studio-detail="${escape(j.id)}">Abrir ${escape(name(j))}</button></article>`;
 }
 function toolbar(jobs){return `<div class="gallery-tools"><div class="tabs studio-tabs"><button data-studio-tab="results" class="active">Resultados</button><button data-studio-tab="activity">Actividad</button></div><label class="gallery-search"><span aria-hidden="true">⌕</span><input id="pc-search" type="search" aria-label="Buscar creaciones" placeholder="Buscar por nombre, prompt o modelo…" value="${escape(PC.search)}"></label><div class="gallery-chips">${[['','Todas'],['IMAGE','Imagen'],['VIDEO','Vídeo'],['AUDIO','Audio'],['CHAT','Texto']].map(([k,l])=>`<button data-gallery-kind="${k}" aria-pressed="${PC.kind===k}">${l}</button>`).join('')}<button data-gallery="favorites" aria-pressed="${G.favorites}">☆ Favoritos</button></div><details class="gallery-filters"><summary>Filtros y orden</summary><div><label>Proveedor<select id="pc-provider">${pcOptions([['','Todos'],...Object.entries(galleryProviders)],PC.provider)}</select></label><label>Estado<select id="pc-status">${pcOptions([['','Todos'],['COMPLETED','Completado'],['PROCESSING','Procesando'],['QUEUED','En cola'],['FAILED','Revisar envío']],PC.status)}</select></label><label>Orden<select id="gallery-sort">${pcOptions([['recent','Recientes'],['name','Por modelo']],G.sort)}</select></label><label>Tamaño de miniaturas<input id="gallery-size" type="range" min="160" max="360" step="20" value="${prefs().size}"></label>${button('save-view','Guardar vista')}</div></details><div class="gallery-count"><span class="pc-results-count nx-meta-badge">${jobs.length} archivos${G.collection?' · '+escape(prefs().collections.find(c=>c.id===G.collection)?.name||'Colección'):''}</span>${G.collection?button('all','Ver todas')+`<button data-project-reference-collection="${escape(prefs().collections.find(c=>c.id===G.collection)?.name||'Colección')}">Archivos de referencia</button>`:''}${PC.search||PC.provider||PC.status||PC.kind||G.favorites?button('reset','Quitar filtros'):''}</div></div>`;}
 function selectionBar(){
  document.querySelector('.gallery-selection')?.remove();if(!G.selected.size)return;
  const bar=document.createElement('section');bar.className='gallery-selection';bar.setAttribute('aria-label','Acciones de selección');
  bar.innerHTML=`<strong>${G.selected.size} seleccionados</strong>${button('compare','Comparar A/B',G.selected.size<2?'disabled':'')}${button('downloads','Descargar')}${button('assign','Añadir a colección')}${button('hide','Quitar de la galería')}${button('clear','✕','aria-label="Limpiar selección"')}`;document.body.append(bar);
 }
 function stopVideo(){
  const video=G.activeVideo;if(!video)return;G.activeVideo=null;
  video.pause();video.closest('.gallery-piece')?.classList.remove('is-previewing');
  video.removeAttribute('src');video.load();
 }
 function playVideo(video){
  if(!video.isConnected||document.hidden||matchMedia('(prefers-reduced-motion: reduce)').matches||G.viewer)return;
  const card=video.closest('.gallery-piece');if(card.querySelector('.gallery-media')?.dataset.previewState!=='ready')return;
  if(G.activeVideo!==video){stopVideo();G.activeVideo=video;video.src=video.dataset.galleryOriginal;}
  video.play().then(()=>{if(G.activeVideo===video&&card.isConnected)card.classList.add('is-previewing');}).catch(()=>{if(G.activeVideo===video)stopVideo();});
 }
 function previewState(image,status){
  const media=image.closest('.gallery-media');if(!media)return;
  media.dataset.previewState=status;image.dataset.previewState=status;
  const notice=media.querySelector('[data-gallery-preview-state]');
  if(!notice)return;
  if(status==='ready'){notice.hidden=true;return;}
  notice.hidden=false;
  if(status==='failed'){
   notice.replaceChildren();
   const title=document.createElement('strong'),detail=document.createElement('small');
   title.textContent='Vista previa no disponible';detail.textContent='Abre para ver el original o reintentar.';notice.append(title,detail);
  }
 }
 function pumpPreviews(){
  // Imported/generated entries reach the server through the normal save queue.
  // Wait for that save instead of racing an unknown job against its thumbnail.
  if(document.hidden||['pending','saving'].includes(SaveStatus.status))return;
  while(G.previewActive<2&&G.previewQueue.length){
   const image=G.previewQueue.shift();if(!image.isConnected||image.dataset.previewState==='ready')continue;
   G.previewActive++;image.dataset.previewState='loading';let finished=false;
   const finish=ok=>{
    if(finished)return;finished=true;clearTimeout(timer);image.removeEventListener('load',loaded);image.removeEventListener('error',failed);
    G.previewActive--;if(image.isConnected){previewState(image,ok?'ready':'failed');if(ok){const card=image.closest('.gallery-piece');if(card?.matches(':hover')){const video=card.querySelector('video');if(video)playVideo(video);}}}
    pumpPreviews();
   };
   const loaded=()=>finish(image.naturalWidth>0),failed=()=>finish(false);
   const timer=setTimeout(()=>{finish(false);image.removeAttribute('src');},90000);
   image.addEventListener('load',loaded);image.addEventListener('error',failed);image.src=image.dataset.gallerySrc;
  }
 }
 window.addEventListener('nexus:save-status',()=>pumpPreviews());
 function watchPreviews(){
  G.previewObserver?.disconnect();
  // Browser native lazy loading can fetch several screens ahead. This queue only
  // asks for visible posters, and never decodes 36 original videos at startup.
  G.previewObserver=new IntersectionObserver(entries=>{
   for(const entry of entries){if(!entry.isIntersecting)continue;const image=entry.target;G.previewObserver.unobserve(image);
    if(image.dataset.previewQueued)continue;image.dataset.previewQueued='1';G.previewQueue.push(image);
   }
   pumpPreviews();
  },{root:document.querySelector('#content'),rootMargin:'160px'});
  document.querySelectorAll('.gallery-thumbnail:not([data-preview-queued])').forEach(image=>G.previewObserver.observe(image));
  G.previewQueue=G.previewQueue.filter(image=>image.isConnected);pumpPreviews();
 }
 function wireMedia(){
  document.querySelectorAll('.gallery-piece').forEach(card=>{
   card.dataset.nx='1';if(card.dataset.galleryWired)return;card.dataset.galleryWired='1';
   const video=card.querySelector('video');if(!video)return;
   card.addEventListener('mouseenter',()=>playVideo(video));
   card.addEventListener('mouseleave',()=>{if(G.activeVideo===video)stopVideo();});
   video.addEventListener('error',()=>{if(G.activeVideo===video)stopVideo();});
  });
  if(G.activeVideo&&!G.activeVideo.isConnected)stopVideo();watchPreviews();
 }
 function wireViewerPreview(dialog,job,index){
  const asset=dialog.querySelector('[data-gallery-preview="original"]');if(!asset)return;
  const failed=()=>{
   if(!dialog.isConnected)return;asset.hidden=true;
   dialog.querySelector('.gallery-player')?.setAttribute('hidden','');
   const downloadButton=dialog.querySelector('[data-gallery=download-original]');if(downloadButton)downloadButton.disabled=true;
   if(dialog.querySelector('[data-gallery-original-error]'))return;
   const message=document.createElement('div');message.className='gallery-original-error';message.dataset.galleryOriginalError='';message.setAttribute('role','status');
   const title=document.createElement('strong'),detail=document.createElement('p'),retry=document.createElement('button');
   title.textContent='No se pudo abrir el original.';detail.textContent='El proyecto y sus ajustes se conservan.';
   retry.type='button';retry.dataset.gallery='retry-preview';retry.textContent='Reintentar carga';message.append(title,detail,retry);dialog.querySelector('.gallery-stage').append(message);
  };
  asset.addEventListener('error',failed,{once:true});
  if(asset instanceof HTMLImageElement&&asset.complete&&!asset.naturalWidth)failed();
 }
 document.addEventListener('visibilitychange',()=>{if(document.hidden)stopVideo();else pumpPreviews();});
 function watchMore(jobs){
  G.observer?.disconnect();let sentinel=$('.gallery-sentinel');
  if(jobs.length<=G.limit){sentinel?.remove();return;}
  if(!sentinel){sentinel=document.createElement('div');sentinel.className='gallery-sentinel';sentinel.setAttribute('role','status');sentinel.textContent='Más creaciones al continuar';$('.gallery-grid').after(sentinel);}
  G.observer=new IntersectionObserver(es=>{if(es.some(e=>e.isIntersecting))appendMore();},{root:$('#content'),rootMargin:'250px'});
  G.observer.observe(sentinel);
 }
 function appendMore(){
  if(view!=='library'||Studio.tab!=='results')return;
  const grid=$('.gallery-grid');if(!grid)return;
  const jobs=entries(),existing=[...grid.children];
  // A filter or incoming job can change the ordering between observer callbacks.
  if(existing.some((node,i)=>node.dataset.job!==jobs[i]?.id)){renderGallery();return;}
  G.limit=Math.min(jobs.length,G.limit+36);PC.limit=G.limit;
  grid.insertAdjacentHTML('beforeend',jobs.slice(existing.length,G.limit).map(card).join(''));
  wireMedia();watchMore(jobs);
 }
 function renderGallery(){
  if(!['library','history'].includes(view))return;
  if(Studio.tab==='activity'){oldRenderPC();return;}
  G.observer?.disconnect();const jobs=entries();PC.limit=G.limit;
  $('#content').classList.add('studio-library','gallery-library');
  $('#content').innerHTML=`${toolbar(jobs)}<div class="gallery-grid results" style="--gallery-size:${prefs().size}px">${jobs.slice(0,G.limit).map(card).join('')}</div>${jobs.length>G.limit?'<div class="gallery-sentinel" role="status">Más creaciones al continuar</div>':''}${jobs.length?'':`<div class="empty"><h2>${state.jobs.length?'No hay coincidencias':'Tu galería empieza aquí'}</h2><p>${state.jobs.length?'Prueba otra búsqueda o quita los filtros.':'Elige un modelo y crea tu primera imagen o vídeo.'}</p>${state.jobs.length?button('reset','Quitar filtros'):'<button data-nav="canvas">Crear</button>'}</div>`}`;
  wireMedia();selectionBar();watchMore(jobs);
 }
 const oldRenderPC=renderPC;renderPC=renderGallery;renderLibrary=renderGallery;
 renderHistory=function(){Studio.tab='activity';oldRenderPC();};
 const oldRefresh=refreshJobViews;
 refreshJobViews=function(ids){
  if(view!=='library'||Studio.tab!=='results'){oldRefresh(ids);return;}
  const grid=$('.gallery-grid'),jobs=entries();if(!grid||!jobs.length){renderGallery();return;}
  const scroll=$('#content').scrollTop,visible=jobs.slice(0,G.limit),nodes=new Map([...grid.children].map(n=>[n.dataset.job,n]));
  const wanted=new Set(visible.map(j=>j.id)),changed=new Set(ids);
  for(const [id,node] of nodes)if(!wanted.has(id))node.remove();
  visible.forEach((job,i)=>{
   let node=nodes.get(job.id);
   if(!node||changed.has(job.id)){const template=document.createElement('template');template.innerHTML=card(job);const next=template.content.firstElementChild;if(node)node.replaceWith(next);node=next;}
   if(grid.children[i]!==node)grid.insertBefore(node,grid.children[i]||null);
  });
  $('.pc-results-count').textContent=jobs.length+' archivos'+(G.collection?' · '+(prefs().collections.find(c=>c.id===G.collection)?.name||'Colección'):'');
  wireMedia();watchMore(jobs);$('#content').scrollTop=scroll;
  if(G.viewer&&changed.has(G.viewer.id))showViewer(G.viewer.id,G.viewer.index);
 };
 function removeViewer(){const dialog=document.querySelector('.gallery-lightbox');if(!dialog)return;dialog.close();dialog.remove();dialog.querySelectorAll('video,audio').forEach(asset=>{asset.pause();asset.removeAttribute('src');asset.load();});}
 function closeViewer(){stopVideo();const id=G.viewer?.id;removeViewer();G.viewer=null;Studio.detail=null;document.querySelector(`[data-job="${CSS.escape(id||'')}"] .gallery-media`)?.focus();}
 function showViewer(id,index){
  const j=state.jobs.find(j=>j.id===id);if(!j)return;stopVideo();
  index=outputIndex(j,index);removeViewer();G.viewer={id,index};Studio.detail=id;outputSelection.set(id,index);
  const u=media(j,index),valid=isMediaUrl(u),dlg=document.createElement('dialog');dlg.className='gallery-lightbox';dlg.setAttribute('aria-label',name(j));
  const asset=active(j)?pendingMedia(j):j.modality==='CHAT'?`<pre id="gallery-document">${escape(valid?'Cargando documento…':u||j.prompt)}</pre>`:previewable(j)?j.modality==='VIDEO'?`<video data-gallery-preview="original" src="${escape(previewUrl(j,index,true))}" poster="${escape(previewUrl(j,index))}" playsinline preload="metadata"></video><div class="gallery-player">${button('play','Reproducir / pausar')}<input id="gallery-seek" type="range" min="0" max="100" value="0" aria-label="Posición del vídeo">${button('mute','Activar sonido')}${button('fullscreen','Pantalla completa')}</div>`:`<img data-gallery-preview="original" src="${escape(previewUrl(j,index,true))}" alt="${escape(name(j))}">`:valid&&j.modality==='AUDIO'?`<audio src="${escape(u)}" controls preload="metadata"></audio>`:`<p>${escape(status(j))}</p>`;
  dlg.innerHTML=`<div class="gallery-viewer-media"><div class="gallery-viewer-top">${button('prev','←','aria-label="Creación anterior"')}${button('next','→','aria-label="Creación siguiente"')}<span>${Math.max(0,viewerEntries().findIndex(x=>x.id===id&&x.index===index))+1} / ${Math.max(1,viewerEntries().length)}</span>${button('close-viewer','✕','aria-label="Cerrar visor"')}</div><div class="gallery-stage">${asset}</div>${j.outputs?.length>1?`<div class="gallery-output-nav">${button('prev-output','← Resultado')}${button('next-output','Resultado →')}</div>`:''}</div><aside class="gallery-viewer-details"><h2>${escape(name(j))}</h2><p class="hint">${escape(galleryProviders[j.providerId]||j.providerId)} · ${escape(status(j))}</p>${j.providerId==='google_flow'?`<p class="hint flow-model-evidence">Modelo ${j.modelConfirmed===true?'confirmado':'solicitado · atribución no confirmada por Google'}: ${escape(j.requestedModel||j.name||j.modelId)}</p>`:''}<h3>Prompt</h3><p class="gallery-prompt">${escape(j.prompt)}</p><div class="gallery-viewer-actions">${button('copy-prompt','Copiar prompt')}<button data-reuse="${escape(id)}">Reutilizar ajustes</button>${previewable(j)?button('download-original','Descargar'):valid?`<button ${u.startsWith('/media/')?'data-flow-save':'data-download'}="${escape(u)}">Descargar</button>`:''}${j.modality==='IMAGE'&&valid?`<button data-studio-reference="${escape(id)}">Usar como referencia</button>${button('to-video','Preparar vídeo')}`:''}${j.providerId==='google_flow'&&j.status==='COMPLETED'?`<button data-flow-improve="${escape(id)}">Mejorar / exportar</button>`:''}<button data-gallery-favorite="${escape(id)}">${prefs().favorites.includes(id)?'★ Favorito':'☆ Favorito'}</button></div>${j.error?`<div class="inline-notice" role="alert">No se pudo completar el encargo.<details><summary>Detalles</summary><pre>${escape(j.error)}</pre></details></div>`:''}${j.uncertain||j.pollError?'<p class="ux-notice">Estado pendiente de comprobar. Consulta el trabajo antes de repetir el envío.</p>':''}<h3>Parámetros</h3><dl>${Object.entries(j.parameters||{}).map(([k,v])=>`<dt>${escape(E.label(k))}</dt><dd>${escape(typeof v==='object'?JSON.stringify(v):v)}</dd>`).join('')}</dl><h3>Coste</h3><p>${j.estimated&&Number.isFinite(j.estimated.amount)?escape(amount(j.estimated))+' · estimación, cargo no verificado':'No registrado'}</p></aside>`;
  document.body.append(dlg);wireViewerPreview(dlg,j,index);dlg.showModal();dlg.querySelector('[data-gallery="close-viewer"]').focus();dlg.addEventListener('cancel',e=>{e.preventDefault();closeViewer();});
  if(j.modality==='CHAT'&&valid&&u.startsWith('/media/'))fetch(u).then(r=>{if(!r.ok)throw Error('No se pudo cargar el texto.');return r.text();}).then(text=>{if(dlg.isConnected)dlg.querySelector('#gallery-document').textContent=text.slice(0,100000);}).catch(e=>{if(dlg.isConnected)dlg.querySelector('#gallery-document').textContent=e.message;});
  const image=dlg.querySelector('.gallery-stage img');if(image){
   image.draggable=false;image.style.touchAction='pan-y';let start=null;
   image.addEventListener('pointerdown',e=>{if(e.isPrimary){start={x:e.clientX,y:e.clientY};image.setPointerCapture(e.pointerId);}});
   image.addEventListener('pointercancel',()=>{start=null;});
   image.addEventListener('pointerup',e=>{if(!start)return;const dx=e.clientX-start.x,dy=e.clientY-start.y;start=null;if(Math.abs(dx)>80&&Math.abs(dx)>Math.abs(dy)*1.5)nextViewer(dx<0?1:-1);});
  }
  const v=dlg.querySelector('video');if(v)v.addEventListener('timeupdate',()=>{if(Number.isFinite(v.duration)&&v.duration>0)dlg.querySelector('#gallery-seek').value=String(100*v.currentTime/v.duration);});
 }
 const oldDetails=studioDetails;studioDetails=function(id){const job=state.jobs.find(j=>j.id===id);if(view==='library'&&Studio.tab==='results'||job?.modality==='CHAT'&&media(job)?.startsWith('/media/'))showViewer(id);else oldDetails(id);};
 function nextViewer(step){const list=viewerEntries(),i=list.findIndex(j=>j.id===G.viewer?.id&&j.index===G.viewer.index);if(list.length){const target=list[(i+step+list.length)%list.length];showViewer(target.id,target.index);}}
 async function download(j,index=outputIndex(j)){
  const u=media(j,index);
  if(previewable(j)){
   const link=document.createElement('a');let filename='';
   try{if(typeof u==='string'&&u)filename=decodeURIComponent(new URL(u,location.href).pathname.split('/').pop()||'');}catch{}
   link.href=previewUrl(j,index,true)+'&download=1';link.download=filename||(j.modality==='VIDEO'?'video.mp4':'imagen.jpg');link.click();return;
  }
  if(!isMediaUrl(u))return;
  if(u.startsWith('/media/')){const link=document.createElement('a');link.href=u;link.download=u.split('/').pop();link.click();}
  else await rpc('download',{url:u});
 }
 function commands(){
  modal('<h2>Comandos</h2><input id="gallery-command-search" type="search" aria-label="Buscar comando o modelo" placeholder="Crear, biblioteca, modelo…"><div id="gallery-command-list"></div>');commandList();$('#gallery-command-search').focus();
 }
 function commandList(){
  const q=$('#gallery-command-search').value.toLocaleLowerCase();
  const list=[['canvas','Crear'],['library','Biblioteca'],['assistant','Asistente'],['activity','Cola de actividad'],['settings','Ajustes']].filter(([,l])=>l.toLocaleLowerCase().includes(q)).map(([v,l])=>`<button data-gallery-command="${v}">${l}</button>`);
  if(q)list.push(...models.filter(m=>!forbiddenModel(m)&&m.displayName.toLocaleLowerCase().includes(q)).slice(0,12).map(m=>`<button data-gallery-model="${escape(m.id)}">${escape(m.displayName)}<small>${escape(providers[m.providerId]||m.providerId)}</small></button>`));
  $('#gallery-command-list').innerHTML=list.join('')||'<p>No hay coincidencias</p>';
 }
 function collectionSidebar(){
  $('#nx-sidebar-note')?.remove();let area=$('#gallery-collections');if(!area){area=document.createElement('section');area.id='gallery-collections';$('#studio-settings')?.before(area);}
  area.innerHTML=`<div class="gallery-collection-heading">Colecciones${button('new-collection','+','aria-label="Nueva colección"')}</div>${prefs().collections.map(c=>`<button data-gallery-collection="${escape(c.id)}" aria-pressed="${G.collection===c.id}">${escape(c.name)}</button>`).join('')||'<small>Organiza tus creaciones</small>'}`;
 }
 function shell(){
  document.body.classList.add('gallery-studio');collectionSidebar();
  if(!['library','history'].includes(view)||Studio.tab==='activity'){document.querySelector('.gallery-selection')?.remove();$('#content')?.classList.remove('gallery-library');G.previewObserver?.disconnect();G.previewQueue=[];stopVideo();}
  let activity=$('#gallery-activity');if(!activity){activity=document.createElement('button');activity.id='gallery-activity';activity.dataset.gallery='activity';$('#header-actions')?.append(activity);}
  if(activity){const count=state.jobs.filter(j=>['SENDING','QUEUED','PROCESSING','GENERATING'].includes(j.status)).length;activity.textContent=count?`Actividad · ${count}`:'Actividad';activity.setAttribute('aria-label','Abrir cola de actividad');}
  balances();
 }
 const oldRender=render;render=function(){oldRender();shell();};
 function balances(){
  const bar=$('#account-balances');if(!bar)return;
  bar.classList.add('gallery-balances');bar.querySelectorAll('.account-balance').forEach((chip,i)=>{
   const id=initial?.configured?.[i],item=typeof AccountBalances!=='undefined'?AccountBalances.values[id]:null;
   chip.classList.toggle('is-warning',!!item?.error||id==='runway'&&item?.currency==='credits'&&item.value<50);
   if(id==='google_flow'&&typeof FlowConnection!=='undefined'){
    const s=FlowConnection.value?.status;if(s!==G.flowStatus){G.flowStatus=s;G.flowSince=Date.now();}
    const waiting=['pending','refreshing','signing_in'].includes(s),elapsed=Math.floor((Date.now()-G.flowSince)/1000);
    chip.classList.toggle('is-warning',s!=='connected');
    if(waiting&&elapsed>=45){const text='Sesión sin confirmar · '+elapsed+' s';if(chip.querySelector('strong').textContent!==text)chip.querySelector('strong').textContent=text;chip.title='La sesión aún no se confirma. Puedes comprobar el acceso desde Ajustes; no se ha reenviado ninguna generación.';}
    if(s==='error'){chip.querySelector('strong').textContent='Conexión fallida';const b=chip.querySelector('[data-refresh-flow]');if(b){b.textContent='Reconectar';b.removeAttribute('disabled');}}
   }
  });
 }
 const balanceObserver=new MutationObserver(()=>{balanceObserver.disconnect();balances();balanceObserver.observe(document.querySelector('main'),{childList:true,subtree:true});});
 balanceObserver.observe(document.querySelector('main'),{childList:true,subtree:true});
 setInterval(()=>{if(!document.hidden)balances();},5000);
 document.addEventListener('click',async e=>{
  const b=e.target.closest('button'),visual=e.target.closest('.gallery-media');
  if(!b&&visual&&!e.target.closest('audio'))return showViewer(visual.closest('[data-job]').dataset.job);
  if(!b||b.disabled)return;
  try{
   if(b.dataset.gallerySelect){const j=state.jobs.find(j=>j.id===b.dataset.gallerySelect);if(G.selected.has(j.id))G.selected.delete(j.id);else G.selected.set(j.id,j);renderGallery();return;}
   if(b.dataset.galleryFavorite){const id=b.dataset.galleryFavorite,p=prefs();p.favorites=p.favorites.includes(id)?p.favorites.filter(x=>x!==id):[...p.favorites,id];persist();if(G.viewer)showViewer(id,G.viewer.index);else renderGallery();return;}
   if(b.dataset.galleryKind!==undefined){PC.kind=b.dataset.galleryKind;G.limit=36;renderGallery();return;}
   if(b.dataset.galleryCollection){G.collection=b.dataset.galleryCollection;G.limit=36;Studio.tab='results';navigate('library');return;}
   if(b.dataset.galleryCommand){$('#dialog').close();if(b.dataset.galleryCommand==='activity'){Studio.tab='activity';navigate('library');}else navigate(b.dataset.galleryCommand);return;}
   if(b.dataset.galleryModel){$('#dialog').close();await openModel(b.dataset.galleryModel);return;}
   if(G.viewer&&(b.dataset.reuse||b.dataset.studioReference||b.dataset.flowImprove))closeViewer();
   switch(b.dataset.gallery){
    case 'retry-preview':if(G.viewer)showViewer(G.viewer.id,G.viewer.index);break;
    case 'download-original':if(G.viewer){const job=state.jobs.find(job=>job.id===G.viewer.id);if(job)await download(job,G.viewer.index);}break;
    case 'close-viewer':closeViewer();break;
    case 'prev':nextViewer(-1);break;case 'next':nextViewer(1);break;
    case 'prev-output':case 'next-output':{const j=state.jobs.find(j=>j.id===G.viewer.id),n=j.outputs.length;showViewer(j.id,(G.viewer.index+(b.dataset.gallery==='next-output'?1:-1)+n)%n);break;}
    case 'play':{const v=document.querySelector('.gallery-lightbox video');if(v.paused)v.play().catch(()=>toast('No se pudo reproducir el vídeo.'));else v.pause();break;}
    case 'mute':{const v=document.querySelector('.gallery-lightbox video');v.muted=!v.muted;b.textContent=v.muted?'Activar sonido':'Silenciar';break;}
    case 'fullscreen':await document.querySelector('.gallery-stage').requestFullscreen();break;
    case 'copy-prompt':await navigator.clipboard.writeText(state.jobs.find(j=>j.id===G.viewer.id).prompt||'');toast('Prompt copiado.');break;
    case 'to-video':{const id=G.viewer.id;closeViewer();const j=state.jobs.find(j=>j.id===id),u=media(j),ext=new URL(u,location.href).pathname.split('.').pop().toLowerCase(),mime=({jpg:'image/jpeg',jpeg:'image/jpeg',webp:'image/webp',avif:'image/avif',gif:'image/gif'})[ext]||'image/png';Studio.reference={id:crypto.randomUUID(),name:name(j),mime,size:0,url:u,remoteUrl:u.startsWith('/media/')?null:u};navigate('canvas');studioPicker();Studio.pickerKind='VIDEO';$('#studio-model-kind').value='VIDEO';studioModelList();break;}
    case 'favorites':G.favorites=!G.favorites;G.limit=36;renderGallery();break;
    case 'all':G.collection='';navigate('library');break;
    case 'reset':PC.search='';PC.kind='';PC.provider='';PC.status='';G.favorites=false;G.collection='';G.limit=36;renderGallery();break;
    case 'clear':G.selected.clear();renderGallery();break;
    case 'compare':{if(G.selected.size>4)throw Error('Selecciona hasta cuatro resultados para comparar.');PC.selected=new Map([...G.selected.values()].map(j=>[j.id+':'+(outputSelection.get(j.id)||0),{...j,outputs:[j.outputs[outputSelection.get(j.id)||0]]}]));modal('<h2>Comparar resultados</h2><div class="pc-comparison">'+[...PC.selected.values()].map(j=>'<section class="pc-comparison-item">'+baseJobCard(j)+'</section>').join('')+'</div>');$('#dialog').classList.add('pc-dialog');break;}
    case 'downloads':for(const j of G.selected.values())await download(j);break;
    case 'hide':modal('<h2>Quitar de la galería</h2><p>Se ocultarán '+G.selected.size+' creaciones. Los archivos y el historial se conservan.</p>'+button('confirm-hide','Quitar de la galería'));break;
    case 'confirm-hide':prefs().hidden=[...new Set([...prefs().hidden,...G.selected.keys()])];G.selected.clear();persist();$('#dialog').close();renderGallery();toast('Creaciones ocultas. Puedes recuperarlas en Comandos.');break;
    case 'new-collection':modal('<h2>Nueva colección</h2><form id="gallery-new-collection"><label>Nombre<input name="name" required maxlength="80" autocomplete="off"></label><button class="primary">Crear colección</button></form>');$('#gallery-new-collection input').focus();break;
    case 'assign':modal('<h2>Añadir a colección</h2>'+prefs().collections.map(c=>`<button data-gallery-assign="${escape(c.id)}">${escape(c.name)}</button>`).join('')+button('new-collection','Nueva colección'));break;
    case 'commands':commands();break;
    case 'activity':Studio.tab='activity';navigate('library');break;
    case 'save-view':prefs().savedView={search:PC.search,provider:PC.provider,kind:PC.kind,status:PC.status,favorites:G.favorites,sort:G.sort};persist();toast('Vista guardada. Disponible en Comandos.');break;
   }
   if(b.dataset.galleryAssign){for(const id of G.selected.keys())prefs().members[id]=b.dataset.galleryAssign;persist();$('#dialog').close();toast('Colección actualizada.');}
  }catch(error){toast(error.message);}
 });
 document.addEventListener('submit',e=>{if(e.target.id!=='gallery-new-collection')return;e.preventDefault();const n=new FormData(e.target).get('name').trim();if(!n)return;const c={id:crypto.randomUUID(),name:n};prefs().collections.push(c);for(const id of G.selected.keys())prefs().members[id]=c.id;persist();$('#dialog').close();shell();});
 document.addEventListener('input',e=>{
  if(e.target.id==='pc-search')G.limit=36;
  if(e.target.id==='gallery-command-search')commandList();
  if(e.target.id==='gallery-size'){prefs().size=Number(e.target.value);$('.gallery-grid')?.style.setProperty('--gallery-size',prefs().size+'px');persist();}
  if(e.target.id==='gallery-seek'){const v=document.querySelector('.gallery-lightbox video');if(Number.isFinite(v.duration))v.currentTime=v.duration*Number(e.target.value)/100;}
 });
 document.addEventListener('change',e=>{if(['pc-provider','pc-status'].includes(e.target.id)){G.limit=36;renderGallery();}if(e.target.id==='gallery-sort'){G.sort=e.target.value;renderGallery();}});
 document.addEventListener('keydown',e=>{
  if(e.key==='Escape'&&G.viewer){e.preventDefault();e.stopImmediatePropagation();closeViewer();return;}
  if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();e.stopImmediatePropagation();commands();return;}
  if(e.target.matches('input,textarea,select')||e.target.isContentEditable)return;
  if(G.viewer){if(['ArrowRight','j'].includes(e.key)){e.preventDefault();nextViewer(1);}if(['ArrowLeft','k'].includes(e.key)){e.preventDefault();nextViewer(-1);}if(e.key===' '){e.preventDefault();document.querySelector('[data-gallery="play"]')?.click();}if(e.key.toLowerCase()==='f')document.querySelector('.gallery-lightbox [data-gallery-favorite]')?.click();}
  else if(e.key==='Enter'&&e.target.matches('.gallery-media'))showViewer(e.target.closest('[data-job]').dataset.job);
  if(e.key==='ArrowDown'&&$('#gallery-command-search')){e.preventDefault();$('#gallery-command-list button')?.focus();}
 },true);
 // Additional commands remain reversible and local.
 const baseCommands=commandList;commandList=function(){baseCommands();const q=$('#gallery-command-search').value.toLocaleLowerCase();if(!q||'recuperar ocultos'.includes(q))$('#gallery-command-list').insertAdjacentHTML('beforeend',button('restore-hidden','Recuperar creaciones ocultas'));if(prefs().savedView&&(!q||'vista guardada'.includes(q)))$('#gallery-command-list').insertAdjacentHTML('beforeend',button('saved-view','Abrir vista guardada'));};
 document.addEventListener('click',e=>{const a=e.target.closest('[data-gallery]')?.dataset.gallery;if(a==='restore-hidden'){prefs().hidden=[];persist();$('#dialog').close();Studio.tab='results';navigate('library');}if(a==='saved-view'){const v=prefs().savedView;Object.assign(PC,{search:v.search,provider:v.provider,kind:v.kind,status:v.status});G.favorites=v.favorites;G.sort=v.sort;$('#dialog').close();Studio.tab='results';navigate('library');}});
 setInterval(()=>{if(document.hidden)return;document.querySelectorAll('[data-job-progress]').forEach(node=>{const j=state.jobs.find(x=>x.id===node.dataset.jobProgress);if(j&&active(j))node.innerHTML=progress(j);});},1000);
 if(initial){render();}window.NexusGallery={version:2,render:renderGallery,close:closeViewer,get preview(){return {active:G.previewActive,queued:G.previewQueue.filter(image=>image.isConnected).length,limit:2};}};
})();
