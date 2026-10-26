"""Conversation index, titles and RPC compatibility on isolated temporary folders."""
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import conversation_store as store_module
from conversation_store import ConversationStore, smart_title
import local_host as host


ANDROID_CHAT = {
    'items': [
        {'type': 'com.vidadomu.nexus.assistant.ChatItem.User', 'id': 'u1', 'text': 'Oye Nexus, necesito que me hagas un cartel para el festival', 'attachments': []},
        {'type': 'com.vidadomu.nexus.assistant.ChatItem.Assistant', 'id': 'a1', 'text': 'Claro'},
        {'type': 'com.vidadomu.nexus.assistant.ChatItem.PlanCard', 'id': 'p1', 'planId': 'plan1'},
        {'type': 'com.vidadomu.nexus.assistant.ChatItem.User', 'id': 'u2', 'text': 'Ahora en azul marino'},
    ],
    'plans': {'plan1': {'plan': {'id': 'plan1', 'modelName': 'GPT Image', 'prompt': 'cartel'}, 'state': 'DONE'}},
    'projectId': 'general', 'updatedAt': 1761370000000,
}


def desktop_chat(identifier, *texts):
    return {'id': identifier, 'items': [{'role': 'user' if n % 2 == 0 else 'assistant', 'text': t} for n, t in enumerate(texts)], 'draft': ''}


class TitleTests(unittest.TestCase):
    def test_greetings_and_requests_are_dropped(self):
        self.assertEqual('Un vídeo de un faro en la niebla', smart_title('hola, puedes hacerme un vídeo de un faro en la niebla. Que dure 8 s'))
        self.assertEqual('Un cartel para el festival', smart_title('Oye Nexus, necesito que me hagas un cartel para el festival'))

    def test_long_titles_cut_on_a_word_without_markup(self):
        title = smart_title('**Corrige** el encargo GPT Image 2.5 Sunburst (ord_1a408518134a): falló con «Metadatos locales no admitidos.»')
        self.assertLessEqual(len(title), 49)
        self.assertTrue(title.endswith('…'))
        self.assertNotIn('*', title)

    def test_synced_mark_kept_and_never_empty(self):
        self.assertEqual('↔ Ideas para el corto', smart_title('↔ ideas para el corto'))
        self.assertEqual('Nueva conversación', smart_title(''))
        self.assertEqual('Primera línea', smart_title('primera línea\nsegunda línea'))


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name) / 'assistant'
        self.store = ConversationStore(self.root)

    def tearDown(self):
        self.folder.cleanup()

    def write_android(self, name='android1'):
        path = self.root / 'conversations' / (name + '.json')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(ANDROID_CHAT), 'utf-8')
        return path

    def test_unsent_draft_and_attachments_can_be_archived(self):
        saved = self.store.archive({'id': 'draft_only', 'items': [], 'draft': 'Idea pendiente', 'draftAttachments': [{'id': 'file_a', 'name': 'Referencia.png'}]})
        self.assertTrue(saved['saved'])
        loaded = self.store.load('draft_only')
        self.assertEqual('Idea pendiente', loaded['draft'])
        self.assertEqual('file_a', loaded['draftAttachments'][0]['id'])
        self.assertEqual(0, self.store.list()[0]['messages'])
        self.assertTrue(self.store.archive({'id': 'file_only', 'items': [], 'draftAttachments': [{'id': 'file_b'}]})['saved'])
        self.assertFalse(self.store.archive({'items': [], 'draft': '  '})['saved'])

    def test_list_includes_metadata_and_android_chats(self):
        self.write_android()
        self.store.archive(desktop_chat('desk1', 'hola, crea un logo para la tienda', 'Hecho', 'más grande'))
        chats = {c['id']: c for c in self.store.list()}
        self.assertEqual({'id', 'title', 'messages', 'preview', 'updatedAt', 'customTitle'}, set(chats['desk1']))
        self.assertEqual('Un logo para la tienda', chats['desk1']['title'])
        self.assertEqual('más grande', chats['desk1']['preview'])
        self.assertEqual(3, chats['desk1']['messages'])
        self.assertIsNone(chats['desk1']['customTitle'])
        self.assertEqual('Un cartel para el festival', chats['android1']['title'])
        self.assertEqual(4, chats['android1']['messages'])
        self.assertEqual('Ahora en azul marino', chats['android1']['preview'])
        self.assertEqual(1761370000000, chats['android1']['updatedAt'])

    def test_unchanged_files_are_not_decoded_again(self):
        self.write_android()
        self.store.archive(desktop_chat('desk1', 'uno', 'dos'))
        self.store.list()
        fresh = ConversationStore(self.root)
        with patch.object(store_module, 'converted', side_effect=AssertionError('decoded')):
            self.assertEqual(2, len(fresh.list()))
        path = self.root / 'conversations' / 'android1.json'
        changed = dict(ANDROID_CHAT, items=ANDROID_CHAT['items'][:1])
        path.write_text(json.dumps(changed), 'utf-8')
        os.utime(path, ns=(path.stat().st_atime_ns, path.stat().st_mtime_ns + 5_000_000_000))
        calls = []
        original = store_module.converted
        with patch.object(store_module, 'converted', side_effect=lambda v: calls.append(1) or original(v)):
            chats = {c['id']: c for c in fresh.list()}
        self.assertEqual(1, len(calls))
        self.assertEqual(1, chats['android1']['messages'])

    def test_deleted_and_corrupt_files_leave_the_index(self):
        self.write_android()
        self.store.archive(desktop_chat('desk1', 'uno'))
        self.store.list()
        (self.root / 'conversations' / 'android1.json').unlink()
        (self.root / 'conversations' / 'broken.json').write_text('{no json', 'utf-8')
        self.assertEqual(['desk1'], [c['id'] for c in self.store.list()])
        self.assertTrue((self.root / 'conversations' / 'broken.json').exists())
        index = json.loads((self.root / 'conversations-index-v1.json').read_text('utf-8'))
        self.assertEqual({'desk1', 'broken'}, set(index['entries']))
        self.assertTrue(index['entries']['broken']['invalid'])

    def test_valid_json_with_malformed_content_is_skipped_not_deleted(self):
        folder = self.root / 'conversations'
        self.store.archive(desktop_chat('good', 'uno'))
        bad = {
            'nullitem': {'items': [None]},
            'itemsdict': {'items': {'a': 1}},
            'desktopjunk': {'desktop': True, 'items': [1, 'x', None, {'role': 'user', 'text': 'vale'}]},
            'plansaslist': {'items': [{'type': 'PlanCard', 'planId': 'p'}], 'plans': []},
            'badattach': {'items': [{'type': 'User', 'text': 'a', 'attachments': [None]}]},
            'notobject': [1, 2],
            'hugeupdated': {'items': [{'type': 'User', 'text': 'b'}], 'updatedAt': float('inf')},
        }
        for name, value in bad.items():
            (folder / (name + '.json')).write_text(json.dumps(value), 'utf-8')
        chats = {c['id']: c for c in self.store.list()}
        self.assertEqual({'good', 'desktopjunk'}, set(chats))
        self.assertEqual(1, chats['desktopjunk']['messages'])
        self.assertTrue(all((folder / (name + '.json')).exists() for name in bad))
        with patch.object(store_module, 'converted', side_effect=AssertionError('decoded')):
            self.assertEqual({'good', 'desktopjunk'}, {c['id'] for c in ConversationStore(self.root).list()})
        (folder / 'nullitem.json').write_text(json.dumps(desktop_chat('nullitem', 'arreglado')), 'utf-8')
        os.utime(folder / 'nullitem.json', ns=(0, (folder / 'nullitem.json').stat().st_mtime_ns + 5_000_000_000))
        self.assertIn('nullitem', {c['id'] for c in self.store.list()})

    def test_structurally_corrupt_index_entries_are_rebuilt(self):
        self.store.archive(desktop_chat('a', 'uno'))
        self.store.archive(desktop_chat('b', 'dos'))
        self.store.archive(desktop_chat('c', 'tres'))
        stat = {name: (self.root / 'conversations' / (name + '.json')).stat() for name in 'abc'}
        index_file = self.root / 'conversations-index-v1.json'
        for entries in ([1, 2], 'x', None):
            index_file.write_text(json.dumps({'version': 1, 'entries': entries}), 'utf-8')
            self.assertEqual({'a', 'b', 'c'}, {c['id'] for c in ConversationStore(self.root).list()})
        index_file.write_text(json.dumps({'version': 1, 'entries': {
            'a': {'modified': stat['a'].st_mtime_ns, 'length': stat['a'].st_size},
            'b': 'not a dict',
            'c': {'modified': stat['c'].st_mtime_ns, 'length': stat['c'].st_size, 'automatic': 3, 'messages': '1',
                  'preview': '', 'updatedAt': 0, 'search': ''},
            '../evil': {'modified': 1, 'length': 1, 'invalid': True},
        }}), 'utf-8')
        chats = {c['id']: c for c in ConversationStore(self.root).list()}
        self.assertEqual({'a': 'Uno', 'b': 'Dos', 'c': 'Tres'}, {k: v['title'] for k, v in chats.items()})
        self.assertNotIn('../evil', json.loads(index_file.read_text('utf-8'))['entries'])
        index_file.write_text(json.dumps([{'version': 1}]), 'utf-8')
        self.assertEqual(3, len(ConversationStore(self.root).list()))

    def test_corrupt_index_is_rebuilt(self):
        self.store.archive(desktop_chat('desk1', 'uno'))
        (self.root / 'conversations-index-v1.json').write_text('garbage', 'utf-8')
        self.assertEqual(['desk1'], [c['id'] for c in ConversationStore(self.root).list()])

    def test_rename_and_reset_never_touch_the_chat_file(self):
        path = self.write_android()
        before = path.read_bytes()
        renamed = self.store.rename('android1', '  Cartel   festival  ')
        self.assertEqual({'id': 'android1', 'title': 'Cartel festival', 'customTitle': 'Cartel festival'}, renamed)
        chat = self.store.list()[0]
        self.assertEqual(('Cartel festival', 'Cartel festival'), (chat['title'], chat['customTitle']))
        self.assertEqual(before, path.read_bytes())
        self.assertEqual({'android1': 'Cartel festival'}, json.loads((self.root / 'conversation-titles.json').read_text('utf-8')))
        reset = self.store.rename('android1', '   ')
        self.assertEqual({'id': 'android1', 'title': 'Un cartel para el festival', 'customTitle': None}, reset)
        self.assertIsNone(self.store.list()[0]['customTitle'])
        self.assertEqual(80, len(self.store.rename('android1', 'x' * 200)['customTitle']))

    def test_custom_title_survives_re_archive(self):
        self.store.archive(desktop_chat('desk1', 'uno'))
        self.store.rename('desk1', 'Mi nombre')
        self.store.archive(desktop_chat('desk1', 'uno', 'dos', 'tres'))
        chat = self.store.list()[0]
        self.assertEqual(('Mi nombre', 3), (chat['title'], chat['messages']))

    def test_rename_rejects_invalid_or_missing_conversations(self):
        with self.assertRaises(ValueError):
            self.store.rename('../x', 'a')
        with self.assertRaises(ValueError):
            self.store.rename('missing', 'a')

    def test_query_searches_titles_and_messages(self):
        self.store.archive(desktop_chat('a', 'vídeo de un faro', 'Listo con niebla'))
        self.store.archive(desktop_chat('b', 'cartel azul'))
        self.store.rename('b', 'Festival')
        self.assertEqual(['a'], [c['id'] for c in self.store.list('NIEBLA')])
        self.assertEqual(['b'], [c['id'] for c in self.store.list('festival')])
        self.assertEqual([], [c['id'] for c in self.store.list('faro azul')])

    def test_archiving_over_android_chat_keeps_the_original(self):
        path = self.write_android()
        original = path.read_bytes()
        self.store.archive(desktop_chat('android1', 'nuevo'))
        self.assertEqual(original, (self.root / 'conversations-originals' / 'android1.json').read_bytes())
        self.store.archive(desktop_chat('android1', 'otra vez'))
        self.assertEqual(original, (self.root / 'conversations-originals' / 'android1.json').read_bytes())

    def test_concurrent_archives_and_renames_stay_consistent(self):
        errors = []
        def work(n):
            try:
                for round_ in range(5):
                    self.store.archive(desktop_chat(f'c{n}', f'mensaje {n}', f'ronda {round_}'))
                    self.store.rename(f'c{n}', f'Chat {n}')
                    self.store.list()
            except Exception as error:
                errors.append(error)
        threads = [threading.Thread(target=work, args=(n,)) for n in range(8)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        self.assertEqual([], errors)
        chats = {c['id']: c for c in ConversationStore(self.root).list()}
        self.assertEqual({f'c{n}' for n in range(8)}, set(chats))
        self.assertTrue(all(chats[f'c{n}']['title'] == f'Chat {n}' for n in range(8)))
        self.assertEqual([], [p.name for p in self.root.rglob('*.tmp')])


class DeviceContinuityStoreTests(unittest.TestCase):
    def test_rich_attachments_survive_and_only_drive_projection_is_hidden(self):
        with tempfile.TemporaryDirectory() as folder:
            store = ConversationStore(Path(folder))
            items = [{'role': 'user', 'text': 'Escena de prueba'}]
            from chat_continuity import history_id
            fingerprint = history_id(items)
            store.archive({'id': 'sync_' + fingerprint, 'items': items})
            rich = {'id': 'device_one', 'items': items, 'continuityOrigin': {'device': 'phone'},
                    'continuityTextHash': fingerprint, 'continuityAttachments': [{'id': 'a', 'url': '/media/a.png', 'mime': 'image/png', 'name': 'a.png'}]}
            store.archive(rich)
            store.archive({**rich, 'id': 'device_two'})
            self.assertEqual({'device_one', 'device_two'}, {row['id'] for row in store.list()})
            self.assertEqual('a.png', store.load('device_one')['items'][-1]['attachments'][0]['name'])
            self.assertEqual(1, len(json.loads((store.directory / 'device_one.json').read_text('utf-8'))['items']))
            store.archive(store.load('device_one'))
            self.assertEqual(2, len(store.load('device_one')['items']))
            self.assertTrue((store.directory / ('sync_' + fingerprint + '.json')).is_file())
            self.assertEqual({'device_one', 'device_two'}, {row['id'] for row in ConversationStore(Path(folder)).list()})


class RpcTests(unittest.TestCase):
    def test_dispatch_operations_keep_their_contract(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(host, 'DATA', Path(folder)):
            self.assertEqual({'saved': False}, host.dispatch({}, 'archiveChat', {'items': []}))
            saved = host.dispatch({}, 'archiveChat', {'items': [{'role': 'user', 'text': 'hola, genera un faro'}], 'draft': 'x'})
            self.assertTrue(saved['saved'])
            loaded = host.dispatch({}, 'loadChat', {'id': saved['id']})
            self.assertEqual(('x', True), (loaded['draft'], loaded['desktop']))
            self.assertEqual('Un faro', host.dispatch({}, 'listChats', {})[0]['title'])
            self.assertEqual('Faro', host.dispatch({}, 'renameChat', {'id': saved['id'], 'title': 'Faro'})['title'])
            self.assertEqual('Faro', host.dispatch({}, 'listChats', {})[0]['title'])
            self.assertIsNone(host.dispatch({}, 'renameChat', {'id': saved['id'], 'title': ''})['customTitle'])
            with self.assertRaises(ValueError):
                host.dispatch({}, 'loadChat', {'id': '../secret'})
            with self.assertRaises(ValueError):
                host.dispatch({}, 'archiveChat', {'id': 'bad id', 'items': [{'role': 'user', 'text': 'a'}]})


if __name__ == '__main__':
    unittest.main()
