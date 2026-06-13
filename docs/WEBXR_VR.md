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
     automatically in your browser; WASD + mouse to move).
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

### Controls in VR

- **Left stick**: move (head-relative).
- **Right stick up/down**: scale the model (default 0.01 = cm to meters, i.e.
  life size).
- **Left grip or R**: recenter the model on the floor.
- Desktop fallback: WASD + mouse, `+`/`-` to scale.

## Notes and limitations

- The headset needs internet access the first time: the viewer page loads
  three.js from a CDN. The OBJ/MTL scene itself streams from your computer.
- The bundled TLS certificate is self-signed and **deliberately not a
  secret** - it carries no trust and only exists so the headset browser grants
  the page a secure context. The server only serves the exported scene files
  from a temporary folder and refuses any path outside it.
- Very large homes export large OBJ files; if loading on the headset is slow,
  generate the model LOD cache first (**3D view > Generate model LOD cache**)
  and save, or hide unneeded levels before exporting.
- The export is a static snapshot: re-run **Tools > WebXR Preview...** after
  editing the home.

## Implementation

`pluginsrc/com/eteks/sweethome3d/plugin/webxr/`:

- `WebXRPreviewPlugin` - menu action, OBJ export (same path as
  `HomePane`'s 3D export), HTML page, browser launch and the instructions
  dialog.
- `WebXRPreviewServer` - localhost HTTP server for the desktop plus an
  all-interfaces HTTPS server for headsets (WebXR secure-context requirement),
  with the self-signed keystore bundled as a resource. Covered by
  `WebXRPreviewServerTest` (serving, content types, HTTPS handshake, path
  traversal rejection, LAN URL shape).

The release workflow attaches the built plugin to every GitHub release as
`SweetHome3D-<version>-WebXRPreview.sh3p`.
