const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const html = fs.readFileSync(path.join(__dirname, 'recovery.html'), 'utf8');
const script = html.split('<script>')[1].split('</script>')[0];

function fixture(states = ['connected']) {
    const elements = {};
    const document = { getElementById: id => elements[id] };
    const submit = {};
    for (const id of ['screen', 'keyboard', 'keyboard-icon', 'token', 'status', 'access', 'check', 'close', 'connected', 'open']) {
        elements[id] = { value: '', hidden: ['screen','keyboard','check','close','connected'].includes(id), disabled: false, events: {},
            addEventListener(name, callback) { this.events[name] = callback; },
            querySelector() { return submit; } };
    }
    let focused = false;
    let selection;
    const input = { value: '____', focus() { focused = true; },
        setSelectionRange(start, end) { selection = [start, end]; } };
    elements.screen.contentDocument = { getElementById: id => id === 'noVNC_keyboardinput' ? input : null };
    const requests = [], timers = new Map();let timerId=0;
    vm.runInNewContext(script, { document, URLSearchParams, AbortController,
        setTimeout(callback, delay) { timers.set(++timerId,{callback,delay});return timerId; },
        clearTimeout(id) { timers.delete(id); },
        location: { hash: '', pathname: '/' }, history: { replaceState() {} },
        sessionStorage: { getItem() { return ''; },setItem() {} },
        fetch: async (url, options) => {
            requests.push({url,options});
            const value = url==='/status' ? states.shift() : null;
            if(value instanceof Error)throw value;
            return {ok: true, json: async () => url==='/unlock'?{vnc_password:'testpass'}:{status:value}};
        } });
    return { elements, input, requests, timers, focused: () => focused, selection: () => selection };
}

test('keyboard tap focuses noVNC synchronously without reading or forwarding a password', () => {
    const f = fixture();
    const result = f.elements.keyboard.onclick();
    assert.equal(result, undefined);
    assert.equal(f.focused(), true);
    assert.deepEqual(f.selection(), [4, 4]);
    assert.equal(f.input.value, '____');
});

test('keyboard stays disabled until the iframe input exists', () => {
    const f = fixture();
    f.elements.screen.events.load();
    assert.equal(f.elements.keyboard.disabled, false);
    f.elements.screen.contentDocument = { getElementById() { return null; } };
    f.elements.screen.events.load();
    assert.equal(f.elements.keyboard.disabled, true);
    f.elements.keyboard.onclick();
    assert.equal(f.focused(), false);
    assert.match(f.elements.status.textContent, /Espera/);
});

test('closing remote access hides and disables the keyboard control', async () => {
    const f = fixture();
    await f.elements.close.onclick();
    assert.equal(f.elements.keyboard.hidden, true);
    assert.equal(f.elements.keyboard.disabled, true);
    assert.equal(f.elements.screen.src, 'about:blank');
});

test('a valid Google session skips the remote login and revokes remote access', async () => {
    const f = fixture();
    f.elements.token.value='test-token';
    await f.elements.access.onsubmit();
    assert.equal(f.elements.screen.hidden,true);
    assert.equal(f.elements.connected.hidden,false);
    assert.equal(f.elements.access.hidden,true);
    assert.deepEqual(f.requests.map(r=>r.url),['/unlock','/status','/lock']);
    assert.equal(f.requests[1].options.headers.Authorization,'Bearer test-token');
    assert.equal(f.timers.size,0);
});

test('manual login is monitored and closes only the remote view after success', async () => {
    const f = fixture(['auth_required','connected']);
    await f.elements.access.onsubmit();
    assert.equal(f.elements.screen.hidden,false);
    assert.equal(f.elements.connected.hidden,true);
    const pending=[...f.timers.values()][0];
    assert.equal(pending.delay,6000);
    await pending.callback();
    assert.equal(f.elements.screen.hidden,true);
    assert.equal(f.elements.connected.hidden,false);
    assert.equal(f.requests.at(-1).url,'/lock');
});

test('explicitly opening the browser keeps the connected workspace visible', async () => {
    const f = fixture();
    await f.elements.open.onclick();
    assert.equal(f.elements.screen.hidden,false);
    assert.equal(f.elements.connected.hidden,true);
    assert.equal(f.requests.some(r=>r.url==='/lock'),false);
});

test('unverified is never shown as connected and close stops monitoring', async () => {
    const f = fixture(['unverified']);
    await f.elements.access.onsubmit();
    assert.equal(f.elements.connected.hidden,true);
    assert.equal(f.timers.size,1);
    await f.elements.close.onclick();
    assert.equal(f.timers.size,0);
    assert.equal(f.elements.screen.hidden,true);
});

test('network failures stop automatic retries after three attempts', async () => {
    const f=fixture([new Error('offline'),new Error('offline'),new Error('offline')]);
    await f.elements.access.onsubmit();
    for(let i=0;i<2;i++){
        const [id,pending]=[...f.timers.entries()][0];f.timers.delete(id);
        await pending.callback();
    }
    assert.equal(f.timers.size,0);
    assert.equal(f.elements.access.hidden,false);
    assert.equal(f.elements.connected.hidden,true);
});

test('a later expired Google session does not reopen a revoked remote viewer', async () => {
    const f=fixture(['connected','auth_required']);
    await f.elements.access.onsubmit();
    await f.elements.check.onclick();
    assert.equal(f.elements.connected.hidden,true);
    assert.equal(f.elements.access.hidden,false);
    assert.equal(f.elements.screen.hidden,true);
    assert.equal(f.timers.size,0);
});
