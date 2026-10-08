'use strict';
// Presentation layer: retain original controls, schema validation and dispatch handlers.
(()=>{
 let scheduled=false,dockExpanded=false;
 let trayKey='';
 const el=(tag,cls,html='')=>{const n=document.createElement(tag);n.className=cls;n.innerHTML=html;return n;};
 const text=(n,s)=>{if(n.textContent!==s)n.textContent=s;};
 function shell(){
  document.body.classList.add('creative-engine');
  document.body.classList.toggle('creative-dock-expanded',dockExpanded);
  if(!$('#creative-dock-toggle')){
   const b=el('button','creative-dock-toggle','☰');b.id='creative-dock-toggle';b.type='button';b.setAttribute('aria-label','Expandir navegación');b.setAttribute('aria-expanded','false');
   b.onclick=()=>{dockExpanded=!dockExpanded;document.body.classList.toggle('creative-dock-expanded',dockExpanded);b.setAttribute('aria-expanded',String(dockExpanded));b.setAttribute('aria-label',dockExpanded?'Contraer navegación':'Expandir navegación');};$('.sidebar .brand')?.after(b);
  }
  document.querySelectorAll('.sidebar button[data-nav],#studio-settings').forEach(b=>{
   if(b.dataset.creativeLabel)return;
   const label=b.textContent.trim();b.dataset.creativeLabel=label;b.title=label;b.setAttribute('aria-label',label);
   const span=el('span','creative-nav-label');[...b.childNodes].filter(n=>n.nodeType===3).forEach(n=>span.append(n));b.append(span);
  });
  if(!$('#creative-status')){
   const bar=el('footer','creative-status');bar.id='creative-status';bar.innerHTML='<details id="creative-control"><summary aria-label="Abrir estado, saldos y telemetría"><span class="creative-health"></span><span id="creative-status-label">Conectando</span><span class="creative-status-chevron">⌃</span></summary><div class="creative-control-panel"><h2>Estado y consumo</h2><p id="creative-local-status"></p><div id="creative-control-actions"></div></div></details><button type="button" data-gallery="commands" class="creative-shortcut" title="Paleta de comandos">Ctrl K</button>';$('main').append(bar);
  }
  const panel=$('.creative-control-panel'),actions=$('#creative-control-actions');
  const balances=$('#account-balances');if(balances&&balances.parentElement!==panel)panel.insertBefore(balances,actions);
  ['telemetry-button','flow-control-button','order-latest','gallery-activity'].forEach(id=>{const b=$('#'+id);if(b&&b.parentElement!==actions)actions.append(b);});
  const count=initial?.configured?.length||0;
  text($('#creative-status-label'),initial?.qa?'Laboratorio local':`Multi-Cloud · ${count} motores configurados`);
  text($('#creative-local-status'),$('#connection')?.textContent||'Estado local pendiente');
  document.body.classList.toggle('creative-canvas',view==='canvas');
 }
 function canvas(){
  if(view!=='canvas'||configuring||$('#creative-command'))return;
  const source=$('.canvas .sheet')||$('.studio-start'),prompt=$('#prompt');if(!source||!prompt)return;
  const canvasRoot=$('.canvas');if(canvasRoot)canvasRoot.classList.add('creative-layout');
  const command=el('section','creative-command');command.id='creative-command';command.setAttribute('aria-label','Crear con tus modelos');
  const heading=el('div','creative-heading','<h2>¿Qué quieres crear?</h2><span>Imagen, movimiento, posibilidades.</span>');
  const input=el('div','creative-input');prompt.setAttribute('aria-label','Describe tu creación');prompt.placeholder='Describe una escena. Añade tus referencias. Dale forma.';input.append(prompt);
  const chips=el('div','creative-chips');
  const motor=el('button','creative-motor');motor.type='button';motor.dataset.studio='picker';motor.textContent=loaded?loaded.displayName:'Elegir motor';motor.title='Elegir modelo';chips.append(motor);
  const extras=el('details','creative-parameters','<summary>Parámetros y herramientas</summary><div class="creative-parameter-body"></div>');
  const parameterBody=extras.lastElementChild;
  source.querySelectorAll('[data-param]').forEach(control=>{
   const field=control.closest('.field');if(!field)return;
   const p=loaded?.parameters.find(p=>p.key===control.dataset.param);
   // Promote schema fields, never invent aspect ratios, resolutions or durations.
   const quick=control.tagName==='SELECT'&&/aspect|ratio|resolution|duration/i.test(p?.key||'');
   (quick?chips:parameterBody).append(field);if(quick)field.classList.add('creative-chip');
  });
  const references=el('div','creative-references');source.querySelectorAll('.drop').forEach(field=>{
   const add=field.querySelector('[data-pick]');if(add){add.classList.add('creative-reference-action');add.title=field.querySelector('label')?.textContent||'Añadir referencia';add.textContent='+ '+add.title;chips.append(add);}
   if(!field.querySelector('.file'))field.classList.add('creative-reference-empty');references.append(field);
  });
  const limits=$('#prompt-limits');if(limits)input.append(limits);
  source.querySelectorAll('.studio-more').forEach(n=>parameterBody.append(n));
  const send=$('.canvas .sendbar')||el('div','sendbar');
  const action=el('div','creative-split');
  let generate=$('#generate');if(!generate){generate=el('button','primary','Elegir motor');generate.type='button';generate.dataset.studio='picker';}
  action.append(generate);
  const multi=el('details','creative-multi','<summary aria-label="Opciones de generación">⌄</summary><div><button type="button" data-action="chooseCompare">Multi-Run · varios modelos</button><small>Prompt y ajustes propios por modelo</small></div>');action.append(multi);
  source.querySelectorAll('[data-action="chooseCompare"]').forEach(n=>n.remove());
  const footer=el('div','creative-command-footer');footer.append(send,action);
  command.append(heading,input,chips,references);if(parameterBody.children.length)command.append(extras);command.append(footer);
  source.replaceWith(command);
  if(canvasRoot){canvasRoot.querySelector('.canvas-right')?.remove();canvasRoot.append(tray());}
  else command.after(tray());
 }
 function tray(){
  trayKey=productionKey();
  const wrap=el('section','creative-production');wrap.setAttribute('aria-label','Producción reciente');
  wrap.innerHTML='<div class="creative-tray-heading"><h2>Producción reciente</h2><button type="button" data-nav="library">Ver biblioteca ↗</button></div><div class="creative-tray"></div>';
  const list=wrap.lastElementChild;
  const jobs=productionJobs();
  jobs.forEach(j=>{
   const output=j.outputs?.[0],url=typeof output==='string'?output:output?.url,valid=isMediaUrl(url),completed=j.status==='COMPLETED';
   const piece=el('button','creative-piece');piece.type='button';piece.dataset.creativeJob=j.id;piece.title=j.name||j.modelId;piece.setAttribute('aria-label','Abrir '+piece.title);
   piece.innerHTML=valid?(j.modality==='VIDEO'?`<video src="${escape(url)}" muted playsinline preload="none"></video><span>▷</span>`:`<img src="${escape(url)}" alt="${escape(piece.title)}" loading="lazy" decoding="async">`):`<span>${escape(j.status==='FAILED'?'Fallido':'En proceso')}</span>`;
   if(valid&&completed&&j.modality==='IMAGE'){piece.draggable=true;piece.addEventListener('dragstart',e=>{e.dataTransfer.setData('application/x-nexus-job',j.id);e.dataTransfer.effectAllowed='copy';});piece.title+=' · Arrastra para usar como referencia';}
   list.append(piece);
  });
  if(!jobs.length)list.innerHTML='<p class="creative-tray-empty">Tu próxima creación empieza arriba. Aquí aparecerán tus resultados.</p>';
  return wrap;
 }
 const productionJobs=()=>state.jobs.filter(j=>['IMAGE','VIDEO'].includes(j.modality)&&!state.studioGallery?.hidden?.includes(j.id)).slice(0,12);
 const productionKey=()=>JSON.stringify(productionJobs().map(j=>[j.id,j.status,j.outputs]));
 function attach(id,fieldKey){
  const job=state.jobs.find(j=>j.id===id),output=job?.outputs?.[0],url=typeof output==='string'?output:output?.url;
  if(!loaded||busy||job?.status!=='COMPLETED'||job.modality!=='IMAGE'||!isMediaUrl(url))return toast('Elige un motor compatible con referencias de imagen.');
  const fields=loaded.fields.filter(f=>f.mime.startsWith('image'));
  const field=fieldKey?fields.find(f=>f.key===fieldKey):fields.length===1?fields[0]:null;
  if(!field)return toast(fields.length?'Suelta la imagen sobre la entrada de referencia que quieras usar.':'Este modelo no admite referencias de imagen.');
  if(state.draft.media.filter(m=>m.field===field.key).length>=(field.maximum||1))return toast('Esta entrada ya tiene el máximo de referencias.');
  const extension=new URL(url,location.href).pathname.split('.').pop().toLowerCase(),mime=({jpg:'image/jpeg',jpeg:'image/jpeg',webp:'image/webp',avif:'image/avif',gif:'image/gif'})[extension]||'image/png';
  state.draft.media.push({id:crypto.randomUUID(),name:job.name||'Referencia',mime,size:0,url,remoteUrl:url.startsWith('/media/')?null:url,field:field.key});quote=null;persist();renderCanvas();
 }
 document.addEventListener('dragover',e=>{if(e.target.closest('#creative-command')&&e.dataTransfer?.types.includes('application/x-nexus-job')){e.preventDefault();e.dataTransfer.dropEffect='copy';}});
 document.addEventListener('drop',e=>{if(!e.target.closest('#creative-command'))return;const id=e.dataTransfer?.getData('application/x-nexus-job');if(id){e.preventDefault();attach(id,e.target.closest('[data-field]')?.dataset.field);}});
 document.addEventListener('keydown',e=>{
  if(e.target.id==='prompt'&&e.key==='Enter'&&!e.shiftKey&&!e.ctrlKey&&!e.metaKey&&!e.altKey&&!e.isComposing&&!e.repeat){
   e.preventDefault();const b=$('#generate');if(b&&!b.disabled&&!$('#dialog').open)b.click();
  }
  if(e.key==='Escape'){document.querySelectorAll('#creative-control,.creative-multi').forEach(n=>n.open=false);}
 });
 document.addEventListener('click',e=>{
  const b=e.target.closest('[data-creative-job]');if(b)studioDetails(b.dataset.creativeJob);
  if(!e.target.closest('.creative-multi'))document.querySelectorAll('.creative-multi[open]').forEach(n=>n.open=false);
 });
 function mount(){scheduled=false;shell();canvas();$('#creative-command')?.classList.toggle('is-processing',busy);
  const current=$('.creative-production'),key=productionKey();
  if(view==='canvas'&&current&&key!==trayKey)current.replaceWith(tray());
 }
 function schedule(){if(!scheduled){scheduled=true;setTimeout(mount,0);}}
 new MutationObserver(schedule).observe($('main'),{childList:true,subtree:true});
 new MutationObserver(schedule).observe($('#navigation'),{childList:true});
 const previousResponse=parseResponse;parseResponse=function(...args){const result=previousResponse(...args);schedule();return result;};
 window.NexusCreative={version:1,attach};mount();
})();
