# 3D View Zoom, Standard Views and Room Dimensions

## Zoom of the 3D view

The mouse wheel used to move the camera of the aerial view by 12.5 cm per
notch, whatever its distance: hundreds of notches to step back from a house.
A notch now changes the distance to the home by 12 % (and at least 12.5 cm),
so the zoom is fast from far away and still precise close to the home. In the
virtual visit a notch walks 25 cm (2.5 cm before). Shift makes it three times
faster, and touchpads scroll smoothly (`getPreciseWheelRotation`).

Other ways to zoom:

| Control | Action |
| --- | --- |
| **3D +** / **3D −** in the tool bar | Zoom in / out; keep the button pressed to continue |
| **3D fit** in the tool bar, or `0` in the 3D view | The whole home in sight (aerial view) |
| `+` / `-` in the 3D view (Shift: faster) | Zoom in / out |
| **3D view > Zoom in / Zoom out / Show whole home**, also in the right-click menu of the 3D view | Same |

The aerial camera can now move away to 12 times the size of the home (5 times
before), at least 100 m.

## Standard views

**3D view > Standard views** shows the home from the top, from each of its
four sides and from its four corners, at the distance where it is entirely in
sight - the quick views of other design programs. They use the aerial view;
store one with **3D view > Store point of view...** to come back to it.

## Room dimensions in one step

**Plan > Add room dimensions** adds the dimension lines that give the size of
each room of the level, as floor plans show them: inside the room, 25 cm from
its sides, without repeating a size (a rectangle gets one width and one
depth). With rooms selected, only those are dimensioned; lines that already
exist aren't added again, and the whole is one step of Undo. The assistant
and Claude have the same as `{"action":"auto_dimensions"}` (optionally
`"ids":["R1"]`).

## Implementation

- `HomeController3D.zoomCamera(steps)`, `zoomCameraToFit()` and
  `viewFromDirection(yaw, pitch)`; `TopCameraState.zoomCamera` holds the
  ratio. `HomeComponent3D` maps the wheel and the keys, `HomePane` the tool
  bar buttons (`createZoom3DButton`) and menus.
- `viewcontroller/RoomDimensions.createDimensionLines`,
  `HomeController.addRoomDimensions`.
- `HomeController3DZoomTest`.
