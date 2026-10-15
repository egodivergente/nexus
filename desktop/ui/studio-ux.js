'use strict';
// Local workspace feedback. Retains the existing generation confirmation flow.
(()=>{
 let helpOpen=false,scheduled=false;
 const labels={saved:'Guardado',pending:'Cambios pendientes',saving:'Guardando…',error:'Sin guardar'};
 const operations={model:'Cargando los controles del modelo…',quote:'Consultando el coste…',send:'Preparando y enviando la solicitud…',recovery:'Comprobando el trabajo existente…'};
 function canvasFeedback(){
  const content=$('#content');if(!content)return;
  const active=view==='canvas',operation=active?CanvasFeedback.operation:null;
  const ariaBusy=String(!!operation);if(content.getAttribute('aria-busy')!==ariaBusy)content.setAttribute('aria-busy',ariaBusy);
  let error=active?CanvasFeedback.error:null;
  if(error?.key&&error.key!==quoteKey())error=null;
  const job=error?.jobId?state.jobs.find(j=>j.id===error.jobId):null;
  if(job?.status==='COMPLETED')error=null;
  let panel=$('#studio-canvas-feedback');
  if(!operation&&!error){panel?.remove();return;}
  if(operation==='model'&&configuring&&!error){
   panel?.remove();const status=content.querySelector('.empty p');
   status?.setAttribute('role','status');status?.setAttribute('aria-live','polite');return;
  }
  const command=$('.creative-command'),host=command||$('.canvas .sendbar')||content;
  if(panel&&panel.parentElement!==host){panel.remove();panel=null;}
  if(!panel){panel=document.createElement('section');panel.id='studio-canvas-feedback';panel.setAttribute('aria-label','Estado de la creación');if(command)host.append(panel);else host.prepend(panel);}
  const key=JSON.stringify([operation,error,job?.providerId,job?.uncertain]);if(panel.dataset.feedback===key)return;panel.dataset.feedback=key;
  panel.replaceChildren();
  if(operation){const progress=document.createElement('p');progress.className='studio-operation';progress.setAttribute('role','status');progress.setAttribute('aria-live','polite');progress.textContent=operations[operation]||'Comprobando…';panel.append(progress);}
  if(!error)return;
  const notice=document.createElement('div');notice.className='studio-recovery-notice';notice.setAttribute('role','status');notice.setAttribute('aria-live','polite');
  const title=document.createElement('strong');title.textContent={model:'No se pudo abrir el modelo',quote:'Coste sin confirmar',send:job?.uncertain?'Envío por comprobar':'No se pudo completar el envío'}[error.kind]||'No se pudo completar la operación';
  const reason=document.createElement('p');reason.textContent=error.message;
  const help=document.createElement('p');help.className='hint';help.textContent=error.kind==='model'?'Tu prompt se conserva. Reintenta cargar sus controles o elige otro modelo.':error.kind==='quote'?'Puedes consultar otra vez el coste o revisar el proveedor en Ajustes. No se ha enviado una generación.':job?'Comprueba este trabajo en Actividad antes de repetir el envío.':'Revisa los campos y la configuración del proveedor antes de volver a enviar.';
  const actions=document.createElement('div');actions.className='studio-recovery-actions';
  const add=(action,label)=>{const b=document.createElement('button');b.type='button';b.dataset.canvasRecovery=action;b.textContent=label;b.disabled=!!operation;actions.append(b);};
  if(error.kind==='model')add('model','Reintentar modelo');
  if(error.kind==='quote')add('quote','Consultar otra vez');
  if(error.kind!=='send')add('choose','Cambiar modelo');
  if(error.kind==='send'&&job){add('activity','Ver trabajo en Actividad');if(job.providerId==='google_flow')add('recover','Comprobar trabajo Flow');}
  add('settings','Abrir Ajustes');add('dismiss','Cerrar aviso');
  notice.append(title,reason,help,actions);panel.append(notice);
 }
 function saveFeedback(){
  const output=$('#studio-save-state'),retry=$('#studio-save-retry');if(!output)return;
  const label=labels[SaveStatus.status]||labels.pending;
  if(output.textContent!==label)output.textContent=label;
  if(output.dataset.status!==SaveStatus.status)output.dataset.status=SaveStatus.status;
  const title=SaveStatus.error?'No se pudo guardar: '+SaveStatus.error+'. Reintenta el guardado.':label;
  if(output.title!==title)output.title=title;
  retry.hidden=SaveStatus.status!=='error';
 }
 function guide(){
  const firstUse=initial&&!state.jobs.length&&!state.draft.prompt&&!state.studioHelp?.dismissed;
  const show=view==='canvas'&&!configuring&&!$('#dialog').open&&CanvasFeedback.error?.kind!=='model'&&(helpOpen||firstUse);
  let panel=$('#studio-start-guide');
  if(!show){panel?.remove();return;}
  if(panel)return;
  const ver=(typeof initial!=='undefined'&&initial?.version)?('v'+String(initial.version).replace(/^v/i,'')):'v1.9.5-agent-desktop';
  panel=document.createElement('section');panel.id='studio-start-guide';panel.setAttribute('aria-label','Primeros pasos');
  panel.innerHTML='<div class="studio-guide-heading"><div class="nx-guide-brand"><img src="branding/nexus-logo.png" alt="Logo oficial de NEXUS" width="52" height="50"><div><strong>Tu primera creación</strong><span class="nx-version-badge" data-nx-version>'+ver+'</span></div></div><button type="button" data-studio-help="dismiss" aria-label="Cerrar guía">Cerrar</button></div><ol><li><button type="button" data-studio-help="model">Elige un modelo</button><span>Imagen o vídeo, con las entradas que necesitas.</span></li><li><button type="button" data-studio-help="prompt">Describe tu idea</button><span>Añade referencias y ajusta sus opciones.</span></li><li><button type="button" data-studio-help="results">Revisa tus resultados</button><span>Consulta el coste antes de generar. Tus creaciones quedan en la galería.</span></li></ol>';
  $('#content').prepend(panel);
 }
 function mount(){
  scheduled=false;if(!initial)return;
  const footer=$('#creative-status');if(!footer)return;
  if(!$('#studio-workspace-status')){
   const group=document.createElement('div');group.id='studio-workspace-status';
   group.innerHTML='<output id="studio-save-state" role="status" aria-live="polite" aria-atomic="true"></output><button type="button" id="studio-save-retry" hidden>Reintentar guardado</button><button type="button" data-studio-help="toggle" aria-controls="studio-start-guide" aria-expanded="false">Guía</button>';
   footer.insertBefore(group,footer.querySelector('.creative-shortcut'));
  }
  saveFeedback();canvasFeedback();guide();
  const toggle=$('[data-studio-help="toggle"]'),expanded=String(!!$('#studio-start-guide'));
  if(toggle.getAttribute('aria-expanded')!==expanded)toggle.setAttribute('aria-expanded',expanded);
 }
 function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(mount);}}
 async function saveNow(){try{await flush();}catch(error){toast('No se han guardado los cambios: '+error.message);}}
 window.addEventListener('nexus:save-status',saveFeedback);
 window.addEventListener('nexus:canvas-status',schedule);
 new MutationObserver(schedule).observe($('main'),{childList:true,subtree:true});
 document.addEventListener('click',e=>{
  if(e.target.closest('#studio-save-retry')){saveNow();return;}
  const action=e.target.closest('[data-studio-help]')?.dataset.studioHelp;if(!action)return;
  if(action==='dismiss'||action==='toggle'&&$('#studio-start-guide')){
   helpOpen=false;state.studioHelp={...state.studioHelp,dismissed:true};persist();
  }else if(action==='toggle'){helpOpen=true;navigate('canvas');}
  else if(action==='model'){if(typeof studioPicker==='function')studioPicker();else navigate('catalog');}
  else if(action==='prompt'){if(view!=='canvas')navigate('canvas');$('#prompt')?.focus();}
  else if(action==='results'){helpOpen=false;Studio.tab='results';navigate('library');}
  schedule();
 });
 document.addEventListener('click',async e=>{
  const b=e.target.closest('[data-canvas-recovery]');if(!b||b.disabled)return;
  const error=CanvasFeedback.error;
  try{
   switch(b.dataset.canvasRecovery){
    case 'model':if(error?.kind==='model')await openModel(error.id,error.reset);break;
    case 'quote':await getQuote();break;
    case 'choose':studioPicker();break;
    case 'settings':navigate('settings');break;
    case 'dismiss':setCanvasFeedback({error:null});break;
    case 'activity':Studio.tab='activity';navigate('library');if(error?.jobId)studioDetails(error.jobId);break;
    case 'recover':if(error?.jobId){setCanvasFeedback({operation:'recovery'});try{await recoverFlowJob(error.jobId);if(!state.jobs.find(j=>j.id===error.jobId)?.uncertain)setCanvasFeedback({error:null});}finally{setCanvasFeedback({operation:null});}}break;
   }
  }catch(reason){toast(reason.message);if(error)setCanvasFeedback({error:{...error,message:reason.message}});}
 });
 document.addEventListener('input',schedule);
 document.addEventListener('change',schedule);
 document.addEventListener('keydown',e=>{
  if(e.isComposing||e.repeat||e.altKey||!(e.ctrlKey||e.metaKey))return;
  if(e.key.toLowerCase()==='s'){e.preventDefault();e.stopImmediatePropagation();saveNow();return;}
  if(e.key!=='Enter'||view!=='canvas'||e.target.closest('#agent-compose')||document.querySelector('dialog[open]'))return;
  e.preventDefault();e.stopImmediatePropagation();
  const button=$('#generate');
  if(button&&!button.disabled&&!busy)button.click();else toast('Elige un modelo y completa los campos antes de generar.');
 },true);
 window.NexusStudioUX={version:1};mount();
})();
