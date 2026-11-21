<!-- Fragment to merge into docs/MCP_COMMANDS.md (and docs/DESIGN_TOOLS.md for the report). -->

## Schedules, symbols and framing

### Schedules

A schedule is a table of the home, answered as text: a line of titles, then a
line per row, values separated by ` | `. Schedules change nothing unless
`tag_marks` or `labels` is given.

| Command | What it does |
| --- | --- |
| `door_schedule` | The doors grouped by name and size: mark, type, name, width, height, sill height, count, levels, ids |
| `window_schedule` | The same table for the windows |
| `room_schedule` | The rooms: id, name, level, area, perimeter, ceiling height, floor finish, net wall surface |
| `symbol_schedule` | The electrical and plumbing symbols counted by type, then by room and level, then by circuit |
| `schedule` | Any of them: `"type":"doors"`, `windows`, `doors_and_windows`, `furniture`, `symbols` or `rooms` |
| `export_schedule` | Writes the table of a schedule in a CSV file: `type`, `path`, `replace` |

Every schedule takes `"level":"<id or name>"` to keep one level.

```json
{"action":"door_schedule"}
{"action":"schedule","type":"doors_and_windows","level":"Ground floor","tag_marks":true,"labels":true}
{"action":"export_schedule","type":"rooms","path":"/home/me/house/rooms.csv","replace":true}
```

```
Door schedule (cm):
Mark | Type | Name | Width | Height | Sill height | Count | Levels | Ids
D1 | door | Door | 80 | 205 | 0 | 1 | Ground floor | F12
D2 | door | Door | 90 | 205 | 0 | 2 | Ground floor | F3 F7
Total: 3 doors of 2 types
```

- **Marks.** Doors get `D1`, `D2`..., windows `W1`, `W2`... The doors that
  have the same name, width and height share a mark. Marks are given in the
  order of the table: by name, then width, then height. They are computed
  again at each call, so adding a new kind of door may shift the marks that
  follow it.
- **Door or window.** A piece whose name contains "window" is a window, one
  whose name contains "door" or "gate" is a door; otherwise a piece that
  starts under 30 cm and is at least 150 cm high is a door.
- **Sill height** is the elevation of the piece above the floor of its level.
  Pieces that share a mark at different heights list them: `90 / 110`.
- `"tag_marks":true` writes the mark in the property `scheduleMark` of each
  door and window. `"labels":true` also writes it in the plan, in a label 25 cm
  in front of each piece; asking again replaces the labels written before.
  Both are undone by Edit > Undo.
- **Room schedule.** The ceiling height is the height under the ceiling of the
  level, or the lowest and the highest headroom (`120 to 310`) under a roof
  built by `add_roof`; it is empty for a room without ceiling. The floor
  finish is the name of the floor texture, else its color `#RRGGBB`, else
  `default`. The net wall surface is explained under
  [the report](#what-changed-in-home_report).
- **Furniture schedule.** Grouped by name and size, with the count, the
  levels, the sum of the prices and the ids. Doors, windows, symbols, roofs,
  slabs and the other parts of the building aren't listed.
- **CSV.** `export_schedule` writes the titles and the rows of the table, in
  UTF-8, values separated by commas, lines ended by CR LF. A value that
  contains a comma, a semicolon, a quote or a new line is written between
  quotes, its quotes doubled. `path` must be absolute, end with `.csv` and be
  in a folder that exists; an existing file is kept unless `"replace":true`.
  The remarks under a table (totals, counts by room and circuit) aren't
  written in the file.

### Electrical and plumbing symbols

```json
{"action":"add_symbol","type":"outlet","wall":"W3","offset":120,"circuit":"Kitchen 1"}
{"action":"add_symbol","type":"switch","wall":"W3","offset":20,"side":"right","height":105,"label":"S1"}
{"action":"add_symbol","type":"ceiling_light","x":250,"y":200}
{"action":"add_symbols","room":"R3","type":"outlet","spacing":360}
{"action":"symbol_schedule"}
{"action":"list_symbols"}
```

A symbol is a small piece of furniture. The plan draws it as on an electrical
or plumbing drawing (electrical in red, plumbing in blue); the 3D view shows
a small plate, box or fixture at its real height.

| Type | Is | Fixed | Default height (cm) |
| --- | --- | --- | --- |
| `outlet`, `double_outlet` | Electrical | on a wall | 30 |
| `switch`, `three_way_switch` | Electrical | on a wall | 110 |
| `wall_light` | Electrical | on a wall | 180 |
| `thermostat` | Electrical | on a wall | 150 |
| `data`, `tv` | Electrical | on a wall | 30 |
| `panel` | Electrical | on a wall | 150 |
| `ceiling_light`, `fan`, `smoke_detector` | Electrical | under the ceiling | the ceiling |
| `sink` | Plumbing (supplies and drain) | on a wall | 50 |
| `hose_bib` | Plumbing | on a wall | 45 |
| `toilet_rough`, `shower_drain`, `floor_drain` | Plumbing | on the floor | 0 |
| `water_heater` | Plumbing | on the floor | 0 |

| Field of `add_symbol` | Meaning |
| --- | --- |
| `type` | One of the types above |
| `wall`, `offset` | The wall and the distance in cm from its start point: the symbol stands against the face of the wall, turned to it. Without `offset`, the point of the wall the closest to `x`,`y`, or its middle if they aren't given |
| `side` | `left` or `right` of the wall, seen from its start to its end. Without it: the side of `x`,`y` if given, else the side where a room is, else the left one |
| `x`, `y`, `angle` | The place of the symbol when no `wall` is given; `level` chooses its level |
| `height` | For a wall symbol the height of its **center** above the floor; for a ceiling symbol the height of its top (by default the height of the wall, of the level or of the home); for a floor symbol the height of its bottom |
| `label` | A text that names the piece and is written beside it in the plan |
| `circuit` | The name of its circuit, counted by `symbol_schedule` |

- A symbol has the tag `electrical` or `plumbing`
  ([Tags and layers](#tags-and-layers)): `{"action":"hide_layer","tag":"electrical"}`
  and Furniture > Layers and tags show or hide them all. Its type is in its
  property `symbolType` and its circuit in `symbolCircuit`.
- `check_home` ignores symbols, `home_report` counts them on a line of their
  own, and the furniture schedule leaves them out.
- A symbol is moved, turned, renamed and deleted like any piece of furniture,
  with its `F#` id. It doesn't follow its wall when the wall moves.
- In the plan a wall symbol is drawn 16 cm wide and 12 cm deep (more for a
  panel or a sink) so that it can be read; its plate in 3D is smaller.
- `add_symbols` puts symbols along the straight walls that follow the sides
  of a room: on each wall side `ceil(length / spacing)` of them, evenly
  spaced (the default 360 cm leaves no point of a wall more than 180 cm from
  an outlet). A symbol that would stand in a door, or in a window that goes
  down to its height, moves 20 cm beside the opening, or is left out if
  there is no room. Wall sides shorter than 60 cm get none. The answer gives
  the count and the ids. It takes `height` and `circuit` like `add_symbol`.

### Framing estimate

```json
{"action":"framing_estimate"}
{"action":"framing_estimate","ids":["W1","W2"],"stud_spacing":60.96,"stud":"2x6","plates":3,"header":"2x8","waste":0.1}
```

Estimates the lumber of walls framed in wood. **It is an estimate for a
budget, not a structural design**: sizes, spacing, headers and bracing are
decided by a building code or an engineer.

| Field | Default | Meaning |
| --- | --- | --- |
| `ids`, `where` | every wall | The walls to estimate; `level` keeps one level. Round walls are left out |
| `stud_spacing` | 40.64 (16 in) | The distance between studs in cm; 60.96 is 24 in |
| `stud` | `2x4` | The lumber of the studs and plates, like `2x6` |
| `plates` | 3 | The plates along each wall: one at the bottom, two at the top |
| `header` | `2x8` | The lumber of the headers, which are doubled |
| `waste` | 0.1 | The share added for waste |

How it counts:

- **Studs**: for each wall `ceil(length / stud_spacing) + 1`, plus 2 for each
  door or window in it (king and jack), plus 2 for each corner where two
  walls are joined. A stud is as long as its wall is high.
- **Plates**: `plates` x the length of the walls.
- **Headers**: one for each opening, as long as the opening is wide plus
  2 x 7.6 cm.
- **Sheathing and drywall**: the surface of the two sides of the walls,
  openings deducted, also given for one side and in sheets of 4 ft x 8 ft.
- Totals are given in pieces, meters, feet and board feet (1 in x 12 in x
  1 ft), then again with the waste, then wall by wall.

Not counted: blocking, cripple studs, sills under windows, bracing and
hardware. The lengths of the walls are the ones of their middle lines, so the
thickness of a wall is counted twice at a corner.

### Unit prices of a room or a wall

```json
{"action":"set_unit_prices","paint":8,"flooring":35,"ceiling":6,"baseboard":7,"currency":"EUR"}
{"action":"set_unit_prices","room":"R3","flooring":60,"paint":12}
{"action":"set_unit_prices","ids":["W3"],"paint":25,"side":"left"}
{"action":"set_unit_prices","room":"R3","clear":true}
```

`set_unit_prices` without a room or a wall still sets the prices of the whole
home. With `room` (an id or a name), `ids`, `where` or `"target":"selection"`
it gives rooms and walls a price of their own:

| Item | Prices | Applies to |
| --- | --- | --- |
| A room | `flooring`, `ceiling` (per m2 of its area), `baseboard` (per m of its perimeter), `paint` (per m2) | Its floor, its ceiling, its perimeter, and the wall sides that face it |
| A wall | `paint` (per m2) | Its two sides, or one of them with `"side":"left"` or `"right"` |

- The price of a wall side wins over the price of the room it faces, which
  wins over the price of the home.
- A negative price removes that price from the room or the wall.
  `"clear":true` removes all the prices of the items given; with no item, of
  every room and every wall. The prices of the home are kept.
- These changes are undone by Edit > Undo. They are stored in the properties
  `budgetUnitPrice.<key>` of the room or the wall and saved with the home.

### What changed in `home_report`

The report (also Plan > Home report and budget...) now deducts doors and windows:

```
- Level "Ground floor": floor 20.0 m2, 4 walls, 18.4 m long, 92.0 m2 of wall surface on both sides, 3.7 m2 of openings, 88.3 m2 net
    - Living: 20.0 m2, perimeter 18.0 m, walls 43.2 m2 net (1.8 m2 of openings deducted) [own prices: flooring 50.0]
- Total: floor 20.0 m2, walls 18.4 m long, 92.0 m2 of wall surface on both sides, 3.7 m2 of openings of doors and windows deducted, 88.3 m2 net
- Walls with their own price: W1: paint right 30.0
- Electrical and plumbing symbols: 9 (8 x outlet, 1 x switch); symbol_schedule lists them
- Budget from the unit prices (walls on both sides, openings of doors and windows deducted):
    - Wall paint or covering, per m2 of wall side: 44.9 m2 x 10.0 = 449.00 EUR
        - W1, right side, own price: 10.9 m2 x 30.0 = 327.00 EUR
        - Living (R1), own price: 32.5 m2 x 12.0 = 390.00 EUR
    - Flooring, per m2 of floor: no price for the whole home
        - Living (R1), own price: 20.0 m2 x 50.0 = 1000.00 EUR
```

- **Openings.** A door or a window is deducted from the wall of its level
  that contains its center. Its opening is its width by its height, clipped
  to the length and the height of the wall, and it is deducted on both sides
  of the wall. A door that stands in no wall deducts nothing.
- **Gross, openings, net.** Each level and the total give the three
  surfaces. The paint of the budget is now computed on the **net** surface,
  so a budget is lower than before for the same prices.
- **Rooms.** Each room gives the net surface of the wall sides that face it:
  the straight walls of its level that run along its sides, within their
  thickness. It is left out when no wall follows the room. A wall between two
  rooms counts one side for each.
- **Own prices.** Rooms and walls that have their own price are marked, and
  the budget gives a line for each under the line of the home, which then
  covers only what is left.
- **Symbols** are counted on their own line, not with the furniture.

Limits: round walls deduct their openings but aren't attributed to rooms; a
sloping wall is measured with its mean height; the baseboard is the whole
perimeter of the rooms, door widths included.
