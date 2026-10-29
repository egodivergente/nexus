'use strict';
const Corte={snapshot:null,projectId:null,jobs:[],selected:new Set(),playhead:0,zoom:70,busy:false,error:'',loaded:false,audioTrackId:null};
const corteMediaAnalysis=new Map();
const corteGlyph=name=>`<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${({media:'<rect x="3" y="4" width="18" height="16" rx="3"/><path d="m3 16 5-5 4 4 3-3 6 6"/><circle cx="16" cy="8" r="1"/>',video:'<rect x="3" y="5" width="18" height="14" rx="3"/><path d="m10 9 5 3-5 3Z"/>',audio:'<path d="M9 18V5l11-2v13M9 7l11-2"/><ellipse cx="6" cy="18" rx="3" ry="3"/><ellipse cx="17" cy="16" rx="3" ry="3"/>',chat:'<path d="M21 12a8 8 0 0 1-8 8H5l-3 2V11a9 9 0 0 1 18-2"/><path d="m18 2 1 3 3 1-3 1-1 3-1-3-3-1 3-1Z"/>',focus:'<path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>',plus:'<path d="M12 5v14M5 12h14"/>',search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',play:'<path d="m9 5 11 7-11 7Z"/>',export:'<path d="M12 15V3m-4 4 4-4 4 4M4 14v6h16v-6"/>'})[name]||''}</svg>`;
let cortePanels={media:true,chat:true},corteSearch='';
try{const saved=JSON.parse(localStorage.getItem('corte-panels'));if(saved&&typeof saved.media==='boolean'&&typeof saved.chat==='boolean')cortePanels=saved;}catch{}
function corteApplyPanels(){
 const layout=$('.corte-layout');if(!layout)return;
 for(const side of ['media','chat']){layout.classList.toggle('hide-'+side,!cortePanels[side]);document.querySelector(`[data-corte="${side}"]`)?.setAttribute('aria-pressed',String(cortePanels[side]));}
 document.querySelector('[data-corte="focus"]')?.setAttribute('aria-pressed',String(!cortePanels.media&&!cortePanels.chat));
}
function corteTimecode(){
 const node=$('#corte-timecode');if(!node)return;
 const seconds=Corte.playhead/1e6;node.textContent=`${String(Math.floor(seconds/60)).padStart(2,'0')}:${(seconds%60).toFixed(2).padStart(5,'0')}`;
}
function corteAudioTargets(){
 const select=$('#corte-audio-target');if(!select)return;
 const tracks=Corte.snapshot?.project.timeline.audioTracks||[];
 if(!tracks.some(t=>t.id===Corte.audioTrackId))Corte.audioTrackId=tracks[0]?.id||null;
 const markup=tracks.map(t=>`<option value="${escape(t.id)}" ${t.id===Corte.audioTrackId?'selected':''}>${escape(t.name)}</option>`).join('');
 if(select.innerHTML!==markup)select.innerHTML=markup;select.disabled=!tracks.length||Corte.busy;
 document.querySelector('[data-corte="add-audio-track"]')?.toggleAttribute('disabled',!Corte.snapshot||Corte.busy);
}
function corteTrackHeading(track){
 if(track.kind!=='AUDIO')return `<span class="corte-track-name">${corteGlyph('video')}${escape(track.name)}</span>`;
 return `<div class="corte-track-name corte-audio-header ${track.id===Corte.audioTrackId?'destination':''}"><button class="corte-track-settings" data-corte-track-settings="${escape(track.id)}" title="Ajustar pista ${escape(track.name)}" aria-label="Ajustar pista ${escape(track.name)}">${escape(track.name)}</button><div><button data-corte-track-mute="${escape(track.id)}" aria-label="Silenciar ${escape(track.name)}" aria-pressed="${track.muted}" title="Silenciar">M</button><button data-corte-track-solo="${escape(track.id)}" aria-label="Solo ${escape(track.name)}" aria-pressed="${track.solo}" title="Escuchar solo esta pista">S</button><span>${track.gainDb>0?'+':''}${track.gainDb} dB</span></div></div>`;
}
const corteRpc=(method,args={})=>rpc('corte',{method,...args});
const corteSeconds=us=>(us/1e6).toFixed(2)+' s';
const corteTracks=()=>Corte.snapshot?.project.timeline.videoTracks.concat(Corte.snapshot.project.timeline.audioTracks)||[];
const corteClips=()=>corteTracks().flatMap(t=>t.clips);
const corteMutate=async(method,args={})=>{
 if(Corte.busy)throw Error('Espera a que termine la edición.');
 const projectAtStart=Corte.projectId;
 Corte.busy=true;
 try{
  const r=await corteRpc(method,{project_id:Corte.projectId,expected_revision:Corte.snapshot.revision,request_id:crypto.randomUUID(),...args});
  if(r.project&&Corte.projectId===projectAtStart){Corte.snapshot=r;Corte.selected=new Set(r.selection);Corte.playhead=r.playhead_us;Corte.error='';}
  return r;
 }catch(e){Corte.error=e.message;throw e;}
 finally{Corte.busy=false;if(view==='editor')renderCorteTimeline();}
};
async function corteOpen(projectId){
 if(Agent.busy||Corte.busy)throw Error('Espera a que termine el trabajo del proyecto actual.');
 await ensureAssistantReady();
 const p=Agent.snapshot.index.projects.find(p=>p.id===projectId);if(!p)throw Error('Proyecto desconocido.');
 await agentCall('project',{id:projectId});
 const snapshot=await corteRpc('create_project',{project_id:projectId,name:p.name});
 Corte.projectId=projectId;Corte.snapshot=snapshot;Corte.loaded=true;Corte.selected=new Set(snapshot.selection);Corte.playhead=snapshot.playhead_us;
 if(view==='editor')renderCorte();
}
async function corteRefresh(){
 if(!Corte.projectId||Corte.busy)return;
 const r=await corteRpc('read_project',{project_id:Corte.projectId});
 if(r.revision!==Corte.snapshot?.revision||JSON.stringify(r.missing_media)!==JSON.stringify(Corte.snapshot?.missing_media)){Corte.snapshot=r;Corte.selected=new Set(r.selection);Corte.playhead=r.playhead_us;if(view==='editor')renderCorteTimeline();}
}
function renderCorte(){
 // Project/assistant initialization can complete after navigation has changed.
 // A late editor render must never replace the current workspace or its draft.
 if(view!=='editor')return;
 const content=$('#content');content.classList.remove('agent-view','canvas-view');content.classList.add('corte-view');
 const projects=(Agent.snapshot?.index.projects||[]).filter(p=>!p.archived);
 content.innerHTML=`<div class="corte-top"><div class="corte-project-group"><span class="corte-kicker">PROYECTO</span><select id="corte-project" aria-label="Proyecto Nexus"><option value="">Selecciona un proyecto</option>${projects.map(p=>`<option value="${escape(p.id)}" ${Corte.projectId===p.id?'selected':''}>${escape(p.name)}</option>`).join('')}</select><button data-corte="new" class="corte-icon-button" title="Nuevo proyecto" aria-label="Nuevo proyecto">${corteGlyph('plus')}</button></div><span id="corte-revision" role="status"></span><div class="corte-panel-switches" role="group" aria-label="Paneles del espacio de trabajo"><button data-corte="media" aria-controls="corte-media" title="Mostrar u ocultar medios" aria-pressed="true">${corteGlyph('media')}<span>Medios</span></button><button data-corte="chat" aria-controls="corte-chat" title="Mostrar u ocultar agente" aria-pressed="true">${corteGlyph('chat')}<span>Agente</span></button><button data-corte="focus" class="corte-icon-button" title="Vista de enfoque" aria-label="Vista de enfoque" aria-pressed="false">${corteGlyph('focus')}</button></div><button data-corte="export" class="primary">${corteGlyph('export')}Exportar vídeo</button></div>
 <div class="corte-layout" style="--corte-media:${Number(localStorage.getItem('corte-media-width'))||224}px;--corte-chat:${Number(localStorage.getItem('corte-chat-width'))||320}px">
 <aside id="corte-media" class="corte-media"><div class="corte-divider" role="separator" aria-label="Ancho de medios" data-corte-resize="media"></div><div class="corte-panel-heading"><h3>Medios</h3><span id="corte-asset-count"></span></div><button data-corte="import" class="corte-import">${corteGlyph('plus')}Importar archivos</button><label class="corte-search">${corteGlyph('search')}<input id="corte-search" type="search" aria-label="Buscar medios del proyecto" placeholder="Buscar medios" value="${escape(corteSearch)}"></label><div id="corte-assets"></div></aside>
 <section class="corte-edit"><div class="corte-monitor"><div class="corte-monitor-bar"><span>VISOR</span><output id="corte-timecode" aria-label="Posición del cabezal">00:00.00</output><button data-corte="preview">${corteGlyph('play')}Preparar visor</button></div><div class="corte-viewer"><video id="corte-video" controls preload="metadata"></video><div class="corte-viewer-empty">${corteGlyph('video')}<strong>Tu historia empieza aquí</strong><span>Añade medios a la línea de tiempo.</span></div></div><div id="corte-preview-state">Prepara el visor para reproducir el montaje.</div></div><div class="corte-timeline-panel"><div class="corte-timeline-heading"><span>MONTAJE</span><div class="corte-audio-destination"><select id="corte-audio-target" aria-label="Pista de audio de destino"></select><button data-corte="add-audio-track" title="Añadir pista de audio" aria-label="Añadir pista de audio">${corteGlyph('plus')}Audio</button></div><span id="corte-selection-info"></span></div><div class="corte-controls"><div class="corte-control-group"><button data-corte="undo" title="Deshacer (Ctrl+Z)" aria-label="Deshacer">↶</button><button data-corte="redo" title="Rehacer (Ctrl+Mayús+Z)" aria-label="Rehacer">↷</button></div><div class="corte-control-group"><button data-corte="split" title="Dividir en el cabezal">Dividir</button><button data-corte="duplicate">Duplicar</button><button data-corte="delete" title="Eliminar selección (Supr)">Eliminar</button></div><div class="corte-control-group"><button data-corte="inspector">Inspector</button><button data-corte="title">Título</button></div><label class="corte-zoom">Zoom <input id="corte-zoom" type="range" min="15" max="250" value="${Corte.zoom}"></label></div><div id="corte-timeline" tabindex="0" aria-label="Línea de tiempo"></div></div><p id="corte-error" role="alert"></p><div id="corte-jobs" aria-live="polite"></div></section>
 <aside id="corte-chat" class="agent-view"></aside></div>`;
 renderCorteTimeline();renderCorteAssets();renderAssistant();corteApplyPanels();
 const video=$('#corte-video');video.addEventListener('timeupdate',()=>{Corte.playhead=Math.round(video.currentTime*1e6);corteTimecode();const line=$('.corte-playhead');if(line)line.style.left=(100+Corte.playhead/1e6*Corte.zoom)+'px';});
 if(Corte.loaded&&Agent.snapshot?.projectId&&Agent.snapshot.projectId!==Corte.projectId&&!Corte.loading){Corte.loading=true;corteOpen(Agent.snapshot.projectId).catch(e=>toast(e.message)).finally(()=>Corte.loading=false);return;}
 if(!Corte.loaded&&!Corte.loading){Corte.loading=true;ensureAssistantReady().then(()=>{if(view!=='editor')return;const id=Agent.snapshot.projectId;if(id)return corteOpen(id);renderCorte();}).catch(e=>toast(e.message)).finally(()=>Corte.loading=false);}
}
function renderCorteTimeline(){
 const timeline=$('#corte-timeline');if(!timeline)return;
 const scroll={left:timeline.scrollLeft,top:timeline.scrollTop},focusedClip=timeline.contains(document.activeElement)?document.activeElement.dataset.corteClip:null;
 corteTimecode();corteAudioTargets();
 const selected=corteClips().some(c=>Corte.selected.has(c.id));
 for(const action of ['split','duplicate','delete','inspector'])document.querySelector(`[data-corte="${action}"]`)?.toggleAttribute('disabled',!selected||Corte.busy);
 for(const action of ['undo','redo'])document.querySelector(`[data-corte="${action}"]`)?.toggleAttribute('disabled',!Corte.snapshot?.[action+'_id']||Corte.busy);
 for(const action of ['preview','export','title'])document.querySelector(`[data-corte="${action}"]`)?.toggleAttribute('disabled',!Corte.snapshot||Corte.busy||(['preview','export'].includes(action)&&!corteClips().length));
 const info=$('#corte-selection-info');if(info)info.textContent=selected?Corte.selected.size+' seleccionado'+(Corte.selected.size>1?'s':''):corteClips().length+' clip'+(corteClips().length===1?'':'s');
 if(!Corte.snapshot){timeline.innerHTML='<p>Crea o abre un proyecto Nexus para montar.</p>';return;}
 const duration=Math.max(10,Corte.snapshot.duration_us/1e6+2),width=duration*Corte.zoom;
 $('#corte-revision').textContent=Corte.busy?'Guardando…':'Guardado';
 $('#corte-revision').title='Revisión '+Corte.snapshot.revision;
 const empty=$('.corte-viewer-empty');if(empty){empty.querySelector('strong').textContent=corteClips().length?'Tu montaje, listo para el visor':'Tu historia empieza aquí';empty.querySelector('span').textContent=corteClips().length?'Pulsa Preparar visor para reproducirlo.':'Añade medios a la línea de tiempo.';}
 const viewer=$('#corte-video');if(viewer?.getAttribute('src')&&Number(viewer.dataset.revision)!==Corte.snapshot.revision)$('#corte-preview-state').textContent='Visor de revisión '+viewer.dataset.revision+' · prepara el visor para ver la revisión actual '+Corte.snapshot.revision;
 $('#corte-error').innerHTML=escape(Corte.error)+(Corte.snapshot.missing_media||[]).map(m=>`<span>Medio ausente: ${escape(m.name)} <button data-corte-relink="${escape(m.id)}">Revincular</button></span>`).join('');
 const step=Math.max(1,Math.ceil(48/Corte.zoom));
 timeline.innerHTML=`<div class="corte-ruler" style="width:${width+100}px">${Array.from({length:Math.ceil(duration/step)},(_,i)=>`<button data-corte-time="${i*step*1e6}" style="left:${100+i*step*Corte.zoom}px" aria-label="Ir a ${i*step} segundos">${i*step}s</button>`).join('')}</div>${corteTracks().map(track=>`<div class="corte-track ${track.kind==='AUDIO'?'audio':'video'}" style="width:${width+100}px">${corteTrackHeading(track)}${track.clips.map(clip=>`<button draggable="true" data-corte-clip="${escape(clip.id)}" class="corte-clip ${clip.kind==='AUDIO'?'audio':clip.kind==='TITLE'?'title':'video'} ${Corte.selected.has(clip.id)?'selected':''}" aria-pressed="${Corte.selected.has(clip.id)}" style="left:${100+clip.timelineStartUs/1e6*Corte.zoom}px;width:${Math.max(12,clip.durationUs/1e6*Corte.zoom)}px" title="${escape(clip.name||clip.text?.text||clip.id)} · ${corteSeconds(clip.timelineStartUs)} · ${corteSeconds(clip.durationUs)}">${escape(clip.name||clip.text?.text||clip.id)}<small>${corteSeconds(clip.durationUs)}</small></button>`).join('')}</div>`).join('')}<div class="corte-playhead" style="left:${100+Corte.playhead/1e6*Corte.zoom}px"></div>`;
 timeline.scrollLeft=scroll.left;timeline.scrollTop=scroll.top;
 if(focusedClip)timeline.querySelector(`[data-corte-clip="${CSS.escape(focusedClip)}"]`)?.focus({preventScroll:true});
 renderCorteAssets();
 for(const media of Corte.snapshot.project.media){
  if(corteMediaAnalysis.has(media.id))continue;
  const projectId=Corte.projectId;corteMediaAnalysis.set(media.id,null);
  corteRpc('media_analysis',{project_id:projectId,media_id:media.id,pixels:200}).then(result=>{corteMediaAnalysis.set(media.id,result);if(view==='editor'&&Corte.projectId===projectId)corteDrawAnalysis();}).catch(error=>{corteMediaAnalysis.set(media.id,{error:error.message});});
 }
 corteDrawAnalysis();
}
function corteDrawAnalysis(){
 for(const clip of corteClips()){
  const data=corteMediaAnalysis.get(clip.mediaId),node=document.querySelector(`[data-corte-clip="${CSS.escape(clip.id)}"]`);if(!data||!node)continue;
  if(data.thumbnail&&clip.kind!=='AUDIO'){node.style.backgroundImage=`linear-gradient(#101b2380,#101b2380),url("${data.thumbnail}")`;node.style.backgroundSize='auto 100%';}
  if(data.max&&clip.kind==='AUDIO'&&!node.querySelector('svg')){
   const points=data.max.map((value,i)=>`${i},${24-Math.max(0,value)*22}`).join(' ');
   const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 200 48');svg.setAttribute('preserveAspectRatio','none');svg.style.cssText='position:absolute;inset:0;width:100%;height:100%;opacity:.3;pointer-events:none';
   const line=document.createElementNS(svg.namespaceURI,'polyline');line.setAttribute('points',points);line.setAttribute('stroke','#8cf3db');line.setAttribute('fill','none');svg.append(line);node.prepend(svg);
  }
 }
}
function renderCorteAssets(){
 const node=$('#corte-assets');if(!node)return;
 const all=(Agent.snapshot?.index.assets||[]).filter(a=>Corte.projectId&&!a.libraryDeleted&&['VIDEO','IMAGE','AUDIO'].includes(a.type)&&a.projectId===Corte.projectId),assets=all.filter(a=>String(a.displayName||'').toLocaleLowerCase().includes(corteSearch.toLocaleLowerCase()));
 const count=$('#corte-asset-count');if(count)count.textContent=String(all.length);
 const markup=assets.map(a=>CorteMedia.markup(a,Agent.snapshot?.index,!!Corte.selected.size,corteGlyph)).join('')||`<div class="corte-empty-media">${corteGlyph('media')}<strong>${corteSearch?'Sin coincidencias':'Tu material, aquí'}</strong><p>${corteSearch?'Prueba otro nombre.':'Importa archivos o genera medios con el agente.'}</p></div>`;
 if(node.innerHTML!==markup)node.innerHTML=markup;
}
async function corteFocus(){
 await corteMutate('focus',{playhead_us:Corte.playhead,selection:[...Corte.selected]});
 const video=$('#corte-video');if(video?.src)video.currentTime=Corte.playhead/1e6;
}
async function corteOps(ops,label){return corteMutate('apply_ops',{ops,label});}
async function corteAction(action){
 if(action==='chat'||action==='media'||action==='focus'){
  if(action==='focus'){const focus=cortePanels.media||cortePanels.chat;cortePanels={media:!focus,chat:!focus};}else cortePanels[action]=!cortePanels[action];
  localStorage.setItem('corte-panels',JSON.stringify(cortePanels));corteApplyPanels();return;
 }
 if(action==='add-audio-track'){modal(`<h2>Nueva pista de audio</h2><form id="corte-audio-new"><label>Nombre<input name="name" maxlength="80" value="Audio ${(Corte.snapshot?.project.timeline.audioTracks.length||0)+1}" required></label><label>Uso<select name="role"><option value="GENERIC_AUDIO">Audio</option><option value="MUSIC">Música</option><option value="VOICE_OVER">Voz en off</option><option value="DIALOGUE">Diálogo</option><option value="AMBIENCE">Ambiente</option><option value="SFX">Efectos</option></select></label><p class="corte-form-error" role="alert"></p><button class="primary" type="submit">Crear pista</button></form>`);return;}
 if(action==='new'){const name=prompt('Nombre del proyecto');if(!name?.trim())return;await agentCall('createProject',{name});await corteOpen(Agent.snapshot.projectId);return;}
 if(!Corte.snapshot)throw Error('Abre un proyecto primero.');
 const ids=[...Corte.selected];
 if(action==='import'){
  const files=await rpc('pick');if(!files.length)return;
  await agentCall('importReferences',{projectId:Corte.projectId,files:files.map(f=>({uri:f.url,mime:f.mime,name:f.name}))});renderCorteAssets();return;
 }
 if(action==='undo'||action==='redo')return corteMutate(action,{transaction_id:Corte.snapshot[action+'_id']});
 if(action==='split')return corteOps([{op:'split',clip_ids:ids,at_us:Corte.playhead}],'Dividir');
 if(action==='duplicate'||action==='delete')return corteOps([{op:action,clip_ids:ids,ripple:true}],action==='delete'?'Eliminar':'Duplicar');
 if(action==='title'){const text=prompt('Texto del título');if(text)return corteOps([{op:'add_title',at_us:Corte.playhead,duration_us:3000000,text}],'Título');return;}
 if(action==='inspector'){
  if(!ids.length)throw Error('Selecciona un clip.');
  modal(`<h2>Inspector</h2><p>${ids.length} clips seleccionados</p><button data-corte-color="bw">Blanco y negro</button><button data-corte-color="blue">Azul</button><button data-corte-color="green">Verde</button><button data-corte-color="reset">Restablecer color</button><button data-corte="fades">Fundidos de entrada y salida</button><button data-corte="transition">Transición a negro</button><button data-corte="marker">Marcador</button>`);return;
 }
 if(action==='marker'){const label=prompt('Nombre del marcador');if(label!=null)return corteOps([{op:'add_marker',at_us:Corte.playhead,label}],'Marcador');return;}
 if(action==='fades')return corteOps([{op:'set_video',clip_ids:ids,fade_in_us:250000,fade_out_us:250000},{op:'set_audio',clip_ids:ids,fade_in_us:250000,fade_out_us:250000}],'Fundidos');
 if(action==='transition'){
  const track=corteTracks().find(t=>t.clips.some(c=>ids.includes(c.id)));const index=track?.clips.findIndex(c=>ids.includes(c.id));
  if(!track||index<0||!track.clips[index+1])throw Error('Selecciona el clip anterior a un corte.');
  return corteOps([{op:'set_transition',track_id:track.id,left_clip_id:track.clips[index].id,right_clip_id:track.clips[index+1].id,kind:track.kind==='VIDEO'?'DIP_TO_BLACK':'AUDIO_CROSSFADE',duration_us:500000}],'Transición');
 }
 if(action==='preview'||action==='export'){
  await corteMutate(action);toast(action==='preview'?'Preparando el montaje del visor…':'Exportación en cola');cortePollJobs();return;
 }
}
async function cortePollJobs(){
 try{
  const {jobs}=await corteRpc('jobs');Corte.jobs=jobs;
  const node=$('#corte-jobs');if(!node)return;
  const markup=jobs.filter(j=>j.project_id===Corte.projectId).slice(0,6).map(j=>`<div>${j.kind==='preview'?'Visor':'Exportación'} · ${escape(({QUEUED:'En cola',RUNNING:'Preparando',COMPLETED:'Listo',FAILED:'Error',CANCELLED:'Cancelado'})[j.state]||j.state)} ${Math.round(j.progress*100)} % ${j.url?`<a href="${escape(j.url)}" download="Nexus-montaje.mp4">Guardar vídeo</a>`:['QUEUED','RUNNING'].includes(j.state)?`<button data-corte-cancel="${escape(j.id)}">Cancelar</button>`:''}${j.error?`<p role="alert">${escape(j.error)}</p>`:''}</div>`).join('');
  if(node.innerHTML!==markup)node.innerHTML=markup;
  const preview=jobs.find(j=>j.project_id===Corte.projectId&&j.revision===Corte.snapshot?.revision&&j.kind==='preview'&&j.state==='COMPLETED');
  const video=$('#corte-video');if(preview&&video&&video.getAttribute('src')!==preview.url){video.src=preview.url;video.dataset.revision=String(preview.revision);$('#corte-preview-state').textContent='Montaje · revisión '+preview.revision;}
 }catch(e){toast(e.message);}
}
const beforeCorteRender=render;
render=function(){
 document.body.classList.toggle('corte-workspace',view==='editor');
 $('#content').classList.remove('corte-view');
 if(view==='editor'){renderShell();$('#title').textContent='Montaje';$('#eyebrow').textContent='NEXUS · CORTE';renderCorte();}
 else beforeCorteRender();
 if(!$('#navigation [data-nav="editor"]'))$('#navigation').insertAdjacentHTML('beforeend',`<button data-nav="editor" class="${view==='editor'?'active':''}">${icon('canvas')}Montaje</button>`);
};
document.addEventListener('click',async e=>{
 const relink=e.target.closest('[data-corte-relink]');if(relink){
  try{const [file]=await rpc('pick');if(!file)return;const accept=confirm('Usar este archivo para revincular el medio. Si su huella es diferente, se sustituirá la referencia del montaje; se conservan ambos originales.');if(!accept)return;await corteMutate('relink',{media_id:relink.dataset.corteRelink,uri:file.url,mime:file.mime,name:file.name,accept_changed_file:true});}catch(error){toast(error.message);}return;
 }
 const button=e.target.closest('[data-corte],[data-corte-clip],[data-corte-time],[data-corte-place],[data-corte-after],[data-corte-color],[data-corte-cancel]');if(!button)return;
 try{
  if(button.dataset.corteClip){if(!e.ctrlKey&&!e.metaKey&&!e.shiftKey)Corte.selected.clear();Corte.selected.add(button.dataset.corteClip);Corte.playhead=corteClips().find(c=>c.id===button.dataset.corteClip).timelineStartUs;return await corteFocus();}
  if(button.dataset.corteTime){Corte.playhead=Number(button.dataset.corteTime);return await corteFocus();}
  if(button.dataset.cortePlace||button.dataset.corteAfter){
   const selected=corteClips().find(c=>Corte.selected.has(c.id));
   if(button.dataset.corteAfter&&!selected)throw Error('Selecciona el clip después del que insertar.');
   const assetId=button.dataset.cortePlace||button.dataset.corteAfter,asset=Agent.snapshot?.index.assets.find(a=>a.id===assetId);
   return await corteMutate('import_media',{asset_ids:[assetId],audio_track_id:Corte.audioTrackId,...(asset?.type==='AUDIO'?{mode:'mix'}:{}),at_us:button.dataset.corteAfter?selected.timelineStartUs+selected.durationUs:Corte.playhead});
  }
  if(button.dataset.corteColor){const colors={bw:{saturation:-1},blue:{temperature:-1},green:{tint:-1},reset:{exposure:0,contrast:0,saturation:0,temperature:0,tint:0,fade:0}};return await corteOps([{op:'set_color',clip_ids:[...Corte.selected],...colors[button.dataset.corteColor]}],'Color');}
  if(button.dataset.corteCancel)return await corteRpc('cancel_export',{job_id:button.dataset.corteCancel});
  await corteAction(button.dataset.corte);
 }catch(error){toast(error.message);}
});
document.addEventListener('change',e=>{if(e.target.id==='corte-audio-target'){Corte.audioTrackId=e.target.value;renderCorteTimeline();}if(e.target.id==='corte-project'&&e.target.value)corteOpen(e.target.value).catch(e=>toast(e.message));});
document.addEventListener('change',e=>{if(view==='editor'&&e.target.id==='agent-project'){e.stopImmediatePropagation();if(e.target.value)corteOpen(e.target.value).catch(e=>toast(e.message));else{e.target.value=Corte.projectId;toast('Abre otro proyecto para cambiar el montaje.');}}},true);
document.addEventListener('input',e=>{if(e.target.id==='corte-zoom'){Corte.zoom=Number(e.target.value);renderCorteTimeline();}if(e.target.id==='corte-search'){corteSearch=e.target.value;renderCorteAssets();}});
let corteDrag;
document.addEventListener('dragstart',e=>{const c=e.target.closest('[data-corte-clip]');if(c){corteDrag={id:c.dataset.corteClip,x:e.clientX};e.dataTransfer.setData('text/plain',corteDrag.id);}});
document.addEventListener('dragover',e=>{if(e.target.closest('#corte-timeline'))e.preventDefault();});
document.addEventListener('drop',e=>{if(!corteDrag||!e.target.closest('#corte-timeline'))return;e.preventDefault();const drag=corteDrag;corteDrag=null;corteOps([{op:'move',clip_ids:[drag.id],delta_us:Math.round((e.clientX-drag.x)/Corte.zoom*1e6),mode:'insert'}],'Mover clip').catch(e=>toast(e.message));});
document.addEventListener('keydown',e=>{
 if(view!=='editor'||e.target.closest('input,textarea,select,button,summary,a,[contenteditable="true"]'))return;
 if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'){e.preventDefault();corteAction(e.shiftKey?'redo':'undo').catch(e=>toast(e.message));}
 if(e.code==='Space'){e.preventDefault();const video=$('#corte-video');if(video?.src)(video.paused?video.play():video.pause());}
 if(e.key==='Delete'){e.preventDefault();corteAction('delete').catch(e=>toast(e.message));}
});
setInterval(()=>{if(view==='editor'&&!document.hidden){corteRefresh().catch(e=>{Corte.error=e.message;});cortePollJobs();}},2000);
let corteResize;
document.addEventListener('pointerdown',e=>{const divider=e.target.closest('[data-corte-resize]');if(!divider)return;const side=divider.dataset.corteResize;corteResize={side,x:e.clientX,width:(side==='media'?$('.corte-media'):$('#corte-chat')).getBoundingClientRect().width};divider.setPointerCapture(e.pointerId);e.preventDefault();});
document.addEventListener('pointermove',e=>{if(!corteResize)return;const {side,x,width}=corteResize;const next=Math.max(side==='media'?180:240,Math.min(600,width+(e.clientX-x)*(side==='chat'?-1:1)));$('.corte-layout')?.style.setProperty('--corte-'+side,next+'px');localStorage.setItem('corte-'+side+'-width',String(next));});
document.addEventListener('pointerup',()=>{corteResize=null;});
document.addEventListener('contextmenu',e=>{const clip=e.target.closest('[data-corte-clip]');if(!clip)return;e.preventDefault();Corte.selected=new Set([clip.dataset.corteClip]);corteFocus().then(()=>modal('<h2>Clip seleccionado</h2><button data-corte="inspector">Inspector</button><button data-corte="duplicate">Duplicar</button><button data-corte="split">Dividir en el cabezal</button><button data-corte="delete">Eliminar</button>')).catch(e=>toast(e.message));});
const beforeCorteSend=sendAgent;
sendAgent=async function(requestText){if(view==='editor'&&Corte.snapshot&&!Corte.busy&&!Agent.busy)await corteFocus();return beforeCorteSend(requestText);};

// Track controls use the same transactional path as clip edits and the agent.
document.addEventListener('click',async e=>{
 const button=e.target.closest('[data-corte-track-settings],[data-corte-track-mute],[data-corte-track-solo]');if(!button)return;
 const id=button.dataset.corteTrackSettings||button.dataset.corteTrackMute||button.dataset.corteTrackSolo,track=Corte.snapshot?.project.timeline.audioTracks.find(t=>t.id===id);if(!track)return;
 try{
  if(button.dataset.corteTrackMute||button.dataset.corteTrackSolo){const field=button.dataset.corteTrackMute?'muted':'solo';return await corteOps([{op:'set_audio_track',track_id:id,[field]:!track[field]}],field==='muted'?'Silenciar pista':'Solo de pista');}
  modal(`<h2>Ajustes de pista</h2><form id="corte-audio-settings" data-track-id="${escape(id)}"><label>Nombre<input name="name" maxlength="80" value="${escape(track.name)}" required></label><label>Volumen (dB)<input name="gain_db" type="number" min="-60" max="12" step="0.5" value="${track.gainDb}" required></label><label><input type="checkbox" name="muted" ${track.muted?'checked':''}>Silenciar pista</label><label><input type="checkbox" name="solo" ${track.solo?'checked':''}>Escuchar solo esta pista</label><p class="corte-form-error" role="alert"></p><button class="primary" type="submit">Guardar pista</button></form>`);
 }catch(error){toast(error.message);}
});
document.addEventListener('submit',async e=>{
 const form=e.target;if(!['corte-audio-new','corte-audio-settings'].includes(form.id))return;e.preventDefault();
 const values=new FormData(form),submit=form.querySelector('[type=submit]');submit.disabled=true;
 try{
  if(form.id==='corte-audio-new'){
   const previous=new Set(Corte.snapshot.project.timeline.audioTracks.map(t=>t.id));
   await corteOps([{op:'add_audio_track',name:String(values.get('name')).trim(),role:values.get('role')}],'Añadir pista de audio');
   Corte.audioTrackId=Corte.snapshot.project.timeline.audioTracks.find(t=>!previous.has(t.id))?.id||Corte.audioTrackId;renderCorteTimeline();
  }else await corteOps([{op:'set_audio_track',track_id:form.dataset.trackId,name:String(values.get('name')).trim(),gain_db:Number(values.get('gain_db')),muted:values.has('muted'),solo:values.has('solo')}],'Ajustar pista de audio');
  $('#dialog').close();
 }catch(error){form.querySelector('.corte-form-error').textContent=error.message;}finally{submit.disabled=false;}
});
