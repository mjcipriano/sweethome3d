# Walking Through a Home in VR (Meta Quest 2, SteamVR)

The **WebXR Preview** plugin exports the current home and serves a WebXR page
so you can look around the house in a VR headset - no app install on the
headset, it uses the headset's browser.

## Nothing to install

The preview is part of the application: **Tools > WebXR Preview...** is there
as soon as a home is open. Earlier versions shipped it as a separate
`WebXRPreview.sh3p` file to copy in the plugins folder; such a file is now
ignored, whatever its age, so an old copy can't take the place of the current
preview (an old copy exported the home without reducing it - a 564 MB scene
for the reference home, which Chrome can't read). You may delete it from
`%APPDATA%\eTeks\Sweet Home 3D\plugins` (Windows),
`~/Library/Application Support/eTeks/Sweet Home 3D/plugins` (macOS) or
`~/.eteks/sweethome3d/plugins` (Linux).

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
   The headset address keeps the same port from one preview to the next
   (47443; the desktop one is 47480), so it can be bookmarked in the headset.
   Another free port is used when it is taken, for instance by the preview
   of a second home; change them with
   `-Dcom.eteks.sweethome3d.webxr.httpsPort` and `...webxr.httpPort`.

   **If the headset says the connection timed out**, the firewall of the
   computer is blocking it. On Windows: a network marked *Public* blocks
   incoming connections, and a *Block* rule for Sweet Home 3D (created when the
   firewall question was dismissed once) wins over any *Allow* rule. In an
   administrator PowerShell:

   ```powershell
   Get-NetFirewallRule -DisplayName "sweet home 3d.exe" | Where-Object Action -eq Block | Remove-NetFirewallRule
   New-NetFirewallRule -DisplayName "Sweet Home 3D WebXR" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 47443
   ```

   Use the `https://` address: the desktop one only answers on the computer
   itself.
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

The export has one mesh per part of each object - 24,565 for the reference
home. Once loaded, the viewer merges the parts that share a material
(`webxr-merge.js`), by squares of 6 m of the plan: the same home becomes 1,565
meshes, of which only those in sight are drawn - 308 meshes and 400,000
triangles from the start point of the reference home, instead of 24,565
meshes and 1.19 million triangles - and the page uses about half the memory
(desktop Chrome, loaded in 7 seconds). See-through parts stay apart so they
are still drawn back to front.

In VR the viewer counts the pictures drawn per second. When the headset
can't keep up (under 60 % of its frame rate), the view is shortened step by
step (60, 35, 22, 14, 9 m) and a message says so.

### When something goes wrong in the headset

The page reports the steps of the visit to Sweet Home 3D, which writes them in
`preview-log.txt` in the exported folder (`sh3d-webxr-...` in the temporary
folder of the computer, also at `https://<address>:<port>/preview-log.txt`):
the browser and the size of the home once loaded, the start and the end of a
VR session, the pictures per second and the triangles drawn, errors of the
page, and a lost graphics context (home too heavy for the graphics memory). An
error in the handling of the controllers no longer prevents the picture from
being drawn. **Enter VR** reports each step (pressed, session granted, session
attached); if VR hasn't started after 20 seconds, the page says at which step
it stopped.

With a headset linked to the computer (Quest Link, Air Link, SteamVR), VR is
entered from the desktop page: the application of the headset must run and be
the active OpenXR runtime. The page creates its graphics on the graphics card
of the headset (`xrCompatible`, high performance GPU), so the home needn't be
moved to another card when VR starts. The log is plain text, limited to 512 KB, and only the viewer's
`POST /log` can add to it.

While the home loads, the page shows how much of it was received; **Enter VR**
appears only when the home is ready, and a failure is explained on the page
instead of leaving an empty world.

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
- `webxr-merge.js` - dependency-free merge of the mesh parts sharing a
  material (`test/webxr/merge.test.mjs`, run by the same target).
- `WebXRPreviewServer.appendToLog` - the visit log (`testViewerReportsAreLogged`).
- `WebXRPreviewServer` - localhost HTTP server for the desktop plus an
  all-interfaces HTTPS server for headsets (WebXR secure-context requirement),
  with the self-signed keystore bundled as a resource. Covered by
  `WebXRPreviewServerTest` (serving, content types, HTTPS handshake, path
  traversal rejection, LAN URL shape); `WebXRSceneDescriptionTest` covers the
  scene description and the viewer files.

The Ant `build` target compiles `pluginsrc` into `SweetHome3D.jar`, and
`PluginManager` registers the plug-ins of `BUILT_IN_PLUGIN_FAMILIES` before
those of the plugins folders (the first plug-in of a given name wins). The
release workflow still attaches `SweetHome3D-<version>-WebXRPreview.sh3p` to
every GitHub release; installing it changes nothing.

Only the latest preview of a home stays served: starting a new preview, or
closing the home, stops the previous servers and deletes their export folder
(a freshly created private temporary directory). Files are streamed rather
than buffered, so a large export doesn't consume the application's heap.
