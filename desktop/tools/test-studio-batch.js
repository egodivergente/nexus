'use strict';
const assert=require('node:assert/strict');
const {nexusRunBatch}=require('../ui/studio-batch.js');
const plan=(id,providerId='atlas_cloud')=>({model:{id,providerId},prompt:'original',values:{n:1}});
const wait=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 let active=0,maximum=0,started=[],releases=[],events=[];
 const input=[plan('a'),plan('b'),plan('c'),plan('d')];
 const running=nexusRunBatch(input,{isPaused:()=>false,isStopped:()=>false,onUpdate:()=>{},onItem:(p,status)=>events.push([p.model.id,status]),dispatch:p=>{
  active++;maximum=Math.max(maximum,active);started.push(p);
  return new Promise(r=>releases.push(()=>{active--;r();}));
 }});
 assert.deepEqual(started.map(p=>p.model.id),['a','b','c']);assert.equal(maximum,3);
 assert.deepEqual(events.map(e=>e[1]),['SENDING','SENDING','SENDING']);
 input[0].values.n=99;assert.equal(started[0].values.n,1);
 releases.shift()();await wait(0);assert.equal(started.length,4);
 while(releases.length)releases.shift()();assert.equal((await running).sent,4);
 assert.equal(events.filter(e=>e[1]==='SUBMITTED').length,4);
 let flow=0,maxFlow=0;
 const mixed=await nexusRunBatch([plan('f1','google_flow'),plan('f2','google_flow'),plan('a'),plan('f3','google_flow')],{
  isPaused:()=>false,isStopped:()=>false,onUpdate:()=>{},dispatch:async p=>{
   if(p.model.providerId==='google_flow'){flow++;maxFlow=Math.max(flow,maxFlow);await wait(10);flow--;}else await wait(2);
  }
 });assert.equal(maxFlow,1);assert.equal(mixed.sent,4);
 let paused=true,calls=0,stop=false;
 const controlled=nexusRunBatch([plan('a'),plan('b'),plan('c'),plan('d')],{isPaused:()=>paused,isStopped:()=>stop,onUpdate:()=>{},dispatch:async()=>{calls++;stop=true;await wait(1);}});
 await wait(50);assert.equal(calls,0);paused=false;const stopped=await controlled;assert.equal(calls,1);assert.equal(stopped.pending,3);
 let attempted=[];
 const failed=await nexusRunBatch([plan('fail'),plan('b'),plan('c'),plan('d')],{
  isPaused:()=>false,isStopped:()=>false,onUpdate:()=>{},dispatch:async p=>{attempted.push(p.model.id);if(p.model.id==='fail')throw Error('unconfirmed');await wait(5);}
 });assert.equal(failed.failed,1);assert.equal(failed.sent,2);assert.equal(failed.pending,1);assert(!attempted.includes('d'));
 assert.equal(new Set(attempted).size,attempted.length);
 console.log('PASS: concurrent submissions, cap 3, immutable plans, Flow lane, pause/stop, settle in-flight after failure and no retry. Zero network.');
})().catch(e=>{console.error(e);process.exitCode=1;});
