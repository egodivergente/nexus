'use strict';
(()=>{
 let projectId='',working=false;
 const references=()=> (Agent.snapshot?.index.assets||[]).filter(a=>a.projectId===projectId&&a.role==='REFERENCE');
 async function selectProject(id){projectId=id;if(id&&Agent.snapshot?.projectId!==id)await agentCall('project',{id});}
 function draw(){
  const projects=(Agent.snapshot?.index.projects||[]).filter(p=>!p.archived),files=references();
  modal(`<h2>Archivos de referencia</h2><label>Proyecto<select id="reference-project"><option value="">Elige un proyecto</option>${projects.map(p=>`<option value="${escape(p.id)}" ${projectId===p.id?'selected':''}>${escape(p.name)}</option>`).join('')}</select></label><div class="toolbar"><button type="button" id="reference-import" ${!projectId||working?'disabled':''}>${working?'Importando…':'Importar archivos'}</button><button type="button" id="reference-chat" ${!projectId||working?'disabled':''}>Abrir asistente</button></div><p class="hint">Importa una vez. Puedes pedir: «Usa Mateo face reference para esta escena».</p><div class="project-reference-grid">${files.map(a=>`<article class="project-reference">${a.type==='IMAGE'&&isMediaUrl(a.uri)?`<img src="${escape(a.uri)}" alt="${escape(a.displayName)}" loading="lazy">`:a.type==='VIDEO'&&isMediaUrl(a.uri)?`<video src="${escape(a.uri)}" controls preload="metadata"></video>`:`<span>${escape(a.type)}</span>`}<label>Nombre de referencia<input data-reference-name="${escape(a.id)}" value="${escape(a.displayName)}" maxlength="180" ${working?'disabled':''}></label></article>`).join('')||'<p class="hint">Añade fotos, vídeos o audio para reutilizarlos en este proyecto.</p>'}</div>`);
 }
 async function open(name){
  if(Agent.busy)throw Error('Espera a que termine el asistente.');
  await agentCall('snapshot');
  if(name){const existing=Agent.snapshot.index.projects.find(p=>p.name===name);if(existing)await selectProject(existing.id);else{await agentCall('createProject',{name});projectId=Agent.snapshot.projectId;}}
  else projectId=Agent.snapshot.projectId||'';
  draw();
 }
 async function importFiles(){
  if(!projectId||working)return;working=true;draw();
  try{const files=await rpc('pick');if(!files.length)return;await agentCall('importReferences',{projectId,files:files.map(f=>({uri:f.url,mime:f.mime,name:f.name.replace(/\.[^.]+$/,'')}))});toast(`${files.length} archivos disponibles en el proyecto.`);}
  finally{working=false;draw();}
 }
 document.addEventListener('click',async e=>{
  const b=e.target.closest('button');if(!b)return;
  try{
   if(b.hasAttribute('data-project-references'))await open();
   if(b.hasAttribute('data-project-reference-collection'))await open(b.dataset.projectReferenceCollection);
   if(b.id==='reference-import')await importFiles();
   if(b.id==='reference-chat'){await selectProject(projectId);$('#dialog').close();navigate('assistant');}
  }catch(error){toast(error.message);}
 });
 document.addEventListener('change',async e=>{
  try{if(e.target.id==='reference-project'){await selectProject(e.target.value);draw();}
   if(e.target.dataset.referenceName){const name=e.target.value.trim();if(!name){draw();return;}await agentCall('renameReference',{id:e.target.dataset.referenceName,projectId,name});toast('Nombre de referencia guardado.');}
  }catch(error){toast(error.message);draw();}
 });
 document.addEventListener('input',e=>{if(e.target.id==='agent-input'){state.agentConversation ||= {};state.agentConversation.draft=e.target.value;persist();}});
 const style=document.createElement('style');style.textContent='.project-reference-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:16px;margin-top:20px}.project-reference{min-width:0}.project-reference img,.project-reference video{width:100%;height:160px;object-fit:contain;background:#0b0d10;border-radius:var(--radius-md)}.project-reference input,#reference-project{width:100%;margin:8px 0}.project-reference label{font-size:var(--text-sm)}';document.head.append(style);
 window.NexusProjectReferences={open,importFiles,references};
})();
