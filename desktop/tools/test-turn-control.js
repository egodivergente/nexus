'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const listeners=new Map();let renders=0;
const context={
 rpc:async()=>({}),renderAssistant:()=>renders++,agentCall:async()=>({ok:true}),
 Agent:{callbacks:new Set(),cancelDraining:false},view:'assistant',
 document:{querySelector:()=>null},$:()=>null,
 window:{addEventListener:(name,handler)=>listeners.set(name,handler)},
 setInterval:()=>{throw Error('An idle workspace must not poll cancellation');}
};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../ui/turn-control.js'),'utf8'),context);
const drain=listeners.get('nexus:agent-callbacks-drained');
assert.equal(typeof drain,'function');
drain();assert.equal(renders,0);
context.Agent.cancelDraining=true;context.Agent.callbacks.add('pending');
drain();assert.equal(context.Agent.cancelDraining,true);assert.equal(renders,0);
(async()=>{
 await assert.rejects(context.agentCall('snapshot'),/Espera/);
 context.Agent.callbacks.clear();drain();
 assert.equal(context.Agent.cancelDraining,false);assert.equal(renders,1);
 drain();assert.equal(renders,1);
 assert.equal((await context.agentCall('snapshot')).ok,true);
 console.log('PASS cancellation preserves pending callbacks, drains once, allows recovery and has no idle timer');
})().catch(error=>{console.error(error);process.exitCode=1;});
