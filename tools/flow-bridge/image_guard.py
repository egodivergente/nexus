"""Model proof for the measured migrated Flow image RPC, without prompt matching."""
import json

# Nano Banana 2 was read back from its picker and its intercepted request carried
# BELUGA on 2026-10-06. The request was aborted before reaching Google.
MODEL_CODES = {"NARWHAL": {"NARWHAL", "BELUGA"}}

def model_problem(body, requested):
    try:
        start = body.index("f.req=") + len("f.req=")
        envelope, _ = json.JSONDecoder().raw_decode(body[start:])
        entry = envelope[0][0]
        if entry[0] != "ogiZ0b":
            raise ValueError()
        payload = json.loads(entry[1])
        rows = payload[1]
        if not isinstance(rows, list) or not rows:
            raise ValueError()
        codes = [row[5] for row in rows]
        expected = MODEL_CODES.get(requested, {requested})
        if any(code not in expected for code in codes):
            return "Flow image request carries a different model than requested; submission refused."
    except (ValueError, TypeError, IndexError, KeyError):
        return "Flow image request model cannot be verified from its RPC structure; submission refused."
    return None
