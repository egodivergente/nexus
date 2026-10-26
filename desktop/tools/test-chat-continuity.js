// Unit tests for desktop/ui/chat-continuity.js: pure markup/contract helpers plus the
// archive/open/rename flow against an in-memory host. No network, no paid calls.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../ui/chat-continuity.js'),'utf8');
const CC=require('../ui/chat-continuity.js');
let passed=0;const check=(name,fn)=>{fn();passed++;};

check('assistant answers never fold',()=>{
 const long=Array.from({length:40},(_,i)=>'línea '+i).join('\n');
 const html=CC.messageMarkup({id:'a1',role:'assistant',text:long,html:'<p>x</p>'});
 assert(!html.includes('cc-foldable')&&!html.includes('cc-fold-toggle')&&!html.includes('cc-fold-body'));
 assert(html.includes('<p>x</p>'),'el HTML del asistente se conserva');
 assert(html.includes('data-cc-copy="a1"')&&html.includes('data-cc-select="a1"'));
});
check('user messages fold with an accessible toggle',()=>{
 const html=CC.messageMarkup({id:'u1',role:'user',text:'<b>hola</b>'});
 assert(html.includes('class="agent-message user cc-foldable"'));
 assert(html.includes('&lt;b&gt;hola&lt;/b&gt;'),'el texto del usuario se escapa');
 assert(/data-cc-fold="u1" aria-controls="cc-text-u1" aria-expanded="false" hidden>Expandir</.test(html));
 assert(html.includes('id="cc-text-u1"'));
 const open=CC.messageMarkup({id:'u1',role:'user',text:'x'},{expanded:true});
 assert(open.includes('cc-expanded')&&/aria-expanded="true">Contraer</.test(open));
});
check('markup keeps legacy hooks used by review.js/desktop-design.js/agent-parity.js',()=>{
 const html=CC.messageMarkup({id:'u2',role:'user',text:'t',attachments:[{name:'a.png'}],failed:true,usage:'1 entrada'});
 for(const hook of ['data-agent-message="u2"','class="agent-author"','class="agent-text','agent-file','Envío no confirmado'])assert(html.includes(hook),hook);
 const fragment=html.replace(/<[^>]+>/g,'');assert(!fragment.includes('<'));
});
check('ids match the host identifier rule',()=>{
 for(let i=0;i<50;i++)assert.match(CC.newId(),/^[A-Za-z0-9_-]{1,100}$/);
});
check('history sent to the model contains only user/assistant text',()=>{
 assert.deepEqual(CC.historyItems([{role:'user',text:'a',id:1},{role:'assistant',text:''},{role:'notice',text:'n'},{role:'assistant',text:'b',html:'<p>b</p>'}]),[{role:'user',text:'a'},{role:'assistant',text:'b'}]);
});
check('archive payload carries stable id, draft and attachments',()=>{
 const payload=CC.archivePayload({id:'old',anchors:{x:1},resultPlans:[]},{items:[{role:'user',text:'a'}],draft:'borrador',attachments:[{id:'f'}]},'stable',123);
 assert.equal(payload.id,'stable');assert.equal(payload.draft,'borrador');assert.deepEqual(payload.draftAttachments,[{id:'f'}]);assert.equal(payload.updatedAt,123);assert.deepEqual(payload.anchors,{x:1});
});
check('server-side search is trusted; legacy lists filter locally',()=>{
 const modern=[{id:'a',title:'Otra',preview:'',messages:1}];
 assert.deepEqual(CC.filterChats(modern,'gato'),modern,'el host ya filtró por el cuerpo');
 const legacy=[{id:'a',title:'Vídeo del gato',messages:1},{id:'b',title:'Logo',messages:2}];
 assert.deepEqual(CC.filterChats(legacy,'GATO').map(c=>c.id),['a']);
 assert.equal(CC.filterChats(legacy,'').length,2);
});
check('row and rename markup',()=>{
 const now=Date.UTC(2026,9,25,12);
 const row=CC.chatRowMarkup({id:'c1',title:'<Título>',preview:'p',messages:1,updatedAt:now,customTitle:'<Título>'},{current:true,now});
 assert(row.includes('&lt;Título&gt;')&&!row.includes('<Título>'));
 assert(row.includes('aria-current="true"')&&row.includes('1 mensaje ·')&&row.includes('Título propio'));
 assert(row.includes('aria-label="Renombrar «&lt;Título&gt;»"'));
 const form=CC.renameFormMarkup({id:'c1',title:'Auto',customTitle:null});
 assert(form.includes('id="cc-rename-form"')&&!form.includes('data-cc-reset'),'sin título propio no hay restablecer');
 assert(CC.renameFormMarkup({id:'c1',title:'Mío',customTitle:'Mío'}).includes('data-cc-reset="c1"'));
 assert.equal(CC.chatWhen(0),'');
});

check('synced media remains visible and unsafe links stay inert',()=>{
 const html=CC.messageMarkup({id:'sync',role:'assistant',text:'Adjuntos',cancelled:true,attachments:[
  {name:'foto.png',mime:'image/png',url:'/media/foto.png'},
  {name:'clip.mp4',mime:'video/mp4',url:'/media/clip.mp4'},
  {name:'inert.txt',url:'javascript:alert(1)'}]});
 assert(html.includes('<img src="/media/foto.png"')&&html.includes('<video src="/media/clip.mp4"'));
 assert(html.includes('Turno detenido')&&!html.includes('href="javascript:'));
});

// Flow tests: run the browser half against a minimal DOM double and an in-memory host.
function harness({failNew=false,failLoad=false}={}){
 const listeners={};const calls=[];const store=new Map([['android1',{items:[{role:'user',text:'de Android'}]}]]);let failNewOnce=failNew;
 const doc={addEventListener:(t,f)=>(listeners[t]||=[]).push(f),getElementById:()=>null,querySelector:()=>null,querySelectorAll:()=>[],body:{append(){}},createElement:()=>({style:{},setAttribute(){},remove(){}})};
 const ctx={document:doc,localStorage:{getItem:()=>null,setItem(){}},navigator:{},addEventListener(){},getSelection:()=>null,crypto:{randomUUID:()=>'11111111-2222-3333-4444-555555555555'},CSS:{escape:s=>s},setTimeout,clearTimeout,console,
  toast:m=>calls.push(['toast',m]),persist(){},renderAssistant(){},navigate(){},view:'assistant',
  state:{agentConversation:{draft:'mi borrador',items:[]}},
  Agent:{items:[{id:'m1',role:'user',text:'hola'}],draft:'mi borrador',attachments:[{id:'f1',name:'a.png'}],busy:false,generating:false,cardAnchors:new Map(),expandedBatches:new Set(),error:''},
  async rpc(op,args){calls.push([op,JSON.parse(JSON.stringify(args||{}))]);
   if(op==='archiveChat'){const id=args.id||'server';store.set(id,{...args,id});return {saved:true,id};}
   if(op==='loadChat'){if(failLoad)throw Error('no se pudo leer');const v=store.get(args.id);if(!v)throw Error('no existe');return JSON.parse(JSON.stringify(v));}
   if(op==='renameChat')return {id:args.id,title:args.title||'Auto',customTitle:args.title||null};
   if(op==='listChats')return [...store.keys()].map(id=>({id,title:id,messages:1,preview:'',updatedAt:1,customTitle:null}));},
  async agentCall(op,args){calls.push(['agent:'+op,JSON.parse(JSON.stringify(args||{}))]);if(op==='new'&&failNewOnce){failNewOnce=false;throw Error('modelo caído');}return {};}
 };
 ctx.window=ctx;vm.createContext(ctx);
 vm.runInContext('const escape=s=>String(s??"").replace(/[&<>"\']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\\"":"&quot;","\'":"&#39;"}[c]));\n'+source,ctx);
 return {ctx,CC:ctx.NexusChatContinuity,calls,store,listeners};
}
const flows=[];
flows.push((async()=>{
 const {ctx,CC:api,calls,store}=harness();
 const id1=await api.archiveCurrent();
 assert.equal(id1,'11111111222233334444555555555555');
 assert.equal(ctx.state.agentConversation.id,id1,'el id devuelto queda en la conversación');
 await api.archiveCurrent();
 assert.equal(calls.filter(c=>c[0]==='archiveChat').every(c=>c[1].id===id1),true,'archivar dos veces reutiliza el id');
 assert.equal([...store.keys()].filter(k=>k===id1).length,1);
 assert.equal(store.get(id1).draft,'mi borrador');assert.deepEqual(store.get(id1).draftAttachments,[{id:'f1',name:'a.png'}]);
 assert.equal(await api.open('android1'),true);
 assert.deepEqual(ctx.Agent.items.map(i=>i.text),['de Android']);
 assert.equal(ctx.state.agentConversation.id,'android1','un chat sin id guardado toma el id pedido');
 const order=calls.map(c=>c[0]).filter(op=>['loadChat','archiveChat','agent:new','agent:restoreHistory'].includes(op)).slice(-4);
 assert.deepEqual(order,['loadChat','archiveChat','agent:new','agent:restoreHistory']);
 assert.deepEqual(calls.at(-1)[1].items,[{role:'user',text:'de Android'}]);
 assert.equal(await api.open('android1'),false,'abrir la actual no la vuelve a archivar');
 await api.open(id1);
 assert.equal(ctx.Agent.draft,'mi borrador');assert.deepEqual(ctx.Agent.attachments,[{id:'f1',name:'a.png'}]);
 assert.equal(calls.filter(c=>c[0]==='archiveChat'&&c[1].id==='android1').length,1,'el chat de Android se archiva con su propio id');
 passed++;
})());
flows.push((async()=>{
 const {ctx,CC:api,calls,store}=harness({failNew:true});
 const before=ctx.Agent.items;
 await assert.rejects(api.open('android1'),/modelo caído/);
 assert.equal(ctx.Agent.items,before);assert.equal(ctx.Agent.draft,'mi borrador');assert.deepEqual(ctx.Agent.attachments,[{id:'f1',name:'a.png'}]);
 const archivedId=ctx.state.agentConversation.id;assert(archivedId,'conserva el id ya archivado');
 await api.open('android1');
 assert.equal(calls.filter(c=>c[0]==='archiveChat').every(c=>c[1].id===archivedId),true,'el reintento no duplica el archivo');
 assert.equal([...store.keys()].length,2);
 passed++;
})());
flows.push((async()=>{
 const {ctx,CC:api,calls}=harness({failLoad:true});
 await assert.rejects(api.open('android1'),/no se pudo leer/);
 assert.equal(calls.some(c=>c[0]==='archiveChat'||c[0]==='agent:new'),false,'si no se puede leer, no se archiva ni se reinicia nada');
 assert.equal(ctx.Agent.items.length,1);
 ctx.Agent.busy=true;await assert.rejects(api.open('android1'),/Espera/);
 passed++;
})());
flows.push((async()=>{
 const {CC:api,calls}=harness();
 api._panel.chats=[{id:'c1',title:'Auto',messages:1}];
 await api.rename('c1','  Mi vídeo  ');
 assert.deepEqual(JSON.parse(JSON.stringify(calls.find(c=>c[0]==='renameChat')[1])),{id:'c1',title:'  Mi vídeo  '});
 assert.equal(api._panel.chats[0].customTitle,'  Mi vídeo  ');
 await api.rename('c1','');
 assert.equal(api._panel.chats[0].customTitle,null);assert.equal(api._panel.chats[0].title,'Auto');
 assert(calls.some(c=>c[0]==='toast'&&/restablecido/.test(c[1])));
 passed++;
})());
flows.push((async()=>{
 const {ctx,CC:api,calls,store}=harness();
 ctx.Agent.items=[];ctx.Agent.attachments=[];ctx.Agent.draft='   ';
 assert.equal(await api.archiveCurrent(),null,'nada que archivar');
 ctx.Agent.draft='solo borrador';
 const id=await api.archiveCurrent();
 assert(id&&store.get(id).draft==='solo borrador'&&store.get(id).items.length===0,'un borrador sin mensajes también se archiva');
 ctx.Agent.draft='';ctx.Agent.attachments=[{id:'f2'}];ctx.state.agentConversation={};
 const second=await api.archiveCurrent();assert.deepEqual(store.get(second).draftAttachments,[{id:'f2'}]);
 passed++;
})());
flows.push((async()=>{
 const {ctx,CC:api,calls,store}=harness();
 const rpc=ctx.rpc;let release;const gate=new Promise(r=>release=r);
 ctx.rpc=async(op,args)=>{if(op==='loadChat'&&args.id==='android1')await gate;return rpc(op,args);};
 store.set('other',{items:[{role:'user',text:'otro'}]});
 const first=api.open('android1');
 assert.equal(api.isSwitching(),true);
 await assert.rejects(api.open('other'),/Ya se está abriendo/);
 release();await first;
 assert.equal(api.isSwitching(),false);
 assert.deepEqual(ctx.Agent.items.map(i=>i.text),['de Android'],'el segundo clic no mezcla conversaciones');
 assert.equal(calls.filter(c=>c[0]==='agent:new').length,1);
 const failing=harness({failLoad:true});await assert.rejects(failing.CC.open('android1'));assert.equal(failing.CC.isSwitching(),false,'el bloqueo se libera tras un fallo');
 passed++;
})());
Promise.all(flows).then(()=>console.log(`PASS chat-continuity: ${passed} comprobaciones (plegado, acciones, archivo con id estable, rollback, renombrar); 0 llamadas de red`))
 .catch(error=>{console.error(error);process.exit(1);});
