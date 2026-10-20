"""Keep JVM runtime diagnostics separate from the private JSON-lines protocol."""
import json
import re


def decode_line(line):
    # HotSpot may write startup/OS memory warnings to stdout before application code.
    # Discard only recognizable diagnostics, never arbitrary malformed replies.
    if re.match(r'^\[[0-9.]+s\]\[(?:warning|info|error)\]', line) or line.startswith('OpenJDK 64-Bit Server VM warning:'):
        return None
    try:
        result = json.loads(line)
    except json.JSONDecodeError:
        raise ValueError('El motor JVM devolvió una respuesta fuera del protocolo JSON.') from None
    if not isinstance(result, dict):
        raise ValueError('El motor JVM devolvió una respuesta que no es un objeto.')
    return result
