/* An editorial starting point. The complete catalog and user choices remain intact. */
const NexusFocus = {
  entries: [
    ['bytedance/seedream-v5.0-pro/text-to-image','Imagen desde texto','Fotografía, producto y composiciones con texto.','Necesitas editar una foto: abre la variante Edit.'],
    ['bytedance/seedream-v5.0-pro/edit','Editar imagen','Cambios dirigidos sobre una imagen existente.','Quieres empezar sin imagen de entrada.'],
    ['google/nano-banana-2/text-to-image','Ideas rápidas','Explorar conceptos e instrucciones visuales en varias rondas.','Tu prioridad es una edición sobre una foto: elige Edit.'],
    ['google/nano-banana-2/edit','Editar con instrucciones','Probar cambios sobre referencias visuales.','No tienes una imagen de partida.'],
    ['black-forest-labs/flux-2-pro/text-to-image','Composición','Crear una imagen desde el prompt con control de estilo.','Necesitas mantener una referencia visual: usa Edit.'],
    ['black-forest-labs/flux-2-pro/edit','Varias referencias','Combinar y editar imágenes con varias referencias.','No tienes archivos de referencia.'],
    ['microsoft/mai-image-2.6-flash/text-to-image','Coste claro','Imagen desde texto con tarifa por imagen publicada.','Necesitas editar una imagen: usa su variante Edit.'],
    ['microsoft/mai-image-2.6-flash/edit','Editar con coste claro','Editar una imagen con tarifa por imagen publicada.','No tienes imagen de partida.'],
    ['openai/gpt-image-2.5-sunburst/text-to-image','Instrucciones detalladas','Crear imágenes siguiendo un prompt escrito.','Necesitas conservar una imagen: usa Edit.'],
    ['openai/gpt-image-2.5-sunburst/edit','Editar por texto','Modificar una imagen con instrucciones en lenguaje natural.','No tienes imagen de entrada.'],
    ['qwen-image-3.0-pro/text-to-image','Texto en imagen','Composiciones con texto en inglés o chino según su ficha.','Quieres vídeo o una foto de partida.'],
    ['qwen-image-3.0-pro/edit','Editar texto visual','Modificar una imagen existente con instrucciones.','No tienes imagen de entrada.'],
    ['ideogram/v4/quality/text-to-image','Imagen desde texto','Crear una composición con la variante Quality.','Necesitas editar una imagen previa.'],
    ['xai/grok-imagine-image-2.0/text-to-image','Otra interpretación','Probar una interpretación visual distinta del mismo prompt.','Necesitas mantener una referencia exacta.'],
    ['xai/grok-imagine-image-2.0/edit','Edición alternativa','Transformar una imagen existente.','Quieres empezar sin imagen.'],
    ['runway/gen4_image/image','Referencias visuales','Crear o editar imágenes con referencias en Runway.','Necesitas animar un clip.'],
    ['luma/uni-1/text-to-image','Otra estética','Crear imágenes desde texto en un motor distinto.','Necesitas editar: usa Edit.'],
    ['luma/uni-1/edit','Edición visual','Cambiar una imagen existente con instrucciones.','No tienes imagen de entrada.'],
    ['alibaba/wan-2.7/text-to-image','Escenas desde texto','Crear imágenes a partir de una descripción.','Quieres animar una imagen.'],
    ['runway/magnific_precision_upscaler_v2/upscale-image','Ampliar imagen','Aumentar resolución y ajustar nitidez y detalle.','Quieres crear una imagen nueva.'],
    ['minimax/h3/text-to-video','Vídeo desde texto','Crear un clip describiendo la escena y el movimiento.','Necesitas animar un fotograma concreto.'],
    ['minimax/h3/image-to-video','Animar imagen','Dar movimiento a una imagen de partida.','No tienes imagen de entrada.'],
    ['bytedance/seedance-2.5/text-to-video','Movimiento','Crear escenas de vídeo desde texto.','Necesitas conservar un fotograma: usa la variante de imagen.'],
    ['bytedance/seedance-2.5/image-to-video','Animar escena','Dirigir el movimiento desde un fotograma inicial.','Quieres generar desde cero sin imagen.'],
    ['crun/kling-v3-std','Vídeo general','Crear un clip con la variante Standard y tarifa por cinco segundos.','Necesitas transferir un movimiento existente.'],
    ['crun/kling-v3-motion-control-std','Transferir movimiento','Aplicar la actuación de un vídeo al personaje de una imagen.','No tienes ambos archivos: imagen y vídeo.'],
    ['google/veo3.1/text-to-video','Plano cinematográfico','Crear un vídeo desde un prompt de escena.','Necesitas animar una imagen concreta.'],
    ['google/veo3.1/image-to-video','Plano desde imagen','Animar un fotograma inicial con instrucciones.','No tienes imagen de entrada.']
    ,['runway/gen4.5/t2v','Plano desde texto','Crear un clip indicando escena, duración y encuadre.','Necesitas un primer fotograma concreto.']
    ,['runway/gen4.5/i2v','Animar fotograma','Crear un clip desde una imagen inicial.','No tienes imagen de entrada.']
    ,['alibaba/wan-3.0-prime/text-to-video','Vídeo desde texto','Crear una escena de vídeo desde la descripción.','Necesitas referencias visuales: usa su ruta de referencias.']
    ,['alibaba/wan-3.0-prime/image-to-video','Animar imagen','Dar movimiento a un fotograma inicial.','No tienes imagen.']
    ,['alibaba/wan-3.0-prime/reference-to-video','Vídeo con referencias','Guiar una escena con referencias visuales.','Quieres un clip solo desde texto.']
    ,['luma/ray-3.2/text-to-video','Vídeo desde texto','Crear un plano desde una descripción.','Necesitas editar un vídeo grabado.']
    ,['luma/ray-3.2/image-to-video','Animar imagen','Dar movimiento a una imagen inicial.','No tienes imagen de partida.']
    ,['luma/ray-3.2/video-to-video','Transformar vídeo','Cambiar un vídeo de entrada.','Quieres crear desde cero.']
    ,['vidu/q3-pro/start-end-to-video','Inicio y final','Dirigir la transición entre dos fotogramas.','No tienes las imágenes de inicio y final.']
    ,['pixverse/v6/video-extend','Extender vídeo','Continuar un clip ya creado.','No tienes vídeo de entrada.']
    ,['minimax/hailuo-2.3/t2v-standard','Vídeo desde texto','Crear un clip desde texto con la ruta Standard.','Necesitas animar una imagen.']
    ,['minimax/hailuo-2.3/i2v-standard','Animar imagen','Crear un clip desde una imagen con la ruta Standard.','No tienes imagen de partida.']
  ],
  curated(list, modality, hidden, favorites) {
    const byId = new Map(list.filter(m => m.modalities.includes(modality) && !hidden.includes(m.id)).map(m => [m.id,m]));
    return this.entries.map(([id,role,good,avoid]) => ({model:byId.get(id),role,good,avoid}))
      .filter(x => x.model).sort((a,b) => Number(favorites.includes(b.model.id))-Number(favorites.includes(a.model.id)));
  },
  grouped(entries) {
    const groups=new Map();
    for(const entry of entries) {
      const id=entry.model.id;
      const key=id.replace(/\/(text-to-image|image-to-video|text-to-video|reference-to-video|video-to-video|edit|t2v|i2v|t2v-standard|i2v-standard)$/,'');
      if(!groups.has(key))groups.set(key,[]);
      groups.get(key).push(entry);
    }
    return [...groups.values()];
  },

};
const renderFullCatalog=renderCatalog;
renderCatalog=function() {
  renderFullCatalog();
  if(!['IMAGE','VIDEO'].includes(kind)||query||familyFilter||provider||onlyFavorites||showHidden)return;
  const groups=NexusFocus.grouped(NexusFocus.curated(models,kind,state.hidden,state.favorites));
  const titles={'openai/gpt-image-2.5-sunburst':'GPT Image 2.5 Sunburst','qwen-image-3.0-pro':'Qwen Image 3.0 Pro','runway/gen4.5':'Runway Gen-4.5','luma/ray-3.2':'Luma Ray 3.2','minimax/hailuo-2.3':'Hailuo 2.3'};
  const guide=`<section class="quick-guide"><div class="quick-guide-head"><div><p class="eyebrow">GUÍA RÁPIDA</p><h2>Elige por lo que quieres hacer</h2><p class="hint">Sugerencias para orientarte. El catálogo completo permanece debajo.</p></div><button class="small" data-jump-catalog>Ver ${filteredModels().length} rutas ↓</button></div><div class="guide-grid">${groups.map(group=>{const {model:m,role,good}=group[0],key=m.id.replace(/\/(text-to-image|image-to-video|text-to-video|reference-to-video|video-to-video|edit|t2v|i2v|t2v-standard|i2v-standard)$/,'');const cost=m.basePriceUsd==null?'Coste al configurar':m.priceUnitVerified&&m.priceUnit?`${money(m.basePriceUsd)} / ${m.priceUnit}`:`Base ${money(m.basePriceUsd)} · unidad sin confirmar`;return `<button class="guide-item" data-model="${escape(m.id)}"><span class="guide-name">${escape(titles[key]||m.displayName.replace(/ Edit$/,''))}</span><span class="guide-use">${escape(good)}</span><span class="guide-cost">${escape(cost)}</span><span class="guide-role">${escape(role)} →</span></button>`}).join('')}</div></section>`;
  $('#content .tabs').insertAdjacentHTML('afterend',guide);
};
document.addEventListener('click',event=>{
  if(event.target.closest('[data-jump-catalog]')) document.querySelector('#content .toolbar')?.scrollIntoView({behavior:'smooth',block:'start'});
});
