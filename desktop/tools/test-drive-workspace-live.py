"""Explicit real-account acceptance in isolated profiles and a recoverable Drive folder.

Run with --live after connecting Desktop. Never sends a generation request.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import urllib.request
import uuid
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from agent_host import AgentHost
from corte_host import CorteHost
from drive_sync import DriveSync
from local_host import windows_keys, write_secret
from native_operations import NativeOperations


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', action='store_true')
    args = parser.parse_args()
    if not args.live:
        parser.error('Explicit --live is required; this creates an isolated test folder in Drive.')
    profile = Path(os.environ['LOCALAPPDATA']) / 'NexusDesktop'
    setup = type('PrivateConnection', (), {'data': profile / 'assistant', 'read_secret': staticmethod(windows_keys), 'write_secret': staticmethod(write_secret)})()
    connection = DriveSync(setup, start_scheduler=False)
    proof = json.loads((profile / 'sync-verification/result.json').read_text('utf-8'))
    assert proof['result'] == 'PASS'

    def drive(method, suffix, body):
        request = urllib.request.Request('https://www.googleapis.com/drive/v3/files' + suffix,
            data=json.dumps(body).encode(), method=method,
            headers={'Authorization': 'Bearer ' + connection.token(), 'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.load(response)

    folder = drive('POST', '?fields=id', {'name': 'Nexus workspace verification ' + uuid.uuid4().hex,
        'mimeType': 'application/vnd.google-apps.folder', 'parents': [proof['rootId']]})['id']
    hosts = []
    temporary = tempfile.TemporaryDirectory(prefix='nexus-drive-workspace-')
    try:
        desktop = Path(__file__).resolve().parents[1]
        for name in ['source', 'target']:
            data = Path(temporary.name) / name
            host = AgentHost(desktop, data, windows_keys, write_secret)
            host.native = NativeOperations(data, host, lambda: {}, lambda _: None, None, lambda: {})
            host.corte = CorteHost(desktop, data, host.native)
            host.drive_sync = DriveSync(host, start_scheduler=False)
            host.drive_sync.token = connection.token
            host.drive_sync.config['folderId'] = folder
            host.command({'op': 'snapshot', 'owner': 'sync-verification'})
            hosts.append(host)
        source, target = hosts
        project = source.command({'op': 'createProject', 'owner': 'sync-verification', 'args': {'name': 'Sync verification'}})['result']['projectId']
        image = source.data.parent / 'media' / ('a' * 32 + '.png')
        image.parent.mkdir(parents=True, exist_ok=True)
        Image.new('RGB', (64, 64), '#52cba6').save(image)
        index = source.command({'op': 'importReferences', 'owner': 'sync-verification', 'args': {'projectId': project,
            'files': [{'uri': '/media/' + image.name, 'mime': 'image/png', 'name': 'Sync original'}]}})['result']['index']
        asset = index['assets'][0]
        source.corte.run({'method': 'create_project', 'project_id': project})
        before = source.corte.run({'method': 'import_media', 'project_id': project, 'expected_revision': 0,
            'request_id': 'place', 'asset_ids': [asset['id']]}, [asset])

        def synchronize(host):
            host.drive_sync.run()
            deadline = time.monotonic() + 120
            while host.drive_sync.lock.locked() and time.monotonic() < deadline:
                time.sleep(.1)
            assert not host.drive_sync.lock.locked(), 'Transfer did not complete within the verification window'
            assert host.drive_sync.status['state'] == 'synced', host.drive_sync.public()

        synchronize(source)
        synchronize(target)
        after = target.command({'op': 'snapshot', 'owner': 'sync-verification'})['result']['index']
        assert after['projects'][0]['id'] == project
        downloaded = target.data.parent / after['assets'][0]['uri'].lstrip('/')
        assert hashlib.file_digest(image.open('rb'), 'sha256').hexdigest() == hashlib.file_digest(downloaded.open('rb'), 'sha256').hexdigest()
        edit = target.corte.run({'method': 'read_project', 'project_id': project})
        assert not edit['missing_media']
        assert before['project']['timeline'] == edit['project']['timeline']
        assert before['undo_id'] == edit['undo_id']
        synchronize(target)
        synchronize(source)
        assert not source.drive_sync.public()['conflicts']
        print('PASS: real Drive project, original PNG, Corte timeline, undo history and idempotent second cycle across isolated Desktop profiles')
    finally:
        for host in hosts:
            host.close()
            host.corte.close()
            host.native.executor.shutdown()
        drive('PATCH', '/' + folder, {'trashed': True})
        temporary.cleanup()


if __name__ == '__main__':
    main()
