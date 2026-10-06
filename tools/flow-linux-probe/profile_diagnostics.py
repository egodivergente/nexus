"""Print cookie persistence counts only, never cookie names, values or identities."""
import json
from pathlib import Path
import sqlite3

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
