/* Verify immutable origins without invoking providers or an agent. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const calls=[];
const context={projectId:'film',shotId:'hand',revision:3,context:'La mano tiembla',referenceAssetIds:[],fingerprint:'revision-3'};
const sandbox={structuredClone,setInterval(){},window:{},state:{jobs:[],draft:{prompt:'Otro borrador',values:{duration:'5'},media:[]}},
 Agent:{busy:false,snapshot:{index:{shots:[{id:'hand',description:'Primerísimo plano de su mano'}],assets:[]}}},
 loaded:null,quote:null,view:'story',persist(){},toast(){},
 dispatch:async(plan,batch,meta)=>{calls.push({plan,meta});return meta;},openModel:async()=>{},renderCanvas(){},
 E:{adapt:(source,model)=>({model,values:{},media:[],prompt:source.prompt})}};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../ui/story-generation.js'),'utf8'),sandbox);
(async()=>{
 await sandbox.window.NexusStoryGeneration.prepare(context);
 context.revision=99;
 assert.equal(sandbox.state.draft.storyContext.revision,3);
 assert.equal(sandbox.state.draft.prompt,'Primerísimo plano de su mano');
 const comparison=sandbox.E.adapt(sandbox.state.draft,{id:'other-model'});
 assert.equal(comparison.storyContext.shotId,'hand');
 await sandbox.dispatch({...comparison,media:[{url:'/media/reference.png'}]},null,{});
 assert.equal(calls[0].meta.projectId,'film');
 assert.equal(calls[0].meta.storyContext.revision,3);
 assert.equal(calls[0].meta.referenceUris[0],'/media/reference.png');
 assert.equal(calls[0].plan.values.__story_context,undefined);
 await sandbox.dispatch({model:{},values:{},media:[]},null,{});
 assert.equal(calls[1].meta.storyContext,null);
 assert.equal(calls[1].meta.projectId,undefined);
 await assert.rejects(()=>sandbox.window.NexusStoryGeneration.prepare({projectId:'film'}),/contexto verificable/);
 console.log('PASS: immutable origin, comparison, actual inputs, isolated payload, no last-screen attribution');
})().catch(error=>{console.error(error);process.exitCode=1;});
