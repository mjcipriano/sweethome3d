# Gap Analysis Against Comparable Programs

Written 2026-11-16 from what is generally known of these programs, not from a
fresh test of each one: Floorplanner, Planner 5D, RoomSketcher, HomeByMe,
Live Home 3D, SketchUp, Chief Architect / Home Designer and Cedreo. It lists
what they offer that matters to someone designing a home, whether this
program has it, and what was added to close the gap.

Legend: **yes** = available, **new** = added with this analysis, **partly**,
**no**.

## Drawing the plan

| Feature | Here | Notes |
| --- | --- | --- |
| Walls, rooms, doors, windows, levels, curved walls | yes | |
| Room shapes to drop in (rectangle, L, T, U) | **new** | Plan > Add room from shape...; `add_room_shape` |
| Automatic room dimensions | yes | Plan > Add room dimensions (7.20) |
| Trace over a scanned plan | yes | background image; `set_background_image` |
| Mirror / flip a plan | yes | select all, then flip horizontally / vertically |
| Automatic roof | yes | `auto_roof` (assistant) |
| Stairs generator | yes | `add_stairs`: straight, L, U (no spiral) |
| Plan checker (walls that don't meet, doors out of walls...) | yes | `check_home`, `fix_home` |
| Terrain, garden, landscaping tools | no | only furniture and a flat ground |
| 2D elevation and section drawings | no | the standard 3D views (front, sides) are the closest |
| Construction documents (framing, schedules, electrical symbols) | no | out of scope |

## Furnishing and decorating

| Feature | Here | Notes |
| --- | --- | --- |
| Furniture catalog, import of 3D models | yes | plus `import_furniture` |
| Replace an item by another in place | **new** | Furniture > Replace by the piece selected in catalog; `replace_furniture` |
| Decoration styles applied to a room in one step | **new** | Plan > Apply style to rooms... (10 styles); `apply_style` |
| Custom furniture without a 3D modeler | yes | `create_furniture` from parts (7.20) |
| Personal furniture libraries to share | yes | `.sh3f` export / import (7.20) |
| Layers and tags to show or hide groups of items | yes | Furniture > Layers and tags... (7.20) |
| Furniture from a photo | partly | a picture panel or rug; the assistant picks the closest catalog piece |
| Brand catalogs of real products with prices and links | no | needs commercial data |
| AI that furnishes a room by itself | partly | the assistant places catalog furniture on request, without a layout engine |

## Seeing the result

| Feature | Here | Notes |
| --- | --- | --- |
| Live 3D view, virtual visit | yes | |
| Fast zoom, zoom buttons, standard views | yes | 7.20 |
| Photo-realistic images and videos | yes | built-in renderers, and Blender export |
| VR walk-through in a headset | yes | Tools > WebXR Preview... |
| A link to share a 3D visit | **new** | Tools > Export 3D web page... writes a folder for any static web host |
| 360 degree panoramas | no | candidate: render six views or an equirectangular image |
| Automatic hiding of the walls in front of the camera (doll house) | partly | walls transparency is global |
| Sun position by date and time in the live view | partly | compass and time are used by the photo renderer |

## Quantities and cost

| Feature | Here | Notes |
| --- | --- | --- |
| Areas, wall lengths and surfaces, furniture list and prices | **new in the UI** | Plan > Home report... (the assistant and MCP already had `home_report`) |
| Furniture list export (CSV) | yes | |
| Paint, flooring and material estimates with unit prices | partly | surfaces are reported; no unit prices yet |

## Working with others, and with AI

| Feature | Here | Notes |
| --- | --- | --- |
| AI design assistant in the program | yes | several providers |
| Control from an external AI agent | yes | MCP server for Claude |
| Cloud storage, real-time collaboration, comments | no | files are local; the web page export shares a read-only visit |
| Versions of a design to compare | no | candidate: named snapshots of a home |
| Mobile and tablet apps | no | |

## What to do next, by value for effort

1. **Unit prices in the report** (paint per m2, flooring per m2, baseboard per
   m) to turn the quantities into a budget.
2. **Named versions of a home** (snapshot, compare, restore).
3. **360 degree panorama export**, viewable in the same web page and in a
   headset without loading the whole model.
4. **Spiral stairs** and walkable catalog staircases.
5. **Elevation drawings** of a wall with its furniture, as a 2D export.
6. **Doll house view**: hide the walls between the camera and the rooms.
