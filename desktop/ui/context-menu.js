'use strict';
// Menú contextual (clic derecho) integrado. No duplica lógica: cada entrada
// dispara los mismos controles (data-*) que ya existen en la interfaz o usa el
// portapapeles. Mayús + clic derecho conserva el menú nativo del sistema.
(()=>{
 const T=(typeof toast==='function')?toast:()=>{};
 const jobOf=id=>(typeof state!=='undefined'?state.jobs:[]).find(j=>j.id===id);
 const urlOf=job=>(job?.outputs||[]).map(o=>typeof o==='string'?o:o?.url).filter(Boolean)[0]||'';
 // Dispara un control existente por su atributo data-* sin añadirlo al diseño.
 const fire=(dataset)=>{
  const real=document.querySelector(Object.entries(dataset).map(([k,v])=>`[data-${k.replace(/[A-Z]/g,c=>'-'+c.toLowerCase())}="${CSS.escape(v)}"]`).join(''));
  if(real&&!real.disabled){real.click();return;}
  const b=document.createElement('button');b.hidden=true;Object.assign(b.dataset,dataset);document.body.append(b);b.click();b.remove();
 };
 const copy=async(text,ok)=>{try{await navigator.clipboard.writeText(text);T(ok||'Copiado.');}catch(e){T('No se pudo copiar.');}};
 const go=view=>()=>fire({nav:view});
 const NAV=[['assistant','Asistente','◌'],['canvas','Lienzo','✦'],['catalog','Modelos','▦'],['history','Historial','◷'],['library','Biblioteca','▣'],['settings','Ajustes','⚙']];
 const navItems=()=>NAV.map(([id,label,icon])=>({label:'Ir a '+label,icon,run:go(id)}));

 function jobItems(id){
  const job=jobOf(id);if(!job)return [];
  const done=job.status==='COMPLETED',url=urlOf(job);
  return [
   {title:(job.prompt||'Creación').slice(0,60)},
   done&&{label:'Abrir',icon:'↗',run:()=>fire({studioDetail:id})},
   {label:'Preparar de nuevo',icon:'↻',run:()=>fire({reuse:id})},
   {label:'Copiar prompt',icon:'⧉',disabled:!job.prompt,run:()=>copy(job.prompt,'Prompt copiado.')},
   url&&{label:'Copiar enlace del resultado',icon:'⛓',run:()=>copy(new URL(url,location.href).href,'Enlace copiado.')},
   url&&{label:'Abrir en pestaña nueva',icon:'⧉',run:()=>window.open(url,'_blank','noopener')},
   {sep:true},
   done&&{label:'Favorito',icon:'☆',run:()=>fire({galleryFavorite:id})},
   done&&{label:'Seleccionar',icon:'✓',run:()=>fire({gallerySelect:id})},
   {label:'Consultar estado',icon:'⟳',run:()=>fire({parityRecover:id})},
   {label:'Detalles y diagnóstico',icon:'ⓘ',run:()=>fire({parityJob:id})},
  ];
 }
 function modelItems(id){
  const m=(typeof models!=='undefined'?models:[]).find(x=>x.id===id);
  return [
   {title:m?.name||'Modelo'},
   {label:'Abrir modelo',icon:'↗',run:()=>fire({model:id})},
   {label:'Marcar como favorito',icon:'☆',run:()=>fire({star:id})},
   {label:'Ocultar',icon:'⊘',run:()=>fire({hide:id})},
   {label:'Copiar nombre',icon:'⧉',run:()=>copy(m?.name||id,'Nombre copiado.')},
  ];
 }
 function messageItems(node){
  const text=node.querySelector('.agent-text')?.innerText||'';
  return [
   {title:'Mensaje'},
   {label:'Copiar mensaje',icon:'⧉',run:()=>copy(text,'Mensaje copiado.')},
   {label:'Reutilizar como mensaje nuevo',icon:'↺',run:()=>{const i=document.querySelector('#agent-input');if(i){i.value=text;i.dispatchEvent(new Event('input',{bubbles:true}));i.focus();}}},
  ];
 }
 function editableItems(el){
  const has=!!(el.selectionEnd-el.selectionStart);
  return [
   {label:'Cortar',icon:'✂',disabled:!has||el.readOnly,kbd:'Ctrl+X',run:async()=>{await copy(el.value.slice(el.selectionStart,el.selectionEnd),'Cortado.');el.setRangeText('',el.selectionStart,el.selectionEnd,'end');el.dispatchEvent(new Event('input',{bubbles:true}));}},
   {label:'Copiar',icon:'⧉',disabled:!has,kbd:'Ctrl+C',run:()=>copy(el.value.slice(el.selectionStart,el.selectionEnd))},
   {label:'Pegar',icon:'⎘',disabled:el.readOnly,kbd:'Ctrl+V',run:async()=>{try{const t=await navigator.clipboard.readText();el.focus();el.setRangeText(t,el.selectionStart,el.selectionEnd,'end');el.dispatchEvent(new Event('input',{bubbles:true}));}catch(e){T('Permite el acceso al portapapeles o usa Ctrl+V.');}}},
   {label:'Seleccionar todo',icon:'▤',kbd:'Ctrl+A',run:()=>{el.focus();el.select();}},
  ];
 }
 function generalItems(){
  const sel=String(getSelection()||'').trim();
  return [
   sel&&{label:'Copiar selección',icon:'⧉',kbd:'Ctrl+C',run:()=>copy(sel)},
   sel&&{label:'Usar selección como prompt en el Lienzo',icon:'✦',run:()=>{try{state.draft.prompt=sel;persist();}catch(e){}fire({nav:'canvas'});}},
   sel&&{sep:true},
   ...navItems(),
   {sep:true},
   {label:'Paleta de comandos',icon:'⌘',kbd:'Ctrl+K',run:()=>fire({gallery:'commands'})},
   {label:'Actualizar saldos',icon:'⟳',run:()=>fire({refreshBalances:''})},
   {label:'Plegar / mostrar navegación',icon:'◧',run:()=>document.querySelector('button[aria-label="Contraer navegación"],button[aria-label="Expandir navegación"]')?.click()},
  ];
 }

 function itemsFor(target){
  if(target.closest('#nx-context-menu'))return null;
  const editable=target.closest('textarea,input:not([type=range]):not([type=checkbox]):not([type=radio]):not([type=button]),[contenteditable=""],[contenteditable=true]');
  if(editable&&'value' in editable)return editableItems(editable);
  const job=target.closest('[data-job]');if(job)return jobItems(job.dataset.job);
  const parityJob=target.closest('[data-parity-job]');if(parityJob)return jobItems(parityJob.dataset.parityJob);
  const piece=target.closest('[data-creative-job]');if(piece)return jobItems(piece.dataset.creativeJob);
  const model=target.closest('[data-model]:not(nav *)');if(model&&model.dataset.model)return modelItems(model.dataset.model);
  const nav=target.closest('nav [data-nav],[data-nav]');if(nav&&nav.closest('#navigation'))return [{label:'Abrir '+nav.textContent.trim(),icon:'↗',run:()=>nav.click()},{sep:true},...generalItems().filter(i=>!/^Ir a/.test(i.label||''))];
  const msg=target.closest('.agent-message');if(msg)return [...messageItems(msg),{sep:true},...generalItems()];
  if(target.closest('dialog[open]'))return null;
  return generalItems();
 }

 let menu=null,returnFocus=null;
 const close=(restore=true)=>{if(!menu)return;menu.remove();menu=null;if(restore&&returnFocus?.isConnected)returnFocus.focus({preventScroll:true});returnFocus=null;};
 function open(x,y,items){
  close(false);returnFocus=document.activeElement;
  menu=document.createElement('div');menu.id='nx-context-menu';menu.setAttribute('role','menu');
  for(const it of items.filter(Boolean)){
   if(it.sep){const s=document.createElement('div');s.className='nx-ctx-sep';s.setAttribute('role','separator');menu.append(s);continue;}
   if(it.title){const t=document.createElement('div');t.className='nx-ctx-title';t.textContent=it.title;menu.append(t);continue;}
   const b=document.createElement('button');b.type='button';b.setAttribute('role','menuitem');b.disabled=!!it.disabled;
   b.innerHTML='<span class="nx-ctx-icon" aria-hidden="true"></span><span class="nx-ctx-label"></span>'+(it.kbd?'<kbd></kbd>':'');
   b.querySelector('.nx-ctx-icon').textContent=it.icon||'';b.querySelector('.nx-ctx-label').textContent=it.label;if(it.kbd)b.querySelector('kbd').textContent=it.kbd;
   b.addEventListener('click',async e=>{e.stopPropagation();close(false);try{await it.run();}catch(error){T(error?.message||'No se pudo completar la acción.');}});
   menu.append(b);
  }
  document.body.append(menu);
  const r=menu.getBoundingClientRect();
  menu.style.left=Math.max(8,Math.min(x,innerWidth-r.width-8))+'px';menu.style.top=Math.max(8,Math.min(y,innerHeight-r.height-8))+'px';
  menu.querySelector('button:not([disabled])')?.focus({preventScroll:true});
 }
 document.addEventListener('contextmenu',e=>{
  if(e.shiftKey)return;                      // menú nativo a demanda
  const items=itemsFor(e.target);if(!items)return;
  e.preventDefault();
  const rect=e.clientX||e.clientY?null:e.target.getBoundingClientRect();
  open(rect?rect.left+12:e.clientX,rect?rect.top+12:e.clientY,items);
 },true);
 document.addEventListener('keydown',e=>{
  if(!menu)return;
  const buttons=[...menu.querySelectorAll('button:not([disabled])')],i=buttons.indexOf(document.activeElement);
  if(e.key==='Escape'){e.preventDefault();close();}
  else if(e.key==='ArrowDown'){e.preventDefault();buttons[(i+1)%buttons.length]?.focus();}
  else if(e.key==='ArrowUp'){e.preventDefault();buttons[(i-1+buttons.length)%buttons.length]?.focus();}
  else if(e.key==='Home'){e.preventDefault();buttons[0]?.focus();}
  else if(e.key==='End'){e.preventDefault();buttons.at(-1)?.focus();}
  else if(e.key==='Tab'){close();}
 },true);
 document.addEventListener('pointerdown',e=>{if(menu&&!menu.contains(e.target))close(false);},true);
 window.addEventListener('blur',()=>close(false));
 window.addEventListener('resize',()=>close(false));
 // Solo el desplazamiento del usuario cierra el menú: los scroll programáticos
 // (re-pintados de la galería, foco) no deben cerrarlo nada más abrirlo.
 document.addEventListener('wheel',e=>{if(menu&&!menu.contains(e.target))close(false);},{capture:true,passive:true});
 document.addEventListener('touchmove',e=>{if(menu&&!menu.contains(e.target))close(false);},{capture:true,passive:true});
 window.NexusContextMenu={open,close};
})();
