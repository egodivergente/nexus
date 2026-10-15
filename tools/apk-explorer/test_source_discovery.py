import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import source_discovery as d
import explorer


class DiscoveryTests(unittest.TestCase):
    def test_annotations_comments_routes_and_dependencies(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root/'Ui.kt').write_text('''
// @Composable fun FakeScreen() {}
val example = "@Composable fun StringScreen() {}"
@Preview(name = "Example")
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
  val state = vm.state.collectAsState()
  LaunchedEffect(Unit) { }
}
@Composable fun Badge(text: String) { Text(text) }
fun routes() { composable("home") { HomeScreen() } }
// composable("fake") {}
''')
            (root/'build').mkdir()
            (root/'build/Generated.kt').write_text('@Composable fun GeneratedScreen() {}')
            (root/'outputs').mkdir()
            (root/'outputs/Old.kt').write_text('@Composable fun OldScreen() {}')
            report = d.discover(root)
            self.assertEqual([x['symbol'] for x in report['components']], ['HomeScreen', 'Badge'])
            home = report['components'][0]
            self.assertEqual(home['readiness'], 'preview_candidate')
            self.assertEqual(home['dependencies'], ['view_model', 'effects', 'observable_state'])
            self.assertFalse(home['render_verified'])
            self.assertEqual([r['route'] for r in report['routes']], ['home'])
            self.assertEqual(home['line'], 6)

    def test_registration_is_by_file_and_symbol_not_name_only(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name in ['A.kt','B.kt']:
                (root/name).write_text('@Composable fun Screen() {}')
            profile = {'states': [{'source':'A.kt', 'symbol':'Screen', 'test':'screenDark'}]}
            with patch('source_render.profile_for', return_value=(profile, None, None)):
                result = d.discover(root)
            self.assertEqual(result['components'][0]['registered_states'], ['screenDark'])
            self.assertEqual(result['components'][1]['registered_states'], [])
            self.assertFalse(result['components'][0]['render_verified'])
            page = d.write_report(result, root/'report')
            self.assertTrue(page.is_file())
            self.assertEqual(json.loads((page.parent/'discovery.json').read_text())['summary']['registered'],1)

    def test_cli_discovery_never_uses_adb_or_build(self):
        import contextlib
        import io
        with tempfile.TemporaryDirectory() as folder, patch('source_render.render_source', side_effect=AssertionError('build')), patch.object(explorer, 'Adb', side_effect=AssertionError('adb'), create=True):
            with contextlib.redirect_stdout(io.StringIO()) as stream:
                explorer.main(['--discover', folder, '--output', str(Path(folder)/'out'), '--json'])
            self.assertEqual(json.loads(stream.getvalue())['mode'], 'source_discovery')


if __name__ == '__main__':
    unittest.main()
