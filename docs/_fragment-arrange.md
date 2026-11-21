## Arranging and measuring

| Command | What it does |
| --- | --- |
| `group` | Makes one piece of furniture of several, moved and rotated together |
| `ungroup` | Frees the pieces of a group |
| `align` | Puts an edge of furniture on the same line, or pushes furniture against a wall |
| `distribute` | Equal gaps between furniture along `x` or `y` |
| `mirror` | Flips furniture, walls, rooms and lines across a vertical or a horizontal line |
| `lock` / `unlock` | Furniture that stays in place, or moves again |
| `lock_base_plan` / `unlock_base_plan` | Locks the walls, rooms, doors and windows, as Plan > Lock base plan |
| `measure` | Distances, free space around a piece, what is at a point, a bounding box. Changes nothing |

```json
{"action":"group","ids":["F1","F2","F3"],"name":"Dining set"}
{"action":"ungroup","id":"F9"}
{"action":"align","ids":["F1","F2","F3"],"edge":"left"}
{"action":"align","ids":["F2","F3"],"edge":"front","to":"F1"}
{"action":"align","ids":["F4"],"wall":"W3"}
{"action":"distribute","ids":["F1","F2","F3","F4"],"axis":"x"}
{"action":"distribute","ids":["F1","F2","F3"],"axis":"y","spacing":20}
{"action":"mirror","ids":["F1","W2","R1"],"axis":"x","about":400}
{"action":"lock","ids":["F1"]}
{"action":"measure","from":"F1","to":"W3"}
{"action":"measure","clearance":"F1"}
{"action":"measure","at":[250,120]}
```

Furniture is designated as in the other commands: `id`, `ids`, `names`,
`where` or `"target":"selection"`. Each change is undone with the rest of
the call.

- `group`: 2 pieces at least, and an optional `name`. The answer gives the id
  of the group, which `move`, `rotate` and `delete` take as any piece. The
  pieces of a group have no id of their own while they are in it; `ungroup`
  gives them back the ids they had.
- `align`: `edge` is `left`, `right`, `top`, `bottom`, `center_x` or
  `center_y`, read on the bounding box of each piece in the plan, so turned
  pieces work. Without `to`, the pieces go to the outermost edge of the set
  (the center of the set for `center_x` and `center_y`). With `"to":"F1"`
  (a piece, a wall, a room or a line), they go to the edge of that item,
  which stays in place.
  `front` and `back` are the sides of the piece `to` (the first of `ids`
  without `to`): the other pieces slide until their own outline reaches the
  line of that side.
  `"wall":"W3"` without `edge` pushes each piece straight against the face
  of the wall on the side where it stands. Pieces are moved, never turned.
  It doesn't work with a round wall.
- `distribute`: `axis` is `x` (left to right) or `y` (top to bottom). With 3
  pieces at least, the two outermost ones stay and the others are spread
  with equal gaps between the bounding boxes. With `"spacing":N`, the first
  piece stays and each next one comes N cm after the previous. The answer
  gives the gap; a negative gap means the pieces overlap.
- `mirror`: `"axis":"x"` swaps left and right across the vertical line
  `x = about`; `"axis":"y"` swaps top and bottom across the horizontal line
  `y = about`. Without `about`, the line goes through the center of the
  bounding box of the items, so they flip in place. Furniture gets its
  position, its angle and its model mirrored; a round wall keeps its curve,
  and the colors of its two sides follow. Walls joined to a mirrored wall
  keep their common end. Dimension lines and labels are mirrored when they
  are in the selection (`"target":"selection"`), as they have no id.
  A door or a window mirrored without its wall may end out of it.
- `lock`: a locked piece can't be moved in the plan with the mouse, and
  `align`, `distribute` and `mirror` leave it in place and say so. `move`,
  `rotate` and `resize` still change it. Walls and rooms aren't locked one by
  one: `lock_base_plan` locks them all together.
- `measure` reports, in cm:
  - `"from"` and `"to"` (ids of furniture, walls, rooms or lines), or
    `"from_point":[x,y]` and `"to_point":[x,y]`: the shortest distance
    between the two outlines with the two nearest points, 0 when they overlap
    or touch, then the distance between the centers with `dx` and `dy`.
  - `"clearance":"F1"`: the free distance at the front, the back, the left
    and the right of a piece up to the nearest wall or piece of its level,
    with the id of what stands there, up to 1000 cm. The front of a piece is
    its side at the bottom of the plan when its angle is 0, and its left the
    side at the left then. Pieces that don't stand at the height of the
    measured piece (a rug, a ceiling light), doors, windows, hidden pieces
    and the slabs, grounds and roofs made by commands aren't counted.
  - `"at":[x,y]`: the rooms, the walls, the furniture and the lines that
    contain the point or come within `tolerance` cm of it (5 by default), on
    the selected level or on `level`, and the elevation of the floor of that
    level.
  - `ids` alone: the bounding box of the items, its size and its center.
