'use strict';
// Fase 6: etiquetas accesibles y tooltips en la navegación cuando la barra lateral se contrae a iconos.
(()=>{
 const label=btn=>{
  if(btn.title)return;
  const text=(btn.getAttribute('aria-label')||btn.textContent||'').trim();
  if(text)btn.title=text;
 };
 const apply=()=>document.querySelectorAll('#navigation button, .sidebar button[data-nav]').forEach(label);
 const host=document.querySelector('.sidebar')||document.body;
 new MutationObserver(apply).observe(host,{childList:true,subtree:true});
 apply();
})();
