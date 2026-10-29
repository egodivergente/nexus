'use strict';
// File.slice keeps memory bounded for 4 GiB videos. RPC payloads stay below 20 MiB.
window.NexusImports = (() => {
 const maxBytes=4*1024**3;
 let busy=false;
 function base64(bytes){let text='';for(let i=0;i<bytes.length;i+=32768)text+=String.fromCharCode(...bytes.subarray(i,i+32768));return btoa(text);}
 async function importFile(file,onProgress=()=>{}){
  if(!file.size||file.size>maxBytes)throw Error('El archivo debe tener contenido y no superar los 4 GiB.');
  const started=await rpc('importBegin',{name:file.name,size:file.size});
  try{
   for(let offset=0;offset<file.size;offset+=started.chunk_bytes){
    const block=new Uint8Array(await file.slice(offset,offset+started.chunk_bytes).arrayBuffer());
    const result=await rpc('importChunk',{import_id:started.import_id,offset,content:base64(block)});
    onProgress(result.received,file.size);
   }
   return await rpc('importFinish',{import_id:started.import_id});
  }catch(error){try{await rpc('importAbort',{import_id:started.import_id});}catch{}throw error;}
 }
 async function library(files){
  if(busy)throw Error('Espera a que termine la importación.');
  if(Agent.busy||Agent.generating)throw Error('Espera a que termine el turno del agente.');
  busy=true;
  const folderContext=window.NexusGallery?.importContext;
  try{
   await ensureAssistantReady();
   const projectId=folderContext&&Object.hasOwn(folderContext,'projectId')?folderContext.projectId:Agent.snapshot.projectId||null;
   for(const file of files){
    const item=await importFile(file,(done,total)=>toast(`Importando ${file.name}: ${Math.round(done/total*100)} %`));
    const kind=item.mime.startsWith('video/')?'VIDEO':item.mime.startsWith('image/')?'IMAGE':item.mime.startsWith('audio/')?'AUDIO':'OTHER';
    await agentCall('importAssets',{assets:[{id:'asset_'+item.id.split('.')[0],uri:item.url,type:kind,role:'REFERENCE',source:'IMPORTED',displayName:item.name,mimeType:item.mime,sizeBytes:item.size,createdAt:Date.now(),projectId}]});
   }
   window.dispatchEvent(new CustomEvent('nexus:library-changed'));await window.NexusGallery?.refresh();toast('Archivos importados en Biblioteca.');if(view==='library')render();
  }finally{busy=false;}
 }
 // Library import is available without choosing a generation model or project first.
 function controls(){
  if(view!=='library')return;
  if(document.querySelector('[data-import-gallery]'))return;
  const toolbar=document.querySelector('#header-actions');if(!toolbar)return;
  const button=document.createElement('button');button.dataset.importGallery='true';button.textContent='Importar · hasta 4 GiB';
  button.onclick=()=>{const picker=document.createElement('input');picker.type='file';picker.multiple=true;picker.onchange=()=>library([...picker.files]).catch(error=>toast(error.message));picker.click();};toolbar.append(button);
 }
 new MutationObserver(controls).observe(document.querySelector('main'),{childList:true,subtree:true});
 document.addEventListener('dragover',event=>{if(view==='library'&&event.dataTransfer?.types.includes('Files'))event.preventDefault();});
 document.addEventListener('drop',event=>{if(view!=='library'||!event.dataTransfer?.files.length)return;event.preventDefault();event.stopImmediatePropagation();library([...event.dataTransfer.files]).catch(error=>toast(error.message));},true);
 return {importFile,library,maxBytes};
})();
