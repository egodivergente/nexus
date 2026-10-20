'use strict';
(()=>{
 let timer=null;
 const labels={idle:'Sin conectar',ready:'Conectado',syncing:'Sincronizando…',synced:'Al día',partial:'Faltan originales por recuperar',conflicts:'Hay versiones para revisar',waiting:'Esperando a que termine el turno',retry:'Pendiente de reintento',authorization_required:'Vuelve a conectar Google',unavailable:'No disponible en el laboratorio'};
 const previous=renderSettings;
 async function refresh(){
  const section=document.querySelector('#nexus-drive-sync');if(!section){clearInterval(timer);timer=null;return;}
  try{
   const state=await rpc('driveSync',{op:'status'});
   section.querySelector('[data-sync-status]').textContent=state.connected&&state.state==='idle'?'Conectado':labels[state.state]||'Pendiente';
   section.querySelector('[data-sync-message]').textContent=state.message||(state.missingMedia?`${state.missingMedia} archivos necesitan recuperar su original.`:'Proyectos y archivos compartidos entre tus dispositivos.');
   section.querySelector('[data-sync=connect]').hidden=!state.configured||(state.connected&&state.state!=='authorization_required');
   section.querySelector('[data-sync=run]').hidden=!state.connected;
   section.querySelector('[data-sync=disconnect]').hidden=!state.connected;
   section.querySelector('[data-sync=enabled]').checked=state.enabled;
   const conflicts=section.querySelector('[data-sync-conflicts]');conflicts.replaceChildren();
   for(const conflict of state.conflicts||[]){
    const group=document.createElement('div'),title=document.createElement('p');title.textContent=`Versiones conservadas: ${conflict.key}`;group.append(title);
    for(const version of conflict.versions){const button=document.createElement('button');button.textContent=(version.value?.name||version.value?.displayName||version.value?.title||(version.value?'Conservar esta versión':'Confirmar retirada'))+' · '+(version.device||'Dispositivo')+(version.at?' · '+new Date(version.at).toLocaleString():'');button.dataset.sync='resolve';button.dataset.key=conflict.key;button.dataset.revision=version.id;group.append(button);}
    conflicts.append(group);
   }
  }catch{section.querySelector('[data-sync-status]').textContent='No se pudo consultar la conexión';}
 }
 renderSettings=function(){
  previous();
  const section=document.createElement('section');section.id='nexus-drive-sync';section.className='sheet';
  section.innerHTML='<h2>Sincronización</h2><p data-sync-status role="status">Consultando…</p><p class="hint" data-sync-message></p><div class="row"><button data-sync="configure">Configurar Google</button><button data-sync="connect" hidden>Conectar Google Drive</button><button data-sync="run" hidden>Sincronizar ahora</button><button data-sync="disconnect" hidden>Desconectar</button></div><label><input type="checkbox" data-sync="enabled"> Sincronizar automáticamente</label><div data-sync-conflicts></div>';
  document.querySelector('#settings-container')?.append(section);
  refresh();clearInterval(timer);timer=setInterval(refresh,5000);
 };
 document.addEventListener('click',async event=>{
  const button=event.target.closest('button[data-sync]');if(!button)return;button.disabled=true;
  try{
   let op=button.dataset.sync,args={};
   const authorizationWindow=op==='connect'?window.open('about:blank','_blank'):null;
   if(op==='configure'){const files=await rpc('pick');if(files.length!==1)return;args.id=files[0].id;}
   if(op==='resolve')args={key:button.dataset.key,revision:button.dataset.revision};
   let result;
   try{result=await rpc('driveSync',{op,...args});}catch(error){authorizationWindow?.close();throw error;}
   if(result.url){if(authorizationWindow){authorizationWindow.opener=null;authorizationWindow.location.replace(result.url);}else{const link=document.createElement('a');link.href=result.url;link.target='_blank';link.rel='noopener noreferrer';link.textContent='Continuar en Google';button.parentElement.append(link);}}
   await refresh();
  }catch(error){toast(error.message);}finally{button.disabled=false;}
 });
 document.addEventListener('change',async event=>{if(event.target.matches('[data-sync=enabled]')){try{await rpc('driveSync',{op:'enabled',enabled:event.target.checked});}catch(error){toast(error.message);}await refresh();}});
})();
