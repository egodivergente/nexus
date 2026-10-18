'use strict';
const BrainTemplates={
 byteplus:{id:'byteplus',name:'BytePlus ModelArk',protocol:'OPENAI_RESPONSES',baseUrl:'https://ark.ap-southeast.bytepluses.com/api/v3',modelId:'dola-seed-2-1-turbo-260628',capabilities:{vision:true,reasoning:true,streaming:true,tools:true,jsonSchema:true},builtin:true},
 responses:{id:'responses',name:'Responses API',protocol:'OPENAI_RESPONSES',baseUrl:'https://api.openai.com/v1',modelId:'',capabilities:{vision:true,reasoning:true,streaming:true,tools:true}},
 chat:{id:'compatible',name:'Compatible OpenAI',protocol:'OPENAI_CHAT',baseUrl:'https://api.openai.com/v1',modelId:'',capabilities:{vision:true,reasoning:true,streaming:true,tools:true}},
 rest:{id:'personalizado',name:'REST personalizado',protocol:'CUSTOM_REST',baseUrl:'https://example.com/api',modelId:'',capabilities:{tools:false},restRequestTemplate:'{"prompt":"{{prompt}}","model":"{{model}}"}',restTextPath:'text'}
};
const originalAgentSettings=agentSettings;
agentSettings=function(){
 const s=Agent.settings;
 modal(`<h2>Cerebro del agente</h2><form id="brain-settings-form"><label>Conexión<select name="backend">${[['direct','Bedrock directo'],['proxy','Proxy existente / Sol'],['anthropic','Anthropic'],['antigravity','Antigravity'],['openai','Compatible OpenAI'],['profile','Perfil configurable (Chat / Responses / REST)']].map(([id,label])=>`<option value="${id}" ${s.backend===id?'selected':''}>${label}</option>`).join('')}</select></label><label>Modelo<input name="modelId" value="${escape(s.modelId||'')}"></label><label>Endpoint<input name="endpoint" value="${escape(s.endpoint||'')}"></label><label>Región AWS<input name="region" value="${escape(s.region||'us-east-1')}"></label><label>Clave del proveedor<input type="password" name="apiKey" autocomplete="off" placeholder="Vacío conserva la clave guardada"></label><label>Clave del perfil<input type="password" name="brainKey" autocomplete="off" placeholder="Protegida con DPAPI del usuario Windows"></label><details><summary>Credenciales Bedrock y proxy</summary>${['accessKey','secretKey','sessionToken','token'].map(key=>`<label>${key}<input type="password" name="${key}" autocomplete="off" placeholder="Vacío conserva la actual"></label>`).join('')}</details><label>Plantilla de perfil<select id="brain-template"><option value="">Perfil actual</option><option value="byteplus">BytePlus ModelArk</option><option value="responses">Responses API</option><option value="chat">Chat compatible</option><option value="rest">REST personalizado</option></select></label><label>Perfil (sin claves)<textarea name="brainProfile" rows="12" spellcheck="false">${escape(JSON.stringify(s.brainProfile||BrainTemplates.byteplus,null,2))}</textarea></label><label>Límites globales<textarea name="limits" rows="5">${escape(JSON.stringify(s.limits||{maxToolRounds:20,turnTimeoutSeconds:600,maxTokens:s.maxTokens||4096,flowMaxResults:20},null,2))}</textarea></label><label>Modo<select name="mode">${['ASSISTANT','COPILOT','AGENT'].map(m=>`<option ${s.mode===m?'selected':''}>${m}</option>`).join('')}</select></label><p>Una credencial aceptada, un modelo listado y una inferencia comprobada son resultados distintos.</p><div id="brain-diagnostic" role="status"></div><div class="toolbar" style="position:sticky;bottom:0;background:var(--surface,#1b2029);padding:12px"><button type="submit" class="primary">Guardar</button><button type="button" data-brain="models">Listar modelos</button><button type="button" data-brain="test">Probar inferencia</button><button type="button" data-brain="close">Cerrar</button></div></form>`);
 $('#brain-template').addEventListener('change',e=>{const template=BrainTemplates[e.target.value];if(template){$('#brain-settings-form [name=brainProfile]').value=JSON.stringify(template,null,2);$('#brain-settings-form [name=backend]').value='profile';}});
 $('#brain-settings-form').addEventListener('submit',async e=>{e.preventDefault();try{await saveBrainForm();toast('Cerebro guardado');$('#dialog').close();if(view==='editor'||view==='assistant')renderAssistant();}catch(error){$('#brain-diagnostic').textContent=error.message;}});
};
async function saveBrainForm(){
 const form=$('#brain-settings-form');const values=Object.fromEntries(new FormData(form));
 values.brainProfile=JSON.parse(values.brainProfile);values.limits=JSON.parse(values.limits);values.maxTokens=values.limits.maxTokens;
 await agentCall('settings',values);return values;
}
document.addEventListener('click',async e=>{
 const action=e.target.closest('[data-brain]')?.dataset.brain;if(!action)return;
 if(action==='close'){$('#dialog').close();return;}
 const status=$('#brain-diagnostic');
 try{
  const values=await saveBrainForm();if(values.backend!=='profile')throw Error('Descubrimiento disponible para perfiles configurables.');
  status.textContent='Consultando el proveedor seleccionado…';
  const result=await agentCall(action==='models'?'discoverBrain':'testBrain',{profile:values.brainProfile});
  status.textContent=action==='models'?'Modelos listados: '+result.models.join(', '):result.message;
 }catch(error){status.textContent=error.message;}
});
const beforeBrainRender=renderAssistant;
renderAssistant=function(){
 beforeBrainRender();
 if(view==='editor'&&!document.querySelector('#corte-chat [data-corte-resize]')){const divider=document.createElement('div');divider.className='corte-divider chat';divider.dataset.corteResize='chat';divider.setAttribute('role','separator');divider.setAttribute('aria-label','Ancho del agente');document.querySelector('#corte-chat')?.append(divider);}
 const container=document.querySelector('.agent-session-info');if(!container)return;
 const s=Agent.settings,profiles=Object.values(s.brainProfiles||{});
 const select=document.createElement('select');select.id='brain-quick';select.setAttribute('aria-label','Cambiar cerebro');
 select.innerHTML=`<option value="">Cambiar cerebro…</option>${[['direct','Bedrock'],['proxy','Proxy / Sol'],['anthropic','Anthropic'],['antigravity','Antigravity'],['openai','Compatible OpenAI']].map(([id,name])=>`<option value="${id}" ${s.backend===id?'selected':''}>${name}</option>`).join('')}${profiles.map(p=>`<option value="profile:${escape(p.id)}" ${s.backend==='profile'&&s.brainProfile?.id===p.id?'selected':''}>${escape(p.name)}</option>`).join('')}`;
 select.disabled=Agent.busy;
 select.addEventListener('change',async()=>{
  if(!select.value)return;
  const backend=select.value.startsWith('profile:')?'profile':select.value;
  const profile=profiles.find(p=>'profile:'+p.id===select.value);
  try{await agentCall('settings',{backend,...(profile?{brainProfile:profile,modelId:profile.modelId}:{})});toast('Cerebro cambiado');}catch(e){toast(e.message);}
 });
 container.append(select);
 const replies=Agent.replies||[];
 if(replies.length){const row=document.createElement('div');row.className='agent-quick-replies';for(const reply of replies){const button=document.createElement('button');button.type='button';button.textContent=reply.label;button.addEventListener('click',()=>{Agent.draft=reply.text;sendAgent();});row.append(button);}document.querySelector('#agent-compose').prepend(row);}
};
window.addEventListener('nexus:replies',()=>{if(view==='assistant'||view==='editor')renderAssistant();});

// Preserve the original RPC path; agent turns inherit the configured timeout, including 0.
const rpcBeforeBrainLimits=rpc;
rpc=function(op,args={}){
 if(op!=='agent'||args.op!=='send')return rpcBeforeBrainLimits(op,args);
 const s=Agent.settings,seconds=(s.backend==='profile'?s.brainProfile?.limits?.turnTimeoutSeconds:undefined)??s.limits?.turnTimeoutSeconds??600;
 return new Promise((resolve,reject)=>{
  const id=String(++sequence),telemetry=window.NexusTelemetry,trace=telemetry?.begin(op,args);
  const finish=(ok,code)=>telemetry?.finish(trace,ok,code),ms=seconds===0?0:(seconds+50)*1000;
  const timer=ms&&ms<2147483647?setTimeout(()=>{pending.delete(id);finish(false,'timeout');reject(Error('La operación está tardando demasiado. Comprueba el historial antes de repetir un envío.'));},ms):null;
  pending.set(id,{resolve:value=>{finish(true);resolve(value);},reject:error=>{finish(false,'rpc_error');reject(error);},timer});
  try{window.chrome.webview.postMessage({id,op,args});}catch(error){clearTimeout(timer);pending.delete(id);finish(false,'bridge_error');reject(error);}
 });
};
