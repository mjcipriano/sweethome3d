<!-- Fragment to merge in docs/MCP_COMMANDS.md: two sections, "Garden" and "Furnishing a room by itself". -->

## Garden

Fences, hedges, paths and planting beds are built as one piece of furniture each, where they are
drawn: `points` are points `[x,y]` of the plan in centimeters. They **follow the ground** built by
`set_terrain` when there is one (and stand at the height 0, or on the floor of a terrace, when there
is none), go to the selected level unless `level` names another one, and are flagged as a part of the
site: `check_home` doesn't report them as furniture out of a room, and they have the tag `garden`.
A line whose last point repeats the first one is closed, as with `"closed":true`.

| Command | What it does |
| --- | --- |
| `add_fence` | A fence along `points`: a post at each corner and every `post_spacing` cm (240) at most, the spans between two posts following the slope of the ground. `height` (120), `"style":"picket\|panel\|rail\|wire"` (picket), `color`, `"closed":true`, `name`. `"gate":{"at":N,"width":N}` leaves an opening `width` cm wide (100) centered `at` cm from the first point along the fence, between two taller posts |
| `add_hedge` | A hedge along `points`, mitred at its corners, with chamfered top edges: `height` (150), `width` (60), `color` (a dark green), `closed`, `name` |
| `add_path` | A paved strip along the center line `points`, mitred at its corners and laid on the ground: `width` (100), `thickness` (4), `color` or `texture` (with `texture_scale`, `texture_angle`), `"edging":true` for a curb on both sides (`edging_color`), `closed`, `name`. `"stepping_stones":{"length":40,"gap":20}` lays separate slabs of `length` x `width` cm instead |
| `add_planting_bed` | An area of soil over the polygon `points`: `thickness` (6), `color` (dark soil) or `texture`, `"border":{"height":10,"width":5,"color":"#RRGGBB"}` for an edging, `name`. `"plants":{"name":"Plant","spacing":80}` (or `"names":[...]`, or just a list of names) scatters catalog pieces inside it |
| `add_trees` / `scatter` | Catalog pieces put on the ground: `"names":[...]` (or `name`), in the polygon `"area":[[x,y],...]` or along the line `"along":[[x,y],...]`, every `spacing` cm (300) or `count` of them. `"min_distance_from_buildings"` (150), `"jitter":N` cm, `"rotate":false` |

```json
{"commands":[
  {"action":"add_fence","points":[[-800,-600],[1800,-600],[1800,1400],[-800,1400]],"closed":true,
   "style":"picket","height":110,"gate":{"at":1300,"width":120}},
  {"action":"add_hedge","points":[[-750,-550],[-750,1350]],"height":180,"width":70},
  {"action":"add_path","points":[[500,-600],[500,-200],[300,0]],"width":120,"texture":"Paving stone","edging":true},
  {"action":"add_planting_bed","points":[[900,900],[1700,900],[1700,1300],[900,1300]],
   "border":{"height":12},"plants":{"names":["Plant","Flowers"],"spacing":70}},
  {"action":"add_trees","area":[[-800,-600],[1800,-600],[1800,1400],[-800,1400]],"names":["Plant"],"count":8}
]}
```

The answer of each command gives the id of what it built, its length or its area, and its count of
posts, of stones or of plants with their ids.

- **Posts of a fence.** Each side of the line is cut in equal spans no longer than `post_spacing`, so
  a side of 5 m with the default spacing has 3 spans of 167 cm. A corner has one post, shared by its
  two sides. A gate is an opening only: no leaf is drawn in it.
- **On a slope.** Posts are upright and stand a little into the ground; rails, panels and wires go
  straight from a post to the next one, parallel to the ground between them. Hedges, paths and
  planting beds are cut every 50 cm (more on very long lines) and each part takes the height of the
  ground under it, across their width too. They aren't built again when `set_terrain` changes the
  ground: delete them and run the command again.
- **Plants and trees.** They are ordinary catalog pieces, each put at the height of the ground at
  its place and marked as standing outdoors. Their places come from a grid moved at random, but the
  same points always give the same places. `add_trees` and `scatter` skip the places in a room or
  closer than `min_distance_from_buildings` to a room or a wall of any level, and place 400 pieces at
  most at a time. The default catalog has no tree, only `Plant` and `Flowers`: add trees with
  `import_furniture` or a furniture library, and find their names with `search_catalog`.

## Furnishing a room by itself

| Command | What it does |
| --- | --- |
| `furnish_room` | Furnishes a room with pieces of the catalog placed by rules. `room` = its id or its name; `"type":"bedroom\|living_room\|dining_room\|kitchen\|office\|bathroom\|kids_room\|entry"`, guessed from the name of the room if missing; `"replace":true` deletes first the furniture that is in the room (not its doors and windows); `"max_items":N`; `"style":"<word>"` prefers the catalog pieces whose name has that word |
| `suggest_layout` | The same computation, reported without changing the home |

```json
{"commands":[{"action":"suggest_layout","room":"Bedroom"}]}
{"commands":[{"action":"furnish_room","room":"R3","type":"living_room","replace":true}]}
```

The answer lists each piece with its id, its name, its place, its angle and the wall it stands
against; then what was skipped and why (no catalog piece of that name, no free wall long enough, no
place that leaves a passage); then the free floor left. The whole is one step of Edit > Undo.

What is placed, in this order:

| Type | Pieces |
| --- | --- |
| `bedroom` | A bed in the middle of the longest free wall, rather one without window or door; a bedside table on each side; a wardrobe on another wall; a dresser or a chest |
| `kids_room` | A single bed along a corner with a bedside table; a desk under a window and its chair; a wardrobe; a bookcase |
| `living_room` | A sofa on the longest free wall; a TV unit on the opposite wall, in front of the sofa; a coffee table 40 cm in front of the sofa; an armchair at a right angle, turned to the table; a bookcase; a plant in a corner |
| `dining_room` | A table in the middle of the free floor, along the longest wall, with chairs on its long sides and at its ends, turned to it (8 at most); a sideboard; a plant |
| `kitchen` | A run of units from a corner along the walls: fridge, cabinet, cooker, cabinet, sink, dishwasher, cabinet; a table with 4 chairs if the room is larger than 12 m2 |
| `office` | A desk under a window, or on the longest free wall, and its chair; a bookcase; an armchair; a plant |
| `bathroom` | A bath along a corner (or a shower); a washbasin; a toilet in a corner; a clothes washer if the room is larger than 7 m2 |
| `entry` | A wardrobe; a chest; a stool; a plant |

The rules every piece follows:

- It stands **inside the room**, between the faces of its walls, 1 cm from the wall it has its back
  to. At the angle 0 the front of a piece faces the bottom of the plan; a piece against a wall faces
  away from it.
- It **overlaps nothing**: no other piece, placed before or already in the room.
- **Doors** keep free the zone their leaf needs: their width along the wall, and as deep (60 cm at
  least, 100 cm at most). A door is a door or a French window that starts at the floor.
- **Windows** keep free of pieces taller than 100 cm the 35 cm in front of them.
- A **passage of 60 cm** stays from each door to every piece: to its front, or to a side of a bed, a
  sofa or a table; a chair is reached from behind or from a side. A place that would close it is
  refused, and the next best place is tried.
- The same room always gets the same layout.

Limits to know:

- The layouts are tidy and usable, not designed: the pieces are the ones of the catalog at their
  catalog size, chosen by name (`bed`, `wardrobe`, `sofa`, `table`, `chair`, `desk`...) and not by
  style or by the size of the room. A large room gets the same few pieces as a small one, and a
  small room may get no bed at all. The names are looked for in English.
- Walls are found from the sides of the room: a room drawn without walls is furnished along its
  sides. Round walls and rooms with very oblique corners are handled roughly.
- Nothing is hung on walls or put on other pieces: no lamp, no TV on the TV unit, no upper cabinet,
  no curtain, no rug.
- The swing of a door is a rectangle in front of it on the side of the room, whatever the side it
  really opens to.
