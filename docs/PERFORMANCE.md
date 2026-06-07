# Performance Work

Performance changes should be measured with repeatable workloads and verified
against functional tests. Do not commit private or large benchmark homes.

## Complex Home Loading

The local benchmark loads a home without starting Swing or Java 3D:

```sh
make benchmark-home-load \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_MODE=recorder \
  BENCHMARK_ITERATIONS=3
```

Capture a Java Flight Recorder profile with:

```sh
make benchmark-home-load \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  HOME_LOAD_JFR=profiles/home-load.jfr
```

The example home used during initial profiling is 141 MB and contains 435
furniture items, 162 walls, 47 rooms, and 8 levels.

## Initial Baseline

Measurements were taken on the same WSL development machine with a 4 GB
benchmark heap. Warm iterations are the useful comparison because the first
iteration includes JVM and class-loading costs.

| Implementation | Warm load time | Retained heap delta |
| --- | ---: | ---: |
| Original content lookup | 2.46-2.82 s | 199-203 MB |
| Digest-keyed content lookup | 2.56-2.99 s | 112-114 MB |
| Digest verification without eager ZIP inflation | 1.50-1.86 s | 113-114 MB |

The digest-keyed lookup reduces retained heap by about 44 percent. It does not
provide a statistically meaningful load-time improvement on its own. Avoiding
the redundant eager inflation pass for modern local files reduces warm load
time by roughly 35-45 percent. Legacy archives, URL inputs, and archives
without a readable digest manifest continue to use exhaustive ZIP validation.

## 2D Plan Rendering

Repeated off-screen plan painting can be measured without Java 3D:

```sh
make benchmark-plan-render \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_ITERATIONS=10
```

Set `PLAN_RENDER_JFR=profiles/plan-render.jfr` to record the render hot paths.

The benchmark performs a five-second warm-up before recording samples. On the
reference complex home, warmed 1920x1080 paints had 9-12 ms medians and 11-15
ms p95 results. JFR identified PNG decoding and image scaling as the main
warm-up costs, with geometry area construction and Java2D rasterization as
secondary costs.

## 2D Interaction

Full-frame paint throughput does not capture interaction latency, where only a
small part of the plan usually changes. This benchmark measures common
interactions and, for each, reports the apply cost, the size of the invalidated
repaint rectangle the plan requests, and the cost to paint only that rectangle:

```sh
make benchmark-plan-interaction \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_ITERATIONS=20
```

Set `PLAN_INTERACTION_JFR=profiles/plan-interaction.jfr` to record the hot
paths. Interactions are synthesized through the model the same way the view is
driven: `setSelectedItems` for selection, `Selectable.move` for a drag result,
and `PlanComponent.setScale` for zoom. The benchmark works on the level that
carries the most furniture and runs off-screen.

The baseline measurement showed that every interaction - selecting a single
piece, selecting all, moving one piece, and zooming - invalidated the **entire**
1920x1080 viewport (`dirty_pct_median=100`), because `PlanComponent` issued bare
full-component `repaint()` at all of its call sites with no targeted
`repaint(rectangle)` calls. The `apply_*` figures (tens of microseconds) confirm
the model mutation itself is cheap - the cost is the paint that follows.

Task C1 made the **selection** change repaint only the area covering the
previously and newly selected items plus a fixed pixel margin for their
indicators. On the reference home, selecting a single piece dropped from
invalidating 100% of the viewport to about 1% (roughly 13k vs 2.07M pixels), and
its paint cost dropped from ~9-10 ms to sub-millisecond. Selecting all items
still invalidates most of the plan (~91%), which is correct because the change
really does cover it. A pixel-coverage check confirmed the targeted region
fully contains every pixel that differs between the unselected and selected
renders, so there are no leftover indicator artifacts. Moving a piece and
zooming still trigger a full repaint and remain follow-up work (a further part
of C1 for moves, and C2 for per-paint cost).

## Windows And Switchable-Graphics Laptops

Two things make the interactive 3D view slow on Windows laptops with switchable
graphics (Intel iGPU + NVIDIA), and both are addressed here.

1. **Antialiasing cost.** The bundled Java 3D pipeline requests scene and
   implicit antialiasing, which is the largest per-frame cost on integrated
   GPUs. `GraphicsEnvironmentConfiguration`, called from `SweetHome3DBootstrap`
   before any AWT/Java 3D class loads, now defaults the renderer to speed on
   Windows, so `Component3DManager` builds a no-antialiasing configuration.
   - Restore antialiasing: `-Dcom.eteks.sweethome3d.j3d.renderingQuality=quality`.
   - Force speed anywhere: `-Dcom.eteks.sweethome3d.j3d.renderingQuality=speed`.
   - Disable all auto-tuning: `-Dcom.eteks.sweethome3d.graphics.autoTune=false`.
   - Log the decisions: `-Dcom.eteks.sweethome3d.graphics.autoTune=verbose`.
   The tuning is a no-op on macOS and Linux, where the current quality default is
   kept, and it never overrides a value the user set with `-D`.

2. **Wrong GPU.** On switchable-graphics laptops the OpenGL context often lands
   on the slow Intel iGPU instead of the NVIDIA card. Forcing the discrete GPU
   is the single biggest win for choppy navigation, but it is a driver/launcher
   setting that a Java application started through the `jpackage` launcher can't
   set programmatically, so it is a one-time manual step:
   - NVIDIA Control Panel -> Manage 3D settings -> Program Settings -> add the
     Sweet Home 3D launcher (or `javaw.exe`) -> set "High-performance NVIDIA
     processor"; or Windows Settings -> System -> Display -> Graphics -> add the
     app -> High performance.
   - Verify with Task Manager: the Performance tab shows activity on the NVIDIA
     GPU (not Intel) while rotating the 3D view.

These changes are correctness-safe (the scene builds with antialiasing off and
all tests pass), but the frame-rate gain itself must be measured on the Windows
NVIDIA hardware; it can't be measured on the WSL/Mesa development host, whose
off-screen frame path still crashes (tracked with the graphics-stack upgrade).

### In-app 3D diagnostics

To make all of the above observable without external tools, the application
reports its 3D rendering device and frame rate:

- **Help -> 3D rendering information** opens a dialog with the OpenGL vendor,
  renderer (the GPU name), and version, plus a live frames-per-second reading and
  a checkbox to toggle an on-canvas overlay. The renderer string is the
  definitive answer to "which GPU?": it reads, for example,
  `NVIDIA GeForce GTX 1660 Ti` on the discrete GPU versus an `Intel(R) ...` name
  on the integrated one.
- The same GPU/renderer line is written once to a `graphics.log` file in the
  user application folder (`~/.eteks/Sweet Home 3D` and the platform equivalents)
  and to standard output, because the packaged Windows application has no
  console.
- Launch with `-Dcom.eteks.sweethome3d.j3d.showStatistics=true` to show the
  overlay from startup.

Use the frames-per-second reading while rotating the 3D view to measure whether
a change (the speed default, forcing the NVIDIA GPU, or future tuning) actually
helps. On the WSL/Mesa host the renderer reads
`D3D12 (NVIDIA GeForce GTX 1660 Ti)`, which confirms the capture path works.

## Startup And First Usable Paint

The cold-start phases a user waits through before a home is usable can be
measured without starting the full Swing application or Java 3D:

```sh
make benchmark-startup \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_ITERATIONS=5
```

Set `STARTUP_JFR=profiles/startup.jfr` to record the cold-start hot paths
(`JavaMonitorWait`, file I/O, allocation, and execution samples).

The benchmark prints four phases per iteration: user-preferences
initialization (`prefs_ms`), home load (`load_ms`), plan-component creation
(`plan_create_ms`), and the first 2D paint (`first_paint_ms`), plus the
`total_ms`. Iteration 1 is the genuine cold pass because it pays one-time
class-loading and JIT warm-up; later iterations show the warm cost of the same
phases. To compare cold startup across runtimes, launch the target several
times and compare the `cold_total_ms` line, since class loading is paid once
per JVM.

On the reference complex home the cold pass was about 2.0 s total
(prefs ~0.21 s, load ~1.18 s, plan create ~0.19 s, first paint ~0.46 s); warm
iterations dropped to roughly 0.22-0.58 s total. The cold first paint is the
cost the warmed `benchmark-plan-render` deliberately excludes, so this harness
is the baseline for startup and EDT work. The first usable 3D frame is measured
separately by `benchmark-home-3d`.

## 3D Scene And Frame Rendering

Run the Java 3D benchmark on a working WSLg/X11 display:

```sh
make benchmark-home-3d \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_MODE=scene
```

Set `HOME_3D_JFR=profiles/home-3d.jfr` to record synchronous scene construction
and model loading. `BENCHMARK_MODE=frame` additionally renders repeated
1920x1080 off-screen frames, but legacy Java 3D / JOGL may crash in Mesa GLX
while creating the large off-screen context. Use `make test-local-check` first
to record the active OpenGL vendor, renderer, and direct-rendering status.
Run this benchmark from the pinned `sweethome3d` Conda environment; its
standard OpenJDK 17 build is required for stable Java 3D profiling under WSL.

For an end-to-end WSLg GPU smoke test, use:

```sh
make test-wsl-gpu \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d
```

This target verifies X11/GLX, rejects software renderers such as `llvmpipe`,
requires WSLg's `D3D12 (...)` renderer by default, runs the safe Java 3D
scene-update benchmark, and runs the on-screen FPS benchmark to prove that
Sweet Home 3D's own `Canvas3D` reports rendered frames on the WSLg GPU path.
Set `WSL_GPU_REQUIRE_D3D12=0` only for non-WSLg Linux diagnostics. This is the
adequate WSL GPU gate for Java 3D correctness and integration; it is not a
native Windows OpenGL performance proxy.

The reference home contains 435 model references backed by 148 distinct model
contents. A controlled three-run comparison reduced median synchronous scene
construction from 15.09 seconds to 13.44 seconds (about 11%) by replacing the
synchronized `BufferedReader` in the thread-confined OBJ parser with a larger
unsynchronized buffer.

When a scene is built synchronously (off-screen image creation, photo rendering,
export), each piece previously loaded its model on the build thread, so the 148
distinct models of the reference home were parsed one after another. Preloading
the distinct models in parallel with the existing CPU-sized loader pool before
the scene-tree build, so the build only clones cached models, cut off-screen
scene construction substantially: an interleaved 4-round A/B on a 12-core
machine measured a median of about 13.2 s with preloading off versus about
7.0 s with it on (around 47%). The interactive on-screen view already loads
models asynchronously and is unaffected. Set
`com.eteks.sweethome3d.j3d.preloadModels=false` to disable the parallel preload
(for example to A/B it or if a loader misbehaves).

Always rebuild with a current jar before measuring; `make` rebuilds the jars
when sources change, but a stale jar would silently benchmark old code.

`BENCHMARK_MODE=update` measures how long the live scene graph takes to react to
repeated model changes after the scene is built - moving a piece, rotating a
piece, and moving the camera - without using the unstable off-screen frame path.
Because `HomeComponent3D` applies furniture updates through
`EventQueue.invokeLater`, each measurement mutates the home on the event
dispatch thread and then posts an empty barrier so the elapsed time covers the
whole deferred update cycle. On the reference home the medians were about 3 ms
for a piece move, 3 ms for a piece rotation, and 1 ms for a camera move. These
are the baselines for the model-loading and scene-update work in workstream D.

### Interactive frame rate

The off-screen frame benchmark crashes on some Mesa stacks, so interactive frame
rate is measured through the real on-screen pipeline instead:

```sh
make benchmark-home-3d-fps \
  BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d \
  BENCHMARK_WARMUP_SECONDS=30 \
  BENCHMARK_SECONDS=15
```

It opens the 3D view, rotates the camera during warm-up and measurement, and
reports `fps_avg/min/max` from the rendering statistics. Use a warm-up for
complex homes so startup model insertion is not silently mixed with settled
viewport performance. A 30-second warm-up followed by a 15-second measurement
still averaged about 1 FPS on WSLg's NVIDIA-backed D3D12 renderer. JFR during
opening identified Java 3D render-bin updates while asynchronous furniture
branches are attached as the dominant Java-side CPU cost. Two existing,
reversible levers target rendering overhead:

- **Scene compilation.** The home branch is now `compile()`d before it is added
  to the universe (flatten transforms, merge shapes, build display lists), which
  is the standard Java 3D optimization for complex static geometry. Runtime edits
  still work because `compile()` preserves the capabilities already set on the
  nodes (verified with `BENCHMARK_MODE=update`). Disable with
  `-Dcom.eteks.sweethome3d.j3d.compileScene=false`.
- **Transparency sorting.** Sorting transparent geometry by depth runs on the CPU
  every frame; under `renderingQuality=speed` (the Windows default) the view uses
  `TRANSPARENCY_SORT_NONE` instead of `TRANSPARENCY_SORT_GEOMETRY`.

WSLg now selects the same speed profile automatically. Native Linux and macOS
keep their previous defaults. Force an A/B run with
`-Dcom.eteks.sweethome3d.j3d.renderingQuality=quality` or `speed`.

Note: the WSL/Mesa D3D12 translation layer is a noisy, unrepresentative proxy for
native NVIDIA OpenGL (display lists behave differently there), so the absolute
`fps_*` numbers from this host are unreliable. Measure these levers on the target
Windows+NVIDIA hardware with the in-app overlay (Help > 3D rendering information),
toggling the properties above.

### Measuring on native Windows NVIDIA from WSL

WSL can drive the real NVIDIA OpenGL driver by running a Windows JDK through
`cmd.exe`/`powershell.exe` interop, which is the only reliable way to measure 3D
frame rate here. `Home3DFpsBenchmark` reports a deterministic average from
`HomeComponent3D.getRenderedFrameCount()` over a fixed camera sweep, plus the
min/max rolling FPS that shows the view-dependent spread. Procedure:

1. `make build` and compile the benchmark into `build/performance-classes`.
2. Stage to a local Windows folder (avoids loading native DLLs over `\\wsl$`):
   the dev resource jars, `build/performance-classes`, the Java 3D 1.6 jars
   (`lib/java3d-1.6/*.jar` - not the 1.5.2 `lib/j3dcore.jar`/`vecmath.jar`), the
   non-3D `lib/*.jar`, the `lib/java3d-1.6/windows/amd64/*.dll` natives, and the
   home file.
3. Run with the Windows JDK and the natives on `-Djava.library.path`. Java 8
   needs no `--add-opens`. Set `JOPTS` to A/B properties such as
   `-Dcom.eteks.sweethome3d.j3d.compileScene=false`.

Findings on a GTX 1660 Ti (OpenGL 4.6, driver 596.36), 1280x800, 16 s sweep of
the reference home:

| Configuration | Average FPS |
| --- | ---: |
| `compile()` on, display lists on (was default) | 10-14 |
| `compile()` on, display lists off | 17-19 |
| `compile()` off, display lists on | ~0.25 (4 frames in 16 s) |
| `compile()` off, display lists off | 8 |

`compile()` is essential (its absence with display lists collapses to ~0.25
FPS). Two findings then redirected the geometry work:

- **By-reference / VBO geometry (task D4) does not apply here.** This Java 3D
  build has no VBO path (no VBO symbols in `j3dcore`/`jogl-all`), the loader
  geometry is by-copy with read-only capabilities (so it is display-list
  eligible), and forcing display lists off is *faster*, not slower. The
  bottleneck is therefore CPU-side per-frame scene-graph work (traversal and
  draw dispatch over thousands of shapes), not GPU geometry residency, so
  uploading geometry to buffers would not help.
- **Display lists are a net loss for this complex home.** Building them for the
  huge static models costs more than it saves. `j3d.displaylist=false` raised
  the average from ~11 to ~18 FPS and removed the ~0.25 FPS failure mode, so
  `GraphicsEnvironmentConfiguration` now sets it on the speed path
  (override with `-Dj3d.displaylist=true`).

The frame rate remains strongly view-dependent: facing the very large models (a
37 MB Collada model and several 5-7 MB tree meshes) it still drops to ~1 FPS in
every configuration. That residual is raw visible-triangle volume, which only
geometry decimation / level-of-detail on those oversized models would address;
it is a larger change and partly a content issue (that model is extreme).

## Optimization Rules

1. Capture a baseline before changing a hot path.
2. Keep damaged-home detection and repair behavior covered by tests.
3. Run `make test-core` for headless I/O and model changes.
4. Run `make test-local` for Swing or Java 3D changes.
5. Record hardware, Java version, workload, and before/after measurements.
6. Treat GPU acceleration as a rendering concern; it will not accelerate XML,
   ZIP, or model parsing.
