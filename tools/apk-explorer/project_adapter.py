"""Project-neutral detection and reproducible Paparazzi fixture generation."""
from pathlib import Path
import hashlib
import html
import json
import os
import re
import xml.etree.ElementTree as ET
from source_discovery import discover, SKIP, write_report


def project_files(root):
    for folder, dirs, files in os.walk(root, followlinks=False):
        dirs[:] = sorted(d for d in dirs if d not in SKIP and not (Path(folder)/d).is_symlink())
        for name in sorted(files):
            path = Path(folder)/name
            if not path.is_symlink():
                yield path


def resolve_input(value, output, ref=''):
    """Use existing gh authentication; never embed credentials or modify the checkout."""
    path = Path(value).expanduser()
    if path.exists():
        return path.resolve(), None
    from explorer import GitHub, command
    github = GitHub(value)
    if ref.startswith('-') or any(c in ref for c in '\r\n'):
        raise ValueError('Referencia Git inválida.')
    destination = Path(output).resolve()/'checkout'
    if destination.exists():
        raise ValueError('La carpeta de descarga ya existe; utiliza una salida nueva.')
    destination.parent.mkdir(parents=True, exist_ok=True)
    args = [github.gh, 'repo', 'clone', github.repo, str(destination), '--', '--depth', '1']
    if ref:
        args += ['--branch', ref]
    command(args, timeout=300)
    sha = command(['git', '-C', str(destination), 'rev-parse', 'HEAD']).strip()
    if isinstance(sha, bytes):
        sha = sha.decode('ascii')
    origin = {'repository': 'https://github.com/'+github.repo, 'commit': sha, 'ref': ref or 'default branch'}
    (destination.parent/'repository.json').write_text(json.dumps(origin, indent=2), encoding='utf-8')
    return destination, origin


def plan_project(project, output):
    root, output = Path(project).resolve(strict=True), Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    if root.is_file():
        if root.suffix.lower() != '.apk':
            raise ValueError('La entrada debe ser una APK, una carpeta o un repositorio GitHub.')
        result = {'mode': 'project_plan', 'status': 'apk_runtime', 'input': str(root),
                  'frameworks': ['apk'], 'profiles': [], 'device_used': False,
                  'next_command': ['--inspect', str(root), '--json'],
                  'note': 'Una APK sola no proporciona los escenarios del código fuente.'}
    else:
        result = plan_sources(root, output)
    write_plan_report(result, output)
    return result


def write_plan_report(result, output):
    output = Path(output)
    root = Path(result.get('project', result.get('input', '.')))
    (output/'project.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    cards = []
    for item in result['profiles']:
        profile = Path(item['profile'])
        command = f'python explorer.py --render "{root}" --render-config "{profile}" --json'
        cards.append('<li>'+html.escape(item['module'])+' · '+str(item['states'])+' escenarios preparados<pre>'+html.escape(command)+'</pre></li>')
    details = ''.join('<li>'+html.escape(str(item))+'</li>' for item in result.get('deferred', []))
    galleries = ''.join('<li><a href="'+html.escape(Path(item['gallery']).as_uri(), quote=True)+'">'+
                        html.escape(item['status'])+' · '+str(len(item['states']))+' imágenes</a></li>' for item in result.get('renders', []))
    (output/'project.html').write_text('<!doctype html><meta charset="utf-8"><title>Proyecto visual</title><style>body{font:16px system-ui;margin:32px;background:#111827;color:#eee}a{color:#9cf}pre{white-space:pre-wrap}</style><h1>Preparación visual</h1><p>'+html.escape(', '.join(result['frameworks']))+' · '+html.escape(result['status'])+'</p><p>'+html.escape(result['note'])+'</p><a href="project.json">Informe JSON</a><ul>'+galleries+''.join(cards)+'</ul><h2>Escenarios pendientes de adaptar</h2><ul>'+details+'</ul>', encoding='utf-8')


def plan_sources(root, output):
    if (root/'apk-explorer.render.json').is_file():
        from source_render import profile_for
        profile, path, _ = profile_for(root)
        write_report(discover(root), output)
        return {'schema_version': 1, 'mode': 'project_plan', 'project': str(root),
                'frameworks': ['configured_android'], 'status': 'prepared',
                'profiles': [{'module': profile['module'], 'profile': str(path), 'states': len(profile['states']), 'verified': False}],
                'deferred': [], 'device_used': False, 'discovery': str(output/'discovery.json'),
                'note': 'Using the explicit profile supplied by this project. Prepared is not rendered.'}
    inventory = discover(root, load_profile=False)
    write_report(inventory, output)
    paths = list(project_files(root))
    builds = [p for p in paths if p.name in {'build.gradle.kts', 'build.gradle'}]
    frameworks, profiles, deferred = [], [], []
    if (root/'pubspec.yaml').exists():
        frameworks.append('flutter')
    if (root/'package.json').exists():
        try:
            package = json.loads((root/'package.json').read_text(encoding='utf-8'))
            dependencies = {**package.get('dependencies', {}), **package.get('devDependencies', {})}
            frameworks.append('react_native' if 'react-native' in dependencies else 'web_javascript')
        except (ValueError, TypeError):
            deferred.append({'reason': 'package.json could not be parsed'})
    if inventory['components']:
        frameworks.append('android_compose')
    # Explicit build/source inputs, including local module dependencies. No project-wide copy.
    inputs = []
    for name in ['settings.gradle.kts', 'settings.gradle', 'build.gradle.kts', 'build.gradle', 'gradlew', 'gradlew.bat', 'gradle']:
        if (root/name).exists():
            inputs.append(name)
    for build in builds:
        relative = build.relative_to(root).as_posix()
        if relative not in inputs:
            inputs.append(relative)
        src = build.parent/'src/main'
        if src.is_dir():
            inputs.append(src.relative_to(root).as_posix())
    for build in builds:
        text = build.read_text(encoding='utf-8')
        if not re.search(r'com\.android\.(application|library)|android[.-](application|library)|\bnamespace\b', text):
            continue
        if not (build.parent / 'src/main').is_dir():
            continue
        if 'android_native' not in frameworks:
            frameworks.append('android_native')
        module = build.parent.relative_to(root).as_posix()
        if module == '.':
            deferred.append({'module': module, 'reason': 'Root Android module requires an explicit profile'})
            continue
        if not re.fullmatch(r'[A-Za-z0-9_/-]+', module):
            deferred.append({'module': module, 'reason': 'Module path needs an explicit profile'})
            continue
        if re.search(r'productFlavors|projectDir|includeBuild', text + ''.join((root/n).read_text(encoding='utf-8') for n in ['settings.gradle.kts','settings.gradle'] if (root/n).exists())):
            deferred.append({'module': module, 'reason': 'Custom Gradle variants or project mapping requires an explicit profile'})
            continue
        if 'app.cash.paparazzi' in text:
            deferred.append({'module': module, 'reason': 'Existing Paparazzi setup: use its own snapshot tasks'})
            continue
        if not re.search(r'kotlin|org\.jetbrains', text):
            deferred.append({'module': module, 'reason': 'Automatic fixture generation currently requires a Kotlin Android module'})
            continue
        states, calls = [], []
        components = [c for c in inventory['components'] if c['file'].startswith(module+'/src/main/')]
        for c in components:
            reason = None
            if not c['preview'] and not (c['kind'] == 'screen' and not c['parameters'] and not c['dependencies']):
                reason = 'Needs a preview or a fixture with representative data'
            elif not c['top_level'] or c['visibility'] == 'private' or c['parameters']:
                reason = 'Automatic previews must be accessible top-level functions with no parameters'
            elif not re.fullmatch(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*', c['package']):
                reason = 'Package name requires an explicit fixture'
            if reason:
                deferred.append({'file': c['file'], 'symbol': c['symbol'], 'reason': reason})
                continue
            test = 'preview_'+hashlib.sha256((c['file']+c['symbol']).encode()).hexdigest()[:12]
            states.append({'test': test, 'title': c['symbol'], 'scope': 'preview' if c['preview'] else 'screen', 'source': c['file'], 'symbol': c['symbol'],
                           'data': 'Source function defaults; preview annotation device/theme variants are not reproduced'})
            calls.append(f'    @Test fun {test}() {{ paparazzi.snapshot {{ {c["package"]}.{c["symbol"]}() }} }}')
        namespace = re.search(r'\bnamespace\s*(?:=\s*)?["\']([\w.]+)["\']', text)
        for layout in sorted((build.parent/'src/main/res/layout').glob('*.xml')):
            if layout.is_symlink():
                continue
            if not namespace or not re.fullmatch(r'[a-z][a-z0-9_]*', layout.stem):
                continue
            try:
                node = ET.parse(layout).getroot()
                if node.tag in {'layout', 'merge'} or any(n.tag in {'fragment', 'androidx.fragment.app.FragmentContainerView'} for n in node.iter()):
                    raise ValueError('Data binding, merge or fragments require a fixture')
            except (ET.ParseError, ValueError) as exc:
                deferred.append({'file': layout.relative_to(root).as_posix(), 'reason': str(exc)})
                continue
            if 'android_views' not in frameworks:
                frameworks.append('android_views')
            test = 'layout_'+layout.stem
            states.append({'test': test, 'title': layout.stem, 'scope': 'layout', 'source': layout.relative_to(root).as_posix(),
                           'symbol': 'R.layout.'+layout.stem, 'data': 'XML defaults; runtime data and activity chrome not included'})
            calls.append(f'    @Test fun {test}() {{ paparazzi.snapshot(paparazzi.inflate<android.view.View>({namespace[1]}.R.layout.{layout.stem})) }}')
        if not states:
            continue
        folder = output/('profile_'+module.replace('/', '_'))
        folder.mkdir()
        fixture = 'GeneratedRenderTest.kt'
        (folder/fixture).write_text('package explorer.generated\nimport app.cash.paparazzi.Paparazzi\nimport org.junit.Rule\nimport org.junit.Test\nclass GeneratedRenderTest {\n    @get:Rule val paparazzi = Paparazzi()\n'+'\n'.join(calls)+'\n}\n', encoding='utf-8')
        test_variant = re.search(r'\btestBuildType\s*(?:=\s*)?["\']([A-Za-z][A-Za-z0-9]*)["\']', text)
        variant = test_variant[1] if test_variant else 'debug'
        profile = {'schema_version': 1, 'engine': 'paparazzi', 'plugin_version': '1.3.5', 'module': module.replace('/', ':'),
                   'variant': variant[0].upper()+variant[1:], 'fixture': fixture, 'test_class': 'explorer.generated.GeneratedRenderTest',
                   'inputs': sorted(set(inputs)), 'requires': [build.relative_to(root).as_posix()], 'states': states,
                   'render_notes': ['Automatically generated candidates, not verified screenshots. Build compatibility is checked when rendering.',
                                    'Zero-argument accessible top-level previews, simple screens without detected dependencies, and basic XML layouts. Custom data, preview parameters and annotation variants need a profile.']}
        profile_path = folder/'render.json'
        profile_path.write_text(json.dumps(profile, ensure_ascii=False, indent=2), encoding='utf-8')
        profiles.append({'module': module, 'profile': str(profile_path), 'states': len(states), 'verified': False})
    return {'schema_version': 1, 'mode': 'project_plan', 'project': str(root), 'frameworks': frameworks or ['unknown'],
            'status': 'prepared' if profiles else 'needs_adapter', 'profiles': profiles, 'deferred': deferred,
            'device_used': False, 'discovery': str(output/'discovery.json'),
            'note': 'Prepared is not rendered. Other frameworks can be explored through an APK; source rendering needs a framework-specific adapter.'}


def automatic_profile(project, output):
    result = plan_project(project, output)
    profiles = result['profiles']
    if len(profiles) != 1:
        raise ValueError('No se puede elegir un perfil único automáticamente. Consulta '+str(Path(output)/'project.json')+
                         ' y selecciona --render-config; los escenarios pendientes necesitan datos o un adaptador.')
    return Path(profiles[0]['profile'])
