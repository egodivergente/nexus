"""Same-origin bridge to the existing, private AXELPC Flow Live service.

No discovery, arbitrary destinations, provider requests or task resubmissions.
The upstream retains its Tailscale access control; loopback requires a local session.
"""
import http.client
import re
import secrets
import select
import socket
import urllib.parse

HOST = '100.72.111.97'
PORT = 8797


def route(handler, *, offline=False):
    parsed = urllib.parse.urlsplit(handler.path)
    if not parsed.path.startswith('/flow-live/'):
        return False
    origin = f'http://127.0.0.1:{handler.server.server_port}'
    query = urllib.parse.parse_qs(parsed.query)
    token = query.get('token', [''])[0]
    if (handler.headers.get('Sec-Fetch-Site') == 'cross-site'
            or not secrets.compare_digest(token, handler.server.token)):
        handler.send_error(403)
        return True
    path = parsed.path.removeprefix('/flow-live')
    if offline:
        handler.respond(b'{"ok":false,"error":"Flow no conecta en las pruebas aisladas."}', 'application/json', 503)
        return True
    if path == '/live':
        if handler.headers.get('Origin') != origin:
            handler.send_error(403)
            return True
        _websocket(handler)
    elif path in {'/health', '/assets'} or re.fullmatch(r'/assets/[A-Za-z0-9_-]{1,128}', path):
        _http(handler, path)
    else:
        handler.send_error(404)
    return True


def _http(handler, path):
    upstream = http.client.HTTPConnection(HOST, PORT, timeout=20)
    started = False
    try:
        upstream.request('GET', path)
        response = upstream.getresponse()
        handler.send_response(response.status)
        started = True
        for name in ('Content-Type', 'Content-Length', 'Content-Disposition'):
            value = response.getheader(name)
            if value:
                handler.send_header(name, value)
        handler.send_header('Cache-Control', 'no-store')
        handler.send_header('X-Content-Type-Options', 'nosniff')
        handler.end_headers()
        while chunk := response.read(256 * 1024):
            handler.wfile.write(chunk)
    except (OSError, http.client.HTTPException):
        if not started:
            handler.send_error(502, 'Flow Live no responde en AXELPC.')
        # Once a response started, close it instead of sending a second status line.
        handler.close_connection = True
    finally:
        upstream.close()


def _websocket(handler):
    key = handler.headers.get('Sec-WebSocket-Key', '')
    if (handler.headers.get('Upgrade', '').lower() != 'websocket'
            or handler.headers.get('Sec-WebSocket-Version') != '13'
            or not re.fullmatch(r'[A-Za-z0-9+/]{22}==', key)):
        handler.send_error(400)
        return
    upstream = None
    upgraded = False
    try:
        upstream = socket.create_connection((HOST, PORT), timeout=10)
        upstream.sendall((f'GET /live HTTP/1.1\r\nHost: {HOST}:{PORT}\r\n'
                          'Upgrade: websocket\r\nConnection: Upgrade\r\n'
                          f'Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n').encode('ascii'))
        reply = bytearray()
        while b'\r\n\r\n' not in reply:
            chunk = upstream.recv(4096)
            if not chunk or len(reply) > 16384:
                raise OSError('Incomplete WebSocket handshake')
            reply.extend(chunk)
        if not reply.startswith(b'HTTP/1.1 101 '):
            handler.send_error(502, 'Flow Live no ha aceptado la conexión.')
            return
        handler.connection.sendall(reply)
        upgraded = True
        handler.close_connection = True
        upstream.settimeout(30)
        handler.connection.settimeout(30)
        sockets = [upstream, handler.connection]
        while True:
            readable, _, failed = select.select(sockets, [], sockets, 30)
            if failed:
                return
            for source in readable:
                data = source.recv(256 * 1024)
                if not data:
                    return
                (handler.connection if source is upstream else upstream).sendall(data)
    except OSError:
        if not upgraded:
            handler.send_error(502, 'Flow Live no responde en AXELPC.')
    finally:
        handler.close_connection = True
        if upstream:
            upstream.close()
