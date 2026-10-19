"""Conversación con el agente de Google Flow (panel «Sesión …» del proyecto abierto).

Nexus escribe un mensaje en el cuadro del agente, espera a que termine de responder y lee su respuesta:
el texto, las opciones que proponga y las imágenes/vídeos que genere. Solo usa la página ya abierta y
autenticada; nunca exporta cookies ni llama a APIs privadas: los archivos se descargan con fetch() desde
la propia página, igual que si el usuario los abriera.
"""
import asyncio
import base64
import secrets
import time

from flow_references import COMPOSER

AGENT_JOBS = {}
AGENT_TEXT_LIMIT = 4000
SETTLE_SECONDS = 4

# Lee las filas de respuesta del agente: texto (sin los iconos de la barra de acciones), opciones y medios.
READ_ROWS = """() => {
  const panel = document.querySelector('flow-agent-panel');
  if (!panel) return null;
  const rows = [...panel.querySelectorAll('.agent-row')];
  return rows.map(row => {
    const clone = row.cloneNode(true);
    clone.querySelectorAll('flow-chat-message-action-bar, mat-icon').forEach(n => n.remove());
    const media = [...row.querySelectorAll('img, video')].filter(m => {
      const src = m.currentSrc || m.src || '';
      return src.startsWith('https://flow.google.com/') || src.startsWith('blob:');
    }).map(m => ({kind: m.tagName === 'VIDEO' ? 'video' : 'image', label: (m.alt || m.getAttribute('aria-label') || '').slice(0, 80),
                  width: m.naturalWidth || m.videoWidth || 0, height: m.naturalHeight || m.videoHeight || 0}));
    const options = [...row.querySelectorAll('flow-a2ui-multiple-choice button, flow-a2ui-multiple-choice [role="option"]')]
      .map(o => (o.innerText || o.getAttribute('aria-label') || '').trim()).filter(Boolean).slice(0, 20);
    return {text: (clone.innerText || '').trim(), media, options, done: !!row.querySelector('flow-chat-message-action-bar')};
  });
}"""

MEDIA_SOURCE = """([rowIndex, mediaIndex]) => {
  const rows = [...document.querySelectorAll('flow-agent-panel .agent-row')];
  const row = rows[rowIndex]; if (!row) return null;
  const media = [...row.querySelectorAll('img, video')].filter(m => {
    const src = m.currentSrc || m.src || '';
    return src.startsWith('https://flow.google.com/') || src.startsWith('blob:');
  });
  const m = media[mediaIndex]; return m ? (m.currentSrc || m.src) : null;
}"""

FETCH_MEDIA = """async (src) => {
  const response = await fetch(src, {credentials: 'include'});
  if (!response.ok) return {error: response.status};
  const blob = await response.blob();
  if (blob.size > 300 * 1024 * 1024) return {error: 'too_large'};
  const data = await new Promise(resolve => { const r = new FileReader(); r.onload = () => resolve(r.result); r.readAsDataURL(blob); });
  return {type: blob.type, data: String(data).split(',', 2)[1]};
}"""


async def agent_rows(page):
    return await page.evaluate(READ_ROWS) or []


async def send_agent_message(page, message, ui_lock, flow_error):
    """Escribe y envía; devuelve el número de respuestas del agente que había antes (para saber cuáles son nuevas)."""
    async with ui_lock:
        composer = page.locator(COMPOSER).first
        submit = page.locator('button[aria-label="Iniciar generación"]')
        if await composer.count() == 0 or not await composer.is_visible():
            raise flow_error('flow_agent_unavailable', 409)
        if await composer.evaluate('el => ((el.value ?? el.innerText) || "").trim().length'):
            raise flow_error('flow_composer_not_empty', 409)
        before = len(await agent_rows(page))
        await composer.fill(message, timeout=10000)
        await page.wait_for_timeout(200)
        if await submit.count() != 1 or not await submit.is_enabled():
            await composer.fill('', timeout=5000)
            raise flow_error('flow_submit_unavailable', 409)
        await submit.click(timeout=10000)
        return before


def new_agent_job(before):
    job_id = secrets.token_urlsafe(18)
    AGENT_JOBS[job_id] = {'job_id': job_id, 'before': before, 'state': 'waiting', 'created_at': time.time(),
                          'last_change': time.time(), 'signature': '', 'reply': None}
    return AGENT_JOBS[job_id]


async def refresh_agent_job(page, job):
    rows = await agent_rows(page)
    fresh = rows[job['before']:]
    signature = repr([(r['text'], len(r['media']), r['done']) for r in fresh])
    now = time.time()
    if signature != job['signature']:
        job['signature'] = signature
        job['last_change'] = now
    settled = fresh and fresh[-1]['done'] and now - job['last_change'] >= SETTLE_SECONDS
    job['state'] = 'completed' if settled else ('replying' if fresh else 'waiting')
    job['reply'] = {
        'text': '\n\n'.join(r['text'] for r in fresh if r['text'])[:AGENT_TEXT_LIMIT],
        'options': [o for r in fresh for o in r['options']][:20],
        'media': [dict(m, row=job['before'] + i, index=j) for i, r in enumerate(fresh) for j, m in enumerate(r['media'])],
    }
    return public_agent_job(job)


def public_agent_job(job):
    return {'job_id': job['job_id'], 'state': job['state'], 'created_at': job['created_at'],
            'reply': job.get('reply') or {'text': '', 'options': [], 'media': []}}


async def fetch_agent_media(page, row, index):
    src = await page.evaluate(MEDIA_SOURCE, [row, index])
    if not src:
        return None
    result = await page.evaluate(FETCH_MEDIA, src)
    if not result or 'error' in result:
        return None
    return result.get('type') or 'application/octet-stream', base64.b64decode(result['data'])
