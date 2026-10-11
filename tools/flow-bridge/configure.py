"""Set limits on the already registered Flow MCP entry, preserving other tables."""
from pathlib import Path
import re
import tomllib

config = Path.home() / ".codex" / "config.toml"
text = config.read_text(encoding="utf-8")
name = "google-flow"
tomllib.loads(text)["mcp_servers"][name]
header = "[mcp_servers.google-flow]"
start = text.index(header) + len(header)
next_table = re.search(r"(?m)^\[", text[start:])
end = start + next_table.start() if next_table else len(text)
section = text[start:end]
for key in ("startup_timeout_sec", "tool_timeout_sec", "enabled_tools"):
    section = re.sub(rf"(?m)^{key}\s*=.*\n?", "", section)
settings = '''
startup_timeout_sec = 30
tool_timeout_sec = 900
enabled_tools = ["gflow_auth_status", "flow_generate_image", "flow_generate_video", "flow_get_quote", "flow_get_result", "flow_list_jobs", "flow_get_asset_options", "flow_process_asset", "flow_get_edit_options", "flow_edit_video", "gflow_get_credits"]
'''
updated = text[:start] + settings + section.lstrip("\n") + text[end:]
tomllib.loads(updated)
temporary = config.with_suffix(".flow-tmp")
temporary.write_text(updated, encoding="utf-8")
temporary.replace(config)
print("Flow MCP configured; other server tables preserved.")
