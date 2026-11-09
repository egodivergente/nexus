(function(root){'use strict';
const client={socket:null,token:null,phase:'idle',message:'Conecta para ver y controlar tu pestaña de Flow.',assets:new Map(),imported:new Map(),pointer:null,frame:0};
const escapeText=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const el=id=>document.getElementById(id);
function active(){return typeof view!=='undefined'&&view==='flow';}
function status(message,phase=client.phase){client.message=message;client.phase=phase;if(el('flow-live-status'))el('flow-live-status').textContent=message;const frame=el('flow-live-canvas');if(frame)frame.setAttribute('aria-disabled',String(phase!=='live'));if(el('flow-live-connect'))el('flow-live-connect').textContent=phase==='live'?'Desconectar':phase==='connecting'?'Conectando…':'Conectar';}
function send(event){if(client.phase==='live'&&client.socket?.readyState===WebSocket.OPEN)client.socket.send(JSON.stringify(event));}
async function session(){const response=await fetch('/session',{cache:'no-store'});if(!response.ok)throw Error('No se pudo abrir la sesión local.');const value=await response.json();client.token=value.token;return value.token;}
const url=path=>'/flow-live/'+path+(path.includes('?')?'&':'?')+'token='+encodeURIComponent(client.token||'');
function stop(){const socket=client.socket;client.socket=null;client.pointer=null;client.frame++;if(socket)socket.close(1000,'Pantalla cerrada');status('Desconectado. Los trabajos aceptados en Flow siguen su curso.','idle');}
async function connect(){
 if(client.phase==='live'){stop();return;}
 if(client.phase==='connecting')return;
 if(typeof initial!=='undefined'&&initial?.qa){status('Flow en directo está deshabilitado en las pruebas aisladas.','error');return;}
 if(client.socket){const old=client.socket;client.socket=null;old.close(1000,'Reconexión explícita');}
 status('Conectando con Flow en AXELPC…','connecting');
 try{
  const token=await session();if(!active())return stop();
  const response=await fetch(url('health'),{cache:'no-store'});if(!response.ok)throw Error('El servicio de Flow no responde.');
  const health=await response.json();if(health.ok!==true)throw Error('No se pudo abrir la pestaña de Flow en AXELPC.');
  if(!active())return stop();
  const endpoint=new URL('/flow-live/live',location.href);endpoint.protocol=location.protocol==='https:'?'wss:':'ws:';endpoint.searchParams.set('token',token);
  const socket=new WebSocket(endpoint);client.socket=socket;socket.binaryType='arraybuffer';
  socket.onopen=()=>{if(client.socket!==socket)return socket.close();status('Conectado · los controles actúan sobre tu pestaña de Flow.','live');};
  socket.onmessage=event=>{if(client.socket!==socket||!active())return;if(typeof event.data==='string'){try{message(JSON.parse(event.data));}catch{status('Flow envió una respuesta que no se pudo leer.','error');}}else draw(event.data,socket);};
  socket.onerror=()=>{if(client.socket===socket)status('Se perdió la conexión. Reconecta para continuar; no se repite ninguna acción.','error');};
  socket.onclose=()=>{if(client.socket===socket){client.socket=null;status('Desconectado. Tu trabajo en Flow se conserva.','idle');}};
 }catch(error){status(error.message||'No se pudo conectar con Flow.','error');}
}
async function draw(bytes,socket){
 const sequence=++client.frame;
 try{const bitmap=await createImageBitmap(new Blob([bytes],{type:'image/jpeg'}));
  if(sequence!==client.frame||client.socket!==socket||!active()){bitmap.close();return;}
  const canvas=el('flow-live-canvas');if(canvas){canvas.width=bitmap.width;canvas.height=bitmap.height;canvas.getContext('2d',{alpha:false}).drawImage(bitmap,0,0);el('flow-live-placeholder')?.remove();}bitmap.close();
 }catch{status('No se pudo mostrar el último fotograma. Puedes actualizar la vista.','error');}
}
function message(data){
 switch(data.type){
 case 'hello':for(const asset of data.assets||[])client.assets.set(asset.id,asset);renderAssets();break;
 case 'state':if(el('flow-live-page'))el('flow-live-page').textContent=data.title||'Google Flow';break;
 case 'asset':if(data.asset?.id){client.assets.set(data.asset.id,data.asset);renderAssets();status('Descarga recibida. Puedes guardarla en Biblioteca.','live');}break;
 case 'download':status('Descargando en Flow: '+(data.name||'archivo'),'live');break;
 case 'error':status(data.message||'Flow no pudo completar la acción.','error');break;
 case 'order':status(data.state==='sent'?'Encargo enviado a Flow.':data.message||'Flow está preparando el encargo.','live');break;
 }
}
function point(event,canvas){const box=canvas.getBoundingClientRect();return{x:Math.max(0,Math.min(1,(event.clientX-box.left)/box.width)),y:Math.max(0,Math.min(1,(event.clientY-box.top)/box.height))};}
function bindCanvas(canvas){
 canvas.addEventListener('pointerdown',event=>{if(event.button!==0||client.phase!=='live')return;event.preventDefault();canvas.focus();canvas.setPointerCapture(event.pointerId);client.pointer={id:event.pointerId,start:point(event,canvas),x:event.clientX,y:event.clientY,drag:false};});
 canvas.addEventListener('pointermove',event=>{const p=client.pointer;if(!p||p.id!==event.pointerId||client.phase!=='live')return;const at=point(event,canvas);if(!p.drag&&Math.hypot(event.clientX-p.x,event.clientY-p.y)>5){p.drag=true;send({t:'down',...p.start});}if(p.drag)send({t:'move',...at});});
 canvas.addEventListener('pointerup',event=>{const p=client.pointer;if(!p||p.id!==event.pointerId)return;client.pointer=null;send({t:p.drag?'up':'click',...point(event,canvas)});});
 canvas.addEventListener('pointercancel',event=>{if(client.pointer?.drag)send({t:'up',...point(event,canvas)});client.pointer=null;});
 canvas.addEventListener('wheel',event=>{if(client.phase!=='live')return;event.preventDefault();send({t:'wheel',...point(event,canvas),dx:event.deltaX,dy:event.deltaY});},{passive:false});
 canvas.addEventListener('keydown',event=>{
  if(document.activeElement!==canvas||client.phase!=='live')return;
  if((event.ctrlKey||event.metaKey)&&['a','z','y'].includes(event.key.toLowerCase())){event.preventDefault();send({t:'command',c:({a:'selectAll',z:event.shiftKey?'redo':'undo',y:'redo'})[event.key.toLowerCase()]});return;}
  if(event.ctrlKey||event.metaKey||event.altKey)return;
  if(event.key.length===1){event.preventDefault();send({t:'text',s:event.key});}
  else if(['Enter','Backspace','Delete','Tab','Escape','ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Home','End','PageUp','PageDown'].includes(event.key)){event.preventDefault();send({t:'key',k:event.key});}
 });
 canvas.addEventListener('paste',event=>{if(document.activeElement!==canvas||client.phase!=='live')return;const text=event.clipboardData?.getData('text/plain');if(text){event.preventDefault();send({t:'text',s:text});}});
}
function renderAssets(){
 const host=el('flow-live-assets');if(!host)return;
 const list=[...client.assets.values()].sort((a,b)=>(b.ts||0)-(a.ts||0));
 host.innerHTML=list.length?list.map(asset=>`<article class="flow-result"><span><strong>${escapeText(asset.name||'Resultado de Flow')}</strong><small>${escapeText(asset.mime||'archivo')} · ${Math.round((asset.size||0)/1024)} KB</small></span><button data-flow-import="${escapeText(asset.id)}" ${client.imported.has(asset.id)?'disabled':''}>${client.imported.has(asset.id)?'En Biblioteca':'Guardar en Biblioteca'}</button><a href="${escapeText(url('assets/'+encodeURIComponent(asset.id)))}" download="${escapeText(asset.name||'flow')}">Descargar</a></article>`).join(''):'<p class="hint">Las descargas de esta pestaña aparecerán aquí. Se guardan en Por colocar, sin atribuirlas al último plano abierto.</p>';
}
function b64(bytes){let value='';for(let n=0;n<bytes.length;n+=32768)value+=String.fromCharCode(...bytes.subarray(n,n+32768));return btoa(value);}
async function importAsset(assetId){
 const asset=client.assets.get(assetId);if(!asset)throw Error('El resultado no está disponible.');
 if(!client.token)await session();
 const response=await fetch(url('assets/'+encodeURIComponent(assetId)),{cache:'no-store'});if(!response.ok||!response.body)throw Error('No se pudo recuperar el archivo de Flow.');
 const started=await rpc('importBegin',{name:asset.name,size:asset.size});let finished=false;
 try{
  const reader=response.body.getReader();let buffered=new Uint8Array(0),offset=0;
  while(true){const next=await reader.read();if(next.done)break;const joined=new Uint8Array(buffered.length+next.value.length);joined.set(buffered);joined.set(next.value,buffered.length);buffered=joined;
   while(buffered.length>=started.chunk_bytes){const block=buffered.slice(0,started.chunk_bytes);await rpc('importChunk',{import_id:started.import_id,offset,content:b64(block)});offset+=block.length;buffered=buffered.slice(block.length);status('Guardando '+asset.name+' · '+Math.round(offset/asset.size*100)+' %');}
  }
  if(buffered.length)await rpc('importChunk',{import_id:started.import_id,offset,content:b64(buffered)});
  const imported=await rpc('importFinish',{import_id:started.import_id});finished=true;
  if(asset.sha256&&imported.sha256!==asset.sha256)throw Error('El archivo no coincide con la huella de Flow. La copia queda sin asociar; no se ha sustituido ningún original.');
  const registered=await rpc('libraryImport',{id:imported.id,projectId:null});
  if(!['created','exists'].includes(registered.status))throw Error('El archivo ya estaba retirado. No se ha restaurado automáticamente.');
  client.imported.set(assetId,registered.assetId);renderAssets();window.dispatchEvent(new CustomEvent('nexus:library-changed'));status('Guardado en Biblioteca · Por colocar.');
 }catch(error){if(!finished)try{await rpc('importAbort',{import_id:started.import_id});}catch{}throw error;}
}
function selectedShot(){const state=root.NexusStory?.state;return state?.data?.shots?.find(shot=>shot.id===(state.shotId||state.selection?.shotId));}
function render(){
 if(!active())return;
 if(el('flow-live-workspace'))return;
 const shot=selectedShot();
 document.querySelector('#content').innerHTML=`<section id="flow-live-workspace"><div class="flow-live-toolbar"><strong id="flow-live-page">Flow en directo</strong><button id="flow-live-connect">Conectar</button><button data-flow-command="back" title="Volver en Flow">←</button><button data-flow-command="reload">Recargar</button><button data-flow-command="refresh">Actualizar imagen</button><button id="flow-live-desktop">Vista de escritorio</button></div><p id="flow-live-status" role="status">${escapeText(client.message)}</p><div class="flow-live-screen"><span id="flow-live-placeholder">La pestaña real de Flow se mostrará al conectar.</span><canvas id="flow-live-canvas" width="1280" height="720" tabindex="0" role="application" aria-label="Pantalla de Google Flow. Haz clic para controlar; el teclado solo se envía con el foco aquí." aria-disabled="true"></canvas></div><div class="flow-live-compose">${shot?`<button id="flow-live-context">Preparar texto de ${escapeText(shot.title||shot.code||'este plano')}</button>`:''}<textarea id="flow-live-text" rows="2" placeholder="Escribir en el campo de Flow que hayas seleccionado" aria-label="Texto para Flow"></textarea><button id="flow-live-type">Escribir en Flow</button></div><details class="flow-live-results"><summary>Resultados descargados</summary><div id="flow-live-assets"></div></details></section>`;
 bindCanvas(el('flow-live-canvas'));el('flow-live-connect').onclick=connect;
 document.querySelectorAll('[data-flow-command]').forEach(button=>button.onclick=()=>send({t:button.dataset.flowCommand}));
 el('flow-live-desktop').onclick=()=>send({t:'view',mode:'desktop',width:1280,vw:1280,vh:800,dpr:1,quality:65,fps:8,maxpx:1280});
 el('flow-live-type').onclick=()=>{const text=el('flow-live-text').value;if(!text.trim())return;if(client.phase!=='live')return status('Conecta y selecciona un campo de Flow antes de escribir.');send({t:'text',s:text});status('Texto enviado al campo seleccionado. Revisa Flow antes de generar.');};
 if(el('flow-live-context'))el('flow-live-context').onclick=()=>{const current=selectedShot();if(current)el('flow-live-text').value=[current.title,current.description,current.intent&&'Intención: '+current.intent,current.framing&&'Encuadre: '+current.framing,current.camera&&'Cámara: '+current.camera,current.dialogue&&'Diálogo: '+current.dialogue].filter(Boolean).join('\n');};
 el('flow-live-assets').onclick=async event=>{const button=event.target.closest('[data-flow-import]');if(!button)return;button.disabled=true;try{await importAsset(button.dataset.flowImport);}catch(error){status(error.message,'error');button.disabled=false;}};
 renderAssets();status(client.message);
}
root.NexusFlowLive={render,stop,connect};
// `render` in app.js is a global function binding; keep all existing views and their final decorators.
const previousRender=window.render;
window.render=function(){
 if(active()){applyTheme();document.querySelector('#content').classList.remove('canvas-view','studio-library');document.querySelector('#header-actions').replaceChildren();render();}else{if(client.socket||client.phase==='connecting')stop();previousRender();}
 const nav=document.querySelector('#navigation');if(nav&&!nav.querySelector('[data-nav="flow"]')){const button=document.createElement('button');button.dataset.nav='flow';button.textContent='Flow';nav.append(button);}const button=nav?.querySelector('[data-nav="flow"]');if(button){button.classList.toggle('active',active());if(active())button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');}
 if(active()){nav?.querySelectorAll('[data-nav]').forEach(item=>{if(item.dataset.nav!=='flow'){item.classList.remove('active');item.removeAttribute('aria-current');}});if(el('title'))el('title').textContent='Flow';if(el('eyebrow'))el('eyebrow').textContent='TU CUENTA · EN DIRECTO';}
};
window.addEventListener('pagehide',stop);
})(window);
