"""Print cookie persistence counts only, never cookie names, values or identities."""
import json
from pathlib import Path
import sqlite3
from urllib.parse import urlsplit

profile = Path.home() / '.local/share/nexus-flow-probe/profile/Default'
db = profile / 'Cookies'
if not db.exists():
    db = profile / 'Network/Cookies'
with sqlite3.connect(db.as_uri() + '?mode=ro', uri=True) as connection:
    counts = connection.execute(
        "SELECT is_persistent, has_expires, count(*) FROM cookies "
        "WHERE host_key IN ('.google.com', 'accounts.google.com', '.flow.google.com', 'flow.google.com') "
        "GROUP BY is_persistent, has_expires"
    ).fetchall()
preferences = json.loads((profile / 'Preferences').read_text())
print(json.dumps({'cookie_persistence_counts': counts,
                  'exit_type': preferences.get('profile', {}).get('exit_type'),
                  'restore_on_startup': preferences.get('session', {}).get('restore_on_startup')}))
with sqlite3.connect((profile / 'History').as_uri() + '?mode=ro', uri=True) as connection:
    recent = connection.execute('SELECT url FROM urls ORDER BY last_visit_time DESC LIMIT 10').fetchall()
routes = []
for (url,) in recent:
    parts = urlsplit(url)
    if parts.hostname == 'accounts.google.com':
        routes.append('rejected' if '/rejected' in parts.path else 'password' if '/challenge/pwd' in parts.path
                      else 'challenge' if '/challenge/' in parts.path else 'account_chooser' if '/accountchooser' in parts.path
                      else 'identifier' if '/identifier' in parts.path else 'other_google_auth')
    elif parts.hostname == 'flow.google.com':
        routes.append('flow_public' if parts.path.startswith('/about') else 'flow_app')
print(json.dumps({'recent_route_classes': routes}))
