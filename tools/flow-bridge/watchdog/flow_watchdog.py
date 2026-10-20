"""Vigilante del puente privado de Flow (recovery.py, puerto 8899).

Solo observa el puerto y, si el proceso deja de responder, ejecuta la orden de reinicio
configurada. Nunca lee ni borra cookies, perfiles del navegador ni credenciales: el token
del puente solo se lee si se pasa --token-file y nunca se escribe en el log.
"""
import argparse
import json
import logging
import logging.handlers
import os
from pathlib import Path
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.request

DEFAULT_INTERVAL = 20
DEFAULT_TIMEOUT = 8
DEFAULT_FAILURES = 3
BACKOFF_START = 15
BACKOFF_MAX = 600
STABLE_SECONDS = 300
LOG_BYTES = 1_000_000
LOG_BACKUPS = 5


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description='Vigilante del puente privado de Flow')
    parser.add_argument('--url', default=os.environ.get('NEXUS_FLOW_WATCH_URL', 'http://100.112.57.2:8899/'),
                        help='URL pública del puente (debe usar la IP o el nombre que acepta su cabecera Host)')
    parser.add_argument('--restart', default=os.environ.get('NEXUS_FLOW_RESTART', ''),
                        help='Orden de reinicio, p. ej. "systemctl --user restart nexus-flow-recovery.service"')
    parser.add_argument('--restart-ps1', default=os.environ.get('NEXUS_FLOW_RESTART_PS1', ''),
                        help='Windows: script de PowerShell de reinicio (alternativa a --restart)')
    parser.add_argument('--token-file', default=os.environ.get('NEXUS_FLOW_TOKEN_FILE', ''),
                        help='Opcional: token del puente para registrar también el estado de Flow (solo lectura)')
    parser.add_argument('--interval', type=int, default=DEFAULT_INTERVAL)
    parser.add_argument('--timeout', type=int, default=DEFAULT_TIMEOUT)
    parser.add_argument('--failures', type=int, default=DEFAULT_FAILURES,
                        help='Fallos seguidos antes de reiniciar')
    parser.add_argument('--log-file', default=os.environ.get('NEXUS_FLOW_WATCH_LOG',
                                                            str(Path.home() / '.local/state/nexus-flow-watchdog/watchdog.log')))
    parser.add_argument('--once', action='store_true', help='Una sola comprobación; código 0 si el puente responde')
    parser.add_argument('--dry-run', action='store_true', help='Registra el reinicio que haría, sin ejecutarlo')
    return parser.parse_args(argv)


def make_logger(path):
    logger = logging.getLogger('nexus-flow-watchdog')
    logger.setLevel(logging.INFO)
    if logger.handlers:
        return logger
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    handler = logging.handlers.RotatingFileHandler(target, maxBytes=LOG_BYTES, backupCount=LOG_BACKUPS, encoding='utf-8')
    handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s %(message)s'))
    logger.addHandler(handler)
    if sys.stderr is not None and sys.stderr.isatty():
        logger.addHandler(logging.StreamHandler(sys.stderr))
    return logger


def alive(url, timeout):
    """El portal responde en «/» sin autenticación: basta para saber que el proceso vive."""
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method='GET'), timeout=timeout) as response:
            return 200 <= response.status < 500, f'http {response.status}'
    except urllib.error.HTTPError as error:
        return error.code < 500, f'http {error.code}'
    except Exception as error:
        return False, type(error).__name__


def flow_state(url, token_file, timeout):
    """Estado de Flow para el log. Un fallo aquí (sesión, proyecto) no se arregla reiniciando."""
    if not token_file:
        return None
    try:
        token = Path(token_file).read_text(encoding='utf-8').strip()
    except OSError:
        return 'token_unreadable'
    request = urllib.request.Request(url.rstrip('/') + '/api/flow/status',
                                     headers={'Authorization': f'Bearer {token}', 'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.loads(response.read(65536) or b'{}')
    except urllib.error.HTTPError as error:
        return f'http_{error.code}'
    except Exception as error:
        return type(error).__name__
    return 'ready' if payload.get('generation_ready') else str(payload.get('reason') or payload.get('bridge_status') or 'not_ready')


def next_backoff(current):
    return BACKOFF_START if current <= 0 else min(current * 2, BACKOFF_MAX)


def restart_args(command, ps1):
    if ps1:
        return ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps1]
    return shlex.split(command) if command else []


def restart(args_list, logger, dry_run):
    if not args_list:
        logger.warning('Sin orden de reinicio configurada; solo se registra la caída.')
        return False
    if dry_run:
        logger.info('dry-run: se ejecutaría el reinicio configurado')
        return True
    try:
        completed = subprocess.run(args_list, timeout=120, capture_output=True, text=True)
    except Exception as error:
        logger.error('Reinicio fallido: %s', type(error).__name__)
        return False
    if completed.returncode != 0:
        logger.error('Reinicio terminó con código %s: %s', completed.returncode, (completed.stderr or '').strip()[:300])
        return False
    logger.info('Reinicio solicitado correctamente')
    return True


def run(args, sleep=time.sleep, clock=time.monotonic):
    logger = make_logger(args.log_file)
    if args.once:
        ok, detail = alive(args.url, args.timeout)
        logger.info('Comprobación única: %s (%s)', 'ok' if ok else 'caído', detail)
        return 0 if ok else 1
    logger.info('Vigilante iniciado: cada %ss sobre %s', args.interval, args.url)
    failures = 0
    backoff = 0
    blocked_until = 0.0
    healthy_since = None
    last_flow = None
    while True:
        ok, detail = alive(args.url, args.timeout)
        now = clock()
        if ok:
            if failures:
                logger.info('Puente recuperado tras %s fallos (%s)', failures, detail)
            failures = 0
            healthy_since = healthy_since or now
            if backoff and now - healthy_since >= STABLE_SECONDS:
                backoff = 0
            state = flow_state(args.url, args.token_file, args.timeout)
            if state != last_flow:
                if state is not None:
                    logger.info('Estado de Flow: %s', state)
                last_flow = state
        else:
            failures += 1
            healthy_since = None
            logger.warning('Puente sin respuesta (%s/%s): %s', failures, args.failures, detail)
            if failures >= args.failures and now >= blocked_until:
                backoff = next_backoff(backoff)
                restart(restart_args(args.restart, args.restart_ps1), logger, args.dry_run)
                blocked_until = now + backoff
                failures = 0
                logger.info('Próximo reinicio permitido dentro de %ss', backoff)
        sleep(args.interval)


if __name__ == '__main__':
    sys.exit(run(parse_args()))
