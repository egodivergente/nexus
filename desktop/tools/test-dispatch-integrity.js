'use strict';
// Run the actual UI functions in a VM: no browser, network or provider calls.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../ui/app.js'),'utf8');
const functions=source.slice(source.indexOf('function forbiddenModel('),source.indexOf('\n',source.indexOf('async function dispatch(')));
let release,posted;
const saved=new Promise(resolve=>{release=resolve;});
const context={structuredClone,Date,crypto:require('node:crypto').webcrypto,
 initial:{qa:false},view:'canvas',state:{jobs:[]},E:{validate:()=>null,payload:(m,p,v,media)=>({prompt:p,...v,media}),endpoint:()=>'/fixture'},
 flush:()=>saved,rpc:async(op,args)=>{assert.equal(op,'generate');posted=args;return {id:'task',status:'completed',outputs:['result']};}};
vm.createContext(context);vm.runInContext(source.match(/^function expectedOutputCount.*$/m)[0]+'\n'+functions,context);
for(const id of ['google/nano-banana-2-lite/edit','nano_lite','crun/google/nano-banana-2-lite'])assert(context.forbiddenModel({id}));
assert(!context.forbiddenModel({id:'google-flow/nano2',displayName:'Nano Banana 2'}));
for(const providerId of ['atlas_cloud','runway','crun','google_flow']){
 for(const [remote,status] of [['failed','FAILED'],['processing','PROCESSING'],['succeeded','COMPLETED']]){
  const job={providerId,modality:'IMAGE',outputs:[]};
  const result={id:'remote',status:remote,outputs:['partial'],output:['partial'],result:{media_urls:['partial']}};
  context.parseResponse(job,providerId==='runway'?result:{data:result});
  assert.equal(job.status,status);assert.equal(job.outputs[0],'partial');
 }
}
const recovered={providerId:'google_flow',modality:'IMAGE',error:'old',uncertain:true};
context.parseResponse(recovered,{id:'same',status:'completed',outputs:['result']});
assert.equal(recovered.status,'COMPLETED');assert.equal(recovered.uncertain,false);assert.equal(recovered.error,undefined);
const immediate={providerId:'atlas_cloud',modality:'IMAGE'};
context.parseResponse(immediate,{outputs:['result']});assert.equal(immediate.status,'COMPLETED');
(async()=>{
 await assert.rejects(context.dispatch({model:{id:'google/nano-banana-2-lite/edit'},values:{},media:[],cost:null}),/Lite/);
 const plan={model:{id:'fixture',providerId:'atlas_cloud',modalities:['IMAGE']},prompt:'confirmed',
  values:{ratio:'1:1'},media:[{id:'ref',remoteUrl:'https://example.invalid/original'}],cost:{amount:1,time:Date.now()}};
 const sending=context.dispatch(plan);
 plan.values.ratio='16:9';plan.media[0].remoteUrl='https://example.invalid/changed';plan.cost.amount=99;plan.prompt='changed';
 release();const job=await sending;
 assert.equal(posted.job_id,job.id);assert.equal(posted.body.ratio,'1:1');assert.equal(posted.body.prompt,'confirmed');assert.equal(posted.cost,1);
 assert.equal(posted.body.media[0].remoteUrl,'https://example.invalid/original');
 assert.equal(job.parameters.ratio,'1:1');assert.equal(job.estimated.amount,1);
 const unknown={model:plan.model,prompt:'fitted prompt',originalPrompt:'unabridged original',values:{},media:[],cost:{amount:null,currency:'unknown',status:'unavailable',time:Date.now()}};
 const noPrice=await context.dispatch(unknown);
 assert.equal(posted.job_id,noPrice.id);assert.equal(posted.cost,null);assert.equal(posted.estimate_status,'unavailable');assert.equal(noPrice.originalPrompt,'unabridged original');
 console.log('PASS: 14 provider-state cases and immutable confirmed dispatch across an awaited save. Zero network.');
})().catch(error=>{console.error(error);process.exitCode=1;});
