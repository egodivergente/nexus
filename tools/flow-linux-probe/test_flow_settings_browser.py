import sys
import unittest
from playwright.async_api import async_playwright
from flow_settings import prepare_generation, quantity_for_run, SettingsError


class NativeSettingsTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.pw=await async_playwright().start()
        options={'channel':'msedge'} if sys.platform=='win32' else {'executable_path':'/usr/bin/chromium'}
        self.browser=await self.pw.chromium.launch(headless=True,**options)
        self.page=await self.browser.new_page()
        await self.page.set_content('''
        <button aria-pressed="true" onclick="this.setAttribute('aria-pressed','false')">Agente</button>
        <button onclick="document.querySelector('#settings').hidden=false">Activador de ajustes</button>
        <div id="settings" hidden>
        <button role="radio" aria-checked="false" onclick="this.setAttribute('aria-checked','true')">image Imagen</button>
        <button role="radio" aria-checked="true">videocam Vídeo</button>
        <button aria-label="Seleccionar familia de modelos">Nano Banana 2.1</button>
        <button role="radio" aria-checked="false" onclick="this.setAttribute('aria-checked','true')">x1</button>
        <button role="radio" aria-checked="true">x4</button>
        </div><button id="generate" onclick="window.submitted=true">Generar</button>
        <script>document.addEventListener('keydown',e=>{if(e.key==='Escape')document.querySelector('#settings').hidden=true})</script>
        ''')

    async def asyncTearDown(self):
        await self.browser.close()
        await self.pw.stop()

    async def test_image_request_leaves_agent_mode_and_selects_exactly_one_before_submit(self):
        await prepare_generation(self.page,'image',1)
        self.assertEqual(await self.page.get_by_role('button',name='Agente',exact=True).get_attribute('aria-pressed'),'false')
        self.assertEqual(await self.page.locator('button[role=radio]').first.get_attribute('aria-checked'),'true')
        self.assertEqual(await self.page.locator('button[role=radio]').nth(2).get_attribute('aria-checked'),'true')
        self.assertTrue(await self.page.locator('#settings').is_hidden())
        self.assertIsNone(await self.page.evaluate('window.submitted'))

    async def test_forbidden_lite_never_reaches_generate(self):
        await self.page.get_by_label('Seleccionar familia de modelos').evaluate("e=>e.textContent='Nano Banana Lite'")
        with self.assertRaisesRegex(SettingsError,'flow_model_forbidden'):
            await prepare_generation(self.page,'image',1)
        self.assertIsNone(await self.page.evaluate('window.submitted'))

    def test_batch_quantities_sum_to_requested_count_including_partial_last_run(self):
        for total in range(1,41):
            runs=(total+3)//4
            quantities=[quantity_for_run(total,runs,index) for index in range(runs)]
            self.assertEqual(sum(quantities),total)
            self.assertTrue(all(1<=quantity<=4 for quantity in quantities))
        self.assertIsNone(quantity_for_run(0,1,0))


if __name__=='__main__':
    unittest.main()
