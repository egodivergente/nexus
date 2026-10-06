"""Real MCP client for diagnostics and calls before Codex reloads its catalog."""
import argparse
import asyncio
import json
import os
from pathlib import Path
import sys

from launcher import ROOT, environment
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

async def run(args):
    ROOT.mkdir(parents=True, exist_ok=True)
    config = StdioServerParameters(
        command=sys.executable,
        args=[str(Path(__file__).with_name("launcher.py")), "mcp", "run", "--profile", "nexus"],
        env={**os.environ, **environment()},
        cwd=str(ROOT),
    )
    with (ROOT / "mcp-client-stderr.log").open("a", encoding="utf-8") as errors:
        async with stdio_client(config, errlog=errors) as (read, write):
            async with ClientSession(read, write, read_timeout_seconds=900) as session:
                initialized = await session.initialize()
                if args.action == "check":
                    tools = await session.list_tools()
                    status = await session.call_tool("gflow_auth_status", {"profile": "nexus"})
                    return {"server": initialized.server_info.model_dump(mode="json"),
                            "tools": [t.name for t in tools.tools],
                            "auth": status.model_dump(mode="json")}
                parameters = json.loads(Path(args.args_file).read_text(encoding="utf-8-sig")) if args.args_file else {}
                result = await session.call_tool(args.tool, parameters)
                return result.model_dump(mode="json")

def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("check")
    call = sub.add_parser("call")
    call.add_argument("tool")
    call.add_argument("--args-file")
    parser.add_argument("--output")
    args = parser.parse_args()
    result = asyncio.run(run(args))
    serialized = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        destination = Path(args.output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(serialized + "\n", encoding="utf-8")
    print(serialized)

if __name__ == "__main__":
    main()
