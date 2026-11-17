# OpenGL renderer of the 3D view

The 3D view of a home can be drawn by an OpenGL renderer instead of Java 3D.
Java 3D stays the default; the renderer is an option.

## Choosing the renderer

- **3D view > Use OpenGL renderer (experimental)** switches the 3D view of the
  home at once and is remembered for the next homes and sessions.
- `-Dcom.eteks.sweethome3d.j3d.renderer=opengl` (or `java3d`) forces a renderer
  and has the priority on the menu choice.
- **Help > 3D rendering information** names the graphics card followed by
  `(OpenGL renderer)` when this renderer is the one in use.

If the JOGL classes can't be loaded or the OpenGL canvas can't be created, Java 3D is used and
a message is printed on the error output.

## What it does

Java 3D remains the description of the scene. `HomeComponent3D` builds the same
tree of nodes it builds for Java 3D (walls, rooms, furniture, ground, sky,
lights) and updates it with the same listeners, but the tree is not attached to
a Java 3D universe. `OpenGLSceneRenderer` reads it:

- Each geometry is copied once to a vertex buffer kept by the graphics card and
  shared by every shape that uses it, so the 30 copies of a chair use the
  memory of one. Java 3D sent the vertices of every shape again at each frame.
- An object of the home (a wall, a piece of furniture) is described again only
  when it changes. Models and textures loaded in the background mark their
  object, or all objects, to be described again.
- Shapes are sorted by appearance to change the OpenGL state rarely, shapes out
  of the view frustum are skipped, and transparent shapes are drawn last from
  the farthest to the closest.
- Geometries are copied to the graphics card over several frames (1,500,000
  vertices per frame, `-Dcom.eteks.sweethome3d.opengl.vertexUploadBudget`), so
  the home appears progressively while it loads.

`OpenGLView3D` holds the JOGL canvas. It is the only class of the Swing package
that depends on JOGL. JOGL is already bundled, as Java 3D 1.6 runs on it.

## Measured

Reference home (435 pieces, about 20,000 shapes, 25.8 million vertices), camera
sweep of `Home3DFpsBenchmark` (20 s after a 45 s warm-up, three runs each),
native Windows, GeForce GTX 1660 Ti, Java 8, 4 GB heap:

| Renderer | Average FPS | Minimum FPS |
| --- | --- | --- |
| Java 3D | 30, 30, 29 | 3, 3, 3 |
| OpenGL | 58, 58, 58 | 49, 51, 50 |

The sweep moves the camera about 60 times per second, so 58 FPS is the ceiling
of this benchmark and not of the renderer. Under WSLg, where OpenGL goes
through the D3D12 translation layer, the two renderers measure about the same
(12 and 10 FPS).

```bash
make build
# then, as described in AGENTS.md for the native benchmark, add
#   -Dcom.eteks.sweethome3d.j3d.renderer=opengl -Dcom.eteks.sweethome3d.opengl.vsync=false
```

## What differs from Java 3D

- Frames are drawn in the event dispatch thread, when something changes. A
  frame that takes long delays the user interface by as much; Java 3D draws in
  its own thread.
- The camera jumps to its new location: there is no animated transition.
- The lights of the home (light sources of lamps, shown when the 3D view
  displays lights) are not drawn; the four default lights and the ambient
  light are.
- Transparent shapes are sorted by the center of each shape.
- The frame rate overlay isn't drawn.
- Photos, videos, printing and the export of the 3D view still use Java 3D,
  whatever the renderer of the view.
- Colors are slightly darker than Java 3D on some materials.

## Not tested

- macOS and Linux outside WSLg; screens with a scale factor other than 100%.
- Editing in the 3D view by hand (moving furniture with the mouse). Picking an
  object under a point is tested by `OpenGLSceneRendererTest` and was checked in
  the running application.

## Tests

- `OpenGLMeshTest` (no OpenGL context needed): quads, strips, fans, indexed
  geometries and lines turned into lists of triangles and segments.
- `OpenGLSceneRendererTest`: a lit square drawn off screen, its pixels and its
  picking. It needs an OpenGL context, so it is in `GL_TEST_EXCLUDES` and runs
  with `make test-local` or
  `make test TEST_SOURCES=test/com/eteks/sweethome3d/j3d/OpenGLSceneRendererTest.java`.
