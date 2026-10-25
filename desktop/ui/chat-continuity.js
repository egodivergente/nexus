'use strict';
// Chat continuity for Desktop: folded long user turns, message copy/select actions and the
// compact conversation side panel. Pure helpers are exported for desktop/tools tests.
(function(root){
 const FOLD_LINES=8;
 const PANEL_KEY='nexus_chat_panel_open';
 const ROLES=new Set(['user','assistant']);
 const plainEscape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const svg=path=>`<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
 const glyphs={
  copy:svg('<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>'),
  select:svg('<path d="M4 7V5a1 1 0 0 1 1-1h2M17 4h2a1 1 0 0 1 1 1v2M20 17v2a1 1 0 0 1-1 1h-2M7 20H5a1 1 0 0 1-1-1v-2M9 12h6"/>'),
  rename:svg('<path d="M4 20h4L19 9l-4-4L4 16v4ZM13.5 6.5l4 4"/>'),
  close:svg('<path d="M6 6l12 12M18 6 6 18"/>')
 };

 function newId(){
  const raw=root.crypto?.randomUUID?.()||(Date.now().toString(16)+Math.random().toString(16).slice(2));
  return raw.replace(/[^A-Za-z0-9]/g,'').slice(0,40);
 }
 function historyItems(items){
  return (Array.isArray(items)?items:[]).filter(i=>i&&i.text&&ROLES.has(i.role)).map(i=>({role:i.role,text:i.text}));
 }
 // Assistant answers are never folded: only the user's own long prompts fold.
 function messageMarkup(item,{escape=plainEscape,expanded=false}={}){
  const id=String(item.id),user=item.role==='user',textId='cc-text-'+id;
  const body=item.role==='assistant'&&item.html?item.html:escape(item.text);
  const classes=['agent-message',item.role,user?'cc-foldable':'',user&&expanded?'cc-expanded':''].filter(Boolean).join(' ');
  return `<article data-agent-message="${escape(id)}" class="${escape(classes)}"><div class="agent-author">${user?'Tú':'Nexus'}</div>`+
   `<div class="agent-text${user?' cc-fold-body':''}" id="${escape(textId)}">${body}</div>`+
   (user?`<button type="button" class="cc-fold-toggle" data-cc-fold="${escape(id)}" aria-controls="${escape(textId)}" aria-expanded="${expanded}"${expanded?'':' hidden'}>${expanded?'Contraer':'Expandir'}</button>`:'')+
   (item.attachments||[]).map(f=>`<span class="agent-file">${escape(f.name)}</span>`).join('')+
   (item.usage?`<small class="hint">${escape(item.usage)}</small>`:'')+
   (item.failed?'<small class="hint">Envío no confirmado. Tu mensaje se conserva.</small>':'')+
   `<div class="cc-actions" role="group" aria-label="Acciones del mensaje"><button type="button" class="cc-action" data-cc-copy="${escape(id)}" title="Copiar mensaje" aria-label="Copiar mensaje">${glyphs.copy}<span>Copiar</span></button><button type="button" class="cc-action" data-cc-select="${escape(id)}" title="Seleccionar el texto del mensaje" aria-label="Seleccionar texto">${glyphs.select}<span>Seleccionar</span></button></div></article>`;
 }
 function archivePayload(conversation,{items,draft,attachments},id,now=Date.now()){
  return {...(conversation||{}),id,items,draft:draft||'',draftAttachments:attachments||[],updatedAt:now};
 }
 // Older hosts ignore the query and return no preview; only then is the list filtered locally.
 function filterChats(chats,query){
  const list=Array.isArray(chats)?chats:[];
  const terms=String(query||'').toLocaleLowerCase().split(/\s+/).filter(Boolean);
  if(!terms.length||list.some(c=>'preview' in c))return list;
  return list.filter(c=>{const text=(c.title+' '+(c.preview||'')).toLocaleLowerCase();return terms.every(t=>text.includes(t));});
 }
 function chatWhen(updatedAt,now=Date.now()){
  const value=Number(updatedAt);if(!Number.isFinite(value)||value<=0)return '';
  const date=new Date(value),today=new Date(now);
  if(date.toDateString()===today.toDateString())return date.toLocaleTimeString('es-ES',{hour:'2-digit',minute:'2-digit'});
  return date.toLocaleDateString('es-ES',{day:'numeric',month:'short',...(date.getFullYear()!==today.getFullYear()?{year:'numeric'}:{})});
 }
 function chatRowMarkup(chat,{escape=plainEscape,current=false,now=Date.now()}={}){
  const title=chat.title||'Conversación',when=chatWhen(chat.updatedAt,now),count=Number(chat.messages)||0;
  const foot=[`${count} mensaje${count===1?'':'s'}`,when,chat.customTitle?'Título propio':''].filter(Boolean).join(' · ');
  return `<li class="cc-row${current?' is-current':''}" data-cc-row="${escape(chat.id)}"><button type="button" class="cc-row-open" data-cc-open="${escape(chat.id)}"${current?' aria-current="true"':''}><span class="cc-row-title">${escape(title)}</span>${chat.preview?`<span class="cc-row-preview">${escape(chat.preview)}</span>`:''}<span class="cc-row-foot">${escape(foot)}${current?' · <strong>Actual</strong>':''}</span></button><button type="button" class="cc-icon" data-cc-rename="${escape(chat.id)}" title="Renombrar" aria-label="Renombrar «${escape(title)}»">${glyphs.rename}</button></li>`;
 }
 function renameFormMarkup(chat,{escape=plainEscape}={}){
  const id=escape(chat.id);
  return `<li class="cc-row is-editing" data-cc-row="${id}"><form class="cc-rename" id="cc-rename-form" data-cc-rename-form="${id}"><label for="cc-rename-input">Título de la conversación</label><input id="cc-rename-input" name="title" maxlength="80" autocomplete="off" value="${escape(chat.customTitle||chat.title||'')}" placeholder="Vacío = título automático"><div class="cc-rename-actions"><button type="submit" class="primary">Guardar</button>${chat.customTitle?`<button type="button" data-cc-reset="${id}">Restablecer</button>`:''}<button type="button" data-cc-cancel>Cancelar</button></div></form></li>`;
 }
 const api={FOLD_LINES,newId,historyItems,messageMarkup,archivePayload,filterChats,chatWhen,chatRowMarkup,renameFormMarkup};
 if(typeof module!=='undefined'&&module.exports)module.exports=api;
 if(!root.document||typeof Agent==='undefined')return;

 const doc=root.document,esc=typeof escape==='function'?escape:plainEscape;
 const expanded=new Set();
 let switching=false;
 const panel={open:(()=>{try{return root.localStorage.getItem(PANEL_KEY)==='1';}catch{return false;}})(),query:'',chats:null,error:'',editing:null,seq:0,timer:0,loading:false};
 const announce=message=>typeof toast==='function'&&toast(message);
 const currentId=()=>state.agentConversation?.id||null;
 const itemById=id=>Agent.items.find(i=>String(i.id)===String(id));

 function measure(){
  for(const article of doc.querySelectorAll('.agent-messages .agent-message.cc-foldable')){
   const body=article.querySelector('.cc-fold-body'),toggle=article.querySelector('.cc-fold-toggle');
   if(!body||!toggle)continue;
   if(article.classList.contains('cc-expanded')){toggle.hidden=false;continue;}
   const over=body.scrollHeight-body.clientHeight>2;
   article.classList.toggle('cc-overflow',over);toggle.hidden=!over;
  }
 }
 let widthWatch=null,watchedWidth=0;
 function watchWidth(list){
  if(!list||widthWatch?.target===list)return;
  widthWatch?.observer.disconnect();
  const observer=new ResizeObserver(entries=>{const width=Math.round(entries[0].contentRect.width);if(width!==watchedWidth){watchedWidth=width;measure();}});
  observer.observe(list);widthWatch={observer,target:list};
 }
 function setExpanded(article,value){
  const id=article.dataset.agentMessage,toggle=article.querySelector('.cc-fold-toggle');
  if(typeof AgentViewport!=='undefined'){AgentViewport.following=false;AgentViewport.remember();}
  if(value)expanded.add(id);else expanded.delete(id);
  article.classList.toggle('cc-expanded',value);
  if(toggle){toggle.setAttribute('aria-expanded',String(value));toggle.textContent=value?'Contraer':'Expandir';toggle.hidden=false;}
  if(!value){measure();article.scrollIntoView({block:'nearest'});}
 }

 async function copyText(text){
  try{await root.navigator.clipboard.writeText(text);return;}catch{}
  const area=doc.createElement('textarea');area.value=text;area.setAttribute('readonly','');area.style.cssText='position:fixed;opacity:0;pointer-events:none';
  doc.body.append(area);area.select();const ok=doc.execCommand('copy');area.remove();
  if(!ok)throw Error('No se pudo copiar. Selecciona el texto y usa Ctrl+C.');
 }
 async function copyMessage(id){
  const article=doc.querySelector(`[data-agent-message="${CSS.escape(id)}"]`);
  const text=itemById(id)?.text||article?.querySelector('.agent-text')?.innerText||'';
  if(!text.trim())throw Error('Este mensaje no tiene texto que copiar.');
  await copyText(text);announce('Mensaje copiado.');
 }
 function selectMessage(id){
  const node=doc.querySelector(`[data-agent-message="${CSS.escape(id)}"] .agent-text`);if(!node)return;
  const range=doc.createRange();range.selectNodeContents(node);
  const selection=root.getSelection();selection.removeAllRanges();selection.addRange(range);
  announce('Texto seleccionado. Pulsa Ctrl+C para copiarlo.');
 }

 let menu=null;
 function closeMenu(restore){
  if(!menu)return;const {node,returnTo}=menu;menu=null;node.remove();
  if(restore&&returnTo?.isConnected)returnTo.focus({preventScroll:true});
 }
 function openMenu(article,event){
  closeMenu(false);
  const id=article.dataset.agentMessage,toggle=article.querySelector('.cc-fold-toggle');
  const entries=[['copy','Copiar mensaje'],['select','Seleccionar texto']];
  if(toggle&&!toggle.hidden)entries.push(['fold',article.classList.contains('cc-expanded')?'Contraer mensaje':'Expandir mensaje']);
  const node=doc.createElement('div');node.className='cc-menu';node.setAttribute('role','menu');node.setAttribute('aria-label','Acciones del mensaje');
  node.innerHTML=entries.map(([action,label])=>`<button type="button" role="menuitem" tabindex="-1" data-cc-menu="${action}">${esc(label)}</button>`).join('');
  doc.body.append(node);
  const keyboard=event.button!==2||(!event.clientX&&!event.clientY),box=article.getBoundingClientRect();
  const x=keyboard?box.left+16:event.clientX,y=keyboard?box.top+16:event.clientY;
  node.style.left=Math.max(8,Math.min(x,root.innerWidth-node.offsetWidth-8))+'px';
  node.style.top=Math.max(8,Math.min(y,root.innerHeight-node.offsetHeight-8))+'px';
  menu={node,id,article,returnTo:doc.activeElement&&doc.activeElement!==doc.body?doc.activeElement:article.querySelector('[data-cc-copy]')};
  node.querySelector('[role=menuitem]').focus({preventScroll:true});
 }
 async function runMenu(action){
  const {id,article}=menu;closeMenu(true);
  if(action==='copy')await copyMessage(id);
  if(action==='select')selectMessage(id);
  if(action==='fold'&&article.isConnected)setExpanded(article,!article.classList.contains('cc-expanded'));
 }

 // A plain right-click keeps the browser menu on links, code, media, form fields and selected text.
 doc.addEventListener('contextmenu',event=>{
  const article=event.target.closest?.('.agent-messages .agent-message');
  if(!article||event.shiftKey)return;
  if(event.target.closest('a,pre,code,img,video,audio,input,textarea,select,summary,details,[contenteditable]'))return;
  if(event.target.closest('button')&&!event.target.closest('.cc-actions'))return;
  const selection=root.getSelection();
  if(selection&&!selection.isCollapsed&&article.contains(selection.anchorNode))return;
  event.preventDefault();openMenu(article,event);
 });
 doc.addEventListener('pointerdown',event=>{if(menu&&!menu.node.contains(event.target))closeMenu(false);},true);
 doc.addEventListener('scroll',()=>closeMenu(false),true);
 root.addEventListener('resize',()=>closeMenu(false));
 root.addEventListener('blur',()=>closeMenu(false));

 function panelNode(){return doc.getElementById('cc-panel');}
 function storeOpen(){try{root.localStorage.setItem(PANEL_KEY,panel.open?'1':'0');}catch{}}
 function listMarkup(){
  if(panel.error)return `<div class="cc-state" role="alert"><p>No se pudieron cargar las conversaciones.</p><small>${esc(panel.error)}</small><button type="button" data-cc-panel="retry">Reintentar</button></div>`;
  if(!panel.chats)return '<p class="cc-state" aria-live="polite">Cargando conversaciones…</p>';
  const current=currentId(),rows=filterChats(panel.chats,panel.query);
  const unsaved=(Agent.items.length||Agent.draft.trim())&&!panel.chats.some(c=>c.id===current)&&!panel.query?'<li class="cc-row is-current cc-row-unsaved"><div class="cc-row-open" aria-current="true"><span class="cc-row-title">Conversación actual</span><span class="cc-row-foot">Se archivará al abrir otra o al empezar una nueva</span></div></li>':'';
  const body=rows.map(chat=>panel.editing===chat.id?renameFormMarkup(chat,{escape:esc}):chatRowMarkup(chat,{escape:esc,current:chat.id===current})).join('');
  if(!unsaved&&!body)return `<p class="cc-state">${panel.query?'Ninguna conversación coincide con la búsqueda.':'Aún no hay conversaciones archivadas.'}</p>`;
  return `<ul class="cc-list" aria-label="Conversaciones archivadas">${unsaved}${body}</ul>`;
 }
 function paintList(){
  const host=doc.getElementById('cc-panel-list');if(!host)return;
  const focused=doc.activeElement?.closest?.('#cc-panel-list [data-cc-row]')?.dataset.ccRow;
  host.innerHTML=listMarkup();host.setAttribute('aria-busy',String(panel.loading));
  if(panel.editing){const input=doc.getElementById('cc-rename-input');if(input){input.focus();input.select();}}
  else if(focused)host.querySelector(`[data-cc-row="${CSS.escape(focused)}"] button`)?.focus({preventScroll:true});
 }
 function paintPanel(){
  const node=panelNode(),content=doc.getElementById('content');
  content?.classList.toggle('cc-chats-open',panel.open&&!!node);
  doc.querySelectorAll('[data-parity="conversations"]').forEach(b=>{b.setAttribute('aria-expanded',String(panel.open));b.setAttribute('aria-controls','cc-panel');});
  if(!node)return;
  node.hidden=!panel.open;if(!panel.open)return;
  if(!node.firstElementChild){
   node.innerHTML=`<div class="cc-panel-head"><h2 id="cc-panel-title">Conversaciones</h2><button type="button" class="cc-icon" data-cc-panel="close" title="Cerrar" aria-label="Cerrar panel de conversaciones">${glyphs.close}</button></div><input type="search" id="cc-search" placeholder="Buscar conversaciones" aria-label="Buscar conversaciones" autocomplete="off"><p class="cc-hint">Al abrir otra, la actual se archiva con su borrador.</p><div id="cc-panel-list" class="cc-panel-list"></div>`;
   node.setAttribute('aria-labelledby','cc-panel-title');
  }
  const search=doc.getElementById('cc-search');if(search&&search.value!==panel.query)search.value=panel.query;
  paintList();
 }
 async function refresh(){
  const seq=++panel.seq;panel.loading=true;panel.error='';
  if(!panel.chats)paintList();
  try{
   const chats=await rpc('listChats',panel.query?{query:panel.query}:{});
   if(seq!==panel.seq)return;panel.chats=Array.isArray(chats)?chats:[];
  }catch(error){if(seq!==panel.seq)return;panel.error=error.message;}
  finally{if(seq===panel.seq)panel.loading=false;}
  paintList();
 }
 function toggle(force){
  panel.open=typeof force==='boolean'?force:!panel.open;storeOpen();
  if(!panel.open)panel.editing=null;
  if(panel.open&&view!=='assistant'&&typeof navigate==='function'){navigate('assistant');}
  paintPanel();
  if(panel.open){refresh();doc.getElementById('cc-search')?.focus({preventScroll:true});}
  else doc.querySelector('[data-parity="conversations"]')?.focus({preventScroll:true});
 }

 async function archiveCurrent(){
  if(!Agent.items.length&&!Agent.draft.trim()&&!Agent.attachments.length)return null;
  const id=currentId()||newId();
  const saved=await rpc('archiveChat',archivePayload(state.agentConversation,{items:Agent.items,draft:Agent.draft,attachments:Agent.attachments},id));
  const stable=saved?.id||id;
  state.agentConversation={...(state.agentConversation||{}),id:stable};
  if(typeof persist==='function')persist();
  return stable;
 }
 // Load first, then archive, then reset the model session. Any failure restores the visible chat,
 // its draft and attachments; the archived copy keeps the same id so a retry never duplicates it.
 async function open(id){
  if(Agent.busy||Agent.generating)throw Error('Espera a que termine el mensaje actual.');
  if(switching)throw Error('Ya se está abriendo otra conversación.');
  if(id===currentId()){announce('Ya estás en esta conversación.');return false;}
  switching=true;
  try{return await switchTo(id);}finally{switching=false;}
 }
 async function switchTo(id){
  if(Agent.initPromise){try{await Agent.initPromise;}catch{}}
  const chat=await rpc('loadChat',{id});
  const before={items:Agent.items,draft:Agent.draft,attachments:Agent.attachments.slice(),conversation:state.agentConversation,anchors:new Map(Agent.cardAnchors),batches:new Set(Agent.expandedBatches),expanded:new Set(expanded)};
  let reset=false;
  try{
   await archiveCurrent();
   before.conversation=state.agentConversation;
   await agentCall('new');reset=true;
   const items=Array.isArray(chat.items)?chat.items:[];
   await agentCall('restoreHistory',{items:historyItems(items)});
   Agent.items=items;Agent.draft=chat.draft||'';Agent.attachments=Array.isArray(chat.draftAttachments)?chat.draftAttachments:[];Agent.error='';
   Agent.cardAnchors.clear();for(const [key,index] of Object.entries(chat.anchors||{}))if(items[index])Agent.cardAnchors.set(key,items[index]);
   Agent.expandedBatches.clear();expanded.clear();
   state.agentConversation={...chat,id:chat.id||id};
  }catch(error){
   Object.assign(Agent,{items:before.items,draft:before.draft,attachments:before.attachments,cardAnchors:before.anchors,expandedBatches:before.batches});
   expanded.clear();before.expanded.forEach(v=>expanded.add(v));
   state.agentConversation=before.conversation;
   if(reset)try{await agentCall('restoreHistory',{items:historyItems(before.items)});}catch{}
   if(typeof persist==='function')persist();
   if(view==='assistant')renderAssistant();
   throw error;
  }
  if(typeof persist==='function')persist();
  doc.getElementById('dialog')?.open&&doc.getElementById('dialog').close();
  if(typeof AgentViewport!=='undefined')AgentViewport.latest();
  if(view==='assistant')renderAssistant();else navigate('assistant');
  if(panel.open)refresh();
  return true;
 }
 async function rename(id,title){
  const result=await rpc('renameChat',{id,title});
  const chat=panel.chats?.find(c=>c.id===id);
  if(chat)Object.assign(chat,{title:result?.title||chat.title,customTitle:result?.customTitle||null});
  panel.editing=null;paintList();
  doc.querySelector(`#cc-panel-list [data-cc-rename="${CSS.escape(id)}"]`)?.focus({preventScroll:true});
  announce(String(title||'').trim()?'Título actualizado.':'Título automático restablecido.');
 }

 doc.addEventListener('click',async event=>{
  const button=event.target.closest?.('button');if(!button||button.disabled)return;
  const data=button.dataset;
  try{
   if(data.ccMenu){event.preventDefault();await runMenu(data.ccMenu);return;}
   if(data.ccFold){const article=button.closest('.agent-message');if(article)setExpanded(article,!article.classList.contains('cc-expanded'));return;}
   if(data.ccCopy){await copyMessage(data.ccCopy);return;}
   if(data.ccSelect){selectMessage(data.ccSelect);return;}
   if(data.ccPanel==='close'){toggle(false);return;}
   if(data.ccPanel==='retry'){await refresh();return;}
   if(data.ccOpen){button.disabled=true;try{await open(data.ccOpen);}finally{if(button.isConnected)button.disabled=false;}return;}
   if(data.ccRename){panel.editing=data.ccRename;paintList();return;}
   if(data.ccCancel!==undefined){const id=panel.editing;panel.editing=null;paintList();doc.querySelector(`#cc-panel-list [data-cc-rename="${CSS.escape(id||'')}"]`)?.focus({preventScroll:true});return;}
   if(data.ccReset){button.disabled=true;await rename(data.ccReset,'');return;}
  }catch(error){announce(error.message);if(button.isConnected)button.disabled=false;}
 });
 doc.addEventListener('submit',async event=>{
  const form=event.target;if(form.id!=='cc-rename-form')return;event.preventDefault();
  const submit=form.querySelector('[type=submit]');submit.disabled=true;
  try{await rename(form.dataset.ccRenameForm,form.elements.title.value);}catch(error){announce(error.message);if(submit.isConnected)submit.disabled=false;}
 });
 doc.addEventListener('input',event=>{
  if(event.target.id!=='cc-search')return;
  panel.query=event.target.value.trim();panel.editing=null;clearTimeout(panel.timer);panel.timer=setTimeout(refresh,180);
 });
 doc.addEventListener('keydown',event=>{
  if(menu){
   const items=[...menu.node.querySelectorAll('[role=menuitem]')],index=items.indexOf(doc.activeElement);
   if(event.key==='Escape'){event.preventDefault();closeMenu(true);return;}
   if(event.key==='Tab'){closeMenu(true);return;}
   const next={ArrowDown:index+1,ArrowUp:index-1,Home:0,End:items.length-1}[event.key];
   if(next!==undefined){event.preventDefault();items[(next+items.length)%items.length].focus();}
   return;
  }
  if(event.key==='Escape'&&event.target.closest?.('#cc-panel')){
   event.preventDefault();
   if(panel.editing){const id=panel.editing;panel.editing=null;paintList();doc.querySelector(`#cc-panel-list [data-cc-rename="${CSS.escape(id)}"]`)?.focus();}
   else toggle(false);
  }
 });

 root.NexusChatContinuity={...api,
  panelPlaceholder:()=>'<aside id="cc-panel" class="cc-panel" hidden></aside>',
  message:item=>messageMarkup(item,{escape:esc,expanded:expanded.has(String(item.id))}),
  afterRender(){measure();watchWidth(doc.querySelector('.agent-messages'));paintPanel();},
  leaveView(){closeMenu(false);doc.getElementById('content')?.classList.remove('cc-chats-open');},
  isOpen:()=>panel.open,isSwitching:()=>switching,toggle,refresh,open,archiveCurrent,rename,measure,
  _panel:panel,_expanded:expanded
 };
})(typeof window!=='undefined'?window:globalThis);
