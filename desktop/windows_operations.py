"""Windows tools exposed only by the desktop agent, under its existing mode controls."""
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

OPERATIONS = {'list_windows_files', 'import_windows_files', 'open_windows_item', 'run_windows_command'}


def run(native, operation, args, payload):
    if operation == 'list_windows_files':
        directory = Path(args.get('directory') or Path.home()).expanduser().resolve(strict=True)
        if not directory.is_dir():
            raise ValueError('La ruta no es una carpeta.')
        query = str(args.get('query', '')).casefold()
        offset = max(0, int(args.get('offset', 0)))
        limit = max(1, min(200, int(args.get('limit', 50))))
        # Do not recursively crawl disks or load file contents to locate an import.
        entries, matched = [], 0
        with os.scandir(directory) as iterator:
            for item in iterator:
                if query not in item.name.casefold():
                    continue
                if offset <= matched < offset + limit:
                    info = item.stat(follow_symlinks=False)
                    entries.append({'name': item.name, 'path': item.path, 'directory': item.is_dir(),
                                    'size_bytes': info.st_size, 'modified_ms': round(info.st_mtime * 1000)})
                matched += 1
        return {'directory': str(directory), 'entries': entries, 'total': matched,
                'next_offset': offset + limit if offset + limit < matched else None}
    if operation == 'import_windows_files':
        from media_import import MediaImporter
        paths = args.get('paths', [])
        if not isinstance(paths, list) or not 1 <= len(paths) <= 20 or any(not isinstance(p, str) for p in paths):
            raise ValueError('Indica entre 1 y 20 rutas locales existentes.')
        project_id = args.get('project_id') or payload.get('projectId')
        if project_id:
            import json
            index = native.data / 'assistant/index.json'
            projects = json.loads(index.read_text('utf-8')).get('projects', []) if index.exists() else []
            if not any(p['id'] == project_id and not p.get('archived') for p in projects):
                raise ValueError('Proyecto de destino inexistente. Usa list_projects o create_project.')
        importer = MediaImporter(native.media)
        # Validate all sources before starting; every completed copy is registered even if a later one fails.
        from media_import import validate
        for path in paths:
            source = Path(path).expanduser().resolve(strict=True)
            validate(source.name, source.stat().st_size)
        result, errors = [], []
        for path in paths:
            try:
                item = importer.copy(path)
                kind = {'image': 'IMAGE', 'video': 'VIDEO', 'audio': 'AUDIO'}.get(item['mime'].split('/')[0], 'OTHER')
                result.append({'id': 'asset_' + uuid.uuid4().hex, 'uri': item['url'], 'displayName': item['name'],
                               'mimeType': item['mime'], 'sizeBytes': item['size'], 'type': kind,
                               'source': 'IMPORTED', 'role': 'REFERENCE', 'createdAt': int(time.time()*1000),
                               'projectId': project_id})
            except (OSError, ValueError) as error:
                errors.append({'name': Path(path).name, 'error': str(error)})
        return {'imported': result, 'errors': errors, '_assets': result, 'originals_preserved': True}
    if operation == 'open_windows_item':
        target = str(args.get('target', '')).strip()
        if target.startswith(('https://', 'http://')):
            import webbrowser
            opened = webbrowser.open(target)
            return {'requested': opened, 'target': target, 'visible_state_verified': False}
        path = Path(target).expanduser().resolve(strict=True)
        os.startfile(str(path))
        return {'requested': True, 'target': str(path), 'visible_state_verified': False}
    if operation == 'run_windows_command':
        command = args.get('command')
        if not isinstance(command, str) or not command.strip() or len(command) > 32000:
            raise ValueError('Indica el comando PowerShell solicitado por el usuario.')
        directory = Path(args.get('directory') or Path.home()).expanduser().resolve(strict=True)
        if not directory.is_dir():
            raise ValueError('La carpeta de trabajo no existe.')
        timeout = max(1, min(120, int(args.get('timeout_seconds', 30))))
        # Spool output to disk so a verbose command cannot exhaust RAM. Never log the command.
        with tempfile.TemporaryFile() as output, tempfile.TemporaryFile() as errors:
            process = subprocess.Popen(['powershell.exe', '-NoLogo', '-NoProfile', '-NonInteractive',
                                        '-Command', "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;\n" + command],
                                       cwd=directory, stdin=subprocess.DEVNULL, stdout=output, stderr=errors,
                                       creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            timed_out = False
            try:
                process.wait(timeout)
            except subprocess.TimeoutExpired:
                timed_out = True
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True,
                               creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0), timeout=10)
                process.wait(10)
            def read(stream):
                size = stream.tell()
                stream.seek(max(0, size - 24000))
                return stream.read(24000).decode('utf-8', errors='replace'), size > 24000
            stdout, out_cut = read(output)
            stderr, err_cut = read(errors)
        return {'exit_code': process.returncode, 'timed_out': timed_out, 'stdout': stdout,
                'stderr': stderr, 'truncated': out_cut or err_cut, 'directory': str(directory)}
    raise ValueError('Operación de Windows desconocida.')
