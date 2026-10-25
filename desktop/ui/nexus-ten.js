'use strict';
/* NEXUS 10 · final presentation layer.
   Recomposes every view (hierarchy, vocabulary, navigation and states) on top of the existing
   renderers. Each control keeps its original data-* attribute, so the delegated handlers, the cost
   confirmation and the dispatch paths stay exactly as they were. Nothing here sends a request. */
(()=>{
if(window.NexusTen||typeof render!=='function')return;
const T={version:'10.0',intent:'',filtersOpen:false,frame:0,lastView:null};
const has=(m,c)=>(m.capabilities||[]).includes(c);
const words=m=>[m.id,m.displayName,m.modelFamily,m.family,m.providerModelId].join(' ');
const svg=(body,extra='')=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"${extra}>${body}</svg>`;
const glyph={
 image:svg('<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><circle cx="9" cy="10" r="1.6"/><path d="m4.5 17 4.8-4.6 3.4 3.2 2.6-2.4 4.2 3.8"/>'),
 video:svg('<rect x="3.5" y="5.5" width="12.5" height="13" rx="2.5"/><path d="m16 10.2 4.5-2.7v9l-4.5-2.7"/>'),
 audio:svg('<path d="M5 10v4M9 7v10M13 4.5v15M17 8v8M21 10.5v3"/>'),
 chat:svg('<path d="M4.5 5.5h15v10h-8l-4.5 3.5v-3.5h-2.5z"/>'),
 tools:svg('<path d="M14.5 6.5a4 4 0 0 0-5.3 5.1L4.5 16.3a1.8 1.8 0 0 0 2.6 2.6l4.7-4.7a4 4 0 0 0 5.1-5.3l-2.4 2.4-2.3-.4-.4-2.3z"/>'),
 filter:svg('<path d="M4 7h16M7 12h10M10 17h4"/>'),
 hide:svg('<path d="M3.5 12s3-5.5 8.5-5.5c1.6 0 3 .5 4.2 1.1M20.5 12s-3 5.5-8.5 5.5c-1.6 0-3-.5-4.2-1.1"/><path d="M4.5 19.5 19.5 4.5"/>'),
 plus:svg('<path d="M12 5.5v13M5.5 12h13"/>'),
 check:svg('<path d="m5.5 12.5 4.2 4 8.8-9"/>'),
 more:'<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="6" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="18" cy="12" r="1.6"/></svg>',
 close:svg('<path d="m7 7 10 10M17 7 7 17"/>'),
 star:svg('<path d="m12 4.6 2.3 4.6 5.1.8-3.7 3.6.9 5.1-4.6-2.4-4.6 2.4.9-5.1-3.7-3.6 5.1-.8z"/>'),
 starOn:svg('<path d="m12 4.6 2.3 4.6 5.1.8-3.7 3.6.9 5.1-4.6-2.4-4.6 2.4.9-5.1-3.7-3.6 5.1-.8z" fill="currentColor"/>')
};
const kindGlyph=k=>({VIDEO:glyph.video,AUDIO:glyph.audio,CHAT:glyph.chat}[k]||glyph.image);
function setText(node,text){if(node&&node.textContent!==text)node.textContent=text;}

/* ---------- Vocabulary, header and navigation ---------- */
const VIEWS={
 assistant:['Asistente','Planifica y prepara tus creaciones conversando'],
 canvas:['Crear','Describe tu idea, elige un modelo y revisa el coste antes de generar'],
 catalog:['Modelos','Elige por lo que quieres conseguir'],
 history:['Historial','Todas tus generaciones, su estado y su coste'],
 library:['Biblioteca','Tus resultados, listos para reutilizar'],
 settings:['Ajustes','Apariencia, proveedores, agente y datos']
};
const TOOLS=[
 ['editor','Edición local','Recorta, une y extrae audio o fotogramas en este PC'],
 ['media-jobs','Encargos locales','Sigue las ediciones que se procesan aquí'],
 ['whisper','Whisper','Transcribe audio y vídeo sin salir del equipo'],
 ['alexia','Alexia S25','Abre Alexia en tu móvil conectado']
];
function header(){
 const [title,subtitle]=VIEWS[view]||['Nexus Studio',''];
 setText($('#title'),title);
 const eyebrow=$('#eyebrow');
 if(eyebrow){eyebrow.classList.add('nx-subtitle');setText(eyebrow,(initial?.qa?'Vista de prueba · ':'')+subtitle);}
 const actions=$('#header-actions');if(!actions)return;
 const back=actions.querySelector(':scope > [data-nav="canvas"]:not(.nx-header-primary)');
 if(back&&view==='catalog')setText(back,'Ir a Crear');
 const wantsPrimary=(view==='library'||view==='history')&&state.jobs.length>0;
 const primary=actions.querySelector('.nx-header-primary');
 if(wantsPrimary&&!primary)actions.insertAdjacentHTML('afterbegin',`<button type="button" class="primary nx-header-primary" data-nav="canvas">${glyph.plus}<span>Crear</span></button>`);
 else if(!wantsPrimary&&primary)primary.remove();
 if(!actions.querySelector('#nx-tools')){
  actions.insertAdjacentHTML('beforeend',`<details id="nx-tools" class="nx-menu nx-tools"><summary title="Herramientas locales">${glyph.tools}<span>Herramientas</span></summary><div class="nx-menu-panel" role="group" aria-label="Herramientas locales">${TOOLS.map(([id,label,hint])=>`<button type="button" class="nx-menu-item" data-parity="${id}"><strong>${label}</strong><small>${hint}</small></button>`).join('')}</div></details>`);
 }
 const chip=$('#parity-balance-chip');
 if(chip){chip.classList.add('nx-balance');if(chip.parentElement===actions&&actions.lastElementChild!==chip)actions.append(chip);}
}
function sidebar(){
 const settings=$('#studio-settings');
 if(settings){const label=settings.querySelector('.creative-nav-label');if(label)setText(label,'Ajustes');}
 document.querySelectorAll('#navigation [data-nav="canvas"]').forEach(b=>{
  const label=b.querySelector('.creative-nav-label');if(label)setText(label,'Crear');
  if(b.getAttribute('aria-label')!=='Crear')b.setAttribute('aria-label','Crear');
  if(b.title!=='Crear')b.title='Crear';
  if(b.dataset.creativeLabel!=='Crear')b.dataset.creativeLabel='Crear';
 });
}

/* ---------- Crear: one composition with the preview as protagonist ---------- */
const mediaOf=j=>{const o=j?.outputs?.[0];return typeof o==='string'?o:o?.url;};
const activeJob=j=>['SENDING','QUEUED','PROCESSING','GENERATING'].includes(j?.status);
const stageJob=()=>state.jobs.find(j=>['IMAGE','VIDEO'].includes(j.modality)&&!state.studioGallery?.hidden?.includes(j.id));
const stageKey=()=>{const j=stageJob();return JSON.stringify([j?.id,j?.status,mediaOf(j),busy,!!loaded]);};
function stageMarkup(){
 const j=stageJob(),url=mediaOf(j),label=j?(j.name||models.find(m=>m.id===j.modelId)?.displayName||j.modelId):'';
 if(busy&&!j)return `<div class="nx-stage-frame is-working" role="status"><span class="loading"></span><strong>Enviando la petición…</strong><span>Espera la confirmación del proveedor.</span></div>`;
 if(!j)return `<div class="nx-stage-frame is-empty"><img class="nx-stage-logo" src="branding/nexus-logo.png" alt="Logo oficial de NEXUS" width="88" height="84"><strong>Tu creación aparecerá aquí</strong><span>${loaded?'Describe la escena, revisa el coste y pulsa Generar.':'Elige un modelo, describe la escena y revisa el coste.'} El resultado se mostrará en este espacio y quedará en tu biblioteca.</span></div>`;
 if(activeJob(j))return `<div class="nx-stage-frame is-working" role="status"><span class="loading"></span><strong>${j.status==='SENDING'?'Enviando la petición…':'Generando con '+escape(label)}</strong><span>${escape(String(j.prompt||'').slice(0,140))}</span><small>El estado se actualiza solo. Puedes seguir trabajando.</small></div>`;
 if(j.status==='FAILED')return `<div class="nx-stage-frame is-error" role="status"><strong>No se completó la última creación</strong><span>${escape(String(j.error||'El proveedor no completó la tarea.').slice(0,180))}</span><button type="button" data-creative-job="${escape(j.id)}">Ver detalles</button></div>`;
 const visual=url&&isMediaUrl(url)?(j.modality==='VIDEO'?`<video src="${escape(url)}" muted playsinline loop preload="metadata" aria-label="${escape(label)}"></video>`:`<img src="${escape(url)}" alt="${escape(label)}" decoding="async">`):'<span class="nx-stage-missing">Resultado sin vista previa</span>';
 return `<button type="button" class="nx-stage-frame is-result" data-creative-job="${escape(j.id)}" aria-label="Abrir ${escape(label)}">${visual}<span class="nx-stage-caption"><strong>${escape(label)}</strong><small>Última creación · abrir detalles</small></span></button>`;
}
function create(){
 const content=$('#content');if(!content)return;
 if(view!=='canvas'){content.classList.remove('nx-create-view');return;}
 const command=$('#creative-command');if(!command)return;
 content.classList.add('nx-create-view');
 const root=command.closest('.canvas')||content;
 let grid=root.querySelector(':scope > .nx-create-grid');
 if(!grid){
  grid=document.createElement('div');grid.className='nx-create-grid';
  grid.innerHTML='<div class="nx-create-main"></div><aside class="nx-create-stage" aria-label="Vista previa y producción"></aside>';
  root.prepend(grid);
 }
 const main=grid.firstElementChild,stage=grid.lastElementChild;
 // Guide, notices and the composer read as one column; results own the wide side.
 const loose=[...content.children,...(root!==content?[...root.children]:[])].filter(n=>n!==grid&&n!==root&&!n.contains(grid));
 for(const node of loose)(node.matches('.creative-production')?stage:main).append(node);
 if(command.parentElement!==main)main.append(command);
 const guide=$('#studio-start-guide');if(guide&&main.firstElementChild!==guide)main.prepend(guide);
 let preview=stage.querySelector(':scope > .nx-stage');
 if(!preview){preview=document.createElement('section');preview.className='nx-stage';preview.setAttribute('aria-label','Vista previa');stage.prepend(preview);}
 const key=stageKey();if(preview.dataset.key!==key){preview.dataset.key=key;preview.innerHTML=stageMarkup();}
 const production=stage.querySelector('.creative-production');
 if(production){
  setText(production.querySelector('.creative-tray-heading h2'),'Recientes');
  setText(production.querySelector('.creative-tray-heading [data-nav="library"]'),'Ver biblioteca');
  setText(production.querySelector('.creative-tray-empty'),'Aquí verás tus últimas imágenes y vídeos.');
 }
 // Composer: a persistent label, the model as an explicit choice and a stated reason when sending is not possible.
 const input=command.querySelector('.creative-input');
 if(input&&!input.querySelector('.nx-field-label'))input.insertAdjacentHTML('afterbegin','<label class="nx-field-label" for="prompt">Describe tu creación</label>');
 const prompt=$('#prompt'),placeholder='Escena, estilo, luz, encuadre o movimiento…';
 if(prompt&&prompt.placeholder!==placeholder)prompt.placeholder=placeholder;
 const motor=command.querySelector('.creative-motor');
 if(motor){motor.classList.toggle('is-empty',!loaded);if(!loaded)setText(motor,'Elegir modelo');}
 const refs=command.querySelector('.creative-references');
 if(refs&&loaded)refs.querySelector('.nx-ref-hint')?.remove();
 if(refs&&!loaded&&!refs.querySelector('.nx-ref-hint'))refs.insertAdjacentHTML('beforeend','<p class="nx-ref-hint">Referencias: al elegir un modelo verás qué imágenes, vídeos o audios admite.</p>');
 const footer=command.querySelector('.creative-command-footer'),generate=$('#generate');
 let reason=command.querySelector('#nx-generate-reason');
 if(footer&&generate&&!reason){reason=document.createElement('p');reason.id='nx-generate-reason';reason.className='nx-generate-reason';reason.setAttribute('aria-live','polite');footer.append(reason);}
 const multi=command.querySelector('.creative-multi [data-action="chooseCompare"]');if(multi&&multi.textContent!=='Varios modelos a la vez')multi.textContent='Varios modelos a la vez';
 if(reason&&generate){
  // #validation already explains itself beside Generar; the reason line only covers what nothing else says.
  const validation=$('#validation')?.textContent.trim();
  const why=busy?'Enviando… espera la confirmación del proveedor.':!loaded?'Elige un modelo para poder generar.':generate.disabled?(validation?'':'Vuelve a consultar el coste para poder generar.'):'';
  setText(reason,why);reason.hidden=!why;
  if(generate.getAttribute('aria-describedby')!=='nx-generate-reason')generate.setAttribute('aria-describedby','nx-generate-reason');
 }
 if(guide&&!guide.dataset.ten){
  guide.dataset.ten='1';guide.classList.add('nx-guide');
  setText(guide.querySelector('.nx-guide-brand strong'),'Primeros pasos');
  guide.querySelector('.nx-guide-brand img')?.setAttribute('hidden','');
  guide.querySelectorAll('.nx-version-badge').forEach(n=>n.remove());
 }
}

/* ---------- Modelos: intents first, a calmer grid and honest prices ---------- */
const utility=m=>/background|upscal|inpaint|outpaint|expand|watermark|coloriz|face[-_ ]?swap|eras(e|er)|matting/i.test(words(m));
const INTENTS={
 IMAGE:[
  ['generate','Generar imagen','Desde una descripción',m=>has(m,'TEXT_TO_IMAGE')&&!utility(m)],
  ['edit','Editar con instrucciones','Cambia una imagen existente',m=>(has(m,'IMAGE_EDITING')||has(m,'IMAGE_TO_IMAGE'))&&!utility(m)],
  ['background','Eliminar fondo','Recorta el sujeto',m=>/background[-_ ]?remov|remove[-_ ]?(bg|background)|rembg|matting/i.test(words(m))],
  ['upscale','Ampliar resolución','Más tamaño y detalle',m=>has(m,'UPSCALE')||/upscal/i.test(words(m))],
  ['retouch','Retocar o expandir','Rellena zonas, amplía el encuadre',m=>/inpaint|outpaint|expand|watermark|coloriz|face[-_ ]?swap|eras(e|er)/i.test(words(m))],
  ['3d','Crear en 3D','Desde texto o imagen',m=>has(m,'IMAGE_TO_3D')||has(m,'TEXT_TO_3D')]
 ],
 VIDEO:[
  ['t2v','Vídeo desde texto','Describe escena y movimiento',m=>has(m,'TEXT_TO_VIDEO')],
  ['i2v','Animar una imagen','Da movimiento a un fotograma',m=>has(m,'IMAGE_TO_VIDEO')],
  ['ref','Vídeo con referencias','Mantén personajes u objetos',m=>has(m,'REFERENCE_TO_VIDEO')],
  ['v2v','Transformar un vídeo','Cambia estilo o contenido',m=>has(m,'VIDEO_TO_VIDEO')||has(m,'VIDEO_EDIT')],
  ['extend','Alargar un vídeo','Continúa un clip',m=>has(m,'VIDEO_EXTEND')],
  ['motion','Transferir movimiento','De un vídeo a un personaje',m=>has(m,'MOTION_CONTROL')],
  ['sound','Vídeo desde audio','Sincroniza con voz o música',m=>has(m,'AUDIO_TO_VIDEO')]
 ],
 AUDIO:[
  ['tts','Voz desde texto','Narración y locución',m=>has(m,'TEXT_TO_SPEECH')],
  ['music','Música y efectos','Pistas y sonidos',m=>has(m,'TEXT_TO_AUDIO')],
  ['stt','Transcribir','De voz a texto',m=>has(m,'SPEECH_TO_TEXT')]
 ],
 CHAT:[]
};
const intentOf=id=>(INTENTS[kind]||[]).find(i=>i[0]===id);
const baseFiltered=filteredModels;
filteredModels=function(){
 const list=baseFiltered();
 if(!T.intent)return list;
 const intent=intentOf(T.intent);if(!intent){T.intent='';return list;}
 return list.filter(intent[3]);
};
const SORTS=[['recommended','Recomendados'],['new','Más recientes'],['price','Precio más bajo'],['name','Nombre']];
const DENSITY=[['compact','Compacto'],['comfortable','Cómodo'],['spacious','Amplio']];
function catalogMenu(){
 const active=[provider,sort!=='recommended',onlyFavorites,onlyDated].filter(Boolean).length;
 const hiddenCount=window.CatalogParity?CatalogParity.hiddenEntries(state,models,family).total:(state.hidden||[]).length;
 const density=window.CatalogParity?CatalogParity.densityOf(state):(state.compact?'compact':'comfortable');
 const seg=(items,attr,current)=>`<div class="nx-seg">${items.map(([id,label])=>`<button type="button" ${attr}="${escape(id)}" aria-pressed="${current===id}">${escape(label)}</button>`).join('')}</div>`;
 return `<details class="nx-menu nx-filter-menu"${T.filtersOpen?' open':''}><summary>${glyph.filter}<span>Filtros</span>${active?`<span class="nx-count" aria-label="${active} activos">${active}</span>`:''}</summary><div class="nx-menu-panel nx-filter-panel" role="group" aria-label="Filtros y vista">`+
  `<fieldset><legend>Proveedor</legend>${seg([['','Todos'],...Object.entries(providers)],'data-nx-provider',provider)}</fieldset>`+
  `<fieldset><legend>Orden</legend>${seg(SORTS,'data-nx-sort',sort)}</fieldset>`+
  `<fieldset><legend>Mostrar</legend><div class="nx-seg"><button type="button" data-action="favorites" aria-pressed="${onlyFavorites}">Solo favoritos</button><button type="button" data-action="dated" aria-pressed="${onlyDated}">Con fecha de lanzamiento</button><button type="button" data-action="hidden" aria-pressed="${showHidden}">${showHidden?'Volver al catálogo':'Modelos ocultos'+(hiddenCount?' · '+hiddenCount:'')}</button></div></fieldset>`+
  `<fieldset><legend>Tamaño de las tarjetas</legend>${seg(DENSITY,'data-catalog-density',density)}</fieldset>`+
  `</div></details>`;
}
function activeChips(){
 const chips=[];
 if(T.intent&&intentOf(T.intent))chips.push(['intent','Objetivo: '+intentOf(T.intent)[1]]);
 if(familyFilter)chips.push(['family','Familia: '+familyFilter]);
 if(query)chips.push(['query',`Búsqueda: «${query}»`]);
 if(provider)chips.push(['provider','Proveedor: '+(providers[provider]||provider)]);
 if(sort!=='recommended')chips.push(['sort','Orden: '+(SORTS.find(s=>s[0]===sort)?.[1]||sort)]);
 if(onlyFavorites)chips.push(['favorites','Solo favoritos']);
 if(onlyDated)chips.push(['dated','Con fecha de lanzamiento']);
 if(!chips.length)return '';
 return `<div class="nx-active-filters" role="group" aria-label="Filtros activos">${chips.map(([id,label])=>`<button type="button" class="nx-chip" data-nx-clear="${id}" aria-label="Quitar ${escape(label)}"><span>${escape(label)}</span>${glyph.close}</button>`).join('')}${chips.length>1?'<button type="button" class="nx-chip-clear" data-nx-clear="all">Limpiar todo</button>':''}</div>`;
}
function intentsMarkup(){
 const list=INTENTS[kind]||[];if(!list.length||showHidden)return '';
 const pool=baseFiltered();
 const items=list.map(([id,label,hint,test])=>[id,label,hint,pool.filter(test).length]).filter(i=>i[3]>0||i[0]===T.intent);
 if(!items.length)return '';
 return `<section class="nx-intents" aria-labelledby="nx-intents-title"><h2 id="nx-intents-title">¿Qué quieres hacer?</h2><div class="nx-intent-row">${items.map(([id,label,hint,count])=>`<button type="button" class="nx-intent" data-nx-intent="${id}" aria-pressed="${T.intent===id}"><span class="nx-intent-icon">${kindGlyph(kind)}</span><span class="nx-intent-text"><strong>${escape(label)}</strong><small>${escape(hint)}</small></span><span class="nx-intent-count" title="${count} modelos disponibles">${count}</span></button>`).join('')}</div></section>`;
}
function variantsOf(card){
 if(card.dataset.cardModel){const m=models.find(x=>x.id===card.dataset.cardModel);return m?[m]:[];}
 const name=card.dataset.cardFamily,intent=T.intent&&intentOf(T.intent);
 const list=models.filter(m=>m.modalities?.includes(kind)&&family(m)===name&&!(state.hidden||[]).includes(m.id));
 return intent?list.filter(intent[3]):list;
}
// Remote examples reveal the user's IP to the provider CDN: opt-out in Ajustes, never in offline QA unless asked.
const examplesOn=()=>typeof initial!=='undefined'&&initial&&initial.qa?state.catalogExamples===true:state.catalogExamples!==false;
const exampleOf=list=>list.map(m=>m.coverUrl).find(u=>typeof u==='string'&&/^https:\/\//.test(u)&&/\.(png|jpe?g|webp|avif)(\?|$)/i.test(u))||'';
function bestResolution(list){
 const score=r=>{const s=String(r).toLowerCase();if(/8k/.test(s))return 8000;if(/4k|2160/.test(s))return 4000;if(/2k|1440/.test(s))return 2000;if(/1080/.test(s))return 1080;if(/720/.test(s))return 720;const m=s.match(/(\d{3,4})x(\d{3,4})/);return m?Math.max(+m[1],+m[2]):0;};
 const best=Math.max(0,...list.flatMap(m=>m.supportedResolutions||[]).map(score));
 return best>=8000?'Hasta 8K':best>=3840||best===4000?'Hasta 4K':best>=2000?'Hasta 2K':best>=1080?'Hasta 1080p':best>=720?'Hasta 720p':'';
}
function traits(list,k=kind){
 const out=[],caps=new Set(list.flatMap(m=>m.capabilities||[])),any=key=>list.some(m=>m[key]);
 if(k==='IMAGE'){
  if(caps.has('TEXT_TO_IMAGE')&&(caps.has('IMAGE_EDITING')||caps.has('IMAGE_TO_IMAGE')))out.push('Crea y edita');
  else if(caps.has('IMAGE_EDITING')||caps.has('IMAGE_TO_IMAGE'))out.push('Edita tus imágenes');
  else if(caps.has('TEXT_TO_IMAGE'))out.push('Desde texto');
  if(caps.has('IMAGE_TO_3D')||caps.has('TEXT_TO_3D'))out.push('Modelo 3D');
 }
 if(k==='VIDEO'){
  const modes=[caps.has('TEXT_TO_VIDEO')&&'texto',caps.has('IMAGE_TO_VIDEO')&&'imagen',(caps.has('VIDEO_TO_VIDEO')||caps.has('VIDEO_EDIT'))&&'vídeo'].filter(Boolean);
  if(modes.length)out.push('Desde '+(modes.length>1?modes.slice(0,-1).join(', ')+' o '+modes.at(-1):modes[0]));
  if(any('supportsFirstFrame')&&any('supportsLastFrame'))out.push('Inicio y final');
  if(any('supportsAudio'))out.push('Con audio');
 }
 if(k==='AUDIO'){if(caps.has('TEXT_TO_SPEECH'))out.push('Voz');if(caps.has('TEXT_TO_AUDIO'))out.push('Música y efectos');if(caps.has('SPEECH_TO_TEXT'))out.push('Transcripción');}
 if(k==='IMAGE'||k==='VIDEO'){const refs=Math.max(0,...list.map(m=>Number(m.maxImages)||0));if(refs>1)out.push(`Hasta ${refs} referencias`);else if(refs===1||any('supportsImageInput'))out.push('1 referencia');}
 const durations=list.flatMap(m=>m.supportedDurations||[]).map(Number).filter(n=>Number.isFinite(n)&&n>0);
 if(k==='VIDEO'&&durations.length){const lo=Math.min(...durations),hi=Math.max(...durations);out.push(lo===hi?`${lo} s`:`${lo}–${hi} s`);}
 const res=bestResolution(list);if(res)out.push(res);
 return [...new Set(out)].slice(0,3);
}
const NOISE=/contrato|openapi|endpoint|schema|esquema|\bapi\b/i;
function cards(){
 const grid=$('#content .catalog');if(!grid)return;
 const all=[...grid.querySelectorAll('article.model')];
 const counts=new Map();all.forEach(c=>{const t=c.querySelector('.description')?.textContent.trim();if(t)counts.set(t,(counts.get(t)||0)+1);});
 for(const card of all){
  if(card.dataset.ten)continue;card.dataset.ten='1';
  const list=variantsOf(card),top=card.querySelector('.top');
  // A neutral mark when the brand has no logo; real logos are kept untouched.
  const mark=card.querySelector('.top > .logo, .top > .initial');
  if(mark){const box=document.createElement('span');box.className='nx-mark';mark.replaceWith(box);box.append(mark);if(mark.classList.contains('initial')){mark.innerHTML=kindGlyph(kind);mark.setAttribute('aria-hidden','true');box.classList.add('is-neutral');}}
  if(kind!=='CHAT'){
   const example=examplesOn()?exampleOf(list):'',logo=card.querySelector('.nx-mark img.logo');
   const media=document.createElement('div');media.className='nx-card-media';media.dataset.hasExample=example?'1':'0';
   media.innerHTML=`<span class="nx-card-fallback" aria-hidden="true">${logo?`<img src="${escape(logo.getAttribute('src'))}" alt="">`:kindGlyph(kind)}</span>${example?`<img class="nx-example" src="${escape(example)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer"><span class="nx-example-tag">Ejemplo del proveedor</span>`:''}`;
   card.prepend(media);
   const shown=media.querySelector('img.nx-example');if(shown?.complete&&shown.naturalWidth)media.classList.add('is-loaded');
  }
  const description=card.querySelector('.description'),text=description?.textContent.trim()||'';
  if(description&&(!text||counts.get(text)>=3||NOISE.test(text)))description.hidden=true;
  const facts=traits(list);
  if(facts.length)(description||top)?.insertAdjacentHTML('afterend',`<ul class="nx-traits" title="Según la ficha del catálogo">${facts.map(f=>`<li>${escape(f)}</li>`).join('')}</ul>`);
  const price=card.querySelector('.price');
  if(price&&['unknown','unconfirmed'].includes(price.dataset.priceState)){
   const button=document.createElement('button');button.type='button';button.className='price nx-price-link';button.dataset.priceState=price.dataset.priceState;button.dataset.nxOpenCard='';
   button.title='La tarifa depende de las opciones. Nexus la consulta al configurar, antes de generar.';button.textContent='Ver estimación';price.replaceWith(button);
  }
  const compare=card.querySelector('[data-compare]');
  if(compare){const on=selection.has(compare.dataset.compare),label=on?'Quitar de la comparativa':'Añadir a la comparativa';compare.innerHTML=on?glyph.check:glyph.plus;compare.setAttribute('aria-label',label);compare.title=label;compare.classList.add('nx-icon-btn');}
  const hide=card.querySelector('[data-hide-family],[data-hide]');
  if(hide&&!showHidden){const label=hide.dataset.hideFamily!=null?'Ocultar familia':'Ocultar modelo';hide.innerHTML=glyph.hide;hide.setAttribute('aria-label',label);hide.title=label;hide.classList.add('nx-icon-btn');}
  const star=card.querySelector('.star');if(star){const on=star.classList.contains('on');star.classList.add('nx-icon-btn');star.setAttribute('aria-label',on?'Quitar de favoritos':'Marcar como favorito');star.setAttribute('aria-pressed',String(on));star.innerHTML=on?glyph.starOn:glyph.star;}
 }
}
function catalog(){
 if(view!=='catalog')return;
 const head=$('#content .catalog-header-bar');if(!head)return;
 if(!head.dataset.ten){
  head.dataset.ten='1';head.classList.add('nx-catalog-head');
  const search=head.querySelector('#search');
  if(search){search.placeholder='Buscar por modelo, familia o proveedor';search.insertAdjacentHTML('afterend',catalogMenu());}
  head.insertAdjacentHTML('beforeend',intentsMarkup()+activeChips());
 }
 // Filters live in the «Filtros» menu; the original controls stay mounted (renderCatalog reads #sort) but hidden.
 head.querySelectorAll('.catalog-filters > select, .catalog-filters > .select-trigger, .catalog-filters > [data-action="favorites"], .catalog-filters > [data-action="dated"], .catalog-summary > .catalog-density, .catalog-summary > [data-action="hidden"]').forEach(n=>n.classList.add('nx-orig'));
 cards();
 const empty=$('#content .catalog ~ .empty');
 if(empty&&!empty.dataset.ten){empty.dataset.ten='1';empty.classList.add('nx-empty');empty.innerHTML='<div class="nx-empty-art is-search" aria-hidden="true"><span></span><span></span><span></span></div><h2>No hay modelos para esta búsqueda</h2><p>Prueba con otro término o quita algún filtro. El catálogo completo sigue disponible.</p><div class="nx-empty-actions"><button type="button" class="primary" data-nx-clear="all">Limpiar filtros</button></div>';}
}
const catalogBefore=renderCatalog;
renderCatalog=function(){catalogBefore();try{catalog();}catch(error){console.warn('Nexus 10 · Modelos:',error);}};

/* ---------- Asistente: one reading column, clear action levels ---------- */
const MODES={ASSISTANT:'Recomienda y explica. No prepara ni cambia nada.',COPILOT:'Prepara modelo, prompt y opciones. Tú decides cuándo enviar.',AGENT:'Ejecuta y encadena los pasos que pidas. Fuera de Flow, el coste se revisa antes de enviar.'};
const MORE=[['[data-agent="project-new"]','Nuevo proyecto','Agrupa conversaciones, referencias y memoria'],['[data-parity="project"]','Memoria del proyecto','Lo que Nexus recuerda de este proyecto'],['[data-project-references]','Referencias','Imágenes y archivos del proyecto'],['[data-parity="media-jobs"]','Ediciones locales','Encargos procesados en este PC']];
function assistant(){
 if(view!=='assistant')return;
 const bar=$('.agent-toolbar');
 if(bar&&!bar.dataset.ten){
  bar.dataset.ten='1';
  const tabs=bar.querySelector(':scope > .tabs');
  if(tabs){
   tabs.querySelectorAll('[data-agent-mode]').forEach(b=>{b.title=MODES[b.dataset.agentMode]||'';});
   const mode=tabs.querySelector('.active')?.dataset.agentMode||Agent.snapshot?.mode;
   const wrap=document.createElement('div');wrap.className='nx-mode';tabs.replaceWith(wrap);wrap.append(tabs);
   wrap.insertAdjacentHTML('beforeend',`<p class="nx-mode-hint">${escape(MODES[mode]||'Elige cómo quieres que trabaje el asistente.')}</p>`);
  }
  const project=bar.querySelector('.agent-project');
  if(project){
   const fresh=project.querySelector('[data-agent="new"]');
   if(fresh){fresh.classList.add('primary','nx-primary-action');fresh.classList.remove('nx-toolbar-btn');setText(fresh,'Nueva charla');}
   project.querySelector('[data-parity="conversations"]')?.classList.add('nx-secondary');
   const more=document.createElement('details');more.className='nx-menu nx-more';
   more.innerHTML=`<summary title="Más opciones del proyecto">${glyph.more}<span>Más</span></summary><div class="nx-menu-panel" role="group" aria-label="Más opciones del proyecto"></div>`;
   const panel=more.lastElementChild;
   for(const [selector,label,hint] of MORE){const b=project.querySelector(selector);if(!b)continue;b.classList.remove('nx-toolbar-btn');b.classList.add('nx-menu-item');b.innerHTML=`<strong>${label}</strong><small>${hint}</small>`;b.setAttribute('aria-label',label);b.removeAttribute('title');panel.append(b);}
   if(panel.children.length){if(fresh)fresh.before(more);else project.append(more);}
  }
 }
 const info=$('#agent-compose .agent-session-info');
 if(info&&!info.dataset.ten){
  info.dataset.ten='1';
  const extras=[...info.children].filter(n=>!n.matches('[data-agent="settings"]'));
  if(extras.length){
   const details=document.createElement('details');details.className='nx-menu nx-session-more';
   details.innerHTML='<summary title="Skills y herramientas de esta sesión">Detalles</summary><div class="nx-menu-panel" role="group" aria-label="Detalles de la sesión"><p class="nx-menu-note">Lo que el asistente puede usar en esta conversación.</p></div>';
   extras.forEach(n=>details.lastElementChild.append(n));info.append(details);
  }
 }
}

/* ---------- Historial y Biblioteca: wide surfaces and real starting points ---------- */
const ART='<div class="nx-empty-art" aria-hidden="true"><span></span><span></span><span></span></div>';
function emptyMarkup(kindOf){
 if(kindOf==='first')return `<section class="nx-empty" data-empty="first">${ART}<h2>Todavía no has creado nada</h2><p>Cuando generes una imagen, un vídeo o un audio, aparecerá aquí con su estado y su coste.</p><div class="nx-empty-actions"><button type="button" class="primary" data-nav="canvas">Crear primera imagen</button><button type="button" data-nav="assistant">Pedir ideas al asistente</button></div></section>`;
 return `<section class="nx-empty" data-empty="filtered">${ART.replace('nx-empty-art','nx-empty-art is-search')}<h2>No hay resultados para esta búsqueda</h2><p>Ninguna generación coincide con los filtros actuales. Tus creaciones siguen guardadas.</p><div class="nx-empty-actions"><button type="button" class="primary" data-nx-clear-history>Limpiar filtros</button></div></section>`;
}
function history(){
 const content=$('#content');if(!content)return;
 content.classList.toggle('nx-history',view==='history');
 if(view!=='history')return;
 const list=content.querySelector('.history-list');if(!list)return;
 if(list.children.length===1&&list.firstElementChild.matches('p.hint'))list.innerHTML=emptyMarkup(state.jobs.length?'filtered':'first');
}
function library(){
 if(view!=='library')return;
 const empty=$('#content > .empty');
 if(!empty||empty.querySelector('.nx-empty-art'))return;
 if(!state.jobs.length){empty.outerHTML=emptyMarkup('first');return;}
 empty.classList.add('nx-empty');empty.dataset.empty='filtered';empty.insertAdjacentHTML('afterbegin',ART.replace('nx-empty-art','nx-empty-art is-search'));
 setText(empty.querySelector('h2'),'No hay resultados para esta búsqueda');
 const reset=empty.querySelector('button');if(reset){setText(reset,'Limpiar filtros');reset.classList.add('primary');}
 if(!empty.querySelector('.nx-empty-actions')&&reset){const actions=document.createElement('div');actions.className='nx-empty-actions';reset.replaceWith(actions);actions.append(reset);}
}

/* ---------- Ajustes: categories and aligned rows ---------- */
const SECTIONS=[['appearance','Apariencia','Tema, tamaño del texto y moneda'],['providers','Proveedores','Conexiones y saldos de cada servicio'],['agent','Agente','Modelo conversacional y herramientas'],['data','Datos','Lo que se guarda en este equipo'],['about','Acerca de','Versión y ayuda']];
const AGENT_ROWS={settings:['Modelo y conexión','El modelo conversacional y cómo se conecta: Bedrock directo o proxy.','Configurar'],whisper:['Edición y Whisper','Edición local de vídeo y audio y transcripción en este PC.','Abrir'],internet:['Internet y herramientas MCP','Búsqueda web y conexiones MCP que el agente puede usar.','Gestionar'],diagnostics:['Diagnóstico del sistema','Estado de los servicios locales y de las conexiones.','Ver diagnóstico']};
const rowMarkup=(title,detail='')=>`<div class="nx-row"><div class="nx-row-text"><strong>${title}</strong>${detail?`<small>${detail}</small>`:''}</div><div class="nx-row-controls"></div></div>`;
function makeRow(title,detail,...controls){const holder=document.createElement('div');holder.innerHTML=rowMarkup(title,detail);const r=holder.firstElementChild;r.lastElementChild.append(...controls.filter(Boolean));return r;}
function settings(){
 if(view!=='settings')return;
 const host=$('#settings-container');if(!host)return;
 let shell=host.querySelector(':scope > .nx-settings');
 const loose=[...host.children].filter(n=>n!==shell);
 if(!shell){
  host.classList.add('nx-settings-host');
  shell=document.createElement('div');shell.className='nx-settings';
  shell.innerHTML=`<nav class="nx-settings-index" aria-label="Secciones de ajustes">${SECTIONS.map(([id,label])=>`<button type="button" data-nx-settings-jump="${id}">${label}</button>`).join('')}</nav><div class="nx-settings-body">${SECTIONS.map(([id,label,hint])=>`<section class="nx-settings-section" id="nx-settings-${id}" aria-labelledby="nx-settings-${id}-title"><header><h2 id="nx-settings-${id}-title" tabindex="-1">${label}</h2><p>${hint}</p></header><div class="nx-rows"></div></section>`).join('')}<div class="nx-settings-originals" hidden></div></div>`;
  host.prepend(shell);
  const version=document.createElement('span');version.className='nx-value';version.dataset.nxVersion='';version.textContent=typeof desktopVersionLabel==='function'?desktopVersionLabel():'';
  const guide=document.createElement('button');guide.type='button';guide.dataset.studioHelp='toggle';guide.textContent='Mostrar guía';
  const examples=document.createElement('button');examples.type='button';examples.dataset.nxExamples='';
  shell.querySelector('#nx-settings-data .nx-rows').append(makeRow('Ejemplos en Modelos','Imágenes de muestra publicadas por cada proveedor. Se descargan desde sus servidores.',examples));
  shell.querySelector('#nx-settings-about .nx-rows').append(makeRow('Versión','Compilación instalada en este equipo.',version),makeRow('Guía de primeros pasos','Vuelve a mostrar los tres pasos en Crear.',guide));
 }
 const toggle=shell.querySelector('[data-nx-examples]');if(toggle){const on=examplesOn();toggle.setAttribute('aria-pressed',String(on));setText(toggle,on?'Activados':'Desactivados');}
 if(!loose.length)return;
 const rows=id=>shell.querySelector(`#nx-settings-${id} .nx-rows`),originals=shell.querySelector('.nx-settings-originals');
 for(const node of loose){
  if(node.querySelector('.account')){
   node.querySelectorAll('.account').forEach(account=>{
    account.classList.add('nx-row','nx-account');
    const info=account.firstElementChild;info?.classList.add('nx-row-text');
    const controls=document.createElement('div');controls.className='nx-row-controls';
    [...account.children].filter(n=>n!==info).forEach(n=>{if(n.matches('span.hint')){n.classList.remove('hint');n.classList.add('nx-status');}controls.append(n);});
    account.append(controls);rows('providers').append(account);
   });
   node.remove();continue;
  }
  if(node.querySelector('.interface-themes')){
   const r=makeRow('Tema','Se aplica al instante en toda la aplicación.');r.classList.add('nx-row-block');r.lastElementChild.remove();r.append(node.querySelector('.interface-themes'));rows('appearance').prepend(r);node.remove();continue;
  }
  if(node.id==='parity-settings'){
   node.querySelectorAll('button').forEach(b=>{const meta=AGENT_ROWS[b.dataset.agent||b.dataset.parity];if(!meta)return;setText(b,meta[2]);rows('agent').append(makeRow(meta[0],meta[1],b));});
   node.replaceChildren();originals.append(node);continue;
  }
  if(node.querySelector('#parity-font')){
   const font=node.querySelector('#parity-font'),money=node.querySelector('#parity-currency'),rate=node.querySelector('#parity-rate'),update=node.querySelector('[data-parity="exchange-rate"]');
   font?.setAttribute('aria-label','Tamaño del texto');money?.setAttribute('aria-label','Moneda');if(update)setText(update,'Actualizar cambio');
   const currency=makeRow('Moneda','Los importes se muestran en esta moneda.',money,update);if(rate){rate.classList.remove('hint');rate.classList.add('nx-row-note');currency.firstElementChild.append(rate);}
   rows('appearance').append(makeRow('Tamaño del texto','Aumenta el texto de toda la interfaz.',font),currency);node.remove();continue;
  }
  const open=node.querySelector('[data-action="openData"]');
  if(open){setText(open,'Abrir carpeta');rows('data').append(makeRow('Datos locales',escape(node.querySelector('.hint')?.textContent||''),open));node.remove();continue;}
  rows('data').append(node);
 }
}

/* ---------- Global wiring ---------- */
function versionNoise(){document.querySelectorAll('.brand .nx-version-badge,#studio-start-guide .nx-version-badge').forEach(n=>n.remove());}
function enhance(){
 T.frame=0;
 try{
  document.body.classList.add('nx-ten');
  if(document.body.dataset.tenView!==view)document.body.dataset.tenView=view;
  if(T.lastView!==view){T.lastView=view;document.querySelectorAll('.nx-menu[open]').forEach(d=>{d.open=false;});}
  header();sidebar();versionNoise();create();catalog();assistant();history();library();settings();
 }catch(error){console.warn('Nexus 10:',error);}
}
function schedule(){if(!T.frame)T.frame=requestAnimationFrame(enhance);}
const renderBefore=render;render=function(){renderBefore();enhance();};
new MutationObserver(schedule).observe(document.body,{childList:true,subtree:true});
window.addEventListener('nexus:canvas-status',schedule);

const closeMenu=d=>{d.open=false;if(d.classList.contains('nx-filter-menu'))T.filtersOpen=false;};
document.addEventListener('toggle',e=>{
 const d=e.target;if(!(d instanceof HTMLDetailsElement)||!d.classList.contains('nx-menu'))return;
 if(d.classList.contains('nx-filter-menu'))T.filtersOpen=d.open;
 if(d.open)document.querySelectorAll('.nx-menu[open]').forEach(o=>{if(o!==d)o.open=false;});
},true);
document.addEventListener('load',e=>{const img=e.target;if(img instanceof HTMLImageElement&&img.classList.contains('nx-example'))img.closest('.nx-card-media')?.classList.add('is-loaded');},true);
document.addEventListener('error',e=>{const img=e.target;if(img instanceof HTMLImageElement&&img.classList.contains('nx-example')){const media=img.closest('.nx-card-media');if(media)media.dataset.hasExample='0';img.nextElementSibling?.classList.contains('nx-example-tag')&&img.nextElementSibling.remove();img.remove();}},true);
document.addEventListener('keydown',e=>{
 if(e.key!=='Escape'||document.querySelector('dialog[open]'))return;
 const open=document.querySelector('.nx-menu[open]');if(!open)return;
 closeMenu(open);open.querySelector('summary')?.focus();
});
document.addEventListener('click',e=>{
 const target=e.target;
 document.querySelectorAll('.nx-menu[open]').forEach(d=>{if(!d.contains(target))closeMenu(d);});
 const item=target.closest?.('.nx-menu-panel .nx-menu-item');if(item){const d=item.closest('.nx-menu');setTimeout(()=>{if(d?.isConnected)d.open=false;},0);}
 const b=target.closest?.('button');if(!b||b.disabled)return;
 const d=b.dataset,again=sel=>requestAnimationFrame(()=>document.querySelector(sel)?.focus({preventScroll:true}));
 if(d.nxExamples!=null){state.catalogExamples=!examplesOn();persist();settings();again('[data-nx-examples]');return;}
 if(d.nxIntent!=null){T.intent=T.intent===d.nxIntent?'':d.nxIntent;limit=60;familyFilter='';renderCatalog();again(`[data-nx-intent="${CSS.escape(d.nxIntent)}"]`);return;}
 if(d.nxProvider!=null){provider=d.nxProvider;limit=60;T.filtersOpen=true;renderCatalog();again(`[data-nx-provider="${CSS.escape(d.nxProvider)}"]`);return;}
 if(d.nxSort!=null){sort=d.nxSort;limit=60;T.filtersOpen=true;renderCatalog();again(`[data-nx-sort="${CSS.escape(d.nxSort)}"]`);return;}
 if(b.closest('.nx-filter-panel'))T.filtersOpen=d.action!=='hidden';
 if(d.nxClear!=null){
  const all=d.nxClear==='all',is=k=>all||d.nxClear===k;
  if(is('intent'))T.intent='';if(is('family'))familyFilter='';if(is('query'))query='';if(is('provider'))provider='';
  if(is('sort'))sort='recommended';if(is('favorites'))onlyFavorites=false;if(is('dated'))onlyDated=false;
  limit=60;renderCatalog();again('#search');return;
 }
 if(d.nxOpenCard!=null){b.closest('article.model')?.querySelector('.choose')?.click();return;}
 if(d.nxClearHistory!=null){document.querySelector('[data-parity-status=""]')?.click();const input=$('#parity-history-search');if(input?.value){input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));}return;}
 if(d.nxSettingsJump){const section=document.getElementById('nx-settings-'+d.nxSettingsJump);section?.scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});section?.querySelector('h2')?.focus({preventScroll:true});}
});
// Switching kind starts from a clean intent (intents belong to one kind).
document.addEventListener('click',e=>{if(e.target.closest?.('[data-kind]'))T.intent='';},true);

window.NexusTen={version:T.version,state:T,intents:INTENTS,traits,enhance};
if(initial){if(view==='catalog')renderCatalog();enhance();}
})();
