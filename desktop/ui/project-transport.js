'use strict';
const beforeTransportSettings=renderSettings;
renderSettings=function(){
 beforeTransportSettings();
 const section=document.createElement('section');section.className='sheet';section.innerHTML='<h2>Proyectos portables</h2><p class="hint">Exporta medios, memoria y montaje con su historial. La importación crea una copia independiente.</p><button data-transport="import">Importar proyecto</button><button data-transport="export">Exportar proyecto activo</button>';
 $('#settings-container')?.append(section);
 if(sessionStorage.getItem('nexus-project-import'))section.insertAdjacentHTML('beforeend','<button data-transport="resume">Reanudar importación pendiente</button>');
};
document.addEventListener('click',async e=>{
 const button=e.target.closest('[data-transport]');if(!button)return;
 button.disabled=true;
 try{
  if(Agent.busy||Agent.generating||Corte.busy)throw Error('Espera a que termine el trabajo actual.');
  await ensureAssistantReady();await flush();
  if(button.dataset.transport==='export'){
   if(!Agent.snapshot.projectId)throw Error('Selecciona un proyecto antes de exportar.');
   const result=await rpc('portableProject',{op:'export',projectId:Agent.snapshot.projectId});
   const link=document.createElement('a');link.href=result.url;link.download=result.name;link.click();toast('Proyecto portable exportado.');
  }else{
   let saved;
   if(button.dataset.transport==='resume')saved=JSON.parse(sessionStorage.getItem('nexus-project-import')||'null');
   else{
    const files=await rpc('pick');if(!files.length)return;
    if(files.length!==1||!files[0].name.toLowerCase().endsWith('.zip'))throw Error('Selecciona un archivo ZIP de proyecto.');
    saved={request_id:crypto.randomUUID(),id:files[0].id};
   }
   if(!saved)throw Error('No hay una importación pendiente.');
   const {request_id,id}=saved;
   // Keep the same receipt available if the bridge disconnects after import.
   sessionStorage.setItem('nexus-project-import',JSON.stringify({request_id,id}));
   const result=await rpc('portableProject',{op:'import',request_id,id});
   sessionStorage.removeItem('nexus-project-import');
   await agentCall('project',{id:result.projectId});Corte.loaded=false;Corte.snapshot=null;Corte.projectId=null;navigate('editor');toast('Proyecto importado como copia independiente.');
  }
 }catch(error){toast(error.message);}
 finally{button.disabled=false;}
});
