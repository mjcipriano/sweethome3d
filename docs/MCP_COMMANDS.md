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
- [Site: lot lines, setbacks and georeference](#site-lot-lines-setbacks-and-georeference)
- [Furniture made from parts](#furniture-made-from-parts)
- [Pictures, models and libraries](#pictures-models-and-libraries)
- [Tags and layers](#tags-and-layers)
- [Labels and dimensions](#labels-and-dimensions)
- [Camera](#camera)
- [Checks, reports and exports](#checks-reports-and-exports)
- [Undo groups](#undo-groups)

## Conventions

A call is a list of commands, each an object with an `action`:

```json
{"commands": [
  {"action": "add_rectangular_room", "name": "Study", "x": 0, "y": 0, "width": 400, "depth": 300},
  {"action": "add_furniture", "name": "Desk", "x": 200, "y": 40}
]}
```

- **One call is one step of Edit > Undo**, whatever the number of commands.
  [Undo groups](#undo-groups) make one step of several calls.
- At most **100 commands** per call. A longer list goes in a file: the
  `apply_commands` tool takes `{"file":"<absolute path>"}` in place of
  `commands`, a JSON file holding the list (or an object with `commands`), up
  to 2000 commands, applied as one step of undo.
- Lengths and coordinates are in **centimeters**. `x` grows to the right of
  the plan and `y` **downward**: north is `-y`, south is `+y`.
- Angles are in degrees, clockwise. At angle 0 the front of a piece faces
  `+y` (the bottom of the plan).
- Heights (`elevation`, `eave_height`, `top`) are above the floor of the
  level of the item, unless said otherwise.
- A field left out keeps its default, or leaves the item unchanged.
- Colors are `"#RRGGBB"`.
- Coordinates beyond 1,000,000 cm and sizes beyond 100,000 cm are refused.
- The answer lists what was applied, what was ignored and why. It ends with
  `Ids of what was created:` and the id and name of every item the call
  added - walls of a room, windows of a dormer, stakes of a lot line - so no
  `get_home` is needed to find them. A command that fails doesn't stop the
  others.

## Designating items

Every item has an id: `F12` for furniture, doors and windows, `W3` for walls,
`R5` for rooms, `L2` for levels, `P1` for the lines of the plan. The id is given once and stored in the item
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
| `"name"` | The items whose name contains this text; `"Pine\|Oak"` matches either |
| `"type"` | `furniture`, `doors_and_windows`, `lights`, `stairs`, `groups`, `all_furniture`, `walls`, `rooms` or `polylines`; several may be given |
| `"bbox":[x1,y1,x2,y2]` | The items inside that box |

```json
{"action":"move_to_level","where":{"level":"Old","type":"furniture"},"level":"L2"}
{"action":"delete","where":{"tag":"scaffold","bbox":[0,0,500,400]}}
{"action":"select","where":{"name":"Pine|Oak","bbox":[0,0,2000,1500]}}
```

`select` answers with the list of what it selected, with ids: with a `bbox`
it is the way to see what stands in an area. The `get_home` tool takes the
same `where`, and `"fields":["name","level","area"]` to list only these of
each item.

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
| `modify_room` | Changes rooms: `name`, `points` (one room at a time), `ceiling_visible`, `floor_visible`, `area_visible`, `floor_offset`, `ceiling` and `flat_ceiling_at` (see [ceilings under a roof](#ceilings-under-a-roof)). The room may also be given by `"room":"<name>"` |
| `modify_wall` | Changes walls: `height`, `height_at_end` (a sloped top; 0 levels it), `thickness`, `arc_extent` in degrees (0 straightens it), `top_color`, `x1`...`y2`, `baseboard` |

```json
{"action":"add_rectangular_room","name":"Kitchen","next_to":"Living room","side":"east","width":380,"depth":420}
{"action":"modify_room","id":"R12","ceiling_visible":false,"name":"Bonus room"}
{"action":"modify_wall","id":"W4","baseboard":{"height":90,"thickness":2,"texture":"Small red bricks","side":"left"}}
```

Every room is drawn with a flat ceiling. Over a terrace or for a paved area
outdoors, hide it with `"ceiling_visible":false`: otherwise it shows as a
slab at the height of the level. Under a sloped roof, give the room a
[ceiling that follows the roof](#ceilings-under-a-roof).

A baseboard as high as needed, with a color or a texture, is how one wall
gets two finishes by height - a brick base under siding. `"baseboard":false`
removes it.

## Levels and stairs

| Command | What it does |
| --- | --- |
| `add_level` | Adds a level above the others and selects it: `name`, `elevation`, `height` |
| `select_level` | New items go to the selected level: `name` (a name or an id) |
| `modify_level` | `new_name`, `elevation`, `height`, `floor_thickness`, `viewable`, `elevation_index` |
| `delete_level` | Deletes an empty level, given by `name`, `id` or `ids` (several levels). One that isn't empty is refused with the list of what is on it, unless `"move_contents_to":"<level>"` or `"delete_contents":true` |
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
| `select` | Selects items in the application, by `ids`, `names` or `where`, and lists them |
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
| `add_dormer` | A dormer or a cross gable on a slope of such a roof, with its windows |
| `modify_dormer` | Changes one dormer; the others and their windows are kept |
| `remove_dormer` | Removes one dormer and its windows |
| `modify_roof` | Changes the pitch, the eaves, the overhang or one edge of such a roof |
| `fit_walls_to_roof` | Gives the walls under a roof the height of its underside |
| `headroom` | The height of the ceiling of the rooms under a roof |
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
| `edges` | Instead of `type`, one item per edge, the first from point 1 to point 2: `{"type":"hip"}` is an eave with a slope rising from it, `{"type":"gable"}` a vertical end, `{"type":"abut"}` an end against the wall of a taller part of the building: no overhang and no gable wall there. An edge may have its own `pitch` and `overhang` |
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

### Dormers

```json
{"action":"add_dormer","roof":"F51","side":"south","offset":300,"width":240,"height":150,
 "type":"gable","window":"Small window","windows":2}
{"action":"add_dormer","roof":"F51","side":"north","offset":200,"width":300,"height":140,
 "setback":0,"ridge":"match","windows":[{"name":"Small window","width":90,"height":110}]}
{"action":"modify_dormer","roof":"F51","index":1,"width":260,"windows":3}
{"action":"remove_dormer","roof":"F51","index":2}
```

| Field of `add_dormer` | Meaning |
| --- | --- |
| `roof` | The id of the roof. It may be left out when the home has one roof |
| `side` | The eave the dormer looks over: `north`, `south`, `east`, `west`, or `"edge":N` |
| `offset` | Centimeters from the west or north end of that side (for an `edge`, from its start) to the **center** of the dormer |
| `position` | The same place as a fraction, from 0 to 1. A value above 1 is read as centimeters, and the answer says so |
| `width`, `height` | Its width and the height of its front wall |
| `type` | `gable`, `shed`, `hip` or `flat` |
| `setback` | How far its front wall stands back from the eave (default 40); 0 puts it in line with the wall of the house |
| `pitch` | The slope of its own roof |
| `ridge` | `"match"` raises its ridge to the top of the roof: a cross gable |
| `window` | The catalog window of its front wall |
| `windows` | A number, for that many `window` side by side, or a list where each item is a name or `{"name","width","height","sill"}` |

The opening a dormer cuts in the roof ends at its side walls, whatever the
width of the dormer. Its windows are doors and windows of the home; a dormer
without `window` and without a name in `windows` is built without window, and
the answer says so.

`modify_dormer` takes `index` (the dormers are numbered from 1, in the order
`describe_item` on the roof lists them) and any field of `add_dormer`.
`remove_dormer` takes `index`; the dormers after it are numbered again. Both
leave the other dormers alone: their windows keep their ids, their place and
the size you may have given them by hand. The windows of the changed dormer
move with it.

### Changing a roof

```json
{"action":"modify_roof","roof":"F51","pitch":35,"eave_height":140,"raise_walls":true}
{"action":"modify_roof","roof":"F52","edge":{"index":2,"type":"abut"}}
```

`modify_roof` changes a roof built by `add_roof` with `points` without
building it again: any field of `add_roof`, or `edge` (one edge) / `edges` (a
list), each with the `index` of the edge - the edge 1 goes from point 1 to
point 2 - and its `type`, `pitch` or `overhang`. Its dormers and their windows
are kept. `"raise_walls":true` moves the top of the walls under the roof by as
much as its eaves moved.

### Walls under a roof

`fit_walls_to_roof`: `roof` (its id). The outside walls under the roof get the
height of its underside; `"all":true` includes the inside walls, `ids`
chooses walls. The top of a wall is one straight line, so a wall is cut where
the roof changes above it: under a peak, and at the sides of a dormer or of a
cross gable, where the wall goes up to the roof of the dormer. The walls
added stay at the level of the wall they continue and joined to it, and the
answer gives their ids. Run it again after the roof or its dormers change.

### Ceilings under a roof

```json
{"action":"modify_room","id":"R7","ceiling":"follow_roof"}
{"action":"modify_room","id":"R9","ceiling":"follow_roof","flat_ceiling_at":260}
{"action":"headroom","ids":["R7","R9"]}
```

`"ceiling":"follow_roof"` (for `add_room` and `modify_room`) gives a room
under a roof built by `add_roof` a ceiling under its slopes, dormers
included, in place of the flat one. `"flat_ceiling_at":N` levels it at N cm
above the floor where the roof is higher: a cathedral ceiling with a flat
middle. `"ceiling":"flat"` gives back the flat ceiling of the level. The
ceiling is a piece of the home named after the room; it is built again when
the roof, its dormers or the room change, and isn't drawn in the plan.

`headroom` reports, for the rooms given by `ids` or for every room under a
roof, the lowest and the highest height of the ceiling and the floor area
at least 213 cm (7 ft) high. `describe_item` on a room gives the same.

In the plan, slabs and pieces made of parts are drawn seen from the top,
whatever the preference for furniture icons. A roof is a thin dashed outline at its own level and at the
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
  so the floors show. It is built again by itself when rooms or walls are
  added, moved or deleted, also by hand in the plan, and after undo.
- A slab whose top isn't above the height 0 is hidden by the flat ground
  where no terrain replaces it; the answer of `add_slab` says so.

## Site: lot lines, setbacks and georeference

| Command | What it does |
| --- | --- |
| `add_polyline` | A line drawn in the plan: an easement, a utility line, a fence line |
| `add_property_line` | The closed line of a lot, with its area |
| `add_setback` | The line buildings must keep behind, at a distance from a property line |
| `modify_polyline` | Changes a line: `points`, `color`, `thickness`, `dash`, `name` |
| `set_compass` | The north of the plan, the place and the look of the compass |
| `set_georeference` | The place of the plan on the Earth |
| `import_parcel` | Draws the lot lines of a survey from GeoJSON or Esri JSON |
| `get_coordinates` | The latitude, longitude and projected coordinates of points of the plan |

```json
{"action":"add_property_line","points":[[-600,-700],[1900,-700],[1900,1500],[-600,1500]],
 "label":"Lot 12","stakes":true}
{"action":"add_setback","from":"P1","distance":300}
{"action":"add_polyline","points":[[-600,900],[1900,900]],"name":"Sewer easement",
 "color":"#2060C0","dash":"dash_dot","thickness":2}
```

These are lines of the plan, not furniture: they get ids `P1`, `P2`..., are
listed by `get_home`, matched by `where` with `"type":"polylines"`, described
by `describe_item` and deleted by `delete`. They never count as furniture out
of a room.

- `add_polyline`: `points`, `color`, `thickness`, `dash` (`solid`, `dashed`,
  `dotted`, `dash_dot`), `"closed":true`, `name`, `level`, `arrows` (`start`,
  `end`, `both`), and `"elevation":N` to show it in the 3D view at that
  height. In a georeferenced home, `"geo":[[lon,lat],...]` replaces `points`.
- `add_property_line`: `points` and an optional `label`. The answer gives its
  length and its area in m2, sq ft and acres. `"stakes":true` adds stakes at
  its corners and a string between them in the 3D view, which follow the
  ground (`stake_height`, 90 cm by default).
- `add_setback`: `from` (the id of a property line), `distance`, and `side`
  (`inside` by default). The answer gives the area left to build on.

`check_home` reports the walls that stand beyond a setback line or a
property line, as errors.

![A lot line shown in the 3D view by its stakes and its string](images/site-lines.jpg)

### North and georeference

```json
{"action":"set_compass","north_direction":12,"latitude":33.95,"longitude":-84.55}
{"action":"set_georeference","lat":33.95,"lon":-84.55,"x":0,"y":0,"rotation":12}
{"action":"import_parcel","srs":"EPSG:2240","geojson":{"type":"Polygon","coordinates":[[[2180338,1437084],[2180420,1437084],[2180420,1437180],[2180338,1437180],[2180338,1437084]]]}}
{"action":"get_coordinates","points":[[0,0],[1000,0]],"srs":"EPSG:2240"}
```

- `set_compass`: `north_direction` is the angle in degrees, clockwise, from
  the top of the plan to the north. The sun of the 3D view and of photos
  follows it. Also `latitude`, `longitude`, `time_zone`, `x`, `y`,
  `diameter`, `"visible":false`.
- `set_georeference`: the point `x`,`y` of the plan is at `lat`,`lon`, and
  the true north is `rotation` degrees clockwise from the top of the plan.
  Or `"points":[{"lat","lon","x","y"},{...}]`, two points known both ways. It
  sets the compass to the true north and is stored with the home.
- `import_parcel`: `geojson` (a Polygon, MultiPolygon, Feature or
  FeatureCollection), or `esrijson` (with `rings`), or the `path` of such a
  file. `srs` is the system of its coordinates. The lot lines become property
  lines, placed by the georeference of the home; in a home without one, the
  middle of the parcel goes to `x`,`y` with the north at the top, and the
  home gets that georeference.
- `get_coordinates`: `points` of the plan, or `ids` of items, with an
  optional `srs` for projected coordinates beside latitude and longitude.

| `srs` | System |
| --- | --- |
| `EPSG:4326`, `EPSG:4269` | Longitude and latitude in degrees (the default) |
| `EPSG:3857` | Web Mercator, meters |
| `EPSG:326xx`, `EPSG:327xx`, `EPSG:269xx` | UTM zones, meters |
| `EPSG:2240`, `EPSG:2239` | Georgia West and East, US survey feet |
| `EPSG:26967`, `EPSG:26966` | Georgia West and East, meters |
| A PROJ text | `+proj=tmerc`, `utm`, `lcc`, `merc` or `longlat`, with `+lat_0`, `+lon_0`, `+k`, `+x_0`, `+y_0`, `+lat_1`, `+lat_2`, `+units=m\|ft\|us-ft` |

Coordinates are computed on the GRS80 ellipsoid, without datum shift: NAD83
and WGS84 are taken as equal, which is right within about a meter. A plan is
flat, so a lot is drawn at its true size around the georeferenced point; this
is for placing a house on its lot, not for surveying.

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
**bottom**. At most 150 parts. A part and its coordinates may reach 200 m,
enough for a fence or a string along a lot; a part beyond that is refused
with its number and its size.

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
`"on_terrain":true` measures the `z` of each part, and of each point of the
`path` of an `extrude`, from the ground under it: stakes and a string line
that follow the slopes of the ground built by `set_terrain`.

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
| `store_point_of_view` | Keeps the camera under a `name` in **3D view > Go to point of view**. With `"camera":{"x","y","height","look_at_x","look_at_y","look_at_height"}` or a `view`, it stores that point of view without moving the camera of the user |

The place of the virtual visitor is also where the VR walk-through and the
game mode start. Camera moves aren't undoable.

## Checks, reports and exports

| Command | What it does |
| --- | --- |
| `check_home` | Reports plan defects with ids: walls that don't meet, duplicates, rooms without door, doors out of walls, doors and windows above the underside of the roof, walls beyond a setback or a property line, furniture under the floor or out of rooms, heavy models |
| `fix_home` | Repairs the defects marked fixable; `"types":["wall_gap"]` limits it |
| `home_report` | Floor areas, wall lengths and surfaces, furniture counts and prices; roofs, slabs and the ground are listed apart |
| `set_unit_prices` | `paint`, `flooring`, `ceiling`, `baseboard`, `currency`: the report then ends with a budget |
| `export_elevation` | Writes a SVG elevation drawing: `side`, `path`, `section`. Roofs, slabs, pieces made of parts and the ground are drawn with their real shape, catalog furniture as boxes |

`check_home` doesn't report roofs, slabs, the ground, pieces put on the ground
with `on_terrain`, pieces created with `"structure":true`, or pieces given a
negative elevation on purpose.

Each issue is an `error`, a `warning` or an `info`, the most serious first.
Options, for the command and for the `check_home` tool:

| Option | Effect |
| --- | --- |
| `"min_severity":"warning"` | Leaves out the less serious issues (`info`, `warning` or `error`) |
| `"ignore":["F516","W3"]` | Leaves out the issues about these items |
| `"ignore_tags":["marker"]` | Leaves out the issues about the furniture with these tags |
| `"remember":true` | Keeps `ignore` and `ignore_tags` in the home for the next checks |
| `"forget":true` | Stops ignoring what was remembered |

The answer says how many issues were left out.

## Undo groups

```json
{"action":"begin_group"}
{"action":"end_group"}
```

One call of `apply_commands` is one step of undo. `begin_group` makes one
step of every call that follows, until `end_group`: a house built in ten
calls is then undone at once. The `undo` tool ends an open group and undoes
it, and a group left open ends by itself after 10 minutes without a command.
