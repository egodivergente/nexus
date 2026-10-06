"""Refuse wrong video tiers/counts before Google receives the migrated RPC."""
import json

def model_problem(body):
    try:
        start = body.index("f.req=") + len("f.req=")
        envelope, _ = json.JSONDecoder().raw_decode(body[start:])
        entry = envelope[0][0]
        if entry[0] != "YhhmEf":
            raise ValueError()
        payload = json.loads(entry[1])
        rows = payload[0]
        if len(rows) != 1 or rows[0][1] not in {"veo_3_1_t2v_fast_ultra", "veo_3_1_t2v_fast"}:
            return "Unexpected video tier or output count; submission refused."
    except (ValueError, TypeError, IndexError, KeyError):
        return "Video model cannot be verified from its RPC structure; submission refused."
    return None
