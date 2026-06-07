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

The reference home contains 435 model references backed by 148 distinct model
contents. A controlled three-run comparison reduced median synchronous scene
construction from 15.09 seconds to 13.44 seconds (about 11%) by replacing the
synchronized `BufferedReader` in the thread-confined OBJ parser with a larger
unsynchronized buffer.

When a scene is built synchronously (off-screen image creation, photo rendering,
export), each piece previously loaded its model on the build thread, so the 148
distinct models of the reference home were parsed one after another. Preloading
the distinct models in parallel with the existing CPU-sized loader pool before
the scene-tree build, so the build only clones cached models, reduced median
off-screen scene construction from about 13.1 s to about 6.4 s (around 51%) on a
12-core machine. The interactive on-screen view already loads models
asynchronously and is unaffected.

`BENCHMARK_MODE=update` measures how long the live scene graph takes to react to
repeated model changes after the scene is built - moving a piece, rotating a
piece, and moving the camera - without using the unstable off-screen frame path.
Because `HomeComponent3D` applies furniture updates through
`EventQueue.invokeLater`, each measurement mutates the home on the event
dispatch thread and then posts an empty barrier so the elapsed time covers the
whole deferred update cycle. On the reference home the medians were about 3 ms
for a piece move, 3 ms for a piece rotation, and 1 ms for a camera move. These
are the baselines for the model-loading and scene-update work in workstream D.

## Optimization Rules

1. Capture a baseline before changing a hot path.
2. Keep damaged-home detection and repair behavior covered by tests.
3. Run `make test-core` for headless I/O and model changes.
4. Run `make test-local` for Swing or Java 3D changes.
5. Record hardware, Java version, workload, and before/after measurements.
6. Treat GPU acceleration as a rendering concern; it will not accelerate XML,
   ZIP, or model parsing.
