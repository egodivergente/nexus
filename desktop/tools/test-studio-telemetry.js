'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const {createNexusTelemetry}=require('../ui/studio-telemetry.js');
let now=0;const T=createNexusTelemetry(()=>now,()=>1000+now);
let id=T.begin('generate',{provider:'runway',prompt:'PRIVATE',token:'SECRET',url:'https://private.invalid'});now=125;T.finish(id,true);
assert.equal(T.snapshot().rows[0].ms,125);assert.equal(T.snapshot().rows[0].provider,'runway');
assert(!JSON.stringify(T.snapshot()).match(/PRIVATE|SECRET|private\.invalid/));
id=T.begin('SECRET-OP',{provider:'PRIVATE'});now+=5;T.finish(id,false,'SECRET-ERROR');
assert.equal(T.snapshot().rows[1].op,'other');assert.equal(T.snapshot().rows[1].code,'rpc_error');
for(let i=0;i<210;i++){id=T.begin('save');now++;T.finish(id,true);}
assert.equal(T.snapshot().rows.length,200);assert.equal(T.snapshot().total,212);
T.setEnabled(false);assert.equal(T.begin('init'),null);assert.equal(T.snapshot().total,212);
T.clear();assert.equal(T.snapshot().total,0);T.setEnabled(true);
const source=fs.readFileSync(require('node:path').join(__dirname,'../ui/app.js'),'utf8');
const rpcSource=source.slice(source.indexOf('const pending=new Map()'),source.indexOf('let models=[]'));
let receive,posted,throwBridge=false;const timers=[];
const context={Map,Promise,Error,setTimeout:f=>{timers.push(f);return timers.length;},clearTimeout:()=>{},window:{NexusTelemetry:T,chrome:{webview:{postMessage:m=>{if(throwBridge)throw Error('SECRET');posted=m;},addEventListener:(name,f)=>receive=f}}}};
vm.createContext(context);vm.runInContext(rpcSource,context);
(async()=>{
 const success=context.rpc('generate',{provider:'runway',prompt:'PRIVATE'});now+=45;receive({data:{id:posted.id,ok:true,value:{accepted:true}}});assert.deepEqual(await success,{accepted:true});
 const fail=context.rpc('save');receive({data:{id:posted.id,ok:false,error:'SECRET'}});await assert.rejects(fail,/SECRET/);
 throwBridge=true;await assert.rejects(context.rpc('init'),/SECRET/);assert.equal(vm.runInContext('pending.size',context),0);
 throwBridge=false;const timeout=context.rpc('save');timers.at(-1)();await assert.rejects(timeout,/tardando/);
 assert.equal(T.snapshot().inFlight,0);assert.equal(T.snapshot().errors,3);
 assert(!JSON.stringify(T.snapshot()).match(/PRIVATE|SECRET/));
 assert.equal(T.snapshot().rows.at(-1).code,'timeout');
 console.log('PASS: sanitized metrics, bounded history, pause/clear, actual RPC success/error/timeout/bridge failure, cleanup and unchanged results. Zero network.');
})().catch(e=>{console.error(e);process.exitCode=1;});
