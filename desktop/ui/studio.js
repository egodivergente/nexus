'use strict';
const reviewScript=document.createElement('script');
reviewScript.src='review.js';
document.head.append(reviewScript);
const Studio={tab:'results',detail:null,pickerQuery:'',pickerKind:'IMAGE',reference:null};
const openBeforeStudio=openModel;
openModel=async function(id,reset=true){
 if(loaded&&reset){state.modelValues={...state.modelValues,[loaded.id]:structuredClone(state.draft.values)};}
 const saved=reset&&id!==state.draft.modelId?state.modelValues?.[id]:null;
 await openBeforeStudio(id,reset);
 if(saved&&loaded?.id===id){state.draft.values={...E.defaults(loaded),...saved};quote=null;persist();renderCanvas();}
};
const renderBeforeStudio=render;
render=function(){
 $('#content').classList.remove('studio-library');
 renderBeforeStudio();
 $('#navigation').innerHTML=[['canvas','Crear'],['library','Biblioteca'],['assistant','Asistente']].map(([id,label])=>'<button data-nav="'+id+'" class="'+((id==='canvas'&&view==='catalog'||id==='library'&&view==='history'||view===id)?'active':'')+'">'+(id==='assistant'?aiIcon('chat'):icon(id))+label+'</button>').join('');
 $('#title').textContent={canvas:'Crear',catalog:'Modelos',library:'Biblioteca',history:'Biblioteca',assistant:'Asistente',settings:'Ajustes'}[view];
 $('#eyebrow').textContent=initial?.qa?'LABORATORIO · SIN GENERACIONES':'NEXUS STUDIO';
 if(view==='canvas')$('#header-actions').innerHTML='<button data-studio="picker">'+icon('catalog')+'Modelo</button>';
 if(view==='catalog')$('#header-actions').innerHTML='<button data-nav="canvas">&#8592; Crear</button>';
 if(!$('#studio-settings'))$('.sidebar-bottom').insertAdjacentHTML('beforebegin','<button id="studio-settings" data-nav="settings" title="Ajustes">'+icon('settings')+'Ajustes</button>');
};
const canvasBeforeStudio=renderCanvas;
renderCanvas=function(){
 canvasBeforeStudio();
 if(configuring)return;
 if(!loaded){
  $('#content').innerHTML='<div class="studio-start"><label for="prompt">Prompt</label><textarea id="prompt" placeholder="Describe lo que quieres crear">'+escape(state.draft.prompt)+'</textarea><button data-studio="picker" class="primary">'+icon('catalog')+'Elegir modelo</button></div>';
  return;
 }
 const selected=$('.canvas .selected');
 selected.insertAdjacentHTML('beforeend','<button class="small" data-studio="picker" title="Cambiar modelo">'+icon('catalog')+'</button>');
 const options=$('.canvas .sheet > .toolbar');
 if(options){const more=document.createElement('details');more.className='studio-more';more.innerHTML='<summary>Presets y herramientas</summary>';options.before(more);more.append(options);}
 document.querySelectorAll('.canvas .drop').forEach((el,i)=>el.dataset.field=loaded.fields[i].key);
 const preview=$('.canvas-right .preview');
 if(preview){const latest=state.jobs.find(j=>j.outputs?.length);preview.outerHTML=latest?'<div class="studio-latest">'+baseJobCard(latest)+'</div>':'<div class="studio-blank">'+icon('canvas')+'</div>';}
 const title=$('.canvas-right h2');if(title)title.textContent='Actividad reciente';
 const prompt=$('#prompt');
 if(prompt){prompt.insertAdjacentHTML('afterend','<small id="prompt-limits" class="hint" aria-live="polite"></small>');studioPromptLimits();}
};
function studioPromptLimits(){
 const node=$('#prompt-limits'),prompt=$('#prompt');if(!node||!prompt)return;
 const limit=loaded?.textLimits?.prompt,max=limit?.maximum,utf16=limit?.unit==='utf16_code_units',count=utf16?prompt.value.trim().length:Array.from(prompt.value.trim()).length;
 node.textContent=max==null?count+' caracteres · limite sin verificar':count+' / '+max+(utf16?' unidades UTF-16':' caracteres');
 node.classList.toggle('error',max!=null&&count>max);
 prompt.setAttribute('aria-describedby','prompt-limits');
 prompt.setAttribute('aria-invalid',String(max!=null&&count>max));
}
document.addEventListener('input',e=>{if(e.target.id==='prompt')studioPromptLimits();});
function studioPicker(){
 Studio.pickerQuery='';Studio.pickerKind=loaded?.modalities[0]||'IMAGE';
 modal('<h2>Modelo</h2><div class="studio-picker-toolbar"><input id="studio-model-search" type="search" placeholder="Buscar modelo o proveedor" aria-label="Buscar modelo"><select id="studio-model-kind" aria-label="Formato">'+pcOptions([['IMAGE','Imagen'],['VIDEO','Video'],['AUDIO','Audio'],['CHAT','Texto']],Studio.pickerKind)+'</select></div><div id="studio-model-list"></div><button data-studio="catalog">Catalogo completo</button>');
 studioModelList();$('#studio-model-search').focus();
}
function studioModelList(){
 const q=Studio.pickerQuery.toLocaleLowerCase();
 const list=models.filter(m=>m.modalities.includes(Studio.pickerKind)&&!state.hidden.includes(m.id)&&(!q||[m.displayName,m.id,providers[m.providerId]].join(' ').toLocaleLowerCase().includes(q)))
 .sort((a,b)=>Number(state.favorites.includes(b.id))-Number(state.favorites.includes(a.id))||Number(state.recent.includes(b.id))-Number(state.recent.includes(a.id)));
 $('#studio-model-list').innerHTML=list.slice(0,50).map(m=>'<button class="studio-model-row" data-studio-model="'+escape(m.id)+'">'+logo(m)+'<span><strong>'+escape(m.displayName)+'</strong><small>'+escape(providers[m.providerId])+' · '+escape(description(m))+'</small></span><span class="studio-price">'+(m.basePriceUsd!=null?'Base '+money(m.basePriceUsd):'Consultar coste')+'</span></button>').join('')||'<p>Sin coincidencias</p>';
}
const pcJobsBeforeStudio=pcJobs;
pcJobs=function(){const previous=view;view=Studio.tab==='activity'?'history':'library';try{return pcJobsBeforeStudio();}finally{view=previous;}};
const pcBeforeStudio=renderPC;
renderPC=function(){
 pcBeforeStudio();
 $('#content').classList.add('studio-library');
 $('#content').insertAdjacentHTML('afterbegin','<div class="tabs studio-tabs"><button data-studio-tab="results" class="'+(Studio.tab==='results'?'active':'')+'">Resultados</button><button data-studio-tab="activity" class="'+(Studio.tab==='activity'?'active':'')+'">Actividad</button></div>');
 if(Studio.tab==='activity'&&!$('[data-action=refreshJobs]'))$('.summary .toolbar').insertAdjacentHTML('beforeend','<button data-action="refreshJobs">Actualizar estados</button>');
 if(Studio.detail)studioDetails(Studio.detail);
};
renderLibrary=renderPC;renderHistory=function(){Studio.tab='activity';renderPC();};
const jobBeforeStudio=jobCard;
jobCard=function(job){
 const html=jobBeforeStudio(job);
 if(!['library','history'].includes(view))return html;
 const template=document.createElement('template');template.innerHTML=html;
 const card=template.content.firstElementChild,body=card.querySelector('.body');
 const title=body.querySelector('h3')?.textContent||job.name||job.modelId;
 body.innerHTML='<button class="studio-detail-button" data-studio-detail="'+escape(job.id)+'" title="Abrir detalles"><span>'+escape(title)+'</span><small>'+escape({COMPLETED:'Completado',PROCESSING:'Procesando',FAILED:'Revisar envio',QUEUED:'En cola'}[job.status]||job.status)+'</small></button>';
 const output=job.outputs?.[outputSelection.get(job.id)||0];
 if(output)body.insertAdjacentHTML('beforeend','<button class="small studio-compare" data-pc-compare="'+escape(job.id)+'" aria-label="Seleccionar para comparar" aria-pressed="'+PC.selected.has(job.id+':'+(outputSelection.get(job.id)||0))+'" title="Seleccionar para comparar">'+(PC.selected.has(job.id+':'+(outputSelection.get(job.id)||0))?'&#10003;':'+')+'</button>');
 return template.innerHTML;
};
function studioDetails(id){
 Studio.detail=id;document.querySelector('.studio-details')?.remove();
 const job=state.jobs.find(j=>j.id===id);if(!job)return;
 const index=outputSelection.get(id)||0,output=job.outputs?.[index],url=typeof output==='string'?output:output?.url;
 const panel=document.createElement('aside');panel.className='studio-details';panel.setAttribute('aria-label','Detalles del resultado');
 panel.innerHTML='<button class="studio-detail-close" data-studio="close-detail" aria-label="Cerrar detalles" title="Cerrar detalles">&#215;</button><h2>'+escape(job.name||job.modelId)+'</h2><p class="hint">'+escape(providers[job.providerId]||job.providerId)+' · '+escape(job.status)+'</p><h3>Prompt</h3><p class="studio-prompt">'+escape(job.prompt)+'</p><div class="studio-detail-actions"><button data-reuse="'+escape(id)+'">Reutilizar ajustes</button>'+(url&&isMediaUrl(url)?'<button '+(url.startsWith('/media/')?'data-flow-save':'data-download')+'="'+escape(url)+'">Guardar</button>':'')+(job.modality==='IMAGE'&&url&&isMediaUrl(url)?'<button data-studio-reference="'+escape(id)+'">Usar como referencia</button>':'')+'</div><details><summary>Parametros</summary><dl>'+Object.entries(job.parameters||{}).map(([k,v])=>'<dt>'+escape(E.label(k))+'</dt><dd>'+escape(typeof v==='object'?JSON.stringify(v):v)+'</dd>').join('')+'</dl></details>'+(job.error?'<p class="error">'+escape(job.error)+'</p>':'');
 $('#content').append(panel);
}
async function studioAttach(files,fieldKey){
 if(Studio.importing)throw Error('Espera a que termine la importacion.');
 if(!loaded)throw Error('Elige primero un modelo.');
 const field=loaded.fields.find(f=>f.key===fieldKey)||loaded.fields.find(f=>files.every(file=>file.type.startsWith(f.mime.split('/')[0]+'/')));
 if(!field)throw Error('Este modelo no admite estos archivos.');
 if(files.length+state.draft.media.filter(f=>f.field===field.key).length>(field.maximum||1))throw Error('Se supera el numero de referencias admitidas.');
 if(files.some(f=>!f.type.startsWith(field.mime.split('/')[0]+'/')))throw Error('El formato no corresponde a este campo.');
 if(files.some(f=>f.size>12*1024*1024))throw Error('Para archivos de mas de 12 MB usa el boton de adjuntar.');
 if(!initial.browserImport)throw Error('Reabre Nexus para activar pegar y arrastrar archivos.');
 Studio.importing=true;
 try{
 const modelId=loaded.id,imports=[];
 for(const file of files){
  const content=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.onerror=reject;reader.readAsDataURL(file);});
  const item=await rpc('importMedia',{name:file.name,mime:file.type,content});
  if(file.type.startsWith('video/')||file.type.startsWith('audio/')){
   item.duration=await new Promise(resolve=>{
    const media=document.createElement(file.type.startsWith('video/')?'video':'audio');
    const done=value=>{clearTimeout(timer);media.removeAttribute('src');media.load();resolve(value);};
    const timer=setTimeout(()=>done(null),6000);media.onloadedmetadata=()=>done(Number.isFinite(media.duration)?media.duration:null);media.onerror=()=>done(null);media.preload='metadata';media.src=item.url;
   });
  }
  imports.push({...item,field:field.key});
 }
 if(loaded?.id!==modelId)throw Error('El modelo ha cambiado. Vuelve a adjuntar los archivos.');
 state.draft.media.push(...imports);quote=null;persist();renderCanvas();
 }finally{Studio.importing=false;}
}
document.addEventListener('paste',e=>{
 const files=[...(e.clipboardData?.files||[])];if(view!=='canvas'||!files.length)return;
 e.preventDefault();studioAttach(files,e.target.closest('[data-field]')?.dataset.field).catch(error=>toast(error.message));
});
document.addEventListener('dragover',e=>{if(view==='canvas'&&e.dataTransfer?.types.includes('Files'))e.preventDefault();});
document.addEventListener('drop',e=>{
 if(view!=='canvas'||!e.dataTransfer?.files.length)return;e.preventDefault();
 studioAttach([...e.dataTransfer.files],e.target.closest('[data-field]')?.dataset.field).catch(error=>toast(error.message));
});
document.addEventListener('input',e=>{if(e.target.id==='studio-model-search'){Studio.pickerQuery=e.target.value;studioModelList();}});
document.addEventListener('change',e=>{if(e.target.id==='studio-model-kind'){Studio.pickerKind=e.target.value;studioModelList();}});
document.addEventListener('click',async e=>{
 const b=e.target.closest('button');if(!b||b.disabled)return;
 try{
  if(b.dataset.flowSave){const link=document.createElement('a');link.href=b.dataset.flowSave;link.download=b.dataset.flowSave.split('/').pop();link.click();}
  if(b.dataset.action==='close')Studio.reference=null;
  if(b.dataset.studio==='picker'){Studio.reference=null;studioPicker();}
  if(b.dataset.studio==='catalog'){$('#dialog').close();navigate('catalog');}
  if(b.dataset.studioModel){
   $('#dialog').close();await openModel(b.dataset.studioModel);
   if(Studio.reference&&loaded){
    const field=loaded.fields.find(f=>f.mime.startsWith('image/'));if(!field)throw Error('Elige un modelo que admita referencias de imagen.');
    if(state.draft.media.filter(f=>f.field===field.key).length>=(field.maximum||1))throw Error('El campo de referencias esta completo.');
    state.draft.media.push({...Studio.reference,field:field.key});Studio.reference=null;quote=null;persist();renderCanvas();
   }
  }
  if(b.dataset.studioTab){Studio.tab=b.dataset.studioTab;PC.limit=36;view='library';render();}
  if(b.dataset.studioDetail)studioDetails(b.dataset.studioDetail);
  if(b.dataset.studio==='close-detail'){Studio.detail=null;$('.studio-details')?.remove();}
  if(b.dataset.studioReference){
   const job=state.jobs.find(j=>j.id===b.dataset.studioReference),output=job.outputs[outputSelection.get(job.id)||0],url=typeof output==='string'?output:output.url;
   const local=url.startsWith('/media/');
   Studio.reference={id:local?url.split('/').pop():crypto.randomUUID(),name:job.name||'Resultado',mime:/\.jpe?g$/.test(url)?'image/jpeg':/\.webp$/.test(url)?'image/webp':'image/png',size:0,url,remoteUrl:local?null:url};
   navigate('canvas');studioPicker();
  }
 }catch(error){toast(error.message);}
});
