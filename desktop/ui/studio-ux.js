'use strict';
// Local workspace feedback. Retains the existing generation confirmation flow.
(()=>{
 let helpOpen=false,scheduled=false;
 const labels={saved:'Guardado',pending:'Cambios pendientes',saving:'Guardando…',error:'Sin guardar'};
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
  const show=view==='canvas'&&!$('#dialog').open&&(helpOpen||firstUse);
  let panel=$('#studio-start-guide');
  if(!show){panel?.remove();return;}
  if(panel)return;
  panel=document.createElement('section');panel.id='studio-start-guide';panel.setAttribute('aria-label','Primeros pasos');
  panel.innerHTML='<div class="studio-guide-heading"><strong>Tu primera creación</strong><button type="button" data-studio-help="dismiss" aria-label="Cerrar guía">Cerrar</button></div><ol><li><button type="button" data-studio-help="model">Elige un modelo</button><span>Imagen o vídeo, con las entradas que necesitas.</span></li><li><button type="button" data-studio-help="prompt">Describe tu idea</button><span>Añade referencias y ajusta sus opciones.</span></li><li><button type="button" data-studio-help="results">Revisa tus resultados</button><span>Consulta el coste antes de generar. Tus creaciones quedan en la galería.</span></li></ol>';
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
  saveFeedback();guide();
  const toggle=$('[data-studio-help="toggle"]'),expanded=String(!!$('#studio-start-guide'));
  if(toggle.getAttribute('aria-expanded')!==expanded)toggle.setAttribute('aria-expanded',expanded);
 }
 function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(mount);}}
 async function saveNow(){try{await flush();}catch(error){toast('No se han guardado los cambios: '+error.message);}}
 window.addEventListener('nexus:save-status',saveFeedback);
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
