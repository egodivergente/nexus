"""Read-only BytePlus administrative adapter for the EXISTING private Flow bridge.

The parent app enforces its existing Host/Origin/Bearer checks. No inference,
profile changes, key listing or browser automation is performed here.
"""
import asyncio
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import subprocess
import threading
import time
from fastapi import APIRouter, HTTPException

router = APIRouter()
LOCK = threading.Lock()
ROOT = Path(os.environ.get('NEXUS_BYTEPLUS_CACHE', str(Path.home() / '.local/share/nexus-flow-probe/byteplus-usage')))
CLI = os.environ.get('ARKCLI_BINARY', str(Path.home() / '.npm-global/bin/arkcli'))
TTL = 900


class Unavailable(Exception):
    pass


class SplitBillUnavailable(Unavailable):
    pass


def run(*args):
    env = dict(os.environ, ARKCLI_NO_UPDATE_NOTIFIER='1', ARKCLI_CALLER_TYPE='ai_agent',
               ARKCLI_CALLER_NAME='nexus-admin', ARKCLI_SKILL_NAME='arkcli-billing' if args[0] == 'billing' else 'arkcli-usage')
    try:
        p = subprocess.run([CLI, *args, '--format', 'json'], capture_output=True, timeout=35, env=env)
        if p.returncode and b'not enabled split bill capability' in (p.stdout + p.stderr):
            raise SplitBillUnavailable('La cuenta BytePlus no tiene activado el desglose de facturas requerido por Ark CLI (split bill).')
        if p.returncode or len(p.stdout) > 4 * 1024 * 1024:
            raise Unavailable('La consulta oficial no está disponible. Comprueba sesión SSO y permisos en ASUS.')
        result = json.loads(p.stdout)
        if not isinstance(result, dict) or result.get('error'):
            raise Unavailable('El proveedor devolvió un error administrativo.')
        return result
    except (OSError, subprocess.TimeoutExpired, ValueError):
        raise Unavailable('Ark CLI no está disponible, no respondió a tiempo o devolvió un formato incompatible.') from None


def number(value):
    try:
        if value is None or isinstance(value, bool): raise ValueError()
        amount = Decimal(str(value))
        if not amount.is_finite(): raise ValueError()
        return amount
    except (InvalidOperation, ValueError):
        raise Unavailable('Falta una métrica oficial o su formato es incompatible.') from None


def stats(data):
    if 'records' not in data or 'data_count' not in data:
        raise Unavailable('Formato de consumo oficial no reconocido.')
    fields = [f.get('name') for f in data.get('fields', [])]
    grouped = {}
    for raw in data['records']:
        row = dict(zip(fields, raw)) if isinstance(raw, list) else raw
        model = str(row.get('ModelName') or row.get('ModelUnitID') or 'Modelo no informado')
        group = grouped.setdefault(model, {'model': model, 'input': 0, 'output': 0, 'calls': 0})
        for target, source in [('input', 'InputTokens'), ('output', 'OutputTokens'), ('calls', 'ReqCnt')]:
            value = number(row.get(source))
            if value < 0 or value != value.to_integral_value(): raise Unavailable('Métrica de uso no válida.')
            group[target] += int(value)
    if int(data['data_count']) > len(data['records']):
        raise Unavailable('Consumo oficial incompleto; no se presenta un total parcial como definitivo.')
    return {'rows': list(grouped.values()), 'scope': 'cuenta y proyecto del perfil Ark CLI; puede incluir otras apps',
            'delay_minutes': '5–30', 'empty': not grouped}


def quota(data):
    if not isinstance(data.get('items'), list): raise Unavailable('Formato de cuotas no reconocido.')
    if int(data.get('total_count', len(data['items']))) > len(data['items']):
        raise Unavailable('La lista de cuotas está paginada; no se muestra un saldo parcial.')
    rows = []
    for item in data['items']:
        packs = item.get('resource_packs') or []
        free = item.get('free_usage')
        allowances = packs if packs else ([free] if isinstance(free, dict) else [])
        for pack in allowances:
            if pack.get('remaining') is None: raise Unavailable('La cuota no informa el remanente.')
            rows.append({'model': str(item.get('model') or item.get('display_name') or 'Modelo no informado'),
                         'remaining': str(number(pack['remaining'])), 'unit': str(pack.get('unit') or 'unidades de cuota (CLI)'),
                         'kind': str(pack.get('type') or 'FreeInference')})
        # Never add free_usage and resource_packs: they may represent the same allowance.
    return {'rows': rows, 'empty': not rows, 'kind': 'free-quota'}


def all_quotas():
    items = []
    for page in range(1, 11):
        data = run('usage', 'balance', '--type', 'free-quota', '--modality', 'LLM', '--page-size', '20', '--page-number', str(page))
        current = data.get('items')
        if not isinstance(current, list): raise Unavailable('Formato de cuotas no reconocido.')
        items.extend(current)
        total = int(data.get('total_count', len(items)))
        if len(items) >= total: return dict(data, items=items)
        if not current: break
    raise Unavailable('La lista de cuotas está incompleta; no se muestra un saldo parcial.')


def billing(data):
    summary = data.get('summary') or data
    if summary.get('is_truncated') is True or data.get('is_truncated') is True or data.get('partial_failures'):
        raise Unavailable('Facturación incompleta: requiere una consulta acotada por el administrador. No se calcula un total parcial.')
    items = data.get('items')
    if not isinstance(items, list): raise Unavailable('Formato de facturación no reconocido.')
    if not items: return {'empty': True, 'amount': None, 'currency': None}
    currencies = {r.get('Currency') or r.get('CurrencyCode') for r in items}
    if len(currencies) != 1 or None in currencies: raise Unavailable('La factura no informa una moneda única.')
    amount = sum((number(r.get('PayableAmount')) for r in items), Decimal(0))
    # PayableAmount is kept as supplied. No guessed tax, free-quota discount or local estimate is added.
    return {'amount': str(amount), 'currency': currencies.pop(), 'tax': None, 'total': None,
            'scope': 'cuenta ModelArk, no atribuible íntegramente a Nexus', 'settlement': 'T+1'}


def regular_billing(data):
    rows = data.get('list')
    if not isinstance(rows, list) or data.get('total') is None or int(data['total']) != len(rows):
        raise Unavailable('La factura oficial está incompleta; se conserva la última lectura.')
    if not rows: return {'empty': True, 'amount': None, 'currency': None, '_source': 'BytePlus ListBillDetail · SDK oficial'}
    currencies = {row.get('currency') for row in rows}
    if len(currencies) != 1 or not next(iter(currencies)):
        raise Unavailable('La factura no informa una moneda única.')
    def total(field):
        return str(sum((number(row.get(field)) for row in rows), Decimal(0))) if all(row.get(field) is not None for row in rows) else None
    return {'amount': total('pretax_amount') or total('payable_amount'), 'tax': total('tax'),
            'total': total('posttax_amount'), 'currency': currencies.pop(), 'settlement': 'T+1',
            'scope': 'cuenta BytePlus · todos los productos, puede incluir otras apps',
            '_source': 'BytePlus ListBillDetail · SDK oficial'}


def sdk_billing(today, period):
    # Reuse Ark CLI's EXISTING temporary STS credentials for the active SSO identity.
    # No keys are created, credentials never leave ASUS, and only ListBillDetail is allowed.
    try:
        import re
        import byteplussdkcore
        import byteplussdkbilling
        auth = run('auth', 'status')
        account = str(auth['byteplus_sso']['identity']['account_id'])
        if not re.fullmatch(r'[0-9]+', account): raise ValueError()
        creds = json.loads((Path.home() / '.arkcli-bp/identities' / ('byteplus-' + account) / 'sts.json').read_text())
        expiry = float(creds['expires_at'])
        if expiry > 1e12: expiry /= 1000
        if expiry <= time.time() + 5: raise Unavailable('La sesión financiera temporal ha caducado. Actualiza la sesión Ark CLI en ASUS.')
        config = byteplussdkcore.Configuration()
        config.ak = creds['ak']; config.sk = creds['sk']; config.session_token = creds['session_token']
        config.region = auth['active_profile']['region']; config.debug = False
        client = byteplussdkcore.ApiClient(config)
        try:
            api = byteplussdkbilling.BILLINGApi(client)
            rows = []; expected = None
            for offset in range(0, 3000, 300):
                params = dict(bill_period=today.strftime('%Y-%m'), limit=300, offset=offset,
                              group_term=3, group_period=1 if period == 'day' else 0, need_record_num=1)
                if period == 'day': params['expense_date'] = today.isoformat()
                response = api.list_bill_detail(byteplussdkbilling.ListBillDetailRequest(**params), _request_timeout=(8, 25)).to_dict()
                if not isinstance(response.get('list'), list) or response.get('total') is None: raise ValueError()
                count = int(response['total'])
                if expected is not None and count != expected: raise Unavailable('La factura cambió durante la consulta; vuelve a actualizar.')
                expected = count; rows.extend(response['list'])
                if len(rows) >= count: return regular_billing({'list': rows, 'total': count})
                if not response['list']: break
            raise Unavailable('La factura supera el límite de lectura íntegra; no se calcula un total parcial.')
        finally:
            from contextlib import suppress
            with suppress(Exception):
                pool = getattr(getattr(client, 'rest_client', None), 'pool_manager', None)
                if pool is not None: pool.clear()
    except Unavailable:
        raise
    except Exception:
        # SDK exceptions can carry signed headers or response bodies. Never log/return them.
        raise Unavailable('No se pudo leer la factura mediante el SDK oficial. Se conservan los últimos datos; comprueba sesión y permisos en ASUS.') from None


def fetch_billing(today, period):
    try:
        return billing(run('billing', 'list', '--start', today.strftime('%Y-%m'), '--project=', '--interval', 'day', *(['--day', today.isoformat()] if period == 'day' else [])))
    except SplitBillUnavailable:
        return sdk_billing(today, period)


def monetary_balance(data):
    result = data.get('Result')
    if not isinstance(result, dict) or not result.get('Currency'):
        raise Unavailable('La API financiera no informa saldo y moneda verificables.')
    return {'amount': str(number(result.get('AvailableBalance'))), 'currency': str(result['Currency']),
            'kind': 'available-monetary-balance'}


def model_rates(data, currency):
    if currency != 'USD': raise Unavailable('La moneda de la tarifa no se ha verificado como USD.')
    items = data.get('items')
    if not isinstance(items, list) or len(items) != 1: raise Unavailable('Tarifa de modelo no unívoca.')
    item = items[0]; prices = {}
    for key in ['InferencePrompt', 'InferenceCompletion']:
        candidates = [c for c in item.get('ChargeItems', []) if c.get('Type') == key]
        if len(candidates) != 1 or str(candidates[0].get('UnitCode', '')).lower() != 'k tokens':
            raise Unavailable('La tarifa requiere una unidad o desglose que no admite esta estimación.')
        price = number(candidates[0].get('Price'))
        if price < 0: raise Unavailable('Tarifa no válida.')
        for tier in item.get('MultiChargeItems') or []:
            for charge in tier.get('ChargeItems') or []:
                if charge.get('Type') == key and number(charge.get('Price')) != price:
                    raise Unavailable('La tarifa tiene tramos; configura una tarifa conservadora en el cerebro.')
        prices[key] = str(price * 1000)
    return {'model': item['FoundationModelName'], 'input_usd_per_million': prices['InferencePrompt'],
            'output_usd_per_million': prices['InferenceCompletion'], 'currency': currency}


def snapshot(period, force=False):
    if period not in ('day', 'month'): raise ValueError('Invalid period')
    with LOCK:
        today = date.today()
        path = ROOT / f'{today.isoformat()}-{period}.json'
        old = None
        try:
            old = json.loads(path.read_text())
            if not force and time.time() * 1000 - old['fetched_at'] < TTL * 1000: return old
        except (OSError, ValueError, KeyError): pass
        now = int(time.time() * 1000)
        result = {'schema': 1, 'period': period, 'fetched_at': now, 'timezone': str(datetime.now().astimezone().tzinfo),
                  'start': (today if period == 'day' else today.replace(day=1)).isoformat(), 'end': today.isoformat()}
        try:
            auth = run('auth', 'status')
            if auth.get('logged_in') is not True: raise Unavailable('Ark CLI no tiene una sesión administrativa BytePlus. Completa arkcli auth login en ASUS; la APK no recibe esas credenciales.')
            identity_error = None
        except Unavailable as e: identity_error = str(e)
        queries = {
            'balance': (lambda: run('api', 'billing.query_balance_acct', '--params', '{}'), monetary_balance),
            'stats': (lambda: run('usage', 'stats', '--start', result['start'], '--end', result['end'], '--by', 'model'), stats),
            'quota': (lambda: all_quotas(), quota),
            'billing': (lambda: fetch_billing(today, period), lambda value: value),
        }
        for key, (query, normalize) in queries.items():
            source = {'stats': 'Ark CLI usage stats', 'quota': 'Ark CLI usage balance --type free-quota', 'billing': 'Ark CLI billing list', 'balance': 'BytePlus QueryBalanceAcct vía Ark CLI'}[key]
            try:
                if identity_error: raise Unavailable(identity_error)
                value = normalize(query())
                value.update(start=result['start'], end=result['end'])
                result[key] = dict(value, status='ok', source=value.pop('_source', source), updated_at=now)
            except Unavailable as e:
                previous = (old or {}).get(key, {})
                result[key] = dict(previous, status='stale' if previous.get('updated_at') else 'unavailable', error=str(e), source=source)
        source = 'Ark CLI pricing models · tarifa de la cuenta'
        try:
            if identity_error: raise Unavailable(identity_error)
            models = sorted({row['model'] for row in result.get('stats', {}).get('rows', [])})
            if not models or len(models) > 20: raise Unavailable('Sin modelos de consumo con tarifa verificable.')
            currency = result.get('balance', {}).get('currency')
            rates = [model_rates(run('pricing', 'models', '--model', model), currency) for model in models]
            result['pricing'] = dict(rows=rates, status='ok', source=source, updated_at=now, start=result['start'], end=result['end'])
        except Unavailable as error:
            previous = (old or {}).get('pricing', {})
            result['pricing'] = dict(previous, status='stale' if previous.get('updated_at') else 'unavailable', error=str(error), source=source)
        ROOT.mkdir(parents=True, exist_ok=True)
        os.chmod(ROOT, 0o700)
        tmp = path.with_suffix('.tmp')
        with tmp.open('w') as f:
            os.chmod(tmp, 0o600); json.dump(result, f, ensure_ascii=False); f.flush(); os.fsync(f.fileno())
        os.replace(tmp, path)
        return result


@router.get('/api/byteplus/usage')
async def usage(period: str = 'day', refresh: bool = False):
    if period not in ('day', 'month'): raise HTTPException(400, 'period must be day or month')
    return await asyncio.to_thread(snapshot, period, refresh)
