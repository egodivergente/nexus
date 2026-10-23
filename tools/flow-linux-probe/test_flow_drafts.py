import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock
from unittest.mock import patch
import asyncio
import recovery
import flow_drafts


class DraftTests(unittest.IsolatedAsyncioTestCase):
    def test_exact_unicode_text_survives_and_duplicate_backups_are_reused(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            text = '  Mi borrador 😀\ncon sus saltos y espacios  '
            path = flow_drafts.preserve(root, 'https://flow.google.com/project/local-fixture', text)
            self.assertEqual(text, json.loads(path.read_text('utf-8'))['text'])
            self.assertEqual(path, flow_drafts.preserve(root, 'https://flow.google.com/project/local-fixture', text))
            self.assertEqual(1, len(list(path.parent.glob('*.json'))))

    async def test_empty_or_automation_owned_text_restores_previous_draft(self):
        for current in ['', 'new prompt']:
            composer = SimpleNamespace(evaluate=AsyncMock(return_value=current), fill=AsyncMock())
            self.assertTrue(await flow_drafts.restore_if_untouched(composer, 'previous', 'new prompt'))
            composer.fill.assert_awaited_once_with('previous', timeout=5000)

    async def test_manual_edit_is_never_replaced_by_restoration(self):
        composer = SimpleNamespace(evaluate=AsyncMock(return_value='my manual edit'), fill=AsyncMock())
        self.assertFalse(await flow_drafts.restore_if_untouched(composer, 'previous', 'new prompt'))
        composer.fill.assert_not_awaited()

    async def test_submission_preserves_and_restores_the_exact_existing_text(self):
        class Composer:
            text = '  Mi borrador anterior 😀  '
            is_editable = AsyncMock(return_value=True)
            async def evaluate(self, script):
                return len(self.text.strip()) if script == recovery.COMPOSER_LENGTH else self.text.strip() if '.trim()' in script else self.text
            async def fill(self, text, **kwargs):
                self.text = text
        composer = Composer()
        button = SimpleNamespace(is_enabled=AsyncMock(return_value=True), click=AsyncMock())
        page = SimpleNamespace(url='https://flow.google.com/project/local-fixture', wait_for_timeout=AsyncMock(),
            locator=lambda selector: SimpleNamespace(count=AsyncMock(return_value=0)))
        async def invoke(action):
            return await action(page)
        with tempfile.TemporaryDirectory() as directory, patch.object(recovery, 'ROOT', Path(directory)), \
             patch.object(recovery, 'FLOW_UI_LOCK', asyncio.Lock()), patch.object(recovery, 'flow_controls', AsyncMock(return_value=(composer, button))), \
             patch.object(recovery, 'composer_reference_count', AsyncMock(return_value=0)), patch.object(recovery, 'attach_references', AsyncMock()), \
             patch.object(recovery, 'download_controls', AsyncMock(return_value=[])), patch.object(recovery, 'with_flow_project_page', side_effect=invoke):
            await recovery.drive_flow_submission('Nueva instrucción')
            button.click.assert_awaited_once()
            self.assertEqual('  Mi borrador anterior 😀  ', composer.text)
            saved, = (Path(directory)/'flow-drafts').glob('*.json')
            self.assertEqual(composer.text, json.loads(saved.read_text('utf-8'))['text'])
