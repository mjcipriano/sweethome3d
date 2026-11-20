# Tags, Layers and AI-made Furniture

Two additions of this fork that work together: tags to organise the furniture
of a home in layers you show and hide at once, and commands that let the
design assistant or Claude (through the MCP server) create furniture and
furniture libraries.

## Tags and layers

A piece of furniture, a door or a window can carry any number of **tags**
("kitchen", "to buy", "phase 2", "electrical"...). A **layer** is the set of
the pieces that have a tag, or that are of a type:

| Layer by type | Pieces |
| --- | --- |
| Doors and windows | doors and windows |
| Lights | light sources |
| Stairs | staircases of the catalog and stairs made by `add_stairs` |
| Groups | groups of furniture |
| Other furniture | everything else |

### In the application

- **Right click in the furniture list or in the plan > Tags**: a box per tag of
  the home, ticked when every selected piece has it (and "2 of 5" when only
  some have it). Choosing a tag gives it to the whole selection, choosing a
  ticked one removes it. **New tag...** creates tags (several separated by
  commas), **Remove all tags** clears the selection. The same menu is in the
  **Furniture** menu.
- **Tags column** of the furniture list: appears as soon as a tag is used.
  Click its header to sort the list by tags; show or hide it with **Furniture >
  Display column > Tags**. The furniture list export (CSV) includes it, and
  **Other properties...** in **Modify furniture** edits it as text.
- **Furniture > Layers and tags...** opens a window that stays beside the home:
  one row per tag, one for the pieces without tag, one per type in use. Untick
  a row to hide its furniture in the plan and the 3D view, tick it to show it
  again. **Select items** selects the pieces of the chosen rows, **Show
  everything** makes all furniture visible, and the other buttons create,
  fill, rename and delete tags.

![The window of layers and tags](images/layers-and-tags.png)

Roofs and the ground built by the house modeling commands are tagged `roof`
and `terrain`: untick these rows to look into the house or to hide the garden.

Every change is one step of **Edit > Undo**. Tags are saved in the home file
(as the property `tags` of each piece, a text separated by commas), and so is
whether a piece is hidden.

Hiding a layer sets the *Visible* box of its pieces: a piece that belongs to
two layers follows the layer changed last. Walls, rooms, dimensions and labels
have no such box in Sweet Home 3D and can't be tagged; whole floors are hidden
with **Plan > Make level unviewable**.

### From the assistant or Claude

```json
{"action":"add_tags","ids":["F1","F2"],"tags":["kitchen","to buy"]}
{"action":"remove_tags","target":"selection","tags":["to buy"]}
{"action":"set_tags","type":"lights","tags":["electrical"]}
{"action":"hide_layer","tag":"to buy"}
{"action":"show_layer","type":"doors_and_windows"}
{"action":"select_layer","tag":"untagged"}
```

`type` is `furniture`, `doors_and_windows`, `lights`, `stairs` or `groups`;
`with_tag` restricts a tagging command to the pieces that already have a tag.
The description of the home (`get_home`) lists the layers with their counts
and the tags of each piece, so requests like "hide everything I still have to
buy" or "tag all the lights of the first floor" work.

## Furniture made by the AI

Three ways to get a piece that is in no catalog, from the simplest:

1. **From parts** - `create_furniture` builds the model from boxes, cylinders,
   cones, spheres, prisms, wedges and bars along a path
   ([Command reference](MCP_COMMANDS.md#furniture-made-from-parts)). No file
   is needed, so it works from Claude Desktop too:

   ```json
   {"action":"create_furniture","name":"Oak table","x":200,"y":300,
    "catalog":"My furniture","tags":["custom"],
    "parts":[
     {"shape":"box","x":0,"y":0,"z":71,"width":120,"depth":70,"height":4,"color":"#B08050"},
     {"shape":"cylinder","x":-52,"y":-28,"z":0,"diameter":6,"height":71,"top_scale":0.6,"color":"#604020"},
     {"shape":"cylinder","x":52,"y":-28,"z":0,"diameter":6,"height":71,"top_scale":0.6,"color":"#604020"},
     {"shape":"cylinder","x":-52,"y":28,"z":0,"diameter":6,"height":71,"top_scale":0.6,"color":"#604020"},
     {"shape":"cylinder","x":52,"y":28,"z":0,"diameter":6,"height":71,"top_scale":0.6,"color":"#604020"}]}
   ```

   Parts are in centimeters in the frame of the piece: `x` to its right, `y`
   toward its front, `z` up; `x`,`y` are the centre of a part and `z` its
   bottom. Per part: `rotation` (degrees around the vertical), `axis` `x`/`y`
   to lay a cylinder or a cone down, `diameter`, `top_scale` (0 to 1) to taper
   it, `color` or `texture`. At most 150 parts. The piece gets an icon drawn
   from its parts, and keeps its parts: `modify_furniture_parts` changes some
   of them later.
2. **From a model file** - `import_furniture` reads an OBJ, DAE, 3DS, KMZ or ZIP
   file. Claude Code can write an OBJ file itself (or run a script that
   generates one) and import it, which allows any shape.
3. **From a picture** - `add_picture` makes a painting, a poster or a rug from
   an image; Claude can also look at a photo and rebuild the object from parts.

A workflow that works well through the MCP connector: ask for the piece
("make a mid-century sideboard, 160 cm wide, walnut with brass legs"), let
Claude call `create_furniture`, look at the result with `get_3d_image`, and
correct it (`undo`, then a new `create_furniture`) until it is right.

### Keeping furniture and making libraries

- `"catalog":"<category>"` in `create_furniture`, or
  `{"action":"add_to_catalog","ids":["F3"],"category":"My furniture"}` for
  pieces already in the home (imported or created), saves them in your
  furniture catalog: they appear in the catalog tree and can be added to any
  home by name. `"place":false` builds a piece for the catalog only.
- `{"action":"export_furniture_library","path":"/home/me/sideboards.sh3f","name":"Sideboards","tag":"sideboards"}`
  writes a **furniture library file** from pieces of the home (`ids`,
  `"target":"selection"` or a `tag`), or from a category of the catalog
  (`"category":"My furniture"`). The file is the standard `.sh3f` format: it
  can be shared, opened by a double click or with **Furniture > Import
  furniture library...**, and edited with the Furniture Library Editor.
  `"install":true` installs it at once, `"replace":true` overwrites the file.
- `{"action":"import_furniture_library","path":"/home/me/sideboards.sh3f"}`
  installs a library in the catalog.

So "build me a library of ten garden pieces" is: ten `create_furniture`
commands tagged `garden`, a look at each, then one
`export_furniture_library` with `"tag":"garden"`.

Limits: doors and windows can't be saved in the catalog or in a library this
way yet (they would lose what makes them cut walls), and groups must be
ungrouped first. These commands read and write only the files whose path
is given, and need **Allow changes to this home** on the MCP server.

## Implementation

- `viewcontroller/HomeItemTags` - tags (property `tags`), types, layers, and
  the undoable changes of tags and visibility; `HomeController.changeFurnitureTags`,
  `renameFurnitureTag` and `setFurnitureVisible` expose them to views.
  `SweetHome3D.getFurnitureAdditionalProperties` always declares the property,
  which gives the column of the furniture list.
- `swing/FurnitureLayersView` - the Tags menu and the layers window.
- `viewcontroller/AssistantLayerCommands` - the tag and layer commands.
- `viewcontroller/AssistantFurnitureBuilder` - parts to OBJ model and icon
  (faces turned outward, drawn without AWT).
- `viewcontroller/AssistantLibraryCommands` - `create_furniture`,
  `add_to_catalog`, `export_furniture_library` (`writeLibrary`),
  `import_furniture_library`.
- `AssistantLayersAndLibraryTest` covers all of it, including reading the
  written library back with `DefaultFurnitureCatalog` and saving a home.
