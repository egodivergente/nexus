'use strict';
const Corte={snapshot:null,projectId:null,jobs:[],selected:new Set(),playhead:0,zoom:70,busy:false,error:'',loaded:false};
const corteMediaAnalysis=new Map();
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
 const content=$('#content');content.classList.remove('agent-view','canvas-view');content.classList.add('corte-view');
 const projects=(Agent.snapshot?.index.projects||[]).filter(p=>!p.archived);
 content.innerHTML=`<div class="corte-top"><select id="corte-project" aria-label="Proyecto Nexus"><option value="">Selecciona un proyecto</option>${projects.map(p=>`<option value="${escape(p.id)}" ${Corte.projectId===p.id?'selected':''}>${escape(p.name)}</option>`).join('')}</select><button data-corte="new">Nuevo proyecto</button><button data-corte="media">Medios</button><button data-corte="chat">Agente</button><span id="corte-revision"></span><button data-corte="preview">Preparar visor</button><button data-corte="export" class="primary">Exportar vídeo</button></div>
 <div class="corte-layout" style="--corte-media:${Number(localStorage.getItem('corte-media-width'))||240}px;--corte-chat:${Number(localStorage.getItem('corte-chat-width'))||360}px">
 <aside class="corte-media"><div class="corte-divider" role="separator" aria-label="Ancho de medios" data-corte-resize="media"></div><h3>Medios del proyecto</h3><button data-corte="import">Importar archivos</button><div id="corte-assets"></div></aside>
 <section class="corte-edit"><div class="corte-viewer"><video id="corte-video" controls preload="metadata"></video><div id="corte-preview-state">Prepara el visor para reproducir el montaje con sus efectos.</div></div><div class="corte-controls"><button data-corte="undo" title="Deshacer (Ctrl+Z)">↶</button><button data-corte="redo" title="Rehacer (Ctrl+Mayús+Z)">↷</button><button data-corte="split">Dividir</button><button data-corte="duplicate">Duplicar</button><button data-corte="delete">Eliminar</button><button data-corte="inspector">Inspector</button><button data-corte="title">Título</button><label>Zoom <input id="corte-zoom" type="range" min="15" max="250" value="${Corte.zoom}"></label></div><div id="corte-timeline" tabindex="0" aria-label="Línea de tiempo"></div><p id="corte-error" role="alert"></p><div id="corte-jobs"></div></section>
 <aside id="corte-chat" class="agent-view"></aside></div>`;
 renderCorteTimeline();renderCorteAssets();renderAssistant();
 const video=$('#corte-video');video.addEventListener('timeupdate',()=>{Corte.playhead=Math.round(video.currentTime*1e6);const line=$('.corte-playhead');if(line)line.style.left=(100+Corte.playhead/1e6*Corte.zoom)+'px';});
 if(Corte.loaded&&Agent.snapshot?.projectId&&Agent.snapshot.projectId!==Corte.projectId&&!Corte.loading){Corte.loading=true;corteOpen(Agent.snapshot.projectId).catch(e=>toast(e.message)).finally(()=>Corte.loading=false);return;}
 if(!Corte.loaded&&!Corte.loading){Corte.loading=true;ensureAssistantReady().then(()=>{const id=Agent.snapshot.projectId;if(id)return corteOpen(id);renderCorte();}).catch(e=>toast(e.message)).finally(()=>Corte.loading=false);}
}
function renderCorteTimeline(){
 const timeline=$('#corte-timeline');if(!timeline)return;
 if(!Corte.snapshot){timeline.innerHTML='<p>Crea o abre un proyecto Nexus para montar.</p>';return;}
 const duration=Math.max(10,Corte.snapshot.duration_us/1e6+2),width=duration*Corte.zoom;
 $('#corte-revision').textContent='Guardado · revisión '+Corte.snapshot.revision;
 const viewer=$('#corte-video');if(viewer?.getAttribute('src')&&Number(viewer.dataset.revision)!==Corte.snapshot.revision)$('#corte-preview-state').textContent='Visor de revisión '+viewer.dataset.revision+' · prepara el visor para ver la revisión actual '+Corte.snapshot.revision;
 $('#corte-error').innerHTML=escape(Corte.error)+(Corte.snapshot.missing_media||[]).map(m=>`<span>Medio ausente: ${escape(m.name)} <button data-corte-relink="${escape(m.id)}">Revincular</button></span>`).join('');
 timeline.innerHTML=`<div class="corte-ruler" style="width:${width+100}px">${Array.from({length:Math.ceil(duration)},(_,i)=>`<button data-corte-time="${i*1e6}" style="left:${100+i*Corte.zoom}px">${i}s</button>`).join('')}</div>${corteTracks().map(track=>`<div class="corte-track" style="width:${width+100}px"><span class="corte-track-name">${escape(track.name)}${track.locked?' 🔒':''}</span>${track.clips.map(clip=>`<button draggable="true" data-corte-clip="${escape(clip.id)}" class="corte-clip ${Corte.selected.has(clip.id)?'selected':''}" style="left:${100+clip.timelineStartUs/1e6*Corte.zoom}px;width:${Math.max(12,clip.durationUs/1e6*Corte.zoom)}px" title="${escape(clip.name||clip.text?.text||clip.id)} · ${corteSeconds(clip.timelineStartUs)} · ${corteSeconds(clip.durationUs)}">${escape(clip.name||clip.text?.text||clip.id)}<small>${corteSeconds(clip.durationUs)}</small></button>`).join('')}</div>`).join('')}<div class="corte-playhead" style="left:${100+Corte.playhead/1e6*Corte.zoom}px"></div>`;
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
 const assets=(Agent.snapshot?.index.assets||[]).filter(a=>!a.libraryDeleted&&['VIDEO','IMAGE','AUDIO'].includes(a.type)&&a.projectId===Corte.projectId);
 node.innerHTML=assets.map(a=>`<article><span>${escape(a.displayName)}</span><small>${escape(a.modelId||a.source||'')}</small><button data-corte-place="${escape(a.id)}">Añadir al cabezal</button><button data-corte-after="${escape(a.id)}">Después del clip</button></article>`).join('')||'<p>Los resultados del proyecto aparecerán aquí. Revisa y colócalos cuando quieras.</p>';
}
async function corteFocus(){
 await corteMutate('focus',{playhead_us:Corte.playhead,selection:[...Corte.selected]});
 const video=$('#corte-video');if(video?.src)video.currentTime=Corte.playhead/1e6;
}
async function corteOps(ops,label){return corteMutate('apply_ops',{ops,label});}
async function corteAction(action){
 if(action==='chat'||action==='media'){$('.corte-layout').classList.toggle('hide-'+action);return;}
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
  node.innerHTML=jobs.filter(j=>j.project_id===Corte.projectId).slice(0,6).map(j=>`<div>${j.kind==='preview'?'Visor':'Exportación'} · ${escape(j.state)} ${Math.round(j.progress*100)} % ${j.url?`<a href="${escape(j.url)}" download="Nexus-montaje.mp4">Guardar vídeo</a>`:['QUEUED','RUNNING'].includes(j.state)?`<button data-corte-cancel="${escape(j.id)}">Cancelar</button>`:''}${j.error?`<p role="alert">${escape(j.error)}</p>`:''}</div>`).join('');
  const preview=jobs.find(j=>j.project_id===Corte.projectId&&j.revision===Corte.snapshot?.revision&&j.kind==='preview'&&j.state==='COMPLETED');
  const video=$('#corte-video');if(preview&&video&&video.getAttribute('src')!==preview.url){video.src=preview.url;video.dataset.revision=String(preview.revision);$('#corte-preview-state').textContent='Montaje · revisión '+preview.revision;}
 }catch(e){toast(e.message);}
}
const beforeCorteRender=render;
render=function(){
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
   return await corteMutate('import_media',{asset_ids:[button.dataset.cortePlace||button.dataset.corteAfter],at_us:button.dataset.corteAfter?selected.timelineStartUs+selected.durationUs:Corte.playhead});
  }
  if(button.dataset.corteColor){const colors={bw:{saturation:-1},blue:{temperature:-1},green:{tint:-1},reset:{exposure:0,contrast:0,saturation:0,temperature:0,tint:0,fade:0}};return await corteOps([{op:'set_color',clip_ids:[...Corte.selected],...colors[button.dataset.corteColor]}],'Color');}
  if(button.dataset.corteCancel)return await corteRpc('cancel_export',{job_id:button.dataset.corteCancel});
  await corteAction(button.dataset.corte);
 }catch(error){toast(error.message);}
});
document.addEventListener('change',e=>{if(e.target.id==='corte-project'&&e.target.value)corteOpen(e.target.value).catch(e=>toast(e.message));});
document.addEventListener('change',e=>{if(view==='editor'&&e.target.id==='agent-project'){e.stopImmediatePropagation();if(e.target.value)corteOpen(e.target.value).catch(e=>toast(e.message));else{e.target.value=Corte.projectId;toast('Abre otro proyecto para cambiar el montaje.');}}},true);
document.addEventListener('input',e=>{if(e.target.id==='corte-zoom'){Corte.zoom=Number(e.target.value);renderCorteTimeline();}});
let corteDrag;
document.addEventListener('dragstart',e=>{const c=e.target.closest('[data-corte-clip]');if(c){corteDrag={id:c.dataset.corteClip,x:e.clientX};e.dataTransfer.setData('text/plain',corteDrag.id);}});
document.addEventListener('dragover',e=>{if(e.target.closest('#corte-timeline'))e.preventDefault();});
document.addEventListener('drop',e=>{if(!corteDrag||!e.target.closest('#corte-timeline'))return;e.preventDefault();const drag=corteDrag;corteDrag=null;corteOps([{op:'move',clip_ids:[drag.id],delta_us:Math.round((e.clientX-drag.x)/Corte.zoom*1e6),mode:'insert'}],'Mover clip').catch(e=>toast(e.message));});
document.addEventListener('keydown',e=>{
 if(view!=='editor'||e.target.closest('input,textarea,select,[contenteditable="true"]'))return;
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
