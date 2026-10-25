'use strict';
// Catalogue parity with Android 3b6082c: honest prices, per-family hiding that survives catalogue refreshes,
// and a persisted card density. Pure helpers are exported for node tests; the DOM layer only decorates renderCatalog.
(function(root){
const UNIT_LABELS={second:'s',seconds:'s',s:'s',seg:'s',segundo:'s','5 s':'5 s','5s':'5 s',image:'imagen',img:'imagen',imagen:'imagen',minute:'min',min:'min',generation:'generación',gen:'generación',request:'generación',clip:'clip'};
const DENSITIES=[['compact','Compacta'],['comfortable','Cómoda'],['spacious','Amplia']];
const UNKNOWN='Precio por confirmar';

function positiveRate(model){
 const raw=model?.basePriceUsd;
 // Only real numbers or numeric strings (Atlas sends "0.072"); Number(true) or Number([1]) must not become a rate.
 let value;
 if(typeof raw==='number')value=raw;
 else if(typeof raw==='string'&&/^\s*[+]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?\s*$/.test(raw))value=Number(raw);
 else return null;
 // A zero or missing rate is missing data, never a free model.
 return Number.isFinite(value)&&value>0?value:null;
}
function verifiedUnit(model){
 if(!model?.priceUnitVerified)return null;
 return UNIT_LABELS[String(model.priceUnit??'').trim().toLowerCase()]||null;
}
function priceInfo(model){
 const usd=positiveRate(model),unit=verifiedUnit(model);
 if(usd!=null&&unit)return {state:'known',usd,unit};
 if(usd!=null)return {state:'unconfirmed',usd,unit:null};
 return {state:'unknown',usd:null,unit:null};
}
function sortablePrice(model){const info=priceInfo(model);return info.state==='known'?info.usd:Infinity;}
function routePrice(model,money){
 const info=priceInfo(model);
 if(info.state==='known')return {...info,text:`${money(info.usd)} / ${info.unit}`,title:'Tarifa publicada'};
 if(info.state==='unconfirmed')return {...info,text:`Base ${money(info.usd)} · unidad sin confirmar`,title:'El proveedor publica una tarifa base sin unidad confirmada. Cotiza antes de generar.'};
 return {...info,text:UNKNOWN,title:'El catálogo no publica tarifa para esta ruta. Cotiza antes de generar.'};
}
function familyPrice(variants,money){
 const list=(variants||[]).filter(Boolean);
 if(list.length<=1)return routePrice(list[0],money);
 const known=list.map(priceInfo).filter(info=>info.state==='known');
 if(known.length){
  const units=new Set(known.map(info=>info.unit));
  if(units.size>1)return {state:'mixed',usd:null,unit:null,text:'Varias tarifas · ver variantes',title:'Las variantes cobran en unidades distintas.'};
  const usd=Math.min(...known.map(info=>info.usd)),unit=known[0].unit;
  const partial=known.length<list.length;
  return {state:partial?'partial':'known',usd,unit,text:`Desde ${money(usd)} / ${unit}${partial?' · tarifas parciales':''}`,title:partial?`${known.length} de ${list.length} variantes publican tarifa; el resto está por confirmar.`:'Tarifa más baja entre las variantes.'};
 }
 const unconfirmed=list.some(model=>priceInfo(model).state==='unconfirmed');
 return {state:unconfirmed?'unconfirmed':'unknown',usd:null,unit:null,text:UNKNOWN,title:unconfirmed?'Hay tarifas base sin unidad confirmada. Abre una variante y cotiza antes de generar.':'Ninguna variante publica tarifa en el catálogo.'};
}

function densityOf(state){
 const value=state?.catalogDensity;
 if(DENSITIES.some(([id])=>id===value))return value;
 return state?.compact?'compact':'comfortable';
}
function setDensity(state,value){
 if(!DENSITIES.some(([id])=>id===value))return false;
 state.catalogDensity=value;state.compact=value==='compact';return true;
}

const familyKey=(kind,name)=>`${kind}|${name}`;
function ensureHidden(state){
 if(!Array.isArray(state.hidden))state.hidden=[];
 if(!state.hiddenFamilies||typeof state.hiddenFamilies!=='object'||Array.isArray(state.hiddenFamilies))state.hiddenFamilies={};
 return state;
}
function membersOf(models,kind,name,familyOf){return models.filter(m=>m.modalities?.includes(kind)&&familyOf(m)===name);}
function hideFamily(state,kind,name,models,familyOf,now=Date.now()){
 ensureHidden(state);
 const key=familyKey(kind,name),entry=state.hiddenFamilies[key]||{kind,family:name,routes:[],at:now};
 const already=new Set(state.hidden);
 for(const m of membersOf(models,kind,name,familyOf))if(!already.has(m.id)){already.add(m.id);state.hidden.push(m.id);entry.routes.push(m.id);}
 state.hiddenFamilies[key]=entry;
 return key;
}
function restoreFamily(state,key){
 ensureHidden(state);
 const entry=state.hiddenFamilies[key];if(!entry)return [];
 const routes=new Set(entry.routes||[]);
 state.hidden=state.hidden.filter(id=>!routes.has(id));
 delete state.hiddenFamilies[key];
 return [...routes];
}
function toggleRoute(state,id){
 ensureHidden(state);
 if(state.hidden.includes(id)){
  state.hidden=state.hidden.filter(x=>x!==id);
  // Showing one variant of a hidden family is an explicit exception, so syncHidden must not hide it again.
  for(const entry of Object.values(state.hiddenFamilies))if((entry.routes||[]).includes(id)){entry.routes=entry.routes.filter(x=>x!==id);entry.shown=[...new Set([...(entry.shown||[]),id])];}
  return false;
 }
 for(const entry of Object.values(state.hiddenFamilies))if(entry.shown?.includes(id))entry.shown=entry.shown.filter(x=>x!==id);
 state.hidden.push(id);return true;
}
function restoreAll(state){ensureHidden(state);state.hidden=[];state.hiddenFamilies={};}
// A refreshed catalogue may add variants to a hidden family: keep the family hidden. Unknown ids are never pruned,
// so a route that is temporarily missing (provider offline, Flow not loaded yet) stays hidden when it comes back.
function syncHidden(state,models,familyOf){
 ensureHidden(state);
 const hidden=new Set(state.hidden);let added=0;
 for(const entry of Object.values(state.hiddenFamilies)){
  entry.routes=Array.isArray(entry.routes)?entry.routes:[];
  const shown=new Set(Array.isArray(entry.shown)?entry.shown:[]);
  for(const m of membersOf(models,entry.kind,entry.family,familyOf))if(!hidden.has(m.id)&&!shown.has(m.id)){hidden.add(m.id);state.hidden.push(m.id);entry.routes.push(m.id);added++;}
 }
 return added;
}
function hiddenEntries(state,models,familyOf){
 ensureHidden(state);
 const byId=new Map(models.map(m=>[m.id,m]));
 const covered=new Set();
 const families=Object.entries(state.hiddenFamilies).map(([key,entry])=>{
  for(const id of entry.routes||[])covered.add(id);
  const present=(entry.routes||[]).filter(id=>byId.has(id));
  return {key,kind:entry.kind,family:entry.family,count:(entry.routes||[]).length,present:present.length,sample:byId.get(present[0])||null,at:entry.at||0};
 }).sort((a,b)=>(b.at-a.at)||a.family.localeCompare(b.family));
 const routes=[...new Set(state.hidden)].filter(id=>!covered.has(id)).map(id=>({id,model:byId.get(id)||null}));
 return {families,routes,total:families.length+routes.length};
}

const api={UNIT_LABELS,DENSITIES,UNKNOWN,priceInfo,sortablePrice,routePrice,familyPrice,densityOf,setDensity,familyKey,ensureHidden,hideFamily,restoreFamily,toggleRoute,restoreAll,syncHidden,hiddenEntries};
root.CatalogParity=api;
if(typeof module!=='undefined'&&module.exports)module.exports=api;

if(typeof document==='undefined'||typeof renderCatalog!=='function'||root.CatalogParityMounted)return;
root.CatalogParityMounted=true;
if(!document.querySelector('link[href="catalog-parity.css"]')){const style=document.createElement('link');style.rel='stylesheet';style.href='catalog-parity.css';document.head.append(style);}
const kindNames={IMAGE:'Imagen',VIDEO:'Vídeo',AUDIO:'Audio',CHAT:'Chat'};
let syncedFor=null,undo=null;
function maybeSync(){
 if(!models.length||syncedFor===models)return;
 syncedFor=models;
 if(syncHidden(state,models,family))persist();
}
function hiddenPanel(){
 const {families,routes,total}=hiddenEntries(state,models,family);
 if(!total)return '<div class="catalog-hidden-panel empty-hidden"><h2>No hay modelos ocultos</h2><p class="hint">Oculta una familia o una variante con el botón ··· de su tarjeta. Podrás recuperarla aquí.</p></div>';
 const familyRows=families.map(f=>`<li class="catalog-hidden-row"><span class="catalog-hidden-logo">${f.sample?logo(f.sample):''}</span><span class="catalog-hidden-text"><strong>${escape(f.family)}</strong><small>Familia · ${escape(kindNames[f.kind]||f.kind)} · ${f.count===1?'1 variante':f.count+' variantes'}${f.present<f.count?' · '+(f.count-f.present)+' fuera del catálogo actual':''}</small></span><button class="small" data-catalog-restore-family="${escape(f.key)}">Mostrar</button></li>`).join('');
 const routeRows=routes.map(r=>`<li class="catalog-hidden-row"><span class="catalog-hidden-logo">${r.model?logo(r.model):''}</span><span class="catalog-hidden-text"><strong>${escape(r.model?.displayName||r.id)}</strong><small>${r.model?escape((r.model.modalities||[]).map(k=>kindNames[k]||k).join(' · ')+' · '+(providers[r.model.providerId]||r.model.providerId||'')):'No está en el catálogo actual; se conserva oculto'}</small></span><button class="small" data-catalog-restore="${escape(r.id)}">Mostrar</button></li>`).join('');
 return `<section class="catalog-hidden-panel" aria-label="Modelos ocultos"><div class="catalog-hidden-head"><div><h2>Ocultos</h2><p class="hint">Siguen ocultos aunque se actualice el catálogo. Las variantes nuevas de una familia oculta también se ocultan.</p></div><button class="small" data-catalog-restore-all>Mostrar todo</button></div>${families.length?'<h3>Familias</h3><ul>'+familyRows+'</ul>':''}${routes.length?'<h3>Variantes</h3><ul>'+routeRows+'</ul>':''}</section>`;
}
function densityControl(){
 const current=densityOf(state);
 return `<div class="catalog-density" role="group" aria-label="Tamaño de las tarjetas">${DENSITIES.map(([id,label])=>`<button type="button" class="small${id===current?' active':''}" data-catalog-density="${id}" aria-pressed="${id===current}">${label}</button>`).join('')}</div>`;
}
function decorate(){
 const content=document.getElementById('content');if(!content)return;
 const hiddenButton=content.querySelector('[data-action="hidden"]');
 if(hiddenButton){const count=hiddenEntries(state,models,family).total;hiddenButton.textContent=showHidden?'Volver al catálogo':count?`Ocultos · ${count}`:'Ocultos';hiddenButton.setAttribute('aria-pressed',String(showHidden));}
 const summary=content.querySelector('.catalog-summary');
 if(summary&&!summary.querySelector('.catalog-density')&&!showHidden)summary.insertAdjacentHTML('beforeend',densityControl());
 if(showHidden){
  content.querySelectorAll('.catalog,.catalog ~ .empty,[data-action="more"]').forEach(node=>node.remove());
  const badge=content.querySelector('.catalog-summary .nx-meta-badge');if(badge)badge.textContent='Modelos y familias ocultos';
  content.querySelector('.catalog-header-bar')?.insertAdjacentHTML('afterend',hiddenPanel());
  return;
 }
 content.querySelectorAll('.guide-item[data-model]').forEach(item=>{
  const model=models.find(m=>m.id===item.dataset.model),cost=item.querySelector('.guide-cost');
  if(model&&cost){const price=routePrice(model,money);cost.textContent=price.text;cost.dataset.priceState=price.state;}
 });
}
const catalogBeforeCatalogParity=renderCatalog;
renderCatalog=function(){maybeSync();catalogBeforeCatalogParity();decorate();};
function showUndo(text,revert){
 clearTimeout(undo?.timer);document.querySelector('.catalog-undo')?.remove();
 const bar=document.createElement('div');bar.className='catalog-undo';bar.setAttribute('role','status');
 bar.innerHTML=`<span>${escape(text)}</span><button type="button" class="small" data-catalog-undo>Deshacer</button>`;
 document.body.append(bar);
 undo={revert,timer:setTimeout(()=>{bar.remove();undo=null;},6000)};
}
function commit(){persist();if(view==='catalog')renderCatalog();}
window.addEventListener('nexus:catalog-hidden',event=>{
 const {id,hidden}=event.detail||{};if(!hidden)return;
 const model=models.find(m=>m.id===id);
 showUndo(`${model?.displayName||'Variante'} oculta`,()=>{toggleRoute(state,id);});
});
document.addEventListener('click',event=>{
 const button=event.target.closest('button');if(!button||button.disabled)return;
 const data=button.dataset;
 if(data.hideFamily!=null){
  const name=data.hideFamily,key=hideFamily(state,kind,name,models,family);
  for(const id of state.hiddenFamilies[key].routes)selection.delete(id);
  showUndo(`${name} oculto en ${kindNames[kind]||kind}`,()=>restoreFamily(state,key));
  return commit();
 }
 if(data.catalogRestoreFamily!=null){restoreFamily(state,data.catalogRestoreFamily);return commit();}
 if(data.catalogRestore!=null){if(state.hidden.includes(data.catalogRestore))toggleRoute(state,data.catalogRestore);return commit();}
 if(data.catalogRestoreAll!=null){const snapshot={hidden:[...state.hidden],hiddenFamilies:structuredClone(state.hiddenFamilies)};restoreAll(state);showUndo('Todo vuelve a estar visible',()=>{state.hidden=snapshot.hidden;state.hiddenFamilies=snapshot.hiddenFamilies;});return commit();}
 if(data.catalogUndo!=null&&undo){clearTimeout(undo.timer);undo.revert();undo=null;button.closest('.catalog-undo')?.remove();return commit();}
 if(data.catalogDensity){if(setDensity(state,data.catalogDensity))commit();}
});
if(typeof view!=='undefined'&&view==='catalog'&&document.getElementById('content')?.querySelector('.catalog-header-bar'))renderCatalog();
})(typeof window!=='undefined'?window:globalThis);
