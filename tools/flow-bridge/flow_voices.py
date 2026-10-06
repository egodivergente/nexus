"""Preset voices observed in the account's native Omni ingredient picker."""
import re
from contextvars import ContextVar

VOICES=('Achernar','Achird','Algenib','Algieba','Alnilam','Aoede','Autonoe',
    'Callirrhoe','Charon','Despina','Enceladus','Erinome','Fenrir','Gacrux','Iapetus')
pending_voice=ContextVar('nexus_flow_voice',default=None)

async def attach(page,voice):
    if voice not in {name.lower() for name in VOICES}:raise ValueError('Unknown verified preset voice')
    await page.locator('flow-base-prompt-box button.add-menu-trigger').click(timeout=5000)
    await page.get_by_role('tab',name=re.compile('Voces|Voices')).click(timeout=5000)
    name=next(name for name in VOICES if name.lower()==voice)
    option=page.get_by_role('option').filter(has=page.locator('.asset-title').filter(has_text=re.compile('^'+re.escape(name)+'$')))
    await option.click(timeout=5000)
    # Do not fill performance/sample fields: those create/preview a new voice.
    await page.locator('button.detail-add-to-prompt-btn').click(timeout=5000)
    await page.wait_for_timeout(600)
    await page.keyboard.press('Escape')

def install():
    from gflow_cli.mcp import tools
    if getattr(tools._build_video_payload,'_nexus_voice',False):return
    original=tools._build_video_payload
    def build(**kwargs):
        payload=original(**kwargs)
        voice=pending_voice.get()
        if voice:payload['reference_audio']=voice
        return payload
    build._nexus_voice=True
    tools._build_video_payload=build
