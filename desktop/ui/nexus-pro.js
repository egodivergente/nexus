'use strict';
// Presentation-only enhancements. Reuses existing handlers (opens details through the existing button).
(()=>{
 const safe=fn=>{try{fn();}catch(_){}};
 const cardParts=card=>({visual:card.querySelector('.visual'),detail:card.querySelector('.studio-detail-button')});

 function enhanceCards(){
  document.querySelectorAll('.studio-library .result:not([data-nx])').forEach(card=>{
   card.dataset.nx='1';
   const {visual}=cardParts(card);
   const video=card.querySelector('video');
   if(video){
    video.removeAttribute('controls');video.muted=true;video.loop=true;video.playsInline=true;
    card.addEventListener('mouseenter',()=>{video.play().catch(()=>{});});
    card.addEventListener('mouseleave',()=>{video.pause();});
   }
   if(visual&&!visual.querySelector('audio')){
    visual.tabIndex=0;visual.setAttribute('role','button');visual.setAttribute('aria-label','Abrir detalles');
   }
   const small=card.querySelector('.studio-detail-button small');
   if(small&&/^completado$/i.test(small.textContent.trim()))small.classList.add('nx-ok');
  });
  document.querySelectorAll('img:not([alt])').forEach(img=>img.setAttribute('alt',''));
 }

 function enhanceDetails(){
  const panel=document.querySelector('.studio-details:not([data-nx])');
  if(!panel)return;
  panel.dataset.nx='1';
  const id=panel.querySelector('[data-reuse]')?.dataset.reuse;
  const job=id&&typeof state!=='undefined'?state.jobs.find(j=>j.id===id):null;
  if(!job)return;
  const index=(typeof outputSelection!=='undefined'&&outputSelection.get(id))||0;
  const output=job.outputs?.[index],url=typeof output==='string'?output:output?.url;
  if(!url||typeof isMediaUrl!=='function'||!isMediaUrl(url)||panel.querySelector('.nx-detail-media'))return;
  const media=document.createElement(job.modality==='VIDEO'?'video':job.modality==='AUDIO'?'audio':'img');
  if(media.tagName==='VIDEO'){media.controls=true;media.preload='metadata';}
  else if(media.tagName==='AUDIO'){media.controls=true;media.preload='none';}
  else{media.alt=job.name||job.modelId||'';media.decoding='async';}
  media.src=url;
  const wrap=document.createElement('div');wrap.className='nx-detail-media';wrap.append(media);
  panel.querySelector('h2')?.before(wrap);
 }

 let queued=false;
 const schedule=()=>{if(queued)return;queued=true;requestAnimationFrame(()=>{queued=false;safe(enhanceCards);safe(enhanceDetails);});};
 new MutationObserver(schedule).observe(document.getElementById('content')||document.body,{childList:true,subtree:true});
 schedule();

 // Click or Enter on the media opens the existing details panel
 document.addEventListener('click',e=>{
  const visual=e.target.closest('.studio-library .result .visual');
  if(!visual||e.target.closest('button,a,audio'))return;
  visual.closest('.result')?.querySelector('.studio-detail-button')?.click();
 });
 document.addEventListener('keydown',e=>{
  if(e.key==='Enter'&&e.target.matches?.('.studio-library .result .visual'))e.target.closest('.result')?.querySelector('.studio-detail-button')?.click();
  if(e.key==='Escape'&&!document.querySelector('dialog[open]'))document.querySelector('.studio-detail-close')?.click();
 });

 // Each section starts at the top instead of inheriting the previous scroll
 document.addEventListener('click',e=>{
  if(!e.target.closest('#navigation button,#studio-settings'))return;
  requestAnimationFrame(()=>requestAnimationFrame(()=>{
   ['content','main'].forEach(key=>{const el=key==='content'?document.getElementById('content'):document.querySelector('main');if(el)el.scrollTop=0;});
  }));
 });
})();
