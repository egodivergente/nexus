'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const context=vm.createContext({});
const source=fs.readFileSync(path.join(__dirname,'../ui/flow-assets.js'),'utf8');
vm.runInContext(source.split('\n').slice(0,2).join('\n'),context);
for(const model of ['nano-pro','nano2','veo_fast','veo_quality','veo_lite','omni_flash']){
 context.model={providerModelId:model,promptRequired:true};
 context.values={aspect:model.startsWith('nano')?'3:4':'9:16',source_request_id:'irrelevant',resolution:'4k'};
 const result=JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values)',context)));
 assert.deepEqual(result,{model,aspect:context.values.aspect});
}
context.model={providerModelId:'image_upscale',promptRequired:false};
context.values={source_request_id:'owned-result',resolution:'4k',aspect:'1:1'};
assert.deepEqual(JSON.parse(JSON.stringify(vm.runInContext('flowArguments(model,values)',context))),{model:'image_upscale',source_request_id:'owned-result',resolution:'4k'});
console.log('PASS: Pro and every video model retain their generation arguments; upscales retain the owned source.');
