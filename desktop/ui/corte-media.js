/* Media previews and model identity are separate: a logo never claims to be the image. */
'use strict';
(()=>{
 const failed=new Set();
 // Family logos precede their parent companies when both appear in a model path.
 const brands=[
  ['nanobanana','Nano Banana','nano[ _-]?banana'],['sora','Sora'],['grok','Grok'],
  ['claude','Claude'],['kimi','Kimi'],['glm','GLM'],['hailuo','Hailuo'],
  ['flux','Flux'],['qwen','Qwen'],['wan','Wan'],['vidu','Vidu'],['kling','Kling'],
  ['runway','Runway'],['pixverse','PixVerse'],['pika','Pika'],['luma','Luma'],
  ['ideogram','Ideogram'],['midjourney','Midjourney'],['krea','Krea'],
  ['hunyuan','Hunyuan'],['happyhorse','HappyHorse','happy[ _-]?horse'],
  ['zimage','Z-Image','z[ _-]?image'],['suno','Suno'],['tripo','Tripo'],
  ['elevenlabs','ElevenLabs','eleven[ _-]?labs'],['doubao','Doubao'],['ernie','ERNIE'],
  ['longcat','LongCat','long[ _-]?cat'],['deepseek','DeepSeek'],
  ['stability','Stability AI','stability(?:[ _-]?ai)?|stable[ _-]?diffusion|sdxl'],
  ['bfl','Black Forest Labs','bfl|black[ _-]?forest[ _-]?labs'],
  ['minimax','MiniMax'],['moonshot','Moonshot'],['zai','Z.ai','z[ ._-]?ai|zhipu'],
  ['xai','xAI','x[ ._-]?ai'],['anthropic','Anthropic'],['openai','OpenAI','open[ _-]?ai|gpt'],
  ['google','Google','google|veo|imagen|gemini'],['microsoft','Microsoft'],['nvidia','NVIDIA'],
  ['bytedance','ByteDance','byte[ _-]?dance|byteplus'],['atlascloud','Atlas Cloud','atlas[ _-]?(?:cloud)?']
 ].map(([id,name,pattern=id])=>Object.freeze({id,name,pattern:new RegExp('(?:^|[^a-z0-9])(?:'+pattern+')(?=$|[^a-z]|[0-9])')}));
 const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function identity(asset,index){
  const generation=(index?.generations||[]).find(g=>g.id===asset.generationId);
  const descriptions=[generation?.modelId,asset.modelId,generation?.providerId,asset.providerId,asset.source==='GENERATED'?asset.displayName:''].filter(Boolean).map(value=>String(value).toLowerCase());
  for(const description of descriptions){
   const seed=/(?:^|[^a-z0-9])(seedream|seedance)(?=$|[^a-z]|[0-9])/.exec(description);
   if(seed)return {logo:'branding/providers/bytedance.png',name:seed[1]==='seedream'?'Seedream':'Seedance'};
   const brand=brands.find(brand=>brand.pattern.test(description));
   if(brand)return {logo:'branding/providers/'+brand.id+'.png',name:brand.name};
  }return null;
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
 window.CorteMedia={identity,previewSource,markup,brands:Object.freeze(brands)};
})();
