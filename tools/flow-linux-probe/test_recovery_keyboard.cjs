const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const html = fs.readFileSync(path.join(__dirname, 'recovery.html'), 'utf8');
const script = html.split('<script>')[1].split('</script>')[0];

function fixture() {
    const elements = {};
    const document = { getElementById: id => elements[id] };
    for (const id of ['screen', 'keyboard', 'keyboard-icon', 'token', 'status', 'access', 'check', 'close']) {
        elements[id] = { value: '', hidden: false, disabled: false, events: {},
            addEventListener(name, callback) { this.events[name] = callback; },
            querySelector() { return {}; } };
    }
    let focused = false;
    let selection;
    const input = { value: '____', focus() { focused = true; },
        setSelectionRange(start, end) { selection = [start, end]; } };
    elements.screen.contentDocument = { getElementById: id => id === 'noVNC_keyboardinput' ? input : null };
    vm.runInNewContext(script, { document, URLSearchParams,
        location: { hash: '', pathname: '/' }, history: { replaceState() {} },
        sessionStorage: { getItem() { return ''; } },
        fetch: async () => ({ ok: true }) });
    return { elements, input, focused: () => focused, selection: () => selection };
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
