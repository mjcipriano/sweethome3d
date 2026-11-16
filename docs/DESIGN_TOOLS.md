# Design Tools: Replace, Room Shapes, Styles, Report, Web Page

Tools added after comparing with similar programs (`docs/GAP_ANALYSIS.md`).
The first three are also commands of the design assistant and of the MCP
server, and each is one step of **Edit > Undo**.

## Replace furniture in place

Select the pieces to replace in the plan or the furniture list, select the
one piece that replaces them in the catalog, then **Furniture > Replace by
the piece selected in catalog**. The new piece takes the place, the angle,
the elevation, the level, the visibility and the tags of the old one.

`{"action":"replace_furniture","ids":["F3"],"name":"<exact catalog name>"}`
(`"keep_size":true` keeps the size of the old piece).

## Room shapes

**Plan > Add room from shape...** adds a rectangle, L, T or U shaped room with
its walls from a few sizes, to the right of the plan and selected, ready to
be dragged in place.

`{"action":"add_room_shape","shape":"L","name":"Living","x":0,"y":0,"width":600,"depth":500,"cut_width":250,"cut_depth":200}`

`width` and `depth` are the bounding box inside the walls. L cuts the top
right corner out; T cuts both bottom corners, leaving a leg of
`width - 2 x cut_width`; U cuts a notch in the middle of the top side. Walls
that already stand along a side aren't added twice.

## Decoration styles

**Plan > Apply style to rooms...** colors the floor, the ceiling and the wall
sides facing the selected rooms (or every room of the level): Scandinavian,
Modern, Industrial, Traditional, Coastal, Mid-century, Minimalist, Dark,
Earthy, Mediterranean. The floor gets the texture of the catalog that fits
best when there is one. The other side of a wall shared with a room that
isn't styled is left alone.

`{"action":"apply_style","style":"Coastal","ids":["R1","R2"]}`
(`"textures":false` for plain colors; `list_styles` lists them).

## Home report

**Plan > Home report (areas, quantities, cost)...** shows the floor areas and
perimeters by room and level, the wall lengths and surfaces, the furniture
counts and prices, with **Copy** and **Save as text file...**.

### Budget

At the bottom of the report window, give unit prices - wall paint or covering
per m2 of wall side, flooring per m2, ceiling paint per m2, baseboard per m -
and a currency, then **Update budget**: the report ends with the cost of each
and a total that includes the furniture that has a price. The prices are
saved with the home. Wall surfaces count both sides of every wall and don't
deduct doors and windows, so the paint line is an upper bound.

`{"action":"set_unit_prices","paint":8,"flooring":35,"baseboard":6,"currency":"EUR"}`
(a negative value removes a price).

## Versions of a home

**File > Versions...** keeps named copies of the home in a folder beside its
file (`My home.sh3d.versions`). **Save a version...** stores the home as it is
now, without changing the file you work on. **Compare with now** tells what
changed since a version (levels, rooms, floor area, walls, furniture added and
removed). **Open in a new window** shows a version; to come back to it, save
it there under the name of the home. Versions are full copies: delete the
ones you no longer need.

## Elevation drawings

**Plan > Export elevation drawing...** writes a SVG drawing (1 unit = 1 cm) of
the home seen from its front, back, right or left side, with its overall width
and height and the floors. A **section** distance cuts the home that far
behind its front and leaves out what stands before the cut, which shows the
inside. It is a schematic drawing: each wall, door, window and piece of
furniture is the rectangle it fills seen from that side, not the projection
of its 3D model - a home made of large imported models comes out as boxes.

`{"action":"export_elevation","side":"south","path":"/home/me/front.svg","section":0}`

## 3D web page to share

**Tools > Export 3D web page...** writes a folder (`<home>-3d-web`) with the
same walk-through as the WebXR preview. Put it on any static web host
(GitHub Pages, Netlify, your own server) and share the address of its
`index.html`; with https, a headset gets the **Enter VR** button. A
`README.txt` in the folder explains it, including how to try it locally
(`python -m http.server`). Opening `index.html` from the disk doesn't work.
Anyone with the address sees the whole home.

## Implementation

- `viewcontroller/AssistantDesignCommands` - `replace_furniture`,
  `add_room_shape` (`getShapePoints`), `apply_style` (`getStyles`).
- `swing/HomeDesignToolsView` - the dialogs, which run those commands.
- `WebXRPreviewPlugin.exportWebPage` - the web page export.
- `HomeReport` unit prices (`setUnitPrice`, stored as home properties),
  `HomeVersions`, `ElevationDrawing.createSVG`.
- `AssistantDesignCommandsTest`.
