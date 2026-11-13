# Connecting Claude to a Home (MCP server)

Sweet Home 3D can expose the home you are editing to Claude - Claude Code,
Claude Desktop, or any other [Model Context Protocol](https://modelcontextprotocol.io)
client running on the same computer. Claude can then describe the home, search
the furniture catalog and edit the home with the same undoable commands as the
built-in design assistant (`docs/AI_ASSISTANT.md`). No API key is involved: the
client talks to Sweet Home 3D, not the other way round.

## Use it

1. Open a home and choose **Help > Claude MCP server...**, then **Start server**.
2. Press **Copy command** and run it in a terminal. It looks like:

   ```
   claude mcp add --transport http sweethome3d http://127.0.0.1:47391/mcp --header "Authorization: Bearer <token>"
   ```

   Other clients take the same URL and header in their MCP configuration
   (`"type": "http"`, `"url"`, `"headers": {"Authorization": "Bearer <token>"}`).
3. Ask Claude things like "what rooms does my home have?", "add a desk and an
   office chair to the study", or "move the sofa 50 cm to the right".

The address and the token stay the same from one session to the next, so the
command only has to be run once. The server stops when the home window is
closed and must be started again from the menu; it never starts by itself.

Untick **Allow changes to this home** to let Claude read the home without being
able to modify it. Every `apply_commands` call is one step of **Edit > Undo**.

## Tools

| Tool | What it does |
| --- | --- |
| `get_home` | Text description of levels, rooms (area, extent, centre), walls, furniture and selection, with the ids `F#`/`W#`/`R#` |
| `list_catalog` | Categories and item names of the furniture catalog |
| `search_catalog` | Best catalog matches for a query (`doors_and_windows` to search doors and windows) |
| `search_textures` | Best texture matches, for the `set_texture` command |
| `check_home` | Report of plan defects (unjoined walls, duplicates, rooms without door, doors out of walls...) with ids |
| `get_plan_image` | PNG picture of the 2D plan of the selected level |
| `get_3d_image` | PNG picture of the home in 3D from its camera (move it with `set_camera`) |
| `export_blender` | Writes the model and a Blender scene script in a folder, for a high quality render |
| `apply_commands` | Runs a list of edit commands as one undoable change and returns a summary |
| `undo` / `redo` | Undoes or redoes the last change |

Ids designate the items of the last `get_home` call, even if the home changed
since: an item deleted meanwhile is simply not found, and items added later get
an id at the next `get_home`. The commands are the ones of the assistant
protocol (`HomeAssistantContext.getCommandReference()`), at most 30 per call:

- build: `add_rectangular_room` (room + walls, optionally `next_to` another
  room), `add_room`, `add_wall`, `add_level`, `select_level`, `auto_roof`,
  `add_stairs`, `add_furniture`, `add_door_or_window`, `add_label`,
  `add_dimension`;
- bring in: `add_picture` (a painting, poster or rug from an image file),
  `import_furniture` (a 3D model file), `import_texture` (an image as a
  texture). Claude Code can read a photo you give it, then pick, size and
  color the closest catalog piece, or use these commands with the file path;
- change: `move`, `rotate`, `resize`, `set_color`, `set_texture`,
  `set_elevation`, `set_visible`, `rename`, `duplicate`, `delete`,
  `reduce_detail`, `restore_detail`, `select`, `set_camera`;
- check: `check_home`, `fix_home`, `search_catalog`, `search_textures`.

## Security

- The server listens on `127.0.0.1` only; other computers can't reach it.
- Every request needs the bearer token. It is 256 random bits, created on first
  use and stored in the file `mcp-token` of the Sweet Home 3D application folder
  (`~/.eteks/sweethome3d` on Linux), readable by its owner only. Delete that
  file to get a new token.
- Requests with a foreign `Origin` or `Host` header are refused, so a web page
  open in a browser can't relay requests to the server (cross-site requests,
  DNS rebinding).
- File access is limited to what you ask for: `add_picture`, `import_texture`
  and `import_furniture` read the image or model file whose absolute path is
  given (and, for a model, the materials and images it names in its own
  folder), and `export_blender` writes into the folder given. Writing and
  importing need **Allow changes to this home**. The tools can't read other
  files, save the home, or reach the network.

With several home windows, the first server gets port 47391 and the next ones a
free port shown in their dialog. Set another fixed port with
`-Dcom.eteks.sweethome3d.mcp.port=<port>`.

## Implementation

- `viewcontroller/HomeMCPHandler` - transport-independent JSON-RPC handling
  (`initialize`, `ping`, `tools/list`, `tools/call`; protocol revisions
  2025-06-18, 2025-03-26 and 2024-11-05) on top of `AssistantCommandExecutor`
  and `HomeAssistantContext`. Called in the event dispatch thread.
- `swing/HomeMCPServer` - Streamable HTTP transport on the JDK `HttpServer`
  (POST only: no server-initiated stream, no session), token file, header checks.
- `swing/HomePane.showMCPServerDialog` - the menu item and dialog.
- `HomeMCPServerTest` covers the handler, the transport checks and the token
  file. A real Claude Code client was run against the server on 2026-11-13
  (`claude -p ... --mcp-config`): it listed the tools, read the home, searched
  the catalog and added a bed.
