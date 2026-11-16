# Connecting Claude to a Home (MCP server)

Sweet Home 3D can expose the home you are editing to Claude - Claude Code,
Claude Desktop, or any other [Model Context Protocol](https://modelcontextprotocol.io)
client running on the same computer. Claude can then describe the home, search
the furniture catalog and edit the home with the same undoable commands as the
built-in design assistant (`docs/AI_ASSISTANT.md`). No API key is involved: the
client talks to Sweet Home 3D, not the other way round.

## Use it

1. Open a home and choose **Help > Claude MCP server...**, then **Start server**.
2. Connect Claude, once:
   - **Connect Claude Desktop** adds Sweet Home 3D to the servers of Claude
     Desktop (its `claude_desktop_config.json`, after asking; a copy of the
     previous file is kept beside it as `.before-sweethome3d`). Under Windows
     the place of that file depends on how Claude Desktop was installed
     (`%LOCALAPPDATA%\Claude-Data\claude_<id>`, the store package folder, or
     `%APPDATA%\Claude`), so every one of these folders that exists gets the
     entry. Quit Claude Desktop completely, from its icon near the clock too,
     and reopen it.
   - **Connect Claude Code** runs `claude mcp add --scope user sweethome3d ...`
     for you. If the `claude` command isn't found, **Copy command** and run it
     in a terminal. Start a new Claude Code session.
3. Ask Claude things like "what rooms does my home have?", "add a desk and an
   office chair to the study", or "move the sofa 50 cm to the right".

Tick **Start this server each time a home is opened** to skip step 1 from then
on. Otherwise the server stops when the home window is closed and must be
started again from the menu.

Claude doesn't need to be told anything else: on connection it receives the
instructions of the server, the list of tools and the reference of the edit
commands. If Claude is asked about the home while no server runs, the tools
answer that the server must be started.

### How the connection works

The buttons register a small program, the *bridge* (`sweethome3d-mcp` in the
packaged application, otherwise `java -cp <jar> com.eteks.sweethome3d.swing.HomeMCPBridge`).
Claude launches it and talks to it on its standard input and output; the
bridge forwards each message to the server of the home, whose port and token
it reads in the Sweet Home 3D application folder. The registration therefore
contains no token and no port, and it stays valid from one session to the
next. It does contain the path of the application: connect again after moving
Sweet Home 3D or installing a new version in another folder. With several home
windows sharing their home, the bridge reaches the server started last.

Clients that take a URL can still connect directly to
`http://127.0.0.1:47391/mcp` with the header `Authorization: Bearer <token>`
(the token is in the file `mcp-token`, see Security), for example
`claude mcp add --transport http sweethome3d http://127.0.0.1:47391/mcp --header "Authorization: Bearer <token>"`.

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
| `get_report` | Floor areas and perimeters by room, wall lengths and surfaces, furniture counts and prices |
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
  `add_stairs` (straight, L or U shaped, with handrails), `add_furniture`, `add_door_or_window`, `add_label`,
  `add_dimension`, `auto_dimensions` (the size of every room);
- bring in: `add_picture` (a painting, poster or rug from an image file),
  `import_furniture` (a 3D model file), `import_texture` (an image as a
  texture), `set_background_image` (the picture of a floor plan behind the
  plan, which Claude can look at with `get_plan_image` and trace). Claude Code can read a photo you give it, then pick, size and
  color the closest catalog piece, or use these commands with the file path;
- make furniture: `create_furniture` (a piece built from boxes, cylinders,
  cones and spheres), `add_to_catalog`, `export_furniture_library` and
  `import_furniture_library` (`.sh3f` files) - see
  `docs/LAYERS_AND_FURNITURE_LIBRARIES.md` for the workflow;
- organise: `add_tags`, `remove_tags`, `set_tags`, `hide_layer`, `show_layer`,
  `select_layer` (a layer = the pieces of a tag or of a type);
- change: `move`, `rotate`, `resize`, `set_color`, `set_texture`,
  `set_elevation`, `set_visible`, `rename`, `duplicate`, `delete`,
  `reduce_detail`, `restore_detail`, `select`, `set_camera`, `move_to_level`;
- check: `check_home`, `fix_home`, `home_report`, `search_catalog`,
  `search_textures`.

## Security

- The server listens on `127.0.0.1` only; other computers can't reach it.
- Every request needs the bearer token. It is 256 random bits, created on first
  use and stored in the file `mcp-token` of the Sweet Home 3D application folder
  (`~/.eteks/sweethome3d` on Linux), readable by its owner only. Delete that
  file to get a new token.
- The bridge program reads that token file, so it works for the user who runs
  Sweet Home 3D only. The registration written in Claude's settings holds no
  secret. **Connect Claude Desktop** changes one entry (`mcpServers.sweethome3d`)
  of Claude Desktop's configuration file, after confirmation.
- The server only starts by itself if you ticked **Start this server each time
  a home is opened** (a file `mcp-autostart` in the application folder).
- Requests with a foreign `Origin` or `Host` header are refused, so a web page
  open in a browser can't relay requests to the server (cross-site requests,
  DNS rebinding).
- File access is limited to what you ask for: `add_picture`, `import_texture`
  and `import_furniture` read the image or model file whose absolute path is
  given (and, for a model, the materials and images it names in its own
  folder), `import_furniture_library` reads the `.sh3f` file given,
  `export_blender` writes into the folder given and
  `export_furniture_library` writes the `.sh3f` file given (it refuses to
  overwrite a file without `"replace":true`). `add_to_catalog` and
  `import_furniture_library` change the furniture catalog of the user. Writing and
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
- `swing/HomeMCPBridge` - the stdio program launched by clients (forwards to
  the server, answers the handshake and the tool list itself while no server
  runs via `HomeMCPHandler.handleOffline`), and the registration in Claude
  Desktop and Claude Code. `scripts/package-release.ps1` adds its launcher
  `sweethome3d-mcp` to the application image.
- `swing/HomePane.showMCPServerDialog` - the menu item and dialog.
- `HomeMCPServerTest` covers the handler, the transport checks and the token
  file. A real Claude Code client was run against the server on 2026-11-13
  (`claude -p ... --mcp-config`): it listed the tools, read the home, searched
  the catalog and added a bed.
