"""Read-only provenance for gallery originals across local history and the shared index.

A preview may reuse an original only through explicit output identity. Names,
creation times and prompts never establish that two media files are equivalent.
"""
import hashlib
import json
from pathlib import Path
import threading

from native_operations import resolve_media


class GallerySources:
    def __init__(self, data, read_state):
        self.data = Path(data)
        self.read_state = read_state
        self.lock = threading.RLock()
        self.metadata_key = None
        self.assets = {}
        self.generations = []
        self.historical_generations = {}
        self.cache = {}
        self.hashes = {}

    @staticmethod
    def _stamp(path):
        try:
            stat = path.stat()
            return stat.st_mtime_ns, stat.st_size
        except OSError:
            return None

    @staticmethod
    def _json(path, default):
        try:
            return json.loads(path.read_text('utf-8-sig'))
        except (OSError, ValueError):
            return default

    def _reload(self):
        index_file = self.data / 'assistant' / 'index.json'
        cache_file = self.data / 'remote-media-cache.json'
        sync = self.data / 'assistant' / 'sync'
        configuration_file = sync / 'connection.json'
        configuration = self._json(configuration_file, {})
        folder = configuration.get('folderId') if isinstance(configuration, dict) else None
        journal_file = None
        if isinstance(folder, str) and folder:
            workspace = (sync / 'workspaces' / folder).resolve()
            if workspace.is_relative_to((sync / 'workspaces').resolve()):
                journal_file = workspace / 'journal.json'
        stamp = (self._stamp(index_file), self._stamp(cache_file), self._stamp(configuration_file),
                 self._stamp(journal_file) if journal_file is not None else None)
        if stamp == self.metadata_key:
            return
        index = self._json(index_file, {})
        if not isinstance(index, dict):
            index = {}
        asset_rows = index.get('assets', [])
        generation_rows = index.get('generations', [])
        self.assets = {row['id']: row for row in (asset_rows if isinstance(asset_rows, list) else [])
                       if isinstance(row, dict) and isinstance(row.get('id'), str)}
        self.generations = [row for row in (generation_rows if isinstance(generation_rows, list) else []) if isinstance(row, dict)]
        self.historical_generations = {}
        journal = self._json(journal_file, {}) if journal_file is not None else {}
        revisions = journal.get('revisions') if isinstance(journal, dict) else None
        if isinstance(revisions, dict):
            for revision in revisions.values():
                if not isinstance(revision, dict) or not isinstance(revision.get('key'), str):
                    continue
                row = revision.get('value')
                if (revision['key'].startswith('generations:') and isinstance(row, dict)
                        and isinstance(row.get('id'), str) and revision['key'] == 'generations:' + row['id']):
                    self.historical_generations.setdefault(row['id'], []).append(row)
        cache = self._json(cache_file, {})
        self.cache = cache if isinstance(cache, dict) else {}
        self.metadata_key = stamp

    def _local(self, uri):
        if not isinstance(uri, str) or not uri.startswith('/media/'):
            return None
        try:
            return resolve_media(self.data, uri)
        except (OSError, ValueError, TypeError, AttributeError):
            return None

    def _asset_paths(self, asset):
        if not isinstance(asset, dict):
            return []
        return [path for uri in (asset.get('uri'), self.cache.get(asset.get('id')))
                if (path := self._local(uri)) is not None]

    def _hash(self, path):
        stat = path.stat()
        key = (str(path), stat.st_size, stat.st_mtime_ns)
        if key not in self.hashes:
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(block)
            self.hashes[key] = digest.digest()
        return self.hashes[key]

    def _unambiguous(self, paths):
        identities = {}
        for path in dict.fromkeys(paths):
            try:
                identities.setdefault(self._hash(path), path)
            except OSError:
                continue
            if len(identities) > 1:
                return None
        return next(iter(identities.values()), None)

    @staticmethod
    def _url(output):
        return output.get('url') if isinstance(output, dict) else output if isinstance(output, str) else None

    @staticmethod
    def _compatible(job, generation):
        # A remote provider job ID is meaningful only in that provider/model.
        return all(not job.get(key) or not generation.get(key) or job[key] == generation[key]
                   for key in ('providerId', 'modelId'))

    def _generation_paths(self, generations, count, position):
        paths = []
        for generation in generations:
            identifier = generation.get('id')
            historical = self.historical_generations.get(identifier, []) if isinstance(identifier, str) else []
            variants = [generation] + [row for row in historical if self._compatible(generation, row)
                                      and row.get('nexusJobId') == generation.get('nexusJobId')]
            for variant in variants:
                identifiers = variant.get('outputAssetIds', [])
                if not isinstance(identifiers, list):
                    continue
                # A single output can acquire duplicate asset IDs during device merges.
                # Multi-output unions are mapped only through a coherent historical
                # array of the SAME generation with the exact requested cardinality.
                # All available variants must agree on bytes at that ordinal.
                if count == 1:
                    selected = identifiers
                elif len(identifiers) == count:
                    selected = [identifiers[position]]
                else:
                    continue
                for identifier in selected:
                    if isinstance(identifier, str):
                        paths.extend(self._asset_paths(self.assets.get(identifier)))
        return paths

    def resolve(self, job_id, index=0):
        """Return (historical job, its unchanged output URL, proven local original)."""
        if not isinstance(job_id, str):
            raise ValueError('Identificador de creación no válido.')
        if not isinstance(index, int) or isinstance(index, bool) or index < 0:
            raise ValueError('Índice de resultado no válido.')
        state = self.read_state()
        jobs = state.get('jobs', []) if isinstance(state, dict) else []
        if not isinstance(jobs, list):
            jobs = []
        job = next((row for row in jobs if isinstance(row, dict) and row.get('id') == job_id), None)
        if job is None:
            raise ValueError('La creación no está disponible en el historial.')
        outputs = job.get('outputs', [])
        if not isinstance(outputs, list) or index >= len(outputs):
            raise ValueError('El resultado no está disponible en esta creación.')
        original = self._url(outputs[index])
        if not isinstance(original, str) or not original:
            raise ValueError('El resultado no contiene un archivo válido.')
        direct = self._local(original)
        if direct is not None:
            return job, original, direct
        with self.lock:
            self._reload()
            exact = [path for asset in self.assets.values() if asset.get('uri') == original
                     for path in self._asset_paths(asset)]
            if exact:
                return job, original, self._unambiguous(exact)
            identity = {job_id}
            source = next((row for row in jobs if isinstance(row, dict) and row.get('id') == job.get('sourceJobId')), None)
            # Recovery may point to a previous job; an edited child must never show
            # its parent's media unless that very output URI is retained.
            if source and isinstance(source.get('outputs'), list) and index < len(source['outputs']) and self._url(source['outputs'][index]) == original:
                identity.add(source['id'])
            linked = [generation for generation in self.generations if self._compatible(job, generation)
                      and (isinstance(generation.get('nexusJobId'), str) and generation['nexusJobId'] in identity
                           or isinstance(generation.get('id'), str) and generation['id'] in identity)]
            candidates = self._generation_paths(linked, len(outputs), index)
            if candidates:
                return job, original, self._unambiguous(candidates)
            remote_id = job.get('remoteId')
            linked = [generation for generation in self.generations if remote_id and self._compatible(job, generation)
                      and generation.get('providerJobId') == remote_id
                      and generation.get('providerId') == job.get('providerId')]
            candidates = self._generation_paths(linked, len(outputs), index)
            return job, original, self._unambiguous(candidates)
