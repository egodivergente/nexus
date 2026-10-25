"""Real-browser check of chat-continuity.js/css in isolation: folding, actions, context menu and panel.

Loads only the UI files with stubbed globals and an in-memory rpc; no host, network or paid calls.
"""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

UI = Path(__file__).resolve().parents[1] / 'ui'
CSS = ''.join((UI / name).read_text('utf-8') for name in ['style.css', 'assistant.css', 'desktop-design.css', 'chat-continuity.css'])
LONG = '\n'.join(f'Línea {i} del prompt largo' for i in range(30))
STUBS = """
const $=s=>document.querySelector(s),escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let view='assistant';const toasts=[],calls=[];function toast(m){toasts.push(m);}function persist(){}function navigate(v){view=v;}
const state={agentConversation:{id:'c1',items:[]}};
const Agent={items:[],draft:'',attachments:[],busy:false,generating:false,cardAnchors:new Map(),expandedBatches:new Set()};
const chats=[{id:'c1',title:'Vídeo del gato',messages:2,preview:'gato',updatedAt:Date.now(),customTitle:null},{id:'c2',title:'Logo',messages:4,preview:'logo',updatedAt:1,customTitle:'Logo'}];
async function rpc(op,args){calls.push([op,args]);if(op==='listChats')return chats.filter(c=>!args.query||(c.title+c.preview).toLowerCase().includes(args.query.toLowerCase()));
 if(op==='renameChat'){const c=chats.find(c=>c.id===args.id);c.customTitle=args.title.trim()||null;c.title=c.customTitle||'Automático';return {id:c.id,title:c.title,customTitle:c.customTitle};}}
async function agentCall(){return {};}
function renderAssistant(){
 const box=$('#content');box.classList.add('agent-view');
 box.innerHTML='<div class="agent-toolbar"><button data-parity="conversations">Conversaciones</button></div><div class="agent-messages">'+Agent.items.map(i=>NexusChatContinuity.message(i)).join('')+'</div>'+NexusChatContinuity.panelPlaceholder();
 NexusChatContinuity.afterRender();
}
"""

def main():
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='msedge', headless=True)
        page = browser.new_page(viewport={'width': 1400, 'height': 900})
        page.on('pageerror', lambda e: errors.append(str(e)))
        page.route('**/*', lambda route: route.abort() if route.request.url.startswith('http') else route.continue_())
        page.set_content(f'<!doctype html><html lang="es"><head><style>{CSS}</style></head><body class="nx-desktop"><main><div id="content"></div></main><div id="toast"></div><dialog id="dialog"></dialog></body></html>')
        page.add_script_tag(content=STUBS + (UI / 'chat-continuity.js').read_text('utf-8') + '\nwindow.__ok=!!window.NexusChatContinuity;')
        assert page.evaluate('window.__ok'), 'module did not initialise'
        items = [{'id': 'u1', 'role': 'user', 'text': LONG}, {'id': 'a1', 'role': 'assistant', 'text': LONG, 'html': '<p>' + LONG.replace('\n', '<br>') + '</p><p><a href="#x" id="link">enlace</a> <code id="code">npm i</code></p>'},
                 {'id': 'u2', 'role': 'user', 'text': 'Corto'}]
        page.evaluate('items=>{Agent.items=items;renderAssistant();}', items)
        page.wait_for_timeout(50)
        fold = page.locator('[data-cc-fold="u1"]')
        assert fold.is_visible() and fold.get_attribute('aria-expanded') == 'false', 'long user prompt must offer Expandir'
        body = page.locator('#cc-text-u1')
        clamped = body.evaluate('n=>n.clientHeight')
        line = body.evaluate('n=>parseFloat(getComputedStyle(n).lineHeight)')
        assert abs(clamped - 8 * line) < 2, (clamped, line)
        assert not page.locator('[data-cc-fold="u2"]').is_visible(), 'short prompt shows no toggle'
        assert page.locator('[data-agent-message="a1"] .cc-fold-toggle').count() == 0, 'assistant answers never fold'
        assert page.locator('[data-agent-message="a1"] .agent-text').evaluate('n=>n.scrollHeight<=n.clientHeight+1')
        fold.click()
        assert fold.get_attribute('aria-expanded') == 'true' and fold.inner_text() == 'Contraer'
        assert body.evaluate('n=>n.scrollHeight<=n.clientHeight+1'), 'expanded shows the whole prompt'
        page.evaluate('renderAssistant()')
        assert page.locator('[data-cc-fold="u1"]').get_attribute('aria-expanded') == 'true', 'expansion survives re-render'
        page.locator('[data-cc-fold="u1"]').click()
        assert page.locator('[data-cc-fold="u1"]').get_attribute('aria-expanded') == 'false'

        page.evaluate("Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async t=>{window.__copied=t;}}})")
        page.locator('[data-agent-message="u2"]').hover()
        page.locator('[data-cc-copy="u2"]').click()
        assert page.evaluate('window.__copied') == 'Corto'
        page.locator('[data-cc-select="u2"]').click()
        assert page.evaluate('getSelection().toString()') == 'Corto'
        page.evaluate('getSelection().removeAllRanges()')

        def menu_after(selector):
            page.evaluate('document.querySelector(".cc-menu")?.remove()')
            prevented = page.evaluate("""s=>{const t=document.querySelector(s);const e=new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2,clientX:200,clientY:200});t.dispatchEvent(e);return e.defaultPrevented;}""", selector)
            return prevented, page.locator('.cc-menu').count()
        assert menu_after('#link') == (False, 0), 'links keep the native menu'
        assert menu_after('#code') == (False, 0), 'code keeps the native menu'
        assert menu_after('#cc-text-u1') == (True, 1), 'message text opens the message menu'
        assert page.evaluate('document.activeElement.getAttribute("role")') == 'menuitem'
        labels = page.locator('.cc-menu [role=menuitem]').all_inner_texts()
        assert labels == ['Copiar mensaje', 'Seleccionar texto', 'Expandir mensaje'], labels
        page.keyboard.press('ArrowDown'); page.keyboard.press('ArrowDown')
        assert page.evaluate('document.activeElement.textContent') == 'Expandir mensaje'
        page.keyboard.press('Escape')
        assert page.locator('.cc-menu').count() == 0
        menu_after('#cc-text-u1'); page.locator('.cc-menu [data-cc-menu="copy"]').click()
        assert page.evaluate('window.__copied') == LONG
        page.evaluate("""()=>{const r=document.createRange();r.selectNodeContents(document.querySelector('#cc-text-u2'));getSelection().addRange(r);}""")
        assert menu_after('#cc-text-u2') == (False, 0), 'selected text keeps the native menu'
        page.evaluate('getSelection().removeAllRanges()')

        page.evaluate('NexusChatContinuity.toggle(true)')
        page.wait_for_selector('#cc-panel [data-cc-open="c2"]')
        assert page.locator('#content').evaluate('n=>n.classList.contains("cc-chats-open")')
        assert page.locator('[data-parity="conversations"]').get_attribute('aria-expanded') == 'true'
        assert page.locator('#cc-panel [data-cc-open="c1"]').get_attribute('aria-current') == 'true'
        panel_box = page.locator('#cc-panel').bounding_box(); messages_box = page.locator('.agent-messages').bounding_box()
        assert messages_box['x'] + messages_box['width'] <= panel_box['x'] + 1, 'panel docks beside, not over, the chat'
        page.fill('#cc-search', 'gato'); page.wait_for_timeout(350)
        assert page.locator('#cc-panel .cc-row').count() == 1
        assert json.loads(json.dumps(page.evaluate('calls.filter(c=>c[0]==="listChats").at(-1)[1]'))) == {'query': 'gato'}
        page.fill('#cc-search', ''); page.wait_for_timeout(350)
        page.locator('#cc-panel [data-cc-rename="c1"]').click()
        assert page.evaluate('document.activeElement.id') == 'cc-rename-input'
        page.fill('#cc-rename-input', 'Mi clip'); page.keyboard.press('Enter')
        page.wait_for_selector('#cc-panel [data-cc-row="c1"] .cc-row-title >> text=Mi clip')
        assert page.evaluate('document.activeElement.dataset.ccRename') == 'c1', 'focus returns to the row'
        page.locator('#cc-panel [data-cc-rename="c2"]').click()
        page.locator('#cc-panel [data-cc-reset="c2"]').click()
        page.wait_for_selector('#cc-panel [data-cc-row="c2"] .cc-row-title >> text=Automático')
        assert page.evaluate('calls.filter(c=>c[0]==="renameChat").at(-1)[1]') == {'id': 'c2', 'title': ''}
        page.locator('#cc-search').focus(); page.keyboard.press('Escape')
        assert page.locator('#cc-panel').is_hidden() and page.evaluate('document.activeElement.dataset.parity') == 'conversations'
        browser.close()
    assert not errors, errors
    print('PASS chat-continuity DOM: plegado 8 líneas, respuestas sin plegar, copiar/seleccionar, menú contextual sin secuestrar enlaces/código/selección, panel con búsqueda/renombrar/restablecer; 0 llamadas de red')

if __name__ == '__main__':
    main()
