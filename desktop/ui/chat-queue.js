'use strict';

const AgentQueue=(()=>{
 const prefix='nexus:chat-queue:';
 const chat=()=>state.agentConversation?.id||null;
 const key=id=>prefix+id;
 const read=id=>{
  try{
   const stored=id&&JSON.parse(localStorage.getItem(key(id)));
   if(Array.isArray(stored?.items))return stored;
  }catch{}
  return {items:[],paused:false};
 };
 const lock=(id,fn)=>navigator.locks.request(key(id),()=>fn(read(id)));
 function save(id,queue){
  if(queue.items.length)localStorage.setItem(key(id),JSON.stringify(queue));
  else localStorage.removeItem(key(id));
 }
 const redraw=()=>{if(view==='assistant'||view==='editor')renderAssistant();};
 let draining=false,writing=false,active=null;
 const contextMatches=item=>item.project===(Agent.snapshot?.projectId||'');
 const busy=()=>Agent.busy||Agent.generating||Agent.cancelDraining||Agent.messageSending||window.NexusChatContinuity?.isSwitching();
 const has=()=>Boolean(read(chat()).items.length);

 async function enqueue(){
  const text=Agent.draft.trim();
  if(writing||!text||!Agent.snapshot||window.NexusChatContinuity?.isSwitching())return;
  writing=true;
  const draft=Agent.draft,attachments=Agent.attachments.slice(),project=Agent.snapshot.projectId||'';
  const id=chat()||crypto.randomUUID();
  try{
   if(!chat()){
    state.agentConversation={...state.agentConversation,id};
    await flush();
   }
   await lock(id,queue=>{
    if(chat()!==id)return;
    queue.items.push({id:crypto.randomUUID(),text,attachments,project});
    save(id,queue);
    if(Agent.draft===draft&&Agent.attachments.length===attachments.length&&attachments.every((file,index)=>file===Agent.attachments[index])){
     Agent.draft='';Agent.attachments=[];
    }
    redraw();
    $('#agent-input')?.focus({preventScroll:true});
   });
  }finally{writing=false;}
 }

 async function pause(id=chat()){
  if(!id||!read(id).items.length)return;
  await lock(id,queue=>{queue.paused=true;save(id,queue);});
  redraw();
 }

 async function drain(resuming=false){
  const id=chat();
  if(!id||draining||busy()||!read(id).items.length)return;
  draining=true;
  try{
   await navigator.locks.request(key(id)+':send',{ifAvailable:true},async dispatchLock=>{
    if(!dispatchLock)return;
    if(resuming)await lock(id,queue=>{queue.paused=false;save(id,queue);});
    while(chat()===id&&!busy()){
     const next=await lock(id,queue=>{
      const item=queue.items[0];
      if(chat()!==id||busy()||queue.paused||!item||item.sending||!contextMatches(item)||Agent.snapshot?.offline)return null;
      item.sending=true;
      save(id,queue);
      return item;
     });
     if(!next)break;
     active=next.id;redraw();
     const sent=await deliverAgentMessage(next.text,next.attachments);
     await flush();
     await lock(id,queue=>{
      queue.items=queue.items.filter(item=>item.id!==next.id);
      if(!sent)queue.paused=true;
      save(id,queue);
     });
     active=null;redraw();
     if(!sent)break;
    }
   });
  }finally{active=null;draining=false;redraw();}
 }

 function markup(){
  const queue=read(chat()),first=queue.items[0];
  if(!first)return '<section class="agent-queue" hidden></section>';
  const count=queue.items.length;
  const interrupted=first.sending&&!active;
  const differentProject=!contextMatches(first);
  const status=interrupted?'Envío por confirmar. Revisa el chat antes de continuar.':differentProject?'Vuelve al proyecto de este mensaje.':queue.paused?'Cola pausada':'';
  const rows=queue.items.map(item=>'<li data-agent-message="'+escape(item.id)+'"><span>'+escape(item.text)+'</span>'+
   (item.attachments.length?' <small> · '+item.attachments.length+' adj.</small>':'')+
   (item.sending&&active===item.id?' <small>Enviando…</small>':
    (item.sending?'':'<button type="button" data-agent-queue-edit="'+escape(item.id)+'">Editar</button>')+
    '<button type="button" data-agent-queue-remove="'+escape(item.id)+'">Quitar</button>')+'</li>').join('');
  return '<section class="agent-queue" aria-label="Cola de mensajes"><details>'+
   '<summary>'+count+' en cola</summary><ol>'+rows+'</ol></details>'+
   (status?'<small role="status">'+status+'</small>':'')+
   (!first.sending&&!differentProject&&!Agent.snapshot?.offline&&!busy()&&!draining?
    '<button type="button" data-agent-queue-resume>Reanudar</button>':'')+'</section>';
 }

 async function remove(itemId){
  const id=chat(),target=read(id).items.find(item=>item.id===itemId);
  if(!target)return;
  const removeItem=()=>lock(id,queue=>{
   queue.items=queue.items.filter(item=>item.id!==itemId||item.sending&&!target.sending);
   save(id,queue);
  });
  if(target.sending){
   await navigator.locks.request(key(id)+':send',{ifAvailable:true},async dispatchLock=>{
    if(!dispatchLock)throw Error('El mensaje se está enviando.');
    await removeItem();
   });
  }else await removeItem();
  redraw();
 }

 async function edit(itemId){
  const id=chat();
  const item=await lock(id,queue=>{
   const pending=queue.items.find(item=>item.id===itemId&&!item.sending);
   if(pending){queue.paused=true;save(id,queue);}
   return pending;
  });
  if(!item||chat()!==id)return;
  redraw();
  modal('<h2>Editar mensaje en cola</h2><form id="agent-queue-edit" data-chat="'+escape(id)+'" data-item="'+escape(itemId)+'">'+
   '<label>Mensaje<textarea name="text" required>'+escape(item.text)+'</textarea></label>'+
   item.attachments.map(file=>'<label class="agent-check"><input type="checkbox" name="attachment" value="'+escape(file.id)+'" checked> '+escape(file.name)+'</label>').join('')+
   '<button class="primary">Guardar cambios</button></form>');
 }

 async function saveEdit(form){
  const id=form.dataset.chat,fields=new FormData(form),text=String(fields.get('text')||'').trim();
  if(!text)throw Error('Escribe un mensaje.');
  if(chat()!==id)throw Error('La conversación cambió.');
  await lock(id,queue=>{
   const item=queue.items.find(entry=>entry.id===form.dataset.item&&!entry.sending);
   if(!item)throw Error('El mensaje ya no está en cola.');
   item.text=text;
   const retained=new Set(fields.getAll('attachment'));
   item.attachments=item.attachments.filter(file=>retained.has(file.id));
   save(id,queue);
  });
  redraw();
 }

 function submit(){
  return Agent.messageSending||draining||has()?enqueue():sendAgent();
 }
 window.addEventListener('storage',event=>{if(event.key===key(chat()))redraw();});
 return {submit,has,markup,drain,pause,edit,remove,saveEdit,isDraining:()=>draining};
})();
