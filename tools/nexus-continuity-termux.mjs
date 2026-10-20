/** Private AGY auth adapter, inside the user's existing Debian/Termux installation.
 * Built-in Node APIs only. Bind loopback; use the same random pairing key as Nexus.
 * No token values or encrypted packets are logged. Never replaces CLI conversations/settings.
 */
import fs from 'node:fs';
import http from 'node:http';
import crypto from 'node:crypto';
import path from 'node:path';

const home = process.env.HOME || '/root';
const config = path.join(home, '.nexus-continuity');
const port = Number(process.env.NEXUS_CONTINUITY_PORT || 8796);
const key = () => Buffer.from(fs.readFileSync(path.join(config, 'pairing.key'), 'utf8').trim(), 'base64url');
const authRoot = path.join(home, '.gemini/antigravity-cli');
const names = ['antigravity-oauth-token', 'installation_id'];

export function encode(bytes, secret, context) {
    const stream = crypto.randomBytes(16), parts = [stream];
    let sequence = 0;
    const frame = data => {
        const iv = crypto.randomBytes(12), counter = Buffer.alloc(8); counter.writeBigInt64BE(BigInt(sequence++));
        const cipher = crypto.createCipheriv('aes-256-gcm', secret, iv);
        cipher.setAAD(Buffer.concat([Buffer.from(context), stream, counter]));
        const encrypted = Buffer.concat([cipher.update(data), cipher.final(), cipher.getAuthTag()]);
        const length = Buffer.alloc(4); length.writeInt32BE(encrypted.length);
        parts.push(length, iv, encrypted);
    };
    for (let offset = 0; offset < bytes.length; offset += 65536) frame(bytes.subarray(offset, offset + 65536));
    frame(Buffer.alloc(0));
    return Buffer.concat(parts);
}

export function decode(bytes, secret, context) {
    if (bytes.length < 48) throw Error('Truncated stream');
    const stream = bytes.subarray(0, 16), parts = [];
    let offset = 16, sequence = 0;
    while (offset < bytes.length) {
        if (offset + 16 > bytes.length) throw Error('Truncated frame');
        const length = bytes.readInt32BE(offset); offset += 4;
        if (length < 16 || length > 65552 || offset + 12 + length > bytes.length) throw Error('Invalid frame');
        const iv = bytes.subarray(offset, offset + 12); offset += 12;
        const encrypted = bytes.subarray(offset, offset + length); offset += length;
        const counter = Buffer.alloc(8); counter.writeBigInt64BE(BigInt(sequence++));
        const decipher = crypto.createDecipheriv('aes-256-gcm', secret, iv);
        decipher.setAAD(Buffer.concat([Buffer.from(context), stream, counter]));
        decipher.setAuthTag(encrypted.subarray(-16));
        const plain = Buffer.concat([decipher.update(encrypted.subarray(0, -16)), decipher.final()]);
        if (plain.length === 0) {
            if (offset !== bytes.length) throw Error('Trailing bytes');
            return Buffer.concat(parts);
        }
        parts.push(plain);
    }
    throw Error('Missing terminal frame');
}

function readAuth() {
    const result = {};
    for (const name of names) {
        const file = path.join(authRoot, name);
        if (fs.existsSync(file)) result[name] = fs.readFileSync(file, 'utf8');
    }
    return result;
}

function writeAuth(data, removeMissing = false) {
    if (!data || Object.keys(data).some(name => !names.includes(name)) || Object.values(data).some(value => typeof value !== 'string')) throw Error('Invalid auth');
    if (data['antigravity-oauth-token']) JSON.parse(data['antigravity-oauth-token']);
    fs.mkdirSync(authRoot, { recursive: true, mode: 0o700 });
    // Keep the previous encrypted revision for recovery. Installation_id is not an API token.
    fs.mkdirSync(config, { recursive: true, mode: 0o700 });
    const previous = Buffer.from(JSON.stringify(readAuth()));
    const hash = crypto.createHash('sha256').update(previous).digest('hex');
    const backup = path.join(config, `auth-${hash}.enc`);
    if (!fs.existsSync(backup)) fs.writeFileSync(backup, encode(previous, key(), 'nexus-agy-backup-v1'), { mode: 0o600 });
    for (const name of names) {
        if (!(name in data)) { if (removeMissing && fs.existsSync(path.join(authRoot,name))) fs.unlinkSync(path.join(authRoot,name)); continue; }
        const target = path.join(authRoot, name), tmp = target + '.nexus-sync';
        fs.writeFileSync(tmp, data[name], { mode: 0o600 }); fs.renameSync(tmp, target);
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(new URL(import.meta.url).pathname)) {
    http.createServer(async (req, res) => {
        try {
            const secret = key();
            const token = req.headers['x-nexus-sync'] || '';
            const authorized = decode(Buffer.from(token, 'base64url'), secret, 'nexus-sync-request-v1').toString();
            if (!['/auth', '/pair', '/options'].includes(req.url) || authorized !== `nexus-sync-v1:${req.url}`) throw Error('Unauthorized');
            let data;
            if (req.method === 'POST') {
                const chunks = []; for await (const chunk of req) chunks.push(chunk);
                const body = JSON.parse(decode(Buffer.concat(chunks), secret, token).toString());
                if (req.url === '/pair') {
                    if (typeof body.key !== 'string' || Buffer.from(body.key, 'base64url').length !== 32) throw Error('Invalid pairing');
                    const next = Buffer.from(body.key, 'base64url');
                    for (const file of fs.readdirSync(config).filter(file => /^auth-[a-f0-9]{64}\.enc$/.test(file))) {
                        const target = path.join(config, file);
                        const revision = decode(fs.readFileSync(target), secret, 'nexus-agy-backup-v1');
                        fs.writeFileSync(target + '.new', encode(revision, next, 'nexus-agy-backup-v1'), { mode: 0o600 });
                        fs.renameSync(target + '.new', target);
                    }
                    fs.writeFileSync(path.join(config, 'pairing.key'), body.key, { mode: 0o600 });
                    data = { ok: true };
                } else if (req.url === '/options') {
                    if (typeof body.enabled !== 'boolean' || typeof body.afterBoot !== 'boolean') throw Error('Invalid options');
                    fs.writeFileSync(path.join(config, 'options.json'), JSON.stringify(body), { mode: 0o600 });
                    data = { ok: true };
                } else { writeAuth(body.files || body, body.removeMissing === true); data = readAuth(); }
            } else if (req.method === 'GET' && req.url === '/auth') data = readAuth();
            else throw Error('Unknown method');
            const encoded = encode(Buffer.from(JSON.stringify(data)), secret, token);
            res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': encoded.length, 'Cache-Control': 'no-store', 'Connection': 'close' });
            res.end(encoded);
        } catch {
            res.writeHead(403, { 'Connection': 'close' }); res.end();
        }
    }).listen(port, '127.0.0.1');
}
