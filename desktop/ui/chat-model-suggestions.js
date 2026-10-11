'use strict';
// Complete actual catalog names at the caret, without replacing the user's prompt.
(()=>{
 let input=null,range=null,matches=[],active=0,popup=null;
 const normalize=s=>String(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
 const isEditor=n=>n?.id==='agent-input'||n?.id==='prompt';
 function close(){
  if(popup)popup.hidden=true;
  if(input){input.setAttribute('aria-expanded','false');input.removeAttribute('aria-activedescendant');}
  matches=[];range=null;
 }
 function score(model,query){
  const name=normalize(model.displayName),family=normalize(model.modelFamily),id=normalize(model.providerModelId);
  const q=normalize(query),compact=q.replaceAll(' ','');
  if(!q)return 0;
  if(name.startsWith(q))return 100;
  if(family.startsWith(q)||id.startsWith(q))return 80;
  const fragments=q.split(' ');
  if(fragments.length>1&&[name,family,id].some(value=>{
   const words=value.split(' ').map(word=>word.replace(/^v(?=\d)/,''));let cursor=0;
   return fragments.every(fragment=>{const at=words.findIndex((word,i)=>i>=cursor&&word.startsWith(fragment.replace(/^v(?=\d)/,'')));if(at<0)return false;cursor=at+1;return true;});
  }))return 70;
  if(name.split(' ').some(word=>word.startsWith(q)))return 60;
  if(compact.length>=3&&[name,family,id].some(s=>s.replaceAll(' ','').includes(compact)))return 40;
  return 0;
 }
 function locate(editor){
  if(editor.selectionStart!==editor.selectionEnd)return null;
  const end=editor.selectionStart,before=editor.value.slice(0,end);
  // Only the phrase being typed: never complete past punctuation or a newline.
  if(!/[\p{L}\p{N}._+-]$/u.test(before))return null;
  const line=before.split(/\n/).at(-1),words=[...line.matchAll(/[\p{L}\p{N}@][\p{L}\p{N}._+@-]*/gu)].slice(-5);
  for(const word of words){
   const start=end-line.length+word.index,query=before.slice(start).replace(/^@/,'');
   if(normalize(query).replaceAll(' ','').length<3)continue;
   const found=models.map(model=>({model,rank:score(model,query)})).filter(item=>item.rank);
   if(found.length)return {start,end,query,found};
  }
  return null;
 }
 function position(){
  if(!input?.isConnected||!popup||popup.hidden)return;
  const rect=input.getBoundingClientRect(),width=Math.min(540,rect.width,innerWidth-24);
  popup.style.left=Math.max(12,Math.min(rect.left,innerWidth-width-12))+'px';
  popup.style.width=width+'px';popup.style.bottom=Math.max(8,innerHeight-rect.top+8)+'px';
  popup.style.maxHeight=Math.max(100,Math.min(340,rect.top-20))+'px';
 }
 function highlight(){
  for(const [i,node] of [...popup.querySelectorAll('[role=option]')].entries())node.setAttribute('aria-selected',String(i===active));
  const option=popup.querySelector('[aria-selected=true]');
  input.setAttribute('aria-activedescendant',option.id);option.scrollIntoView({block:'nearest'});
 }
 function update(editor){
  if(!isEditor(editor))return;
  close();input=editor;range=locate(editor);if(!range)return;
  matches=range.found.sort((a,b)=>b.rank-a.rank||Number(b.model.isFlagship)-Number(a.model.isFlagship)||Number(b.model.versionNumeric||0)-Number(a.model.versionNumeric||0)||Number(b.model.isPrimaryGeneration)-Number(a.model.isPrimaryGeneration)||Number(b.model.variantRank||0)-Number(a.model.variantRank||0)||a.model.displayName.localeCompare(b.model.displayName)).map(item=>item.model);
  active=0;
  if(!popup){popup=document.createElement('div');popup.id='chat-model-suggestions';popup.className='chat-model-suggestions';popup.setAttribute('role','listbox');popup.setAttribute('aria-label','Modelos del catálogo');document.body.append(popup);}
  popup.innerHTML=matches.map((m,i)=>`<button type="button" role="option" tabindex="-1" id="model-suggestion-${i}" data-model-suggestion="${i}" aria-selected="${i===0}"><span>${escape(m.displayName)}</span><small>${escape(providers[m.providerId]||m.providerId)} · ${escape((m.modalities||[]).map(x=>({IMAGE:'Imagen',VIDEO:'Vídeo',CHAT:'Chat',AUDIO:'Audio'}[x]||x)).join(' / '))}</small></button>`).join('');
  popup.hidden=false;input.setAttribute('aria-autocomplete','list');input.setAttribute('aria-controls',popup.id);input.setAttribute('aria-expanded','true');input.setAttribute('aria-activedescendant','model-suggestion-0');
  position();
 }
 function choose(index){
  const model=matches[index],editor=input,current=range;
  if(!model||!current||!editor?.isConnected)return close();
  const name=model.displayName,tail=editor.value.slice(current.end),space=!tail||!/^\s|^[.,;:!?)]/.test(tail)?' ':'';
  editor.setRangeText(name+space,current.start,current.end,'end');
  close();editor.focus({preventScroll:true});
  // Notify existing draft/autosize handlers, then keep the completed name closed.
  editor.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertReplacementText',data:name}));close();
 }
 document.addEventListener('input',e=>{if(isEditor(e.target)){if(e.isComposing)close();else update(e.target);}});
 document.addEventListener('compositionend',e=>{if(isEditor(e.target))update(e.target);});
 document.addEventListener('click',e=>{if(isEditor(e.target))update(e.target);});
 document.addEventListener('keydown',e=>{
  if(e.target!==input||!matches.length||e.isComposing)return;
  if(e.ctrlKey||e.altKey||e.metaKey){if(e.key==='Enter'&&e.ctrlKey)close();return;}
  if(e.key==='Escape'){e.preventDefault();e.stopImmediatePropagation();close();return;}
  if(['ArrowDown','ArrowUp'].includes(e.key)){
   e.preventDefault();e.stopImmediatePropagation();active=(active+(e.key==='ArrowDown'?1:-1)+matches.length)%matches.length;highlight();return;
  }
  if((e.key==='Enter'&&!e.shiftKey)||(e.key==='Tab'&&!e.shiftKey)){
   e.preventDefault();e.stopImmediatePropagation();choose(active);
  }else if(['ArrowLeft','ArrowRight','Home','End'].includes(e.key))close();
 },true);
 document.addEventListener('pointerdown',e=>{
  const option=e.target.closest('[data-model-suggestion]');
  if(option){e.preventDefault();choose(Number(option.dataset.modelSuggestion));}
  else if(e.target!==input)close();
 });
 document.addEventListener('focusout',e=>{if(e.target===input)close();});
 window.addEventListener('resize',position);
 window.addEventListener('scroll',position,true);
 window.NexusModelSuggestions={update,close,score};
})();
