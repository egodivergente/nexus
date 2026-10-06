/* Native-feeling controls and content cleanup shared by every view. */
const oldLogo=logo;
const genericMark='<span class="generic-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M4 19V5l16 14V5"/><path d="M4 5h3m10 14h3"/></svg></span>';
logo=function(model) {
  const result=oldLogo(model);
  return result.startsWith('<span class="initial">')?genericMark:result;
};
const reviewedBriefs=new Map(NexusFocus.entries.map(([id,,good,avoid])=>[id,{good,avoid}]));
function decisionBrief(model) {
  const reviewed=reviewedBriefs.get(model.id);if(reviewed)return reviewed;
  const id=model.id.toLowerCase(),caps=model.capabilities||[],has=x=>caps.includes(x);
  if(/motion[-_]?control/.test(id)||has('MOTION_CONTROL'))return {good:'Transferir gestos desde un vídeo a un personaje de referencia.',avoid:'No tienes vídeo e imagen de entrada.'};
  if(/upscal/.test(id)||has('UPSCALE'))return {good:'Ampliar la resolución de un archivo existente.',avoid:'Quieres crear una escena nueva.'};
  if(/lip[-_]?sync/.test(id)||has('LIP_SYNC'))return {good:'Sincronizar labios con una pista de voz.',avoid:'No tienes personaje y audio.'};
  if(/video[-_]?extend/.test(id)||has('VIDEO_EXTEND'))return {good:'Continuar un vídeo existente.',avoid:'No tienes clip de partida.'};
  if(/start[-_]?end|first[-_]?last/.test(id))return {good:'Dirigir un plano con imágenes de inicio y final.',avoid:'Solo tienes un prompt de texto.'};
  if(/reference[-_]?to[-_]?video/.test(id)||has('REFERENCE_TO_VIDEO'))return {good:'Crear vídeo guiado por referencias visuales.',avoid:'No tienes las referencias solicitadas.'};
  if(/video[-_]?to[-_]?video|reframe/.test(id)||has('VIDEO_TO_VIDEO')||has('VIDEO_EDIT'))return {good:'Transformar o reencuadrar un vídeo existente.',avoid:'Quieres empezar sin vídeo de entrada.'};
  if(/image[-_]?to[-_]?video|\/i2v/.test(id)||has('IMAGE_TO_VIDEO'))return {good:'Animar una imagen de partida.',avoid:'No tienes imagen inicial.'};
  if(/text[-_]?to[-_]?video|\/t2v/.test(id)||has('TEXT_TO_VIDEO'))return {good:'Crear un clip desde la descripción de la escena.',avoid:'Necesitas conservar un fotograma concreto.'};
  if(/\/(edit|inpaint|outpaint|image-to-image)$/.test(id)||has('IMAGE_EDITING')||has('IMAGE_TO_IMAGE'))return {good:'Cambiar una imagen existente con instrucciones.',avoid:'No tienes imagen de entrada.'};
  if(/text[-_]?to[-_]?image/.test(id)||has('TEXT_TO_IMAGE'))return {good:'Crear una imagen desde una descripción.',avoid:'Necesitas editar una foto concreta.'};
  if(model.modalities.includes('AUDIO'))return {good:'Crear o transformar audio según las entradas admitidas.',avoid:'Necesitas un resultado visual.'};
  if(model.modalities.includes('CHAT'))return {good:'Trabajar con texto y conversación.',avoid:'Necesitas generar imagen o vídeo.'};
  return {good:'Consulta las entradas y opciones reales de esta variante.',avoid:'Su especialidad no está documentada con suficiente precisión.'};
}
const oldDescription=description;
description=function(model) {return decisionBrief(model).good||oldDescription(model);};
document.addEventListener('error',event=>{
  if(event.target instanceof HTMLImageElement && event.target.matches('img.logo')) event.target.outerHTML=genericMark;
  if(event.target instanceof HTMLImageElement && event.target.matches('.result img')) {
    const fallback=document.createElement('div');fallback.className='media-unavailable';fallback.textContent='Vista previa no disponible';
    event.target.replaceWith(fallback);
  }
},true);

function tidyCatalog() {
  document.querySelectorAll('.summary > span').forEach(el=>{
    const text=el.textContent.replace(/\s*·\s*$/,'').replace(/\b1 modelos\b/g,'1 modelo').replace(/\b1 familias\b/g,'1 familia');
    if(text!==el.textContent)el.textContent=text;
  });
  document.querySelectorAll('[data-family]').forEach(button=>{
    const count=Number(button.textContent.match(/\d+/)?.[0]);
    if(Number.isFinite(count))button.textContent=count===1?'Abrir modelo':`Explorar ${count} variantes`;
  });
  document.querySelectorAll('.card-meta span:last-child').forEach(el=>{
    const match=/^(\d{4})-(\d{2})$/.exec(el.textContent.trim());if(!match)return;
    el.textContent=new Intl.DateTimeFormat('es-ES',{month:'short',year:'numeric'}).format(new Date(Number(match[1]),Number(match[2])-1,1));
  });
  document.querySelectorAll('[data-action="dated"]').forEach(button=>button.textContent='Solo con fecha');
  document.querySelectorAll('[data-hide]').forEach(button=>{
    if(button.textContent==='···'){button.innerHTML='<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="5" cy="12" r="1.7"/><circle cx="12" cy="12" r="1.7"/><circle cx="19" cy="12" r="1.7"/></svg>';button.setAttribute('aria-label','Ocultar modelo');}
  });
  document.querySelectorAll('.model').forEach(card=>{
    const id=card.querySelector('[data-model]')?.dataset.model;
    const provider=card.querySelector('.provider');
    if(!id){
      if(provider)provider.textContent='';
      const cost=card.querySelector('.price');
      if(cost)cost.textContent=cost.textContent.replace('Consultar coste','Cotizar antes de usar').replace(/^Desde (.+) · base$/,'Base $1 · unidad sin confirmar');
      return;
    }
    const model=models.find(item=>item.id===id);if(!model)return;
    if(provider)provider.textContent=model.organization==='Atlas Cloud'?'':model.organization;
    const cost=card.querySelector('.price');
    if(cost)cost.textContent=cost.textContent.replace('Consultar coste','Cotizar antes de usar').replace(/^Desde (.+) · base$/,'Base $1 · unidad sin confirmar');
    const brief=decisionBrief(model),paragraph=card.querySelector('.description');
    if(paragraph){paragraph.innerHTML=`<strong>Para</strong> ${escape(brief.good)}<br><strong>Evita</strong> ${escape(brief.avoid)}`;paragraph.classList.add('decision-brief');}
  });
}
const beforePolishCatalog=renderCatalog;
renderCatalog=function() {
  for(const model of models) if(model.id.startsWith('youchuan/')) {model.modelFamily='Youchuan';model.organization='Youchuan';}
  beforePolishCatalog();tidyCatalog();
};
const beforePolishCanvas=renderCanvas;
renderCanvas=function() {
  if(configuring||loaded)return beforePolishCanvas();
  $('#content').innerHTML=`<div class="canvas-empty"><p class="eyebrow">LIENZO</p><h2>¿Qué vamos a crear?</h2><p>Elige el resultado. Después verás los modelos, referencias y ajustes que corresponden a esa tarea.</p><div class="canvas-start-actions"><button class="primary" data-start-kind="IMAGE">Crear imagen</button><button data-start-kind="VIDEO">Crear vídeo</button><button data-action="browse">Explorar todos los modelos</button></div></div>`;
};
document.addEventListener('click',event=>{
  const modality=event.target.closest('[data-start-kind]')?.dataset.startKind;
  if(!modality)return;kind=modality;familyFilter='';query='';navigate('catalog');
});

let activePopup=null;
function closeSelect() {if(!activePopup)return;activePopup.list.remove();activePopup.trigger.setAttribute('aria-expanded','false');activePopup=null;}
function openSelect(select,trigger) {
  closeSelect();
  const list=document.createElement('div');list.className='select-popup';list.setAttribute('role','listbox');
  const options=[...select.options].filter(option=>!option.disabled);
  for(const option of options) {
    const item=document.createElement('button');item.type='button';item.className='select-option';item.setAttribute('role','option');
    item.setAttribute('aria-selected',String(option.selected));item.textContent=option.textContent;
    item.addEventListener('click',()=>{
      select.value=option.value;trigger.textContent=option.textContent;closeSelect();
      select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));
      if(trigger.isConnected)trigger.focus();
    });list.appendChild(item);
  }
  document.body.appendChild(list);
  const rect=trigger.getBoundingClientRect(),height=Math.min(280,list.scrollHeight);
  list.style.left=`${Math.max(8,Math.min(rect.left,innerWidth-rect.width-8))}px`;
  list.style.width=`${Math.max(rect.width,190)}px`;
  list.style.top=`${rect.bottom+height+8>innerHeight?Math.max(8,rect.top-height-5):rect.bottom+5}px`;
  trigger.setAttribute('aria-expanded','true');activePopup={list,trigger};
  list.querySelector('[aria-selected="true"]')?.scrollIntoView({block:'nearest'});
  list.addEventListener('keydown',event=>{
    if(event.key==='Escape'){closeSelect();trigger.focus();}
    if(event.key==='ArrowDown'||event.key==='ArrowUp'){
      event.preventDefault();const all=[...list.querySelectorAll('button')],index=all.indexOf(document.activeElement);
      all[Math.max(0,Math.min(all.length-1,index+(event.key==='ArrowDown'?1:-1)))].focus();
    }
  });
}
function enhanceSelects() {
  document.querySelectorAll('select:not([data-enhanced])').forEach(select=>{
    select.dataset.enhanced='true';select.classList.add('native-select-hidden');select.tabIndex=-1;select.setAttribute('aria-hidden','true');
    const trigger=document.createElement('button');trigger.type='button';trigger.className='select-trigger';
    trigger.setAttribute('aria-haspopup','listbox');trigger.setAttribute('aria-expanded','false');
    const label=select.getAttribute('aria-label')||document.querySelector(`label[for="${select.id}"]`)?.textContent||select.closest('.field')?.querySelector('label')?.textContent||'Seleccionar opción';
    trigger.setAttribute('aria-label',label);trigger.textContent=select.selectedOptions[0]?.textContent||'Seleccionar';
    select.insertAdjacentElement('afterend',trigger);
    trigger.addEventListener('click',()=>activePopup?.trigger===trigger?closeSelect():openSelect(select,trigger));
    trigger.addEventListener('keydown',event=>{
      if(['ArrowDown','ArrowUp','Enter',' '].includes(event.key)){event.preventDefault();openSelect(select,trigger);activePopup?.list.querySelector('[aria-selected="true"]')?.focus();}
      if(event.key==='Escape')closeSelect();
    });
  });
}
let selectFrame=0;
new MutationObserver(()=>{if(selectFrame)return;selectFrame=requestAnimationFrame(()=>{selectFrame=0;enhanceSelects();});}).observe(document.body,{childList:true,subtree:true});
enhanceSelects();
document.addEventListener('pointerdown',event=>{
  if(activePopup&&!activePopup.list.contains(event.target)&&event.target!==activePopup.trigger)closeSelect();
},true);
document.addEventListener('keydown',event=>{if(event.key==='Escape')closeSelect();});
document.getElementById('content').addEventListener('scroll',closeSelect,{passive:true});
window.addEventListener('resize',closeSelect);
