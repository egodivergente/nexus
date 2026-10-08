# Shared layouts

## Application shell

- Source: `desktop/ui/index.html`
- Description: persistent sidebar, header, content mount point, status toast, and dialog host.

```html
<!doctype html><html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https: data:; media-src 'self' https: blob:; connect-src 'self'; object-src 'none'; frame-src 'none'"><title>NEXUS Studio</title><link rel="icon" href="favicon.ico"><link rel="stylesheet" href="style.css"><link rel="stylesheet" href="assistant.css"><link rel="stylesheet" href="workspace.css"><link rel="stylesheet" href="studio.css"><link rel="stylesheet" href="flow-ux.css"><link rel="stylesheet" href="desktop-design.css"></head><body><aside class="sidebar"><div class="brand"><svg width="34" height="34" viewBox="0 0 40 40"><path d="M8 31V9h6l12 22h6V9" fill="none" stroke="currentColor" stroke-width="4" stroke-linejoin="round"/></svg><span>NEXUS<small>CREATIVE STUDIO</small></span></div><div class="section-label">ESPACIO DE TRABAJO</div><nav id="navigation"></nav><div class="sidebar-bottom"><span class="status-dot"></span><span id="connection">Iniciando…</span></div></aside><main><header><div><p class="eyebrow" id="eyebrow">TU ESTUDIO, SIN LÍMITES CREATIVOS</p><h1 id="title">Modelos</h1></div><div id="header-actions"></div></header><div id="content"></div></main><div id="toast" role="status" aria-live="polite"></div><dialog id="dialog"></dialog><script src="engine.js"></script><script src="local-bridge.js"></script><script src="app.js"></script><script src="focus.js"></script><script src="polish.js"></script><script src="assistant.js"></script><script src="parity.js"></script><script src="workspace.js"></script><script src="studio.js"></script><script src="flow-assets.js"></script><script src="flow-ux.js"></script><script src="desktop-design.js"></script></body></html>
```

## Navigation and header renderer

- Source: `desktop/ui/app.js`
- Description: establishes the five main destinations and the page title/action area.

```js
function renderShell(){applyTheme();$('#content').classList.toggle('canvas-view',view==='canvas');$('#navigation').innerHTML=[['canvas','Lienzo'],['catalog','Modelos'],['history','Historial'],['library','Biblioteca'],['settings','Ajustes']].map(([id,text])=>`<button data-nav="${id}" class="${view===id?'active':''}">${icon(id)}${text}</button>`).join('');$('#title').textContent={catalog:'Tu próximo punto de partida',canvas:'Lienzo',history:'Historial',library:'Biblioteca',settings:'Tu estudio, a tu manera'}[view];$('#eyebrow').textContent=initial?.qa?'LABORATORIO · SIN CONEXIÓN NI GENERACIONES':'NEXUS STUDIO · '+{catalog:'MODELOS',canvas:'CREACIÓN',history:'ACTIVIDAD',library:'RESULTADOS',settings:'PERSONALIZACIÓN'}[view];$('#header-actions').innerHTML=view==='catalog'?`<button data-action="density" class="small">${state.compact?'Vista cómoda':'Vista compacta'}</button>`:view==='canvas'?'<button data-action="browse">Cambiar modelo</button>':'';}
```

## Desktop shell enhancement

- Source: `desktop/ui/desktop-design.js`
- Description: applies desktop labels and accessible navigation state after every render.

```js
function desktopShell(){
 applyTheme();document.body.dataset.desktopView=view;
 document.querySelectorAll('#navigation [data-nav],#studio-settings').forEach(button=>{
  const target=button.dataset.nav;button.setAttribute('aria-label',({canvas:'Crear',library:'Biblioteca',assistant:'Asistente',settings:'Ajustes'})[target]||target);
  if(button.classList.contains('active')||target==='settings'&&view==='settings')button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');
  button.title=button.getAttribute('aria-label');
 });
 const brand=$('.brand');if(brand&&!brand.querySelector('.nx-brand-subtitle'))brand.insertAdjacentHTML('beforeend','<small class="nx-brand-subtitle">ESTUDIO PERSONAL</small>');
 if(!$('#nx-sidebar-note'))$('#studio-settings')?.insertAdjacentHTML('beforebegin','<div id="nx-sidebar-note"><span>Un lugar para tus ideas.</span></div>');
 const eyebrow=$('#eyebrow');if(eyebrow)eyebrow.textContent=initial?.qa?'VISTA DE PRUEBA':'MI ESTUDIO';
}
```
