#!/usr/bin/env python3
"""Sends the command files of this folder to the MCP server of Sweet Home 3D.

Start the server first (Help > Claude MCP server... > Start server) in an
empty home, then run:

    python3 replay.py                 # every step, in order
    python3 replay.py 01 02 03        # only these steps
    python3 replay.py --folder DIR    # another Sweet Home 3D application folder

The application folder holds the files mcp-port and mcp-token written by the
server. Uses only the Python standard library.
"""
import glob
import json
import os
import sys
import urllib.request


def application_folder():
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        return os.path.join(os.environ.get("APPDATA", home), "eTeks", "Sweet Home 3D")
    if sys.platform == "darwin":
        return os.path.join(home, "Library", "Application Support", "eTeks", "Sweet Home 3D")
    return os.path.join(home, ".eteks", "sweethome3d")


def call(folder, tool, arguments):
    with open(os.path.join(folder, "mcp-port")) as port_file:
        port = port_file.read().strip()
    with open(os.path.join(folder, "mcp-token")) as token_file:
        token = token_file.read().strip()
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                       "params": {"name": tool, "arguments": arguments}}).encode()
    request = urllib.request.Request(
        "http://127.0.0.1:%s/mcp" % port, data=body,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json",
                 "Accept": "application/json, text/event-stream"})
    result = json.loads(urllib.request.urlopen(request, timeout=600).read()).get("result", {})
    return "\n".join(item["text"] for item in result.get("content", []) if item["type"] == "text")


def main():
    arguments = sys.argv[1:]
    folder = application_folder()
    if "--folder" in arguments:
        index = arguments.index("--folder")
        folder = arguments[index + 1]
        del arguments[index:index + 2]
    here = os.path.dirname(os.path.abspath(__file__))
    for path in sorted(glob.glob(os.path.join(here, "[0-9]*.json"))):
        name = os.path.basename(path)
        if arguments and not any(name.startswith(prefix) for prefix in arguments):
            continue
        with open(path) as step_file:
            commands = json.load(step_file)
        print("== " + name)
        print(call(folder, "apply_commands", {"commands": commands}))


if __name__ == "__main__":
    main()
