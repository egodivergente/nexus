'use strict';
// Presentation only: keep the existing catalogue, dispatch and confirmation paths.
const DesktopDesign={version:'2026.10.26-desktop-modern.2',ready:false};
const desktopThemeBefore=applyTheme;
applyTheme=function(){desktopThemeBefore();document.body.classList.add('nx-desktop');};
const desktopSpark='<img class="nexus-mark" src="branding/nexus-mark.png" alt="" width="20" height="20" aria-hidden="true">';
function desktopVersionLabel(){
 const raw=(typeof initial!=='undefined'&&initial?.version)?String(initial.version):DesktopDesign.version;
 return raw.startsWith('v')?raw:'v'+raw;
}
function syncVersionBadges(){
 const label=desktopVersionLabel();
 document.querySelectorAll('[data-nx-version]').forEach(node=>{if(node.textContent!==label)node.textContent=label;});
}
function mountStartupOnboarding(){
 if((typeof initial!=='undefined'&&initial?.qa)||document.getElementById('nx-startup-onboarding'))return;
 const overlay=document.createElement('div');
 overlay.id='nx-startup-onboarding';
 overlay.className='nx-startup-onboarding';
 overlay.setAttribute('role','status');
 overlay.setAttribute('aria-label','Bienvenida a NEXUS Studio');
 overlay.innerHTML='<div class="nx-startup-card"><img class="nx-startup-logo" src="branding/nexus-logo.png" alt="Logo oficial de NEXUS" width="136" height="130"><span class="nx-version-badge" data-nx-version>'+desktopVersionLabel()+'</span><span class="nx-kicker">TU ESPACIO CREATIVO</span><h2>Las buenas ideas empiezan aquí.</h2><p>Estudio y agente multimodal</p></div>';
 const dismiss=()=>{if(!overlay.isConnected||overlay.classList.contains('is-hiding'))return;overlay.classList.add('is-hiding');setTimeout(()=>overlay.remove(),360);};
 overlay.addEventListener('click',dismiss,{once:true});
 document.addEventListener('keydown',dismiss,{once:true});
 setTimeout(dismiss,1850);
 document.body.append(overlay);
}
const desktopIdeas=[
 {id:'image',title:'Una imagen',subtitle:'De una idea a algo visual',icon:'library',text:'Quiero crear una imagen. Ayúdame a preparar un buen prompt y elegir el modelo.'},
 {id:'video',title:'Un vídeo',subtitle:'Una escena con movimiento',icon:'canvas',text:'Quiero crear un vídeo. Ayúdame a definir la escena y preparar sus opciones.'},
 {id:'improve',title:'Mejorar un resultado',subtitle:'Más detalle, otro acabado',icon:'settings',text:'Quiero mejorar uno de mis resultados existentes. Ayúdame a elegirlo y revisar las opciones compatibles.'}
];
function desktopIdeaButtons(target){return '<div class="nx-ideas">'+desktopIdeas.map(idea=>'<button type="button" data-desktop-idea="'+idea.id+'" data-desktop-target="'+target+'">'+icon(idea.icon)+'<span><strong>'+idea.title+'</strong><small>'+idea.subtitle+'</small></span></button>').join('')+'</div>';}
function desktopWelcome(kind){return '<div class="nx-welcome-mark nexus-welcome"><img src="branding/nexus-logo.png" alt="Logo oficial de NEXUS" width="118" height="112"></div><div class="nx-version-row"><span class="nx-version-badge" data-nx-version>'+desktopVersionLabel()+'</span></div><span class="nx-kicker">TU ESPACIO CREATIVO</span><h2>'+(kind==='assistant'?'Las buenas ideas empiezan aquí.':'Dale forma a tu próxima idea.')+'</h2><p>'+(kind==='assistant'?'Cuéntame qué tienes en mente. Lo preparamos juntos.':'Una imagen, un vídeo o un nuevo acabado. Tú eliges por dónde empezar.')+'</p>';}
function desktopShell(){
 applyTheme();document.body.dataset.desktopView=view;
 const favicon=document.querySelector('link[rel="icon"]');if(favicon&&favicon.getAttribute('href')!=='branding/nexus.ico')favicon.setAttribute('href','branding/nexus.ico');
 document.querySelectorAll('#navigation [data-nav],#studio-settings').forEach(button=>{
  const target=button.dataset.nav;button.setAttribute('aria-label',({canvas:'Crear',library:'Biblioteca',assistant:'Asistente',settings:'Ajustes'})[target]||target);
  if(button.classList.contains('active')||target==='settings'&&view==='settings')button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');
  button.title=button.getAttribute('aria-label');
 });
 const brand=$('.brand');if(brand&&!brand.querySelector('.nexus-brand-mark')){
  const toggle=brand.querySelector('#creative-dock-toggle')||$('#creative-dock-toggle');
  brand.setAttribute('aria-label','NEXUS Studio');
  brand.innerHTML='<div class="nx-brand-identity"><img class="nexus-brand-mark" src="branding/nexus-mark.png" alt="NEXUS" width="26" height="26"><img class="nexus-brand-logo" src="branding/nexus-logo.png" alt="Logo oficial de NEXUS" width="32" height="30"><strong class="nx-brand-title">NEXUS</strong><span class="nx-brand-version nx-version-badge" data-nx-version>'+desktopVersionLabel()+'</span></div>';
  if(toggle)brand.append(toggle);
 }
 const eyebrow=$('#eyebrow');if(eyebrow)eyebrow.textContent=initial?.qa?'VISTA DE PRUEBA':'MI ESTUDIO · '+desktopVersionLabel();
 syncVersionBadges();
}
const desktopAssistantBefore=renderAssistant;
renderAssistant=function(){
 desktopAssistantBefore();
 const empty=$('.agent-empty');if(empty){empty.classList.add('nx-assistant-welcome');empty.innerHTML=desktopWelcome('assistant')+desktopIdeaButtons('assistant');}
 const input=$('#agent-input');if(input){input.placeholder='Escribe un mensaje o describe lo que quieres crear…';input.setAttribute('aria-label','Tu idea para el asistente');}
 document.querySelectorAll('.agent-message .agent-author:not(:has(.nx-avatar))').forEach(author=>{
  author.insertAdjacentHTML('afterbegin','<span class="nx-avatar" aria-hidden="true">'+(author.closest('.user')?'<svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M5 20v-2a7 7 0 0 1 14 0v2" stroke="currentColor" stroke-width="1.5"/></svg>':desktopSpark)+'</span>');
 });
 syncVersionBadges();
};
const desktopCanvasBefore=renderCanvas;
renderCanvas=function(){
 desktopCanvasBefore();
 const start=$('.studio-start');if(start){
  start.classList.add('nx-create-start');start.insertAdjacentHTML('afterbegin','<div class="nx-create-welcome">'+desktopWelcome('canvas')+'</div>');
  const prompt=start.querySelector('#prompt');prompt.placeholder='Describe la escena, el estilo o lo que quieres sentir…';
  start.querySelector('label').textContent='Tu idea';
  start.insertAdjacentHTML('beforeend','<p class="nx-start-hint">Podrás revisar el modelo, las opciones y el coste antes de generar.</p>');
 }
 const blank=$('.studio-blank');if(blank)blank.innerHTML='<div class="nx-preview-empty"><img class="nx-preview-logo" src="branding/nexus-logo.png" alt="Logo oficial de NEXUS" width="72" height="69"><span class="nx-version-badge" data-nx-version>'+desktopVersionLabel()+'</span><strong>Tu próxima creación</strong><span>Los resultados aparecerán aquí.</span></div>';
 const selected=$('.canvas .selected');if(selected&&!selected.querySelector('.nx-selected-label'))selected.insertAdjacentHTML('afterbegin','<span class="nx-selected-label">MODELO</span>');
 syncVersionBadges();
};
const desktopRenderBefore=render;
render=function(){desktopRenderBefore();desktopShell();};
document.addEventListener('click',event=>{
 const button=event.target.closest('[data-desktop-idea]');if(!button||button.disabled)return;
 const idea=desktopIdeas.find(item=>item.id===button.dataset.desktopIdea);if(!idea||Agent.busy)return;
 Agent.draft=idea.text;const input=$('#agent-input');if(input){input.value=idea.text;input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();input.setSelectionRange(input.value.length,input.value.length);}
});
document.addEventListener('DOMContentLoaded',()=>{desktopShell();mountStartupOnboarding();DesktopDesign.ready=true;},{once:true});

