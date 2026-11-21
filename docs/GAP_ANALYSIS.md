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
| Stairs generator | yes | `add_stairs`: straight, L, U and spiral |
| Plan checker (walls that don't meet, doors out of walls...) | yes | `check_home`, `fix_home` |
| Terrain, garden, landscaping tools | **new**, by commands | `set_terrain`; `add_fence`, `add_hedge`, `add_path`, `add_planting_bed`, `add_trees`; Plan > Garden builds them along a selected line |
| 2D elevation and section drawings | yes | File > Export > Export elevation drawing... (outlines in SVG); `get_elevation_image` draws an elevation from the 3D models with its dimensions |
| Construction documents (framing, schedules, electrical symbols) | **new**, by commands | door, window and room schedules, electrical and plumbing symbols, a framing estimate for a budget. Not structural drawings |
| Align, distribute, mirror, group, measure | **new** for the assistant | `align`, `distribute`, `mirror`, `group`, `measure` |

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
| AI that furnishes a room by itself | **new**, simple | `furnish_room`: fixed rules per kind of room, clear of doors, windows and passages. Tidy, not designed |
| One material of a model recolored | yes | the furniture window; `set_material` for the assistant |
| Doors and windows shown open | **new** | `open_door`, for models with hinges |

## Seeing the result

| Feature | Here | Notes |
| --- | --- | --- |
| Live 3D view, virtual visit | yes | |
| Fast zoom, zoom buttons, standard views | yes | 7.20 |
| Photo-realistic images and videos | yes | built-in renderers, and Blender export |
| VR walk-through in a headset | yes | Tools > WebXR Preview... |
| Game-like visit (run, jump, fly, open doors) | yes | Tools > Game mode... (`docs/GAME_MODE.md`) |
| A link to share a 3D visit | **new** | File > Export > Export 3D web page... writes a folder for any static web host |
| 360 degree panoramas | **new** | File > Export > Export 360° panorama... |
| Automatic hiding of the walls in front of the camera (doll house) | **new** | 3D view > Hide walls in front of the camera |
| Sun position by date and time in the live view | **new** | 3D view > Sun at the date and time of the camera; `set_sun` |

## Quantities and cost

| Feature | Here | Notes |
| --- | --- | --- |
| Areas, wall lengths and surfaces, furniture list and prices | **new in the UI** | Plan > Home report... (the assistant and MCP already had `home_report`) |
| Furniture list export (CSV) | yes | |
| Paint, flooring and baseboard budget from unit prices | yes | in the Home report window; `set_unit_prices`, also for one room or one wall; openings are deducted from wall surfaces |

## Working with others, and with AI

| Feature | Here | Notes |
| --- | --- | --- |
| AI design assistant in the program | yes | several providers |
| Control from an external AI agent | yes | MCP server for Claude: versions, copies, exports, undo history, changes made by hand |
| Cloud storage, real-time collaboration, comments | no | files are local; the web page export shares a read-only visit |
| Versions of a design to compare and restore | yes | File > Versions...; `compare_versions` with a picture, `restore_version` in place |
| Mobile and tablet apps | no | |

## What to do next, by value for effort

The six items first listed here (unit prices, versions, panoramas, spiral and
walkable stairs, elevation drawings, doll house) were done on 2026-11-16.
What remains:

Items 1, 2, 4, 5 and 6 of that list were done on 2026-11-21 (elevation
pictures, restoring versions, prices per room, the sun in the 3D view,
garden commands). What remains:

1. **Catalog staircases walked exactly** (their steps read from the model).
2. **Drawing tools in the plan** for fences, hedges and symbols, placed with
   the mouse instead of from a selected line or wall.
3. **Sliding doors and windows shown open**, and doors opened in the
   walk-through from their hinges.
4. **A layout engine that designs**: the size of the room and of the pieces
   chosen from it, lamps, rugs and what hangs on walls.
5. **Elevations as vector drawings** from the models, and sections.
6. **Lines of the plan in the game mode and the web pages.**
