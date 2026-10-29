/* Read-only library projection. File identity never depends on a name, prompt or date. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.NexusLibraryModel=api;})(typeof globalThis==='object'?globalThis:this,()=>{
 'use strict';
 const list=value=>Array.isArray(value)?value:[];
 const strings=value=>list(value).filter(item=>typeof item==='string'&&item);
 const unique=value=>[...new Set(value)];
 const text=value=>typeof value==='string'?value:'';
 const kind=value=>({OTHER:'CHAT',DOCUMENT:'CHAT',TEXT:'CHAT'}[String(value||'').toUpperCase()]||String(value||'IMAGE').toUpperCase());
 const fileUrl=output=>typeof output==='string'?output:text(output?.url);
 const local=value=>typeof value==='string'&&/^\/media\/[a-zA-Z0-9_.-]+$/.test(value);
 const origin=value=>value.source==='GENERATED'?'GENERATED':value.source==='SHARED'?'SHARED':value.parentAssetId!=null?'EDITED':'IMPORTED';
 function membership(value,known){
  const projectId=text(value.projectId)||null,source=text(value.source)||'IMPORTED';
  return {...value,projectId,source,originCategory:origin({...value,source}),role:text(value.role)||'OTHER',
   sceneId:text(value.sceneId)||null,shotId:text(value.shotId)||null,characterIds:strings(value.characterIds),tags:strings(value.tags),
   knownProject:projectId!==null&&(known===null||known.has(projectId))};
 }
 function classification(row,known){
  const supplied=Array.isArray(row.memberships)?row.memberships:null;
  const members=(supplied||[{projectId:row.projectId??null,source:row.source,role:row.role,sceneId:row.sceneId,shotId:row.shotId,
   parentAssetId:row.parentAssetId,characterIds:row.characterIds,tags:row.tags,assetId:row.assetId}]).filter(value=>value&&typeof value==='object').map(value=>membership(value,known));
  const projectIds=unique([...strings(row.projectIds),...members.map(m=>m.projectId).filter(Boolean)]);
  const knownProjectIds=known===null?projectIds:projectIds.filter(id=>known.has(id));
  return {memberships:members,projectIds,knownProjectIds,projectAssignmentExplicit:row.projectAssignmentExplicit===true||(row.projectAssignmentExplicit!==false&&!!row.assetId)};
 }
 function scopedMembers(entry,filters={}){
  const project=filters.project||filters.folder||'__all__',known=entry.knownProjectIds||entry.projectIds||[];
  if(project==='__unassigned__'&&known.length)return [];
  if(project!=='__all__'&&project!=='__unassigned__'&&!known.includes(project))return [];
  return (entry.memberships||[]).filter(member=>project==='__all__'||(project==='__unassigned__'?!member.knownProject:member.projectId===project));
 }
 function matchesClassification(entry,filters={}){
  return scopedMembers(entry,filters).some(member=>(!filters.source||member.originCategory===filters.source)&&(!filters.role||member.role===filters.role)
   &&(!filters.sceneId||member.sceneId===filters.sceneId)&&(!filters.shotId||member.shotId===filters.shotId));
 }

 function normalize(row,known=null){
  if(!row||typeof row!=='object'||row.libraryDeleted===true||row.hidden===true||!text(row.id))return null;
  const assetId=text(row.assetId),sourceJobId=text(row.sourceJobId).startsWith('local-import:')?'':text(row.sourceJobId),uri=text(row.localUri)||text(row.uri)||text(row.url);
  const identities=unique([`id:${row.id}`,...strings(row.identityKeys),...(assetId?[`asset:${assetId}`]:[]),...(local(uri)?[`local:${uri}`]:[])]);
  if(row.hashVerified===true&&/^[a-fA-F0-9]{64}$/.test(row.sha256||''))identities.push('sha256:'+row.sha256.toLowerCase());
  const aliases=unique([row.id,...strings(row.aliases),...(assetId?[assetId,'asset:'+assetId,'local-import:'+assetId]:[]),...(sourceJobId?[sourceJobId]:[])]);
  return {...row,...classification(row,known),id:row.id,assetId,sourceJobId,uri,localUri:local(uri)?uri:'',displayName:text(row.displayName)||text(row.name)||'Archivo',
   modality:kind(row.modality||row.type),createdAt:Number(row.createdAt??row.startedAt)||0,status:row.status||'COMPLETED',
   identityKeys:identities,aliases,sourceReferences:[...list(row.sourceReferences).filter(ref=>ref&&typeof ref.sourceJobId==='string'&&Number.isInteger(ref.outputIndex)&&ref.outputIndex>=0),...(sourceJobId&&Number.isInteger(row.outputIndex)?[{sourceJobId,outputIndex:row.outputIndex}]:[])],sourceJobIds:unique([sourceJobId,...strings(row.sourceJobIds)].filter(Boolean)),assetIds:unique([assetId,...strings(row.assetIds)].filter(Boolean))};
 }
 function build(snapshot,jobs,options={}){
  const deleted=new Set(strings(options.deletedJobIds)),hiddenKeys=new Set(strings(snapshot?.hiddenIdentityKeys)),rows=[],known=Array.isArray(snapshot?.projects)?new Set(snapshot.projects.map(p=>p.id)):null;
  for(const row of list(snapshot?.entries)){const normalized=normalize(row,known);if(normalized&&!normalized.identityKeys.some(key=>hiddenKeys.has(key)))rows.push(normalized);}
  // A just-completed provider result is visible before the shared index catches
  // up. These objects are a projection; jobs and their output arrays stay intact.
  for(const job of list(jobs)){
   if(!job||typeof job.id!=='string'||deleted.has(job.id))continue;
   const imported=job.localImport===true&&job.id.startsWith('local-import:'),assetId=imported?job.id.slice('local-import:'.length):'';
   list(job.outputs).forEach((output,index)=>{
    const uri=fileUrl(output);if(!uri)return;
    const row=normalize({id:`job:${job.id}:${index}`,assetId,sourceJobId:imported?'':job.id,outputIndex:index,uri,
     displayName:job.name||job.modelId||'Resultado',modality:job.modality,createdAt:job.startedAt,status:job.status,
     providerId:job.providerId,modelId:job.modelId,prompt:job.prompt,parameters:job.parameters||{},projectId:job.parameters?.__project_id??job.projectId??null,role:job.role||(imported?'REFERENCE':'OUTPUT'),
     source:imported?'IMPORTED':'GENERATED',projectAssignmentExplicit:false,identityKeys:[`job:${job.id}:${index}`],aliases:[job.id]},known);
    if(row&&!row.identityKeys.some(key=>hiddenKeys.has(key)))rows.push(row);
   });
  }
  const identities=new Map(),groups=new Set();
  function merge(target,source){
   // Prefer the indexed asset's durable identity while retaining all provenance
   // and legacy preference keys of identical files from other devices/jobs.
   const preferred=!target.assetId&&source.assetId?source:target,other=preferred===source?target:source;
   const merged={...other,...preferred,createdAt:Math.max(target.createdAt,source.createdAt)};
   for(const key of ['identityKeys','aliases','sourceJobIds','assetIds'])merged[key]=unique([...target[key],...source[key]]);
   const classified=target.projectAssignmentExplicit&&!source.projectAssignmentExplicit?[target]:source.projectAssignmentExplicit&&!target.projectAssignmentExplicit?[source]:[target,source];
   merged.memberships=[...new Map(classified.flatMap(row=>row.memberships).map(m=>[JSON.stringify(m),m])).values()];
   merged.projectIds=unique(classified.flatMap(row=>row.projectIds));merged.knownProjectIds=unique(classified.flatMap(row=>row.knownProjectIds));
   merged.projectAssignmentExplicit=classified.some(row=>row.projectAssignmentExplicit);merged.projectId=merged.projectIds[0]||null;
   merged.sourceReferences=[...new Map([...target.sourceReferences,...source.sourceReferences].map(ref=>[ref.sourceJobId+':'+ref.outputIndex,ref])).values()];
   merged.aliases=unique([merged.id,...merged.aliases]);
   if(!merged.sourceJobId)merged.sourceJobId=merged.sourceJobIds[0]||'';
   if(!merged.localUri){merged.localUri=target.localUri||source.localUri;if(merged.localUri)merged.uri=merged.localUri;}
   return merged;
  }
  for(const row of rows){
   const matches=unique(row.identityKeys.map(key=>identities.get(key)).filter(Boolean));let combined=row;
   for(const existing of matches){combined=merge(existing,combined);groups.delete(existing);}
   groups.add(combined);for(const key of combined.identityKeys)identities.set(key,combined);
  }
  const entries=[...groups];
  for(const entry of entries){entry.groupIds=entry.sourceJobIds.length?entry.sourceJobIds.map(id=>'job:'+id):entry.generationId?['generation:'+entry.generationId]:[];entry.groupId=entry.sourceJobId?'job:'+entry.sourceJobId:entry.groupIds[0]||'';entry.variants={};}
  const variantGroups=new Map();
  for(const entry of entries)for(const group of entry.groupIds){if(!variantGroups.has(group))variantGroups.set(group,[]);variantGroups.get(group).push(entry);}
  for(const [group,members] of variantGroups){
   const ordinal=entry=>entry.sourceReferences.find(ref=>'job:'+ref.sourceJobId===group)?.outputIndex??entry.outputIndex??0;
   members.sort((a,b)=>ordinal(a)-ordinal(b)||a.id.localeCompare(b.id));
   members.forEach((entry,index)=>{entry.variants[group]={index,count:members.length};});
  }
  for(const entry of entries){entry.variantIndex=entry.variants[entry.groupId]?.index||0;entry.variantCount=entry.variants[entry.groupId]?.count||1;}
  return entries;
 }
 const inGroup=(entry,group)=>entry.groupIds?.includes(group)||entry.groupId===group;
 const matches=(entry,values)=>{const keys=new Set(strings(values));return entry.aliases.some(key=>keys.has(key));};
 const isFavorite=(entry,prefs={})=>{for(const key of entry.aliases){if(Object.hasOwn(prefs.entryFavorites||{},key))return !!prefs.entryFavorites[key];}return matches(entry,prefs.favorites);};
 const isHidden=(entry,prefs={})=>matches(entry,prefs.hidden);
 const collection=(entry,prefs={})=>{const members=prefs.members||{};for(const key of entry.aliases){if(Object.hasOwn(members,key))return members[key];}return '';};
 function filter(entries,filters={},prefs={}){
  const query=text(filters.query).trim().toLocaleLowerCase();
  return list(entries).filter(entry=>!isHidden(entry,prefs)&&matchesClassification(entry,filters)&&(!filters.kind||entry.modality===filters.kind)&&(!filters.provider||entry.providerId===filters.provider)
   &&(!filters.status||entry.status===filters.status)&&(!filters.favorites||isFavorite(entry,prefs))&&(!filters.collection||collection(entry,prefs)===filters.collection)
   &&(!filters.group||inGroup(entry,filters.group))&&(!query||[entry.displayName,entry.prompt,entry.modelId,entry.source,entry.projectName].join(' ').toLocaleLowerCase().includes(query)));
 }
 function folders(entries,snapshot={},prefs={}){
  const visible=list(entries).filter(entry=>!isHidden(entry,prefs));
  const rows=Array.isArray(snapshot.folders)?snapshot.folders:list(snapshot.projects).map(project=>({...project,projectId:project.id,kind:'project',parentId:null}));
  return rows.filter(row=>row&&typeof(row.projectId||row.id)==='string').map(row=>{const id=row.projectId||row.id;return {...row,id,projectId:id,count:visible.filter(entry=>entry.knownProjectIds.includes(id)).length};})
   .filter(row=>!row.archived||row.count>0).sort((a,b)=>text(a.name).localeCompare(text(b.name),'es'));
 }
 return {build,filter,isFavorite,isHidden,collection,matches,inGroup,origin,scopedMembers,matchesClassification,folders};
});
