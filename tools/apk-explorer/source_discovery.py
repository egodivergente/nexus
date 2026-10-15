"""Conservative Kotlin source inventory; findings are candidates, never render proof."""
from pathlib import Path
import os
import re
import json
import html

SKIP = {'.git', '.gradle', 'build', 'node_modules', 'sessions', '__pycache__', '.idea', 'outputs', 'dist', 'vendor', 'backups', '.superdesign', 'entregas', '.agents', '.claude'}


def mask(text):
    # Preserve offsets and newlines so evidence links refer to the original source.
    pattern = r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    return re.sub(pattern, lambda m: re.sub(r'[^\n]', ' ', m[0]), text, flags=re.S)


def close_at(code, start, opening='(', closing=')'):
    depth = 0
    for i in range(start, len(code)):
        if code[i] == opening:
            depth += 1
        elif code[i] == closing:
            depth -= 1
            if depth == 0:
                return i
    return start


def discover(project, config=None):
    from source_render import profile_for
    root = Path(project).resolve(strict=True)
    if not root.is_dir():
        raise ValueError('Selecciona una carpeta de proyecto.')
    registered, profile_error = {}, None
    try:
        profile, _, _ = profile_for(root, config)
        for state in profile['states']:
            if state.get('source') and state.get('symbol'):
                registered.setdefault((state['source'], state['symbol']), []).append(state['test'])
    except (OSError, ValueError, KeyError) as exc:
        profile_error = str(exc)
    items, routes, warnings = [], [], []
    files = 0
    for folder, dirs, names in os.walk(root, followlinks=False):
        dirs[:] = sorted(d for d in dirs if d not in SKIP and not (Path(folder)/d).is_symlink())
        for name in sorted(names):
            path = Path(folder)/name
            if path.suffix != '.kt' or path.is_symlink() or '/src/test/' in path.as_posix() or '/src/androidTest/' in path.as_posix():
                continue
            try:
                text = path.read_text(encoding='utf-8-sig')
            except (OSError, UnicodeError) as exc:
                warnings.append({'file': path.relative_to(root).as_posix(), 'error': str(exc)})
                continue
            files += 1
            code = mask(text)
            relative = path.relative_to(root).as_posix()
            for match in re.finditer(r'\bfun\s+(\w+)\s*\(', code):
                name = match[1]
                # Annotation block immediately preceding the declaration (not prior functions).
                boundary = max(code.rfind('}', 0, match.start()), code.rfind('{', 0, match.start()), code.rfind('fun ', 0, match.start()))
                prefix = code[boundary+1:match.start()]
                if not re.search(r'@(?:[\w.]+\.)?Composable\b', prefix):
                    continue
                start = match.end()-1
                end = close_at(code, start)
                params = text[start+1:end].strip()
                body_start = code.find('{', end)
                next_fun = code.find('fun ', end)
                body = code[body_start:close_at(code, body_start, '{', '}')+1] if body_start >= 0 and (next_fun < 0 or body_start < next_fun) else ''
                dependencies = [label for pattern, label in [
                    (r'\bviewModel\b|\b\w*ViewModel\b', 'view_model'),
                    (r'\bLocalContext\b|\bapplicationContext\b', 'android_context'),
                    (r'\bLaunchedEffect\b|\bDisposableEffect\b', 'effects'),
                    (r'\bcollectAsState\b', 'observable_state'),
                    (r'\brememberLauncherForActivityResult\b', 'activity_result')]
                    if re.search(pattern, code[start:end]+body)]
                previews = bool(re.search(r'@(?:[\w.]+\.)?Preview\b', prefix))
                states = registered.get((relative, name), [])
                items.append({'symbol': name, 'file': relative, 'line': text.count('\n', 0, match.start())+1,
                    'kind': 'screen' if name.endswith('Screen') else 'component',
                    'parameters': params, 'preview': previews, 'dependencies': dependencies,
                    'registered_states': states,
                    'readiness': 'registered' if states else 'preview_candidate' if previews else 'fixture_required',
                    'render_verified': False})
            # Literal navigation registrations only; dynamic routes are outside this heuristic.
            for match in re.finditer(r'\b(?:composable|dialog)\s*\(\s*(?:route\s*=\s*)?"([^"\n]+)"', text):
                if code[match.start():match.start()+len('dialog')].strip():
                    routes.append({'route': match[1], 'file': relative, 'line': text.count('\n', 0, match.start())+1})
    return {'schema_version': 1, 'mode': 'source_discovery', 'project': str(root), 'device_used': False,
        'method': 'Static Kotlin heuristics; not a compiler or exhaustive state enumeration. Registered does not mean rendered.',
        'files_scanned': files, 'components': items, 'routes': routes, 'warnings': warnings,
        'profile_error': profile_error, 'summary': {'screens': sum(x['kind']=='screen' for x in items),
            'components': len(items), 'registered': sum(bool(x['registered_states']) for x in items),
            'preview_candidates': sum(x['preview'] for x in items)}}


def write_report(result, output):
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    (output/'discovery.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    rows = []
    for item in sorted(result['components'], key=lambda x: (x['kind'] != 'screen', x['file'], x['line'])):
        link = (Path(result['project'])/item['file']).as_uri()
        rows.append('<tr><td>'+html.escape(item['symbol'])+'</td><td>'+html.escape(item['readiness'])+
            '</td><td>'+html.escape(', '.join(item['dependencies']))+'</td><td><a href="'+html.escape(link, quote=True)+'">'+
            html.escape(item['file'])+':'+str(item['line'])+'</a></td></tr>')
    page = '<!doctype html><meta charset="utf-8"><title>Pantallas detectadas</title><style>body{font:16px system-ui;margin:32px;background:#101522;color:#eee}td,th{padding:12px;text-align:left;border-bottom:1px solid #394150}a{color:#91caff}</style><h1>Pantallas detectadas</h1><p>AnÃ¡lisis estÃ¡tico. Registrado significa que existe un escenario, no que estÃ© renderizado. Las dependencias detectadas ayudan a preparar datos de prueba.</p><p>'+html.escape(str(result['summary']))+'</p><table><tr><th>FunciÃ³n</th><th>PreparaciÃ³n</th><th>Dependencias</th><th>Origen</th></tr>'+''.join(rows)+'</table>'
    (output/'discovery.html').write_text(page, encoding='utf-8')
    return output/'discovery.html'
