"""Windows filesystem integration checks; no browser sessions or provider calls."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('flow_router', Path(__file__).with_name('flow-download-router.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RoutingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='flow-router-qa-')
        self.root = Path(self.temp.name)
        self.source = self.root / 'downloads'
        self.target = self.root / 'bridge'
        self.source.mkdir()
        self.target.mkdir()
        self.router = module.Router([self.source], self.target, self.root / 'state.json')

    def tearDown(self):
        self.temp.cleanup()

    def media(self, name='scene.png', origin='https://flow.google.com/'):
        path = self.source / name
        path.write_bytes(b'owned media bytes')
        Path(str(path) + ':Zone.Identifier').write_text(
            '[ZoneTransfer]\nZoneId=3\nReferrerUrl=' + origin + '\n', encoding='utf-8')
        return path

    def settle(self):
        self.router.scan(now=0)
        return self.router.scan(now=5)

    def test_moves_new_flow_media_and_preserves_bytes(self):
        source = self.media()
        expected = source.read_bytes()
        result = self.settle()
        self.assertEqual([self.target / source.name], result)
        self.assertFalse(source.exists())
        self.assertEqual(expected, result[0].read_bytes())

    def test_old_downloads_are_left_in_place_across_restart(self):
        old = self.media()
        state = self.root / 'baseline.json'
        self.router = module.Router([self.source], self.target, state)
        self.assertEqual([], self.settle())
        self.router = module.Router([self.source], self.target, state)
        self.assertEqual([], self.settle())
        self.assertTrue(old.exists())

    def test_other_sites_and_flow_named_host_impostor_are_untouched(self):
        a = self.media('normal.jpg', 'https://drive.google.com/')
        b = self.media('impostor.mp4', 'https://flow.google.com.example.net/')
        c = self.media('webpage.html')
        self.assertEqual([], self.settle())
        self.assertTrue(all(p.exists() for p in [a, b, c]))

    def test_only_real_https_flow_origins_and_legacy_paths_are_accepted(self):
        for url in ['https://flow.google.com/', 'blob:https://flow.google.com/id',
                    'https://labs.google/fx/es/tools/flow/project/id']:
            self.assertTrue(module.flow_url(url), url)
        for url in ['http://flow.google.com/', 'https://flow.google.com.evil.test/',
                    'https://labs.google/fx/tools/flow-other', 'https://labs.google/fx/tools/imagefx']:
            self.assertFalse(module.flow_url(url), url)

    def test_a_collision_never_replaces_the_existing_file(self):
        existing = self.target / 'scene.png'
        existing.write_bytes(b'existing user picture')
        source = self.media()
        result = self.settle()
        self.assertEqual([self.target / 'scene (2).png'], result)
        self.assertEqual(b'existing user picture', existing.read_bytes())
        self.assertFalse(source.exists())

    def test_partial_sibling_and_changed_size_wait_for_completion(self):
        source = self.media()
        partial = Path(str(source) + '.crdownload')
        partial.write_bytes(b'partial')
        self.assertEqual([], self.settle())
        partial.unlink()
        with source.open('ab') as writer:
            writer.write(b' completed media content')
        self.assertEqual([], self.router.scan(now=6))
        self.assertEqual([], self.router.scan(now=8))
        self.assertEqual([self.target / source.name], self.router.scan(now=11))

    def test_a_file_open_in_another_process_is_not_moved(self):
        source = self.media()
        with source.open('rb'):
            self.assertEqual([], self.settle())
            self.assertTrue(source.exists())
        self.assertEqual([self.target / source.name], self.router.scan(now=6))


if __name__ == '__main__':
    unittest.main()
