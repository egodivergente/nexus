'use strict';
const AccountBalances={values:{},busy:false,started:false};
const FlowConnection={value:null,busy:false,timer:null};
function flowConnectionText(){
 if(FlowConnection.value?.status==='auth_required')return {browser_rejected:'Google rechaza el acceso',password_required:'Google solicita contraseña',verification_required:'Verificar en Google',account_selection_required:'Elegir cuenta de Google'}[FlowConnection.value.reason]||'Iniciar sesión';
 return {pending:'Conectando…',refreshing:'Renovando sesión…',signing_in:'Completa el acceso en Google',connected:'Conectado',auth_required:'Iniciar sesión',busy:'En uso',error:'Reintentando conexión',unavailable:'Puente no instalado'}[FlowConnection.value?.status||'pending'];
}
function flowConnectionHelp(){
 if(FlowConnection.value?.status==='auth_required')return {browser_rejected:'Google ha rechazado el acceso automático. Abre Conectar para usar su acceso normal.',password_required:'Google solicita tu contraseña; Nexus no la guarda ni la introduce.',verification_required:'Google exige que completes su verificación de identidad.',account_selection_required:'Hay varias cuentas o no se puede identificar una sola. Completa la selección en Google para que Nexus recuerde esa identidad.'}[FlowConnection.value.reason]||'Google necesita que completes el acceso a Flow. La conexión automática se ha intentado.';
 return {pending:'Comprobando la sesión de Flow; saldo sin consultar.',refreshing:'Renovando el acceso con tu sesión de Google existente.',signing_in:'Completa el acceso en la ventana de Google. Nexus detectará el resultado automáticamente.',connected:'Sesión de Flow comprobada; saldo sin consultar.',auth_required:'Google necesita que completes el acceso a Flow. La renovación automática se ha intentado sin enviar generaciones.',busy:'Flow está usando el perfil. La conexión se comprobará al terminar.',error:'La comprobación de conexión falló y se reintentará automáticamente.',unavailable:'Falta el puente local de Flow.'}[FlowConnection.value?.status||'pending'];
}
async function refreshFlowConnection(force=false){
 if(FlowConnection.busy||!initial?.configured?.includes('google_flow')||initial.qa)return;
 FlowConnection.busy=true;
 try{FlowConnection.value=await rpc('flowConnection',{refresh:force});}
 catch(error){FlowConnection.value={status:'error'};}
 finally{
  FlowConnection.busy=false;renderBalances();clearTimeout(FlowConnection.timer);
  FlowConnection.timer=setTimeout(()=>refreshFlowConnection(),['refreshing','signing_in'].includes(FlowConnection.value?.status)?3000:60000);
 }
}
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
 if(item.currency==='USD'&&window.NexusCurrency)return window.NexusCurrency.cost(item.value);
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
  const isFlow=id==='google_flow',label=isFlow?flowConnectionText():!supported?'Saldo sin consultar':item?balanceText(item):AccountBalances.busy?'Consultando…':'Pendiente';
  const time=item?.time?new Date(item.time).toLocaleTimeString('es-ES',{hour:'2-digit',minute:'2-digit'}):'';
  const title=isFlow?flowConnectionHelp() : !supported?'La consulta de saldo no está implementada. Este mensaje no indica el estado de conexión del proveedor.':item?.error||'Saldo devuelto por el proveedor'+(item?.currency==='USD'?' ('+new Intl.NumberFormat('es-ES',{maximumFractionDigits:4}).format(item.value)+' USD, convertido a euros)':'')+(item?.time?' · '+new Date(item.time).toLocaleString('es-ES'):'');
  const settingsBalance=$('#balance-'+id);if(settingsBalance&&supported&&item)settingsBalance.textContent='Saldo: '+balanceText(item)+(item.error?' · Última consulta fallida':'');
  const connecting=['refreshing','signing_in'].includes(FlowConnection.value?.status);
  const flowAction=isFlow?(FlowConnection.value?.status==='auth_required'?'<button type="button" data-connect-flow style="width:auto;min-width:70px;font-size:var(--text-xs);padding:4px 10px" '+(FlowConnection.busy?'disabled':'')+'>Conectar</button>':'<button type="button" data-refresh-flow title="Renovar sesión de Flow" aria-label="Renovar sesión de Flow" '+(FlowConnection.busy||connecting?'disabled':'')+'>&#8635;</button>'):'';
  return `<span class="account-balance" title="${escape(title)}"><span>${escape(providers[id]||(id==='google_flow'?'Google Flow':id))}</span><strong>${escape(label)}</strong>${flowAction}${time?`<small>${item.error?'Último dato':'Consulta'} ${time}</small>`:''}</span>`;
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
render=function(){renderBeforeBalances();renderBalances();if(!AccountBalances.started&&initial&&!initial.qa)refreshBalances();if(!FlowConnection.value&&!FlowConnection.busy)refreshFlowConnection();};
document.addEventListener('click',e=>{if(e.target.closest('[data-refresh-flow]'))refreshFlowConnection(true);});
document.addEventListener('click',async e=>{
 if(!e.target.closest('[data-connect-flow]')||FlowConnection.busy)return;
 FlowConnection.busy=true;
 try{FlowConnection.value=await rpc('flowConnect',{});}
 catch(error){FlowConnection.value={status:'error'};toast(error.message);}
 finally{FlowConnection.busy=false;renderBalances();refreshFlowConnection();}
});
window.addEventListener('focus',()=>refreshFlowConnection());
document.addEventListener('click',e=>{if(e.target.closest('[data-refresh-balances]'))refreshBalances();});
document.addEventListener('click',e=>{const button=e.target.closest('[data-balance]');if(button&&!button.disabled){e.preventDefault();e.stopImmediatePropagation();refreshBalances();}},true);
renderBalances();if(initial&&!initial.qa)refreshBalances();
refreshFlowConnection();
