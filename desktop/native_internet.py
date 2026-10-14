"""Bounded web reading and Android-compatible Streamable HTTP MCP adapters."""
from html.parser import HTMLParser
import ipaddress
import json
import re
import socket
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from native_operations import atomic_json


def url(value):
    parsed = urllib.parse.urlsplit(str(value))
    if parsed.scheme not in {'http', 'https'} or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('Introduce una URL HTTP o HTTPS sin credenciales incrustadas.')
    return str(value)


def public_url(value):
    checked = url(value)
    host = (urllib.parse.urlsplit(checked).hostname or '').lower().strip('[]')
    if host == 'localhost' or host.endswith('.localhost') or host.endswith('.local'):
        raise ValueError('Por seguridad, read_web_page solo accede a páginas públicas de Internet.')
    try:
        ip = ipaddress.ip_address(host)
        if ip.is_loopback or ip.is_private or ip.is_link_local or ip.is_unspecified or ip.is_reserved:
            raise ValueError('Por seguridad, read_web_page no accede a direcciones locales o privadas.')
    except ValueError as err:
        if 'Por seguridad' in str(err):
            raise
        try:
            for info in socket.getaddrinfo(host, None):
                addr = ipaddress.ip_address(info[4][0].split('%')[0])
                if addr.is_loopback or addr.is_private or addr.is_link_local or addr.is_unspecified:
                    raise ValueError('Por seguridad, read_web_page no accede a direcciones locales o privadas.')
        except ValueError as inner:
            if 'Por seguridad' in str(inner):
                raise
        except OSError:
            pass
    return checked


def bounded(response, maximum=2_000_000):
    raw = response.read(maximum+1)
    if len(raw) > maximum:
        raise ValueError('La respuesta supera el tamaño admitido.')
    return raw


class PageText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts, self.links, self.skip = [], [], 0
    def handle_starttag(self, tag, attributes):
        if tag in {'script', 'style', 'nav', 'footer'}:
            self.skip += 1
        if not self.skip and tag == 'a':
            href = dict(attributes).get('href')
            if href and len(self.links) < 40:
                self.links.append(href)
        if tag in {'p', 'div', 'br', 'h1', 'h2', 'li'}:
            self.parts.append('\n')
    def handle_endtag(self, tag):
        if tag in {'script', 'style', 'nav', 'footer'}:
            self.skip = max(0, self.skip-1)
    def handle_data(self, data):
        if not self.skip:
            self.parts.append(data)


class MCP:
    def __init__(self, endpoint, token=''):
        self.endpoint, self.token = url(endpoint), token
        self.session, self.version = None, '2025-03-26'
        result = self.rpc('initialize', {'protocolVersion': self.version, 'capabilities': {}, 'clientInfo': {'name': 'NexusStudioWindows', 'version': '1.9.2'}})
        self.version = result.get('protocolVersion', self.version)
        if self.version not in {'2024-11-05', '2025-03-26', '2025-06-18', '2025-11-25'}:
            raise ValueError('Versión MCP no compatible: '+self.version)
        self.rpc('notifications/initialized', {}, notification=True)

    def headers(self):
        result = {'Accept': 'application/json, text/event-stream', 'Content-Type': 'application/json', 'MCP-Protocol-Version': self.version}
        if self.session:
            result['Mcp-Session-Id'] = self.session
        if self.token:
            result['Authorization'] = 'Bearer '+self.token
        return result

    def rpc(self, method, params, notification=False):
        identifier = uuid.uuid4().hex
        body = {'jsonrpc': '2.0', 'method': method, 'params': params}
        if not notification:
            body['id'] = identifier
        request = urllib.request.Request(self.endpoint, json.dumps(body).encode(), self.headers(), method='POST')
        with urllib.request.urlopen(request, timeout=60) as response:
            self.session = response.headers.get('Mcp-Session-Id', self.session)
            if notification:
                return {}
            def result(raw):
                try:
                    message = json.loads(raw)
                except ValueError:
                    return None
                if message.get('id') != identifier:
                    return None
                if message.get('error') is not None:
                    raise ValueError('El servidor MCP rechazó '+method+'. Consulta el esquema y los argumentos.')
                return message.get('result', {})
            if 'text/event-stream' in response.headers.get('Content-Type', ''):
                event, size = [], 0
                for raw in response:
                    size += len(raw)
                    if size > 2_000_000:
                        raise ValueError('Respuesta MCP demasiado grande.')
                    line = raw.decode('utf-8').rstrip('\r\n')
                    if not line:
                        parsed = result('\n'.join(event))
                        event = []
                        if parsed is not None:
                            return parsed
                    elif line.startswith('data:'):
                        event.append(line[5:].lstrip())
                parsed = result('\n'.join(event))
            else:
                parsed = result(bounded(response))
            if parsed is None:
                raise ValueError('MCP no devolvió un resultado para esta llamada.')
            return parsed

    def tools(self, cursor=None):
        return self.rpc('tools/list', {'cursor': cursor} if cursor else {})

    def call(self, name, arguments):
        cursor, seen, selected = None, set(), None
        for _ in range(20):
            catalog = self.tools(cursor)
            selected = next((tool for tool in catalog.get('tools', []) if tool.get('name') == name), None)
            if selected:
                break
            cursor = catalog.get('nextCursor')
            if not cursor or cursor in seen:
                break
            seen.add(cursor)
        if not selected:
            raise ValueError('La herramienta no figura en el catálogo actual del servidor.')
        if selected.get('annotations', {}).get('readOnlyHint') is not True or re.search('generate|create|delete|remove|submit|purchase|pay|execute|send|write|update', name, re.I):
            raise ValueError('Esta conexión MCP admite consultas. Las generaciones usan el pipeline nativo de Nexus.')
        schema = selected.get('inputSchema')
        if not isinstance(schema, dict) or not isinstance(arguments, dict):
            raise ValueError('Consulta el esquema y envía argumentos JSON de objeto.')
        if any(key not in arguments for key in schema.get('required', [])):
            raise ValueError('Faltan argumentos obligatorios. Consulta list_mcp_tools.')
        result = self.rpc('tools/call', {'name': name, 'arguments': arguments})
        if result.get('isError') is True:
            detail = ' '.join(str(part.get('text', '')) for part in result.get('content', []))[:1500]
            raise ValueError('La herramienta MCP falló: '+(detail or 'sin detalle'))
        return result

    def close(self):
        if self.session:
            try:
                with urllib.request.urlopen(urllib.request.Request(self.endpoint, headers=self.headers(), method='DELETE'), timeout=10):
                    pass
            except Exception:
                pass


def run(native, operation, args):
    if operation == 'web_search':
        endpoint = 'https://www.bing.com/search?'+urllib.parse.urlencode({'format': 'rss', 'q': str(args['query'])[:1000]})
        with urllib.request.urlopen(urllib.request.Request(endpoint, headers={'User-Agent': 'NexusStudio/1.9.2'}), timeout=30) as response:
            root = ET.fromstring(bounded(response))
        return {'query': args['query'], 'results': [{'title': node.findtext('title'), 'url': node.findtext('link'), 'snippet': node.findtext('description')} for node in root.findall('.//item')[:10]]}
    if operation == 'read_web_page':
        endpoint = public_url(args['url'])
        with urllib.request.urlopen(urllib.request.Request(endpoint, headers={'User-Agent': 'NexusStudio/1.9.2'}), timeout=30) as response:
            final_url = public_url(response.url)
            mime = response.headers.get('Content-Type', '')
            if not any(kind in mime for kind in ['text/', 'application/xhtml+xml', 'application/json']):
                raise ValueError('La URL no devuelve una página o documento de texto.')
            text = bounded(response).decode(response.headers.get_content_charset() or 'utf-8', errors='replace')
        parser = PageText()
        parser.feed(text)
        content = re.sub('[ \t]+', ' ', ''.join(parser.parts)).strip()
        return {'url': final_url, 'text': content[:40000], 'truncated': len(content) > 40000, 'links': [urllib.parse.urljoin(final_url, link) for link in parser.links]}
    path = native.data / 'mcp-servers.json'
    servers = json.loads(path.read_text('utf-8')) if path.exists() else {}
    if operation == 'list_mcp_servers':
        return {'servers': [{'server_id': key, 'url': value['url'], 'connected': key in native.mcp} for key, value in servers.items()]}
    identifier = str(args['server_id'])
    if not re.fullmatch('[A-Za-z0-9_-]{1,80}', identifier):
        raise ValueError('Identificador MCP no válido.')
    secrets_path = native.data / 'mcp-credentials.dpapi'
    credentials = native.host.read_secret(secrets_path)
    if operation == 'connect_mcp_server':
        endpoint = url(args['url'])
        previous_url = (servers.get(identifier) or {}).get('url', '')
        same_host = bool(previous_url) and urllib.parse.urlsplit(previous_url).netloc.lower() == urllib.parse.urlsplit(endpoint).netloc.lower()
        token = str(args['token']) if 'token' in args and args['token'] is not None else (credentials.get(identifier, '') if same_host else '')
        client = MCP(endpoint, token)
        catalog = client.tools()
        old = native.mcp.get(identifier)
        if old:
            old.close()
        native.mcp[identifier] = client
        servers[identifier] = {'url': endpoint}
        atomic_json(path, servers)
        if args.get('token'):
            credentials[identifier] = token
            native.host.write_secret(secrets_path, credentials)
        elif not same_host and identifier in credentials:
            credentials.pop(identifier, None)
            native.host.write_secret(secrets_path, credentials)
        return {'server_id': identifier, 'connected': True, **catalog}
    if operation == 'disconnect_mcp_server':
        client = native.mcp.pop(identifier, None)
        if client:
            client.close()
        servers.pop(identifier, None)
        atomic_json(path, servers)
        credentials.pop(identifier, None)
        native.host.write_secret(secrets_path, credentials)
        return {'disconnected': True}
    if identifier not in servers:
        raise ValueError('Conecta primero el servidor MCP.')
    if identifier not in native.mcp:
        native.mcp[identifier] = MCP(servers[identifier]['url'], credentials.get(identifier, ''))
    client = native.mcp[identifier]
    if operation == 'list_mcp_tools':
        return client.tools(args.get('cursor'))
    return client.call(args['tool_name'], json.loads(args['arguments_json']))
