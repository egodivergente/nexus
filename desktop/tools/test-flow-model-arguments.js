'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const context=vm.createContext({});
const source=fs.readFileSync(path.join(__dirname,'../ui/flow-assets.js'),'utf8');
vm.runInContext(source.slice(0,source.indexOf('// Keep the existing providers')),context);
for(const model of ['nano-pro','nano2','veo_fast','veo_quality','veo_lite','omni_flash']){
 context.model={providerModelId:model,promptRequired:true};
 context.values={aspect:model.startsWith('nano')?'3:4':'9:16',source_request_id:'irrelevant'};
 const result=JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values)',context)));
 assert.deepEqual(result,{model,aspect:context.values.aspect});
}
context.model={providerModelId:'image_upscale',promptRequired:false};
context.values={source_request_id:'owned-result',resolution:'4k',aspect:'1:1'};
assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values)',context))),{model:'image_upscale',source_request_id:'owned-result',resolution:'4k'});
console.log('PASS: Pro and every video model retain their generation arguments; upscales retain the owned source.');
context.model={providerModelId:'omni_flash',flowMode:'i2v',contractMedia:[{key:'initial_frame'},{key:'end_frame'}]};
context.values={aspect:'9:16',duration:'10',resolution:'360p'};
context.media=[{field:'initial_frame',id:'first'},{field:'end_frame',id:'last'}];
assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values,media)',context))),
 {model:'omni_flash',mode:'i2v',aspect:'9:16',duration:10,resolution:'360p',initial_frame:'first',end_frame:'last'});
context.model={providerModelId:'veo_fast',flowMode:'r2v',contractMedia:[{key:'reference_images'}]};
context.values={aspect:'16:9',duration:'8',resolution:'720p'};
context.media=[{field:'reference_images',id:'one'},{field:'reference_images',id:'two'}];
assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values,media)',context))),
 {model:'veo_fast',mode:'r2v',aspect:'16:9',duration:8,resolution:'720p',reference_images:['one','two']});
console.log('PASS: native modes, duration, resolution and input roles reach Flow without external uploads.');
context.model={providerModelId:'video_edit',flowOperation:'edit_video',promptRequired:true};
context.values={source_request_id:'original',duration:'8',aspect:'9:16'};
assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values)',context))),
 {model:'video_edit',source_request_id:'original'});
console.log('PASS: native edits use the original job without inventing generation settings.');
context.model={providerModelId:'omni_flash',flowMode:'r2v',contractMedia:[]};
context.values={aspect:'16:9',duration:'8',resolution:'720p',voice:'Charon'};
assert.equal(JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values)',context))).voice,'Charon');
