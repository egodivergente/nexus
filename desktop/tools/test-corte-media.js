const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const listeners={};
const context=vm.createContext({window:{},URL,document:{addEventListener:(name,fn)=>listeners[name]=fn}});
vm.runInContext(fs.readFileSync(path.join(__dirname,'../ui/corte-media.js'),'utf8'),context);
const media=context.window.CorteMedia,glyph=name=>'<svg data-icon="'+name+'"></svg>';
const asset={id:'a',type:'IMAGE',source:'GENERATED',displayName:'seedream-5-pro 1',uri:'https://invalid.example/image.png'};
assert.equal(media.identity(asset,{}).name,'Seedream');
assert.equal(media.identity({...asset,source:'IMPORTED',displayName:'flux.png'},{}),null);
assert.equal(media.identity({...asset,displayName:'seedream name changed by user',generationId:'g'},{generations:[{id:'g',modelId:'qwen-image-3.0-pro',providerId:'atlas_cloud'}]}).name,'Qwen');
assert.equal(media.previewSource('javascript:alert(1)'),null);
assert.equal(media.previewSource('file:///private.png'),null);
assert.equal(media.previewSource('/media/'+ 'a'.repeat(32)+'.png'),'/media/'+ 'a'.repeat(32)+'.png');
const html=media.markup({...asset,displayName:'<img src=x onerror="alert(1)">'},{},false,glyph);
assert(!html.includes('<img src=x'));assert(html.includes('&lt;img'));
assert(media.markup(asset,{},false,glyph).includes('data-corte-preview='));
const status={hidden:true},parent={title:''};const img={matches:()=>true,dataset:{cortePreview:'a|'+asset.uri},parentElement:parent,closest:()=>({querySelector:()=>status})};
listeners.error({target:img});assert.equal(img.hidden,true);assert.equal(status.hidden,false);
const failed=media.markup(asset,{},false,glyph);assert(!failed.includes('data-corte-preview='));assert(failed.includes('branding/providers/bytedance.png'));assert(failed.includes('Vista no disponible'));
assert(media.markup({...asset,uri:'https://invalid.example/new.png'}, {},false,glyph).includes('data-corte-preview='));
assert(media.markup({...asset,uri:'content://old-phone/image'}, {},false,glyph).includes('Vista no disponible'));
let loaded=false;listeners.load({target:{matches:()=>true,classList:{add:c=>loaded=c==='loaded'}}});assert(loaded);
for(const name of ['bytedance','qwen','flux','runway','kling','google','atlascloud']){
 const bytes=fs.readFileSync(path.join(__dirname,'../ui/branding/providers',name+'.png'));assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
}
console.log('PASS: model identity, seven logo files, safe URLs, escaped names, failed previews, retry after URI change and successful image loading');
