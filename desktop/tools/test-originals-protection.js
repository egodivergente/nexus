'use strict';
const assert=require('node:assert/strict'),{summary}=require('../ui/originals-protection.js');
assert.deepEqual(summary({archived:8,pending:2,failed:3,pendingCloud:5}),{archived:8,pending:2,failed:3,pendingCloud:5,cloudVerified:3});
assert.equal(summary({archived:8,connected:true}).cloudVerified,null,'a connection is never proof that Drive has copied originals');
assert.equal(summary({archived:0,pendingCloud:0}).cloudVerified,0);
assert.equal(summary({archived:-1,pendingCloud:0}).archived,null,'invalid/missing counts are unknown rather than fabricated zero');
assert.equal(summary({archived:2,pendingCloud:5}).cloudVerified,0,'inconsistent counters cannot produce a negative completed count');
console.log('originals-protection: 5 state checks passed');
