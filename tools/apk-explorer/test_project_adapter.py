from pathlib import Path
import contextlib
import io
import json
import tempfile
import unittest
from unittest.mock import patch
import project_adapter as a
import source_render as r
import explorer as e


class ProjectAdapterTests(unittest.TestCase):
    def project(self, base, name='First', module='mobile', groovy=False):
        root = Path(base)/name
        source = root/module/'src/main/java/demo'
        source.mkdir(parents=True)
        (root/'settings.gradle.kts').write_text('include(":'+module+'")')
        (root/module/('build.gradle' if groovy else 'build.gradle.kts')).write_text(
            'plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }\nandroid { namespace = "demo.app" }')
        (source/'Ui.kt').write_text('''package demo
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
@Preview
@Composable fun WelcomePreview() { }
@Preview
@Composable private fun HiddenPreview() { }
@Preview
@Composable fun ParameterPreview(text: String) { }
@Composable fun RealScreen(vm: ScreenViewModel) { }
class Holder { @Preview @Composable fun MemberPreview() { } }
''')
        layouts = root/module/'src/main/res/layout'
        layouts.mkdir(parents=True)
        (layouts/'welcome.xml').write_text('<TextView xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Welcome"/>')
        (layouts/'merge.xml').write_text('<merge/>')
        return root

    def test_unrelated_projects_generate_isolated_profiles(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name, module in [('Weather','weather'), ('Notes','mobile')]:
                root = self.project(tmp,name,module)
                result = a.plan_project(root,Path(tmp)/(name+'-plan'))
                self.assertEqual(result['status'],'prepared')
                profile = json.loads(Path(result['profiles'][0]['profile']).read_text())
                self.assertEqual(profile['module'], module)
                self.assertEqual(len(profile['states']),2)
                self.assertEqual({s['scope'] for s in profile['states']}, {'preview','layout'})
                self.assertEqual(len(result['deferred']),5)
                fixture = Path(result['profiles'][0]['profile']).with_name('GeneratedRenderTest.kt').read_text()
                self.assertIn('demo.WelcomePreview()',fixture)
                self.assertIn('demo.app.R.layout.welcome',fixture)
                self.assertNotIn('nexus',fixture.lower())
                self.assertFalse((root/'apk-explorer.render.json').exists())

    def test_prepare_automatically_uses_sources_without_nexus_profile(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.project(tmp)
            before = (root/'mobile/build.gradle.kts').read_bytes()
            profile, work, _, _ = r.prepare(root,Path(tmp)/'render')
            self.assertEqual(profile['test_class'],'explorer.generated.GeneratedRenderTest')
            self.assertTrue((work/'mobile/src/explorerRender/GeneratedRenderTest.kt').is_file())
            self.assertEqual((root/'mobile/build.gradle.kts').read_bytes(),before)

    def test_groovy_build_injection_uses_groovy_syntax(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.project(tmp,groovy=True)
            _, work, _, _ = r.prepare(root,Path(tmp)/'render')
            build = (work/'mobile/build.gradle').read_text()
            self.assertIn("id 'app.cash.paparazzi'",build)
            self.assertNotIn('tasks.withType<',build)

    def test_other_framework_is_not_reported_as_rendered(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/'flutter'
            root.mkdir()
            (root/'pubspec.yaml').write_text('name: other_app')
            report=a.plan_project(root,Path(tmp)/'plan')
            self.assertEqual(report['frameworks'],['flutter'])
            self.assertEqual(report['status'],'needs_adapter')
            self.assertFalse(report['profiles'])

    def test_apk_routes_to_existing_runtime_explorer_without_using_device(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'Private.apk'
            apk.write_bytes(b'PK')
            result=a.plan_project(apk,Path(tmp)/'plan')
            self.assertEqual(result['status'],'apk_runtime')
            self.assertEqual(result['next_command'],['--inspect',str(apk),'--json'])
            self.assertFalse(result['device_used'])

    def test_github_uses_authenticated_cli_and_records_commit(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(e,'executable',return_value='gh'), patch.object(e,'command') as command:
            def run(args, **kwargs):
                if args[0]=='gh':
                    Path(args[4]).mkdir()
                    return ''
                return 'a'*40
            command.side_effect=run
            root, origin=a.resolve_input('https://github.com/example/private',Path(tmp)/'out','main')
            self.assertTrue(root.is_dir())
            self.assertEqual(origin['commit'],'a'*40)
            self.assertIn('example/private',command.call_args_list[0].args[0])
            with self.assertRaises(ValueError):
                a.resolve_input('https://other.invalid/repo',Path(tmp)/'out2')

    def test_cli_plan_returns_machine_readable_result_without_build(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=self.project(tmp)
            with patch.object(r,'render_source',side_effect=AssertionError('no build')), contextlib.redirect_stdout(io.StringIO()) as stdout:
                e.main(['--project',str(root),'--output',str(Path(tmp)/'out'),'--json'])
            result=json.loads(stdout.getvalue())
            self.assertEqual(result['status'],'prepared')

    def test_apk_run_preserves_selected_device_and_limits(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk=Path(tmp)/'example.apk'
            apk.write_bytes(b'PK')
            run=e.main
            with patch.object(e,'main',return_value='delegated') as inspect:
                result=run(['--project',str(apk),'--project-run','--serial','selected-phone','--minutes','1','--output',str(Path(tmp)/'out')])
            self.assertEqual(result,'delegated')
            args=inspect.call_args.args[0]
            self.assertEqual(args[args.index('--serial')+1],'selected-phone')
            self.assertEqual(args[args.index('--minutes')+1],'1.0')

    def test_multiple_modules_are_not_silently_reduced_to_one(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=self.project(tmp,module='mobile')
            other=self.project(tmp,name='Dependency',module='feature')
            import shutil
            shutil.copytree(other/'feature',root/'feature')
            with self.assertRaisesRegex(ValueError,'perfil único'):
                a.automatic_profile(root,Path(tmp)/'plan')

    def test_nexus_profiles_are_never_an_implicit_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=self.project(tmp)
            with self.assertRaises(FileNotFoundError):
                r.profile_for(root)

    def test_simple_screen_without_preview_can_be_prepared(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=self.project(tmp)
            source=root/'mobile/src/main/java/demo/Ui.kt'
            source.write_text('package demo\n@Composable fun HomeScreen() { Text("hello") }')
            result=a.plan_project(root,Path(tmp)/'plan')
            profile=json.loads(Path(result['profiles'][0]['profile']).read_text())
            screen=next(s for s in profile['states'] if s['symbol']=='HomeScreen')
            self.assertEqual(screen['scope'],'screen')

    def test_project_supplied_profile_takes_precedence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=self.project(tmp)
            generated=a.plan_project(root,Path(tmp)/'generated')['profiles'][0]['profile']
            import shutil
            shutil.copy2(generated,root/'apk-explorer.render.json')
            shutil.copy2(Path(generated).with_name('GeneratedRenderTest.kt'),root/'GeneratedRenderTest.kt')
            result=a.plan_project(root,Path(tmp)/'plan')
            self.assertEqual(result['profiles'][0]['profile'],str(root/'apk-explorer.render.json'))
            self.assertEqual(result['frameworks'],['configured_android'])


if __name__=='__main__':
    unittest.main()
