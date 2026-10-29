'use strict';
const assert=require('node:assert/strict');
const M=require('../ui/library-model.js');
const jobs=[{id:'g',name:'Dos planos',modality:'IMAGE',providerId:'fal',status:'COMPLETED',outputs:['/media/a.png','/media/b.png'],startedAt:5},{id:'fresh',modality:'VIDEO',status:'COMPLETED',outputs:['https://example.test/new.mp4'],startedAt:9}];
const snapshot={entries:[
 {id:'asset:a',assetId:'a',sourceJobId:'g',outputIndex:0,uri:'/media/a.png',displayName:'A',modality:'IMAGE',identityKeys:['job:g:0']},
 {id:'asset:a-copy',assetId:'a-copy',uri:'/media/a.png',displayName:'Different name',modality:'IMAGE'},
 {id:'asset:b',assetId:'b',sourceJobId:'g',outputIndex:1,uri:'/media/b.png',displayName:'B',modality:'IMAGE',identityKeys:['job:g:1']},
 {id:'asset:source-none',assetId:'source-none',uri:'/media/source.png',displayName:'Material sin origen',source:null,modality:'IMAGE'},
 {id:'asset:hidden',assetId:'hidden',uri:'/media/hidden.png',hidden:true},
 {id:'asset:removed',assetId:'removed',libraryDeleted:true,uri:'/media/removed.png'}]};
const before=JSON.stringify({jobs,snapshot});const result=M.build(snapshot,jobs);assert.equal(JSON.stringify({jobs,snapshot}),before,'projection never mutates source history or index');
assert.equal(result.length,4,'dedups indexed/job results and exact local URI, includes sourceNone and fresh unindexed output');
const a=result.find(r=>r.id==='asset:a');assert(a&&a.aliases.includes('g')&&a.assetIds.includes('a-copy'));assert.equal(a.variantCount,2);assert.equal(a.variantIndex,0);
assert.equal(result.find(r=>r.id==='asset:b').variantIndex,1,'all distinct outputs remain separate, ordered variants');
assert(result.some(r=>r.sourceJobId==='fresh'),'new result appears without snapshot refresh');
assert(M.isFavorite(a,{favorites:['g']}),'legacy generation favourite applies to variants');assert(!M.isFavorite(a,{favorites:['g'],entryFavorites:{'asset:a':false}}),'per-file override does not alter legacy preference');
assert.equal(M.collection(a,{members:{g:'legacy'}}),'legacy');assert.equal(M.collection(a,{members:{g:'legacy','asset:a':'new'}}),'new','entry collection wins');
assert.equal(M.filter(result,{query:'sin origen'}).length,1);assert.equal(M.filter(result,{}, {hidden:['g']}).length,2,'legacy hidden generation keeps its variants hidden');
assert.equal(M.filter(result,{group:'job:g'}).length,2,'group exposes both real variants');
const hash='a'.repeat(64);assert.equal(M.build({entries:[{id:'x',uri:'/media/x.png',sha256:hash,hashVerified:true},{id:'y',uri:'/media/y.png',sha256:hash,hashVerified:true}]},[]).length,1,'verified equal bytes dedup');
assert.equal(M.build({entries:[{id:'x',uri:'/media/x.png',sha256:hash},{id:'y',uri:'/media/y.png',sha256:hash}]},[]).length,2,'unverified metadata hashes do not dedup');
assert.equal(M.build({entries:[{id:'x',uri:'/media/x.png',displayName:'Same',createdAt:1},{id:'y',uri:'/media/y.png',displayName:'Same',createdAt:1}]},[]).length,2,'name and timestamp never establish identity');
assert.equal(M.build({},[{id:'one',outputs:['https://example.test/a.png']},{id:'two',outputs:['https://example.test/a.png']}]).length,2,'same remote URL alone is not verified local identity');
assert.equal(M.build({},[{id:'gone',outputs:['/media/g.png']}],{deletedJobIds:['gone']}).length,0);
assert.equal(M.build({hiddenIdentityKeys:['job:g:0']},jobs).filter(r=>r.sourceJobId==='g').length,1,'explicit hidden output is not resurrected from job history');
assert.equal(M.build({entries:[{id:'legacy',sourceJobId:'local-import:a',uri:'/media/a.png'}]},[])[0].sourceJobId,'','legacy imported rows are not counted as generations');
assert(M.isFavorite(a,{entryFavorites:{'job:g:0':true}}),'favorite survives provisional job identity becoming indexed asset');
assert(!M.isFavorite(a,{entryFavorites:{'asset:a':false,'job:g:0':true}}),'canonical per-file preference takes precedence over prior alias');
const shared=M.build({},[{id:'A',modality:'IMAGE',outputs:['/media/shared.png','/media/a-only.png']},{id:'B',modality:'IMAGE',outputs:['/media/b-only.png','/media/shared.png']}]);
const sharedFile=shared.find(row=>row.uri==='/media/shared.png');assert.equal(shared.length,3);assert.equal(M.filter(shared,{group:'job:B'}).length,2,'secondary generation retains a shared file');assert.equal(sharedFile.variants['job:A'].index,0);assert.equal(sharedFile.variants['job:B'].index,1,'variant order follows the selected generation membership');
console.log('library-model: 26 behavioral checks passed');
