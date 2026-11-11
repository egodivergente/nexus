'use strict';
const assert=require('node:assert/strict');
const E=require('../ui/engine.js');
const fixtures=require('./fixtures/atlas-media-inputs.json').models;
const model=id=>{const f=fixtures.find(f=>f.model.id===id);return E.normalize(f.model,f.schema);};
const media=(field,n=0,mime='image/png')=>({id:field+n,field,mime,remoteUrl:'https://example.invalid/'+field+n});
let passed=0;
function test(name,fn){fn();console.log('PASS '+name);passed++;}
const kling=model('kwaivgi/kling-v4.0/image-to-video');
test('Kling real schema exposes ordered intermediate frames with its eight-image limit',()=>{
 assert.deepEqual(kling.fields.map(f=>f.key),['image','middle_images','last_image']);
 assert.deepEqual(kling.fields.find(f=>f.key==='middle_images'),{key:'middle_images',label:'Fotogramas intermedios',mime:'image/*',required:false,maximum:8,encoding:'array',minItems:0});
 assert(!kling.parameters.some(p=>p.key==='middle_images'));
});
test('Kling first, intermediate and last frame payload retains timeline order',()=>{
 const images=[media('image'),media('middle_images',2),media('middle_images',1),media('last_image')];
 const p=E.payload(kling,'An action',{},images);
 assert.equal(p.image,images[0].remoteUrl);assert.deepEqual(p.middle_images,images.slice(1,3).map(f=>f.remoteUrl));assert.equal(p.last_image,images[3].remoteUrl);
 assert.match(E.validate(kling,'',{},[media('image'),...Array.from({length:9},(_,n)=>media('middle_images',n))]),/máximo 8/);
});
const vidu=model('vidu/q4-preview/reference-to-video');
test('Vidu reference audio uses audio attachments and the published three-item limit',()=>{
 const f=vidu.fields.find(f=>f.key==='reference_audios');assert.equal(f.mime,'audio/*');assert.equal(f.maximum,3);assert.equal(f.encoding,'array');
 const image=media('reference_images'),audio=media('reference_audios',0,'audio/mpeg');
 assert.deepEqual(E.payload(vidu,'An action',{},[image,audio]).reference_audios,[audio.remoteUrl]);
 assert.match(E.validate(vidu,'An action',{},[image,...Array.from({length:4},(_,n)=>media('reference_audios',n,'audio/mpeg'))]),/máximo 3/);
 assert.match(E.validate(vidu,'An action',{},[image,media('reference_audios')]),/formato/);
});
const heygen=model('heygen/avatar-5/digital-twin');
test('HeyGen background image is uploaded as the exact schema string field',()=>{
 assert.equal(heygen.fields.find(f=>f.key==='background_image').encoding,'string');
 assert(!heygen.parameters.some(p=>p.key==='background_image'));
 const file=media('background_image');assert.equal(E.payload(heygen,'Narration',{},[file]).background_image,file.remoteUrl);
});
test('HeyGen real required alternatives reject an empty draft before quote or submission',()=>{
 assert.match(E.validate(heygen,'   ',{},[]),/prompt.*audio/);
 assert.throws(()=>E.payload(heygen,'',{},[],true),/prompt.*audio/);
 assert.throws(()=>E.payload(heygen,'',{},[]),/prompt.*audio/);
});
test('HeyGen accepts narration or exact audio key without serializing schema metadata',()=>{
 const audio=media('audio',0,'audio/mpeg');
 assert.equal(E.validate(heygen,'Narration',{},[]),null);
 assert.equal(E.validate(heygen,'',{},[audio]),null);
 assert.equal(E.payload(heygen,'',{},[audio]).audio,audio.remoteUrl);
 assert(!('requiredAlternatives' in E.payload(heygen,'Narration',{},[])));
 assert(!('requiredAlternatives' in E.payload(heygen,'',{},[audio],true)));
 assert.match(E.validate(heygen,'',{},[media('reference_audios',0,'audio/mpeg')]),/prompt.*audio/);
});
test('Optional attachments remain optional and quote payload removes private media URLs',()=>{
 assert.equal(E.validate(vidu,'An action',{},[media('reference_images')]),null);
 const p=E.payload(vidu,'Private prompt',{},[media('reference_images'),media('reference_audios',0,'audio/mpeg')],true);
 assert.deepEqual(p.reference_audios,['']);assert(!JSON.stringify(p).includes('example.invalid'));assert(!('prompt' in p));
});
test('Attachments are never introduced when the provider schema does not declare them',()=>{
 const m=E.normalize(fixtures[0].model,{components:{schemas:{Input:{properties:{model:{type:'string'}}}}}});
 assert.deepEqual(m.fields,[]);assert.match(E.validate(m,'',{},[media('middle_images')]),/no corresponden/);
});
console.log(JSON.stringify({passed,failed:0,networkCalls:0}));
