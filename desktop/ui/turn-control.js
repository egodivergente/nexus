'use strict';
const rpcBeforeTurnControl=rpc;
rpc=function(op,args={}){
 if(op!=='agent'||!['send','rewritePrompt','testBrain'].includes(args.op))return rpcBeforeTurnControl(op,args);
 const id=crypto.randomUUID();Agent.activeTurn={id,op:args.op};
 if(view==='assistant'||view==='editor')renderAssistant();
 return rpcBeforeTurnControl(op,{...args,turn_id:id}).finally(()=>{
  if(Agent.activeTurn?.id===id)Agent.activeTurn=null;
 });
};
const renderBeforeTurnControl=renderAssistant;
renderAssistant=function(){
 renderBeforeTurnControl();
 const compose=$('#agent-compose');if(!compose)return;
 const turn=Agent.busy?Agent.activeTurn?.id:null;
 const previousButton=compose.querySelector('[data-stop-turn]');
 if(previousButton&&previousButton.dataset.stopTurn!==turn)previousButton.remove();
 if(turn&&previousButton?.dataset.stopTurn!==turn){
  const button=document.createElement('button');button.type='button';button.dataset.stopTurn=turn;button.textContent='Detener agente';
  button.addEventListener('click',async()=>{
   button.disabled=true;button.textContent='Deteniendo…';
   try{await AgentQueue.pause();}catch(error){toast(error.message);}
   try{await rpc('agentCancel',{owner:Agent.owner,turn_id:turn});Agent.cancelDraining=Agent.callbacks.size>0;}
   catch(error){toast(error.message);button.disabled=false;}
  });
  compose.querySelector('.agent-compose-actions')?.append(button);
 }
 const notice=compose.querySelector('.agent-stopping-notice');
 if(Agent.cancelDraining){
  if(!notice){
   const message=document.createElement('p');message.className='agent-stopping-notice';message.textContent='Finalizando una operación ya enviada…';compose.prepend(message);
  }
  if(!AgentQueue.has())compose.querySelector('[data-agent=send]')?.setAttribute('disabled','');
 }else notice?.remove();
};
const agentCallBeforeTurnControl=agentCall;
agentCall=function(op,args={}){
 if(Agent.cancelDraining)return Promise.reject(Error('Espera a que termine la operación ya enviada.'));
 return agentCallBeforeTurnControl(op,args);
};
// Callback completion is already known by the event pump. Idle workspaces do
// not need four timer wakeups per second just to discover that nothing changed.
window.addEventListener('nexus:agent-callbacks-drained',()=>{
 if(Agent.cancelDraining&&!Agent.callbacks.size){Agent.cancelDraining=false;if(view==='assistant'||view==='editor')renderAssistant();}
});
