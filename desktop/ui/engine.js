(function(root){'use strict';
const valid=v=>typeof v==='string'&&v.trim()&&!['null','undefined','-','–','—'].includes(v.trim().toLowerCase())&&/[\p{L}\p{N}]/u.test(v);
const labels={prompt:'Prompt',negative_prompt:'Evitar',aspect_ratio:'Relación de aspecto',ratio:'Formato',resolution:'Resolución',quality:'Calidad',duration:'Duración',num_images:'Imágenes',outputCount:'Imágenes',seed:'Semilla',character_orientation:'Orientación del personaje',keep_original_sound:'Conservar sonido original',generate_audio:'Generar sonido',guidance_scale:'Fidelidad al prompt',steps:'Pasos',num_inference_steps:'Pasos',image:'Imagen de entrada',images:'Imágenes de referencia',video:'Vídeo de entrada',referenceImages:'Referencias',reference_images:'Referencias',size:'Tamaño',enable_safety_checker:'Filtro de seguridad',prompt_enhancement:'Mejorar prompt'};
const label=k=>labels[k]||k.replace(/[_.]/g,' ').replace(/([a-z])([A-Z])/g,'$1 $2').replace(/^./,s=>s.toUpperCase());
function normalize(input,schema){
 const m=structuredClone(input);m.fields=[];m.unsupported=[];
 if(m.providerId==='crun'&&!m.providerModelId)m.providerModelId=({'crun/kling-v3-std':'kling/v3','crun/kling-v3-pro':'kling/v3','crun/kling-v3-motion-control-std':'kling/v3-motion-control','crun/kling-v3-motion-control-pro':'kling/v3-motion-control'})[m.id];
 if(!m.contractEndpoint&&m.nativeRequiredFields?.length)m.promptRequired=m.nativeRequiredFields.includes('prompt');
 if(schema){
  const s=schema.components?.schemas?.Input;if(!s?.properties)throw Error('El esquema no contiene una definición Input compatible.');
  m.parameters=[];m.promptRequired=(s.required||[]).includes('prompt');m.schemaLoaded=true;
  for(const [key,p] of Object.entries(s.properties)){
   if(!valid(key)||key.startsWith('__')||['prompt','model'].includes(key))continue;
   const required=(s.required||[]).includes(key);
   if(!p||typeof p!=='object'){if(required)m.unsupported.push(label(key));continue;}
   const media=/^(image|images|image_url|image_urls|input_image|input_images|input_image_url|input_image_urls|reference_image_url|reference_image_urls|reference_images|ref_images|first_frame_image|start_image|last_image|last_frame_image|end_image|video|videos|video_url|video_urls|input_video|input_video_url|source_video|source_video_url|reference_video_urls|reference_videos|audio|audio_url|mask|mask_image|mask_url|refers)$/i.test(key);
   if(media&&!p.enum&&['array','string'].includes(p.type)){
    m.fields.push({key,label:label(key),mime:/video/.test(key)?'video/*':/audio/.test(key)?'audio/*':'image/*',required,maximum:p.type==='array'?(p.maxItems||m.maxImages||10):1,encoding:key==='refers'?'atlas-references':p.type==='array'?'array':'string',minItems:p.minItems||0});continue;
   }
   if(!['string','integer','number','boolean'].includes(p.type)){if(required)m.unsupported.push(label(key));continue;}
   const options=(p.enum||[]).filter(v=>valid(String(v))).map(String);
   if(p.enum&&!options.length){if(required)m.unsupported.push(label(key));continue;}
   m.parameters.push({key,displayName:label(key),nativeType:p.type,type:p.type==='boolean'?'BOOLEAN':options.length?'ENUM':'TEXT',options,defaultValue:p.default==null?'':String(p.default),isRequired:required,minValue:p.minimum,maxValue:p.maximum,description:p.description||''});
  }
  for(const key of s.required||[])if(valid(key)&&!['prompt','model'].includes(key)&&!s.properties[key])m.unsupported.push(label(key));
 }else{
  m.parameters=(m.parameters||[]).filter(p=>valid(p.key)&&valid(p.displayName)).filter((p,i,a)=>a.findIndex(x=>x.key===p.key)===i);
  m.fields=(m.contractMedia||[]).map(f=>({...f}));
  if(!m.contractEndpoint){for(const [key,mime,maximum,encoding] of [[m.firstFrameField,'image/*',m.firstFrameField?.endsWith('urls')?(m.maxImages||1):1,m.firstFrameField?.endsWith('urls')?'array':'string'],[m.lastFrameField,'image/*',1,'string'],[m.imagesArrayField,'image/*',m.maxImages||10,m.structuredImageReferences?'atlas-references':'array'],[m.videoField,'video/*',1,m.videoField?.endsWith('urls')?'array':'string'],[m.videosArrayField,'video/*',m.maxVideos||1,'array']]){
   if(key&&!m.fields.some(f=>f.key===key))m.fields.push({key,label:label(key),mime,maximum,encoding,required:(m.nativeRequiredFields||[]).includes(key)||(m.isMediaRequired&&key===m.firstFrameField)||(m.supportsVideoInput&&key===m.videoField)});
  }}
 }
 return m;
}
function defaults(m){return Object.fromEntries(m.parameters.map(p=>[p.key,p.defaultValue??'']));}
function type(p,v){const t=p.nativeType||(p.type==='BOOLEAN'?'boolean':['INTEGER','SEED'].includes(p.type)?'integer':['FLOAT','SLIDER'].includes(p.type)?'number':'string');if(t==='boolean'){if(!['true','false'].includes(String(v)))throw Error('Revisa '+p.displayName);return String(v)==='true';}if(['number','integer'].includes(t)){const n=Number(v);if(!Number.isFinite(n)||(t==='integer'&&!Number.isInteger(n)))throw Error('Revisa '+p.displayName);return n;}if(['array','object'].includes(t))return JSON.parse(v);return String(v);}
function validate(m,prompt,values,media){
 if(m.unsupported?.length)return 'Entradas aún no compatibles: '+m.unsupported.join(', ');
 if(m.promptRequired!==false&&!prompt.trim())return 'Escribe un prompt.';
 for(const f of m.fields){const items=media.filter(x=>x.field===f.key);if((f.required&&!items.length)||items.length<(f.minItems||0))return 'Añade '+f.label.toLowerCase()+'.';if(items.length>f.maximum)return f.label+': máximo '+f.maximum+'.';if(items.some(x=>!x.mime.startsWith(f.mime.split('/')[0]+'/')))return 'Revisa el formato de '+f.label.toLowerCase()+'.';}
 for(const p of m.parameters){const v=values[p.key];if(v==null||String(v).trim()===''){if(p.isRequired)return 'Completa '+p.displayName+'.';continue;}try{const n=type(p,v);if(p.options?.length&&!p.options.includes(String(v)))return 'El valor de '+p.displayName+' no está admitido.';if(p.minValue!=null&&Number(n)<p.minValue||p.maxValue!=null&&Number(n)>p.maxValue)return 'Revisa los límites de '+p.displayName+'.';}catch(e){return e.message;}}
 if(media.some(x=>!m.fields.some(f=>f.key===x.field)))return 'Hay archivos que no corresponden a este modelo.';
 return null;
}
function put(o,key,value){const parts=key.split('.');let at=o;for(const p of parts.slice(0,-1)){if(['__proto__','prototype','constructor'].includes(p))throw Error('Clave no válida');at=at[p]||(at[p]={});}const last=parts.at(-1);if(['__proto__','prototype','constructor'].includes(last))throw Error('Clave no válida');at[last]=value;}
function payload(m,prompt,values,media,quote=false){
 const error=quote?null:validate(m,prompt,values,media);if(error)throw Error(error);
 const out={};for(const [key,value] of Object.entries(m.contractDefaults||{}))put(out,key,value);
 if(!quote&&prompt.trim())out[m.contractEndpoint?(m.contractPromptField||'promptText'):'prompt']=prompt.trim();
 for(const p of m.parameters){let v=values[p.key]??p.defaultValue;if(v==null||String(v).trim()===''||p.key.startsWith('__')||quote&&['prompt','negative_prompt','system_prompt'].includes(p.key))continue;put(out,p.key,type(p,v));}
 for(const f of m.fields){const files=media.filter(x=>x.field===f.key);if(!files.length)continue;const urls=files.map(x=>quote?'':x.remoteUrl);if(!quote&&urls.some(x=>!x))throw Error('Hay archivos pendientes de subir.');if(quote&&f.encoding!=='array'&&f.encoding!=='atlas-references')continue;put(out,f.key,f.encoding==='array'?urls:f.encoding==='object-array'?urls.map(uri=>({uri})):f.encoding==='atlas-references'?urls.map(url=>({url,type:'image'})):f.encoding==='typed-object'?{type:f.fixedType||files[0].mime.split('/')[0],uri:urls[0]}:urls[0]);}
 if(m.providerId==='crun')return {model:m.providerModelId,input:out};return {model:m.providerId==='atlas_cloud'?m.id:m.providerModelId,...out};
}
function endpoint(m){if(m.modalities.includes('CHAT'))return m.providerId==='crun'?'/api/v1/chat/completions':'/v1/chat/completions';if(m.providerId==='crun')return '/api/v1/client/job/CreateTask';if(m.providerId==='runway'){if(!m.contractEndpoint)throw Error('Falta un endpoint verificado.');return '/v1/'+m.contractEndpoint;}return '/api/v1/model/generate'+(m.modalities.includes('VIDEO')?'Video':m.modalities.includes('AUDIO')?'Audio':'Image');}
function adapt(source,target){const values=defaults(target),changes=[];for(const [key,value] of Object.entries(source.values)){if(value==null||String(value).trim()==='')continue;const p=target.parameters.find(p=>p.key===key)||(['aspect_ratio','ratio','aspectRatio'].includes(key)?target.parameters.find(p=>['aspect_ratio','ratio','aspectRatio'].includes(p.key)):null);if(!p){if(value!=='')changes.push(label(key)+': no disponible');continue;}let v=value;if(p.options?.length&&!p.options.includes(String(v))){const ratio=s=>{const a=String(s).split(':').map(Number);return a.length===2&&a[1]>0?a[0]/a[1]:null};const r=ratio(v);v=r?p.options.find(x=>Math.abs(ratio(x)-r)<.0001):null;}try{if(v==null)throw Error();type(p,v);if(p.minValue!=null&&Number(v)<p.minValue||p.maxValue!=null&&Number(v)>p.maxValue)throw Error();values[p.key]=v;}catch{changes.push(p.displayName+': '+(p.defaultValue||'automático'));}}
 for(const p of target.parameters)if(['num_images','n','outputCount','num_outputs'].includes(p.key)){if(p.minValue>1||p.options?.length&&!p.options.includes('1'))throw Error('Este modelo exige varias salidas.');if(values[p.key]&&String(values[p.key])!=='1')changes.push(p.displayName+': 1 por modelo');values[p.key]='1';}
 const images=target.fields.filter(f=>f.mime.startsWith('image/'));if(source.media.some(f=>!f.mime.startsWith('image/')))throw Error('Solo se comparan referencias de imagen.');let media=[];if(source.media.length){if(images.length!==1||images[0].key.includes('mask'))throw Error('Las referencias necesitan una configuración específica.');media=source.media.map(f=>({...f,field:images[0].key,remoteUrl:null}));}const error=validate(target,source.prompt,values,media);if(error)throw Error(error);return {model:target,prompt:source.prompt,values,media,changes};}
function runwayPrice(m,v,refs=0,inputSeconds=null,promptLength=0){const id=m.providerModelId,ratio=v.ratio||'',dims=ratio.split(':').map(Number),pixels=dims.length===2?dims[0]*dims[1]:0,count=Number(v.outputCount||1),quality=v.quality||'high';if(m.modalities.includes('IMAGE')){if(ratio==='auto')return null;let credits;const large=['3840:1648','3840:2160','3504:2336','3264:2448','3200:2560','2880:2880','2560:3200','2448:3264','2336:3504','2160:3840'].includes(ratio);switch(id){case 'gen4_image':credits=['1280:720','720:1280','720:720','960:720','720:960','1680:720'].includes(ratio)?5:8;break;case 'gen4_image_turbo':credits=2;break;case 'muse_image':credits=1;break;case 'seedream5_lite':credits=4;break;case 'seedream5_pro':credits=pixels>2000000||ratio==='auto_2k'?9:5;break;case 'gemini_image3_pro':credits=pixels>8000000?40:20;break;case 'gemini_2.5_flash':credits=5;break;case 'grok_imagine_image_2':credits=(v.quality==='low'?4:6)+(pixels>2000000||ratio==='auto_2k'?2:0);return Math.max(6,credits*count+refs)*.01;case 'gpt_image_2':credits=quality==='low'?(large?2:1):quality==='medium'?(large?11:5):(large?41:20);break;case 'gpt_image_2_5_flare':case 'gpt_image_2_5_sunburst':credits=quality==='low'?(large?2:1):quality==='medium'?(large?11:5):quality==='xhigh'?(large?34:28):quality==='max'?(large?76:63):(large?19:16);credits+=refs;break;default:return null;}return credits*count*.01;}
 if(['eleven_v3','eleven_multilingual_v2'].includes(id))return Math.max(1,Math.ceil(promptLength/50))*.01;
 if(id==='eleven_text_to_sound_v2')return Number(v.duration||2)*.01;
 if(['eleven_voice_isolation','eleven_voice_dubbing','eleven_multilingual_sts_v2'].includes(id))return inputSeconds?Math.ceil(inputSeconds/(id==='eleven_voice_isolation'?6:id==='eleven_voice_dubbing'?2:3))*.01:null;
 if(id==='enhance_frame_rate')return inputSeconds?Math.ceil(inputSeconds/2)*.01:null;
 const duration=Number(v.duration||inputSeconds);
 if(!Number.isFinite(duration)||duration<=0)return null;
 const t=v.resolution|| (ratio.includes('1080')?'1080p':ratio.includes('720')?'720p':ratio.includes('480')?'480p':pixels>4000000?'4k':pixels>1200000?'1080p':pixels>500000?'720p':'480p');
 const audio=v.audio!=='false';const rate={wan3:t==='1080p'?20:t==='720p'?10:5,seedance2_5:t==='1080p'?68:t==='720p'?30:20,seedance2:t==='4k'?150:t==='1080p'?40:36,seedance2_fast:29,seedance2_mini:16,grok_imagine_1_5:t==='1080p'?29:t==='720p'?16:10,h3_max:t==='768p'?8:5,hailuo3:t==='2k'?15:10,aleph2:28,'gen4.5':12,gen4_turbo:5,act_two:5,'veo3.1':audio?40:20,'veo3.1_fast':audio?15:10,happyhorse_1_0:t==='1080p'?30:15,gemini_omni_flash:m.fields.some(f=>f.required&&f.mime.startsWith('video/'))?11:10}[id];
 if(rate==null)return null;
 if(['seedance2_5','hailuo3'].includes(id)&&m.fields.some(f=>f.required&&f.mime.startsWith('video/'))&&!inputSeconds)return null;
 let total=duration*rate+(id==='seedance2_5'?(inputSeconds||0)*rate/2:id==='hailuo3'?Math.min(inputSeconds||0,15)*rate+refs*2:['grok_imagine_1_5','gemini_omni_flash'].includes(id)?refs:0);
 total=Math.max(total,{seedance2_5:80,seedance2_mini:64,aleph2:56}[id]||0);
 const format=v.outputFormat||'';total+=duration*(['prores','png_sequence'].includes(format)?5:format.startsWith('hdr')||['hlg','sdr_rec709_10bit'].includes(format)?pixels>4000000?40:20:0);
 return total*.01;
}
const engine={valid,label,normalize,defaults,validate,payload,endpoint,adapt,runwayPrice};if(typeof module!=='undefined')module.exports=engine;root.NexusEngine=engine;
})(typeof window!=='undefined'?window:globalThis);
