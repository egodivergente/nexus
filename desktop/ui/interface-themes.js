'use strict';
(()=>{
 const themes=[
  {id:'graphite',name:'Grafito',bg:'#0b0d10',sidebar:'#101318',panel:'#171b22',raised:'#242a34',border:'#373e4b',text:'#f1f3f7',muted:'#a9b1c0',accent:'#bca4ed'},
  {id:'ocean',name:'Océano',bg:'#0a1018',sidebar:'#101b28',panel:'#162332',raised:'#24374b',border:'#3c5067',text:'#eef5ff',muted:'#a8bbcf',accent:'#81bef5'},
  {id:'amber',name:'Ámbar',bg:'#14100e',sidebar:'#1c1713',panel:'#272019',raised:'#373027',border:'#55493a',text:'#fff4e7',muted:'#c8b8a4',accent:'#edb87c'},
  {id:'forest',name:'Bosque',bg:'#0c1210',sidebar:'#111d18',panel:'#192820',raised:'#283b30',border:'#3e5748',text:'#eef6ef',muted:'#adc3b5',accent:'#9bd3ad'},
  {id:'carbon',name:'Carbón',bg:'#101318',sidebar:'#161b22',panel:'#1e252e',raised:'#2b3540',border:'#414d59',text:'#eef5f7',muted:'#adbac5',accent:'#7cddcf'},
  {id:'oled',name:'Negro puro',bg:'#000000',sidebar:'#070809',panel:'#101114',raised:'#1c1e24',border:'#34363f',text:'#f5f5fa',muted:'#b2b2c2',accent:'#c7b6f4'}
 ];
 function selected(){return themes.find(t=>t.id===state.interfaceTheme)||themes.find(t=>t.id===(state.theme==='oled'?'oled':'graphite'));}
 function apply(){
  const t=selected(),body=document.body;body.dataset.interfaceTheme=t.id;
  for(const [key,value] of Object.entries({bg:t.bg,panel:t.panel,surface:t.raised,line:t.border,border:t.border,text:t.text,muted:t.muted,accent:t.accent,'nx-bg':t.bg,'nx-sidebar':t.sidebar,'nx-panel':t.panel,'nx-raised':t.raised,'nx-border':t.border,'nx-text':t.text,'nx-muted':t.muted,'nx-soft':t.muted,'surface-0':t.bg,'surface-1':t.panel,'surface-2':t.raised,'surface-3':t.raised,'surface-4':t.border}))body.style.setProperty('--'+key,value);
 }
 function markup(){return `<h2>Temas</h2><div class="interface-themes" role="group" aria-label="Tema de la interfaz">${themes.map(t=>`<button type="button" data-interface-theme="${t.id}" aria-pressed="${selected().id===t.id}" style="--swatch-bg:${t.bg};--swatch-panel:${t.raised};--swatch-accent:${t.accent}"><span class="theme-preview" aria-hidden="true"><i></i><i></i><i></i></span><span>${t.name}</span><span class="theme-selected" aria-hidden="true">✓</span></button>`).join('')}</div>`;}
 document.addEventListener('click',e=>{
  const button=e.target.closest('button[data-interface-theme]');if(!button)return;
  const t=themes.find(t=>t.id===button.dataset.interfaceTheme);if(!t)return;
  state.interfaceTheme=t.id;apply();persist();
  document.querySelectorAll('button[data-interface-theme]').forEach(n=>n.setAttribute('aria-pressed',String(n===button)));
 });
 window.NexusThemes={apply,markup,themes};apply();
 if(view==='settings')renderSettings();
})();
