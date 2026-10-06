'use strict';
const AccountBalances={values:{},busy:false,started:false};
const balanceRoutes={atlas_cloud:'/public/v1/balance',runway:'/v1/organization',crun:'/api/v1/client/account/balance'};
function parseAccountBalance(provider,response){
 const data=response?.data||response;
 let value,currency;
 if(provider==='atlas_cloud'){
  value=data?.available?.value??data?.available_usd??data?.balance??data?.available;
  currency=data?.available?.currency?.toUpperCase()||'USD';
 }else{value=provider==='runway'?data?.creditBalance:data?.balance;currency='credits';}
 if(typeof value==='string'&&value.trim()&&/^\d+(\.\d+)?$/.test(value))value=Number(value);
 if(typeof value!=='number'||!Number.isFinite(value)||value<0)throw Error('El proveedor no devolvió un saldo válido.');
 return {value,currency,time:Date.now()};
}
function balanceText(item){
 if(!item)return 'Pendiente';
 if(item.value==null)return 'No disponible';
 const value=new Intl.NumberFormat('es-ES',{maximumFractionDigits:4}).format(item.value);
 return value+(item.currency==='credits'?' créditos':' '+item.currency);
}
function renderBalances(){
 if(!initial)return;
 let bar=$('#account-balances');
 if(!bar){bar=document.createElement('section');bar.id='account-balances';bar.setAttribute('aria-label','Saldos de proveedores');document.querySelector('main>header').after(bar);}
 const configured=initial.configured||[];
 bar.innerHTML='<span class="balance-heading">Saldos</span>'+configured.map(id=>{
  const item=AccountBalances.values[id],supported=!!balanceRoutes[id];
  const label=!supported?'Saldo sin consultar':item?balanceText(item):AccountBalances.busy?'Consultando…':'Pendiente';
  const time=item?.time?new Date(item.time).toLocaleTimeString('es-ES',{hour:'2-digit',minute:'2-digit'}):'';
  const title=!supported?'La consulta de saldo no está implementada. Este mensaje no indica el estado de conexión del proveedor.':item?.error||'Saldo devuelto por el proveedor'+(item?.time?' · '+new Date(item.time).toLocaleString('es-ES'):'');
  const settingsBalance=$('#balance-'+id);if(settingsBalance&&supported&&item)settingsBalance.textContent='Saldo: '+balanceText(item)+(item.error?' · Última consulta fallida':'');
  return `<span class="account-balance" title="${escape(title)}"><span>${escape(providers[id]||(id==='google_flow'?'Google Flow':id))}</span><strong>${escape(label)}</strong>${time?`<small>${item.error?'Último dato':'Consulta'} ${time}</small>`:''}</span>`;
 }).join('')+(configured.length?'':'<span class="hint">Sin proveedores configurados</span>')+
 `<button type="button" data-refresh-balances title="Actualizar saldos" aria-label="Actualizar saldos" ${AccountBalances.busy||!configured.some(id=>balanceRoutes[id])?'disabled':''}>&#8635;</button>`;
}
async function refreshBalances(){
 if(AccountBalances.busy||!initial)return;
 AccountBalances.started=true;AccountBalances.busy=true;renderBalances();
 try{
  for(const provider of initial.configured.filter(id=>balanceRoutes[id])){
   try{
    const response=await rpc('request',{provider,path:balanceRoutes[provider],method:'GET'});
    AccountBalances.values[provider]=parseAccountBalance(provider,response);
   }catch(error){AccountBalances.values[provider]={...AccountBalances.values[provider],error:error.message};}
   renderBalances();
  }
 }finally{AccountBalances.busy=false;renderBalances();}
}
const renderBeforeBalances=render;
render=function(){renderBeforeBalances();renderBalances();if(!AccountBalances.started&&initial&&!initial.qa)refreshBalances();};
document.addEventListener('click',e=>{if(e.target.closest('[data-refresh-balances]'))refreshBalances();});
document.addEventListener('click',e=>{const button=e.target.closest('[data-balance]');if(button&&!button.disabled){e.preventDefault();e.stopImmediatePropagation();refreshBalances();}},true);
renderBalances();if(initial&&!initial.qa)refreshBalances();
