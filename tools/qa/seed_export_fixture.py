"""Seed only a fresh, empty test installation's copied SQLite database."""
import sqlite3
import sys
import time

with sqlite3.connect(sys.argv[1]) as db:
    assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
    assert db.execute("SELECT COUNT(*) FROM generations").fetchone()[0] == 0, "Refuse nonempty history"
    assert db.execute("SELECT COUNT(*) FROM outputs").fetchone()[0] == 0, "Refuse nonempty outputs"
    now = int(time.time() * 1000)
    db.execute("INSERT INTO generations(id,provider_id,model_id,modality,prompt,parameters_json,status,started_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?)",
               ("qa-export-local", "qa", "qa-export-local", "IMAGE", "Prueba local de descarga", "{}", "COMPLETED", now, now))
    db.execute("INSERT INTO outputs(id,job_id,type,url,local_uri,mime_type) VALUES(?,?,?,?,?,?)",
               ("qa-export-local", "qa-export-local", "IMAGE", "https://example.invalid/qa.png",
                "file:///data/user/0/com.vidadomu.nexus.agent/files/qa-export.png", "image/png"))
    assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
print("Seeded one local-only output in empty test database")
