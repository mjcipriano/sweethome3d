#!/usr/bin/env python3
"""Calls tools of the MCP server of the application started by make-screenshots.sh.

Reads lines "<image name> <tool> <JSON arguments>" on its standard input, or
sends a file of commands with: mcp.py --commands <file.json>
Pictures returned by a tool are written to $SHOTS_OUT/<image name>.png.
"""
import base64
import json
import os
import sys
import urllib.request

FOLDER = os.path.join(os.environ["SHOTS_HOME"], ".eteks", "sweethome3d")
OUT = os.environ["SHOTS_OUT"]


def call(name, arguments, image=None):
    with open(os.path.join(FOLDER, "mcp-port")) as f:
        port = f.read().strip()
    with open(os.path.join(FOLDER, "mcp-token")) as f:
        token = f.read().strip()
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                       "params": {"name": name, "arguments": arguments}}).encode()
    request = urllib.request.Request(
        "http://127.0.0.1:%s/mcp" % port, data=body,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json",
                 "Accept": "application/json, text/event-stream"})
    result = json.loads(urllib.request.urlopen(request, timeout=900).read()).get("result", {})
    count = 0
    for item in result.get("content", []):
        if item["type"] == "text":
            print(item["text"][:int(os.environ.get("LIMIT", "400"))])
        else:
            count += 1
            path = os.path.join(OUT, "%s%s.png" % (image, "" if count == 1 else "-%d" % count))
            with open(path, "wb") as f:
                f.write(base64.b64decode(item["data"]))
            print("image " + os.path.basename(path))
    if result.get("isError"):
        print("ERROR from " + name)
        sys.exit(1)


if len(sys.argv) > 2 and sys.argv[1] == "--commands":
    with open(sys.argv[2]) as f:
        call("apply_commands", {"commands": json.load(f)})
else:
    for line in sys.stdin:
        line = line.strip()
        if line and not line.startswith("#"):
            image, tool, arguments = line.split(" ", 2)
            call(tool, json.loads(arguments), image)
