import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

import flow_import
import recovery
import flow_references

AUTH = {'Authorization': 'Bearer private-test-token'}
IMG = 'https://flow.google.com/asb/ANqvLOabc?w=1376'


class MediaHelpers(unittest.TestCase):
    def test_key_ignores_query_and_is_stable(self):
        self.assertEqual(flow_import.media_key(IMG), flow_import.media_key('https://flow.google.com/asb/ANqvLOabc'))
        self.assertRegex(flow_import.media_key(IMG), flow_import.MEDIA_KEY)

    def test_one_entry_per_file_and_video_beats_poster(self):
        raw = [{'kind': 'image', 'src': IMG, 'width': 1376, 'height': 768},
               {'kind': 'image', 'src': IMG, 'width': 1376, 'height': 768},
               {'kind': 'image', 'src': 'https://flow.google.com/v/1', 'width': 1280, 'height': 720},
               {'kind': 'video', 'src': 'https://flow.google.com/v/1', 'width': 1280, 'height': 720}]
        items = flow_import.unique_media(raw)
        self.assertEqual([i['kind'] for i in items], ['image', 'video'])

    def test_public_media_hides_source_urls(self):
        public = flow_import.public_media(flow_import.unique_media([{'kind': 'image', 'src': IMG, 'width': 1, 'height': 1}]))
        self.assertNotIn('src', public[0])
        self.assertEqual(set(public[0]), {'key', 'kind', 'width', 'height'})

    def test_frame_pair_requires_uploaded_start_and_preserves_order(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            directory = root / 'flow-references'
            directory.mkdir()
            image = b'\x89PNG\r\n\x1a\n' + b'frame'
            import hashlib
            start = 'ref_' + hashlib.sha256(image).hexdigest()
            (directory / (start + '.png')).write_bytes(image)
            self.assertEqual([directory / (start + '.png')], flow_references.frame_paths(root, start))
            with self.assertRaises(flow_references.ReferenceError):
                flow_references.frame_paths(root, end_frame_id=start)
            with self.assertRaises(flow_references.ReferenceError):
                flow_references.frame_paths(root, 'https://example.test/frame')


class ImportRoutes(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        (root / 'recovery-token').write_text('private-test-token')
        self.patches = [patch.object(recovery, 'ROOT', root), patch.object(recovery, 'TOKEN_FILE', root / 'recovery-token'),
                        patch.object(recovery, 'start_browser', AsyncMock(return_value=True))]
        for p in self.patches:
            p.start()
        self.client = TestClient(recovery.app, base_url=recovery.ORIGIN)

    def tearDown(self):
        self.client.close()
        for p in reversed(self.patches):
            p.stop()
        self.tmp.cleanup()

    def test_routes_require_auth(self):
        self.assertEqual(self.client.get('/api/flow/projects').status_code, 401)

    def test_invalid_identifiers_never_reach_the_browser(self):
        with patch.object(recovery, 'with_flow_browser', AsyncMock()) as browser:
            self.assertEqual(self.client.get('/api/flow/projects/..%2Fx/media', headers=AUTH).status_code, 404)
            self.assertEqual(self.client.get('/api/flow/projects/abcdefgh-1234/media/zz', headers=AUTH).status_code, 404)
            browser.assert_not_called()

    def test_signed_out_gallery_reports_auth_required(self):
        with patch.object(recovery, 'with_flow_browser', AsyncMock(return_value=None)):
            response = self.client.get('/api/flow/projects', headers=AUTH)
        self.assertEqual((response.status_code, response.json()), (409, {'error': 'auth_required'}))

    def test_media_listing_and_download(self):
        items = flow_import.unique_media([{'kind': 'image', 'src': IMG, 'width': 1376, 'height': 768}])
        key = items[0]['key']
        with patch.object(recovery, 'with_flow_browser', AsyncMock(return_value=(None, items))):
            listed = self.client.get('/api/flow/projects/abcdefgh-1234/media', headers=AUTH).json()
        self.assertEqual(listed, {'media': [{'key': key, 'kind': 'image', 'width': 1376, 'height': 768}]})
        with patch.object(recovery, 'with_flow_browser', AsyncMock(return_value=('image/webp', b'RIFF'))):
            response = self.client.get(f'/api/flow/projects/abcdefgh-1234/media/{key}', headers=AUTH)
        self.assertEqual((response.status_code, response.headers['content-type'], response.content), (200, 'image/webp', b'RIFF'))

    def test_sized_download_uses_flow_menu_and_reports_failed_upscale(self):
        key = flow_import.media_key(IMG)
        with patch.object(recovery, 'with_flow_browser', AsyncMock(return_value=('cat.png', b'PNG'))):
            ok = self.client.get(f'/api/flow/projects/abcdefgh-1234/media/{key}?size=2K', headers=AUTH)
        self.assertEqual((ok.status_code, ok.headers['content-type'], ok.content), (200, 'image/png', b'PNG'))
        with patch.object(recovery, 'with_flow_browser', AsyncMock(return_value='upscale_failed')):
            failed = self.client.get(f'/api/flow/projects/abcdefgh-1234/media/{key}?size=4K', headers=AUTH)
        self.assertEqual((failed.status_code, failed.json()), (409, {'error': 'upscale_failed'}))
        with patch.object(recovery, 'with_flow_browser', AsyncMock()) as browser:
            self.assertEqual(self.client.get(f'/api/flow/projects/abcdefgh-1234/media/{key}?size=8K', headers=AUTH).status_code, 400)
            browser.assert_not_called()


if __name__ == '__main__':
    unittest.main()
