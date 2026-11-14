# Walking Through a Home in VR (Meta Quest 2, SteamVR)

The **WebXR Preview** plugin exports the current home and serves a WebXR page
so you can look around the house in a VR headset - no app install on the
headset, it uses the headset's browser.

## Install the plugin

- Download `SweetHome3D-<version>-WebXRPreview.sh3p` from the GitHub release
  (or build it locally with `make webxr-plugin`, which produces
  `plugins/WebXRPreview.sh3p`).
- Double-click the `.sh3p` file, or copy it into the Sweet Home 3D plugins
  folder (`~/.eteks/sweethome3d/plugins` on Linux, `%APPDATA%\eTeks\Sweet Home 3D\plugins`
  on Windows, `~/Library/Application Support/eTeks/Sweet Home 3D/plugins` on
  macOS), then restart Sweet Home 3D.
- For development, `make run-webxr-preview [VR_HOME_FILE=<file.sh3d>]` builds
  the plugin and launches the app with it loaded.

## Use it

1. Open your home and choose **Tools > WebXR Preview...**
2. The home is exported to OBJ in a temporary folder and two servers start:
   - `http://127.0.0.1:<port>/index.html` - desktop preview (opens
     automatically in your browser; W A S D to walk, drag the mouse to look).
   - `https://<your LAN IP>:<port>/index.html` - for headsets. A dialog lists
     the exact URLs to type.
3. **Quest 2 / Quest 3:** put on the headset (same Wi-Fi network as the
   computer), open the **Browser** app and enter the `https://...` URL from the
   dialog. The browser shows a certificate warning once - the plugin uses a
   bundled self-signed certificate because browsers only allow WebXR ("Enter
   VR") on secure (https) pages; choose **Advanced > Proceed**. Then press
   **Enter VR**.
4. **SteamVR / PC headsets (Quest 2 over Link/Air Link too):** open the
   *desktop* URL in Chrome or Edge while SteamVR (or the Oculus runtime) is
   running. Desktop Chrome/Edge expose WebXR through the active OpenXR
   runtime, so **Enter VR** renders into the headset.

### What the walk-through is like

- **Life size.** The home is shown at its real dimensions and the floor of your
  real room is the floor of the level you visit, so your eyes are at your own
  height and doors, counters and ceilings have their true size.
- **You start where the virtual visitor stands** (the camera of the *Virtual
  visit* mode of the 3D view), looking the way it looks, on the level selected
  in the application. Move the visitor in the plan before exporting to choose
  the starting point.
- **Walls stop you, doorways let you through.** The viewer knows the plan: walls
  block walking and teleporting, and each door leaves an opening in its wall.
  Windows don't. Furniture doesn't block you, and door leaves are drawn as they
  are in the 3D view (you walk through a closed one).
- **Every level is exported**, whatever level is selected. Levels without rooms
  (roofs) are skipped when changing level.

### Controls in VR (Quest 2 / Quest 3 Touch controllers)

| Control | Action |
| --- | --- |
| Left stick | Walk, relative to where you look (1.4 m/s) |
| Left grip (hold) | Run |
| Right stick left / right | Turn by 30 degrees |
| Right stick forward, or right trigger | Aim a teleport arc; release to jump there (green = allowed, red = a wall is in the way) |
| A / B | Go to the level above / below |
| Walk or teleport onto stairs | Climb them; you arrive on the level they lead to |
| X | Switch wall blocking off / on |
| Y | Back to the start |

You can also simply walk in your room: the viewer keeps you out of walls. A
panel reminds the controls for a few seconds when you enter VR.

Desktop: W A S D or arrows to walk (Shift to run), drag the mouse to look,
Q / E to turn, Page Up / Page Down to change level, C for wall blocking, R to
go back to the start.

### Heavy homes

A standalone headset can draw about a million triangles and has little memory,
while a furnished home easily has ten times more. The export therefore gives
itself a budget of 900,000 furniture triangles: when the home exceeds it, every
heavy model is reduced by the same ratio for the headset copy only (the home
itself isn't modified). The 435-piece reference home went from 12.9 million
triangles and a 1 GB export to 1.18 million triangles and 123 MB, in about 30
seconds. Reduction needs the model LOD native library shipped with the
application; without it the full models are exported. Change the budget with
`-Dcom.eteks.sweethome3d.webxr.triangleBudget=<triangles>`.

## Notes and limitations

- **Not yet verified on a physical headset.** The page was exercised in desktop
  Chrome (loading, start position, walking into a wall, turning, changing
  level, climbing stairs) and the walk-through logic has automated tests, but the controller
  mapping, teleport arc and comfort were not tried on a Quest. Please report
  what you see.
- The headset needs internet access the first time: the viewer page loads
  three.js from a CDN. The scene itself streams from your computer.
- The scene is still an OBJ text file, which is slow to load for big homes
  (load time on a headset hasn't been measured). Textures are exported at full
  resolution.
- Only the stairs made by the assistant's `add_stairs` command can be walked:
  they describe their flights and landings. You climb by walking or by
  teleporting onto them, their sides and the hole in the floor above block
  you like walls, and wall blocking off (X) also switches stairs off.
  Staircases from the furniture catalog are scenery: use A / B to change
  level.
- The bundled TLS certificate is self-signed and **deliberately not a
  secret** - it carries no trust and only exists so the headset browser grants
  the page a secure context. The server only serves the exported scene files
  from a temporary folder and refuses any path outside it.
- The export is a static snapshot: re-run **Tools > WebXR Preview...** after
  editing the home.

## Implementation

`pluginsrc/com/eteks/sweethome3d/plugin/webxr/`:

- `WebXRPreviewPlugin` - menu action, OBJ export of every viewable level with
  the headset triangle budget (`reduceModelsForHeadset`, using
  `ModelLODGenerator.generate(model, ratio, targetError)`), viewer files,
  browser launch and the instructions dialog.
- `WebXRSceneDescription` - writes `scene.json`: levels, walls, doors and
  windows, and the visitor's start, in meters (`x`, `z` = plan `x`, `y`).
- `webxr-viewer.html` / `webxr-viewer.js` - the three.js page: rig and head
  placement, desktop and controller input, teleport, level changes.
- `webxr-walk.js` - dependency-free walk logic: wall boxes with door gaps,
  sliding collision, teleport validation, level order. Tested with Node by
  `make test-webxr-walk` (`test/webxr/walk.test.mjs`).
- `WebXRPreviewServer` - localhost HTTP server for the desktop plus an
  all-interfaces HTTPS server for headsets (WebXR secure-context requirement),
  with the self-signed keystore bundled as a resource. Covered by
  `WebXRPreviewServerTest` (serving, content types, HTTPS handshake, path
  traversal rejection, LAN URL shape); `WebXRSceneDescriptionTest` covers the
  scene description and the viewer files.

The release workflow attaches the built plugin to every GitHub release as
`SweetHome3D-<version>-WebXRPreview.sh3p`.

Only the latest preview of a home stays served: starting a new preview, or
closing the home, stops the previous servers and deletes their export folder
(a freshly created private temporary directory). Files are streamed rather
than buffered, so a large export doesn't consume the application's heap.
