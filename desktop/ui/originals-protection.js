/* Durable local originals and verified Drive copies are separate states. */
(function(root){
 'use strict';
 function summary(value={}){
  const count=key=>Number.isInteger(value[key])&&value[key]>=0?value[key]:null;
  const archived=count('archived'),pendingCloud=count('pendingCloud');
  return {archived,pending:count('pending'),failed:count('failed'),pendingCloud,
   cloudVerified:archived!==null&&pendingCloud!==null?Math.max(0,archived-pendingCloud):null};
 }
 if(typeof module==='object'&&module.exports){module.exports={summary};return;}
 let refreshing=null,pending=false;
 const write=(node,value)=>{if(node&&node.textContent!==value)node.textContent=value;};
 function paint(value){
  const section=document.getElementById('originals-protection');if(!section)return;
  const data=summary(value);
  for(const [key,count] of Object.entries(data))write(section.querySelector('[data-originals-count="'+key+'"]'),count===null?'—':new Intl.NumberFormat('es').format(count));
  write(section.querySelector('[data-originals-status]'),data.pending?'Protegiendo los originales disponibles…':data.failed?'Hay originales pendientes de recuperar.':data.archived?'Protección local actualizada.':'Tus originales se protegerán al estar disponibles.');
  section.querySelector('[data-originals-op=retry]').disabled=pending||!data.failed;
  section.querySelector('[data-originals-op=scan]').disabled=pending;
 }
 async function refresh(){
  if(refreshing)return refreshing;
  refreshing=rpc('originalArchive',{op:'status'}).then(value=>{paint(value);return value;}).catch(error=>{write(document.querySelector('[data-originals-status]'),error.message||'No se pudo consultar la protección de originales.');}).finally(()=>{refreshing=null;});
  return refreshing;
 }
 function mount(){
  const hub=document.getElementById('continuity-hub');if(!hub||document.getElementById('originals-protection'))return;
  const section=document.createElement('section');section.id='originals-protection';section.className='originals-protection continuity-channel';
  section.innerHTML='<h3>Tus originales, protegidos</h3><p class="continuity-detail">Conserva el archivo completo en este PC. La copia en Drive se comprueba por separado, después de sincronizar.</p><dl class="originals-counts"><div><dt>Verificados localmente</dt><dd data-originals-count="archived">—</dd></div><div><dt>En cola</dt><dd data-originals-count="pending">—</dd></div><div><dt>Por recuperar</dt><dd data-originals-count="failed">—</dd></div><div><dt>Copias verificadas en Drive</dt><dd data-originals-count="cloudVerified">—</dd></div><div><dt>Pendientes de verificar en Drive</dt><dd data-originals-count="pendingCloud">—</dd></div></dl><p class="hint" data-originals-status role="status">Consultando protección…</p><div class="continuity-actions"><button type="button" data-originals-op="scan">Proteger originales</button><button type="button" data-originals-op="retry" disabled>Reintentar pendientes</button></div>';
  hub.append(section);refresh();
 }
 const previous=renderSettings;renderSettings=function(){previous();mount();};
 window.addEventListener('nexus:continuity-status',()=>{if(!document.hidden&&document.getElementById('originals-protection'))refresh();});
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&document.getElementById('originals-protection'))refresh();});
 document.addEventListener('click',async event=>{
  const button=event.target.closest('[data-originals-op]');if(!button||button.disabled)return;
  pending=true;button.disabled=true;
  try{await rpc('originalArchive',{op:button.dataset.originalsOp});toast('Protección solicitada. Puedes seguir trabajando.');window.dispatchEvent(new CustomEvent('nexus:library-changed'));}
  catch(error){toast(error.message);}
  finally{pending=false;await refresh();}
 });
 root.NexusOriginals={summary,refresh};if(view==='settings')mount();
})(typeof globalThis==='object'?globalThis:this);
