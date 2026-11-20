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
| `get_home` | Text description of levels, rooms (area, extent, centre, hidden ceiling or floor), walls, furniture and selection, with the ids `F#`/`W#`/`R#`. Optional filters `level`, `type` and `name` |
| `list_catalog` | Categories and item names of the furniture catalog |
| `search_catalog` | Best catalog matches for a query (`doors_and_windows` to search doors and windows) |
| `search_textures` | Best texture matches, for the `set_texture` command |
| `check_home` | Report of plan defects (unjoined walls, duplicates, rooms without door, doors out of walls...) with ids |
| `get_report` | Floor areas and perimeters by room, wall lengths and surfaces, furniture counts and prices |
| `get_plan_image` | PNG picture of the 2D plan of a level, or of a part of it enlarged |
| `get_3d_image` | PNG picture of the home in 3D, from its camera or from a view chosen for the picture, drawn by the OpenGL renderer or the ray tracer (see Pictures) |
| `get_3d_info` | The camera, the stored points of view, the levels, the views and the renderers `get_3d_image` accepts |
| `get_catalog_image` | The pictures of catalog pieces or textures, to choose between search results |
| `export_blender` | Writes the model and a Blender scene script in a folder, for a high quality render |
| `get_notes` | The review notes the user pinned to walls, furniture and rooms while visiting the home, with ids and coordinates (`docs/REVIEW_NOTES.md`) |
| `update_note` | Marks a note done with a reply, or deletes it |
| `apply_commands` | Runs a list of edit commands as one undoable change and returns a summary |
| `undo` / `redo` | Undoes or redoes the last change |

An id (`F12` furniture, `W3` walls, `R5` rooms, `L2` levels) belongs to its
item for good: it's a number given once and stored in a property of the item
(`assistantId`), saved with the home. Deleting an item doesn't change the ids
of the others, and its id isn't given to another item afterwards. Items added
by a call get their id at the end of it, and the answer of the commands that
build something names it. An id whose item was deleted is an error: the answer
names it (`No item has the id R54...`) and is flagged `isError`, while the
other commands of the call are still applied. The internal id of an object
(`HomeObject.getId()`, shown by `describe_item`) is accepted too.

Any command that takes ids also takes `"where"`, which designates every item
matching all the criteria given:

```json
{"action":"move_to_level","where":{"level":"Roof-gen","type":"furniture"},"level":"L2"}
{"action":"delete","where":{"tag":"scaffold","bbox":[0,0,500,400]}}
```

`level` is a name or an id, `tag` a tag of furniture, `name` a part of a name,
`type` one or several of `furniture`, `doors_and_windows`, `lights`, `stairs`,
`groups`, `all_furniture`, `walls`, `rooms`, and `bbox` a box `[x1,y1,x2,y2]`
that contains the item. An empty `where` designates nothing.

`get_home` lists every room and wall but only the first 80 pieces of
furniture. Its filters return a part of a large home, with the same ids:

| Argument | Values |
| --- | --- |
| `level` | The name of a level: only its rooms, walls and furniture |
| `type` | `rooms`, `walls`, `furniture` (with doors and windows) or `doors_and_windows`, several separated by commas |
| `name` | Only the rooms and furniture whose name contains this text |

A filtered description lists up to 500 pieces. A room is followed by
`ceiling hidden` or `floor hidden` when it isn't drawn in 3D, and a tilted
piece by its `pitch` or `roll`.

The commands are the ones of the assistant
protocol (`HomeAssistantContext.getCommandReference()`), at most 100 per call:

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
- design: `add_room_shape` (L, T, U rooms), `apply_style` (a decoration
  style for rooms), `replace_furniture` (swap a piece in place) - see
  `docs/DESIGN_TOOLS.md`;
- organise: `add_tags`, `remove_tags`, `set_tags`, `hide_layer`, `show_layer`,
  `select_layer` (a layer = the pieces of a tag or of a type);
- change: `move`, `rotate` (also `pitch` and `roll`, and both at 0 to set a
  tilted piece upright), `resize`, `set_color`, `set_texture`, `modify_room`,
  `set_elevation`, `set_visible`, `rename`, `duplicate`, `delete`,
  `reduce_detail`, `restore_detail`, `select`, `move_to_level`, `modify_wall`,
  `set_plan_visibility`, `modify_furniture_parts`;
- house and site: `add_roof` with `points`, `add_dormer`, `fit_walls_to_roof`,
  `add_slab`, `set_terrain`, `modify_level`, `delete_level`, `merge_levels`
  (see "Modeling a whole house" below);
- look: `set_camera` (a place and a direction, a `view` as in `get_3d_image`,
  or a stored `point_of_view`), `store_point_of_view` (keeps the camera in
  **3D view > Go to point of view**);
- check: `check_home`, `fix_home`, `home_report`, `search_catalog`,
  `search_textures`, `describe_item` (every property of one item).

### Rooms: ceiling, floor, name and shape

Every room is drawn with a flat ceiling, at the top of its walls or at the
height of its level. Under a sloped roof, over a terrace or for a paved area
outdoors that ceiling shows as a slab floating in the 3D view. `modify_room`
changes rooms that exist:

```json
{"action":"modify_room","id":"R12",
 "ceiling_visible":false, "floor_visible":true, "area_visible":true,
 "name":"Bonus Room", "points":[[0,0],[600,0],[600,400],[0,400]]}
```

The room is given by `id`, `ids`, `"target":"selection"` or its name
(`"room":"Patio"`; a name several rooms share is refused, with their ids).
Fields left out don't change; `points` reshapes one room at a time. The
change is one step of Undo with the rest of the call, kept as the before and
after states of the rooms like the other editing commands. The answer gives
the state of each room (`Modified R12 "Bonus Room": ceiling hidden, floor
visible, ...`) and `get_home` shows `ceiling hidden` afterwards.

### Textures with the same name

Several catalog textures may share a name (the default catalog and imported
libraries each have a "Grass"). `set_texture` takes the first one and says
so, with the others: `Applied the texture "Grass" (Floor) to 1 item. 3
textures are named "Grass": ...`. Add `"category":"<category>"`, or
`"catalog_id":"<id>"` for the textures of libraries that have one, to choose.
The answer always names the texture applied and its category.

`set_texture` also takes `"scale"` (2 = twice as large), `"angle"` in degrees
and `"offset_x"` / `"offset_y"` (fractions of the size of the texture).

## Modeling a whole house

These commands exist so a house and its site need only its real levels: no
helper level for a garage slab, a lawn or a roof.

### Levels

`get_home` lists each level with its id, elevation, height, floor thickness
and what it holds, `EMPTY` if nothing, and says when levels share a name:

```
- L4 "Roof-gen" at 524 cm, height 120, floor thickness 12: EMPTY, name shared with 2 other levels (designate it by its id)
```

- `delete_level` deletes an empty level. One that isn't empty is refused with
  the list of what is on it, unless `"move_contents_to":"<level>"` or
  `"delete_contents":true` is given.
- `merge_levels` moves everything from a level into another one and deletes
  it. Furniture keeps its height above the ground (its elevation changes by
  the difference between the levels); walls and rooms stand on the floor of
  the new level, which the answer says.
- `modify_level` changes `new_name`, `elevation`, `height`,
  `floor_thickness`, `viewable` and `elevation_index`.
- A level is designated by its id, or by its name when only one level has
  it: a name several levels share is refused with their ids. The `level`
  filter of `get_home` takes an id for one level, or a name for all the
  levels with it.

### Roofs

`add_roof` with `points` builds **one roof over any polygon**, as one piece
of furniture on the level given (the selected one by default):

```json
{"action":"add_roof","level":"Level 1","points":[[-10,-10],[1010,-10],[1010,610],[-10,610]],
 "eave_height":250,"overhang":40,"pitch":35,
 "edges":[{"type":"hip"},{"type":"gable"},{"type":"hip"},{"type":"gable"}],
 "gutters":true,"texture":"Red tiles"}
```

`points` are the outside corners of the walls, `eave_height` the top of
these walls above the floor of the level. `edges` has one item per edge (the
first one from point 1 to point 2): `hip` is an eave with a slope rising from
it, `gable` (or `shed`, `flat`) a vertical end. Each edge may have its own
`pitch` and `overhang`. Without `edges`, `"type"` chooses them: `hip`,
`gable` (gables at the short ends of each wing), `shed` (one slope from
`low_side`) or `flat`.

How the shape is computed: the polygon is covered by convex parts whose sides
lie along its edges - the polygon itself if it's convex, the largest
rectangles in it if its corners are square. Over each part a plane rises from
each eave and the roof is the lowest of them, which is the roof a straight
skeleton gives for a convex polygon. Where parts overlap the highest shows, so
the wings of an L, T or U shaped house meet in valleys and the ridge of a
narrow wing ends in the slope of a wider one, as such roofs are framed. This
is **not** a straight skeleton of the whole polygon when it isn't convex: a
straight skeleton gives slanted ridges between wings of different widths. A
polygon that is neither convex nor made of square corners is cut in convex
parts that may meet with a step; the answer says so, and one `add_roof` per
wing gives a better result.

Options: `texture` (with `texture_scale`, `texture_angle`) or `color`,
`gable_color`, `fascia` and `fascia_color`, `underside_color`, `soffit`,
`ridge_cap`, `gutters` with `downspouts`, `gable_walls` (the walls the roof
draws under its gables, on by default), `thickness`.

- `add_dormer` adds a dormer on a slope: `roof`, `side` (the eave it looks
  over: north, south, east, west, or `edge` with a number), `position` from 0
  (west or north end of that side) to 1, `width`, `height` of its front wall,
  `type` (gable, shed, hip, flat), `window` (a catalog window, placed in its
  front wall, which is cut around it). The roof is rebuilt with the dormer
  and is open under it.
- `fit_walls_to_roof` gives the outside walls under a roof the height of its
  underside. A wall under a peak is cut in two walls that slope up to it,
  since the top of a wall is one straight line. Use `"gable_walls":false` on
  the roof to see these walls rather than the ones of the roof.

In the plan, a roof is drawn as a thin dashed outline at its own level and at
the levels above, and isn't picked by a click there (select it in the
furniture list). `set_plan_visibility` changes this for any piece that rises
through the levels above its own: `draw`, `outline` or `hide`, stored in the
property `planOnUpperLevels` of the piece. What is tagged `roof` is outlined
by default.

### Slabs and floor heights

`add_slab` builds a slab of any shape as a piece of furniture: `points`,
`top` (height of its top above the floor of the level, negative under it),
`thickness`, `color` or `texture`, `name`. `"top_at":[[x,y,z],[x,y,z],[x,y,z]]`
tilts its top through three points, for a ramp or a sloped driveway.
`add_room` and `modify_room` take `"floor_offset"` and `"slab_thickness"`: a
slab with the finish of the floor replaces the floor of the room, which is
hidden. Furniture dragged over a slab in the plan is put on it.

`set_elevation` takes a negative elevation, and `check_home` doesn't report
slabs, the ground, roofs or pieces put under the floor on purpose.

### Ground

`set_terrain` builds the ground through points of known height:

```json
{"action":"set_terrain","points":[[-1500,-1500,220],[2500,-1500,220],[-1500,-300,0],[2500,-300,0],
 [-1500,2500,-90],[2500,2500,-90]],"areas":[{"points":[[0,1030],[400,1030],[400,2500],[0,2500]],"color":"#77777A"}]}
```

Heights are in centimeters, 0 being the floor of the level at the elevation
0; a `grid` of heights is accepted too. The points are joined by triangles
(Delaunay). `areas` give a color or a texture to parts of it. The 3D view of
Sweet Home 3D draws a flat ground at the height 0, except over what stands on
a level under it: the ground is therefore put on a level under the others
(`Terrain`, added if needed), where it replaces the flat ground over its
bounding rectangle. It is hidden in the plans of the other levels.
`{"action":"set_elevation","id":"F12","on_terrain":true}` puts a piece on the
ground at its place.

A slab whose top isn't above the height 0 is hidden by the flat ground where
no terrain replaces it; the answer of `add_slab` says so.

### Parts of custom furniture

`create_furniture` parts have three more shapes: `prism` (a polygon `points`
pushed up by `height`), `wedge` (`high_side` back, front, left or right, or
`ridge` x or y for a peak) and `extrude` (a bar along a `path` of points
x,y,z with a `width` and `height` or a `diameter`). Any part takes a
`texture` (with `texture_scale`, `texture_angle`) and `yaw`, `pitch`, `roll`.
The parts are kept with the piece, so `modify_furniture_parts` changes some
of them (`set` with an `index`, `add`, `remove`) without building it again.

### Walls

`modify_wall` changes `height`, `height_at_end` (a sloped top), `thickness`,
`arc_extent`, `top_color`, the ends (`x1`...`y2`) and the baseboards. A
baseboard is how one wall gets two finishes by height: a baseboard 90 cm high
with a brick texture under a wall side with siding.

## Pictures

Claude works better when it can look at what it did. `get_3d_image` takes:

| Argument | Values |
| --- | --- |
| `view` | `current` (default, the camera of the home), `top`, `south`, `north`, `east`, `west`, `south_east`, `south_west`, `north_east`, `north_west` (the target entirely in sight from that side, south being the bottom of the plan), `inside` (standing in a corner of a room, or in the room of a piece or in front of a wall) |
| `target` | With `view`: ids (`R1`, `F3`, `W2`, several separated by commas), `selection`, or `building` (the walls, without the garden). Default: the whole home |
| `point_of_view` | The name of a point of view stored in the home |
| `camera` | `{"x","y","height","look_at_x","look_at_y","look_at_height"}` in centimeters, heights above the floor of the level |
| `fov` | Field of view, 20 to 120 degrees (default 63, 85 inside) |
| `levels` | `all` (default) or `selected`: the selected level and the ones under it, to look into a floor from above |
| `level` | The name of the level to select for the picture |
| `renderer` | `auto` (default: `opengl`, or the ray tracer if OpenGL can't draw), `opengl` (a few seconds), `raytrace` (much slower in a large home) |
| `shadows`, `ambient_occlusion` | OpenGL renderer, both `true` by default |
| `quality` | Ray tracer: `low` (default) or `high` (at most 1000 x 750 pixels) |
| `timeout` | Ray tracer: seconds after which it is stopped and an error comes back (default: no limit) |
| `width`, `height` | 800 x 500 by default, at most 1600 x 1200 |
| `save_to`, `replace` | Absolute path of a `.png` file written at up to 4096 x 4096 pixels; a copy 1000 pixels wide comes back. Needs **Allow changes to this home** |
| `store_as` | Name under which the view of the picture is kept in the points of view of the home; `"point_of_view":"<name>"` draws the same view again after changes, for before / after pictures. Needs **Allow changes to this home** |
| `compare_with`, `compare_mode`, `compare_opacity` | Absolute path of a photo (PNG or JPEG): the picture returned shows the home at the left and the photo at the right, or with `"compare_mode":"overlay"` the photo drawn over the picture at the given opacity (0.5 by default), stretched to its size |

A view, a point of view or a camera given to `get_3d_image` is used for that
picture only: the camera of the user doesn't move. The `set_camera` command
moves it. Walls and ceilings hide what is indoors from the outside views; for
a room or a piece indoors use `inside`, or `top` with `"levels":"selected"`.
The answer comes with a line which tells the renderer, the options, the time
and the place of the camera.

Renderers:

- `opengl` is the renderer of `docs/OPENGL_RENDERER.md` in a context without
  window: 8 samples per pixel, shadows and ambient occlusion unless turned
  off, the light sources of the lamps in their room. It is used whichever
  renderer the 3D view uses.
- `raytrace` is the photo renderer (SunFlow) at its fastest quality, or at its
  best one with `"quality":"high"`: slower, with reflections.
- `auto` is `opengl`, whichever renderer the 3D view of the user uses: the
  ray tracer took minutes in homes with large models, longer than MCP clients
  wait for an answer. If OpenGL can't draw, the ray tracer does and the answer
  says so; `"timeout":N` bounds it. `-Dcom.eteks.sweethome3d.opengl.images=false` turns the OpenGL
  pictures off.

Measured on the reference home (435 pieces, 25.8 million vertices) through a
running server, while the application displayed the same home with the OpenGL
renderer. Native Windows, GeForce GTX 1660 Ti, Java 8, 3 GB heap, one call each:

| Picture | Time |
| --- | --- |
| OpenGL with shadows and ambient occlusion, 1200 x 750: the house from a corner, a room from the inside, a floor from the top | 5.3 s, 4.6 s, 2.8 s |
| OpenGL with shadows and ambient occlusion, 1600 x 1000, a room from the inside | 3.3 s |
| OpenGL without shadow, 800 x 500, the whole home | 5.3 s |
| OpenGL with shadows and ambient occlusion, 4000 x 2500 saved in a file | 7.3 s |
| Ray tracer, low quality, 600 x 400, a room from the inside | 149.2 s |

Each picture describes a copy of the home again, which is most of the time
of an OpenGL picture. The ray tracer ran out of memory in one of its threads
during its picture, which came back with a black square: in a home of this
size, with this heap, only the OpenGL renderer is usable.

Under WSLg, where sending models to the graphics card is slow (see
`docs/OPENGL_RENDERER.md`): 1.2 to 5.6 s without shadow, 8 to 35 s with
shadows (31 s and 66 s for the first picture after launch, while the home was
still loading), 71 s for the ray tracer.

`get_plan_image` takes `level` (the name of a level), and `area`
(`{"x","y","width","height"}` in centimeters) or `target` (ids or `building`)
to draw a part of the plan enlarged, up to 6 times its default scale. With
one of these, the plan is drawn black on white by a plan which isn't
displayed; without them it is the plan as the window shows it.

`get_catalog_image` returns the icons (256 pixels at most) of up to 8 catalog
pieces or textures named exactly.

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
  `export_blender` writes into the folder given, `get_3d_image` writes the
  `.png` file given by `save_to` (it refuses to overwrite a file without
  `"replace":true`) and
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
- `viewcontroller/HomeViewpoints` - the cameras of the standard views and of
  the views from the inside, computed from the bounds of the items.
  `HomePane.create3DImage` applies them to a copy of the home and renders it
  with `HomeComponent3D.createOpenGLImage` or `PhotoRenderer`.
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
