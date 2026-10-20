import { test } from 'node:test';
import assert from 'node:assert/strict';
import { encode, decode } from './nexus-continuity-termux.mjs';
import crypto from 'node:crypto';

test('AGY adapter shares authenticated multi-frame protocol', () => {
    const key = crypto.randomBytes(32), bytes = crypto.randomBytes(180_123);
    const encrypted = encode(bytes, key, 'request-context');
    assert.deepEqual(decode(encrypted, key, 'request-context'), bytes);
    assert.throws(() => decode(encrypted, crypto.randomBytes(32), 'request-context'));
    assert.throws(() => decode(encrypted, key, 'wrong-context'));
    assert.throws(() => decode(encrypted.subarray(0, -1), key, 'request-context'));
    const tampered = Buffer.from(encrypted); tampered[90] ^= 1;
    assert.throws(() => decode(tampered, key, 'request-context'));
});

test('empty payload still requires the authenticated terminal frame', () => {
    const key = crypto.randomBytes(32), encrypted = encode(Buffer.alloc(0), key, 'empty');
    assert.equal(decode(encrypted, key, 'empty').length, 0);
    assert.throws(() => decode(encrypted.subarray(0, 16), key, 'empty'));
});
