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
 document:{addEventListener:()=>{}},window:{addEventListener:()=>{}},setTimeout:()=>1,clearTimeout:()=>{},
 rpc:async(op,args)=>{
  requests.push({op,args});
  assert.equal(args.provider,'atlas_cloud');
  return {available:{value:12.5,currency:'USD'}};
 }
});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../ui/balances.js'),'utf8'),context);
const flowLabel=()=>bar.innerHTML.match(/<span>Google Flow<\/span><strong>(.*?)<\/strong>/)?.[1];
assert.equal(flowLabel(),'Conectando…');
assert.match(bar.innerHTML,/Comprobando la sesión/);
assert.equal(requests.length,0);
(async()=>{
 await vm.runInContext('refreshBalances()',context);
 assert.equal(requests.length,1);
 assert.equal(flowLabel(),'Conectando…');
 // Point 13: one money format everywhere (two decimals, US$ symbol).
 assert.match(bar.innerHTML,/12,50 US\$/);
 assert.doesNotMatch(bar.innerHTML,/12,5 USD|12,5000/);
 // Genuine request failures still remain visible for supported providers.
 context.rpc=async()=>{throw Error('Consulta fallida');};
 vm.runInContext('AccountBalances.values={}',context);
 await vm.runInContext('refreshBalances()',context);
 assert.match(bar.innerHTML,/Consulta fallida/);
 assert.match(bar.innerHTML,/<span>Atlas Cloud<\/span><strong>No disponible<\/strong>/);
 assert.equal(flowLabel(),'Conectando…');
 context.rpc=async(op,args)=>{
  assert.equal(op,'flowConnection');
  return {status:'auth_required'};
 };
 vm.runInContext('initial.qa=false',context);
 await vm.runInContext('refreshFlowConnection(true)',context);
 assert.equal(flowLabel(),'Iniciar sesión');
 assert.match(bar.innerHTML,/data-connect-flow/);
 vm.runInContext("FlowConnection.value={status:'auth_required',reason:'password_required'};renderBalances()",context);
 assert.equal(flowLabel(),'Google solicita contraseña');
 vm.runInContext("FlowConnection.value={status:'auth_required',reason:'browser_rejected'};renderBalances()",context);
 assert.equal(flowLabel(),'Google rechaza el acceso');
 context.rpc=async()=>({status:'connected'});
 await vm.runInContext('refreshFlowConnection()',context);
 assert.equal(flowLabel(),'Conectado');
 assert.doesNotMatch(bar.innerHTML,/data-connect-flow/);
 context.rpc=async()=>{throw Error('Offline');};
 await vm.runInContext('refreshFlowConnection()',context);
 assert.equal(flowLabel(),'Reintentando conexión');
 assert.doesNotMatch(bar.innerHTML,/Sesión de Flow comprobada/);
 console.log('PASS: startup, balance, login recovery and loss of connection; no generation or balance invented.');
})().catch(error=>{console.error(error);process.exitCode=1;});
