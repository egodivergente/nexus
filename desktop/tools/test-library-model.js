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


// Real file assignment wins over the old generation's project. A shared file
// keeps each classification together, so scope A cannot borrow B's role/scene.
const projects=[{id:'A',name:'Primero'},{id:'B',name:'Segundo'},{id:'empty',name:'Vacío'},{id:'archived',name:'Archivado',archived:true},{id:'archived-empty',name:'Archivo vacío',archived:true}];
const classifiedSnapshot={projects,entries:[
 {id:'asset:shared-a',assetId:'shared-a',uri:'/media/together.png',sourceJobId:'old',outputIndex:0,identityKeys:['job:old:0'],projectId:'A',source:'GENERATED',role:'OUTPUT',sceneId:'scene-a',shotId:'shot-a'},
 {id:'asset:shared-b',assetId:'shared-b',uri:'/media/together.png',projectId:'B',source:'SHARED',role:'REFERENCE',sceneId:'scene-b',shotId:'shot-b'},
 {id:'asset:unassigned',assetId:'unassigned',uri:'/media/unassigned.pdf',sourceJobId:'old',outputIndex:1,identityKeys:['job:old:1'],projectId:null,type:'OTHER'},
 {id:'asset:unknown',assetId:'unknown',uri:'/media/unknown.png',projectId:'deleted-project',source:'IMPORTED',parentAssetId:'parent'},
 {id:'asset:archived',assetId:'archived',uri:'/media/archive.mp4',projectId:'archived',type:'VIDEO',source:'GENERATED'}]};
const oldJobs=[{id:'old',parameters:{__project_id:'A'},modality:'IMAGE',outputs:['/media/together.png','/media/unassigned.pdf']}];
const pristine=JSON.stringify({classifiedSnapshot,oldJobs}),files=M.build(classifiedSnapshot,oldJobs),together=files.find(row=>row.uri==='/media/together.png');
assert.equal(files.length,4,'same physical file is one card across multiple projects');
assert.deepEqual(new Set(together.projectIds),new Set(['A','B']),'dedup preserves all actual memberships');
assert.equal(M.filter(files,{project:'A'}).length,1);assert.equal(M.filter(files,{project:'B'}).length,1);
assert.equal(M.filter(files,{project:'A',source:'GENERATED',role:'OUTPUT',sceneId:'scene-a',shotId:'shot-a'}).length,1);
assert.equal(M.filter(files,{project:'B',source:'SHARED',role:'REFERENCE',sceneId:'scene-b'}).length,1);
assert.equal(M.filter(files,{project:'A',role:'REFERENCE',sceneId:'scene-b'}).length,0,'classification must match a single membership');
assert.equal(M.filter(files,{project:'__unassigned__'}).length,2,'explicit null and dangling project are unassigned as Android');
const unassigned=files.find(row=>row.assetId==='unassigned');assert.deepEqual(unassigned.projectIds,[],'legacy generation project cannot override explicit null');
assert.equal(M.filter(files,{project:'A',query:'unassigned'}).length,0);
assert.equal(M.filter(files,{source:'IMPORTED'}).length,1,'missing source defaults to imported, including documents');
assert.equal(M.filter(files,{source:'EDITED'}).length,1);
assert.deepEqual(files.find(row=>row.assetId==='unknown').projectIds,['deleted-project'],'unknown project metadata is preserved');
assert.equal(M.origin({source:'GENERATED',parentAssetId:'parent'}),'GENERATED');
assert.equal(M.origin({source:'SHARED',parentAssetId:'parent'}),'SHARED');
assert.equal(M.origin({source:'IMPORTED',parentAssetId:''}),'EDITED','Android tests parent non-null, even empty');
assert.equal(M.origin({}),'IMPORTED');
const folders=M.folders(files,classifiedSnapshot);assert.equal(folders.find(row=>row.id==='A').count,1);assert.equal(folders.find(row=>row.id==='B').count,1);
assert.equal(folders.find(row=>row.id==='empty').count,0,'real empty active folders stay discoverable');
assert.equal(folders.find(row=>row.id==='archived').count,1,'archived folder with files remains visible');
assert(!folders.some(row=>row.id==='archived-empty'),'empty archived project stays out of folder navigation');
assert.equal(M.folders(files,classifiedSnapshot,{hidden:[together.id]}).find(row=>row.id==='A').count,0,'folder counts exclude hidden files');
assert.equal(JSON.stringify({classifiedSnapshot,oldJobs}),pristine,'classification never rewrites Android metadata or history');
const legacyImport={id:'local-import:moved',localImport:true,modality:'IMAGE',projectId:'A',outputs:['/media/moved.png']};
const moved=M.build({projects,entries:[{id:'asset:moved',assetId:'moved',uri:'/media/moved.png',projectId:'B',source:'IMPORTED'}]},[legacyImport]);
assert.deepEqual(moved[0].projectIds,['B'],'indexed APK move wins over a legacy local-import asset alias');
assert.equal(M.filter(moved,{project:'A'}).length,0,'legacy import cannot resurrect the former project');
assert.equal(M.filter(M.build({projects},[legacyImport]),{project:'A'}).length,1,'standalone legacy import retains its fallback project');
console.log('library-model: 53 behavioral checks passed');
