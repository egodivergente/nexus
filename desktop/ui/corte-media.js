/* Media previews and model identity are separate: a logo never claims to be the image. */
'use strict';
(()=>{
 const failed=new Set();
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function identity(asset,index){
  const generation=(index?.generations||[]).find(g=>g.id===asset.generationId);
  const descriptions=[generation?.modelId,asset.modelId,generation?.providerId,asset.providerId,asset.source==='GENERATED'?asset.displayName:''].filter(Boolean).map(value=>String(value).toLowerCase());
  const rules=[[/seedream/, 'bytedance','Seedream'],[/seedance/,'bytedance','Seedance'],[/bytedance|byteplus/,'bytedance','ByteDance'],[/qwen/,'qwen','Qwen'],[/flux/,'flux','Flux'],[/runway/,'runway','Runway'],[/kling/,'kling','Kling'],[/google|veo|nano.?banana/,'google','Google'],[/atlas/,'atlascloud','Atlas Cloud']];
  for(const description of descriptions){const found=rules.find(([pattern])=>pattern.test(description));if(found)return {logo:'branding/providers/'+found[1]+'.png',name:found[2]};}return null;
 }
 function previewSource(uri){
  if(typeof uri!=='string'||uri.length>8192)return null;
  if(/^\/media\/[a-f0-9]{32}\.(png|jpe?g|webp|gif)$/i.test(uri))return uri;
  try{return new URL(uri).protocol==='https:'?uri:null;}catch{return null;}
 }
 function markup(asset,index,selected,glyph){
  const brand=identity(asset,index),source=asset.type==='IMAGE'?previewSource(asset.uri):null,key=asset.id+'|'+(asset.uri||''),unavailable=asset.type==='IMAGE'&&(!source||failed.has(key));
  const fallback=brand?`<img class="corte-model-logo" src="${escape(brand.logo)}" alt="" loading="lazy" decoding="async">`:glyph(asset.type==='VIDEO'?'video':asset.type==='AUDIO'?'audio':'media');
  const preview=source&&!unavailable?`<img class="corte-asset-preview" src="${escape(source)}" data-corte-preview="${escape(key)}" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer">`:'';
  return `<article class="corte-asset" data-corte-asset="${escape(asset.id)}"><div class="corte-asset-thumb ${escape(asset.type.toLowerCase())}" ${unavailable?'title="Vista previa no disponible"':''}><span class="corte-asset-fallback" aria-hidden="true">${fallback}</span>${preview}</div><div class="corte-asset-info"><span title="${escape(asset.displayName)}">${escape(asset.displayName)}</span><small>${{VIDEO:'Vídeo',IMAGE:'Imagen',AUDIO:'Audio'}[asset.type]}${brand?' · '+escape(brand.name):''}</small><small class="corte-preview-status" ${unavailable?'':'hidden'}>Vista no disponible</small></div><div class="corte-asset-actions"><button data-corte-place="${escape(asset.id)}" title="Añadir al cabezal" aria-label="Añadir al cabezal">${glyph('plus')}</button><button data-corte-after="${escape(asset.id)}" title="Después del clip" aria-label="Después del clip" ${!selected?'disabled':''}>↳</button></div></article>`;
 }
 document.addEventListener('error',event=>{
  const img=event.target;if(!img.matches?.('img[data-corte-preview]'))return;
  if(failed.size>=512)failed.delete(failed.values().next().value);failed.add(img.dataset.cortePreview);
  img.hidden=true;img.parentElement.title='Vista previa no disponible';const status=img.closest('.corte-asset')?.querySelector('.corte-preview-status');if(status)status.hidden=false;
 },true);
 document.addEventListener('load',event=>{if(event.target.matches?.('img[data-corte-preview]'))event.target.classList.add('loaded');},true);
 window.CorteMedia={identity,previewSource,markup};
})();
