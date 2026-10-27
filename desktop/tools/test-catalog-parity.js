'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const P=require('../ui/catalog-parity.js');
const money=v=>'$'+v.toFixed(3);
const family=m=>m.modelFamily||m.family||m.organization||m.displayName;
const route=(id,extra={})=>({id,displayName:id,providerId:'atlas_cloud',modalities:['IMAGE'],modelFamily:'Kling',...extra});
let checks=0;const check=(name,fn)=>{fn();checks++;};

check('display categories preserve provider contracts and separate tools from generators',()=>{
 const kling=route('crun/kling/v3-turbo');
 assert.deepEqual(P.displayModalities(kling),['VIDEO']);
 assert.deepEqual(kling.modalities,['IMAGE']);
 assert.deepEqual(P.displayModalities(route('atlascloud/video-analysis')),['VIDEO']);
 assert.equal(P.categoryOf(route('meshy-v7/image-to-3d',{capabilities:['IMAGE_TO_3D']})),'3d');
 for(const id of ['runway/ruby/hdr','atlascloud/video-analysis','crun/image-inpaint','pixverse/v6/video-extend','runway/aleph2/v2v','bytedance/avatar-omni-human-v1.5','atlascloud/studio/ugc-ad'])assert.equal(P.categoryOf(route(id)),'tools',id);
 for(const id of ['bytedance/seedream-v5.0-lite','google/nano-banana-pro/edit-ultra','xai/grok-imagine-video-v1.5/text-to-video'])assert.equal(P.categoryOf(route(id)),'generation',id);
});

check('missing, zero, negative and non-numeric rates are never priced',()=>{
 for(const basePriceUsd of [null,undefined,'','   ',0,'0','0.000',-1,'-0.5',NaN,Infinity,'Infinity','abc','0x10','1,5',true,false,[1],['0.5'],{},{valueOf:()=>2}]){
  for(const priceUnitVerified of [true,false]){
   const price=P.routePrice(route('x',{basePriceUsd,priceUnitVerified,priceUnit:'img'}),money);
   assert.equal(price.state,'unknown',String(basePriceUsd));
   assert.equal(price.text,'Precio por confirmar');
   assert.doesNotMatch(price.text,/\$|0[,.]00|gratis|free|incluido|desde/i);
  }
 }
});
check('verified unit is required for a priced label; unverified keeps the base honest',()=>{
 assert.equal(P.routePrice(route('a',{basePriceUsd:0.04,priceUnitVerified:true,priceUnit:'img'}),money).text,'$0.040 / imagen');
 assert.equal(P.routePrice(route('a',{basePriceUsd:'0.072',priceUnitVerified:true,priceUnit:'second'}),money).text,'$0.072 / s');
 assert.equal(P.routePrice(route('a',{basePriceUsd:0.04,priceUnitVerified:false,priceUnit:'img'}),money).text,'Base $0.040 · unidad sin confirmar');
 assert.equal(P.routePrice(route('a',{basePriceUsd:0.04,priceUnitVerified:true,priceUnit:'unidad sin especificar'}),money).state,'unconfirmed');
 assert.equal(P.sortablePrice(route('a',{basePriceUsd:0,priceUnitVerified:true,priceUnit:'img'})),Infinity);
});
check('family "Desde" only with a verified, positive, single-unit rate',()=>{
 const unknown=[route('a'),route('b',{basePriceUsd:0,priceUnitVerified:true,priceUnit:'img'})];
 assert.equal(P.familyPrice(unknown,money).text,'Precio por confirmar');
 const unconfirmed=[route('a',{basePriceUsd:0.05}),route('b')];
 const u=P.familyPrice(unconfirmed,money);
 assert.equal(u.state,'unconfirmed');assert.doesNotMatch(u.text,/Desde|\$/);
 const mixed=[route('a',{basePriceUsd:0.05,priceUnitVerified:true,priceUnit:'img'}),route('b',{basePriceUsd:0.01,priceUnitVerified:true,priceUnit:'second'})];
 assert.equal(P.familyPrice(mixed,money).state,'mixed');
 const partial=P.familyPrice([route('a',{basePriceUsd:0.05,priceUnitVerified:true,priceUnit:'img'}),route('b',{basePriceUsd:0.03,priceUnitVerified:true,priceUnit:'image'}),route('c')],money);
 assert.equal(partial.text,'Desde $0.030 / imagen · tarifas parciales');assert.equal(partial.state,'partial');assert.match(partial.title,/2 de 3/);
 const complete=P.familyPrice([route('a',{basePriceUsd:0.05,priceUnitVerified:true,priceUnit:'img'}),route('b',{basePriceUsd:' 0.03 ',priceUnitVerified:true,priceUnit:'image'})],money);
 assert.equal(complete.text,'Desde $0.030 / imagen');assert.equal(complete.state,'known');
 assert.equal(P.familyPrice([route('solo',{basePriceUsd:0.05,priceUnitVerified:true,priceUnit:'img'})],money).text,'$0.050 / imagen');
});
check('real desktop catalogue: no unknown/zero rate is ever shown as a price or "Desde"',()=>{
 const models=JSON.parse(fs.readFileSync(path.join(__dirname,'../ui/catalog.json'),'utf8'));
 let unknown=0;
 for(const m of models){
  const p=P.routePrice(m,money),rate=Number(m.basePriceUsd);
  if(m.basePriceUsd==null||!(rate>0)){unknown++;assert.equal(p.text,'Precio por confirmar',m.id);}
  if(p.state==='known')assert.ok(m.priceUnitVerified&&rate>0,m.id);
 }
 assert.ok(unknown>0,'catalogue fixture should contain unknown prices');
 const groups=new Map();for(const m of models)for(const k of m.modalities){const key=k+'|'+family(m);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(m);}
 for(const [key,variants] of groups){
  const p=P.familyPrice(variants,money);
  if(/^Desde /.test(p.text))assert.ok(variants.some(v=>P.priceInfo(v).state==='known'),key);
  if(!variants.some(v=>P.priceInfo(v).state==='known'))assert.doesNotMatch(p.text,variants.length>1?/Desde|\$/:/Desde|^\$/,key);
 }
});
check('hiding a family captures its routes and survives a catalogue refresh with new variants',()=>{
 const state={hidden:['other']};
 const v1=[route('k1'),route('k2'),route('k3',{modalities:['VIDEO']}),route('f1',{modelFamily:'Flux'})];
 const key=P.hideFamily(state,'IMAGE','Kling',v1,family,1);
 assert.deepEqual(state.hidden,['other','k1','k2']);
 const refreshed=[route('k2'),route('k4'),route('f1',{modelFamily:'Flux'})];
 assert.equal(P.syncHidden(state,refreshed,family),1);
 assert.deepEqual(state.hidden,['other','k1','k2','k4'],'k1 is kept although absent from the refreshed catalogue');
 assert.equal(P.syncHidden(state,refreshed,family),0,'idempotent');
 const entries=P.hiddenEntries(state,refreshed,family);
 assert.equal(entries.families[0].count,3);assert.equal(entries.families[0].present,2);
 assert.deepEqual(entries.routes.map(r=>r.id),['other']);assert.equal(entries.routes[0].model,null);
 P.restoreFamily(state,key);
 assert.deepEqual(state.hidden,['other'],'restoring the family keeps routes hidden individually before');
 assert.deepEqual(state.hiddenFamilies,{});
});
check('a variant shown from a hidden family stays visible after refresh until hidden again',()=>{
 const state={};const v=[route('k1'),route('k2')];
 P.hideFamily(state,'IMAGE','Kling',v,family);
 assert.equal(P.toggleRoute(state,'k1'),false);
 assert.equal(P.syncHidden(state,[...v,route('k3')],family),1,'only the new variant is hidden');
 assert.deepEqual(state.hidden,['k2','k3']);
 assert.equal(P.toggleRoute(state,'k1'),true);
 assert.deepEqual(state.hiddenFamilies['IMAGE|Kling'].shown,[]);
 P.restoreFamily(state,'IMAGE|Kling');assert.deepEqual(state.hidden,['k1'],'re-hidden individually, so it survives restoring the family');
});
check('route toggle, restore all and persisted shape stay JSON-serialisable',()=>{
 const state={};
 assert.equal(P.toggleRoute(state,'a'),true);P.hideFamily(state,'IMAGE','Kling',[route('k1'),route('a')],family);
 assert.deepEqual(state.hidden,['a','k1']);
 assert.equal(P.toggleRoute(state,'k1'),false);
 assert.deepEqual(state.hiddenFamilies['IMAGE|Kling'].routes,[],'unhidden route leaves the family capture');
 assert.deepEqual(state.hiddenFamilies['IMAGE|Kling'].shown,['k1']);
 assert.deepEqual(JSON.parse(JSON.stringify(state)),state);
 P.restoreAll(state);assert.deepEqual(state,{hidden:[],hiddenFamilies:{}});
 const legacy={hidden:'broken',hiddenFamilies:[]};P.ensureHidden(legacy);assert.deepEqual(legacy,{hidden:[],hiddenFamilies:{}});
});
check('density is configurable, validated and migrates the legacy compact flag',()=>{
 assert.equal(P.densityOf({}),'comfortable');
 assert.equal(P.densityOf({compact:true}),'compact');
 const state={compact:true};
 assert.equal(P.setDensity(state,'spacious'),true);assert.deepEqual(state,{compact:false,catalogDensity:'spacious'});
 assert.equal(P.setDensity(state,'huge'),false);assert.equal(P.densityOf(state),'spacious');
 assert.equal(P.densityOf({catalogDensity:'bogus',compact:false}),'comfortable');
});
check('app.js and polish.js no longer derive "Desde"/zero labels from raw rates',()=>{
 const app=fs.readFileSync(path.join(__dirname,'../ui/app.js'),'utf8'),polish=fs.readFileSync(path.join(__dirname,'../ui/polish.js'),'utf8');
 assert.doesNotMatch(app,/'Desde '\+money/);
 assert.match(app,/data-hide-family=/);
 const index=fs.readFileSync(path.join(__dirname,'../ui/index.html'),'utf8');
 assert.ok(index.indexOf('src="catalog-parity.js"')>index.indexOf('src="polish.js"')&&index.indexOf('src="catalog-parity.js"')>index.indexOf('src="focus.js"'),'catalog-parity must wrap after polish/focus');
 assert.doesNotMatch(polish,/Cotizar antes de usar/);
});
console.log(`catalog-parity: ${checks} checks passed`);
