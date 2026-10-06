'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');

// Exercise the startup balance bar: an unsupported balance query must never
// describe a configured provider as unavailable, or make up a balance.
const bar={innerHTML:''}, requests=[];
const context=vm.createContext({
 initial:{configured:['google_flow','atlas_cloud'],qa:true},
 providers:{google_flow:'Google Flow',atlas_cloud:'Atlas Cloud'},
 $:selector=>selector==='#account-balances'?bar:null,
 escape:value=>String(value),render:()=>{},Intl,Date,
 document:{addEventListener:()=>{}},
 rpc:async(op,args)=>{
  requests.push({op,args});
  assert.equal(args.provider,'atlas_cloud');
  return {available:{value:12.5,currency:'USD'}};
 }
});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../ui/balances.js'),'utf8'),context);
const flowLabel=()=>bar.innerHTML.match(/<span>Google Flow<\/span><strong>(.*?)<\/strong>/)?.[1];
assert.equal(flowLabel(),'Saldo sin consultar');
assert.match(bar.innerHTML,/no indica el estado de conexión/);
assert.equal(requests.length,0);
(async()=>{
 await vm.runInContext('refreshBalances()',context);
 assert.equal(requests.length,1);
 assert.equal(flowLabel(),'Saldo sin consultar');
 assert.match(bar.innerHTML,/12,5 USD/);
 // Genuine request failures still remain visible for supported providers.
 context.rpc=async()=>{throw Error('Consulta fallida');};
 vm.runInContext('AccountBalances.values={}',context);
 await vm.runInContext('refreshBalances()',context);
 assert.match(bar.innerHTML,/Consulta fallida/);
 assert.match(bar.innerHTML,/<span>Atlas Cloud<\/span><strong>No disponible<\/strong>/);
 assert.equal(flowLabel(),'Saldo sin consultar');
 console.log('PASS: startup, balance refresh and query failure; no Flow generation or balance invented.');
})().catch(error=>{console.error(error);process.exitCode=1;});
