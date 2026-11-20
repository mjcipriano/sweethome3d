# Command Reference

The commands that edit a home. Claude sends them through the `apply_commands`
tool of the [MCP server](MCP_SERVER.md), and the built-in
[design assistant](AI_ASSISTANT.md) uses the same ones. You normally don't
write them yourself: you ask in plain words and the AI chooses the commands.
This page is for understanding what it can do, for checking what it did, and
for writing prompts that name a command.

The text Claude receives is produced by
`HomeAssistantContext.getCommandReference()`; this page follows it.

- [Conventions](#conventions)
- [Designating items](#designating-items)
- [Rooms and walls](#rooms-and-walls)
- [Levels and stairs](#levels-and-stairs)
- [Doors, windows and furniture](#doors-windows-and-furniture)
- [Changing items](#changing-items)
- [Colors, textures and styles](#colors-textures-and-styles)
- [Roofs](#roofs)
- [Slabs and ground](#slabs-and-ground)
- [Furniture made from parts](#furniture-made-from-parts)
- [Pictures, models and libraries](#pictures-models-and-libraries)
- [Tags and layers](#tags-and-layers)
- [Labels and dimensions](#labels-and-dimensions)
- [Camera](#camera)
- [Checks, reports and exports](#checks-reports-and-exports)

## Conventions

A call is a list of commands, each an object with an `action`:

```json
{"commands": [
  {"action": "add_rectangular_room", "name": "Study", "x": 0, "y": 0, "width": 400, "depth": 300},
  {"action": "add_furniture", "name": "Desk", "x": 200, "y": 40}
]}
```

- **One call is one step of Edit > Undo**, whatever the number of commands.
- At most **100 commands** per call.
- Lengths and coordinates are in **centimeters**. `x` grows to the right of
  the plan and `y` **downward**: north is `-y`, south is `+y`.
- Angles are in degrees, clockwise. At angle 0 the front of a piece faces
  `+y` (the bottom of the plan).
- Heights (`elevation`, `eave_height`, `top`) are above the floor of the
  level of the item, unless said otherwise.
- A field left out keeps its default, or leaves the item unchanged.
- Colors are `"#RRGGBB"`.
- Coordinates beyond 1,000,000 cm and sizes beyond 100,000 cm are refused.
- The answer lists what was applied, what was ignored and why, and the ids of
  what was added. A command that fails doesn't stop the others.

## Designating items

Every item has an id: `F12` for furniture, doors and windows, `W3` for walls,
`R5` for rooms, `L2` for levels. The id is given once and stored in the item
(property `assistantId`), saved with the home. It doesn't change when other
items are added or deleted, and it isn't given again after its item is
deleted. `get_home` lists the ids.

A command that changes items takes one of:

| Field | Designates |
| --- | --- |
| `"id":"F12"` | One item |
| `"ids":["F12","W3"]` | Several items |
| `"target":"selection"` | What is selected in the application |
| `"names":["Sofa"]` | Furniture by name |
| `"where":{...}` | Every item that matches all the criteria given |

The criteria of `where`:

| Criterion | Matches |
| --- | --- |
| `"level"` | The items of that level (a name or an id) |
| `"tag"` | The furniture with that tag |
| `"name"` | The items whose name contains this text |
| `"type"` | `furniture`, `doors_and_windows`, `lights`, `stairs`, `groups`, `all_furniture`, `walls` or `rooms`; several may be given |
| `"bbox":[x1,y1,x2,y2]` | The items inside that box |

```json
{"action":"move_to_level","where":{"level":"Old","type":"furniture"},"level":"L2"}
{"action":"delete","where":{"tag":"scaffold","bbox":[0,0,500,400]}}
```

An empty `where` designates nothing. An id whose item was deleted is reported
as an error (`No item has the id R54...`), and the other commands of the call
are still applied.

## Rooms and walls

| Command | What it does |
| --- | --- |
| `add_rectangular_room` | A room **and** its walls. `x`,`y` = top left corner of the inside, `width`, `depth`. Or `"next_to":"R1"` (an id or a name) with `"side":"north\|south\|east\|west"` and `"offset"` to put it against a room. A wall that already stands along a side is used again. Options: `"walls":false`, `thickness`, `height`, `floor_color` |
| `add_room_shape` | An L, T or U shaped room with its walls: `shape`, `width`, `depth` (its bounding box), `cut_width`, `cut_depth`. See [Design tools](DESIGN_TOOLS.md#room-shapes) |
| `add_room` | A room from `points` `[[x,y],...]`, without walls. Options: `floor_color`, `"ceiling_visible":false`, `floor_offset` with `slab_thickness` (see [Slabs](#slabs-and-ground)) |
| `add_wall` | One wall: `x1`,`y1`,`x2`,`y2`, `thickness`, `height` |
| `modify_room` | Changes rooms: `name`, `points` (one room at a time), `ceiling_visible`, `floor_visible`, `area_visible`, `floor_offset`. The room may also be given by `"room":"<name>"` |
| `modify_wall` | Changes walls: `height`, `height_at_end` (a sloped top; 0 levels it), `thickness`, `arc_extent` in degrees (0 straightens it), `top_color`, `x1`...`y2`, `baseboard` |

```json
{"action":"add_rectangular_room","name":"Kitchen","next_to":"Living room","side":"east","width":380,"depth":420}
{"action":"modify_room","id":"R12","ceiling_visible":false,"name":"Bonus room"}
{"action":"modify_wall","id":"W4","baseboard":{"height":90,"thickness":2,"texture":"Small red bricks","side":"left"}}
```

Every room is drawn with a flat ceiling. Under a sloped roof, over a terrace
or for a paved area outdoors, hide it with `"ceiling_visible":false`:
otherwise it shows as a slab at the height of the level.

A baseboard as high as needed, with a color or a texture, is how one wall
gets two finishes by height - a brick base under siding. `"baseboard":false`
removes it.

## Levels and stairs

| Command | What it does |
| --- | --- |
| `add_level` | Adds a level above the others and selects it: `name`, `elevation`, `height` |
| `select_level` | New items go to the selected level: `name` (a name or an id) |
| `modify_level` | `new_name`, `elevation`, `height`, `floor_thickness`, `viewable`, `elevation_index` |
| `delete_level` | Deletes an empty level. One that isn't empty is refused with the list of what is on it, unless `"move_contents_to":"<level>"` or `"delete_contents":true` |
| `merge_levels` | Moves everything `from` a level `into` another one and deletes it. Furniture keeps its height above the ground |
| `move_to_level` | Moves furniture, walls or rooms to `level` |
| `add_stairs` | Stairs from the selected level to the next one, which they cut open: `x`, `y`, `angle`, `width`, `shape` (`straight`, `L`, `U`, `spiral`), `turn` (`left`, `right`), `railing` (`true`, `"left"`, `"right"`). Options: `length`, `tread`, `rise`, `color` |

A level is designated by its id, or by its name when no other level has it: a
name shared by several levels is refused, with their ids. Add the upper level
before the stairs; the answer gives the floor size the stairs take.

## Doors, windows and furniture

| Command | What it does |
| --- | --- |
| `add_furniture` | A catalog piece by its **exact** name: `name`, `x`, `y`, `angle`, `width`, `depth`, `elevation` |
| `add_door_or_window` | A catalog door or window: `name`, `x`, `y`, `angle`. Give a point of the center line of the wall and the direction of the wall as the angle |
| `replace_furniture` | Swaps pieces for another catalog piece at the same place, angle, level and tags: `name`, `"keep_size":true` |
| `duplicate` | Copies of furniture, each shifted by `dx`,`dy`: `count` |
| `search_catalog` | The best catalog matches for `query`, reported in the answer |

Furniture can only be added by a name of the catalog: the `search_catalog`
and `list_catalog` tools find one, and `get_catalog_image` shows it.

## Changing items

| Command | What it does |
| --- | --- |
| `move` | `dx`,`dy` (relative) or `x`,`y` (absolute) |
| `rotate` | `angle` (absolute) or `by` (added); `pitch` or `roll` tilts a piece, and both at 0 set it upright |
| `resize` | `width`, `depth`, `height` |
| `set_elevation` | `elevation` above the floor of the level (negative: under it), or `"on_terrain":true` to put the piece on the ground built by `set_terrain` |
| `set_visible` | `"visible":true\|false` |
| `rename` | `name` |
| `delete` | Deletes the items |
| `select` | Selects items in the application |
| `set_plan_visibility` | How the plans of the levels above a piece show it: `"value":"draw\|outline\|hide"` |
| `describe_item` | Every property of one item, reported in the answer |
| `reduce_detail` / `restore_detail` | Shows the reduced model of heavy pieces in the 3D view, or the full one |

## Colors, textures and styles

| Command | What it does |
| --- | --- |
| `set_color` | `color` of furniture; of a wall (both sides, or `"part":"left"\|"right"`); of a room (its floor, or `"part":"ceiling"`) |
| `set_texture` | `texture` of rooms, walls and furniture, with `part` (`floor`, `ceiling`, `left`, `right`). Options: `scale` (2 = twice as large), `angle`, `offset_x`, `offset_y` |
| `search_textures` | The best texture matches for `query` |
| `apply_style` | Colors the floor, the ceiling and the facing wall sides of rooms in a named style; `"textures":false` keeps plain colors |
| `list_styles` | The styles |

Several catalog textures may share a name. `set_texture` takes the first one
and says so with the others; add `"category":"<category>"` or
`"catalog_id":"<id>"` to choose. The answer always names the texture applied.

The left side of a wall is at the left of someone walking from its start to
its end.

## Roofs

| Command | What it does |
| --- | --- |
| `add_roof` with `points` | One roof over any polygon, as one piece on a level |
| `add_dormer` | A dormer on a slope of such a roof, with its window |
| `fit_walls_to_roof` | Gives the walls under a roof the height of its underside |
| `auto_roof` | A simple roof over the walls of the selected level, on a level named `Roof` above them (the older command) |

```json
{"action":"add_roof","level":"First floor",
 "points":[[-20,-20],[1020,-20],[1020,740],[-20,740]],
 "eave_height":120,"pitch":40,"overhang":50,"type":"gable",
 "texture":"Red tiles","texture_scale":2,"gutters":true,"ridge_cap":true}
```

| Field of `add_roof` | Meaning |
| --- | --- |
| `points` | The outside corners of the walls it covers |
| `level` | The level it stands on (default: the selected one) |
| `eave_height` | Height of the top of these walls above the floor of the level |
| `pitch` | Slope in degrees |
| `overhang` | How far it goes beyond the walls |
| `type` | `hip`, `gable` (gables at the ends of each wing), `shed` (one slope rising from `low_side`) or `flat` |
| `edges` | Instead of `type`, one item per edge, the first from point 1 to point 2: `{"type":"hip"}` is an eave with a slope rising from it, `{"type":"gable"}` a vertical end. An edge may have its own `pitch` and `overhang` |
| `texture`, `texture_scale`, `texture_angle`, `color` | The covering |
| `gable_color`, `fascia`, `fascia_color`, `underside_color`, `soffit`, `ridge_cap` | The finish |
| `gutters`, `downspouts` | Gutters along the eaves, with their downspouts |
| `gable_walls` | The walls the roof draws under its gables, on by default |
| `thickness` | Thickness of the roof |

How the shape is computed: the polygon is covered by convex parts - itself if
it is convex, the largest rectangles in it if its corners are square. Over
each part a plane rises from each eave and the roof is the lowest of them.
Where parts overlap, the highest shows: the wings of an L, T or U shaped
house meet in valleys, and the ridge of a narrow wing ends in the slope of a
wider one. This equals a straight skeleton for a convex polygon only. A
polygon that is neither convex nor made of square corners is cut in parts
that may meet with a step; the answer says so, and one `add_roof` per wing
gives a better result.

`add_dormer`: `roof` (its id), `side` (the eave it looks over: `north`,
`south`, `east`, `west`, or `"edge":N`), `position` from 0 (the west or north
end of that side) to 1, or `offset` in cm, `width`, `height` of its front
wall, `type` (`gable`, `shed`, `hip`, `flat`), `window` (a catalog window),
`setback`, `pitch`. The roof is built again with the dormer and is open under
it. The windows of the dormers of a roof get new ids each time a dormer is
added to it.

`fit_walls_to_roof`: `roof` (its id). The outside walls under the roof get the
height of its underside; `"all":true` includes the inside walls, `ids`
chooses walls. A wall under a peak is cut in two walls, since the top of a
wall is one straight line.

In the plan, a roof is a thin dashed outline at its own level and at the
levels above, and a click there doesn't select it (select it in the furniture
list). `set_plan_visibility` changes this.

## Slabs and ground

| Command | What it does |
| --- | --- |
| `add_slab` | A slab of any shape, as a piece of furniture: a driveway, a garage floor, a terrace, a ramp |
| `set_terrain` | The ground, through points of known height |

`add_slab`: `points`, `top` (height of its top above the floor of the level,
which may be negative), `thickness`, `color` or `texture` (with
`texture_scale`, `texture_angle`), `side_color`, `name`.
`"top_at":[[x,y,z],[x,y,z],[x,y,z]]` tilts its top through three points.

`add_room` and `modify_room` take `"floor_offset":N` with
`"slab_thickness":N`: a slab with the finish of the floor replaces the floor
of the room, which is hidden.

```json
{"action":"set_terrain","texture":"Grass",
 "points":[[-1400,-1200,60],[2800,-1200,60],[-400,760,0],[1900,760,0],[-1400,2400,-140],[2800,2400,-140]],
 "areas":[{"points":[[0,1030],[400,1030],[400,2500],[0,2500]],"color":"#77777A"}]}
```

`set_terrain`: `points` `[x,y,z]`, joined by triangles, or
`"grid":{"x","y","width","depth","heights":[[z,...],...]}`. `z` is in
centimeters, 0 being the floor of the level at the elevation 0. `texture` or
`color` for the whole, `areas` for parts with a finish of their own. Calling
it again replaces the ground.

- The 3D view of Sweet Home 3D draws a flat ground at the height 0, except
  over what stands on a level under it. The ground therefore goes to a level
  under the others (`Terrain`, added if needed) and is hidden in the plans of
  the other levels.
- The ground is left open under the rooms it reaches and under their walls,
  so the floors show. Call `set_terrain` again after adding or moving rooms.
- A slab whose top isn't above the height 0 is hidden by the flat ground
  where no terrain replaces it; the answer of `add_slab` says so.

## Furniture made from parts

`create_furniture` builds a piece that is in no catalog.

```json
{"action":"create_furniture","name":"Chimney","x":760,"y":250,"elevation":300,"structure":true,
 "parts":[{"shape":"box","z":0,"width":70,"depth":70,"height":220,"texture":"Small red bricks"},
          {"shape":"box","z":220,"width":86,"depth":86,"height":8,"color":"#B8B4AC"},
          {"shape":"cylinder","z":228,"diameter":30,"height":35,"color":"#A0522D"}]}
```

Parts are in centimeters in the frame of the piece: `x` to its right, `y`
toward its front, `z` up. `x`,`y` are the **center** of a part and `z` its
**bottom**. At most 150 parts.

| Shape | Fields |
| --- | --- |
| `box` | `width`, `depth`, `height` |
| `cylinder`, `cone` | `diameter`, `height`, `top_scale` (0 to 1) to taper it, `"axis":"x"\|"y"` to lay it down |
| `sphere` | `diameter` |
| `prism` | A polygon `points` (relative to the `x`,`y` of the part) pushed up by `height` |
| `wedge` | A box cut in a slope: `high_side` (`back`, `front`, `left`, `right`), or `"ridge":"x"\|"y"` for a peak along its middle |
| `extrude` | A bar along a `path` of points `[x,y,z]` with a `width` and a `height`, or a `diameter`; `"closed":true` for a loop |

Any part takes `color`, or `texture` with `texture_scale` and
`texture_angle`, `rotation` (degrees around the vertical) and `yaw`, `pitch`,
`roll`.

Options of the piece: `x`, `y`, `angle`, `elevation`, `tags`,
`"catalog":"<category>"` to save it in the furniture catalog (`"place":false`
to save it without adding it to the home), and `"structure":true` for a part
of the building or of its site - a chimney, a post, a fence - which
`check_home` then doesn't report as furniture out of its room.

`modify_furniture_parts` changes the parts of such a piece without building
it again: `set` (with an `index`, 1 = the first part), `add`, `remove`, or
`parts` to replace them all.

## Pictures, models and libraries

| Command | What it does |
| --- | --- |
| `add_picture` | Furniture showing an `image`: `"mode":"panel"` (a painting, a poster, a screen), `"box"` or `"floor"` (a rug). Missing sizes follow the proportions of the image |
| `import_furniture` | A 3D model file (OBJ, DAE, 3DS, KMZ, ZIP) as a new piece: `path`, `name`, `width` or `height` |
| `import_texture` | An `image` added to the textures catalog and applied to the ids given: `name`, `width` (the real size of what it shows), `part` |
| `set_background_image` | The picture of a floor plan behind the plan, to trace it: `image`, `width` (real width), `x`, `y`. `remove_background_image` removes it |
| `add_to_catalog` | Saves pieces of the home in the furniture catalog: `category` |
| `export_furniture_library` | Writes a `.sh3f` library from pieces or from a `category`: `path`, `name`, `"install":true`, `"replace":true` |
| `import_furniture_library` | Installs a `.sh3f` library: `path` |

Paths are absolute paths on the computer that runs Sweet Home 3D. See
[Tags, layers and furniture made by the AI](LAYERS_AND_FURNITURE_LIBRARIES.md).

## Tags and layers

| Command | What it does |
| --- | --- |
| `add_tags`, `remove_tags`, `set_tags` | Tags of furniture, doors and windows: `tags`. Instead of ids: `type` and / or `with_tag` |
| `hide_layer`, `show_layer`, `select_layer` | Every piece with a `tag`, or of a `type`; `"tag":"untagged"` = the pieces without tag |

## Labels and dimensions

| Command | What it does |
| --- | --- |
| `add_label` | `text`, `x`, `y` |
| `add_dimension` | A measurement line: `x1`,`y1`,`x2`,`y2`, `offset` |
| `auto_dimensions` | The dimension lines that give the size of every room of the level, or of `ids` |

## Camera

| Command | What it does |
| --- | --- |
| `set_camera` | Moves the camera of the user: a place (`x`, `y`, `height`, `look_at_x`, `look_at_y`, `mode` `visitor` or `aerial`), a `view` (`top`, `south`, `north_east`... of the home or of `ids`, or `inside` a room), or a stored `point_of_view` |
| `store_point_of_view` | Keeps the camera under a `name` in **3D view > Go to point of view** |

The place of the virtual visitor is also where the VR walk-through and the
game mode start. Camera moves aren't undoable.

## Checks, reports and exports

| Command | What it does |
| --- | --- |
| `check_home` | Reports plan defects with ids: walls that don't meet, duplicates, rooms without door, doors out of walls, furniture under the floor or out of rooms, heavy models |
| `fix_home` | Repairs the defects marked fixable; `"types":["wall_gap"]` limits it |
| `home_report` | Floor areas, wall lengths and surfaces, furniture counts and prices |
| `set_unit_prices` | `paint`, `flooring`, `ceiling`, `baseboard`, `currency`: the report then ends with a budget |
| `export_elevation` | Writes a SVG elevation drawing: `side`, `path`, `section` |

`check_home` doesn't report roofs, slabs, the ground, pieces put on the ground
with `on_terrain`, pieces created with `"structure":true`, or pieces given a
negative elevation on purpose.
