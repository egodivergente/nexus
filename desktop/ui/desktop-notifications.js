'use strict';
(()=>{
 const seen=new Map(state.jobs.map(job=>[job.id,job.status]));
 const enabled=()=>!!state.desktopNotifications&&'Notification' in window&&Notification.permission==='granted';
 function sync(){const b=document.querySelector('[data-desktop-notifications]');if(b)b.setAttribute('aria-checked',String(enabled()));}
 function controls(){if(view==='settings'&&!document.querySelector('[data-desktop-notifications]'))document.querySelector('#parity-settings')?.insertAdjacentHTML('beforeend','<button type="button" class="nx-switch" role="switch" aria-checked="'+enabled()+'" data-desktop-notifications><span class="nx-switch-track" aria-hidden="true"><span class="nx-switch-thumb"></span></span><span>Avisarme cuando termine una creación</span></button>');}
 new MutationObserver(controls).observe(document.querySelector('#content'),{childList:true,subtree:true});
 document.addEventListener('click',async event=>{if(!event.target.closest('[data-desktop-notifications]'))return;try{if(enabled()){state.desktopNotifications=false;persist();sync();toast('Avisos de finalización desactivados.');return;}if(!('Notification' in window))throw Error('Este navegador no admite avisos.');const permission=await Notification.requestPermission();state.desktopNotifications=permission==='granted';persist();sync();toast(state.desktopNotifications?'Avisos de finalización activados.':'Puedes permitirlos en la configuración de Edge.');}catch(error){toast(error.message);}});
 setInterval(()=>{for(const job of state.jobs){const previous=seen.get(job.id);seen.set(job.id,job.status);if(!previous||previous===job.status||!['COMPLETED','FAILED'].includes(job.status))continue;const title=job.status==='COMPLETED'?'Nexus · Creación terminada':'Nexus · Revisar encargo';if(state.desktopNotifications&&Notification.permission==='granted'){const notice=new Notification(title,{body:job.name||'Tu encargo se ha actualizado.',tag:job.id,icon:'/favicon.ico'});notice.onclick=()=>{window.focus();notice.close();navigate('history');};}else if(!document.hidden)toast(title);}},5000);
 controls();
})();
