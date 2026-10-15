'use strict';
(()=>{
 const themes=[
  {id:'aurora',name:'Aurora',bg:'#0A0D12',sidebar:'#0A0D12',panel:'#131922',raised:'#1C2533',border:'#303C4D',text:'#F0F3F8',muted:'#ADB7C6',accent:'#4EBAE8'},
  {id:'oled',name:'OLED',bg:'#000000',sidebar:'#000000',panel:'#131922',raised:'#1C2533',border:'#303C4D',text:'#F0F3F8',muted:'#ADB7C6',accent:'#4EBAE8'},
  {id:'quartz',name:'Quartz',bg:'#14110F',sidebar:'#14110F',panel:'#201B17',raised:'#302820',border:'#4C4137',text:'#F6EDE0',muted:'#C2B5A3',accent:'#E0C598'},
  {id:'ocean',name:'Océano',bg:'#0A1420',sidebar:'#0A1420',panel:'#122437',raised:'#1C3147',border:'#324C65',text:'#F0F3F8',muted:'#ADB7C6',accent:'#7DD3FC'},
  {id:'forest',name:'Bosque',bg:'#0D1712',sidebar:'#0D1712',panel:'#182A20',raised:'#263B2E',border:'#3B5144',text:'#F0F3F8',muted:'#ADB7C6',accent:'#A3D9AB'},
  {id:'light',name:'Claro',bg:'#F7F5EF',sidebar:'#F7F5EF',panel:'#FFFFFF',raised:'#EAE9E5',border:'#C6C8CB',text:'#191C23',muted:'#555C68',accent:'#243E74'}
 ];
 function selected(){const old={graphite:'aurora',amber:'quartz',carbon:'aurora'};return themes.find(t=>t.id===(old[state.interfaceTheme]||state.interfaceTheme))||themes.find(t=>t.id===(state.theme==='oled'?'oled':'aurora'));}
 function apply(){
  const t=selected(),body=document.body;body.dataset.interfaceTheme=t.id;body.style.setProperty('--danger',t.id==='light'?'#9C3E42':'#E6A5AA');body.style.setProperty('--error-container',t.id==='light'?'#F7DEDF':'#35262B');const light=t.id==='light',semantic=light?{success:'#2F7D4F',warning:'#8A5A00',danger:'#9C3E42',info:'#245C8F'}:{success:'#7FD4A0',warning:'#E8C27A',danger:'#E6A5AA',info:'#8FC7F0'};for(const [k,v] of Object.entries(semantic)){body.style.setProperty('--'+k,v);body.style.setProperty('--'+k+'-soft',v+(light?'1A':'1F'));}
  const lum=h=>{const c=[1,3,5].map(i=>parseInt(h.slice(i,i+2),16)/255).map(v=>v<=.03928?v/12.92:Math.pow((v+.055)/1.055,2.4));return .2126*c[0]+.7152*c[1]+.0722*c[2]};
  const ratio=(a,b)=>(Math.max(lum(a),lum(b))+.05)/(Math.min(lum(a),lum(b))+.05);
  const ink=ratio(t.accent,'#FFFFFF')>=4.5?'#FFFFFF':'#06141C';body.style.setProperty('--accent-ink',ink);
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
