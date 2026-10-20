"""Inert chat history enrichment. Never restores tools, approvals or executable plans."""
import hashlib
import json


def plain_items(items):
    result = []
    for item in items:
        role = str(item.get('role', item.get('type', item.get('kind', '')))).split('.')[-1].lower()
        text = item.get('text')
        if role in {'user', 'assistant', 'notice'} and isinstance(text, str) and text.strip():
            result.append({'role': 'assistant' if role == 'notice' else role, 'text': text})
    return result


def history_id(items):
    # Matches SyncDocuments.capture's existing content identity: no second chat copy.
    value = json.dumps({'items': plain_items(items)}, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(value).hexdigest()


def inert_chat(raw, resolve):
    items = []
    for item in raw.get('items', []):
        role = str(item.get('role', item.get('type', item.get('kind', '')))).split('.')[-1].lower()
        if role not in {'user', 'assistant', 'notice'}:
            continue
        text = item.get('text', '')
        if not isinstance(text, str):
            continue
        attachments = []
        for attachment in item.get('attachments', []):
            if not isinstance(attachment, dict):
                continue
            uri = attachment.get('uri', attachment.get('url', ''))
            if not isinstance(uri, str) or not uri.startswith('nexus-sync-blob:'):
                continue
            attachments.append({'id': str(attachment.get('id', '')), 'url': resolve(attachment),
                                'mime': str(attachment.get('mimeType', attachment.get('mime', 'application/octet-stream'))),
                                'name': str(attachment.get('name') or 'Adjunto')})
        if text.strip() or attachments:
            items.append({'role': 'assistant' if role == 'notice' else role, 'text': text, 'attachments': attachments})
    return {'desktop': True, 'items': items, 'draft': '', 'anchors': {}, 'resultPlans': [],
            'continuityTextHash': history_id(raw.get('items', []))}


def visible_history(files):
    """Hide only the derived Drive text projection; retain distinct source identities.

    Both underlying files remain recoverable. Two rich chats with equal text and
    different source IDs or attachments are never collapsed.
    """
    rich_hashes = {value.get('continuityTextHash') for _, value in files if value.get('continuityOrigin')}
    return [(stem, value) for stem, value in files
            if value.get('continuityOrigin') or not (stem.startswith('sync_') and stem[5:] in rich_hashes)]
