'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../ui/flow-assets.js'),'utf8');
let resolve,scheduled=[];
const context=vm.createContext({structuredClone,document:{addEventListener(){}},setTimeout(fn){scheduled.push(fn);return scheduled.length;},clearTimeout(){},
 view:'canvas',loaded:{providerId:'google_flow',providerModelId:'omni_flash',flowMode:'t2v'},busy:false,quote:null,
 state:{draft:{values:{aspect:'16:9',duration:4,resolution:'360p'},media:[],prompt:''}},
 E:{validate(){return null;}},quotePlan(){return new Promise(r=>{resolve=r;});},renderCanvas(){},updateReadiness(){},amount:q=>q.amount===0?'Incluido':String(q.amount),$:()=>({textContent:''})});
vm.runInContext(source.slice(0,source.indexOf('let flowAssetPlan')),context);
(async()=>{
 vm.runInContext('scheduleFlowAvailability()',context);
 const first=scheduled.shift()();
 context.state.draft.values.duration=10;
 resolve({amount:4,currency:'flow_credits'});await first;
 assert.equal(context.quote,null,'A previous duration must not enable the new configuration.');
 vm.runInContext('scheduleFlowAvailability()',context);
 const second=scheduled.shift()();resolve({amount:0,currency:'flow_credits',time:Date.now()});await second;
 assert.equal(context.quote.amount,0,'Zero is a valid included account response.');
 context.state.draft.prompt='A new prompt';context.quote=null;
 vm.runInContext('scheduleFlowAvailability()',context);
 assert.equal(context.quote.amount,0,'Typing a prompt must retain valid included access for the same settings.');
 console.log('PASS: automatic account checks retain the current configuration and accept included access.');
})().catch(e=>{console.error(e);process.exitCode=1;});
