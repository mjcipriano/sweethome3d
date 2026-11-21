# 3D View Zoom, Standard Views and Room Dimensions

## Zoom of the 3D view

The mouse wheel used to move the camera of the aerial view by 12.5 cm per
notch, whatever its distance: hundreds of notches to step back from a house.
A notch now changes the distance to the home by 12 % (and at least 12.5 cm),
so the zoom is fast from far away and still precise close to the home. In the
virtual visit a notch walks 25 cm (2.5 cm before). Shift makes it three times
faster, and touchpads scroll smoothly (`getPreciseWheelRotation`).

![The tool bar with the 3D zoom buttons](images/toolbar.png)

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

Nine views show the house from the top, from each of its four sides and from
its four corners, at the distance where it fills the 3D view - the quick views
of other design programs. Three ways to get them:

- **Keys 1 to 9 in the 3D view** (click in it first). The keys are laid out
  as a numeric keypad placed over the plan, the front of the house being the
  bottom of the plan:

  | | | |
  | --- | --- | --- |
  | `7` back left corner | `8` back | `9` back right corner |
  | `4` left side | `5` top | `6` right side |
  | `1` front left corner | `2` front | `3` front right corner |

  The digits above the letters and those of the numeric keypad both work.
  **Shift** with a key looks from higher: a side from 30 degrees above the
  horizon instead of level, a corner from 50 degrees instead of 30.
- **3D views** in the tool bar opens a pad of nine buttons in the same layout;
  Shift + click looks from higher.
- **3D view > Standard views**, also in the right-click menu of the 3D view.

![The nine standard views, laid out as their keys](images/standard-views.jpg)

These views frame the building: its walls and what stands among them or
against them, roofs included. The ground, trees and a driveway don't move the
camera away. **3D fit** (key `0`) shows the whole home with its garden. With
*Center aerial view on selection* ticked in the preferences, the views frame
what is selected. They use the aerial view; store one with **3D view > Store
point of view...** to come back to it.

## Moving sideways

Turning goes around the center of the home. To look at something off
center, move the camera sideways (what games call strafing):

| Control | Action |
| --- | --- |
| `Q` / `E` in the 3D view | Move left / right (Shift: faster) |
| Alt + left / right arrow | Same |
| Alt + drag the mouse left or right | The home follows the mouse (Shift: faster); dragging up or down moves forward and back as before |
| Home / End | Move up / down |

This works in the aerial view and in the virtual visit. In the aerial view
the camera then turns around the point it was moved to, and stays there when
the home changes; a standard view or **3D fit** comes back to the center. `A`
and `D` and the arrows still turn.

## Doll house display

**3D view > Hide walls in front of the camera (doll house)** leaves out of the
aerial view the walls that stand between the camera and the rooms - those on
the side of the home the camera is on, and that face it - with their doors
and windows, and brings them back as the camera turns. Seen from the top, and
in the virtual visit, every wall is shown. Nothing changes in the home: it is
a setting of the 3D view, off again at the next start.

![The ground floor without and with the doll house display](images/dollhouse.jpg)

In this picture **3D view > Display selected level** also leaves out the first
floor and the roof.

## 360 degree panorama

**File > Export > Export 360° panorama...** computes a picture of everything around
the virtual visitor (place him in the plan first) with the photo renderer, and
writes a folder with `panorama.jpg` and an `index.html` to look around in it:
drag to turn, wheel to zoom. The page contains the picture, so it opens with
a double click on any computer, without a web server; on a https web site it
also offers **Enter VR** in a headset. As it is one picture, it loads at once on any device,
whatever the size of the home - a light alternative to the 3D web page for a
heavy home. Sizes: 2048 x 1024 (a few seconds for a small home), 4096 x 2048,
and 4096 x 2048 at the best quality of the renderer, which is much longer.

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
- `HomeController3D.viewFromStandardDirection(direction, higher)`;
  `TopCameraState.centerOnBuilding` leaves the garden out of the bounds the
  view turns around, `zoomCameraToBounds` places the camera from the shape of
  the view (`setViewAspectRatio`), `moveCameraSideways` and `elevateCamera`
  move the center of the aerial view. `HomePane.createStandardViewsButton`.
- `viewcontroller/RoomDimensions.createDimensionLines`,
  `HomeController.addRoomDimensions`.
- `viewcontroller/DollHouseView` chooses the walls; `Wall3D.setHiddenInView`
  and `HomePieceOfFurniture3D.setHiddenInView` leave them out;
  `HomeComponent3D.setDollHouseView`.
- `swing/PanoramaExporter` (spherical lens of `PhotoRenderer`).
- `HomeController3DZoomTest`.
