'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const context=vm.createContext({crypto:require('node:crypto').webcrypto,view:'library',render(){},document:{addEventListener(){}},
 setInterval(){},setTimeout(){},clearInterval(){},console});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../ui/assistant.js'),'utf8'),context);
vm.runInContext(`agentModel=async ref=>({providerId:ref.providerId});
 amount=q=>q.amount+' '+q.currency;quotePlan=async()=>testQuote;`,context);
(async()=>{
 const quote=async(provider,value)=>{
  context.testQuote=value;
  return vm.runInContext(`agentCallback('quote',{ref:{providerId:${JSON.stringify(provider)}},params:{}})`,context);
 };
 const verified=await quote('google_flow',{amount:50,currency:'flow_credits',detail:'Flow menu'});
 assert.equal(verified.amountUsd,null);assert.equal(verified.confidence,'UNKNOWN');assert.equal(verified.basis,'Tu cuenta Flow');
 const zero=await quote('google_flow',{amount:0,currency:'flow_credits'});
 assert.equal(zero.confidence,'UNKNOWN');assert.equal(zero.amountUsd,null);
 const conservative=await quote('google_flow',{amount:20,currency:'flow_credits',upperBound:true});
 assert.equal(conservative.confidence,'UNKNOWN');assert.equal(conservative.amountUsd,null);
 const missing=await quote('google_flow',{amount:null,currency:'flow_credits'});
 assert.equal(missing.confidence,'UNKNOWN');assert.equal(missing.amountUsd,null);
 const wrongUnit=await quote('google_flow',{amount:0,currency:'unknown'});
 assert.equal(wrongUnit.confidence,'UNKNOWN');
 const dollars=await quote('atlas_cloud',{amount:.2,currency:'USD'});
 assert.equal(dollars.confidence,'NEXUS_QUOTE');assert.equal(dollars.amountUsd,.2);
 const other=await quote('crun',{amount:10,currency:'credits'});
 assert.equal(other.confidence,'UNKNOWN');assert.equal(other.amountUsd,null);
 console.log('PASS: Flow uses account access without invented prices; paid provider quotes preserved.');
})().catch(error=>{console.error(error);process.exitCode=1;});
