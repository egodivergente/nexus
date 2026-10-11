"""Durable sequential Flow blocks. Recovery reads accepted blocks, never resends."""
import hashlib
import json
from pathlib import Path
from flow_contract import block_counts


def run(provider, data, request_id, params, recover=False, recovery_started_at=0):
    def read():
        with provider.LOCK:
            connection = provider.ledger(data)
            try:
                row = connection.execute('SELECT result FROM jobs WHERE id=?', (request_id,)).fetchone()
                return json.loads(row['result']) if row and row['result'] else {}
            finally:
                connection.close()

    def store():
        result['recovery_started_at'] = recovery_started_at
        with provider.LOCK:
            connection = provider.ledger(data)
            try:
                with connection:
                    connection.execute('UPDATE jobs SET result=? WHERE id=?', (json.dumps(result), request_id))
            finally:
                connection.close()

    previous = read()
    prefix = hashlib.sha256(request_id.encode()).hexdigest()[:32]
    parts = previous.get('blocks') or [
        {'request_id': f'{prefix}-b{i + 1}', 'count': count, 'status': 'queued'}
        for i, count in enumerate(block_counts(params['count']))]
    result = {'status': 'in_progress', 'count': params['count'], 'blocks': parts}
    store()
    tool = 'flow_generate_image' if params['model'] in {'nano2', 'nano-pro'} else 'flow_generate_video'
    for part in parts:
        if part['status'] == 'completed':
            proof=part.get('result', {})
            artifacts=proof.get('artifacts') or ([proof] if proof.get('path') else [])
            try:
                valid=len(artifacts)==part['count'] and all(
                    Path(a['path']).resolve().is_relative_to((provider.RUNTIME/'results').resolve())
                    and hashlib.sha256(Path(a['path']).read_bytes()).hexdigest()==a['sha256'] for a in artifacts)
            except (OSError,KeyError):valid=False
            if not recover or valid:continue
        if recover:
            if part['status'] == 'queued':
                continue  # Interrupted future blocks have never been submitted.
            operation, arguments = 'flow_get_result', {'request_id': part['request_id']}
        else:
            # Persist the intent BEFORE transport. A crash after acceptance cannot duplicate it.
            part['status'] = 'submitting'
            store()
            operation = tool
            arguments = provider.media_parameters(data, {**params, 'count': part['count']})
            arguments['request_id'] = part['request_id']
        try:
            response = provider.call(operation, arguments, part['request_id'] if not recover else 'recover-' + part['request_id'])
            if not isinstance(response, dict):
                raise ValueError('Invalid Flow block response')
            artifacts = response.get('artifacts') or ([response] if response.get('path') else [])
            if response.get('status') == 'completed' and len(artifacts) != part['count']:
                response = {**response, 'status': 'needs_reconciliation'}
            part.update(status=response.get('status', 'needs_reconciliation'), result=response)
        except Exception as error:
            part.update(status='needs_reconciliation', result={'error_class': type(error).__name__})
        store()
        if part['status'] != 'completed':
            break  # Stop further spending when an earlier block is uncertain.
    result['status'] = 'completed' if all(p['status'] == 'completed' for p in parts) else 'needs_reconciliation'
    store()
    return result
