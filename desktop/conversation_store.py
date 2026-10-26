"""Archived assistant conversations with a metadata index and separately stored custom titles.

Listing decodes only conversation files whose size or modification time changed since the last
listing. Custom titles never rewrite a conversation file, so originals (including Android ones)
keep their format.
"""
import json
import os
from pathlib import Path
import re
import shutil
import threading
import time
import uuid

INDEX_VERSION = 2
TITLE_LIMIT = 80
PREVIEW_LIMIT = 110
SEARCH_LIMIT = 4000
IDENTIFIER = re.compile('[A-Za-z0-9_-]{1,100}')

_FILLER = re.compile(r'^(?:(?:hola|buenas|buenos días|buenas tardes|oye|hey|vale|ok|bueno|por favor|porfa|nexus)[,.!:\s]+)+', re.IGNORECASE)
_ASK = re.compile(r'^(?:(?:quiero|necesito|me gustaría|querría) que (?:me )?|(?:puedes|podrías|podrias) |ayúdame a |ayudame a )', re.IGNORECASE)
_VERB = re.compile(r'^(?:haz|hazme|hacer|hacerme|hagas|crea|créame|creame|crear|crearme|genera|generar|generarme|dame|escribe|escribir|prepara|preparar|diseña|diseñar|redacta|redactar)\s+', re.IGNORECASE)
_MARKUP = re.compile(r'[*_`#>\[\]]+')
_SENTENCE = re.compile(r'(?<=[.!?¿¡])\s|\n')


def smart_title(raw, limit=48):
    raw = str(raw or '')
    synced = raw.startswith('↔ ')
    text = raw.removeprefix('↔ ')
    first_line = next((line for line in text.splitlines() if line.strip()), text)
    text = re.sub(r'\s+', ' ', _MARKUP.sub('', first_line)).strip()
    text = _ASK.sub('', _FILLER.sub('', text)).strip()
    without_verb = _VERB.sub('', text).strip()
    if len(without_verb) >= 3:
        text = without_verb
    text = next((part.strip() for part in _SENTENCE.split(text) if part.strip()), text).rstrip('.!?')
    if len(text) > limit:
        text = (text[:limit].rsplit(' ', 1)[0] if ' ' in text[:limit] else '') or text[:limit]
        text = text.rstrip(' ,;:') + '…'
    text = text[:1].upper() + text[1:] if text else (raw.strip() or 'Nueva conversación')
    return ('↔ ' if synced else '') + text


def clean_custom_title(title):
    return re.sub(r'\s+', ' ', str(title or '')).strip()[:TITLE_LIMIT]


def converted(value):
    """Desktop view of an archived chat. Android files are translated in memory, never rewritten."""
    if value.get('desktop'):
        if value.get('continuityAttachments'):
            seen = {(a.get('id'), a.get('url')) for item in value.get('items', []) for a in item.get('attachments', [])}
            missing = [a for a in value['continuityAttachments'] if (a.get('id'), a.get('url')) not in seen]
            if missing:
                return {**value, 'items': value.get('items', []) + [{'role': 'assistant', 'text': 'Adjuntos archivados de esta conversación', 'attachments': missing}]}
        return value
    items = []
    for item in value.get('items', []):
        role = str(item.get('type', item.get('kind', ''))).split('.')[-1].lower()
        if role in {'user', 'assistant'}:
            attachments = [{'id': a.get('id'), 'url': a.get('uri'), 'mime': a.get('mimeType'), 'name': a.get('name')} for a in item.get('attachments', [])]
            items.append({'role': role, 'text': item.get('text', ''), 'attachments': attachments})
        elif role == 'plancard':
            entry = value.get('plans', {}).get(item.get('planId'))
            if entry and entry.get('plan'):
                plan = entry['plan']
                items.append({'role': 'assistant', 'text': plan.get('modelName', 'Encargo')+' · '+entry.get('state', 'Archivado')+'\n'+plan.get('prompt', ''),
                              'archivedPlan': plan, 'archiveState': entry.get('state'), 'archiveJobId': entry.get('jobId')})
        elif role == 'notice':
            items.append({'role': 'assistant', 'text': item.get('text', 'Aviso archivado')})
    return {'desktop': True, 'items': items, 'draft': '', 'anchors': {}, 'resultPlans': []}


def atomic_write_json(path, data):
    """Unique temporary name per writer, so concurrent writers never share or truncate a temp file."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f'.{path.name}.{uuid.uuid4().hex}.tmp')
    try:
        with temporary.open('w', encoding='utf-8') as stream:
            json.dump(data, stream, ensure_ascii=False)
            stream.flush()
            os.fsync(stream.fileno())
        for attempt in range(5):
            try:
                os.replace(temporary, path)
                return
            except PermissionError:
                # Windows refuses the replace while another process briefly holds the target open.
                if attempt == 4:
                    raise
                time.sleep(0.05 * (attempt + 1))
    finally:
        temporary.unlink(missing_ok=True)


def _read_json(path, default):
    try:
        return json.loads(Path(path).read_text('utf-8'))
    except (OSError, ValueError):
        return default


def _text(item):
    return re.sub(r'\s+', ' ', str(item.get('text') or '')).strip()


def _metadata(name, chat, raw, stat):
    items = [item for item in chat['items'] if isinstance(item, dict)]
    users = [_text(item) for item in items if item.get('role') == 'user' and _text(item)]
    automatic = ('↔ ' if name.startswith('sync_') else '') + (users[0][:200] if users else '')
    updated = raw.get('updatedAt') if isinstance(raw.get('updatedAt'), (int, float)) and raw.get('updatedAt') > 0 else stat.st_mtime_ns // 1_000_000
    search = ' '.join(_text(item) for item in items)[:SEARCH_LIMIT].lower()
    return {'modified': stat.st_mtime_ns, 'length': stat.st_size, 'automatic': automatic,
            'messages': len(items), 'preview': (users[-1] if users else '')[:PREVIEW_LIMIT],
            'updatedAt': int(updated), 'projectId': raw.get('projectId'), 'search': search,
            'continuityOrigin': raw.get('continuityOrigin'), 'continuityTextHash': raw.get('continuityTextHash') if isinstance(raw.get('continuityTextHash'), str) else None}


def _derived(name, raw, stat):
    """Index entry for one file. A malformed conversation gets a marker, so it is skipped without
    being decoded again until it changes, and its file is never deleted."""
    try:
        if isinstance(raw, dict) and isinstance(raw.get('items', []), list):
            chat = converted(raw)
            if isinstance(chat.get('items'), list):
                return _metadata(name, chat, raw, stat)
    except (AttributeError, KeyError, OverflowError, TypeError, ValueError):
        pass
    return {'modified': stat.st_mtime_ns, 'length': stat.st_size, 'invalid': True}


_ENTRY_TYPES = {'modified': int, 'length': int, 'automatic': str, 'messages': int,
                'preview': str, 'updatedAt': int, 'search': str}


def _valid_entry(value):
    if not isinstance(value, dict):
        return False
    if value.get('invalid') is True:
        return all(type(value.get(key)) is int for key in ('modified', 'length'))
    return all(type(value.get(key)) is kind for key, kind in _ENTRY_TYPES.items()) and \
        (value.get('projectId') is None or isinstance(value.get('projectId'), str))


class ConversationStore:
    def __init__(self, root):
        self.root = Path(root)
        self.directory = self.root / 'conversations'
        self.index_file = self.root / 'conversations-index-v1.json'
        self.titles_file = self.root / 'conversation-titles.json'
        self.lock = threading.RLock()
        self._index = None

    @staticmethod
    def _identifier(value, message):
        identifier = str(value or '')
        if not IDENTIFIER.fullmatch(identifier):
            raise ValueError(message)
        return identifier

    def _load_index(self):
        if self._index is None:
            stored = _read_json(self.index_file, {})
            entries = stored.get('entries') if isinstance(stored, dict) and stored.get('version') == INDEX_VERSION else None
            entries = entries if isinstance(entries, dict) else {}
            self._index = {name: value for name, value in entries.items()
                           if isinstance(name, str) and IDENTIFIER.fullmatch(name) and _valid_entry(value)}
        return self._index

    def _save_index(self):
        atomic_write_json(self.index_file, {'version': INDEX_VERSION, 'entries': self._index})

    def titles(self):
        value = _read_json(self.titles_file, {})
        return {k: v for k, v in value.items() if isinstance(v, str) and v.strip()} if isinstance(value, dict) else {}

    def archive(self, args):
        items = args.get('items', [])
        if not items and not str(args.get('draft') or '').strip() and not args.get('draftAttachments'):
            return {'saved': False}
        identifier = self._identifier(args.get('id') or uuid.uuid4().hex, 'Identificador de conversación no válido.')
        record = {**args, 'id': identifier, 'desktop': True}
        path = self.directory / (identifier + '.json')
        with self.lock:
            self._keep_original(path)
            atomic_write_json(path, record)
            index = self._load_index()
            index[identifier] = _derived(identifier, record, path.stat())
            self._save_index()
        return {'saved': True, 'id': identifier}

    def _keep_original(self, path):
        if not path.is_file():
            return
        existing = _read_json(path, None)
        backup = self.root / 'conversations-originals' / path.name
        if not (isinstance(existing, dict) and existing.get('desktop')) and not backup.exists():
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, backup)

    def load(self, identifier):
        identifier = self._identifier(identifier, 'Conversación no válida.')
        return converted(json.loads((self.directory / (identifier + '.json')).read_text('utf-8')))

    def _refresh(self):
        index = self._load_index()
        self.directory.mkdir(parents=True, exist_ok=True)
        current = {}
        with os.scandir(self.directory) as entries:
            for entry in entries:
                stem, extension = os.path.splitext(entry.name)
                if extension == '.json' and IDENTIFIER.fullmatch(stem) and entry.is_file():
                    current[stem] = entry
        changed = False
        for stale in set(index) - set(current):
            del index[stale]
            changed = True
        for name, entry in current.items():
            stat = entry.stat()
            known = index.get(name)
            if known and known.get('modified') == stat.st_mtime_ns and known.get('length') == stat.st_size:
                continue
            index[name] = _derived(name, _read_json(entry.path, None), stat)
            changed = True
        if changed:
            self._save_index()
        return index

    def list(self, query='', project_id=None):
        terms = str(query or '').lower().split()
        with self.lock:
            index = dict(self._refresh())
        titles = self.titles()
        result = []
        rich_hashes = {meta.get('continuityTextHash') for meta in index.values() if meta.get('continuityOrigin')}
        for name, meta in index.items():
            if name.startswith('sync_') and name[5:] in rich_hashes and not meta.get('continuityOrigin'):
                continue
            if meta.get('invalid'):
                continue
            custom = titles.get(name)
            title = custom or smart_title(meta['automatic'])
            if project_id is not None and meta.get('projectId') != project_id:
                continue
            haystack = title.lower() + ' ' + meta.get('search', '')
            if any(term not in haystack for term in terms):
                continue
            result.append((meta['modified'], name, {'id': name, 'title': title, 'messages': meta['messages'],
                           'preview': meta['preview'], 'updatedAt': meta['updatedAt'], 'customTitle': custom}))
        result.sort(key=lambda entry: entry[:2], reverse=True)
        return [entry[2] for entry in result]

    def rename(self, identifier, title):
        identifier = self._identifier(identifier, 'Conversación no válida.')
        clean = clean_custom_title(title)
        with self.lock:
            if not (self.directory / (identifier + '.json')).is_file():
                raise ValueError('La conversación ya no existe.')
            titles = self.titles()
            if clean:
                titles[identifier] = clean
            else:
                titles.pop(identifier, None)
            atomic_write_json(self.titles_file, titles)
            automatic = self._refresh().get(identifier, {}).get('automatic') or ''
        return {'id': identifier, 'title': clean or smart_title(automatic), 'customTitle': clean or None}


_STORES = {}
_STORES_LOCK = threading.Lock()


def store_for(root):
    key = os.path.normcase(str(Path(root).resolve()))
    with _STORES_LOCK:
        return _STORES.setdefault(key, ConversationStore(root))
