# Optimization Progress

This file is the durable handoff ledger for performance and modernization work.
Read it before starting an optimization branch and update it before every
optimization pull request is merged. Detailed benchmark commands and historical
measurements remain in `docs/PERFORMANCE.md`.

## Current Checkpoint

- Updated: 2026-06-08
- Checkpoint branch: `fix/model-complexity-feedback`
- Pull request: <https://github.com/mjcipriano/sweethome3d/pull/36>
- Target: restore usable model-complexity diagnostics and remove the unsafe
  load-time model simplifier that delayed or corrupted 3D model display.
- Reference workload:
  `example-files/2025-11-27-House-Layout-v3.sh3d`
- Reference workload shape: 141 MB, 435 furniture items, 162 walls, 47 rooms,
  8 levels, 435 model references, and 148 distinct model contents.
- Development runtime: the pinned `sweethome3d` Conda environment using
  standard conda-forge OpenJDK 17.
- Graphics environment used for local measurements: WSLg direct rendering,
  D3D12, NVIDIA GeForce GTX 1660 Ti, Mesa 21.2.6.

Do not commit the reference home unless its size and license are explicitly
approved. The `example-files/` directory is ignored.

## Completed And Accepted

| Area | Change | Result | Commit |
| --- | --- | --- | --- |
| Home loading memory | Canonicalize embedded content with digest-keyed lookup | Retained load heap reduced from 199-203 MB to 112-114 MB, about 44% | `0b00814` |
| Home loading speed | Skip redundant eager ZIP inflation when a modern local archive has a readable digest manifest | Warm load reduced from 2.46-2.99 s to 1.50-1.86 s, about 35-45% | `4d5d8c3` |
| 2D measurement | Add a warmed, repeatable 1920x1080 plan-paint benchmark with optional JFR | Current warmed medians are 9-12 ms and p95 results are 11-15 ms | `35585b7`, `3573e1b` |
| Toolchain | Pin OpenJDK 17 and developer/runtime dependencies in `environment.yml` | Build, JFR, Git, GitHub CLI, and Java 3D runtime dependencies resolve from one Conda environment | `9e37c1d` |
| OBJ/MTL loading | Replace synchronized `BufferedReader` with a 64 KiB unsynchronized reader in the thread-confined parser | Controlled scene-construction median reduced from 15.09 s to 13.44 s, about 11% | `3573e1b` |
| 3D measurement | Add Java 3D scene/frame benchmark and optional JFR capture | Safe scene mode works with the complex reference home | `3573e1b` |
| Startup measurement | Add a cold-start phase benchmark (`make benchmark-startup`) covering prefs init, home load, plan creation, and first 2D paint, with optional JFR | Cold pass on the reference home is about 2.0 s total; cold first paint is about 0.46 s; harness is headless and adds no app behavior (task A1) | merged PR #7 |
| 2D interaction measurement | Add an interaction benchmark (`make benchmark-plan-interaction`) reporting apply cost, invalidated repaint area, and paint cost for select/move/zoom | Found that every interaction invalidates the full 1920x1080 viewport (`dirty_pct=100`), so a single-piece selection costs a full ~9-10 ms repaint; `PlanComponent` has no targeted `repaint(rectangle)` calls (task A2) | merged PR #9 |
| 2D interaction | Repaint only the area of the previously and newly selected items (plus an indicator margin) on selection change, instead of the whole plan | Selecting a single piece dropped from invalidating 100% of the viewport to ~1% and from a ~9-10 ms paint to sub-ms; pixel-coverage check and GUI suite confirm no artifacts (task C1, selection) | merged PR #10 |
| 3D scene-update measurement | Add `BENCHMARK_MODE=update` to the 3D benchmark, timing scene-graph reaction to piece move, piece rotation, and camera move via an EDT barrier around the deferred `invokeLater` update | Baselines on the reference home: ~3 ms piece move, ~3 ms rotation, ~1 ms camera move; runs stably in scene mode without the off-screen frame path (task A3) | merged |
| 3D scene construction | Preload distinct furniture models in parallel (existing CPU loader pool) before the synchronous scene-tree build so the build only clones cached models; gated by `com.eteks.sweethome3d.j3d.preloadModels` (default on) | Interleaved 4-round A/B on the reference home (12 cores): median off-screen scene construction ~13.2 s with preload off vs ~7.0 s with preload on, about 47%; interactive async view unaffected; tests green (task D2) | merged PR #14 + kill-switch |
| Windows 3D rendering | Central `GraphicsEnvironmentConfiguration` (called from `SweetHome3DBootstrap`) defaults the Java 3D renderer to speed on Windows so `Component3DManager` skips scene/implicit antialiasing; kill-switch and `renderingQuality=quality` override; no-op on macOS/Linux | Targets choppy interactive 3D on switchable-graphics laptops (antialiasing is the biggest per-frame cost on integrated GPUs); scene builds correctly with antialiasing off and all tests pass. Frame-rate gain must be measured on Windows+NVIDIA hardware - cannot be measured on WSL/Mesa (task E1) | merged PR #18 |
| 3D diagnostics | Capture the OpenGL vendor/renderer/version via `Canvas3D.queryProperties` in `HomeComponent3D`'s render observer, measure a rolling FPS, surface both in a Help > 3D rendering information dialog and an optional on-canvas overlay, and log the GPU line to `graphics.log` | Lets a user confirm which GPU drives the 3D view (discrete vs integrated) and measure frame rate so Windows+NVIDIA tuning (E2) is observable; verified on WSLg - renderer reported as `D3D12 (NVIDIA GeForce GTX 1660 Ti)`; tests pass (task E-observability, enables E2) | merged PR #20 |
| 3D interactive frame rate | The diagnostics showed a complex home renders at ~1 FPS on a discrete NVIDIA GPU (CPU-bound render loop, not GPU). Add `compile()` of the home scene branch before `addBranchGraph` (display lists, default on, kill-switch `compileScene=false`) and use `TRANSPARENCY_SORT_NONE` under `renderingQuality=speed`; add an on-screen FPS benchmark (`benchmark-home-3d-fps`) since the off-screen frame path crashes on Mesa | `compile()` verified safe - post-compile scene mutation works with no exceptions; build and tests pass. Magnitude not measurable on the noisy WSL/Mesa D3D12 layer (display lists behave unlike native GL); to be confirmed on Windows+NVIDIA via the in-app overlay (task D-render / E2) | merged PR #22 |
| WSLg GPU testing | Add `make test-wsl-gpu`, a hardware-rendering smoke gate that checks WSLg X11/GLX, rejects software Mesa, requires the D3D12 renderer under WSL by default, runs Java 3D update mode, and confirms the on-screen FPS harness reports frames from Sweet Home 3D's `Canvas3D` | Passed on WSLg with `D3D12 (NVIDIA GeForce GTX 1660 Ti)`: the reference home's scene was created in 7.49 s, update operations completed, and the synthetic on-screen scene averaged 137 FPS over 335 samples. This gives WSL developers an adequate Java 3D GPU correctness/integration test without using the known-crashing off-screen frame benchmark or treating WSLg/Mesa as native Windows OpenGL | merged PR #24 |
| 3D bounds cache (D1) | Audit `ModelManager` model load/clone/bounds caching. Found inner `WeakHashMap<Transform3D, BoundingBox>` in `transformedModelNodeBounds` broken: `computeBounds` creates fresh `Transform3D` keys each call, and WeakHashMap silently evicts entries when keys are only weakly reachable, making the cache a permanent miss. Replaced inner map with `HashMap` - outer map remains `WeakHashMap<Content, ...>` so eviction still works at the Content level when models are GC'd. | Build and tests pass; magnitude TBD on the reference workload (435 pieces, 148 models); small home (7 pieces) shows no measurable delta | merged PR #25 |
| 3D scene update allocations (D3) | Replaced 14 `Arrays.asList(new T[]{x})` call sites in `HomeComponent3D` property-change listeners (furniture, room, polyline, dimension line, label) with `Collections.singletonList(x)`, avoiding a temporary array and ArrayList allocation per interaction event. Removed unused `java.util.Arrays` import. | Build and tests pass; per-event allocation savings are small and do not address steady-state frame rate | merged PR #25 |
| Native NVIDIA FPS loop + frame-rate root cause | Established a native-Windows measurement loop from WSL (Windows JDK via `cmd.exe` interop, staged jars+natives on `C:`), added a deterministic swap counter (`HomeComponent3D.getRenderedFrameCount`) and made `Home3DFpsBenchmark` measure frames over a fixed camera sweep | On a real GTX 1660 Ti (OpenGL 4.6): `compile()` is ~40x (0.25 -> ~10 FPS avg) and essential, but the frame rate is view-dependent and still ~1 FPS facing the high-poly models. Display-list toggling has no effect, so heavy model geometry is not GPU-resident; the home is dominated by huge models (37 MB DAE, 5-7 MB trees), not textures. Next: by-reference/VBO geometry (D4) | merged PR #29 |
| 3D display lists (D4 redirected) | Investigated D4 (by-reference/VBO) with the native loop and found it does not apply: no VBO path in this Java 3D build, loader geometry is by-copy/read-only (display-list eligible), and the bottleneck is CPU-side scene traversal, not GPU residency. Measured that display lists are a net loss for this complex home, so `GraphicsEnvironmentConfiguration` sets `j3d.displaylist=false` on the speed path (override `-Dj3d.displaylist=true`) | Native GTX 1660 Ti: ~11 -> ~18 FPS average (~40-60%, noisy) and removes the ~0.25 FPS display-list-without-compile failure mode; rendering is identical (vertex arrays). Heavy-view minimum stays ~1 FPS - that residual is the 37 MB model's raw triangle volume, needing decimation/LOD, not a rendering toggle. Tests pass | merged PR #30 |
| Release/About version | Put `Implementation-Version` in both application JAR manifests, prefer it in `SweetHome3D.getVersion()`, pass the release version into native launchers, and verify the executable JAR manifest during packaging | Removes the hard-coded `7.5` About fallback from packaged releases and makes artifact verification fail when the displayed and published versions diverge | merged PR #27 |
| WSLg rendering default and FPS measurement | Apply the existing speed rendering profile on WSLg as well as Windows, and add a configurable warm-up period to the real-view FPS benchmark | WSLg correctly selects the D3D12 NVIDIA renderer and speed profile. The complex reference home still averaged about 1 FPS after a 30 s warm-up, proving this profile alone does not solve the primary 3D-view bottleneck | merged PR #27 |
| Model complexity diagnostics | Make Vertices a default furniture-table column for new and existing homes, wire it into the display-property menu, repaint when asynchronous model counts arrive, localize its heading, and avoid invalid sorting state | Large models are visibly identified without changing their geometry. Counts remain diagnostic only until a topology-safe LOD implementation is available | current branch |
| Persistent topology-safe model LOD | Generate simplified OBJ caches asynchronously after furniture import or from `3D view > Generate model LOD cache`; persist original-to-LOD content mappings in `.sh3d`; use meshoptimizer per shape after rebuilding indices from complete position/normal/UV tuples; retain original/unsupported geometry; embed native libraries in platform and universal release JARs | Native simplification, home clone, embedded-content save/reopen, core tests, GUI tests, and embedded-native extraction pass. The complex reference-home generation/visual/FPS run is still pending because the current sandbox couldn't connect to WSLg or Xvfb and display escalation was unavailable | current branch |
| Release automation | Restore the `main` push trigger required by the existing release-please job | Qualifying merged changes once again create or update a release PR; merging that PR builds and attaches platform artifacts | current branch |
| Release asset upload | Upload only files from `release/` (`find -maxdepth 1 -type f`) so the embedded `native/` directory no longer breaks `gh release upload` | Unblocked publishing 7.7.0-beta.1, which had failed with "read release/native: is a directory" | merged PR #40 |
| Model LOD frame-rate validation (D4) | Generated the LOD cache for the reference home and A/B-measured it on a real GTX 1660 Ti via the native loop (`benchmark.preferXmlEntry=false` to read the serialized Home, then `useModelLODs` on vs off on the same home) | LOD gives a consistent ~30-40% average FPS gain (~6.5 -> ~9 FPS, higher peaks; every LOD-on run beat every LOD-off run). It is not a full fix: the heavy-view minimum stays ~1-2 FPS (CPU scene-traversal bound, and several models barely simplify under topology-safe simplification) | merged PR #41 |
| Model LOD XML persistence (D4 fix) | The `.sh3d` reader prefers the `Home.xml` entry, which did not serialize `Home.modelLODs`, so saved homes reopened with zero LODs and the feature delivered nothing. Write/read `modelLOD` elements in `HomeXMLExporter`/`HomeXMLHandler` (referencing the source model content, the LOD content, and vertex counts via the shared `savedContentNames`) and add an XML-path round-trip regression test | `make benchmark-model-lod` reopen assertion now passes; new `ModelLODTest.testModelLODPersistsThroughXmlHomeEntry` passes; native GTX 1660 Ti A/B through the real XML path shows LOD on faster than off in all three interleaved rounds. The LOD cache now survives save/reopen so users get the ~30-40% gain | merged PR #42 |
| Don't compile the live interactive scene (regression fix) | The scene `compile()` from PR #22 was applied to the interactive on-screen universe too. Compiling a live-edited graph broke runtime edits: toggling a piece's **Visible** flag no longer updated the 3D view, and the detached **separate-window** 3D view rendered nothing and broke on close. Restrict `compile()` to static off-screen scenes (`!listenToHomeUpdates`); the interactive view is never compiled | User confirmed both bugs vanish with `compileScene=false`, and the fix makes the interactive path behave identically; tests pass. Cost: the interactive view loses the compile speedup, so its frame rate now relies on LOD + `j3d.displaylist=false` + no-antialiasing, and on the pending scene-attachment batching | _pending_ |
| Fix the WSL/Mesa off-screen crash (F1 unblock) | The "Mesa GLX crash" in the off-screen `frame` benchmark and full Java 3D suite was not a driver bug: the dev/test/benchmark classpath listed `lib/*` before `lib/java3d-1.6/*`, loading the legacy **1.5.2** Java 3D (GLX-pbuffer off-screen) instead of the **1.6.2a** jars the shipped launchers use (JOGL 2.5 FBO off-screen). Flip the order in the `Makefile` (classpath + `JAVA_LIB_PATH`) and all `scripts/profile-*.sh` so 1.6.2a loads first | A 1920x1080 off-screen `frame` run now reports `offscreen_supported=true` and renders both requested frames on WSLg without SIGSEGV. Previously GL-excluded tests also reach normal JUnit completion instead of crashing: `OBJWriterTest` runs all 3 tests but currently has 2 unrelated `visibleOn` localization failures, and `HomeControllerTest` has a stale furniture-column assertion (6 vs 5). Dev now matches production's Java 3D. Follow-up: triage and re-enable the `GL_TEST_EXCLUDES` set | _pending_ |
| 3D visibility render regression guard (toward F2) | Now that off-screen rendering works (F1), add `FurnitureVisibility3DTest`: it renders the off-screen 3D view, hides a piece on the **live** (uncompiled, home-listening) scene, re-renders, and asserts the image changed. This reproduces the class of bug where compiling the interactive scene made runtime edits (Visible toggle, detached window) stop affecting the 3D view - which the construction-only GUI tests cannot catch. Runs in the Java 3D suite (`test-local` / CI `java3d-compatibility`), excluded from the headless `test-gui` | Passes with 1512 changed pixels (threshold 300); a non-updating live scene would leave the renders identical and fail. First behavioral 3D regression gate in CI | current branch |
| Re-enable stable WSLg GUI/Java 3D tests | Run each `GL_TEST_EXCLUDES` class individually on WSLg after the Java 3D 1.6 classpath fix, update stale assertions and remove classes that pass reliably from the exclusion list | Re-enabled in WSLg: `ModelLODGeneratorTest`, `PrintTest`, `UserPreferencesPanelTest`, `IconManagerTest`, `ModelManagerTest`, `PlanControllerTest`, `PackageDependenciesTest`, `HomeFileRecorderTest`, `TransferHandlerTest`, and `LevelTest`. The latest batch fixes raw XML home reads that regressed when local ZIP reads were optimized, removes stale fixed-pixel and fixed-count expectations, and makes transfer paste focus explicit; the normal `make test-gui` target passes all 40 tests on WSLg/D3D12. Still excluded: `PlanComponentTest`, `OBJWriterTest`, `PlanComponentWithFurnitureTest`, `ImportedTextureWizardTest`, `PhotoCreationTest`, `ImportedFurnitureWizardTest`, `HomeCameraTest`, `BackgroundImageWizardTest`, `HomeFurniturePanelTest`, `RoomTest`, and `HomeControllerTest`. CI/Xvfb validation of the latest batch remains pending | current branch |

## Tried And Rejected

Do not repeat these experiments without a materially different design or new
evidence.

| Experiment | Result | Decision |
| --- | --- | --- |
| Hand-written common-case decimal parser in `OBJLoader` | Three scene runs were 13.64 s, 13.96 s, and 17.37 s versus a 13.44 s median with only the reader optimization | Removed because it did not improve the end-to-end workload |
| JetBrains Runtime for Java 3D profiling under WSL | Native Mesa GLX crashes during rendering-context creation | Replaced with standard conda-forge OpenJDK 17 |
| 1920x1080 Java 3D off-screen frame benchmark on the legacy Java 3D/JOGL stack | Native crash in `libGLX_mesa.so` while creating the off-screen context | Keep `scene` mode as the safe default; retry after the graphics stack is upgraded |
| Complete legacy Java 3D JUnit suite on current WSLg/Mesa | JVM terminated in native `libGLX_mesa.so` before JUnit completed | Treat as a scheduled/manual compatibility probe; use `make test-gui` as the required stable GUI suite |
| OpenJDK 21 runtime vs 17 on the headless benchmarks (task A4) | Interleaved 4-round A/B on the reference home showed no repeatable difference: plan-render warmed median ~15 ms on both; startup cold ~2.16 s on both. Run-to-run variance on the WSL host (e.g. 2.0-3.4 s cold-start swings) swamps any JDK delta. No crashes or regressions on 21 | Keep the pinned OpenJDK 17; do not add a Java 21 dev toolchain without a materially quieter measurement host or a different workload. Bytecode stays at Java 8 either way |
| Share a `RenderingAttributes` instance across pieces to shrink `RenderBin.findAttributeBin` (the D-task hotspot) | Profiling the reference home's frame render (now possible after the Java 3D 1.6 off-screen fix) confirms `RenderBin.findAttributeBin` is the dominant Java hotspot: 6614 top-of-stack samples, next is 148. It is an O(n*bins) linear search Java 3D runs per render atom over its `AttributeBin` list. Pointing all visible shapes at one shared `RenderingAttributes` did **not** help - samples rose to 15353 and `AttributeBin.equals` appeared - because Java 3D matches bins by **value** (`AttributeBin.equals` over polygon/line/point/rendering attributes), not object identity, and the 435 pieces carry genuinely distinct attribute combinations from their model materials. Batching live attachment cannot reduce it either: the cost is the per-atom bin search during `RenderBin` construction regardless of how many transactions attach the atoms | The hotspot is internal to Java 3D's `RenderBin` (a list scan that should be a hash lookup), so reducing it belongs to the **F1** graphics-stack work (patch/upgrade Java 3D), not an app-level change; geometry merging is blocked by per-piece mutability (the `compile()` regression). Pivot app-level effort to measurable lower-risk wins - D3 scene-update allocations and C1 2D repaint scope |
| Compile each asynchronously loaded furniture branch before attaching it to the live scene | Compiling shared model geometry caused `RestrictedAccessException`; preserving compiled shared geometry then caused `CapabilityNotSetException` when pickability was initialized. Java 3D requires all mutable capabilities and per-instance state to be established before compilation | Removed. Revisit only as a broader model-instantiation refactor that separates immutable compiled geometry from mutable per-piece nodes |
| Hand-written vertex-clustering model simplifier | Ran synchronously on every large model load even when disabled, delaying observer notification and leaving white loading boxes visible. It also treated indexed geometry as triangle lists, lost separate normal/texture/color indices and attributes, and converted unsupported topology such as quads, strips, and fans incorrectly | Removed. Any future decimation/LOD work must preserve each geometry topology and vertex attributes, run outside the critical model-delivery path, retain the original model, and include visual-fidelity fixtures plus reference-home FPS measurements |

Generated `hs_err_pid*.log` files are diagnostic artifacts and must not be
committed.

## Validation At This Checkpoint

| Check | Result |
| --- | --- |
| `scripts/setup-conda-env.sh` | Passed; reconciled the existing environment from `environment.yml` (relaxed ant and openjdk version pins for conda-forge availability) |
| Ant production build on OpenJDK 17 | Passed |
| `make test-core` | Passed, 7 tests |
| `make test-gui` on WSLg | Passed, 13 tests |
| `make test-local-check` | Passed with direct NVIDIA-backed rendering |
| `make benchmark-plan-render ...` | Passed; latest run median 9 ms, p95 11 ms |
| `make benchmark-home-3d ... BENCHMARK_MODE=scene` | Passed; final confirmation run 14.95 s |
| `make test-wsl-gpu BENCHMARK_HOME=example-files/2025-11-27-House-Layout-v3.sh3d BENCHMARK_SECONDS=6` | Passed; GLX and Java 3D both reported `D3D12 (NVIDIA GeForce GTX 1660 Ti)`; scene creation 7.49 s; synthetic smoke 137 FPS over 335 samples |
| `ant -Dversion=7.5.1-beta.1 jarExecutable` | Passed; produced the prerelease executable JAR |
| Complete `make test-local TEST_DISPLAY_MODE=display` | Native Mesa GLX crash; known legacy graphics-stack limitation |
| Cross-platform GitHub Actions | Passed on Windows, Linux, macOS, and Linux GUI in [CI run 15](https://github.com/mjcipriano/sweethome3d/actions/runs/27095017708) |
| Prerelease packaging on Windows, Linux, and macOS | Required after merge through `release.yml` |
| D1/D3 branch build and core tests | Passed; 7/7 core tests on OpenJDK 17 |
| D1/D3 branch GUI tests (Xvfb) | Passed, 13/13 tests |
| D1/D3 branch 3D benchmark (Xvfb, test home) | Passed; scene creation 222 ms (7-piece home); reference home not available |
| Warmed WSLg FPS benchmark, speed profile, reference home | Passed on D3D12 / NVIDIA GTX 1660 Ti; 30 s warm-up + 15 s measurement; average 1 FPS, min 0, max 3, 916 samples |
| Current branch `make test-core` | Passed, 9/9 tests, including `SweetHome3DVersionTest` for manifest fallback and explicit launcher version override |
| Model-complexity repair production build | Passed on OpenJDK 17 |
| Model-complexity repair `make test-core` | Passed, 10/10 tests, including the new default Vertices column assertion |
| Model-complexity repair targeted `FurnitureTableTest` | Passed headlessly, 3/3 tests, including creation and lookup of the Vertices column |
| Model-complexity repair reference-home load | Passed with 435 furniture items in 1.74 s; confirms the complex 141 MB home still loads after removing eager simplification |
| Model-complexity repair Windows GUI suite | Passed 16/16 tests on the native Windows desktop JVM through WSL interop |
| Model-complexity repair Windows Java 3D loader test | Passed 2/2 tests on Oracle Java 8 with the bundled Windows OpenGL natives |
| Model-complexity repair native Windows FPS benchmark | Passed on NVIDIA GeForce GTX 1660 Ti, OpenGL 4.6 / driver 596.36. No-warm-up 16 s pass: average 5 FPS, min 1, max 33. After 30 s warm-up: average 11 FPS, min 1, max 38 over 182 frames |
| `ant -Dversion=7.5.2-beta.8 jarExecutable` | Passed; executable JAR manifest contains `Implementation-Version: 7.5.2-beta.8` |
| `scripts/verify-release.ps1` locally | Not run; `pwsh` is not installed in the current Conda environment. The equivalent JAR manifest check was verified with `unzip`, and GitHub release packaging will run the PowerShell verifier on hosted runners |
| Current branch Linux `make test-gui` | WSLg/Xvfb socket path is unavailable in this session; native Windows GUI fallback passed 16/16 through WSL interop |
| Forced-quality warmed FPS comparison | Blocked locally after WSLg stopped accepting `DISPLAY=:0` connections. The speed-profile run above is the only completed warmed WSLg FPS measurement for this branch |
| LOD focused native/persistence tests | Passed, 3/3: compact valid native mesh, home clone, and `.sh3d` embedded-content save/reopen after deleting source files |
| LOD branch `make test-core` | Passed, 12/12 including package dependency checks |
| LOD branch `make test-gui` on WSLg | Passed, 17/17 after excluding the JNI-only test from the Java-3D-free GUI suite |
| LOD executable JAR native extraction | Passed with an empty `java.library.path`; embedded Linux x64 meshoptimizer library loaded and native simplification test passed |
| Reference-home LOD generation/persistence benchmark | Pending: both direct `DISPLAY=:0` and private Xvfb failed to connect in the restricted sandbox; display escalation was unavailable |

## Next Work

Start future work from updated `main`. Use a new `perf/<scope>` branch per
task rather than continuing on a merged checkpoint branch. The backlog below is
organized into workstreams; the design rationale, file pointers, and acceptance
criteria for each task live in the working plan. Order is A first, then B/C/D/E
in parallel, then F.

**Workstream A - Measurement foundations**

- A1. Startup/EDT timeline benchmark - **done in this branch** (`make
  benchmark-startup`). Cold pass on the reference home is about 2.0 s with a
  cold first paint near 0.46 s.
- A2. 2D interaction micro-benchmark - **done** (`make
  benchmark-plan-interaction`). Finding: every interaction currently
  invalidates the full viewport, which makes C1 the highest-value 2D task -
  give `PlanComponent` targeted `repaint(rectangle)` calls for localized
  interactions (selection, single-piece move) so a small change stops paying a
  full-frame repaint.
- A3. 3D scene-update micro-benchmark - **done** (`make benchmark-home-3d
  BENCHMARK_MODE=update`). Baselines: piece move/rotate ~3 ms, camera move
  ~1 ms. This is the measurement baseline for workstream D (model loading and
  scene updates).
- A4. Evaluate the Java 21 runtime against Java 17 - **done; rejected**. No
  repeatable win on the WSL host (see Tried And Rejected); keep OpenJDK 17.
  Re-run only on a quieter measurement host. A newer JDK alone is not evidence
  of a speedup. Note: the WSL host's run-to-run variance is large enough that
  small (sub-15%) wins are not measurable here; controlled regression
  thresholds (F2) need a quieter runner.

**Workstream B - Startup and EDT latency** (B1 move blocking work off the EDT;
B2 defer/parallelize startup init). Depends on A1.

**Workstream C - 2D interaction latency** (C1 shrink invalidated repaint scope -
selection done; **plain-piece move done** (dragging a piece now repaints only the
union of its old and new bounds instead of the whole plan: `move_piece` on the
reference home dropped from a full 2,073,600 px / ~10.6 ms repaint to ~8,600 px /
~0 ms; doors, windows, staircases and groups still fall back to a full repaint
because they also repaint the wall/room/children they affect); the rotate/resize
path remains and zoom legitimately needs a full repaint; C2 cut per-paint
allocations and geometry rebuilds; C3 top-view icon decode/scale path). Depends
on A2.

**Workstream D - 3D scene, model loading and memory** (D1 model
load/clone/bounds caching audit - **done**: found and fixed broken inner
WeakHashMap bounds cache, replaced with HashMap; the cloneNode synchronized
bottleneck and per-clone HashMap allocation are noted but not yet addressed;
D2 parallelize model loading across
cores - **done**, ~51% faster off-screen scene construction; D3 reduce per-frame
allocations in scene updates - **in progress**: replaced 14 `Arrays.asList(new
T[]{x})` calls with `Collections.singletonList(x)` in HomeComponent3D
property-change listeners, avoiding temporary array/ArrayList per event. Next:
audit `HomePieceOfFurniture3D.update()` and related Object3DBranch subclasses
for larger allocation wins in the scene-graph rebuild path; D4 topology-safe
decimation/LOD - **current branch**: adds a persistent `Home` model-LOD cache,
import-time background generation for newly added home furniture, a manual
`3D view > Generate model LOD cache` command for existing files, runtime use of
cached models, and a meshoptimizer native simplifier. The generator converts
Java 3D geometry to indexed triangles, rebuilds adjacency from the complete
position/normal/UV tuple to preserve seams, simplifies per shape/material, and
keeps unsupported geometry at original fidelity. Focused native and persistence
tests pass; remaining validation is visual inspection and FPS measurement on the
reference home after generating/saving/reopening the cache. Depends on A3.

JFR confirms `RenderBin.findAttributeBin` is the dominant Java-side CPU hotspot
when rendering the reference home (6614 top-of-stack samples). It is a linear
scan Java 3D runs per render atom over its `AttributeBin` list, matched by value.
Investigation (see Tried And Rejected) showed this is **not** fixable at the app
level: sharing `RenderingAttributes` made it worse, and batching live attachment
cannot reduce a per-atom bin search. It belongs to **F1** (patch/upgrade Java 3D
so `findAttributeBin` is a hash lookup). Do not retry per-piece `compile()` or
geometry merging until immutable shared geometry and mutable instance
capabilities are separated. App-level effort should go to **D3** (scene-update
allocations) and **C1** (2D repaint scope), which are measurable and low-risk.

**Workstream E - Graphics environment tuning** (E1 central Windows auto-tuner
with kill-switch and stock fallback - **done**: defaults the 3D renderer to
speed/no-antialiasing on Windows; in-app 3D diagnostics (GPU/renderer + live FPS
via Help > 3D rendering information, an optional overlay, and `graphics.log`) are
**done** and give the measurement tool; E2 measure the antialiasing-off and any
further levers on real Windows+NVIDIA hardware - **pending user measurement**
(now observable via the diagnostics), since the frame path can't run on WSL/Mesa;
E3 discrete-GPU preference - the
reliable Optimus fix is a manual NVIDIA Control Panel / Windows graphics setting
because a `jpackage`-launched Java app can't export `NvOptimusEnablement`; this
is now documented in `docs/PERFORMANCE.md` rather than coded). Depends on A3.

**Workstream F - Graphics stack upgrade** (F1 unify/upgrade Java 3D, JOGL,
GlueGen, vecmath and native binaries together to stop the Mesa GLX crash and
restore the frame benchmark and full Java 3D suite; F2 3D regression
thresholds). Sequenced after the app-level wins. Until F1 is done,
`make test-wsl-gpu` is the required WSL Java 3D GPU smoke gate; it validates
hardware-accelerated WSLg/D3D12 rendering and on-screen frame production, while
the full off-screen frame path remains a compatibility probe.

`docs/JAVA_MODERNIZATION.md` defines the larger Java and graphics migration
sequence.

## Handoff Checklist

Every optimization agent must complete this list before handing work off:

- [ ] Read `AGENTS.md`, this file, `docs/PERFORMANCE.md`, and
  `docs/JAVA_MODERNIZATION.md`.
- [ ] Pull `main`, create a new `perf/<scope>` branch, and confirm the worktree
  does not contain unrelated user changes.
- [ ] Record the exact baseline command, runtime, hardware, workload, sample
  count, median, and p95 where applicable.
- [ ] Add the experiment to either **Completed And Accepted** or
  **Tried And Rejected**.
- [ ] Update **Validation At This Checkpoint** and **Next Work**.
- [ ] Add or update functional tests for changed behavior.
- [ ] Run the validation matrix required by `AGENTS.md`.
- [ ] Commit only intended files with a conventional commit.
- [ ] Push the branch and create or update a draft PR with measured results.
- [ ] Leave the repository with no generated crash reports or benchmark
  profiles staged.
