# Connecting Claude to a Home (MCP server)

Sweet Home 3D can expose the home you are editing to Claude - Claude Desktop,
Claude Code, or any other [Model Context Protocol](https://modelcontextprotocol.io)
client running on the same computer. Claude can then describe the home, search
the furniture catalog, look at pictures of the plan and of the 3D view, and
edit the home with undoable commands. No API key is involved: the client talks
to Sweet Home 3D, not the other way round.

![The demo house, built through the MCP server](images/house-3d.jpg)

- [Set it up](#set-it-up)
- [What to ask](#what-to-ask)
- [Tools](#tools)
- [Ids and filters](#ids-and-filters)
- [Pictures](#pictures)
- [Security](#security)
- [When something goes wrong](#when-something-goes-wrong)
- [Implementation](#implementation)

Related pages: the [command reference](MCP_COMMANDS.md) lists every edit
command, and the [house modeling tutorial](HOUSE_MODELING_TUTORIAL.md) builds
the house above step by step.

## Set it up

1. Open a home and choose **Tools > Claude MCP server...**
2. Click **Start server**. The dialog shows `Running at http://127.0.0.1:47391/mcp`.

   ![The Claude MCP server dialog](images/mcp-server-dialog.png)

3. Connect Claude, once:
   - **Connect Claude Desktop** adds Sweet Home 3D to the servers of Claude
     Desktop (its `claude_desktop_config.json`, after asking; a copy of the
     previous file is kept beside it as `.before-sweethome3d`). Then quit
     Claude Desktop completely - from its icon near the clock too - and
     reopen it.
   - **Connect Claude Code** runs `claude mcp add --scope user sweethome3d ...`
     for you. If the `claude` command isn't found, click **Copy command** and
     run it in a terminal. Then start a new Claude Code session.
4. Ask Claude about the home: "what rooms does my home have?"

The two boxes of the dialog:

| Box | Effect |
| --- | --- |
| **Allow changes to this home** | Unticked, Claude can read the home and get pictures, but can't change anything nor write files |
| **Start this server each time a home is opened** | Skips steps 1 and 2 from then on. Otherwise the server stops when the home window is closed |

Claude doesn't need to be told anything else: on connection it receives the
instructions of the server, the list of tools and the reference of the edit
commands. If Claude is asked about the home while no server runs, the tools
answer that the server must be started.

Under Windows the place of the configuration file of Claude Desktop depends
on how it was installed (`%LOCALAPPDATA%\Claude-Data\claude_<id>`, the store
package folder, or `%APPDATA%\Claude`), so every one of these folders that
exists gets the entry.

### How the connection works

The buttons register a small program, the *bridge* (`sweethome3d-mcp` in the
packaged application, otherwise
`java -cp <jar> com.eteks.sweethome3d.swing.HomeMCPBridge`). Claude launches
it and talks to it on its standard input and output; the bridge forwards each
message to the server of the home, whose port and token it reads in the Sweet
Home 3D application folder. The registration therefore contains no token and
no port, and it stays valid from one session to the next.

It does contain the path of the application: **connect again after moving
Sweet Home 3D or installing a new version in another folder**. While Claude
runs, its bridge program keeps the application folder in use. Before
deleting or replacing that folder, quit Claude, or click **Release program
folder** in the dialog: the bridge programs stop within a few seconds, and
Claude shows Sweet Home 3D as disconnected until Claude is started again.

With several home windows, the bridge reaches the server started last. The
first server gets port 47391 and the next ones a free port shown in their
dialog; `-Dcom.eteks.sweethome3d.mcp.port=<port>` sets another fixed port.

Clients that take a URL can connect directly to `http://127.0.0.1:47391/mcp`
with the header `Authorization: Bearer <token>` (the token is in the file
`mcp-token`, see [Security](#security)):

```bash
claude mcp add --transport http sweethome3d http://127.0.0.1:47391/mcp \
  --header "Authorization: Bearer <token>"
```

## What to ask

Plain requests work; Claude chooses the tools.

| You ask | Claude uses |
| --- | --- |
| "What rooms does my home have, and how large are they?" | `get_home` |
| "Add a desk and an office chair to the study" | `search_catalog`, `apply_commands` |
| "Move the sofa 50 cm to the right" | `apply_commands` (`move`) |
| "Show me the kitchen" | `get_3d_image` with `"view":"inside"` |
| "Is anything wrong with the plan?" | `check_home`, then `fix_home` |
| "How much flooring do I need?" | `get_report` |
| "Add a hip roof with a dormer over the entrance" | `add_roof`, `add_dormer`, `get_3d_image` |
| "Make a walnut sideboard, 160 cm wide" | `create_furniture`, `get_3d_image` |
| "Here is a photo of the house: check the roof against it" | `get_3d_image` with `compare_with` |
| "Do my review notes" | `get_notes`, `apply_commands`, `update_note` |
| "Undo that" | `undo` |

Every `apply_commands` call is one step of **Edit > Undo** in the
application. Claude works better when it looks at what it did: ask it to
check its work with a picture.

## Tools

| Tool | What it does |
| --- | --- |
| `get_home` | Text description of the levels, rooms (area, extent, center), walls, furniture and selection, with ids. Optional filters `level`, `type`, `name`, `where` and `fields` |
| `list_catalog` | Categories and item names of the furniture catalog |
| `search_catalog` | Best catalog matches for a query (`doors_and_windows` to search doors and windows) |
| `search_textures` | Best texture matches, for the `set_texture` command |
| `get_catalog_image` | The pictures of up to 8 catalog pieces or textures, to choose between search results |
| `check_home` | Report of plan defects (unjoined walls, duplicates, rooms without door, doors out of walls or above the roof, walls beyond a setback...) with ids, each an error, a warning or an info. Options `min_severity`, `ignore`, `ignore_tags`, `remember`, `forget` ([details](MCP_COMMANDS.md#checks-reports-and-exports)) |
| `get_report` | Floor areas and perimeters by room, wall lengths and surfaces, furniture counts and prices |
| `get_plan_image` | PNG picture of the 2D plan of a level, or of a part of it enlarged |
| `get_3d_image` | PNG picture of the home in 3D (see [Pictures](#pictures)) |
| `get_3d_info` | The camera, the stored points of view, the levels, the views and the renderers `get_3d_image` accepts |
| `help` | Every command of `apply_commands` by area with a line on each; `"command":"add_fence"` gives the syntax of one command and the commands related to it, `"search":"gate"` finds a word |
| `get_capabilities` | The version, what it has (styles of fences, types of gates, coordinate systems, formats) and its limits |
| `get_sun_study_image` | One picture of the home at several hours of a day, or at several dates of a year, with the shadows of the sun ([below](#sun-study)) |
| `sun_study` | Opens or closes your window **Sun and shadows**, plays its animations, and chooses the renderer and the shadows of the 3D view on screen ([below](#sun-study)) |
| `export_blender` | Writes the model and a Blender scene script in a folder, for a high quality render |
| `get_notes` | The review notes pinned to walls, furniture and rooms during a visit ([Review notes](REVIEW_NOTES.md)) |
| `update_note` | Marks a note done with a reply, or deletes it |
| `apply_commands` | Runs a list of edit commands as one undoable change and returns a summary with the ids of what was created ([Command reference](MCP_COMMANDS.md)). `"file":"<absolute path>"` runs the commands of a JSON file instead, up to 2000. A command that fails is named by its number and doesn't stop the others, unless `"stop_on_error":true` |
| `undo` / `redo` | Undoes or redoes the last change, or the last `steps` changes |
| `get_history` | The changes undo and redo would revert or apply again, yours and Claude's |
| `get_changes` | What you added, deleted or modified by hand since Claude last asked |
| `get_elevation_image` | A picture of one side of the building drawn square on, with its dimensions ([below](#elevations)) |
| `save_version`, `list_versions`, `compare_versions`, `restore_version` | Versions of the home ([below](#versions-and-saving)) |
| `save_home` | Saves the home in its file if you allowed it, or writes a copy in a new file |
| `list_homes`, `open_home`, `new_home`, `switch_home` | The homes open in the application, and which one Claude works on |
| `export_home` | Writes the home as PDF, SVG, OBJ, CSV, a 3D web page or a 360 degree panorama |
| `add_note` | Pins a review note to an item or a place |
| `start_visit` | Opens the game mode or the walk-through in your browser |

The tools can't reach the network. They write the file of the home only if
you tick **Allow saving this home in its file, and replacing files** in the
dialog; without it, saving stays your decision (**File > Save**) and Claude
can only keep versions and write copies in new files.

## Versions and saving

*"Save a version before you move the kitchen."* - *"What changed since this
morning?"* - *"Go back to the version before the new roof."*

| Tool | What it does |
| --- | --- |
| `save_version` | Saves a copy of the home with a `label` in the folder of versions beside its file, the one of **File > Versions...**. The home and its file don't change |
| `list_versions` | The versions, the most recent first, numbered from 1 |
| `compare_versions` | From a `version` (its number or a part of its label) to the home now, or to another version (`with`): counts and areas, then the items added, removed, moved, resized or renamed, with their ids. `"picture":true` adds both states side by side |
| `restore_version` | Replaces the levels and the items of the home by the ones of a version, as **one change that undo reverts**. The state before it is saved as a version first (`"keep_current":false` skips that). The file of the home isn't written |
| `save_home` | Saves the home in its file: needs **Allow saving**. With `"copy_to":"<path>.sh3d"` it writes a copy in a new file instead, and the home keeps its file and its unsaved state |

![A version at the left, the home now at the right](images/compare-versions.jpg)

A home needs a file before it can have versions: save it once yourself.
Replacing a file that exists (`"replace":true`) needs **Allow saving** too.
To let Claude try something on a copy: `save_home` with `copy_to`, then
`open_home` on the copy.

`open_home`, `new_home` and `switch_home` open or choose a home in its own
window of Sweet Home 3D; from the next call Claude reads and edits that one,
with the same permissions. The home open before stays open. Ids belong to
their home.

`get_changes` lists what you did by hand between two calls of Claude - a
wall you drew, a sofa you moved - with ids. When there are such changes, the
answers of `get_home`, `apply_commands`, `check_home`, `undo` and `redo` end
with a note that says so.

## Exports

`export_home` takes a `format` and a `path`:

| Format | Writes |
| --- | --- |
| `pdf` | The pages of **File > Print to PDF**: plan, furniture list, 3D view |
| `svg` | The plan of the selected level |
| `obj` | The 3D model, with its `.mtl` file and its textures beside it |
| `csv` | The furniture list |
| `web_page` | A new folder with the 3D visit for a browser, in the folder `path` |
| `panorama` | A new folder with a 360 degree picture from the place of the virtual visitor and its page, in the folder `path` (`width` from 1024 to 8192) |

A file that exists is replaced only with `"replace":true`. Pictures are made
by `get_3d_image` and `get_plan_image`, elevation drawings in SVG by the
`export_elevation` command, schedules in CSV by `export_schedule`, and a
Blender scene by `export_blender`.

## Ids and filters

`get_home` gives every item an id: `F12` furniture, `W3` walls, `R5` rooms,
`L2` levels, `P1` lines of the plan (property lines, setbacks, easements).

```
- Levels (3), from the lowest:
    - L3 "Terrain" at -170 cm, height 169, floor thickness 1: 0 rooms, 0 walls, 1 piece of furniture
    - L1 "Ground floor" at 0 cm, height 250, floor thickness 12: 6 rooms, 18 walls, 41 pieces of furniture (selected: new items go here)
    - L2 "First floor" at 262 cm, height 250, floor thickness 12: 6 rooms, 21 walls, 21 pieces of furniture
- Rooms (12), total floor area 167.72 m2:
    - R1 Living room: 25.2 m2, x 0 to 600, y 0 to 420 cm, center (300, 210) [Ground floor]
    - R2 Kitchen: 15.96 m2, x 620 to 1000, y 0 to 420 cm, center (810, 210) [Ground floor]
```

An id belongs to its item for good: it is a number given once and stored in a
property of the item (`assistantId`), saved with the home. Deleting an item
doesn't change the ids of the others, and its id isn't given to another item
afterwards. Items added by a call get their id at the end of it, and the
answer of the commands that build something names it. The internal id of an
object (`HomeObject.getId()`, shown by `describe_item`) is accepted too.

`get_home` lists every room and wall but only the first 80 pieces of
furniture. Its filters return a part of a large home, with the same ids:

| Argument | Values |
| --- | --- |
| `level` | An id for one level, or a name for every level with it |
| `type` | `rooms`, `walls`, `furniture` (with doors and windows) or `doors_and_windows`, several separated by commas |
| `name` | Only the rooms and furniture whose name contains this text; `Pine\|Oak` for either |
| `where` | The same criteria as the commands: `{"level","tag","name","type","bbox":[x1,y1,x2,y2]}`. The answer is a flat list of the items that match them all |
| `fields` | With `where`, what to list of each item: `name`, `position`, `size`, `level`, `elevation`, `angle`, `tags`, `type`, `area`, `points`, `color`. The id is always given |

```json
{"where":{"bbox":[-600,800,1900,1500],"name":"Pine|Oak"},"fields":["name","position"]}
```

A filtered description lists up to 500 pieces. A level is listed with what it
holds, `EMPTY` if nothing, and a note when other levels share its name. A room
is followed by `ceiling hidden` or `floor hidden` when it isn't drawn in 3D,
and a tilted piece by its `pitch` or `roll`.

## Pictures

### The plan

`get_plan_image` draws a level. With an `area` or a `target`, a part of the
plan is drawn enlarged, black on white, up to 6 times the default scale.

| Argument | Values |
| --- | --- |
| `level` | The name of the level to draw (default: the selected one) |
| `area` | `{"x","y","width","height"}` in centimeters |
| `target` | Ids (`R1`, `F3`...), or `building` for the walls without the garden |
| `max_size` | Largest side in pixels (default 1000, at most 2000) |

| `{"level":"Ground floor","target":"building"}` | `{"level":"First floor","target":"building"}` |
| --- | --- |
| ![Plan of the ground floor](images/plan-ground.png) | ![Plan of the first floor, the roofs as dashed outlines](images/plan-first.png) |

A roof is drawn as a dashed outline at its level and at the levels above, so
the rooms under it stay readable. `{"level":"Ground floor","target":"R2"}`
draws one room with what is around it:

![The kitchen enlarged](images/plan-kitchen.png)

### The 3D view

`get_3d_image` draws the home from a view chosen for the picture: the camera
of the user doesn't move.

| `{"view":"south_east","target":"building"}` | `{"view":"top","levels":"selected","level":"Ground floor"}` |
| --- | --- |
| ![The house from the south east](images/tut-5-dormers.jpg) | ![The ground floor from the top](images/view-top-ground.jpg) |

| `{"view":"inside","target":"R1"}` | `{"view":"inside","target":"R2"}` |
| --- | --- |
| ![Inside the living room](images/view-inside-living.jpg) | ![Inside the kitchen](images/view-inside-kitchen.jpg) |

| Argument | Values |
| --- | --- |
| `view` | `current` (default, the camera of the home), `top`, `south`, `north`, `east`, `west`, `south_east`, `south_west`, `north_east`, `north_west` (the target entirely in sight from that side, south being the bottom of the plan), `inside` (standing in a corner of a room, or in the room of a piece, or in front of a wall) |
| `target` | With `view`: ids (`R1`, `F3`, `W2`, several separated by commas), `selection`, or `building` (the walls, without the garden). Default: the whole home |
| `point_of_view` | The name of a point of view stored in the home |
| `camera` | `{"x","y","height","look_at_x","look_at_y","look_at_height"}` in centimeters, heights above the floor of the level |
| `fov` | Field of view, 20 to 120 degrees (default 63, 85 inside) |
| `levels` | `all` (default) or `selected`: the selected level and the ones under it, to look into a floor from above |
| `level` | The name of the level to select for the picture |
| `renderer` | `auto` (default), `opengl` or `raytrace` |
| `shadows`, `ambient_occlusion` | OpenGL renderer, both `true` by default |
| `quality` | Ray tracer: `low` (default) or `high` (at most 1000 x 750 pixels) |
| `timeout` | Ray tracer: seconds after which it is stopped and an error comes back |
| `width`, `height` | 800 x 500 by default, at most 1600 x 1200 |
| `save_to`, `replace` | Absolute path of a `.png` file written at up to 4096 x 4096 pixels; a copy 1000 pixels wide comes back |
| `store_as` | Name under which the view of the picture is kept in the points of view of the home, to draw the same view again after changes with `point_of_view` |
| `compare_with`, `compare_mode`, `compare_opacity` | A photo to compare the picture with (below) |

`save_to` and `store_as` need **Allow changes to this home**.

Walls and ceilings hide what is indoors from the outside views. For a room or
a piece indoors, use `inside`, or `top` with `"levels":"selected"`. A roof
built by `add_roof` stands on the level of the rooms it covers: to see that
level from the top, hide the roofs first with
`{"action":"hide_layer","tag":"roof"}` and show them again afterwards.

![The first floor from the top, the roofs hidden](images/view-top-first.jpg)

The answer comes with a line that tells the renderer, the options, the time
and the place of the camera:

```
Drawn by the OpenGL renderer (antialiasing, shadows, ambient occlusion), 1200 x 750 pixels, in 2.6 s.
Seen from: view south_east of building, aerial view at x 2377, y 1947, height 1646 cm, yaw 135 degrees,
pitch 30 degrees (positive looks down), field of view 63 degrees. All levels.
```

### Renderers

| OpenGL without shadow (0.8 s) | OpenGL, shadows and ambient occlusion (0.8 s) | Ray tracer, low quality (9.8 s) |
| --- | --- | --- |
| ![Without shadow](images/render-plain.jpg) | ![With shadows](images/render-shadows.jpg) | ![Ray traced](images/render-raytrace.jpg) |

The times are those of these 900 x 560 pictures of the demo house under WSLg.

- `opengl` is the renderer of [OpenGL renderer](OPENGL_RENDERER.md) in a
  context without window: 8 samples per pixel, shadows and ambient occlusion
  unless turned off, the light sources of the lamps in their room. It is used
  whichever renderer the 3D view of the user uses.
- `raytrace` is the photo renderer (SunFlow) at its fastest quality, or at its
  best one with `"quality":"high"`: slower, with reflections.
- `auto` is `opengl`. If OpenGL can't draw, the ray tracer does and the answer
  says so; `"timeout":N` bounds it.
  `-Dcom.eteks.sweethome3d.opengl.images=false` turns the OpenGL pictures off.

Measured on a large home (435 pieces, 25.8 million vertices) through a
running server, while the application displayed the same home with the OpenGL
renderer. Native Windows, GeForce GTX 1660 Ti, Java 8, 3 GB heap, one call
each:

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
size, with this heap, only the OpenGL renderer is usable. Under WSLg the same
home took 1.2 to 5.6 s without shadow, 8 to 35 s with shadows, and 71 s with
the ray tracer.

### Sun study

Everything the window **3D view > Sun and shadows...** sets
([guide](VIEW_3D_NAVIGATION.md#sun-at-a-date-and-a-time)) can be set by
Claude:

| What | How |
| --- | --- |
| Date, time of day | The `set_sun` command: `{"action":"set_sun","date":"2026-06-21","time":"17:30"}` |
| Light of the 3D view from the sun, false colors | `set_sun` with `"follow":true`, `"false_colors":true`, a number of lux, or `false` |
| North, latitude, longitude, time zone | The `set_compass` command: `"north_direction"` in degrees clockwise from the top of the plan, `"latitude"`, `"longitude"`, `"time_zone":"America/New_York"`; `"georeference":true` takes the place and the true north of the GPS position of the home |
| The window itself | The `sun_study` tool: `{"window":"open"}` or `{"window":"close"}` |
| Its animations | `sun_study` with `"play":"day"` (sunrise to sunset of the date), `"play":"year"` (the days of the year at the time) or `"play":"stop"` |
| Renderer and shadows of the 3D view on screen | `sun_study` with `"renderer":"opengl"` or `"java3d"`, and `"shadows":true` or `false` |

`sun_study` without argument tells whether the window is open, what plays,
and how the 3D view is drawn. An animation moves the date or the time of the
home until it is stopped, and needs **Allow changes to this home**, as do the
renderer and the shadows, which are your preferences for all homes. Opening
and closing the window, and stopping an animation, don't need it.

Claude doesn't see an animation: to look at the sun through a day or a year
it calls `get_sun_study_image`, which returns one picture of the home at
several instants, each with its date, its time and the height of the sun
under it, and the list of the sun height, its azimuth, the length of shadows
and the light on the ground at each instant. The date, the time and the 3D
view of the home don't change.

![Six hours of the day of the June solstice](images/sun-study-day.jpg)

| Argument | Meaning |
| --- | --- |
| `sweep` | `day` (default): the hours of one day. `year`: dates of the year at the same time. `hours`: one picture colored by the hours of direct sun of the day |
| `steps` | Number of pictures: 2 to 12 for a day (6 by default); 2, 3, 4, 6 or 12 for a year (4 by default: the equinoxes and the solstices; 12: the 21st of each month). For `hours`, the instants counted, 4 to 96 (one each 20 minutes by default) |
| `max_hours` | `hours`: the hours shown by the last color (default: the length of the day) |
| `date` | The day shown, or the year of a year sweep (default: the date of the sun of the home) |
| `time` | Year sweep: the local time (default: the time of the sun of the home) |
| `from`, `to` | `day` and `hours`: the first and the last local time. By default from sunrise to sunset |
| `false_colors` | `true`, a maximum in lux, or `false` (default: as the 3D view of the home) |
| `view`, `target`, `camera`, `point_of_view`, `levels`, `renderer`, `shadows`, `save_to`... | As in `get_3d_image`; `width` and `height` are the ones of each picture (400 x 260 by default, at most 800 x 600; for `hours` 800 x 520, at most 1600 x 1200) |

![The afternoon of the equinoxes and the solstices in false colors](images/sun-study-year.jpg)

With `"sweep":"hours"`, the home is drawn at instants spread through the day,
and each point is colored by the time the sun reaches it, from dark blue (never)
to red (all day): where to plant, to sit or to put solar panels. The answer
adds the share of the surfaces in the picture that get less than 2 hours,
2 to 6 hours and more. It needs the OpenGL renderer, and takes a few seconds
on a graphics card (20 to 30 s under WSLg).

![Hours of direct sun on the day of the December solstice](images/sun-hours.jpg)

`set_sun` warns when the sun isn't computed for the GPS position of the home
(the compass was changed after `set_georeference`), and when the time zone is
more than three hours away from the solar time of the longitude.

### Elevations

`get_elevation_image` draws one `side` of the building (`south`, `north`,
`east`, `west`) square on, from the real 3D models: roofs, dormers, doors,
windows and textures. The camera stands so far that lines going away from
it stay parallel to within 2 %. The width of the walls and the heights of
the levels and of the top are drawn in meters; `"dimensions":false` leaves
them out, and `save_to` writes the picture.

![The south elevation of the demo house with its width and its heights](images/elevation-picture.jpg)

The answer gives the scale in centimeters for a pixel, so other sizes can be
measured in the picture. Trees and furniture in front of the building show.

### Comparing with a photo

`"compare_with":"<absolute path of a PNG or JPEG>"` returns the picture of
the home at the left and the photo at the right, to check roof lines and
heights. `"compare_mode":"overlay"` draws the photo over the picture instead,
stretched to its size, at `compare_opacity` (0.5 by default).

![The home at the left, the reference picture at the right](images/compare.jpg)

In this example a ray-traced picture of the same house stands in for the
photo. The overlay only stretches the photo: it doesn't match its
perspective, so choose a `view` or a `camera` close to where the photo was
taken.

### Catalog pictures

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
- File access is limited to what you ask for:

  | Reads | Writes |
  | --- | --- |
  | `add_picture`, `import_texture`, `set_background_image`: the image file given | `get_3d_image`: the `.png` file given by `save_to` |
  | `import_furniture`: the model file given, and the materials and images it names in its own folder | `export_blender`: the folder given |
  | `import_furniture_library`: the `.sh3f` file given | `export_furniture_library`: the `.sh3f` file given |
  | `get_3d_image`: the photo given by `compare_with` | `export_elevation`: the `.svg` file given |
  | `apply_commands`: the JSON file given by `file` | `save_version`: a new file in the folder of versions of the home |
  | `import_parcel`: the GeoJSON file given by `path` | `save_home` with `copy_to`: the `.sh3d` file given |
  | `open_home`: the `.sh3d` file given | `export_home`, `export_schedule`, `get_elevation_image`: the file or the folder given |
  | `compare_versions`, `restore_version`: the versions of the home | `save_home`: the file of the home, with **Allow saving** only |

  `get_3d_image` and `export_furniture_library` refuse to overwrite a file
  without `"replace":true`.
  `add_to_catalog` and `import_furniture_library` change the furniture
  catalog of the user. Writing and importing need **Allow changes to this
  home**. Writing the file of the home, and replacing a `.sh3d` file that
  exists, need **Allow saving this home in its file, and replacing files**,
  which is off each time the application starts. The tools can't read other
  files or reach the network. `start_visit` opens your web browser on the
  visit served by Sweet Home 3D on this computer. `sun_study` opens a window
of the application and, with **Allow changes to this home**, changes two of
your preferences: the renderer of the 3D view and its shadows.

## When something goes wrong

| What happens | What to do |
| --- | --- |
| Claude says the server must be started | Choose **Tools > Claude MCP server...** and **Start server**, or tick the box that starts it with each home |
| Claude Desktop doesn't list Sweet Home 3D | Quit it completely, from its icon near the clock too, and reopen it. If it still doesn't, click **Connect Claude Desktop** again |
| It worked, then stopped after an update | The registration holds the path of the application: connect again from the new version |
| The application folder can't be deleted or replaced | The bridge program launched by Claude (`sweethome3d-mcp`) runs from it: click **Release program folder** in **Tools > Claude MCP server...**, or quit Claude, then close Sweet Home 3D |
| "No item has the id ..." | The item was deleted. Ask Claude to call `get_home` again |
| "The tool ... has no argument ..." | Claude knows the tools of an older version of Sweet Home 3D. An argument the server doesn't know is refused with the list of the ones it has, instead of being dropped. Start a new conversation, or restart Claude, so it reads the tools again |
| A picture takes minutes | It was drawn by the ray tracer: ask for `"renderer":"opengl"`, or give a `timeout` |
| Claude can't change anything | Tick **Allow changes to this home** |
| Claude says saving isn't allowed | That is the default. Tick **Allow saving this home in its file, and replacing files**, or let it save versions and copies |
| Claude can't save a version | The home was never saved: choose **File > Save as...** once |

## Implementation

- `viewcontroller/HomeMCPHandler` - transport-independent JSON-RPC handling
  (`initialize`, `ping`, `tools/list`, `tools/call`; protocol revisions
  2025-06-18, 2025-03-26 and 2024-11-05) on top of `AssistantCommandExecutor`
  and `HomeAssistantContext`. Called in the event dispatch thread.
- `viewcontroller/HomeItemIds` - the ids of the items.
- `viewcontroller/Assistant*Commands` - the commands: `AssistantBuildingCommands`
  (rooms, walls, textures), `AssistantStructureCommands` (levels, walls, plan
  visibility), `AssistantShapeCommands` (roofs, dormers, slabs, ground),
  `AssistantRoofGeometry` and `AssistantRoofModel`, `AssistantMeshBuilder`,
  `AssistantFurnitureBuilder`, `AssistantLibraryCommands`,
  `AssistantLayerCommands`, `AssistantDesignCommands`, `AssistantSiteCommands`
  (lines of the plan, compass, georeference, parcels) with `HomeGeoreference`
  and `HomeRoofs` (ceilings and headroom under roofs), `AssistantArrangeCommands`
  (group, align, mirror, measure), `AssistantGardenCommands` with
  `AssistantRoomFurnisher`, `AssistantScheduleCommands` with `HomeQuantities`
  and `AssistantSymbols`, `AssistantSceneCommands` (lights, sun, doors,
  materials).
- `viewcontroller/HomeVersions`, `HomeContents` (the contents of a home put in
  place of others, for `restore_version`), `HomeChangeJournal` (what the user
  changes by hand between two calls).
- `swing/AssistantModelInspector` - the materials and the hinges of 3D models.
- `viewcontroller/HomeViewpoints` - the cameras of the standard views and of
  the views from the inside, computed from the bounds of the items.
  `HomePane.create3DImage` applies them to a copy of the home and renders it
  with `HomeComponent3D.createOpenGLImage` or `PhotoRenderer`.
- `swing/HomeMCPServer` - Streamable HTTP transport on the JDK `HttpServer`
  (POST only: no server-initiated stream, no session), token file, header checks.
- `swing/HomeMCPBridge` - the stdio program launched by clients (forwards to
  the server, answers the handshake and the tool list itself while no server
  runs via `HomeMCPHandler.handleOffline`, and sends
  `notifications/tools/list_changed` when the tools of the server differ from
  the ones it gave its client), and the registration in Claude
  Desktop and Claude Code. `scripts/package-release.ps1` adds its launcher
  `sweethome3d-mcp` to the application image.
- `swing/HomePane.showMCPServerDialog` - the menu item and dialog.
- Tests: `HomeMCPServerTest` (handler, transport checks, token file),
  `AssistantHouseModelingTest`, `AssistantCommandExecutorTest`,
  `AssistantLayersAndLibraryTest`, `AssistantDesignCommandsTest`. A real
  Claude Code client was run against the server on 2026-11-13
  (`claude -p ... --mcp-config`): it listed the tools, read the home, searched
  the catalog and added a bed. On 2026-11-20 one was run against the
  application under native Windows (Java 8, GeForce GTX 1660 Ti) through the
  bridge: it read the demo house, added a shed with a hip roof, ran
  `check_home` and described a 3D picture.
