"""Select only the generation controls observed in the current Flow editor."""
import re


class SettingsError(Exception):
    pass


async def read_workspace(page):
    """Read only labels already in the DOM; status must never open or change settings."""
    result = {}
    title = (await page.title()).strip()
    if title and title.lower() not in ('flow', 'google flow'):
        title = re.sub(r'^(?:Google\s+)?Flow\s*[-|]\s*', '', title, flags=re.I)
        result['project_name'] = re.sub(r'\s*[-|]\s*(?:Google\s+)?Flow\s*$', '', title, flags=re.I)[:160]
    model = page.get_by_role('button', name=re.compile(r'^Seleccionar familia de modelos$|^Select model family$'), include_hidden=True)
    if await model.count() == 1:
        label = ' '.join(part.strip() for part in (await model.inner_text()).splitlines()
                         if part.strip() not in ('arrow_drop_down', 'expand_more', 'keyboard_arrow_down'))
        if label:
            result['model_name'] = label[:100]
    ratios = page.locator('[role="radio"][aria-checked="true"]')
    for index in range(await ratios.count()):
        label = (await ratios.nth(index).inner_text()).strip()
        match = re.search(r'(?<!\d)(\d{1,2}\s*:\s*\d{1,2})(?!\d)', label)
        if match:
            result['aspect_ratio'] = re.sub(r'\s', '', match[1])
            break
    return result


async def inspect_workspace(page):
    """Explicit settings inspection; open and close only the observed settings menu."""
    result = await read_workspace(page)
    if result.get('model_name'):
        return result
    trigger = page.get_by_role('button', name=re.compile(r'^Activador de ajustes$|^Settings trigger$'))
    if await trigger.count() != 1 or not await trigger.is_visible():
        return result
    original_url = page.url
    await trigger.click(timeout=5000)
    try:
        model = page.get_by_role('button', name=re.compile(r'^Seleccionar familia de modelos$|^Select model family$'))
        await model.wait_for(state='visible', timeout=5000)
        if page.url == original_url:
            result.update(await read_workspace(page))
        return result
    finally:
        if page.url == original_url:
            await page.keyboard.press('Escape')


async def prepare_generation(page, expected_output, count=None):
    agent = page.get_by_role('button', name=re.compile(r'^Agente$|^Agent$'))
    if await agent.count() == 1 and await agent.get_attribute('aria-pressed') == 'true':
        await agent.click(timeout=5000)
    mode = page.get_by_role('radio', name=re.compile(r'Imagen|Image', re.I) if expected_output == 'image' else re.compile(r'Vídeo|Video', re.I))
    if not await mode.count() or not await mode.first.is_visible():
        await page.get_by_role('button', name=re.compile(r'^Activador de ajustes$|^Settings trigger$')).click(timeout=5000)
    await mode.first.wait_for(state='visible', timeout=5000)
    if await mode.first.get_attribute('aria-checked') != 'true':
        await mode.first.click(timeout=5000)
    if await mode.first.get_attribute('aria-checked') != 'true':
        raise SettingsError('flow_output_mode_unconfirmed')
    if count is not None:
        quantity = page.get_by_role('radio', name=f'x{count}', exact=True)
        await quantity.click(timeout=5000)
        if await quantity.get_attribute('aria-checked') != 'true':
            raise SettingsError('flow_quantity_unconfirmed')
    model = page.get_by_role('button', name=re.compile(r'^Seleccionar familia de modelos$|^Select model family$'))
    if expected_output == 'image' and re.search(r'Nano\s*Banana\s*Lite', await model.inner_text(), re.I):
        raise SettingsError('flow_model_forbidden')
    await page.keyboard.press('Escape')


def quantity_for_run(expected_results, runs, index):
    if not expected_results:
        return None  # Older clients explicitly use the current project's quantity.
    per_run = (expected_results + runs - 1) // runs
    quantity = min(per_run, expected_results - index * per_run)
    if not 1 <= quantity <= 4:
        raise SettingsError('flow_quantity_unconfirmed')
    return quantity
