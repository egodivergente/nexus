'use strict';
(()=>{
 const previous=renderSettings;
 let timer;
 const fields=[['skills','Skills personales y sus archivos'],['credentials','Claves API y puentes'],['settings','Ajustes y perfiles compatibles'],['agy','Sesion AGY'],['deletions','Propagar eliminaciones sin cambios locales']];
 async function refresh(){
  const section=document.querySelector('#device-continuity');if(!section){clearInterval(timer);return;}
  try{const state=await rpc('deviceContinuity',{op:'status'});section.querySelector('[data-continuity-status]').textContent=state.status;
   if(!section.dataset.loaded){for(const name of ['enabled',...fields.map(f=>f[0])])section.querySelector(`[name=${name}]`).checked=!!state[name];
    for(const name of ['address','port','interval','timeout','maxFileMb','agyDirectory'])section.querySelector(`[name=${name}]`).value=state[name]??'';
    section.querySelector('[name=peers]').value=(state.peers||[]).join('\n');section.dataset.loaded='1';}
  }catch(error){section.querySelector('[data-continuity-status]').textContent=error.message;}
 }
 renderSettings=function(){previous();
  const section=document.createElement('section');section.id='device-continuity';section.className='sheet';
  section.innerHTML='<h2>Continuidad con tus moviles</h2><p data-continuity-status role="status">Consultando...</p><p class="hint">Drive comparte proyectos, galeria y chats. Este enlace cifrado comparte skills, claves, ajustes y AGY cuando Nexus esta abierto en Windows.</p><label><input name="enabled" type="checkbox"> Sincronizacion automatica</label><label>Direcciones de los moviles, una por linea<textarea name="peers" rows="2" placeholder="http://100.x.x.x:8795"></textarea></label><label>Direccion de este PC accesible desde los moviles<input name="address" placeholder="http://100.x.x.x:8797"></label><label>Clave de vinculacion compartida<input name="key" type="password" autocomplete="off" placeholder="Dejar vacio para conservarla"></label><div class="row"><label>Puerto<input name="port" type="number" min="1024" max="65535"></label><label>Intervalo (segundos)<input name="interval" type="number" min="1"></label><label>Espera de red (0 sin limite)<input name="timeout" type="number" min="0"></label><label>MB por archivo (0 sin limite)<input name="maxFileMb" type="number" min="0"></label></div><label>Carpeta de sesion AGY en Windows<input name="agyDirectory"></label><div data-continuity-scopes></div><div class="row"><button data-continuity="save">Guardar vinculacion</button><button data-continuity="run">Sincronizar ahora</button></div>';
  for(const [name,title] of fields){const label=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.name=name;label.append(input,document.createTextNode(' '+title));section.querySelector('[data-continuity-scopes]').append(label);}
  document.querySelector('#settings-container')?.append(section);refresh();clearInterval(timer);timer=setInterval(refresh,5000);
 };
 document.addEventListener('click',async event=>{const button=event.target.closest('[data-continuity]');if(!button)return;
  const section=button.closest('#device-continuity');button.disabled=true;
  try{const args={op:button.dataset.continuity==='save'?'configure':'run'};
   if(args.op==='configure'){for(const name of ['enabled',...fields.map(f=>f[0])])args[name]=section.querySelector(`[name=${name}]`).checked;
    for(const name of ['port','interval','timeout','maxFileMb'])args[name]=Number(section.querySelector(`[name=${name}]`).value);
    for(const name of ['address','key','agyDirectory'])args[name]=section.querySelector(`[name=${name}]`).value.trim();
    args.peers=section.querySelector('[name=peers]').value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);}
   await rpc('deviceContinuity',args);section.querySelector('[name=key]').value='';await refresh();
  }catch(error){toast(error.message);}finally{button.disabled=false;}
 });
})();
