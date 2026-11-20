# Pictures of the guides

`make-screenshots.sh` regenerates the pictures of `docs/images` from the demo
house of `docs/examples/demo-house`, so they follow the application when it
changes. It also checks the game mode and the walk-through on that house:
the player starts in the hall, opens a door, follows the slope of the garden,
isn't pushed out by the roof, breaks a table, and notes find their item.

```bash
conda activate sweethome3d
make build
docs/tools/screenshots/make-screenshots.sh            # everything, about 5 minutes
docs/tools/screenshots/make-screenshots.sh renders    # only the pictures drawn by the MCP server
docs/tools/screenshots/make-screenshots.sh windows    # only the windows and dialogs
docs/tools/screenshots/make-screenshots.sh web        # only the game mode and the walk-through
```

It needs Linux with a display (WSLg works), `xwininfo` (x11-utils), ImageMagick
(`import`, `convert`), Python 3, and for the `web` part Node.js 22 or later
and Google Chrome. The window of the application shows on the display while
the script runs. Work files go to `build/doc-screenshots`, which Git ignores.

Review the pictures with `git diff --stat docs/images` and by looking at them
before committing: a capture may catch a window that wasn't drawn yet.

| File | Role |
| --- | --- |
| `make-screenshots.sh` | The sequence: builds the house, asks for pictures, opens each dialog, exports the web page |
| `DocShots.java` | Starts the application with a small command port (127.0.0.1:47999) that clicks menu items and buttons by their text |
| `ui.py` | Sends a command to that port: `item <menu item>`, `click <button>`, `set <n> <text>`, `combo <n> <index>`, `close`, `size <w> <h>`, `windows`, `tree` |
| `mcp.py` | Calls tools of the MCP server of the application and saves the pictures they return |
| `canned_model.py` | A local server that answers the design assistant with a fixed reply, for its picture |
| `browser.mjs` | Drives a page in headless Chrome |
| `game-shots.mjs` | The pictures and the checks of the game mode and of the walk-through |

Three pictures are staged, as the guides say: the assistant is answered by
`canned_model.py`, the command shown in the MCP dialog is replaced by the one
of an installed application, and the photo of the comparison is a ray-traced
picture of the same house.
